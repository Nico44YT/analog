package dev.mrturtle.analog;

import dev.mrturtle.analog.config.ConfigManager;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Analog implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("analog");

	@Override
	public void onInitialize() {
		ConfigManager.loadConfig();
		PolymerResourcePackUtils.addModAssets("analog");
		PolymerResourcePackUtils.markAsRequired();
		ModDataComponents.initialize();
		ModItems.initialize();
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModSounds.initialize();
		// Encoders aren't being used anymore
		// Clean up player's encoder and decoder on disconnect
		/*ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			OpusEncoder encoder = RadioUtil.playerEncoders.remove(handler.player.getUuid());
			if (encoder != null)
				encoder.close();
			OpusDecoder decoder = RadioUtil.playerDecoders.remove(handler.player.getUuid());
			if (decoder != null)
				decoder.close();
		});*/
	}
}