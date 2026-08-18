package miau.ui.clickgui.components.impl;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import miau.property.Property;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.ui.clickgui.components.Component;
import miau.util.animation.AnimationTimer;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import org.lwjgl.opengl.GL11;

public class SliderComponent extends Component {
  public Property<?> property;
  private ModuleComponent moduleComponent;
  public float o;
  public float x;
  private float y;
  public boolean heldDown = false;
  private double width;
  public float xOffset;

  private double targetValue;
  private double displayedValue;

  // For double slider
  public boolean draggingMin = false;
  public boolean draggingMax = false;
  private double targetSecondValue;
  private double displayedSecondValue;

  public boolean isExpanded = false;
  private AnimationTimer dropdownTimer;
  private float dropdownProgress = 0f;
  private float dropdownStartProgress = 0f;
  private float dropdownTargetProgress = 0f;

  private float dropdownScrollY = 0f;
  private float targetDropdownScrollY = 0f;

  private static final double SLIDER_SPEED = 0.6;
  private static final float MODE_HEADER_HEIGHT = 14f;
  private static final float MODE_OPTION_HEIGHT = 12f;
  private static final float MODE_DROPDOWN_GAP = 2f;
  private static final float MODE_ANIMATION_DURATION = 180f;
  private static final int MAX_DROPDOWN_VISIBLE = 8;

  public SliderComponent(Property<?> property, ModuleComponent moduleComponent, float o) {
    this.property = property;
    this.moduleComponent = moduleComponent;
    this.o = o;

    double initial = getValue();
    this.targetValue = initial;
    this.displayedValue = initial;

    if (isDouble()) {
      double second = getSecondValue();
      this.targetSecondValue = second;
      this.displayedSecondValue = second;
    }

    double range = getMax() - getMin();
    this.width =
        range == 0
            ? 0
            : (double) (this.moduleComponent.categoryComponent.getWidth() - 8)
                * (initial - getMin())
                / range;
  }

  public boolean isDouble() {
    return property instanceof FloatProperty && ((FloatProperty) property).isDoubleSlider();
  }

  public double getValue() {
    if (property instanceof FloatProperty) {
      return ((FloatProperty) property).getValue().doubleValue();
    } else if (property instanceof IntProperty) {
      return ((IntProperty) property).getValue().doubleValue();
    } else if (property instanceof PercentProperty) {
      return ((PercentProperty) property).getValue().doubleValue();
    } else if (property instanceof ModeProperty) {
      return ((ModeProperty) property).getValue().doubleValue();
    }
    return 0;
  }

  public double getSecondValue() {
    if (isDouble()) {
      return ((FloatProperty) property).getSecondValue().doubleValue();
    }
    return getValue();
  }

  public double getMin() {
    if (property instanceof FloatProperty) {
      return ((FloatProperty) property).getMinimum().doubleValue();
    } else if (property instanceof IntProperty) {
      return ((IntProperty) property).getMinimum().doubleValue();
    } else if (property instanceof PercentProperty) {
      return ((PercentProperty) property).getMinimum().doubleValue();
    }
    return 0;
  }

  public double getMax() {
    if (property instanceof FloatProperty) {
      return ((FloatProperty) property).getMaximum().doubleValue();
    } else if (property instanceof IntProperty) {
      return ((IntProperty) property).getMaximum().doubleValue();
    } else if (property instanceof PercentProperty) {
      return ((PercentProperty) property).getMaximum().doubleValue();
    } else if (property instanceof ModeProperty) {
      return ((ModeProperty) property).getModes().length - 1;
    }
    return 1;
  }

  public void setValue(double newValue) {
    newValue = Math.max(getMin(), Math.min(getMax(), newValue));
    if (isDouble() && newValue > getSecondValue()) {
      newValue = getSecondValue();
    }
    Object prevValue = property.getValue();
    if (property instanceof FloatProperty) {
      property.setValue((float) newValue);
    } else if (property instanceof IntProperty) {
      property.setValue((int) Math.round(newValue));
    } else if (property instanceof PercentProperty) {
      property.setValue((int) Math.round(newValue));
    } else if (property instanceof ModeProperty) {
      property.setValue((int) Math.round(newValue));
    }
    if (prevValue != null && !prevValue.equals(property.getValue())) {
      this.moduleComponent.reloadSettings();
    }
  }

