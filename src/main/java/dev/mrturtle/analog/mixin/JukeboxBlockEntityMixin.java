package dev.mrturtle.analog.mixin;

import dev.mrturtle.analog.Analog;
import dev.mrturtle.analog.access.JukeboxManagerAccessor;
import dev.mrturtle.analog.util.RadioAudioUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.block.jukebox.JukeboxManager;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin extends BlockEntity {
    @Shadow @Final private JukeboxManager manager;

    public JukeboxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "setStack", at = @At("TAIL"))
    public void setStack(ItemStack stack, CallbackInfo ci) {
        if (world.isClient)
            return;

        boolean isAudioPlayerDisc = false;
        NbtComponent nbt = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbt != null)
            if (nbt.copyNbt().containsUuid("CustomSound"))
                isAudioPlayerDisc = true;

        Optional<RegistryEntry<JukeboxSong>> optionalSongEntry = JukeboxSong.getSongEntryFromStack(world.getRegistryManager(), stack);
        if (isAudioPlayerDisc) {
            String songName = "%s".formatted(nbt.copyNbt().getUuid("CustomSound"));
            Path basePath = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("audio_player_data");

            String songExtension = ".wav";
            if (!Files.exists(basePath.resolve(songName + songExtension)))
                songExtension = ".mp3";
            try {
                ((JukeboxManagerAccessor) manager).analog$setCachedAudio(RadioAudioUtil.getAudioData(basePath.resolve(songName + songExtension)));
                ((JukeboxManagerAccessor) manager).analog$makeNearbyTransmittersPlay(world, true);
            } catch (Exception e) {
                Analog.LOGGER.error("Failed to load a custom Audio Player music disc for playback from path %s".formatted(songName + songExtension));
                e.printStackTrace();
            }
        } else if (optionalSongEntry.isEmpty()) {
            ((JukeboxManagerAccessor) manager).analog$makeNearbyTransmittersStop(world);
        }
    }
}
