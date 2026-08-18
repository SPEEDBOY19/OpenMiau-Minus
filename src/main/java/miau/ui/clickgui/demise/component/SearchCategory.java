package miau.ui.clickgui.demise.component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.module.Module;
import miau.ui.clickgui.demise.IComponent;
import miau.ui.clickgui.demise.PanelGui;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class SearchCategory implements IComponent {
  private boolean isSelected;
  private float interpolatedLineWidth;
  private final List<ModuleComponent> moduleComponents = new ArrayList<ModuleComponent>();
  private float scrollOffset = 0;
  private float targetScrollOffset = 0;
  private float maxScroll = 0;
  private String filter = "";
  private boolean inputting;

  public SearchCategory() {
    this.isSelected = false;
  }

  public void initCategory() {
    moduleComponents.clear();
    java.util.Set<String> addedModules = new java.util.HashSet<String>();

    for (Module module : Miau.moduleManager.modules.values()) {
      String name = module.getName().toLowerCase();
      String query = filter.toLowerCase().trim();

      boolean matches =
          filter.isEmpty()
              || name.contains(query)
              || name.replace(" ", "").contains(query.replace(" ", ""));

      if (matches && !addedModules.contains(name)) {
        moduleComponents.add(new ModuleComponent(module));
        addedModules.add(name);
      }
    }

    for (ModuleComponent mc : moduleComponents) {
      mc.initCategory();
    }
  }

  public void render(boolean shader) {
    if (isSelected) {
      String thing = (System.currentTimeMillis() % 1000 > 500 ? "|" : "");

      if (inputting) {
        if (!filter.isEmpty()) {
          drawText(filter + thing);
        } else {
          drawText("Search..." + thing);
        }
      } else {
        drawText("Search...");
      }

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
        module.setX(PanelGui.posX + 67);
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

  private void drawText(String text) {
    float watermarkWidth =
        FontRepository.getFont("Inter Bold", 35f).getStringWidth("Miau")
            + 2
            + FontRepository.getFont("Inter Bold", 24f).getStringWidth("1.2.0");
    int color =
        inputting ? new Color(193, 193, 193).getRGB() : new Color(119, 119, 119, 255).getRGB();
    FontRepository.getFont("Inter Regular", 18f)
        .draw(
            text,
            (double) (PanelGui.posX + watermarkWidth + 18),
            (double)
                (PanelGui.posY + 7 + FontRepository.getFont("Inter Regular", 15f).height() - 2),
            color);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    for (ModuleComponent moduleComponent : moduleComponents) {
      moduleComponent.drawScreen(mouseX, mouseY);
    }
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    float watermarkWidth =
        FontRepository.getFont("Inter Bold", 35f).getStringWidth("Miau")
            + 2
            + FontRepository.getFont("Inter Bold", 24f).getStringWidth("1.2.0");
    float calcWidth = 450 - watermarkWidth - 19;
    inputting =
        PanelGui.isHovered(
                PanelGui.posX + watermarkWidth + 13,
                PanelGui.posY + 7,
                calcWidth,
                20,
                mouseX,
                mouseY)
            && mouseButton == 0;

    for (ModuleComponent moduleComponent : moduleComponents) {
      moduleComponent.mouseClicked(mouseX, mouseY, mouseButton);
    }
  }

  @Override
  public void keyTyped(char typedChar, int keyCode) {
    if (inputting) {
      String lastFilter = filter;

      if (keyCode == Keyboard.KEY_BACK) {
        deleteLastCharacter();
      }

      if (Character.isLetterOrDigit(typedChar) || keyCode == Keyboard.KEY_SPACE) {
        filter += typedChar;
      }

      if (!lastFilter.equals(filter)) {
        initCategory();
      }
    }

    for (ModuleComponent moduleComponent : moduleComponents) {
      moduleComponent.keyTyped(typedChar, keyCode);
    }
  }

  private void deleteLastCharacter() {
    if (!filter.isEmpty()) {
      filter = filter.substring(0, filter.length() - 1);
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
