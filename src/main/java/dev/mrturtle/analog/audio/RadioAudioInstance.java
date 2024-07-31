package dev.mrturtle.analog.audio;

import java.util.Arrays;
import java.util.function.Supplier;

public class RadioAudioInstance extends Thread {
	private static final long CHUNK_SIZE_NS = 20000000L;
	private final short[] audioData;
	private int currentIndex = 0;

	public int channel;
	public final Supplier<short[]> audioSupplier;
	private Runnable onStopped;

	public boolean isDone = false;

	public RadioAudioInstance(int channel, short[] audioData) {
		this.channel = channel;
		this.audioData = audioData;

		audioSupplier = () -> {
			if (currentIndex > audioData.length)
				return null;
			return Arrays.copyOfRange(audioData, currentIndex, currentIndex + 960);
		};
		start();
	}

	public RadioAudioInstance(int channel, short[] audioData, Runnable onStopped) {
		this(channel, audioData);
		this.onStopped = onStopped;
	}

	public void run() {
		while (currentIndex <= audioData.length) {
			currentIndex += 960;
			try {
				Thread.sleep(CHUNK_SIZE_NS / 1000000L);
			} catch (InterruptedException ignored) {
				break;
			}
		}
		if (onStopped != null)
			onStopped.run();
		isDone = true;
	}

	public void setCurrentIndex(int newIndex) {
		currentIndex = newIndex;
	}
}
