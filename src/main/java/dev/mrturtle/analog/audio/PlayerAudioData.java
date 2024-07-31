package dev.mrturtle.analog.audio;

import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;

public class PlayerAudioData {
	public AudioPlayer audioPlayer;
	public LocationalAudioChannel audioChannel;
	public int channel;
	public RadioAudioInstance instance;

	 public PlayerAudioData(AudioPlayer audioPlayer, LocationalAudioChannel audioChannel, RadioAudioInstance instance) {
		 this.audioPlayer = audioPlayer;
		 this.audioChannel = audioChannel;
		 this.channel = instance.channel;
		 this.instance = instance;
	 }
}
