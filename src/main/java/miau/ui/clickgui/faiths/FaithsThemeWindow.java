package miau.ui.clickgui.faiths;

import java.awt.Color;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import miau.util.render.Themes;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class FaithsThemeWindow {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private float x, y;
  private int prevMouseX, prevMouseY;
  private boolean leftMouseClicked = false, rightMouseClicked = false, expand = true;
  private boolean dragging = false;
  private float scrollY = 0F, targetScrollY = 0F;
  private float lastRenderHeight = 200F;

  private static final int PANEL_WIDTH = 100;
  private static final int TITLE_HEIGHT = 13;
  private static final int ITEM_HEIGHT = 11;
  private static final Color BG_COLOR = new Color(25, 25, 25);
  private static final Color ITEM_BG = new Color(36, 36, 36);

  public FaithsThemeWindow(float x, float y) {
    this.x = x;
    this.y = y;
  }

  private boolean mouseHovered(
      float x, float y, float width, float height, int mouseX, int mouseY) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }

  public boolean onScroll(int dWheel, int mouseX, int mouseY) {
    if (mouseHovered(x, y, PANEL_WIDTH, lastRenderHeight, mouseX, mouseY)) {
      if (dWheel > 0) {
        targetScrollY += 25F;
      } else if (dWheel < 0) {
        targetScrollY -= 25F;
      }
      return true;
    }
    return false;
  }

  private void scissor(double x, double y, double width, double height) {
    net.minecraft.client.gui.ScaledResolution sr =
        new net.minecraft.client.gui.ScaledResolution(mc);
    final double scale = sr.getScaleFactor();
    y = sr.getScaledHeight() - y;
    x *= scale;
    y *= scale;
    width *= scale;
    height *= scale;
    GL11.glScissor((int) x, (int) (y - height), (int) width, (int) height);
  }

  protected void renderWindow(int mouseX, int mouseY) {
    if (Mouse.isButtonDown(0)) {
      if (dragging) {
        this.x += mouseX - prevMouseX;
        this.y += mouseY - prevMouseY;
      } else if (mouseHovered(x, y, PANEL_WIDTH, TITLE_HEIGHT, mouseX, mouseY)) {
        dragging = true;
      }
    } else {
      dragging = false;
    }
    prevMouseX = mouseX;
    prevMouseY = mouseY;

    GL11.glPushMatrix();
    GL11.glTranslatef(x, y, 0);

    Themes[] themesList = Themes.values();
    float height = 15F;
    if (expand) {
      height += themesList.length * ITEM_HEIGHT;
    }

    net.minecraft.client.gui.ScaledResolution sr =
        new net.minecraft.client.gui.ScaledResolution(mc);
    float maxWindowHeight = Math.min(220F, Math.max(100F, sr.getScaledHeight() - y - 10F));
    float renderHeight = Math.min(height, maxWindowHeight);
    lastRenderHeight = renderHeight;

    float maxScroll = Math.min(0F, -(height - renderHeight));
    if (targetScrollY > 0F) targetScrollY = 0F;
    if (targetScrollY < maxScroll) targetScrollY = maxScroll;
    scrollY = miau.util.math.MathUtil.lerp(scrollY, targetScrollY, 0.2f);
    if (scrollY > 0F) scrollY = 0F;
    if (scrollY < maxScroll) scrollY = maxScroll;

    Color themeAccent = Themes.getCurrentTheme().getAccentColor(new Vector2d(x, y));
    RenderUtil.drawOutLineRect(0F, 0F, PANEL_WIDTH, renderHeight, 1F, BG_COLOR, themeAccent);

    Font titleFont = FontRepository.getHudFont(15);
    titleFont.draw("themes", 5, 3, -1);

    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    scissor(x, y + TITLE_HEIGHT, PANEL_WIDTH, renderHeight - TITLE_HEIGHT);

    float itemY = 11F + scrollY;
    if (expand) {
      for (Themes theme : themesList) {
        boolean selected = Themes.getCurrentTheme() == theme;
        Color c1 = Themes.getCurrentTheme().getAccentColor(new Vector2d(x, y + itemY));
        Color c2 =
            Themes.getCurrentTheme()
                .getAccentColor(new Vector2d(x + PANEL_WIDTH, y + itemY + ITEM_HEIGHT));

        RenderUtil.drawRect(3, itemY, 3 + PANEL_WIDTH - 5, itemY + ITEM_HEIGHT, ITEM_BG.getRGB());
        if (selected) {
          RenderUtil.drawHorizontalGradientRect(
              3, itemY, 3 + PANEL_WIDTH - 5, itemY + ITEM_HEIGHT, c1.getRGB(), c2.getRGB());
        }

        if (mouseHovered(x, y + itemY, PANEL_WIDTH, ITEM_HEIGHT, mouseX, mouseY)) {
          if (!selected) {
            RenderUtil.drawRect(
                3,
                itemY,
                3 + PANEL_WIDTH - 5,
                itemY + ITEM_HEIGHT,
                new Color(255, 255, 255, 50).getRGB());
          }
          if (Mouse.isButtonDown(0)) {
            if (!leftMouseClicked) {
              Themes.setCurrentTheme(theme);
              leftMouseClicked = true;
            }
          } else {
            leftMouseClicked = false;
          }
        }

        Font font = FontRepository.getHudFont(13);
        int textColor =
            selected ? RenderUtil.getContrastTextColor(c1) : new Color(160, 160, 160).getRGB();
        font.draw(
            theme.getThemeName().toLowerCase(),
            PANEL_WIDTH - 3 - font.width(theme.getThemeName().toLowerCase()),
            itemY + 2,
            textColor);

        itemY += ITEM_HEIGHT;
      }
    }

    GL11.glDisable(GL11.GL_SCISSOR_TEST);

    if (mouseHovered(x, y, PANEL_WIDTH, TITLE_HEIGHT, mouseX, mouseY)) {
      if (Mouse.isButtonDown(1)) {
        if (!rightMouseClicked) {
          rightMouseClicked = true;
          expand = !expand;
        }
      } else {
        rightMouseClicked = false;
      }
    }

    GL11.glPopMatrix();
  }

  public void mouseReleased(int mouseX, int mouseY, int state) {
    dragging = false;
  }
}
