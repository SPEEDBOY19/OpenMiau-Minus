package miau.util.render;

import java.awt.*;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.font.Font;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.time.*;
import miau.util.vector.Vector2d;
import miau.util.world.*;

public final class ColorUtil {
  public static final Color RED = new Color(255, 0, 0);
  public static final Color GOLD = new Color(255, 165, 0);
  public static final Color YELLOW = new Color(255, 255, 0);
  public static final Color GREEN = new Color(0, 255, 0);

  private ColorUtil() {}

  public static Color fromHSB(float hue, float saturation, float brightness) {
    return new Color(Color.HSBtoRGB(hue, saturation, brightness));
  }

  public static Color interpolate(float progress, Color startColor, Color endColor) {
    progress = Math.min(Math.max(progress, 0.0f), 1.0f);
    return new Color(
        (int)
            ((float) startColor.getRed()
                + progress * (float) (endColor.getRed() - startColor.getRed())),
        (int)
            ((float) startColor.getGreen()
                + progress * (float) (endColor.getGreen() - startColor.getGreen())),
        (int)
            ((float) startColor.getBlue()
                + progress * (float) (endColor.getBlue() - startColor.getBlue())));
  }

  public static Color getHealthBlend(float percent) {
    if (percent >= 0.9f) {
      return GREEN;
    }
    if (percent >= 0.55f) {
      return ColorUtil.interpolate((percent - 0.55f) / 0.35f, YELLOW, GREEN);
    }
    if (percent >= 0.45f) {
      return YELLOW;
    }
    if (percent >= 0.1f) {
      return ColorUtil.interpolate((percent - 0.1f) / 0.35f, RED, YELLOW);
    }
    return RED;
  }

  public static Color scale(Color color, float scaleFactor, int alpha) {
    return new Color(
        Math.min(Math.max((int) ((float) color.getRed() * scaleFactor), 0), 255),
        Math.min(Math.max((int) ((float) color.getGreen() * scaleFactor), 0), 255),
        Math.min(Math.max((int) ((float) color.getBlue() * scaleFactor), 0), 255),
        alpha);
  }

  public static void glColor(final int hex) {
    float a = (hex >> 24 & 0xFF) / 255.0F;
    final float r = (hex >> 16 & 0xFF) / 255.0F;
    final float g = (hex >> 8 & 0xFF) / 255.0F;
    final float b = (hex & 0xFF) / 255.0F;
    if (a == 0.0F) a = 1.0F;
    org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
    net.minecraft.client.renderer.GlStateManager.color(r, g, b, a);
  }

  public static void glColor(final Color color) {
    float r = color.getRed() / 255.0F;
    float g = color.getGreen() / 255.0F;
    float b = color.getBlue() / 255.0F;
    float a = color.getAlpha() / 255.0F;
    org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
    net.minecraft.client.renderer.GlStateManager.color(r, g, b, a);
  }

  public static Color darker(Color color, float factor) {
    return ColorUtil.scale(color, factor, color.getAlpha());
  }

  public static Color brighter(final Color color, final float factor) {
    int red = color.getRed();
    int green = color.getGreen();
    int blue = color.getBlue();
    final int alpha = color.getAlpha();

    final int i = (int) (1 / (1 - factor));
    if (red == 0 && green == 0 && blue == 0) {
      return new Color(i, i, i, alpha);
    }

    if (red > 0 && red < i) red = i;
    if (green > 0 && green < i) green = i;
    if (blue > 0 && blue < i) blue = i;

    return new Color(
        Math.min((int) (red / factor), 255),
        Math.min((int) (green / factor), 255),
        Math.min((int) (blue / factor), 255),
        alpha);
  }

  public static Color withRed(final Color color, final int red) {
    return new Color(red, color.getGreen(), color.getBlue());
  }

  public static Color withGreen(final Color color, final int green) {
    return new Color(color.getRed(), green, color.getBlue());
  }

  public static Color withBlue(final Color color, final int blue) {
    return new Color(color.getRed(), color.getGreen(), blue);
  }

