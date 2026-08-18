package miau.ui.clickgui.components.impl;

import java.awt.Color;
import miau.ui.clickgui.components.Component;
import miau.util.font.Font;
import miau.util.math.MathUtil;
import miau.util.render.ColorUtil;
import miau.util.render.RenderUtil;
import miau.util.render.Themes;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class ThemeSelectComponent extends Component {
  private final CategoryComponent categoryComponent;
  private final Themes theme;
  public float o, x, y;
  private boolean hovered = false;

  private float hoverAnim = 0f;
  private float selectAnim = 0f;
  private long lastMS = System.currentTimeMillis();

  public ThemeSelectComponent(CategoryComponent categoryComponent, float o, Themes theme) {
    this.categoryComponent = categoryComponent;
    this.theme = theme;
    this.o = o;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    this.x = this.categoryComponent.getX();
    this.y = this.categoryComponent.getModuleY() + this.o;
    this.hovered =
        mouseX > this.x
            && mouseX < this.x + this.categoryComponent.getWidth()
            && mouseY > this.y
            && mouseY < this.y + this.getHeightF();
  }

  @Override
  public void render() {
    long currentMS = System.currentTimeMillis();
    float delta = currentMS - lastMS;
    lastMS = currentMS;
    if (delta > 50 || delta < 0) delta = 16;

    boolean isActive = Themes.getCurrentTheme() == this.theme;

    hoverAnim = MathUtil.lerp(hoverAnim, hovered ? 1f : 0f, 0.03f * delta);
    selectAnim = MathUtil.lerp(selectAnim, isActive ? 1f : 0f, 0.03f * delta);

    float cx = this.categoryComponent.getX() + 4;
    float cy = this.categoryComponent.getY() + this.o + 2;
    float w = this.categoryComponent.getWidth() - 8;
    float h = 42;
    float gradientH = 22 + (hoverAnim * 20);

    int bgAlpha = (int) (150 + hoverAnim * 50);
    int bgColor = new Color(18, 21, 30, bgAlpha).getRGB();
    RenderUtil.drawRoundedRectangle(cx, cy, cx + w, cy + h, 6, bgColor);

    Color c1 =
        ColorUtil.interpolate(
            hoverAnim, this.theme.getFirstColor(), this.theme.getFirstColor().brighter());
    Color c2 =
        ColorUtil.interpolate(
            hoverAnim, this.theme.getSecondColor(), this.theme.getSecondColor().brighter());

    long offsetMS = (currentMS / 15L) % 360L;
    if (hoverAnim > 0.01f) {
      float hue = (offsetMS % 360L) / 360f;
      Color popC1 = Color.getHSBColor(hue, 0.8f, 1.0f);
      Color popC2 = Color.getHSBColor((hue + 0.5f) % 1.0f, 0.8f, 1.0f);
      c1 = ColorUtil.interpolate(hoverAnim, c1, popC1);
      c2 = ColorUtil.interpolate(hoverAnim, c2, popC2);
    }

    miau.util.shader.RoundedUtils.drawGradientHorizontal(cx, cy, w, gradientH, 6, c1, c2);

    Font font = miau.util.font.FontRepository.getHudFont(18);
    Color textColor = ColorUtil.interpolate(selectAnim, Color.WHITE, this.theme.getFirstColor());
    font.drawCentered(
        this.theme.getThemeName(),
        cx + w / 2.0f,
        cy + 22 + 8 + (hoverAnim * 2),
        textColor.getRGB());
  }

  @Override
  public boolean onClick(int mouseX, int mouseY, int mouseButton) {
    if (mouseButton == 0 && this.hovered && this.categoryComponent.opened) {
      miau.util.render.Themes.setCurrentTheme(this.theme);
      return true;
    }
    return false;
  }

  private void drawHorizontalGradient(
      float left, float top, float right, float bottom, int startColor, int endColor) {
    float f = (float) (startColor >> 24 & 255) / 255.0F;
    float f1 = (float) (startColor >> 16 & 255) / 255.0F;
    float f2 = (float) (startColor >> 8 & 255) / 255.0F;
    float f3 = (float) (startColor & 255) / 255.0F;
    float f4 = (float) (endColor >> 24 & 255) / 255.0F;
    float f5 = (float) (endColor >> 16 & 255) / 255.0F;
    float f6 = (float) (endColor >> 8 & 255) / 255.0F;
    float f7 = (float) (endColor & 255) / 255.0F;

    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.disableAlpha();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.shadeModel(GL11.GL_SMOOTH);

    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
    worldrenderer.pos(left, top, 0.0D).color(f1, f2, f3, f).endVertex();
    worldrenderer.pos(left, bottom, 0.0D).color(f1, f2, f3, f).endVertex();
    worldrenderer.pos(right, bottom, 0.0D).color(f5, f6, f7, f4).endVertex();
    worldrenderer.pos(right, top, 0.0D).color(f5, f6, f7, f4).endVertex();
    tessellator.draw();

    GlStateManager.shadeModel(GL11.GL_FLAT);
    GlStateManager.disableBlend();
    GlStateManager.enableAlpha();
    GlStateManager.enableTexture2D();
  }

  @Override
  public void updateHeight(float n) {
    this.o = n;
  }

  @Override
  public float getHeightF() {
    return 46f + (hoverAnim * 12f);
  }

  @Override
  public float getOffset() {
    return this.o;
  }
}