  public void setSecondValue(double newValue) {
    newValue = Math.max(getMin(), Math.min(getMax(), newValue));
    if (isDouble() && newValue < getValue()) {
      newValue = getValue();
    }
    Object prevValue = getSecondValue();
    if (isDouble()) {
      ((FloatProperty) property).setSecondValue((float) newValue);
    }
    if (prevValue != null && !prevValue.equals(getSecondValue())) {
      this.moduleComponent.reloadSettings();
    }
  }

  public boolean isString() {
    return property instanceof ModeProperty;
  }

  public String[] getOptions() {
    if (property instanceof ModeProperty) {
      return ((ModeProperty) property).getModes();
    }
    return null;
  }

  public String getSuffix() {
    if (property instanceof PercentProperty) {
      return "%";
    }
    return "";
  }

  @Override
  public void render() {
    if (isString()) {
      renderModeHeader();
      return;
    }

    float cx = this.moduleComponent.categoryComponent.getX();
    float cy = this.moduleComponent.categoryComponent.getY() + this.o;
    float cw = this.moduleComponent.categoryComponent.getWidth();

    double input = getValue();
    String suffix = getSuffix();
    String valueText;

    if (property instanceof IntProperty || property instanceof PercentProperty) {
      valueText = String.valueOf((int) Math.round(input));
    } else if (isDouble()) {
      valueText = String.format("%.1f - %.1f", input, getSecondValue());
    } else {
      valueText = String.format("%.2f", input);
    }

    GL11.glPushMatrix();
    GL11.glScaled(0.5, 0.5, 0.5);
    float labelX = (float) ((cx + 6 + xOffset / 2) * 2);
    float labelY = (float) ((cy + 4) * 2);
    FontRepository.getMinecraftFont()
        .draw(this.property.getName() + ": " + valueText + suffix, labelX, labelY, -1, true);
    GL11.glPopMatrix();

    float trackLeft = cx + 6 + (xOffset / 2);
    float trackRight = cx + cw - 6 + (xOffset / 2);
    float trackY = cy + 13;
    float trackHeight = 2.5f;

    RenderUtil.drawRoundedRectangle(
        trackLeft,
        trackY,
        trackRight,
        trackY + trackHeight,
        trackHeight / 2f,
        new Color(40, 40, 40).getRGB());

    double range = getMax() - getMin();
    double fraction = range == 0 ? 0 : (this.displayedValue - getMin()) / range;
    float actualFillRight = trackLeft + (float) ((trackRight - trackLeft) * fraction);

    int accentColor =
        Color.getHSBColor((float) (System.currentTimeMillis() % 11000L) / 11000.0F, 0.75F, 0.9F)
            .getRGB();

    if (isDouble()) {
      double fraction2 = range == 0 ? 0 : (this.displayedSecondValue - getMin()) / range;
      float actualFillRight2 = trackLeft + (float) ((trackRight - trackLeft) * fraction2);

      RenderUtil.drawRoundedRectangle(
          actualFillRight,
          trackY,
          actualFillRight2,
          trackY + trackHeight,
          trackHeight / 2f,
          accentColor);

      float thumbRadius = 2.5f;
      RenderUtil.drawRoundedRectangle(
          actualFillRight - thumbRadius,
          trackY + trackHeight / 2f - thumbRadius,
          actualFillRight + thumbRadius,
          trackY + trackHeight / 2f + thumbRadius,
          thumbRadius,
          -1);
      RenderUtil.drawRoundedRectangle(
          actualFillRight2 - thumbRadius,
          trackY + trackHeight / 2f - thumbRadius,
          actualFillRight2 + thumbRadius,
          trackY + trackHeight / 2f + thumbRadius,
          thumbRadius,
          -1);
    } else {
      RenderUtil.drawRoundedRectangle(
          trackLeft, trackY, actualFillRight, trackY + trackHeight, trackHeight / 2f, accentColor);

      float thumbRadius = 2.5f;
      RenderUtil.drawRoundedRectangle(
          actualFillRight - thumbRadius,
          trackY + trackHeight / 2f - thumbRadius,
          actualFillRight + thumbRadius,
          trackY + trackHeight / 2f + thumbRadius,
          thumbRadius,
          -1);
    }
  }

