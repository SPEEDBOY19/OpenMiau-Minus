package miau.module.modules.render.hud;

import java.awt.Color;
import lombok.Getter;
import lombok.Setter;
import miau.module.Module;
import miau.module.modules.render.HUD;
import miau.util.vector.Vector2d;
import org.lwjgl.input.Keyboard;

public final class InterfaceComponent {

  public Module module;
  public Vector2d position = new Vector2d(5000, 0), targetPosition = new Vector2d(5000, 0);
  public float animationTime;
  public String tag = "";
  public float nameWidth = 0, tagWidth;
  @Setter @Getter public Color color = Color.WHITE;
  public String translatedName = "";
  public boolean hidden = false;

  public String displayName = "";
  public String displayTag = "";
  public boolean hasTag;

  public float getTotalWidth() {
    return nameWidth + tagWidth;
  }

  public InterfaceComponent(final Module module) {
    this.module = module;
  }

  public boolean shouldDisplay(HUD hudInstance) {
    String name = this.module.getName().toLowerCase();

    switch (hudInstance.modulesToShow.getValue()) {
      case 0:
        return true;

      case 1:
        if (name.equals("clickgui") || name.equals("gui") || name.equals("hud")) {
          return false;
        }
        String category = this.module.getCategory();
        return category == null || !category.equalsIgnoreCase("render");

      case 2:
        if (name.equals("clickgui") || name.equals("gui") || name.equals("hud")) {
          return false;
        }
        return this.module.getKey() != 0 && this.module.getKey() != Keyboard.KEY_NONE;

      default:
        return true;
    }
  }
}
