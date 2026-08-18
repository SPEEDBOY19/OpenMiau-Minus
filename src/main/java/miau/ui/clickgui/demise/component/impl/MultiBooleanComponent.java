package miau.ui.clickgui.demise.component.impl;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import miau.property.properties.BooleanProperty;
import miau.ui.clickgui.demise.Component;
import miau.util.demise.MouseUtils;
import miau.util.font.FontRepository;

public class MultiBooleanComponent extends Component {
  private final String name;
  private final List<BooleanProperty> values;
  private final Map<BooleanProperty, Float> select = new HashMap<BooleanProperty, Float>();

  public MultiBooleanComponent(String name, List<BooleanProperty> values) {
    this.name = name;
    this.values = values;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    float heightoff = 4;
    float lineHeight = FontRepository.getFont("Inter Regular", 13f).height() + 2;

    FontRepository.getFont("Inter SemiBold", 15f)
        .draw(name, (double) (getX() + 4), (double) (getY() + 2), -1);

    for (BooleanProperty boolValue : values) {
      if (!select.containsKey(boolValue)) {
        select.put(boolValue, 0f);
      }
      float anim = select.get(boolValue);
      anim = animate(anim, boolValue.getValue() ? 1 : 0, 0.1f);
      select.put(boolValue, anim);

      Color color = interpolateColorC(new Color(128, 128, 128), Color.white, anim);
      FontRepository.getFont("Inter Regular", 13f)
          .draw(
              boolValue.getName(),
              (double) (getX() + 8),
              (double)
                  (getY() + FontRepository.getFont("Inter Regular", 15f).height() + 1 + heightoff),
              color.getRGB());

      heightoff += lineHeight;
    }

    setHeight(FontRepository.getFont("Inter Regular", 15f).height() + 4 + heightoff);
    super.drawScreen(mouseX, mouseY);
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouse) {
    float heightoff = 4;
    float lineHeight = FontRepository.getFont("Inter Regular", 13f).height() + 2;

    for (BooleanProperty boolValue : values) {
      if (MouseUtils.isHovered(
              getX() + 8,
              getY() + FontRepository.getFont("Inter Regular", 15f).height() + 1 + heightoff,
              (float)
                  FontRepository.getFont("Inter Regular", 13f).getStringWidth(boolValue.getName()),
              FontRepository.getFont("Inter Regular", 13f).height(),
              mouseX,
              mouseY)
          && mouse == 0) {
        boolValue.setValue(!boolValue.getValue());
      }
      heightoff += lineHeight;
    }
    super.mouseClicked(mouseX, mouseY, mouse);
  }

  @Override
  public boolean isVisible() {
    return true;
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
