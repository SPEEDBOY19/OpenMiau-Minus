package miau.ui.clickgui.components.impl;

import java.awt.Color;
import miau.ui.clickgui.components.Component;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

public class SearchBarComponent extends Component {
  private final CategoryComponent categoryComponent;
  public float o, x, y;
  public boolean focused;
  public final StringBuilder currentText = new StringBuilder();

  public SearchBarComponent(CategoryComponent categoryComponent, float o) {
    this.categoryComponent = categoryComponent;
    this.o = o;
  }

  @Override
  public void render() {
    Font font = FontRepository.getMinecraftFont();
    String display =
        currentText.length() == 0 && !focused
            ? "Search module..."
            : currentText.toString()
                + (focused && System.currentTimeMillis() % 1000 < 500 ? "_" : "");

    Gui.drawRect(
        (int) (this.categoryComponent.getX() + 4),
        (int) (this.categoryComponent.getY() + this.o + 2),
        (int) (this.categoryComponent.getX() + this.categoryComponent.getWidth() - 4),
        (int) (this.categoryComponent.getY() + this.o + 16),
        new Color(0, 0, 0, 100).getRGB());

    font.draw(
        display,
        this.categoryComponent.getX() + 8,
        this.categoryComponent.getY() + this.o + 5,
        focused ? Color.WHITE.getRGB() : new Color(150, 150, 150).getRGB(),
        false);
  }

  @Override
  public void updateHeight(float n) {
    this.o = n;
  }

  @Override
  public float getHeightF() {
    return 20f;
  }

  @Override
  public float getOffset() {
    return this.o;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    this.y = this.categoryComponent.getModuleY() + this.o;
    this.x = this.categoryComponent.getX();
  }

  @Override
  public boolean onClick(int mouseX, int mouseY, int mouseButton) {
    if (isHovered(mouseX, mouseY) && this.categoryComponent.opened) {
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
        if (currentText.length() > 0) {
          currentText.setLength(currentText.length() - 1);
          this.categoryComponent.updateSearchResults(currentText.toString());
        }
      } else if (t >= 32 && t <= 126 && currentText.length() < 18) {
        currentText.append(t);
        this.categoryComponent.updateSearchResults(currentText.toString());
      }
    }
  }

  private boolean isHovered(int mouseX, int mouseY) {
    return mouseX > this.x
        && mouseX < this.x + this.categoryComponent.getWidth()
        && mouseY > this.y
        && mouseY < this.y + this.getHeightF();
  }
}
