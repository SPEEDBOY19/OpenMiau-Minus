package miau.ui.clickgui.demise.component.impl;

import java.awt.*;
import miau.property.properties.BooleanProperty;
import miau.ui.clickgui.demise.Component;
import miau.util.demise.MouseUtils;
import miau.util.demise.RoundedUtils;
import miau.util.font.FontRepository;

public class BooleanComponent extends Component {
  private final BooleanProperty setting;
  private float toggleAnimation = 0f;

  public BooleanComponent(BooleanProperty setting) {
    this.setting = setting;
    setHeight(FontRepository.getFont("Inter Regular", 15f).height() + 5);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    toggleAnimation = animate(toggleAnimation, setting.getValue() ? 1 : 0, 0.15f);

    FontRepository.getFont("Inter Regular", 15f)
        .draw(setting.getName(), (double) (getX() + 4), (double) (getY() + 2.5f), -1);

    RoundedUtils.drawRound(
        getX() + getWidth() - 15.5f,
        getY() + 2.5f,
        13f,
        5,
        2.25f,
        interpolateColorC(new Color(128, 128, 128, 255), Color.white, toggleAnimation).darker());
    RoundedUtils.drawRound(
        getX() + getWidth() - 15.5f + 8 * toggleAnimation, getY() + 2.5f, 5, 5, 2.25f, Color.WHITE);
    super.drawScreen(mouseX, mouseY);
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (MouseUtils.isHovered(getX() + getWidth() - 17.5f, getY() + 2.5f, 12.5f, 5f, mouseX, mouseY)
        && mouseButton == 0) {
      setting.setValue(!setting.getValue());
    }
    super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public boolean isVisible() {
    return setting.isVisible();
  }

  @Override
  public boolean isChild() {
    return false;
  }

  private Color interpolateColorC(Color color1, Color color2, float amount) {
    amount = Math.min(1, Math.max(0, amount));
    return new Color(
        (int) (color1.getRed() + (color2.getRed() - color1.getRed()) * amount),
        (int) (color1.getGreen() + (color2.getGreen() - color1.getGreen()) * amount),
        (int) (color1.getBlue() + (color2.getBlue() - color1.getBlue()) * amount),
        (int) (color1.getAlpha() + (color2.getAlpha() - color1.getAlpha()) * amount));
  }

  private float animate(float current, float target, float speed) {
    return current + (target - current) / Math.max(1, speed * 10);
  }
}
