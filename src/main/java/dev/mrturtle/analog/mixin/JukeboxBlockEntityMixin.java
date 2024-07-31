package dev.mrturtle.analog.mixin;

import dev.mrturtle.analog.Analog;
import dev.mrturtle.analog.AnalogPlugin;
import dev.mrturtle.analog.audio.RadioAudioInstance;
import dev.mrturtle.analog.audio.assets.MusicAssetManager;
import dev.mrturtle.analog.block.TransmitterBlockEntity;
import dev.mrturtle.analog.config.ConfigManager;
import dev.mrturtle.analog.util.RadioAudioUtil;
import dev.mrturtle.analog.util.RadioUtil;
import dev.mrturtle.analog.world.GlobalRadioState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.inventory.SingleStackInventory;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin extends BlockEntity implements SingleStackInventory {
	@Shadow public abstract boolean isPlayingRecord();

	@Shadow private long recordStartTick;
	@Shadow private long tickCount;
	@Unique
	private short[] cachedAudio = null;

	public JukeboxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Inject(method = "startPlaying", at = @At("TAIL"))
	public void startPlaying(CallbackInfo ci) {
		if (!hasWorld() || world.isClient)
			return;

		// We can't play anything if the record files failed to load, or they aren't loaded yet
		if (!MusicAssetManager.recordsLoaded)
			return;

		if (!(getStack().getItem() instanceof MusicDiscItem discItem))
			return;
		Identifier songId = discItem.getSound().getId();
		// We can only play vanilla records over the radio
		if (!songId.getNamespace().equals("minecraft"))
			return;

		boolean isAudioPlayerDisc = false;
		NbtCompound nbt = getStack().getNbt();
		if (nbt != null)
			if (nbt.containsUuid("CustomSound"))
				isAudioPlayerDisc = true;

		cachedAudio = null;
		if (!isAudioPlayerDisc) {
			String songPath = "analog/records/%s.ogg".formatted(songId.getPath().replace("music_disc.", ""));
			try {
				cachedAudio = RadioAudioUtil.getAudioData(FabricLoader.getInstance().getConfigDir().resolve(songPath));
			} catch (Exception e) {
				Analog.LOGGER.error("Failed to load music disc for playback from path %s".formatted(songPath));
				e.printStackTrace();
			}
		} else {
			String songName = "%s".formatted(nbt.getUuid("CustomSound"));
			Path basePath = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("audio_player_data");

			String songExtension = ".wav";
			if (!Files.exists(basePath.resolve(songName + songExtension)))
				songExtension = ".mp3";
			try {
				cachedAudio = RadioAudioUtil.getAudioData(basePath.resolve(songName + songExtension));
			} catch (Exception e) {
				Analog.LOGGER.error("Failed to load a custom Audio Player music disc for playback from path %s".formatted(songName + songExtension));
				e.printStackTrace();
			}
		}

		makeNearbyTransmittersPlay();
	}

	@Inject(method = "stopPlaying", at = @At("TAIL"))
	public void stopPlaying(CallbackInfo ci) {
		if (!hasWorld() || world.isClient)
			return;

		GlobalRadioState globalRadioState = RadioUtil.getGlobalRadioState((ServerWorld) world);
		Vec3d center = pos.toCenterPos();
		for (BlockPos transmitterPos : globalRadioState.getTransmitters()) {
			if (center.distanceTo(transmitterPos.toCenterPos()) > ConfigManager.config.radioListeningDistance)
				continue;
			TransmitterBlockEntity transmitter = (TransmitterBlockEntity) world.getBlockEntity(transmitterPos);
			if (transmitter == null)
				continue;
			if (!transmitter.enabled)
				continue;

			globalRadioState.audioManager.stopTransmitter(transmitterPos, pos.toImmutable());
		}
	}

	@Inject(method = "tick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", at = @At("TAIL"))
	public void tick(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
		if (!isPlayingRecord())
			return;
		makeNearbyTransmittersPlay();
	}

	@Unique
	private void makeNearbyTransmittersPlay() {
		if (cachedAudio == null)
			return;

		GlobalRadioState globalRadioState = RadioUtil.getGlobalRadioState((ServerWorld) world);
		Vec3d center = pos.toCenterPos();
		for (BlockPos transmitterPos : globalRadioState.getTransmitters()) {
			if (center.distanceTo(transmitterPos.toCenterPos()) > ConfigManager.config.radioListeningDistance)
				continue;
			TransmitterBlockEntity transmitter = (TransmitterBlockEntity) world.getBlockEntity(transmitterPos);
			if (transmitter == null)
				continue;
			if (!transmitter.enabled)
				continue;
			HashMap<BlockPos, RadioAudioInstance> audioInstances = globalRadioState.audioManager.transmitterAudioInstances.computeIfAbsent(transmitterPos, (playerEntity) -> new HashMap<>());
			// Only create an audio instance if the transmitter isn't already playing this jukebox's audio
			if (!audioInstances.containsKey(pos.toImmutable())) {
				// If the jukebox was playing before the transmitter was turned on it will need to start at the current part of the song
				int ticksPlayingFor = (int) (tickCount - recordStartTick);
				int startIndex = 2400 * ticksPlayingFor;

				RadioAudioInstance audioInstance = RadioUtil.transmitDataOnChannel(AnalogPlugin.API, (ServerWorld) world, cachedAudio, transmitter.channel);
				audioInstance.setCurrentIndex(startIndex);
				audioInstances.put(pos.toImmutable(), audioInstance);
			}
		}
	}
}
