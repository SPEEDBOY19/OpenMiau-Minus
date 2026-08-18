package miau.util.client;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.openal.AL10;

/** Plays short MP3 clips (bundled in the jar) through OpenAL via the jlayer decoder. */
public class Mp3Util {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static int lastSource = 0;
  private static int lastBuffer = 0;

  /** Plays an mp3 bundled under assets, e.g. "miau/sound/welcome/welcome.mp3". */
  public static void play(String assetPath) {
    if (mc.getSoundHandler() == null) return;
    try {
      ResourceLocation loc = new ResourceLocation("minecraft", assetPath);
      InputStream is = mc.getResourceManager().getResource(loc).getInputStream();
      stopCurrent();
      int[] result = playStream(new BufferedInputStream(is));
      lastSource = result[0];
      lastBuffer = result[1];
      is.close();
    } catch (Exception ignored) {
    }
  }

  /** Decodes the whole stream and plays it, returning {source, buffer}. */
  private static int[] playStream(InputStream in) throws Exception {
    Bitstream bitstream = new Bitstream(in);
    Decoder decoder = new Decoder();
    List<short[]> frames = new ArrayList<>();
    int totalSamples = 0;
    int sampleRate = 44100;
    int channels = 2;

    try {
      Header header;
      while ((header = bitstream.readFrame()) != null) {
        SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(header, bitstream);
        if (sb != null) {
          short[] pcm = sb.getBuffer();
          frames.add(pcm);
          totalSamples += pcm.length;
          sampleRate = sb.getSampleFrequency();
          channels = sb.getChannelCount();
        }
        bitstream.closeFrame();
      }
    } finally {
      bitstream.close();
    }

    if (frames.isEmpty() || totalSamples == 0) return new int[] {0, 0};

    short[] pcm = new short[totalSamples];
    int offset = 0;
    for (short[] frame : frames) {
      System.arraycopy(frame, 0, pcm, offset, frame.length);
      offset += frame.length;
    }

    int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;

    ByteBuffer data =
        ByteBuffer.allocateDirect(pcm.length * 2).order(ByteOrder.nativeOrder());
    ShortBuffer sbuf = data.asShortBuffer();
    sbuf.put(pcm);
    data.rewind();

    int buffer = AL10.alGenBuffers();
    AL10.alBufferData(buffer, format, data, sampleRate);

    int source = AL10.alGenSources();
    AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
    AL10.alSourcef(source, AL10.AL_GAIN, 1.0F);
    AL10.alSourcePlay(source);

    return new int[] {source, buffer};
  }

  private static void stopCurrent() {
    if (lastSource != 0) {
      try {
        AL10.alSourceStop(lastSource);
        AL10.alDeleteSources(lastSource);
      } catch (Exception ignored) {
      }
      lastSource = 0;
    }
    if (lastBuffer != 0) {
      try {
        AL10.alDeleteBuffers(lastBuffer);
      } catch (Exception ignored) {
      }
      lastBuffer = 0;
    }
  }
}
