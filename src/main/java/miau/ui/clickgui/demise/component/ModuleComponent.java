package miau.ui.clickgui.demise.component;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;
import miau.module.Module;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.TextProperty;
import miau.ui.clickgui.demise.Component;
import miau.ui.clickgui.demise.IComponent;
import miau.ui.clickgui.demise.PanelGui;
import miau.ui.clickgui.demise.component.impl.BooleanComponent;
import miau.ui.clickgui.demise.component.impl.ColorPickerComponent;
import miau.ui.clickgui.demise.component.impl.ModeComponent;
import miau.ui.clickgui.demise.component.impl.SliderComponent;
import miau.ui.clickgui.demise.component.impl.StringComponent;
import miau.util.demise.RoundedUtils;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class ModuleComponent implements IComponent {
  private Module module;
  private float x, y;
  private boolean isHovered, isExpanded;
  private float height;
  private Color interpolatedColor = new Color(20, 20, 20, 150);
  private Color interpolatedColor1 = new Color(0, 0, 0, 0);
  public boolean visible;
  private boolean visibleSetting;
  private final CopyOnWriteArrayList<Component> settings = new CopyOnWriteArrayList<Component>();
  private float slideProgress = 0f;

  public ModuleComponent(Module module) {
    this.module = module;
    this.height = 35;
    for (Property<?> value : module.getValues()) {
      if (value instanceof BooleanProperty) {
        BooleanProperty bp = (BooleanProperty) value;
        settings.add(new BooleanComponent(bp));
      } else if (value instanceof ColorProperty) {
        ColorProperty cp = (ColorProperty) value;
        settings.add(new ColorPickerComponent(cp));
      } else if (value instanceof FloatProperty) {
        FloatProperty fp = (FloatProperty) value;
        settings.add(new SliderComponent(fp));
      } else if (value instanceof ModeProperty) {
        ModeProperty mp = (ModeProperty) value;
        settings.add(new ModeComponent(mp));
      } else if (value instanceof TextProperty) {
        TextProperty tp = (TextProperty) value;
        settings.add(new StringComponent(tp));
      }
    }
  }

  public void initCategory() {
    slideProgress = 0;
  }

  public void render(boolean shader) {
    if (!visible) return;

    float width = 375;
    slideProgress = animate(slideProgress, visibleSetting ? 1 : 0, 0.1f);
    float slideOffset = (width / 4) * (1.0f - slideProgress);

    if (!shader) {
      if (isHovered) {
        interpolatedColor = interpolateColorC(interpolatedColor, new Color(35, 35, 35, 190), 0.1f);
      } else {
        interpolatedColor = interpolateColorC(interpolatedColor, new Color(20, 20, 20, 150), 0.1f);
      }

      if (module.isEnabled()) {
        interpolatedColor1 =
            interpolateColorC(interpolatedColor1, new Color(50, 50, 50, 150), 0.1f);
      } else {
        interpolatedColor1 = interpolateColorC(interpolatedColor1, new Color(0, 0, 0, 0), 0.1f);
      }

      RoundedUtils.drawRound(x + slideOffset, y, width, height, 8, interpolatedColor);
      RoundedUtils.drawRound(x + slideOffset, y, width, height, 8, interpolatedColor1);

      FontRepository.getFont("Inter Regular", 18f)
          .draw(
              module.getName(),
              (double) (x + 7 + slideOffset),
              (double) (y + 9),
              Color.white.getRGB());
      FontRepository.getFont("Inter Regular", 14f)
          .draw(
              module.getName() + " module",
              (double) (x + 7 + slideOffset),
              (double) (y + 21),
              new Color(200, 200, 200).getRGB());

      String keyName = module.getKey() == 0 ? "None" : getKeyName(module.getKey());
      FontRepository.getFont("Inter Regular", 14f)
          .draw(
              keyName,
              (double)
                  (x
                      + width
                      - 8
                      - FontRepository.getFont("Inter Regular", 14f).getStringWidth(keyName)
                      + slideOffset),
              (double) (y + 10),
              new Color(150, 150, 150, 150).getRGB());
    } else {
      RoundedUtils.drawRound(x + slideOffset, y, width, height, 8, Color.black);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    this.isHovered = PanelGui.isHovered(x, y, 375, 35, mouseX, mouseY);

    float yOffset = 35;
    float width = 375;

    float openOutput = isExpanded ? 1 : 0;

    RenderUtil.scissor(x, PanelGui.posY + 45, width, 255, PanelGui.interpolatedScale);
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    float slideOffset = (width / 4) * (1.0f - slideProgress);

    for (Component component : settings) {
      if (!component.isVisible()) continue;

      component.setX((component.isChild() ? x + 5 : x) + slideOffset);
      component.setY((float) (y + yOffset * openOutput) + 1);
      component.setWidth(component.isChild() ? width - 5 : width);

      if (openOutput > 0.7f) {
        component.drawScreen(mouseX, mouseY);

        if (component.isChild()) {
          RenderUtil.drawRect(
              x + 3.5f + slideOffset,
              component.getY() - 2.8f,
              x + 3.5f + slideOffset + 1,
              component.getY() - 2.8f + component.getHeight(),
              Color.gray.getRGB());
        }
      }

      yOffset += (float) (component.getHeight() * openOutput);
      this.height = yOffset;
    }

    GL11.glDisable(GL11.GL_SCISSOR_TEST);
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (isHovered) {
      if (visible && mouseY > PanelGui.posY + 45) {
        if (mouseButton == 0) {
          module.toggle();
        } else if (mouseButton == 1) {
          isExpanded = !isExpanded;
        }
      }
    } else if (isExpanded) {
      for (Component setting : settings) {
        setting.mouseClicked(mouseX, mouseY, mouseButton);
      }
    }
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int state) {
    if (isExpanded && !isHovered) {
      for (Component setting : settings) {
        setting.mouseReleased(mouseX, mouseY, state);
      }
    }
  }

  @Override
  public void keyTyped(char typedChar, int keyCode) {
    if (isExpanded && !isHovered) {
      for (Component setting : settings) {
        setting.keyTyped(typedChar, keyCode);
      }
    }
  }

  public Module getModule() {
    return module;
  }

  public void setModule(Module module) {
    this.module = module;
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

  public boolean isExpanded() {
    return isExpanded;
  }

  public void setExpanded(boolean expanded) {
    isExpanded = expanded;
  }

  public float getHeight() {
    return height;
  }

  public void setHeight(float height) {
    this.height = height;
  }

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public boolean isVisibleSetting() {
    return visibleSetting;
  }

  public void setVisibleSetting(boolean visibleSetting) {
    this.visibleSetting = visibleSetting;
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

  private String getKeyName(int key) {
    try {
      return Keyboard.getKeyName(key);
    } catch (Exception e) {
      return "None";
    }
  }
}
