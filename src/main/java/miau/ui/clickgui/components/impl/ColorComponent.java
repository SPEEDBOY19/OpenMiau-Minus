package miau.ui.clickgui.components.impl;

import java.awt.Color;
import miau.property.properties.ColorProperty;
import miau.ui.clickgui.components.Component;
import miau.util.animation.AnimationTimer;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import org.lwjgl.opengl.GL11;

public class ColorComponent extends Component {
  public ColorProperty property;
  private ModuleComponent moduleComponent;
  public float o;
  public float x;
  private float y;
  public float xOffset;
  public boolean expanded;
  private int dragMode;
  private float cachedHue;
  private float cachedSat;
  private float cachedBri;

  private AnimationTimer smoothTimer;
  private float animationProgress;
  private float animationStartProgress;
  private float animationTargetProgress;
  private static final float ANIMATION_DURATION = 250f;

  private static final float LABEL_HEIGHT = 12f;
  private static final float SQUARE_SIZE = 50f;
  private static final float HUE_BAR_WIDTH = 10f;
  private static final float HUE_GAP = 4f;
  private static final float BLACK_BRI_EPSILON = 0.001f;
  private static final float GREY_SAT_EPSILON = 0.001f;
  private static final float SQUARE_TOP_PAD = 2f;
  private static final float BOTTOM_PAD = 2f;
  private static final int HUE_STEPS = 20;
  private static final float PREVIEW_BOX_SIZE = 5f;

  public ColorComponent(ColorProperty property, ModuleComponent moduleComponent, float o) {
    this.property = property;
    this.moduleComponent = moduleComponent;
    this.o = o;
    this.animationProgress = 0f;
    this.animationStartProgress = 0f;
    this.animationTargetProgress = 0f;
  }

  public boolean hasAlpha() {
    return false;
  }

  public int getColorRGB() {
    return property.getValue() | 0xFF000000;
  }

  public float getHue() {
    int rgb = property.getValue();
    float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
    return hsb[0] * 360f;
  }

  public float getSaturation() {
    int rgb = property.getValue();
    float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
    return hsb[1];
  }

  public float getBrightness() {
    int rgb = property.getValue();
    float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
    return hsb[2];
  }

  public void setFromHSB(float h, float s, float b) {
    int rgb = Color.HSBtoRGB(h / 360f, s, b) & 0xFFFFFF;
    property.setValue(rgb);
  }

  public float getExpandedHeight() {
    return LABEL_HEIGHT + SQUARE_TOP_PAD + SQUARE_SIZE + BOTTOM_PAD;
  }

  public float getAnimationProgress() {
    if (smoothTimer != null) {
      if (System.currentTimeMillis() - smoothTimer.last >= ANIMATION_DURATION + 30) {
        smoothTimer = null;
        animationProgress = animationTargetProgress;
        animationStartProgress = animationTargetProgress;
      } else {
        animationProgress =
            smoothTimer.getValueFloat(animationStartProgress, animationTargetProgress, 1);
        if (animationProgress == animationTargetProgress) {
          smoothTimer = null;
          animationStartProgress = animationTargetProgress;
        }
      }
    }
    return animationProgress;
  }

