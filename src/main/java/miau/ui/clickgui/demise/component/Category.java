package miau.ui.clickgui.demise.component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import miau.module.Module;
import miau.ui.clickgui.demise.IComponent;
import miau.ui.clickgui.demise.PanelGui;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class Category implements IComponent {
  private String categoryName;
  private float x, y;
  private boolean isHovered, isSelected;
  private float interpolatedX;
  private float interpolatedLineWidth;
  private final List<ModuleComponent> moduleComponents = new ArrayList<ModuleComponent>();
  private float scrollOffset = 0;
  private float targetScrollOffset = 0;
  private float maxScroll = 0;

  public Category(String categoryName, float x, float y) {
    this.categoryName = categoryName;
    this.x = x;
    this.y = y;
    this.isSelected = false;
    this.isHovered = false;
    this.interpolatedX = x;
  }

  public void addModule(Module module) {
    moduleComponents.add(new ModuleComponent(module));
  }

  public void initCategory() {
    for (ModuleComponent mc : moduleComponents) {
      mc.initCategory();
    }
  }

  public void render(boolean shader) {
    float x = this.x;

    if (isSelected) {
      x += 3;
      float width = FontRepository.getFont("Inter Regular", 18f).getStringWidth(categoryName);
      interpolatedLineWidth = animate(interpolatedLineWidth, width, 0.05f);
    } else {
      interpolatedLineWidth = animate(interpolatedLineWidth, 0, 0.05f);
    }

    if (isHovered) {
      x += 2.5f;
    }

    if (!PanelGui.dragging) {
      interpolatedX = animate(interpolatedX, x, 0.15f);
    } else {
      interpolatedX = x;
    }

    if (!shader) {
      FontRepository.getFont("Inter Regular", 18f)
          .draw(categoryName, (double) interpolatedX, (double) y, Color.white.getRGB());
      RenderUtil.drawRect(
          interpolatedX,
          (float) (y + FontRepository.getFont("Inter Regular", 18f).height() - 2.6f),
          interpolatedX + interpolatedLineWidth,
          (float) (y + FontRepository.getFont("Inter Regular", 18f).height() - 2.6f) + 0.5f,
          Color.white.getRGB());
    }

    if (isSelected) {
      handleScroll();

      float componentStartY = PanelGui.posY + 45;
      float viewHeight = 255;

      float totalHeight = 0;

      for (ModuleComponent module : moduleComponents) {
        totalHeight += module.getHeight() + 10;
      }

      maxScroll = Math.max(0, totalHeight - viewHeight);
      scrollOffset = animate(scrollOffset, targetScrollOffset, 0.1f);

      RenderUtil.scissor(
          0, componentStartY, PanelGui.posX + 450, viewHeight, PanelGui.interpolatedScale);
      GL11.glEnable(GL11.GL_SCISSOR_TEST);

      float componentOffsetY = componentStartY + 2;
      for (ModuleComponent module : moduleComponents) {
        float moduleY = componentOffsetY - scrollOffset;
        module.setX(this.x + 60);
        module.setY(moduleY);
        module.setVisible(
            moduleY + 35 >= componentStartY && moduleY <= componentStartY + viewHeight);
        module.setVisibleSetting(
            moduleY + module.getHeight() >= componentStartY
                && moduleY <= componentStartY + viewHeight);
        module.render(shader);

        componentOffsetY += module.getHeight() + 10;
      }

      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    for (ModuleComponent moduleComponent : moduleComponents) {
      moduleComponent.drawScreen(mouseX, mouseY);
    }
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    for (ModuleComponent moduleComponent : moduleComponents) {
      moduleComponent.mouseClicked(mouseX, mouseY, mouseButton);
    }
  }

  @Override
  public void keyTyped(char typedChar, int keyCode) {
    for (ModuleComponent moduleComponent : moduleComponents) {
      moduleComponent.keyTyped(typedChar, keyCode);
    }
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int state) {
    for (ModuleComponent moduleComponent : moduleComponents) {
      moduleComponent.mouseReleased(mouseX, mouseY, state);
    }
  }

  public void handleScroll() {
    int wheel = Mouse.getDWheel();
    if (wheel != 0) {
      float scrollAmount = wheel > 0 ? -25 : 25;
      targetScrollOffset = MathHelper.clamp_float(targetScrollOffset + scrollAmount, 0, maxScroll);
    }
  }

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String categoryName) {
    this.categoryName = categoryName;
  }

  public float getX() {
    return x;
  }

  public void setX(float x) {
    this.x = x;
  }

  public float getY() {
    return y;
  }

  public void setY(float y) {
    this.y = y;
  }

  public boolean isHovered() {
    return isHovered;
  }

  public void setHovered(boolean hovered) {
    isHovered = hovered;
  }

  public boolean isSelected() {
    return isSelected;
  }

  public void setSelected(boolean selected) {
    isSelected = selected;
  }

  private float animate(float current, float target, float speed) {
    return current + (target - current) / Math.max(1, speed * 10);
  }
}
