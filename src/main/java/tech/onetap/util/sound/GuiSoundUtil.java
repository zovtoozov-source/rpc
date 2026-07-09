package tech.onetap.util.sound;

import tech.onetap.util.IMinecraft;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

public final class GuiSoundUtil implements IMinecraft {

    private GuiSoundUtil() {}

    public static void playMenuOpen() {
        playWav("/assets/mre/sounds/gui/opengui.ogg", 0.7f);
    }

    public static void playMenuClose() {
        playWav("/assets/mre/sounds/gui/closegui.ogg", 0.7f);
    }

    public static void playClick() {
        // Не используется пока
    }

    private static void playWav(String resourcePath, float volume) {
        Thread t = new Thread(() -> {
            try {
                InputStream is = GuiSoundUtil.class.getResourceAsStream(resourcePath);
                if (is == null) return;

                BufferedInputStream bis = new BufferedInputStream(is);
                AudioInputStream ais = AudioSystem.getAudioInputStream(bis);

                // Конвертируем в PCM если нужно
                AudioFormat baseFormat = ais.getFormat();
                AudioFormat targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );

                AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, ais);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, targetFormat);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(targetFormat);

                // Устанавливаем громкость
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float dB = (float) (Math.log(volume) / Math.log(10.0) * 20.0);
                    gainControl.setValue(Math.max(gainControl.getMinimum(), dB));
                }

                line.start();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = pcmStream.read(buffer)) != -1) {
                    line.write(buffer, 0, bytesRead);
                }
                line.drain();
                line.close();
                pcmStream.close();
                ais.close();
            } catch (Exception ignored) {}
        }, "GuiSound");
        t.setDaemon(true);
        t.start();
    }
}