  private void renderModeHeader() {
    float cx = this.moduleComponent.categoryComponent.getX();
    float cy = this.moduleComponent.categoryComponent.getY() + this.o;
    float cw = this.moduleComponent.categoryComponent.getWidth();
    float left = cx + 4 + (xOffset / 2);
    float top = cy + 1;
    float right = cx + cw - 4 + (xOffset / 2);
    float bottom = top + MODE_HEADER_HEIGHT;

    RenderUtil.drawRoundedRectangle(
        left, top, right, bottom, 4f, new Color(22, 22, 28, 185).getRGB());

    Font font = FontRepository.getMinecraftFont();
    String valueText = getModeText();
    GL11.glPushMatrix();
    GL11.glScaled(0.5, 0.5, 0.5);
    font.draw(
        property.getName(), (left + 5) * 2, (top + 4) * 2, new Color(235, 235, 240).getRGB(), true);
    float valueWidth = font.getStringWidth(valueText) / 2f;
    font.draw(
        valueText,
        (right - 17 - valueWidth) * 2,
        (top + 4) * 2,
        new Color(160, 205, 255).getRGB(),
        true);
    GL11.glPopMatrix();

    drawArrow(right - 10, top + MODE_HEADER_HEIGHT / 2f, getDropdownProgress());
  }

  public void renderModeDropdownOverlay(int mouseX, int mouseY) {
    if (!isString()
        || getDropdownProgress() <= 0.01f
        || !moduleComponent.isOpened
        || !moduleComponent.isVisible(this)) {
      return;
    }

    String[] options = getOptions();
    if (options == null || options.length == 0) {
      return;
    }

    float progress = getDropdownProgress();
    float cx = this.moduleComponent.categoryComponent.getX();
    float cy = this.y;
    float cw = this.moduleComponent.categoryComponent.getWidth();
    float left = cx + 4 + (xOffset / 2);
    float top = cy + 1 + MODE_HEADER_HEIGHT + MODE_DROPDOWN_GAP;
    float right = cx + cw - 4 + (xOffset / 2);
    float fullHeight = options.length * MODE_OPTION_HEIGHT + 6f;
    float visibleHeight = getVisibleDropdownHeight();
    float shownHeight = visibleHeight * progress;

    float maxScrollY = Math.max(0, fullHeight - visibleHeight);
    targetDropdownScrollY = Math.max(0, Math.min(targetDropdownScrollY, maxScrollY));
    dropdownScrollY += (targetDropdownScrollY - dropdownScrollY) * 0.25f;
    if (Math.abs(targetDropdownScrollY - dropdownScrollY) < 0.05f) {
      dropdownScrollY = targetDropdownScrollY;
    }

    // Isolate GL state to prevent leaking transforms/scissors
    GL11.glPushMatrix();
    GL11.glDisable(GL11.GL_SCISSOR_TEST);

    RenderUtil.drawRoundedRectangle(
        left, top, right, top + shownHeight, 5f, new Color(15, 15, 22, 235).getRGB());
    RenderUtil.scissorPushGui(left, top, right - left, shownHeight);

    if (maxScrollY > 0f) {
      drawDropdownScrollbar(left, top, right, shownHeight, maxScrollY);
    }

    Font font = FontRepository.getMinecraftFont();
    int selected = (int) Math.round(getValue());
    int accentColor =
        Color.getHSBColor((float) (System.currentTimeMillis() % 11000L) / 11000.0F, 0.65F, 0.95F)
            .getRGB();
    for (int i = 0; i < options.length; i++) {
      float rowTop = top + 3f + i * MODE_OPTION_HEIGHT - dropdownScrollY;
      float rowBottom = rowTop + MODE_OPTION_HEIGHT - 1f;
      float rowRight = right - 3f - (maxScrollY > 0f ? 5f : 0f);
      boolean hovered =
          mouseX >= left + 3 && mouseX <= rowRight && mouseY >= rowTop && mouseY <= rowBottom;
      if (hovered || i == selected) {
        int rowColor =
            hovered ? new Color(255, 255, 255, 24).getRGB() : new Color(255, 255, 255, 14).getRGB();
        RenderUtil.drawRoundedRectangle(left + 3, rowTop, rowRight, rowBottom, 3f, rowColor);
      }
      if (i == selected) {
        RenderUtil.drawRoundedRectangle(
            left + 5, rowTop + 3, left + 7, rowBottom - 3, 1f, accentColor);
      }

      GL11.glPushMatrix();
      GL11.glScaled(0.5, 0.5, 0.5);
      int textColor =
          i == selected ? new Color(230, 245, 255).getRGB() : new Color(205, 205, 214).getRGB();
      font.draw(options[i], (left + 12) * 2, (rowTop + 3.5f) * 2, textColor, true);
      GL11.glPopMatrix();
    }

    RenderUtil.scissorPop();
    GL11.glPopMatrix();
  }

