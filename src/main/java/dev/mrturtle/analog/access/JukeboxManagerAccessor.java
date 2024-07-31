package dev.mrturtle.analog.access;

import net.minecraft.world.WorldAccess;

public interface JukeboxManagerAccessor {
    void analog$makeNearbyTransmittersPlay(WorldAccess world);
    void analog$makeNearbyTransmittersStop(WorldAccess world);
    void analog$setCachedAudio(short[] audioData);
}
