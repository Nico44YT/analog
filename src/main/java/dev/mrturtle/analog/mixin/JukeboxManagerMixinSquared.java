package dev.mrturtle.analog.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import dev.mrturtle.analog.access.JukeboxManagerAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.jukebox.JukeboxManager;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JukeboxManager.class, priority = 1500)
public abstract class JukeboxManagerMixinSquared {
    @Shadow public abstract boolean isPlaying();

    @Shadow private @Nullable RegistryEntry<JukeboxSong> song;

    @TargetHandler(
            mixin = "de.maxhenkel.audioplayer.mixin.JukeboxSongPlayerMixin",
            name = "tick"
    )
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"))
    public void tick(WorldAccess world, BlockState state, CallbackInfo originalCi, CallbackInfo ci) {
        if (!isPlaying())
            return;
        if (song != null)
            return;
        ((JukeboxManagerAccessor) this).analog$makeNearbyTransmittersPlay(world, false);
    }
}
