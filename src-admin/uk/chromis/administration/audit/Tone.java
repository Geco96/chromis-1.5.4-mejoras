package uk.chromis.administration.audit;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Beeps of the auditor (the ones of AuditorPickPack): short high = correct,
 * long higher = order finished, long low = error. Played in the background;
 * without a sound card the system beep is used.
 */
final class Tone {

    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auditor-sonido");
        t.setDaemon(true);
        return t;
    });

    private Tone() {
    }

    static void ok() {
        play(1000, 100);
    }

    static void done() {
        play(1500, 300);
    }

    static void error() {
        play(250, 500);
    }

    private static void play(int hz, int ms) {
        PLAYER.execute(() -> {
            float rate = 16000f;
            byte[] wave = new byte[(int) (rate * ms / 1000)];
            for (int i = 0; i < wave.length; i++) {
                // square-ish wave, short fade at the ends so it does not click
                double fade = Math.min(1, Math.min(i, wave.length - i) / 160.0);
                wave[i] = (byte) (Math.signum(Math.sin(2 * Math.PI * hz * i / rate)) * 60 * fade);
            }
            try (SourceDataLine line = AudioSystem.getSourceDataLine(new AudioFormat(rate, 8, 1, true, false))) {
                line.open();
                line.start();
                line.write(wave, 0, wave.length);
                line.drain();
            } catch (Exception | LinkageError ex) {
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
        });
    }
}
