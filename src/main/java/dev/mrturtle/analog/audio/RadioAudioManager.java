package dev.mrturtle.analog.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import dev.mrturtle.analog.AnalogPlugin;
import dev.mrturtle.analog.ModBlocks;
import dev.mrturtle.analog.block.ReceiverBlockEntity;
import dev.mrturtle.analog.util.RadioUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RadioAudioManager {
	public final ArrayList<RadioAudioInstance> activeAudioInstances = new ArrayList<>();
	public final HashMap<BlockPos, HashMap<BlockPos, RadioAudioInstance>> transmitterAudioInstances = new HashMap<>();
	public final HashMap<BlockPos, ArrayList<ReceiverAudioData>> receiverAudioPlayers = new HashMap<>();
	public final HashMap<ServerPlayerEntity, ArrayList<PlayerAudioData>> playerAudioPlayers = new HashMap<>();

	public void tick(ServerWorld world) {
		// Remove audio instances that are done
		activeAudioInstances.removeIf((audioInstance) -> audioInstance.isDone);

		for (ServerPlayerEntity player : world.getPlayers()) {
			for (RadioAudioInstance audioInstance : activeAudioInstances) {
				if (RadioUtil.isReceivingChannel(player, audioInstance.channel))
					playerReceiverTurnedOn(player, audioInstance.channel);
				else
					playerReceiverTurnedOff(player, audioInstance.channel);
			}

			if (playerAudioPlayers.containsKey(player)) {
				ArrayList<PlayerAudioData> audioPlayers = playerAudioPlayers.get(player);
				// Remove audio players that are done or invalid
				audioPlayers.forEach((audioData) -> {
					if (audioData.channel != audioData.instance.channel || audioData.instance.isDone)
						audioData.audioPlayer.stopPlaying();
				});
				audioPlayers.removeIf((audioData) -> audioData.audioPlayer.isStopped());

				for (PlayerAudioData audioData : audioPlayers) {
					audioData.audioChannel.updateLocation(AnalogPlugin.API.createPosition(player.getX(), player.getY(), player.getZ()));
				}
			}
		}

		// Remove audio players that are done or invalid
		for (Map.Entry<BlockPos, ArrayList<ReceiverAudioData>> entry : receiverAudioPlayers.entrySet()) {
			ArrayList<ReceiverAudioData> audioPlayers = entry.getValue();

			audioPlayers.forEach((audioData) -> {
				if (audioData.channel != audioData.instance.channel || audioData.instance.isDone)
					audioData.audioPlayer.stopPlaying();
			});
			audioPlayers.removeIf((audioData) -> audioData.audioPlayer.isStopped());

			if (!audioPlayers.isEmpty()) {
				BlockPos receiverPos = entry.getKey();
				if (world.isChunkLoaded(receiverPos)) {
					ReceiverBlockEntity receiver = (ReceiverBlockEntity) world.getBlockEntity(receiverPos);
					receiver.lastAudioPlayedTick = world.getTime();
					world.updateNeighborsAlways(receiverPos, ModBlocks.RECEIVER_BLOCK);
				}
			}
		}
	}

	public void receiverTurnedOn(ServerWorld world, BlockPos pos, int receivingChannel) {
		for (RadioAudioInstance instance : activeAudioInstances) {
			if (instance.channel != receivingChannel)
				continue;
			startReceivingAudioInstance(world, pos, instance);
		}
	}

	public void receiverTurnedOff(BlockPos pos) {
		ArrayList<ReceiverAudioData> audioPlayers = receiverAudioPlayers.remove(pos);
		if (audioPlayers == null)
			return;
		for (ReceiverAudioData audioData : audioPlayers) {
			if (audioData.audioPlayer.isPlaying())
				audioData.audioPlayer.stopPlaying();
		}
	}

	public void playerReceiverTurnedOn(ServerPlayerEntity player, int receivingChannel) {
		ArrayList<PlayerAudioData> audioPlayers = playerAudioPlayers.computeIfAbsent(player, (playerEntity) -> new ArrayList<>());

		for (RadioAudioInstance instance : activeAudioInstances) {
			if (instance.channel != receivingChannel)
				continue;
			boolean alreadyExists = false;
			for (PlayerAudioData audioData : audioPlayers) {
				if (audioData.instance == instance) {
					alreadyExists = true;
					break;
				}
			}
			if (!alreadyExists)
				startReceivingAudioInstance(player.getServerWorld(), player, instance);
		}
	}

	public void playerReceiverTurnedOff(ServerPlayerEntity player, int receivingChannel) {
		ArrayList<PlayerAudioData> audioPlayers = playerAudioPlayers.get(player);
		if (audioPlayers == null)
			return;

		ArrayList<PlayerAudioData> toBeRemoved = new ArrayList<>();
		for (PlayerAudioData audioData : audioPlayers) {
			if (audioData.channel == receivingChannel) {
				audioData.audioPlayer.stopPlaying();
				toBeRemoved.add(audioData);
			}
		}
		for (PlayerAudioData audioData : toBeRemoved) {
			audioPlayers.remove(audioData);
		}
	}

	public void startReceivingAudioInstance(ServerWorld world, BlockPos pos, RadioAudioInstance instance) {
		VoicechatServerApi serverApi = AnalogPlugin.API;

		LocationalAudioChannel channel = serverApi.createLocationalAudioChannel(UUID.randomUUID(), serverApi.fromServerLevel(world), serverApi.createPosition(pos.toCenterPos().getX(), pos.toCenterPos().getY(), pos.toCenterPos().getZ()));
		if (channel == null)
			return;
		// Receivers play arbitrary audio such as music for triple the distance of voices
		channel.setDistance(24f);
		channel.setCategory(AnalogPlugin.RADIO_CATEGORY);

		AudioPlayer audioPlayer = serverApi.createAudioPlayer(channel, serverApi.createEncoder(), instance.audioSupplier);
		audioPlayer.startPlaying();

		ArrayList<ReceiverAudioData> audioPlayers = receiverAudioPlayers.computeIfAbsent(pos, (blockPos) -> new ArrayList<>());
		audioPlayers.add(new ReceiverAudioData(audioPlayer, instance));
	}

	public void startReceivingAudioInstance(ServerWorld world, ServerPlayerEntity player, RadioAudioInstance instance) {
		VoicechatServerApi serverApi = AnalogPlugin.API;

		LocationalAudioChannel channel = serverApi.createLocationalAudioChannel(UUID.randomUUID(), serverApi.fromServerLevel(world), serverApi.createPosition(player.getX(), player.getY(), player.getZ()));
		if (channel == null)
			return;
		channel.setDistance(8f);
		channel.setCategory(AnalogPlugin.RADIO_CATEGORY);

		AudioPlayer audioPlayer = serverApi.createAudioPlayer(channel, serverApi.createEncoder(), instance.audioSupplier);
		audioPlayer.startPlaying();

		ArrayList<PlayerAudioData> audioPlayers = playerAudioPlayers.computeIfAbsent(player, (playerEntity) -> new ArrayList<>());
		audioPlayers.add(new PlayerAudioData(audioPlayer, channel, instance));
	}

	public void changeTransmitterChannel(BlockPos pos, int newChannel) {
		HashMap<BlockPos, RadioAudioInstance> audioInstances = transmitterAudioInstances.get(pos);
		if (audioInstances == null)
			return;
		for (RadioAudioInstance audioInstance : audioInstances.values()) {
			audioInstance.channel = newChannel;
		}
	}

	public void stopTransmitter(BlockPos pos, BlockPos jukeboxPos) {
		HashMap<BlockPos, RadioAudioInstance> audioInstances = transmitterAudioInstances.get(pos);
		if (audioInstances == null)
			return;
		if (audioInstances.containsKey(jukeboxPos)) {
			stopAudioInstance(audioInstances.get(jukeboxPos));
			audioInstances.remove(jukeboxPos);
		}
	}

	public void stopTransmitter(BlockPos pos) {
		HashMap<BlockPos, RadioAudioInstance> audioInstances = transmitterAudioInstances.get(pos);
		if (audioInstances == null)
			return;
		for (RadioAudioInstance instance : audioInstances.values()) {
			stopAudioInstance(instance);
		}
		audioInstances.clear();
	}

	public void stopAudioInstance(RadioAudioInstance instance) {
		instance.interrupt();

		for (ArrayList<ReceiverAudioData> audioPlayers : receiverAudioPlayers.values()) {
			for (ReceiverAudioData audioData : audioPlayers) {
				audioData.audioPlayer.stopPlaying();
			}
		}

		for (ArrayList<PlayerAudioData> audioPlayers : playerAudioPlayers.values()) {
			for (PlayerAudioData audioData : audioPlayers) {
				audioData.audioPlayer.stopPlaying();
			}
		}
	}
}