  private void drawDropdownScrollbar(
      float left, float top, float right, float shownHeight, float maxScrollY) {
    float trackX = right - 5f;
    float trackTop = top + 2f;
    float trackBottom = top + shownHeight - 2f;
    if (trackBottom <= trackTop) {
      return;
    }
    float fullHeight = getOptions().length * MODE_OPTION_HEIGHT + 6f;
    float shown = shownHeight;
    float trackHeight = trackBottom - trackTop;
    float thumbHeight = Math.max(14f, trackHeight * (shown / fullHeight));
    float maxThumb = trackHeight - thumbHeight;
    float fraction = maxScrollY <= 0 ? 0f : dropdownScrollY / maxScrollY;
    float thumbTop = trackTop + maxThumb * fraction;

    int accentColor =
        Color.getHSBColor((float) (System.currentTimeMillis() % 11000L) / 11000.0F, 0.65F, 0.95F)
            .getRGB();
    RenderUtil.drawRoundedRectangle(
        trackX, trackTop, trackX + 2, trackBottom, 1f, new Color(255, 255, 255, 30).getRGB());
    RenderUtil.drawRoundedRectangle(
        trackX, thumbTop, trackX + 2, thumbTop + thumbHeight, 1f, accentColor);
  }

  private void drawArrow(float centerX, float centerY, float progress) {
    GL11.glPushMatrix();
    GL11.glTranslatef(centerX, centerY, 0f);
    GL11.glRotatef(progress * 180f, 0f, 0f, 1f);
    RenderUtil.drawRect(-3, -1, 0, 2, new Color(220, 220, 228).getRGB());
    RenderUtil.drawRect(0, -1, 3, 2, new Color(220, 220, 228).getRGB());
    GL11.glPopMatrix();
  }

  private String getModeText() {
    String[] opts = getOptions();
    if (opts == null || opts.length == 0) {
      return "";
    }
    int idx = (int) Math.round(getValue());
    idx = Math.max(0, Math.min(idx, opts.length - 1));
    return opts[idx];
  }

  public float getDropdownProgress() {
    if (dropdownTimer != null) {
      if (System.currentTimeMillis() - dropdownTimer.last >= MODE_ANIMATION_DURATION + 30) {
        dropdownTimer = null;
        dropdownProgress = dropdownTargetProgress;
        dropdownStartProgress = dropdownTargetProgress;
      } else {
        dropdownProgress =
            dropdownTimer.getValueFloat(dropdownStartProgress, dropdownTargetProgress, 1);
        if (dropdownProgress == dropdownTargetProgress) {
          dropdownTimer = null;
          dropdownStartProgress = dropdownTargetProgress;
        }
      }
    }
    return dropdownProgress;
  }

  private void setExpanded(boolean expanded) {
    float currentProgress = getDropdownProgress();
    this.dropdownStartProgress = currentProgress;
    this.isExpanded = expanded;
    this.dropdownTargetProgress = expanded ? 1f : 0f;
    if (expanded) {
      this.dropdownScrollY = 0f;
      this.targetDropdownScrollY = 0f;
    }
    (this.dropdownTimer = new AnimationTimer(MODE_ANIMATION_DURATION)).start();
  }

  public boolean isModeDropdownActive() {
    return isString() && (isExpanded || getDropdownProgress() > 0.01f);
  }

  private float getVisibleDropdownHeight() {
    String[] opts = getOptions();
    if (opts == null || opts.length == 0) {
      return 0;
    }
    float full = opts.length * MODE_OPTION_HEIGHT + 6f;
    return Math.min(full, SliderComponent.MAX_DROPDOWN_VISIBLE * MODE_OPTION_HEIGHT + 6f);
  }

