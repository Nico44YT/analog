package dev.mrturtle.analog.mixin;

import dev.mrturtle.analog.Analog;
import dev.mrturtle.analog.AnalogPlugin;
import dev.mrturtle.analog.access.JukeboxManagerAccessor;
import dev.mrturtle.analog.audio.RadioAudioInstance;
import dev.mrturtle.analog.audio.assets.MusicAssetManager;
import dev.mrturtle.analog.block.TransmitterBlockEntity;
import dev.mrturtle.analog.config.ConfigManager;
import dev.mrturtle.analog.util.RadioAudioUtil;
import dev.mrturtle.analog.util.RadioUtil;
import dev.mrturtle.analog.world.GlobalRadioState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.jukebox.JukeboxManager;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

@Mixin(JukeboxManager.class)
public abstract class JukeboxManagerMixin implements JukeboxManagerAccessor {
	@Shadow public abstract boolean isPlaying();

	@Shadow @Final private BlockPos pos;
	@Shadow private long ticksSinceSongStarted;
	@Unique
	private short[] cachedAudio = null;

	@Inject(method = "startPlaying", at = @At("TAIL"))
	public void startPlaying(WorldAccess world, RegistryEntry<JukeboxSong> song, CallbackInfo ci) {
		if (world.isClient())
			return;

		// We can't play anything if the record files failed to load, or they aren't loaded yet
		if (!MusicAssetManager.recordsLoaded)
			return;

		Identifier songId = song.value().soundEvent().value().getId();
		// We can only play vanilla records over the radio
		if (!songId.getNamespace().equals("minecraft"))
			return;

		cachedAudio = null;
		String songPath = "analog/records/%s.ogg".formatted(songId.getPath().replace("music_disc.", ""));
		try {
			cachedAudio = RadioAudioUtil.getAudioData(FabricLoader.getInstance().getConfigDir().resolve(songPath));
		} catch (Exception e) {
			Analog.LOGGER.error("Failed to load music disc for playback from path %s".formatted(songPath));
			e.printStackTrace();
		}

		analog$makeNearbyTransmittersPlay(world);
	}

	@Inject(method = "stopPlaying", at = @At("TAIL"))
	public void stopPlaying(WorldAccess world, BlockState state, CallbackInfo ci) {
		if (world.isClient())
			return;
		analog$makeNearbyTransmittersStop(world);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	public void tick(WorldAccess world, BlockState state, CallbackInfo ci) {
		if (!isPlaying())
			return;
		analog$makeNearbyTransmittersPlay(world);
	}

	@Unique
	public void analog$makeNearbyTransmittersStop(WorldAccess world) {
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

	@Unique
	public void analog$makeNearbyTransmittersPlay(WorldAccess world) {
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
				int startIndex = (int) (2400 * ticksSinceSongStarted);

				RadioAudioInstance audioInstance = RadioUtil.transmitDataOnChannel(AnalogPlugin.API, (ServerWorld) world, cachedAudio, transmitter.channel);
				audioInstance.setCurrentIndex(startIndex);
				audioInstances.put(pos.toImmutable(), audioInstance);
			}
		}
	}

	@Unique
	public void analog$setCachedAudio(short[] audioData) {
		cachedAudio = audioData;
	}
}
