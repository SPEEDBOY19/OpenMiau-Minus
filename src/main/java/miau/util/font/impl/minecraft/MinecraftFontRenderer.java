package miau.util.font.impl.minecraft;

import java.awt.Color;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.font.Font;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public class MinecraftFontRenderer extends Font {
  private final FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;

  @Override
  public int draw(String text, double x, double y, int color, boolean dropShadow) {
    return fr.drawString(text, (float) x, (float) y, color, dropShadow);
  }

  @Override
  public int draw(String text, double x, double y, int color) {
    return fr.drawString(text, (float) x, (float) y, color, true);
  }

  @Override
  public int drawWithShadow(String text, double x, double y, int color) {
    return fr.drawStringWithShadow(text, (float) x, (float) y, color);
  }

  @Override
  public int width(String text) {
    return fr.getStringWidth(text);
  }

  @Override
  public int drawCentered(String text, double x, double y, int color) {
    return fr.drawString(
        text, (float) (x - fr.getStringWidth(text) / 2.0), (float) y, color, false);
  }

  @Override
  public int drawRight(String text, double x, double y, int color) {
    return fr.drawString(text, (float) (x - fr.getStringWidth(text)), (float) y, color, false);
  }

  @Override
  public float height() {
    return fr.FONT_HEIGHT;
  }

  @Override
  public void drawCharacter(char character, int x, int y, Color color) {
    fr.drawStringWithShadow(String.valueOf(character), x, y, color.getRGB());
  }
}
