package miau.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class GuiUpdateClient extends GuiScreen {

  private static final int BG_OVERLAY   = 0xB0000000;
  private static final int PANEL_BG     = 0xE8161620;
  private static final int PANEL_BORDER = 0x40FFFFFF;
  private static final int DIVIDER      = 0x14FFFFFF;
  private static final int DOT_GRID     = 0x14FFFFFF;

  private static final int TEXT_1 = 0xFFF2F2F6;
  private static final int TEXT_2 = 0xFF9C9CB0;
  private static final int TEXT_3 = 0xFF6B6B80;

  private static final int PINK      = 0xFFF6548A;
  private static final int PINK_SOFT = 0xFFE23F79;
  private static final int PINK_GLOW = 0x40F6548A;
  private static final int CORAL     = 0xFFFB8A63;

  private static final int PANEL_RADIUS  = 12;
  private static final int BUTTON_RADIUS = 7;

  private final GuiScreen parent;
  private final String currentVersion;
  private final String latestVersion;
  private final String updateUrl;

  public GuiUpdateClient(
      GuiScreen parent, String currentVersion, String latestVersion, String updateUrl) {
    this.parent = parent;
    this.currentVersion = currentVersion;
    this.latestVersion = latestVersion;
    this.updateUrl = updateUrl;
  }

  @Override
  public void setWorldAndResolution(Minecraft mc, int width, int height) {
    if (this.parent != null) {
      this.parent.setWorldAndResolution(mc, width, height);
    }
    super.setWorldAndResolution(mc, width, height);
  }

  @Override
  public void initGui() {
    this.buttonList.clear();
    int centerX = this.width / 2;
    int centerY = this.height / 2;

    this.buttonList.add(
        new StyledButton(0, centerX - 121, centerY + 52, 116, 24, "Update", true));
    this.buttonList.add(
        new StyledButton(1, centerX + 5, centerY + 52, 116, 24, "Dismiss", false));
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException {
    if (button.id == 0) {
      try {
        Desktop.getDesktop().browse(new URI(this.updateUrl));
      } catch (Exception ignored) {
      }
    } else if (button.id == 1) {
      this.mc.displayGuiScreen(this.parent);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    if (this.parent != null) {
      this.parent.drawScreen(0, 0, partialTicks);
    } else {
      this.drawDefaultBackground();
    }

    drawRect(0, 0, this.width, this.height, BG_OVERLAY);
    drawDotGrid();

    int centerX = this.width / 2;
    int centerY = this.height / 2;

    int panelWidth = 246;
    int panelHeight = 156;
    int left = centerX - panelWidth / 2;
    int top = centerY - panelHeight / 2 - 8;
    int right = centerX + panelWidth / 2;
    int bottom = centerY + panelHeight / 2 + 8;

    drawPanel(left, top, right, bottom, PANEL_RADIUS);

    int contentX = left + 16;
    int y = top + 16;

    drawScaledString("Client Outdated", contentX, y, 1.1f, TEXT_1, true);
    drawPill(right - 16 - 50, top + 12, 50, 12, "UPDATE", PINK);

    y += 17;
    drawRect(left + 16, y, right - 16, y + 1, DIVIDER);
    y += 14;

    y = drawVersionRow(contentX, y, right - 16, CORAL, "Current version", currentVersion, TEXT_3);
    y += 6;
    y = drawVersionRow(contentX, y, right - 16, PINK, "Latest version", latestVersion, PINK);

    y += 8;
    drawRect(left + 16, y, right - 16, y + 1, DIVIDER);

    super.drawScreen(mouseX, mouseY, partialTicks);
  }


  private void drawPanel(int left, int top, int right, int bottom, int radius) {
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    drawRoundedRect(left - 1, top - 1, right + 1, bottom + 1, radius + 1, PANEL_BORDER);
    drawRoundedRect(left, top, right, bottom, radius, PANEL_BG);
    GlStateManager.disableBlend();
  }

  private void drawDotGrid() {
    int step = 26;
    for (int x = 0; x < this.width; x += step) {
      for (int y = 0; y < this.height; y += step) {
        drawRect(x, y, x + 1, y + 1, DOT_GRID);
      }
    }
  }

  private void drawPill(int x, int y, int w, int h, String text, int accent) {
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    int radius = h / 2;
    drawRoundedRect(x, y, x + w, y + h, radius, PANEL_BORDER);
    drawRoundedRect(x + 1, y + 1, x + w - 1, y + h - 1, Math.max(0, radius - 1),
        (accent & 0x00FFFFFF) | 0x22000000);
    GlStateManager.disableBlend();

    int strW = this.fontRendererObj.getStringWidth(text);
    this.fontRendererObj.drawString(text, x + (w - strW) / 2, y + (h - 8) / 2, accent);
  }

  private int drawVersionRow(
      int x, int y, int rightEdge, int dotColor, String label, String value, int valueColor) {
    drawRoundedRect(x, y + 3, x + 5, y + 8, 2, dotColor);
    drawString(this.fontRendererObj, label, x + 11, y, TEXT_2);
    int valW = this.fontRendererObj.getStringWidth(value);
    drawString(this.fontRendererObj, value, rightEdge - valW, y, valueColor);
    return y + 13;
  }

  private void drawScaledString(String text, int x, int y, float scale, int color, boolean bold) {
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0);
    GlStateManager.scale(scale, scale, 1f);
    this.fontRendererObj.drawString(bold ? "\u00a7l" + text : text, 0, 0, color);
    GlStateManager.popMatrix();
  }

  private static void drawRoundedRect(int left, int top, int right, int bottom, int radius, int color) {
    drawRoundedGradientRect(left, top, right, bottom, radius, color, color);
  }

  private static void drawRoundedGradientRect(int left, int top, int right, int bottom, int radius, int colorTop, int colorBottom) {
    int height = bottom - top;
    int width = right - left;
    int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

    float aTop = (colorTop >> 24 & 0xFF) / 255f, aBot = (colorBottom >> 24 & 0xFF) / 255f;
    float rTop = (colorTop >> 16 & 0xFF) / 255f, rBot = (colorBottom >> 16 & 0xFF) / 255f;
    float gTop = (colorTop >> 8 & 0xFF) / 255f,  gBot = (colorBottom >> 8 & 0xFF) / 255f;
    float bTop = (colorTop & 0xFF) / 255f,       bBot = (colorBottom & 0xFF) / 255f;

    for (int i = 0; i < height; i++) {
      float t = height <= 1 ? 0f : (float) i / (height - 1);
      int a  = (int) ((aTop + (aBot - aTop) * t) * 255f);
      int rr = (int) ((rTop + (rBot - rTop) * t) * 255f);
      int gg = (int) ((gTop + (gBot - gTop) * t) * 255f);
      int bb = (int) ((bTop + (bBot - bTop) * t) * 255f);
      int color = (a << 24) | (rr << 16) | (gg << 8) | bb;

      int inset = 0;
      int distFromEdge = Math.min(i, height - 1 - i);
      if (distFromEdge < r) {
        int dy = r - distFromEdge - 1;
        inset = r - (int) Math.sqrt(Math.max(0, r * r - dy * dy));
      }
      drawRect(left + inset, top + i, right - inset, top + i + 1, color);
    }
  }

  private static class StyledButton extends GuiButton {
    private final boolean primary;

    StyledButton(int id, int x, int y, int w, int h, String text, boolean primary) {
      super(id, x, y, w, h, text);
      this.primary = primary;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
      if (!this.visible) return;
      this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
          && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

      GlStateManager.enableBlend();
      GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

      if (primary && hovered) {
        drawRoundedRect(xPosition - 3, yPosition - 3, xPosition + width + 3, yPosition + height + 3,
            BUTTON_RADIUS + 3, PINK_GLOW);
      }

      if (primary) {
        int top = hovered ? PINK : PINK_SOFT;
        int bottom = hovered ? PINK_SOFT : PINK;
        drawRoundedGradientRect(xPosition, yPosition, xPosition + width, yPosition + height, BUTTON_RADIUS, top, bottom);
      } else {
        int border = hovered ? 0x50FFFFFF : 0x30FFFFFF;
        int fill = hovered ? 0x1AFFFFFF : 0x10FFFFFF;
        drawRoundedRect(xPosition, yPosition, xPosition + width, yPosition + height, BUTTON_RADIUS, border);
        drawRoundedRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1,
            Math.max(0, BUTTON_RADIUS - 1), fill);
      }

      int textColor = primary ? 0xFFFFFFFF : (hovered ? TEXT_1 : TEXT_2);
      int strWidth = mc.fontRendererObj.getStringWidth(this.displayString);
      mc.fontRendererObj.drawString(this.displayString,
          xPosition + (width - strWidth) / 2, yPosition + (height - 8) / 2, textColor);

      GlStateManager.disableBlend();
    }
  }
}