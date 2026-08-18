package miau.ui.clickgui.components.impl;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import miau.property.properties.BooleanProperty;
import miau.ui.clickgui.components.Component;
import miau.util.animation.AnimationTimer;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import org.lwjgl.opengl.GL11;

public class GroupComponent extends Component {
  public static final float GROUP_HEADER_HEIGHT = 14f;
  private static final float SUB_PROP_HEIGHT = 12f;
  private static final long ANIMATION_DURATION = 180L;

  private final String groupName;
  private final ModuleComponent moduleComponent;
  private final List<BooleanProperty> subProperties;

  public float o;
  public float x;
  private float y;
  public float xOffset;

  // Animation state
  private boolean expanded = false;
  private AnimationTimer smoothTimer;
  private float animationProgress = 0f;
  private float animationStart = 0f;
  private float animationTarget = 0f;

  public String getGroupName() {
    return groupName;
  }

  public boolean isExpanded() {
    return expanded;
  }

  public GroupComponent(
      String groupName,
      ModuleComponent moduleComponent,
      float o,
      List<BooleanProperty> subProperties) {
    this.groupName = groupName;
    this.moduleComponent = moduleComponent;
    this.o = o;
    this.subProperties = new ArrayList<>(subProperties);
    this.x =
        moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
    this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
  }

  public int getSubCount() {
    return subProperties.size();
  }

  public float getFullHeight() {
    return GROUP_HEADER_HEIGHT + subProperties.size() * SUB_PROP_HEIGHT;
  }

  public float getAnimationProgress() {
    if (smoothTimer != null) {
      if (System.currentTimeMillis() - smoothTimer.last >= ANIMATION_DURATION + 30) {
        smoothTimer = null;
        animationProgress = animationTarget;
        animationStart = animationTarget;
      } else {
        animationProgress = smoothTimer.getValueFloat(animationStart, animationTarget, 1);
        if (animationProgress == animationTarget) {
          smoothTimer = null;
          animationStart = animationTarget;
        }
      }
    }
    return animationProgress;
  }

  public void setExpanded(boolean expanded) {
    float current = getAnimationProgress();
    this.animationStart = current;
    this.expanded = expanded;
    this.animationTarget = expanded ? 1f : 0f;
    (this.smoothTimer = new AnimationTimer(ANIMATION_DURATION)).start();
    this.moduleComponent.updateSettingPositions();
  }

  public void restoreExpandedState(boolean expanded) {
    this.expanded = expanded;
    this.smoothTimer = null;
    this.animationProgress = expanded ? 1f : 0f;
    this.animationStart = this.animationProgress;
    this.animationTarget = this.animationProgress;
  }

  private void toggleExpanded() {
    setExpanded(!this.expanded);
  }

