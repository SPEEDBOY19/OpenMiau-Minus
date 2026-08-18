package miau.ui.clickgui.demise.component.impl;

import java.awt.*;
import miau.property.properties.TextProperty;
import miau.ui.clickgui.demise.Component;
import miau.util.demise.MouseUtils;
import miau.util.font.FontRepository;
import org.lwjgl.input.Keyboard;

public class StringComponent extends Component {
  private final TextProperty setting;
  private float inputAnim = 0f;
  private boolean inputting;
  private String text = "";

  public StringComponent(TextProperty setting) {
    this.setting = setting;
    setHeight(FontRepository.getFont("Inter Regular", 14f).height() * 2 + 4);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    inputAnim = animate(inputAnim, inputting ? 1 : 0, 0.15f);
    text = setting.getValue();
    if (text == null) text = "";

    String textToDraw = text.isEmpty() && !inputting ? "Empty..." : text;
    FontRepository.getFont("Inter Regular", 14f)
        .draw(setting.getName(), (double) (getX() + 4), (double) (getY()), -1);
    drawTextWithLineBreaks(
        textToDraw
            + (inputting && text.length() < 59 && System.currentTimeMillis() % 1000 > 500
                ? "|"
                : ""),
        getX() + 6,
        getY() + FontRepository.getFont("Inter Regular", 14f).height() + 2);
    super.drawScreen(mouseX, mouseY);
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (MouseUtils.isHovered(
            getX(),
            getY() + FontRepository.getFont("Inter Regular", 14f).height() + 4,
            getWidth(),
            4f,
            mouseX,
            mouseY)
        && mouseButton == 0) {
      inputting = !inputting;
    } else {
      inputting = false;
    }
    super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int state) {
    super.mouseReleased(mouseX, mouseY, state);
  }

  @Override
  public void keyTyped(char typedChar, int keyCode) {
    if (inputting) {
      if (keyCode == Keyboard.KEY_BACK) {
        deleteLastCharacter();
      }

      if (Character.isLetterOrDigit(typedChar) || keyCode == Keyboard.KEY_SPACE) {
        text += typedChar;
        setting.setValue(text);
      }
    }
    super.keyTyped(typedChar, keyCode);
  }

  private void drawTextWithLineBreaks(String text, float x, float y) {
    String[] lines = text.split("\n");
    float currentY = y;

    for (String line : lines) {
      Color color = interpolateColorC(new Color(-1).darker(), new Color(-1), inputAnim);
      FontRepository.getFont("Inter Regular", 15f)
          .draw(line, (double) x, (double) currentY, color.getRGB());
      currentY += FontRepository.getFont("Inter Regular", 15f).height();
    }
  }

  private void deleteLastCharacter() {
    if (!text.isEmpty()) {
      text = text.substring(0, text.length() - 1);
      setting.setValue(text);
    }
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
