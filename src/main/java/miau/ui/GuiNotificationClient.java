package miau.ui;

import java.awt.Desktop;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class GuiNotificationClient extends GuiScreen {

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

  private static final int PANEL_RADIUS  = 12;
  private static final int BUTTON_RADIUS = 7;

  private final GuiScreen parent;
  private final String title;
  private final String desc;
  private final String btn1Text;
  private final String btn1Link;
  private final String btn2Text;
  private final String btn2Link;

  public GuiNotificationClient(
      GuiScreen parent, String title, String desc, String btn1Text, String btn1Link, String btn2Text, String btn2Link) {
    this.parent = parent;
    this.title = title;
    this.desc = desc;
    this.btn1Text = btn1Text;
    this.btn1Link = btn1Link;
    this.btn2Text = btn2Text;
    this.btn2Link = btn2Link;
  }

  @Override
  public void initGui() {
    this.buttonList.clear();
    int centerX = this.width / 2;
    int centerY = this.height / 2;

    int y = centerY + 52;

    if (btn2Text != null && !btn2Text.isEmpty()) {
      this.buttonList.add(new StyledButton(0, centerX - 121, y, 116, 24, btn1Text, true));
      this.buttonList.add(new StyledButton(1, centerX + 5, y, 116, 24, btn2Text, false));
    } else {
      this.buttonList.add(new StyledButton(0, centerX - 58, y, 116, 24, btn1Text, true));
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button.id == 0) {
      open(btn1Link);
    } else if (button.id == 1) {
      open(btn2Link);
    }
  }

  private void open(String link) {
    if (link != null && !link.isEmpty()) {
      try {
        Desktop.getDesktop().browse(new URI(link));
      } catch (Exception ignored) { }
    } else {
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

    drawScaledString(this.title, contentX, y, 1.1f, TEXT_1, true);

    y += 17;
    drawRect(left + 16, y, right - 16, y + 1, DIVIDER);
    y += 14;

    this.fontRendererObj.drawSplitString(this.desc, contentX, y, panelWidth - 32, TEXT_2);

    y += 40;
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