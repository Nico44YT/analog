package dev.mrturtle.analog.audio;

import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;

public class ReceiverAudioData {
	public AudioPlayer audioPlayer;
	public int channel;
	public RadioAudioInstance instance;

	public ReceiverAudioData(AudioPlayer audioPlayer, RadioAudioInstance instance) {
		this.audioPlayer = audioPlayer;
		this.channel = instance.channel;
		this.instance = instance;
	}
}