  public static Color withAlpha(final Color color, final int alpha) {
    if (alpha == color.getAlpha()) return color;
    int clampedAlpha = Math.min(255, Math.max(0, alpha));
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampedAlpha);
  }

  public static Color mixColors(final Color color1, final Color color2, final double percent) {
    final double inverse_percent = 1.0 - percent;
    final int redPart = (int) (color1.getRed() * percent + color2.getRed() * inverse_percent);
    final int greenPart = (int) (color1.getGreen() * percent + color2.getGreen() * inverse_percent);
    final int bluePart = (int) (color1.getBlue() * percent + color2.getBlue() * inverse_percent);
    return new Color(redPart, greenPart, bluePart);
  }

  public static double getBlendFactor(Vector2d screenCoordinates) {
    return Math.sin(
                System.currentTimeMillis() / 600.0D
                    + screenCoordinates.getX() * 0.005D
                    + screenCoordinates.getY() * 0.06D)
            * 0.5D
        + 0.5D;
  }

  public static Color rainbow(int delay) {
    double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 10.0);
    rainbowState %= 360;
    return Color.getHSBColor((float) (rainbowState / 360.0f), 0.6f, 1f);
  }

  public static void drawInterpolatedText(
      final Font font, final String text, final double x, final double y, final boolean shadow) {
    float w = 0;
    Themes theme = Themes.getCurrentTheme();
    for (int i = 0; i < text.length(); i++) {
      final String character = String.valueOf(text.charAt(i));
      final Color color =
          ColorUtil.mixColors(
              theme.getFirstColor(), theme.getSecondColor(), Math.sin(i * 0.095) * 0.5D + 0.5D);

      if (shadow) {
        font.drawWithShadow(character, x + w, y, color.getRGB());
      } else {
        font.draw(character, x + w, y, color.getRGB());
      }

      w += font.width(character) + 0.5f;
    }
  }

  public static void drawInterpolatedText(
      final Font font, final String text, final double x, final double y) {
    drawInterpolatedText(font, text, x, y, true);
  }

  public static int getChroma(long speed, long... delay) {
    long time = System.currentTimeMillis() + (delay.length > 0 ? delay[0] : 0L);
    return Color.getHSBColor(
            (float) (time % (15000L / speed)) / (15000.0F / (float) speed), 1.0F, 1.0F)
        .getRGB();
  }

  public static float drawThemeString(String text, float x, float y, boolean shadow) {
    miau.module.modules.render.HUD hud = null;
    if (miau.Miau.moduleManager != null) {
      hud =
          (miau.module.modules.render.HUD)
              miau.Miau.moduleManager.modules.get(miau.module.modules.render.HUD.class);
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
    float currentX = x;

    for (int i = 0; i < text.length(); i++) {
      String character = String.valueOf(text.charAt(i));
      int color;
      if (hud != null && hud.isEnabled()) {
        color = hud.getColor(System.currentTimeMillis(), i).getRGB();
      } else {
        Themes theme = Themes.getCurrentTheme();
        color = theme.getAccentColor(new Vector2d(0, i * 15)).getRGB();
      }

      if (shadow) {
        mc.fontRendererObj.drawStringWithShadow(character, currentX, y, color);
      } else {
        mc.fontRendererObj.drawString(character, currentX, y, color, false);
      }
      currentX += mc.fontRendererObj.getStringWidth(character);
    }
    return currentX;
  }

  public static String getClosestVanillaColor(java.awt.Color color) {
    int[] colors = {
      0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
      0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
      0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
      0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    String[] codes = {
      "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"
    };

    int closest = 0;
    double minDistance = Double.MAX_VALUE;

    for (int i = 0; i < colors.length; i++) {
      int r = (colors[i] >> 16) & 0xFF;
      int g = (colors[i] >> 8) & 0xFF;
      int b = colors[i] & 0xFF;

      double distance =
          Math.pow(color.getRed() - r, 2)
              + Math.pow(color.getGreen() - g, 2)
              + Math.pow(color.getBlue() - b, 2);
      if (distance < minDistance) {
        minDistance = distance;
        closest = i;
      }
    }
    return "§" + codes[closest];
  }

  public static String getThemedName(String name) {
    StringBuilder sb = new StringBuilder();
    miau.module.modules.render.HUD hud = null;
    if (miau.Miau.moduleManager != null) {
      hud =
          (miau.module.modules.render.HUD)
              miau.Miau.moduleManager.modules.get(miau.module.modules.render.HUD.class);
    }

    for (int i = 0; i < name.length(); i++) {
      java.awt.Color c;
      if (hud != null && hud.isEnabled()) {
        c = hud.getColor(System.currentTimeMillis(), i);
      } else {
        miau.util.render.Themes theme = Themes.getCurrentTheme();
        c = theme.getAccentColor(new miau.util.vector.Vector2d(0, i * 15));
      }
      sb.append(getClosestVanillaColor(c)).append(name.charAt(i));
    }
    return sb.toString();
  }

  public static int getColor(int red, int green, int blue, int alpha) {
    return (alpha << 24) | (red << 16) | (green << 8) | blue;
  }

  public static int getColor(int red, int green, int blue) {
    return getColor(red, green, blue, 255);
  }

  public static int[] getRGBA(int color) {
    return new int[] {
      (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF
    };
  }
}
