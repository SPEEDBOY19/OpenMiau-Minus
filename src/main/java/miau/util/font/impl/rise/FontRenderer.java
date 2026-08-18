package miau.util.font.impl.rise;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.render.ColorUtil;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class FontRenderer extends miau.util.font.Font {

  private static final String ALPHABET = "ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final String COLOR_CODE_CHARACTERS = "0123456789abcdefklmnor";
  private static final Color TRANSPARENT_COLOR = new Color(255, 255, 255, 0);
  private static final float SCALE = 0.5f;
  private static final float SCALE_INVERSE = 1 / SCALE;
  private static final char COLOR_INVOKER = '\247';
  private static final int[] COLOR_CODES = new int[32];
  private static final int LATIN_MAX_AMOUNT = 256;
  private static final int MARGIN_WIDTH = 4;
  private static final int MASK = 0xFF;

  private final Font font;
  private final boolean fractionalMetrics;
  private final float fontHeight;

  private final java.util.Map<Character, FontCharacter> regularCharacters =
      new java.util.HashMap<>();
  private final java.util.Map<Character, FontCharacter> boldCharacters = new java.util.HashMap<>();
  private boolean antialiasing = true, international = false;

  private Font plainFont;
  private Font boldFont;
  private FontMetrics plainFontMetrics;
  private FontMetrics boldFontMetrics;
  private Graphics2D plainFontGraphics;
  private Graphics2D boldFontGraphics;

  public FontRenderer(
      Font font, boolean fractionalMetrics, boolean antialiasing, boolean international) {
    calculateColorCodes();
    this.antialiasing = antialiasing;
    this.font = font;
    this.fractionalMetrics = fractionalMetrics;
    this.fontHeight =
        (float)
            (font.getStringBounds(
                        ALPHABET,
                        new FontRenderContext(
                            new AffineTransform(), antialiasing, fractionalMetrics))
                    .getHeight()
                / 2);
    setupFonts();
    this.fillCharacters(this.regularCharacters, Font.PLAIN);
    this.fillCharacters(this.boldCharacters, Font.BOLD);
    this.international = international;
  }

  public FontRenderer(
      final Font font, final boolean fractionalMetrics, final boolean antialiasing) {
    this(font, fractionalMetrics, antialiasing, false);
  }

  public FontRenderer(final Font font, final boolean fractionalMetrics) {
    this(font, fractionalMetrics, true, false);
  }

  private void setupFonts() {
    this.plainFont = font.deriveFont(Font.PLAIN);
    this.boldFont = font.deriveFont(Font.BOLD);

    final BufferedImage plainFontImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    this.plainFontGraphics = (Graphics2D) plainFontImage.getGraphics();
    this.plainFontMetrics = plainFontGraphics.getFontMetrics(this.plainFont);

    final BufferedImage boldFontImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    this.boldFontGraphics = (Graphics2D) boldFontImage.getGraphics();
    this.boldFontMetrics = boldFontGraphics.getFontMetrics(this.boldFont);
  }

  public static void calculateColorCodes() {
    for (int i = 0; i < 32; ++i) {
      final int amplifier = (i >> 3 & 1) * 85;
      int red = (i >> 2 & 1) * 170 + amplifier;
      int green = (i >> 1 & 1) * 170 + amplifier;
      int blue = (i & 1) * 170 + amplifier;
      if (i == 6) {
        red += 85;
      }
      if (i >= 16) {
        red /= 4;
        green /= 4;
        blue /= 4;
      }
      COLOR_CODES[i] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
    }
  }

  public void fillCharacters(
      final java.util.Map<Character, FontCharacter> characters, final int style) {
    for (int i = 0; i < LATIN_MAX_AMOUNT; ++i) {
      final char character = (char) i;
      characters.put(character, createCharacter(character, style));
    }
  }

  private FontCharacter createCharacter(final char character, final int style) {
    final Font font = (style == Font.BOLD) ? this.boldFont : this.plainFont;
    final Graphics2D fontGraphics =
        (style == Font.BOLD) ? this.boldFontGraphics : this.plainFontGraphics;
    final FontMetrics fontMetrics =
        (style == Font.BOLD) ? this.boldFontMetrics : this.plainFontMetrics;

    final Rectangle2D charRectangle = fontMetrics.getStringBounds(character + "", fontGraphics);

    final int width =
        Math.max(
            1, MathHelper.ceiling_float_int((float) charRectangle.getWidth()) + MARGIN_WIDTH * 2);

    final int maxAscent = fontMetrics.getMaxAscent();
    final int maxDescent = fontMetrics.getMaxDescent();
    final int baseline = Math.max(font.getSize(), maxAscent);
    final int height = Math.max(1, baseline + maxDescent);

    final BufferedImage charImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D charGraphics = (Graphics2D) charImage.getGraphics();
    charGraphics.setFont(font);

    charGraphics.setColor(TRANSPARENT_COLOR);
    charGraphics.fillRect(0, 0, width, height);
    setRenderHints(charGraphics);
    charGraphics.drawString(character + "", MARGIN_WIDTH, baseline);

    if (net.minecraft.client.Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
      final int charTexture = GL11.glGenTextures();
      uploadTexture(charTexture, charImage, width, height);
      return new FontCharacter(charTexture, width, height);
    } else {
      return new FontCharacter(charImage, width, height);
    }
  }

  private synchronized FontCharacter getCharacter(final char character, final int style) {
    final java.util.Map<Character, FontCharacter> characters =
        (style == Font.BOLD) ? this.boldCharacters : this.regularCharacters;
    FontCharacter fontCharacter = characters.get(character);
    if (fontCharacter == null) {
      fontCharacter = createCharacter(character, style);
      characters.put(character, fontCharacter);
    }
    if (fontCharacter.getTexture() == -1
        && net.minecraft.client.Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
      fontCharacter.upload();
    }
    return fontCharacter;
  }

  public void setRenderHints(final Graphics2D graphics) {
    graphics.setColor(Color.WHITE);
    if (antialiasing) {
      graphics.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS,
        fractionalMetrics
            ? RenderingHints.VALUE_FRACTIONALMETRICS_ON
            : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
  }

  public void uploadTexture(
      final int texture, final BufferedImage image, final int width, final int height) {
    final int[] pixels = image.getRGB(0, 0, width, height, new int[width * height], 0, width);
    final ByteBuffer byteBuffer = BufferUtils.createByteBuffer(width * height * MARGIN_WIDTH);
    for (int y = 0; y < height; ++y) {
      for (int x = 0; x < width; ++x) {
        final int pixel = pixels[x + y * width];
        byteBuffer.put((byte) ((pixel >> 16) & MASK));
        byteBuffer.put((byte) ((pixel >> 8) & MASK));
        byteBuffer.put((byte) (pixel & MASK));
        byteBuffer.put((byte) ((pixel >> 24) & MASK));
      }
    }
    ((java.nio.Buffer) byteBuffer).flip();
    GlStateManager.bindTexture(texture);
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

  public int draw(final String text, final double x, final double y, final int color) {
    return draw(text, x, y, color, false);
  }

  public int drawCentered(final String text, final double x, final double y, final int color) {
    return draw(text, x - (width(text) >> 1), y, color, false);
  }

  public int drawRight(String text, double x, double y, int color) {
    return draw(text, x - (width(text)), y, color, false);
  }

  public int drawWithShadow(final String text, final double x, final double y, final int color) {
    return draw(text, x, y, color, false);
  }

  public void drawCenteredStringWithShadow(
      final String text, final float x, final float y, final int color) {
    draw(text, x - (width(text) >> 1), y, color, false);
  }

  public int draw(String text, double x, double y, final int color, final boolean shadow) {
    if (text == null) {
      return 0;
    }

    double givenX = x;
    GL11.glPushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glScalef(SCALE, SCALE, SCALE);

    x -= MARGIN_WIDTH / SCALE_INVERSE;
    y -= MARGIN_WIDTH / SCALE_INVERSE;
    x *= SCALE_INVERSE;
    y *= SCALE_INVERSE;
    y -= fontHeight / 5;

    final double startX = x;

    ColorUtil.glColor(shadow ? Color.white.getRGB() : color);
    text = text.replaceAll("\247l", "");
    try {
      char[] characters = text.toCharArray();
      final int textLength = characters.length;
      final int lineHeightTimes2 = (int) (height() * 2);
      final int marginWidthTimes2 = MARGIN_WIDTH * 2;

      for (int i = 0; i < textLength; i++) {
        char character = characters[i];

        if (character == '\n') {
          x = startX;
          y += lineHeightTimes2;
          continue;
        }
        if (character == '§') {
          i++;
          if (i < characters.length) {
            int index = COLOR_CODE_CHARACTERS.indexOf(characters[i]);
            if (index != -1 && index < COLOR_CODES.length) {
              ColorUtil.glColor(COLOR_CODES[index]);
            }
          }
          continue;
        }

        final FontCharacter fontCharacter = getCharacter(character, Font.PLAIN);
        if (fontCharacter == null) continue;

        float characterWidth = fontCharacter.getWidth();
        fontCharacter.render((float) x, (float) y);
        x += characterWidth - marginWidthTimes2;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    GL11.glDisable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GlStateManager.bindTexture(0);
    GL11.glPopAttrib();
    GL11.glPopMatrix();

    return (int) (x - givenX);
  }

  @Override
  public void drawCharacter(final char character, final int x, final int y, final Color color) {
    final FontCharacter fontCharacter = getCharacter(character, Font.PLAIN);
    if (fontCharacter == null) return;
    GlStateManager.color(
        color.getRed() / 255f,
        color.getGreen() / 255f,
        color.getBlue() / 255f,
        color.getAlpha() / 255f);
    fontCharacter.render(x, y);
  }

  public int width(String text) {
    if (text == null) return 0;
    text = text.replaceAll("\247l", "");
    final int length = text.length();
    int width = 0;
    for (int i = 0; i < length; ++i) {
      final char character = text.charAt(i);
      if (character == COLOR_INVOKER) {
        i++;
        continue;
      }
      final FontCharacter fontCharacter = getCharacter(character, Font.PLAIN);
      if (fontCharacter == null) continue;
      width += fontCharacter.getWidth() - MARGIN_WIDTH * 2;
    }

    return width / 2;
  }

  public float height() {
    return fontHeight;
  }

  private boolean requiresInternationalFont(String text) {
    int highest = 0;

    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) > highest) highest = text.charAt(i);
    }

    return highest >= 256;
  }
}
