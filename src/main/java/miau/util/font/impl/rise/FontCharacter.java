package miau.util.font.impl.rise;

import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import org.lwjgl.opengl.GL11;

public class FontCharacter {
  private int texture;
  private final int width;
  private final int height;
  private final java.awt.image.BufferedImage image;

  public FontCharacter(final int texture, final int width, final int height) {
    this.texture = texture;
    this.width = width;
    this.height = height;
    this.image = null;
  }

  public FontCharacter(
      final java.awt.image.BufferedImage image, final int width, final int height) {
    this.texture = -1;
    this.width = width;
    this.height = height;
    this.image = image;
  }

  public void upload() {
    if (this.texture == -1 && this.image != null) {
      this.texture = GL11.glGenTextures();
      final int width = this.width;
      final int height = this.height;
      final int[] pixels =
          this.image.getRGB(0, 0, width, height, new int[width * height], 0, width);
      final java.nio.ByteBuffer byteBuffer =
          org.lwjgl.BufferUtils.createByteBuffer(width * height * 4);
      for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
          final int pixel = pixels[x + y * width];
          byteBuffer.put((byte) ((pixel >> 16) & 0xFF));
          byteBuffer.put((byte) ((pixel >> 8) & 0xFF));
          byteBuffer.put((byte) (pixel & 0xFF));
          byteBuffer.put((byte) ((pixel >> 24) & 0xFF));
        }
      }
      ((java.nio.Buffer) byteBuffer).flip();
      net.minecraft.client.renderer.GlStateManager.bindTexture(this.texture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
      GL11.glTexImage2D(
          GL11.GL_TEXTURE_2D,
          0,
          GL11.GL_RGBA,
          width,
          height,
          0,
          GL11.GL_RGBA,
          GL11.GL_UNSIGNED_BYTE,
          byteBuffer);
    }
  }

  public void render(final float x, final float y) {
    if (this.texture == -1) {
      upload();
    }
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glTexCoord2f(0.0F, 0.0F);
    GL11.glVertex2f(x, y);
    GL11.glTexCoord2f(0.0F, 1.0F);
    GL11.glVertex2f(x, y + this.height);
    GL11.glTexCoord2f(1.0F, 1.0F);
    GL11.glVertex2f(x + this.width, y + this.height);
    GL11.glTexCoord2f(1.0F, 0.0F);
    GL11.glVertex2f(x + this.width, y);
    GL11.glEnd();
  }

  public int getTexture() {
    return this.texture;
  }

  public int getWidth() {
    return this.width;
  }

  public int getHeight() {
    return this.height;
  }
}