  @Override
  public void render() {
    float cx = moduleComponent.categoryComponent.getX();
    float cy = moduleComponent.categoryComponent.getY();
    float cw = moduleComponent.categoryComponent.getWidth();

    float boxX = cx + 4 + (xOffset / 2);
    float boxY = cy + o + 3f;
    RenderUtil.drawRect(
        boxX - 0.5,
        boxY - 0.5,
        boxX + PREVIEW_BOX_SIZE + 0.5,
        boxY + PREVIEW_BOX_SIZE + 0.5,
        0xFF3C3C46);
    RenderUtil.drawRect(
        boxX, boxY, boxX + PREVIEW_BOX_SIZE, boxY + PREVIEW_BOX_SIZE, getColorRGB());

    Font renderer = FontRepository.getMinecraftFont();
    GL11.glPushMatrix();
    GL11.glScaled(0.5, 0.5, 0.5);
    float textOffset = renderer.getStringWidth("[+]  ");
    renderer.draw(
        property.getName(), (cx + 4) * 2 + xOffset + textOffset, (cy + o + 4) * 2, -1, true);
    GL11.glPopMatrix();

    float progress = getAnimationProgress();
    if (progress <= 0f) return;

    float scrollOffset = moduleComponent.categoryComponent.moduleY - cy;
    float contentTopScreen = cy + o + LABEL_HEIGHT + scrollOffset;
    float revealH = (getExpandedHeight() - LABEL_HEIGHT) * progress;
    RenderUtil.scissorPushGui(cx, contentTopScreen, cw, revealH);
    renderPickerContent(cx, cy);
    RenderUtil.scissorPop();
  }

  private void renderPickerContent(float cx, float cy) {
    float areaLeft = cx + 4 + (xOffset / 2);
    float sqTop = cy + o + LABEL_HEIGHT + SQUARE_TOP_PAD;
    float sqRight = areaLeft + SQUARE_SIZE;
    float sqBottom = sqTop + SQUARE_SIZE;

    float bri = (dragMode != 0) ? cachedBri : getBrightness();
    float satFromSetting = (dragMode != 0) ? cachedSat : getSaturation();
    boolean isBlack = bri < BLACK_BRI_EPSILON;
    boolean isGrey = satFromSetting < GREY_SAT_EPSILON;
    if (dragMode == 0 && !isBlack) {
      cachedBri = bri;
      cachedSat = getSaturation();
      if (!isGrey) {
        cachedHue = getHue();
      }
    }
    boolean useCachedHue = dragMode != 0 || isBlack || isGrey;
    float hue = useCachedHue ? cachedHue / 360f : getHue() / 360f;
    float sat = (dragMode != 0 || isBlack) ? cachedSat : satFromSetting;

    int hueRGB = Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
    RenderUtil.drawRect(areaLeft, sqTop, sqRight, sqBottom, hueRGB);
    RenderUtil.drawHorizontalGradientRect(
        areaLeft, sqTop, sqRight, sqBottom, 0xFFFFFFFF, 0x00FFFFFF);
    RenderUtil.drawVerticalGradientRect(areaLeft, sqTop, sqRight, sqBottom, 0x00000000, 0xFF000000);

    RenderUtil.drawOutline(areaLeft - 1, sqTop - 1, sqRight + 1, sqBottom + 1, 1f, 0xFF3C3C46);

    float indX = areaLeft + sat * SQUARE_SIZE;
    float indY = sqTop + (1f - bri) * SQUARE_SIZE;
    RenderUtil.drawRect(indX - 2, indY, indX + 3, indY + 1, 0xFFFFFFFF);
    RenderUtil.drawRect(indX, indY - 2, indX + 1, indY + 3, 0xFFFFFFFF);

    float hueLeft = sqRight + HUE_GAP;
    float hueRight = hueLeft + HUE_BAR_WIDTH;
    float stepH = SQUARE_SIZE / HUE_STEPS;
    for (int i = 0; i < HUE_STEPS; i++) {
      float h1 = (float) i / HUE_STEPS;
      float h2 = (float) (i + 1) / HUE_STEPS;
      int c1 = Color.HSBtoRGB(h1, 1f, 1f) | 0xFF000000;
      int c2 = Color.HSBtoRGB(h2, 1f, 1f) | 0xFF000000;
      RenderUtil.drawVerticalGradientRect(
          hueLeft, sqTop + i * stepH, hueRight, sqTop + (i + 1) * stepH, c1, c2);
    }

    RenderUtil.drawOutline(hueLeft - 1, sqTop - 1, hueRight + 1, sqBottom + 1, 1f, 0xFF3C3C46);

    float hueIndY = sqTop + Math.max(0, Math.min(1, hue)) * SQUARE_SIZE;
    RenderUtil.drawRect(hueLeft - 1, hueIndY - 1, hueRight + 1, hueIndY + 2, 0xFFFFFFFF);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    this.y = moduleComponent.categoryComponent.getModuleY() + this.o;
    this.x = moduleComponent.categoryComponent.getX();

    if (dragMode == 0 || getAnimationProgress() < 1f) return;

    float areaLeft = this.x + 4 + (xOffset / 2);
    float sqTop = this.y + LABEL_HEIGHT + SQUARE_TOP_PAD;
    float sqRight = areaLeft + SQUARE_SIZE;
    float sqBottom = sqTop + SQUARE_SIZE;

    if (dragMode == 1) {
      cachedSat = Math.max(0, Math.min(1, (mouseX - areaLeft) / SQUARE_SIZE));
      cachedBri = Math.max(0, Math.min(1, 1f - (mouseY - sqTop) / SQUARE_SIZE));
      setFromHSB(cachedHue, cachedSat, cachedBri);
    } else if (dragMode == 2) {
      cachedHue = Math.max(0, Math.min(360, (mouseY - sqTop) / SQUARE_SIZE * 360f));
      setFromHSB(cachedHue, cachedSat, cachedBri);
    }
  }