  @Override
  public void render() {
    float cx = this.moduleComponent.categoryComponent.getX();
    float cy = this.moduleComponent.categoryComponent.getY() + this.o;
    float cw = this.moduleComponent.categoryComponent.getWidth();
    float left = cx + 4 + (xOffset / 2);
    float top = cy + 1;
    float right = cx + cw - 4 + (xOffset / 2);
    float bottom = top + GROUP_HEADER_HEIGHT;

    // Header background
    RenderUtil.drawRoundedRectangle(
        left, top, right, bottom, 4f, new Color(22, 22, 28, 185).getRGB());

    Font font = FontRepository.getMinecraftFont();

    // Title
    GL11.glPushMatrix();
    GL11.glScaled(0.5, 0.5, 0.5);
    font.draw(groupName, (left + 5) * 2, (top + 4) * 2, new Color(200, 200, 215).getRGB(), true);
    GL11.glPopMatrix();

    // Arrow
    float progress = getAnimationProgress();
    float arrowCenterX = right - 10;
    float arrowCenterY = top + GROUP_HEADER_HEIGHT / 2f;
    GL11.glPushMatrix();
    GL11.glTranslatef(arrowCenterX, arrowCenterY, 0f);
    GL11.glRotatef(progress * 180f, 0f, 0f, 1f);
    RenderUtil.drawRect(-3, -1, 0, 2, new Color(220, 220, 228).getRGB());
    RenderUtil.drawRect(0, -1, 3, 2, new Color(220, 220, 228).getRGB());
    GL11.glPopMatrix();

    // Sub-properties
    if (progress > 0.01f) {
      float subTop = top + GROUP_HEADER_HEIGHT;
      float subFullHeight = subProperties.size() * SUB_PROP_HEIGHT;
      float shownHeight = subFullHeight * progress;

      RenderUtil.drawRoundedRectangle(
          left, subTop, right, subTop + shownHeight, 4f, new Color(15, 15, 22, 235).getRGB());

      RenderUtil.scissorPushGui(left, subTop, right - left, shownHeight);

      for (int i = 0; i < subProperties.size(); i++) {
        BooleanProperty prop = subProperties.get(i);
        if (!prop.isVisible()) continue;

        float rowTop = subTop + i * SUB_PROP_HEIGHT;
        String propName = prop.getName().replace("target-", "");

        // Label
        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        font.draw(
            propName, (left + 8) * 2, (rowTop + 3) * 2, new Color(210, 210, 220).getRGB(), true);
        GL11.glPopMatrix();

        // Toggle switch
        boolean enabled = prop.getValue();
        float switchW = 16f;
        float switchH = 8f;
        float switchX = right - switchW - 6 + (xOffset / 2);
        float switchY = rowTop + 2f;

        Color c1 = new Color(40, 40, 40);
        Color c2 = new Color(20, 255, 0);
        float toggleAnim = enabled ? 1f : 0f;
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * toggleAnim);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * toggleAnim);
        int bl = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * toggleAnim);
        int bgColor = new Color(r, g, bl).getRGB();

        RenderUtil.drawRoundedRectangle(
            switchX, switchY, switchX + switchW, switchY + switchH, switchH / 2f, bgColor);

        float circleR = switchH / 2f - 1f;
        float minCircleX = switchX + circleR + 1f;
        float maxCircleX = switchX + switchW - circleR - 1f;
        float circleX = minCircleX + (maxCircleX - minCircleX) * toggleAnim;
        float circleY = switchY + switchH / 2f;

        RenderUtil.drawRoundedRectangle(
            circleX - circleR,
            circleY - circleR,
            circleX + circleR,
            circleY + circleR,
            circleR,
            -1);
      }

      RenderUtil.scissorPop();
    }
  }

  @Override
  public void drawScreen(int x, int y) {
    this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
    this.x = this.moduleComponent.categoryComponent.getX();
  }

  @Override
  public boolean onClick(int x, int y, int button) {
    if (button != 0 || !this.moduleComponent.isOpened) return false;

    float cx = this.moduleComponent.categoryComponent.getX();
    float cy = this.moduleComponent.categoryComponent.getModuleY() + this.o;
    float cw = this.moduleComponent.categoryComponent.getWidth();
    float left = cx + 4 + (xOffset / 2);
    float top = cy + 1;
    float right = cx + cw - 4 + (xOffset / 2);

    // Check header click
    boolean overHeader = x > left && x < right && y > top && y < top + GROUP_HEADER_HEIGHT;
    if (overHeader) {
      toggleExpanded();
      return true;
    }

    // Check sub-property clicks
    float progress = getAnimationProgress();
    if (progress > 0.01f) {
      float subTop = top + GROUP_HEADER_HEIGHT;
      for (int i = 0; i < subProperties.size(); i++) {
        BooleanProperty prop = subProperties.get(i);
        if (!prop.isVisible()) continue;

        float rowTop = subTop + i * SUB_PROP_HEIGHT;
        float rowBottom = rowTop + SUB_PROP_HEIGHT;
        if (x > left && x < right && y > rowTop && y < rowBottom) {
          prop.setValue(!prop.getValue());
          this.moduleComponent.reloadSettings();
          return true;
        }
      }
    }

    return false;
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
    for (BooleanProperty prop : subProperties) {
      if (prop.isVisible()) return true;
    }
    return false;
  }

  @Override
  public void onGuiClosed() {
    this.expanded = false;
    this.smoothTimer = null;
    this.animationProgress = 0f;
    this.animationStart = 0f;
    this.animationTarget = 0f;
  }
}
