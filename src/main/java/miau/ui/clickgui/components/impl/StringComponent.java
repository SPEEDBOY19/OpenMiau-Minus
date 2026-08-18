package miau.ui.clickgui.components.impl;

import java.awt.Color;
import miau.property.properties.TextProperty;
import miau.ui.clickgui.components.Component;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

public class StringComponent extends Component {
  public TextProperty property;
  public ModuleComponent moduleComponent;
  public float o, x, y;
  public boolean focused;

  public StringComponent(TextProperty property, ModuleComponent moduleComponent, float o) {
    this.property = property;
    this.moduleComponent = moduleComponent;
    this.o = o;
  }

  @Override
  public void render() {
    Font font = FontRepository.getMinecraftFont();
    String text = property.getValue();
    String display;
    if (focused) {
      display = text + (System.currentTimeMillis() % 1000 < 500 ? "_" : "");
    } else {
      display = text.isEmpty() ? property.getName() + "..." : property.getName() + ": " + text;
    }

    Gui.drawRect(
        (int) (this.moduleComponent.categoryComponent.getX() + 4),
        (int) (this.moduleComponent.categoryComponent.getY() + this.o),
        (int)
            (this.moduleComponent.categoryComponent.getX()
                + this.moduleComponent.categoryComponent.getWidth()
                - 4),
        (int) (this.moduleComponent.categoryComponent.getY() + this.o + 12),
        new Color(0, 0, 0, 100).getRGB());

    font.draw(
        display,
        this.moduleComponent.categoryComponent.getX() + 8,
        this.moduleComponent.categoryComponent.getY() + this.o + 2,
        focused ? Color.WHITE.getRGB() : new Color(150, 150, 150).getRGB(),
        true);
  }

  @Override
  public void updateHeight(float n) {
    this.o = n;
  }

  @Override
  public float getHeightF() {
    return 12f;
  }

  @Override
  public float getOffset() {
    return this.o;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
    this.x = this.moduleComponent.categoryComponent.getX();
  }

  @Override
  public boolean onClick(int mouseX, int mouseY, int mouseButton) {
    if (isHovered(mouseX, mouseY) && this.moduleComponent.isOpened) {
      focused = true;
      return true;
    }
    focused = false;
    return false;
  }

  @Override
  public void keyTyped(char t, int k) {
    if (focused) {
      if (k == Keyboard.KEY_ESCAPE) {
        focused = false;
      } else if (k == Keyboard.KEY_BACK) {
        String currentText = property.getValue();
        if (currentText.length() > 0) {
          property.setValue(currentText.substring(0, currentText.length() - 1));
        }
      } else if (k == Keyboard.KEY_RETURN || k == Keyboard.KEY_NUMPADENTER) {
        focused = false;
      } else if (t >= 32 && t <= 126) {
        property.setValue(property.getValue() + t);
      }
    }
  }

  @Override
  public boolean isBaseVisible() {
    return property.isVisible();
  }

  private boolean isHovered(int mouseX, int mouseY) {
    return mouseX > this.x
        && mouseX < this.x + this.moduleComponent.categoryComponent.getWidth()
        && mouseY > this.y
        && mouseY < this.y + this.getHeightF();
  }
}
