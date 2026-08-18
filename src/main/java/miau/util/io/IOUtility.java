package miau.util.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * IO utility for loading resources into ByteBuffers Adapted for Forge 1.8.9 from Opal's IOUtility
 */
public class IOUtility {

  /**
   * Reads an InputStream into a ByteBuffer
   *
   * @param inputStream The input stream to read
   * @param bufferSize Initial buffer size
   * @return ByteBuffer containing the resource data
   */
  public static ByteBuffer ioResourceToByteBuffer(InputStream inputStream, int bufferSize) {
    if (inputStream == null) {
      throw new IllegalArgumentException("InputStream cannot be null");
    }

    try {
      BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
      ReadableByteChannel channel = Channels.newChannel(bufferedStream);

      ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);

      while (true) {
        int bytes = channel.read(buffer);
        if (bytes == -1) {
          break;
        }
        if (buffer.remaining() == 0) {

          ByteBuffer newBuffer = ByteBuffer.allocateDirect(buffer.capacity() * 2);
          ((java.nio.Buffer) buffer).flip();
          newBuffer.put(buffer);
          buffer = newBuffer;
        }
      }

      ((java.nio.Buffer) buffer).flip();
      channel.close();
      bufferedStream.close();

      return buffer;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read resource into ByteBuffer", e);
    }
  }

  /**
   * Reads an InputStream fully into a byte array
   *
   * @param inputStream The input stream to read
   * @return byte array containing the resource data
   */
  public static byte[] readAllBytes(InputStream inputStream) {
    if (inputStream == null) {
      throw new IllegalArgumentException("InputStream cannot be null");
    }

    try {
      BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
      byte[] buffer = new byte[8192];
      int bytesRead;
      java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

      while ((bytesRead = bufferedStream.read(buffer)) != -1) {
        output.write(buffer, 0, bytesRead);
      }

      bufferedStream.close();
      return output.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read resource into byte array", e);
    }
  }
}