  @Override
  public boolean onClick(int mouseX, int mouseY, int button) {
    if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
      return false;
    }

    float cw = moduleComponent.categoryComponent.getWidth();

    if (mouseX > this.x
        && mouseX < this.x + cw
        && mouseY > this.y
        && mouseY < this.y + LABEL_HEIGHT) {
      if (button == 0 || button == 1) {
        float currentProgress = getAnimationProgress();
        this.animationStartProgress = currentProgress;
        this.expanded = !this.expanded;
        this.animationTargetProgress = this.expanded ? 1f : 0f;
        (this.smoothTimer = new AnimationTimer(ANIMATION_DURATION)).start();
        moduleComponent.updateSettingPositions();
        return true;
      }
    }

    if (button != 0) return false;
    if (getAnimationProgress() < 1f) return false;

    float areaLeft = this.x + 4 + (xOffset / 2);
    float sqTop = this.y + LABEL_HEIGHT + SQUARE_TOP_PAD;
    float sqRight = areaLeft + SQUARE_SIZE;
    float sqBottom = sqTop + SQUARE_SIZE;
    float hueLeft = sqRight + HUE_GAP;
    float hueRight = hueLeft + HUE_BAR_WIDTH;

    if (mouseX >= areaLeft && mouseX <= sqRight && mouseY >= sqTop && mouseY <= sqBottom) {
      cacheHSB();
      dragMode = 1;
      return false;
    }

    if (mouseX >= hueLeft - 2 && mouseX <= hueRight + 2 && mouseY >= sqTop && mouseY <= sqBottom) {
      cacheHSB();
      dragMode = 2;
      return false;
    }

    return false;
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int button) {
    dragMode = 0;
  }

  @Override
  public void onGuiClosed() {
    dragMode = 0;
    smoothTimer = null;
    animationProgress = expanded ? 1f : 0f;
    animationStartProgress = animationProgress;
    animationTargetProgress = animationProgress;
  }

  @Override
  public void updateHeight(float n) {
    this.o = n;
  }

  @Override
  public float getOffset() {
    return this.o;
  }

  @Override
  public boolean isBaseVisible() {
    return property.isVisible();
  }

  public void restoreExpandedState(boolean expanded) {
    this.expanded = expanded;
    this.smoothTimer = null;
    this.animationProgress = expanded ? 1f : 0f;
    this.animationStartProgress = this.animationProgress;
    this.animationTargetProgress = this.animationProgress;
  }

  private void cacheHSB() {
    float bri = getBrightness();
    float sat = getSaturation();
    cachedBri = bri;
    if (bri >= BLACK_BRI_EPSILON) {
      cachedSat = sat;
      if (sat >= GREY_SAT_EPSILON) {
        cachedHue = getHue();
      }
    }
  }
}
