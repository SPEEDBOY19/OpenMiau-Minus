package miau.ui.clickgui.demise.component.impl;

import java.awt.*;
import miau.property.properties.ColorProperty;
import miau.ui.clickgui.demise.Component;
import miau.util.demise.MouseUtils;
import miau.util.demise.RoundedUtils;
import miau.util.font.FontRepository;
import miau.util.render.ShapeUtil;

public class ColorPickerComponent extends Component {
  private final ColorProperty setting;
  private float open = 0f;
  private boolean opened, pickingHue, pickingOthers, pickingAlpha;
  private float hue, saturation, brightness, alpha;

  public ColorPickerComponent(ColorProperty setting) {
    this.setting = setting;
    Color c = new Color(setting.getValue());
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    this.hue = hsb[0];
    this.saturation = hsb[1];
    this.brightness = hsb[2];
    this.alpha = 1.0f;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    open = animate(open, opened ? 1 : 0, 0.15f);
    this.setHeight(
        FontRepository.getFont("Inter Regular", 15f).height()
            + ((FontRepository.getFont("Inter Regular", 15f).height() + 2 + 45 + 2 + 12) * open));

    FontRepository.getFont("Inter Regular", 15f)
        .draw(setting.getName(), (double) (getX() + 4), (double) (getY()), -1);
    RoundedUtils.drawRound(
        getX() + getWidth() - 18,
        getY(),
        15,
        FontRepository.getFont("Inter Regular", 15f).height() - 3,
        2,
        new Color(setting.getValue()));

    if (opened) {
      RoundedUtils.drawGradientRound(
          getX() + 2,
          getY() + FontRepository.getFont("Inter Regular", 15f).height() + 2,
          getWidth() - 4,
          (float) (45 * open),
          4,
          Color.BLACK,
          Color.WHITE,
          Color.BLACK,
          Color.getHSBColor(hue, 1, 1));

      for (int max = (int) (getWidth() - 8), i = 0; i < max; i++) {
        RoundedUtils.drawRound(
            getX() + i + 4,
            (float)
                (getY()
                    + FontRepository.getFont("Inter Regular", 15f).height()
                    + 2
                    + (45 * open)
                    + 4),
            2,
            4,
            2,
            Color.getHSBColor(i / (float) max, 1, 1));
      }

      float alphaSliderY =
          (float)
              (getY()
                  + FontRepository.getFont("Inter Regular", 15f).height()
                  + 2
                  + (45 * open)
                  + 12);
      drawCheckerboard(getX() + 4, alphaSliderY, getWidth() - 8, 4);

      for (int max = (int) (getWidth() - 8), i = 0; i < max; i++) {
        float alphaValue = i / (float) max;
        Color alphaColor =
            new Color(
                new Color(setting.getValue()).getRed(),
                new Color(setting.getValue()).getGreen(),
                new Color(setting.getValue()).getBlue(),
                (int) (alphaValue * 255));
        RoundedUtils.drawRound(getX() + i + 4, alphaSliderY, 2, 4, 1, alphaColor);
      }

      float sliderX = getX() + 4;
      float sliderWidth = getWidth() - 8;
      float alphaHandleX = sliderX + (sliderWidth * alpha);
      alphaHandleX = Math.max(sliderX + 2, Math.min(sliderX + sliderWidth - 2, alphaHandleX));
      ShapeUtil.drawFilledCircle(alphaHandleX, alphaSliderY + 2, 2, -1);

      float gradientX = getX() + 4;
      float gradientY = getY() + FontRepository.getFont("Inter Regular", 15f).height() + 2;
      float gradientWidth = getWidth() - 8;
      float gradientHeight = (float) (45 * open);

      float pickerY = (gradientY) + (gradientHeight * (1 - brightness));
      float pickerX = (gradientX) + (gradientWidth * saturation - 1);
      pickerY = Math.max(Math.min(gradientY + gradientHeight - 2, pickerY), gradientY - 2);
      pickerX = Math.max(Math.min(gradientX + gradientWidth - 2, pickerX), gradientX - 2);

      if (pickingHue) {
        hue = Math.min(1, Math.max(0, (mouseX - gradientX) / gradientWidth));
        updateColor();
      }

      if (pickingOthers) {
        brightness = Math.min(1, Math.max(0, 1 - ((mouseY - gradientY) / gradientHeight)));
        saturation = Math.min(1, Math.max(0, (mouseX - gradientX) / gradientWidth));
        updateColor();
      }

      if (pickingAlpha) {
        float newAlpha = (mouseX - sliderX) / sliderWidth;
        newAlpha = Math.max(0.0f, Math.min(1.0f, newAlpha));
        alpha = newAlpha;
        updateColor();
      }

      ShapeUtil.drawFilledCircle(pickerX, pickerY, 2, -1);
    }
    super.drawScreen(mouseX, mouseY);
  }

  private void updateColor() {
    int rgb = Color.HSBtoRGB(hue, saturation, brightness);
    int argb = (rgb & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
    setting.setValue(rgb);
  }

  private void drawCheckerboard(float x, float y, float width, float height) {
    RoundedUtils.drawRound(x, y, width, height, 2, new Color(200, 200, 200));
    int squareSize = 4;
    boolean white = true;
    for (int i = 0; i < width; i += squareSize) {
      for (int j = 0; j < height; j += squareSize) {
        if (!white) {
          Color color = new Color(150, 150, 150);
          float drawWidth = Math.min(squareSize, width - i);
          float drawHeight = Math.min(squareSize, height - j);

          if (i > 2 && i < width - 2 || j > 0 && j < height - 0) {
            RoundedUtils.drawRound(x + i, y + j, drawWidth, drawHeight, 0, color);
          }
        }
        white = !white;
      }
      if (height / squareSize % 2 == 0) {
        white = !white;
      }
    }
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (MouseUtils.isHovered(
        getX() + getWidth() - 18,
        getY(),
        15f,
        FontRepository.getFont("Inter Regular", 15f).height(),
        mouseX,
        mouseY)) {
      opened = !opened;
    }

    if (opened) {
      if (MouseUtils.isHovered(
          getX() + 4,
          getY() + FontRepository.getFont("Inter Regular", 15f).height() + 2,
          getWidth() - 8,
          (float) (45 * open),
          mouseX,
          mouseY)) {
        pickingOthers = true;
      }

      if (MouseUtils.isHovered(
          getX() + 4,
          (float)
              (getY()
                  + FontRepository.getFont("Inter Regular", 15f).height()
                  + 2
                  + (45 * open)
                  + 4),
          getWidth() - 8,
          6f,
          mouseX,
          mouseY)) {
        pickingHue = true;
      }

      float alphaSliderY =
          (float)
              (getY()
                  + FontRepository.getFont("Inter Regular", 15f).height()
                  + 2
                  + (45 * open)
                  + 12);
      if (MouseUtils.isHovered(getX() + 4, alphaSliderY, getWidth() - 8, 6f, mouseX, mouseY)) {
        pickingAlpha = true;
      }
    }

    super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int state) {
    if (state == 0) {
      pickingHue = false;
      pickingOthers = false;
      pickingAlpha = false;
    }
    super.mouseReleased(mouseX, mouseY, state);
  }

  @Override
  public boolean isVisible() {
    return setting.isVisible();
  }

  @Override
  public boolean isChild() {
    return false;
  }

  private float animate(float current, float target, float speed) {
    return current + (target - current) / Math.max(1, speed * 10);
  }
}
