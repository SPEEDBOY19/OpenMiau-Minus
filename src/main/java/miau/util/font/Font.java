package miau.util.font;

import java.awt.Color;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;

public abstract class Font {
  public abstract int draw(String text, double x, double y, int color, boolean dropShadow);

  public abstract int draw(final String text, final double x, final double y, final int color);

  public abstract int drawWithShadow(
      final String text, final double x, final double y, final int color);

  public abstract int width(String text);

  public abstract int drawCentered(
      final String text, final double x, final double y, final int color);

  public abstract int drawRight(final String text, final double x, final double y, final int color);

  public abstract float height();

  public abstract void drawCharacter(
      final char character, final int x, final int y, final Color color);

  public int getStringWidth(String text) {
    return width(text);
  }

  public int getFontHeight() {
    return (int) height();
  }

  public int getTextTopOffset() {
    return 0;
  }

  public int getTextBottomOffset() {
    return (int) height();
  }
}