  public void scrollModeDropdown(int wheel) {
    if (!isString() || getOptions() == null || getOptions().length == 0) {
      return;
    }
    float fullHeight = getOptions().length * MODE_OPTION_HEIGHT + 6f;
    float visibleHeight = getVisibleDropdownHeight();
    float maxScrollY = Math.max(0, fullHeight - visibleHeight);
    float step = 3 * MODE_OPTION_HEIGHT;
    targetDropdownScrollY += wheel > 0 ? -step : step;
    targetDropdownScrollY = Math.max(0, Math.min(targetDropdownScrollY, maxScrollY));
  }

  public boolean isMouseOverModeDropdown(int mouseX, int mouseY) {
    if (!isString()) {
      return false;
    }
    String[] options = getOptions();
    if (options == null) {
      return false;
    }
    float cx = this.moduleComponent.categoryComponent.getX();
    float cy = this.y;
    float cw = this.moduleComponent.categoryComponent.getWidth();
    float left = cx + 4 + (xOffset / 2);
    float top = cy + 1 + MODE_HEADER_HEIGHT + MODE_DROPDOWN_GAP;
    float right = cx + cw - 4 + (xOffset / 2);
    float bottom = top + getVisibleDropdownHeight();
    return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
  }

  public void collapseModeDropdown() {
    if (isString() && isExpanded) {
      setExpanded(false);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
    this.x = this.moduleComponent.categoryComponent.getX();

    if (isString()) {
      getDropdownProgress();
      return;
    }

    if (this.heldDown || this.draggingMin || this.draggingMax) {
      float trackLeft = this.x + 6 + (xOffset / 2);
      float trackRight =
          this.x + this.moduleComponent.categoryComponent.getWidth() - 6 + (xOffset / 2);
      float trackWidth = trackRight - trackLeft;

      double d = Math.min(trackWidth, Math.max(0, mouseX - trackLeft));
      double range = getMax() - getMin();
      double n = getMin() + (d / trackWidth) * range;

      if (isDouble()) {
        if (this.draggingMin) {
          this.targetValue = roundToInterval(n, 4);
          this.displayedValue = displayedValue + (targetValue - displayedValue) * SLIDER_SPEED;
          setValue(this.targetValue);
        } else if (this.draggingMax) {
          this.targetSecondValue = roundToInterval(n, 4);
          this.displayedSecondValue =
              displayedSecondValue + (targetSecondValue - displayedSecondValue) * SLIDER_SPEED;
          setSecondValue(this.targetSecondValue);
        }
      } else {
        this.targetValue = roundToInterval(n, 4);
        this.displayedValue = displayedValue + (targetValue - displayedValue) * SLIDER_SPEED;
        setValue(this.targetValue);

        if (range == 0) {
          this.width = 0;
        } else {
          double fraction = (this.displayedValue - getMin()) / range;
          this.width = (this.moduleComponent.categoryComponent.getWidth() - 12) * fraction;
        }
      }
    }
  }

  public void onSliderChange() {
    double initial = getValue();
    this.targetValue = initial;
    this.displayedValue = initial;

    if (isDouble()) {
      double second = getSecondValue();
      this.targetSecondValue = second;
      this.displayedSecondValue = second;
    }

    double range = getMax() - getMin();
    this.width =
        range == 0
            ? 0
            : (double) (this.moduleComponent.categoryComponent.getWidth() - 8)
                * (initial - getMin())
                / range;
  }

  private static double roundToInterval(double value, int places) {
    if (places < 0) {
      return 0.0D;
    }

    BigDecimal bd = new BigDecimal(value);
    bd = bd.setScale(places, RoundingMode.HALF_UP);
    return bd.doubleValue();
  }

  @Override
  public boolean onClick(int mouseX, int mouseY, int button) {
    if (isString()) {
      return onModeClick(mouseX, mouseY, button);
    }

    if ((u(mouseX, mouseY) || i(mouseX, mouseY))
        && button == 0
        && this.moduleComponent.isOpened
        && this.moduleComponent.isVisible(this)) {
      if (isDouble()) {
        float cx = this.moduleComponent.categoryComponent.getX();
        float cw = this.moduleComponent.categoryComponent.getWidth();
        float trackLeft = cx + 6 + (xOffset / 2);
        float trackRight = cx + cw - 6 + (xOffset / 2);

        double range = getMax() - getMin();
        double fraction1 = range == 0 ? 0 : (getValue() - getMin()) / range;
        double fraction2 = range == 0 ? 0 : (getSecondValue() - getMin()) / range;

        float thumb1X = trackLeft + (float) ((trackRight - trackLeft) * fraction1);
        float thumb2X = trackLeft + (float) ((trackRight - trackLeft) * fraction2);

        if (Math.abs(mouseX - thumb1X) < Math.abs(mouseX - thumb2X)) {
          this.draggingMin = true;
        } else if (Math.abs(mouseX - thumb1X) > Math.abs(mouseX - thumb2X)) {
          this.draggingMax = true;
        } else {
          boolean bothAtMax =
              Math.abs(getValue() - getMax()) < 0.01
                  && Math.abs(getSecondValue() - getMax()) < 0.01;
          boolean bothAtMin =
              Math.abs(getValue() - getMin()) < 0.01
                  && Math.abs(getSecondValue() - getMin()) < 0.01;
          if (bothAtMin) {
            this.draggingMax = true;
          } else {
            this.draggingMin = true;
          }
        }
      } else {
        this.heldDown = true;
      }
    }
    return false;
  }

  private boolean onModeClick(int mouseX, int mouseY, int button) {
    if (button != 0 || !this.moduleComponent.isOpened || !this.moduleComponent.isVisible(this)) {
      return false;
    }

    float cw = this.moduleComponent.categoryComponent.getWidth();
    boolean overHeader =
        mouseX > this.x + 4 + (xOffset / 2)
            && mouseX < this.x + cw - 4 + (xOffset / 2)
            && mouseY > this.y + 1
            && mouseY < this.y + 1 + MODE_HEADER_HEIGHT;

    if (overHeader) {
      setExpanded(!this.isExpanded);
      return true;
    }

    if (isModeDropdownActive() && isMouseOverModeDropdown(mouseX, mouseY)) {
      String[] options = getOptions();
      if (options != null) {
        float cx = this.moduleComponent.categoryComponent.getX();
        float top = this.y + 1 + MODE_HEADER_HEIGHT + MODE_DROPDOWN_GAP;
        int optionIndex =
            (int) ((mouseY - top - 3f + dropdownScrollY) / MODE_OPTION_HEIGHT);
        if (optionIndex >= 0 && optionIndex < options.length) {
          property.setValue(optionIndex);
          setExpanded(false);
          moduleComponent.reloadSettings();
        }
        return true;
      }
    }

    return false;
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int button) {
    this.heldDown = false;
    this.draggingMin = false;
    this.draggingMax = false;
  }

  public boolean u(int mouseX, int mouseY) {
    return mouseX > this.x
        && mouseX < this.x + this.moduleComponent.categoryComponent.getWidth() / 2 + 1
        && mouseY > this.y
        && mouseY < this.y + 16;
  }

  public boolean i(int mouseX, int mouseY) {
    return mouseX > this.x + this.moduleComponent.categoryComponent.getWidth() / 2
        && mouseX < this.x + this.moduleComponent.categoryComponent.getWidth()
        && mouseY > this.y
        && mouseY < this.y + 16;
  }

  @Override
  public void onGuiClosed() {
    this.heldDown = false;
    this.draggingMin = false;
    this.draggingMax = false;
    this.isExpanded = false;
    this.dropdownTimer = null;
    this.dropdownProgress = 0f;
    this.dropdownStartProgress = 0f;
    this.dropdownTargetProgress = 0f;
    this.dropdownScrollY = 0f;
    this.targetDropdownScrollY = 0f;
  }

  public void restoreModeDropdownState(boolean expanded) {
    this.isExpanded = expanded;
    this.dropdownTimer = null;
    this.dropdownProgress = expanded ? 1f : 0f;
    this.dropdownStartProgress = this.dropdownProgress;
    this.dropdownTargetProgress = this.dropdownProgress;
  }

  public void updateHeight(float n) {
    this.o = n;
  }

  @Override
  public float getOffset() {
    return this.o;
  }

  @Override
  public boolean isBaseVisible() {
    return this.property.isVisible();
  }
}
