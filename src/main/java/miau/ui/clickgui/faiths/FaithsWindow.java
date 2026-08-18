package miau.ui.clickgui.faiths;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.List;
import miau.Miau;
import miau.module.Module;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class FaithsWindow {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final DecimalFormat FLOAT_POINT_FORMAT = new DecimalFormat("0.00");

  private final String category;
  private float x, y;
  private int prevMouseX, prevMouseY;
  private boolean leftMouseClicked = false, rightMouseClicked = false, expand = true;
  private boolean dragging = false;
  private float scrollY = 0F, targetScrollY = 0F;
  private float lastRenderHeight = 200F;

  private String draggingPropertyName = null;

  private static final int PANEL_WIDTH = 100;
  private static final int TITLE_HEIGHT = 13;
  private static final int MODULE_HEIGHT = 11;
  private static final int VALUE_HEIGHT = 11;
  private static final Color ACCENT_COLOR = new Color(164, 53, 144);
  private static final Color BG_COLOR = new Color(25, 25, 25);
  private static final Color MODULE_BG = new Color(36, 36, 36);
  private static final Color EXPANDED_BG = new Color(17, 17, 17);

  public FaithsWindow(String category, float x, float y) {
    this.category = category;
    this.x = x;
    this.y = y;
  }

  private boolean mouseHovered(
      float x, float y, float width, float height, int mouseX, int mouseY) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }

  private boolean isInPanelBounds(float localY, float elementHeight) {
    return localY + elementHeight > TITLE_HEIGHT && localY < lastRenderHeight;
  }

  private void drawRect(float x, float y, float w, float h, Color color) {
    RenderUtil.drawRect(x, y, x + w, y + h, color.getRGB());
  }

  private void drawRect(float x, float y, float w, float h, int color) {
    RenderUtil.drawRect(x, y, x + w, y + h, color);
  }

  public boolean onScroll(int dWheel, int mouseX, int mouseY) {
    if (mouseHovered(x, y, PANEL_WIDTH, lastRenderHeight, mouseX, mouseY)) {
      if (dWheel > 0) {
        targetScrollY += 25F;
      } else if (dWheel < 0) {
        targetScrollY -= 25F;
      }
      return true;
    }
    return false;
  }

  private void scissor(double x, double y, double width, double height) {
    ScaledResolution sr = new ScaledResolution(mc);
    final double scale = sr.getScaleFactor();
    y = sr.getScaledHeight() - y;
    x *= scale;
    y *= scale;
    width *= scale;
    height *= scale;
    GL11.glScissor((int) x, (int) (y - height), (int) width, (int) height);
  }

  protected void renderWindow(int mouseX, int mouseY) {
    if (!Mouse.isButtonDown(0)) {
      draggingPropertyName = null;
    }

    if (Mouse.isButtonDown(0)) {
      if (dragging) {
        this.x += mouseX - prevMouseX;
        this.y += mouseY - prevMouseY;
      } else if (mouseHovered(x, y, PANEL_WIDTH, TITLE_HEIGHT, mouseX, mouseY)) {
        dragging = true;
      }
    } else {
      dragging = false;
    }
    prevMouseX = mouseX;
    prevMouseY = mouseY;

    GL11.glPushMatrix();
    GL11.glTranslatef(x, y, 0);

    List<Module> modules = Miau.moduleManager.getModulesByCategory().get(category);
    float height = 15F;
    if (expand && modules != null) {
      for (Module module : modules) {
        height += MODULE_HEIGHT;
        if (isModuleExpanded(module)) {
          for (Property<?> value : module.getValues()) {
            if (!value.isVisible()) continue;
            height += VALUE_HEIGHT;
          }
        }
      }
    }

    ScaledResolution sr = new ScaledResolution(mc);
    float maxWindowHeight = Math.min(220F, Math.max(100F, sr.getScaledHeight() - y - 10F));
    float renderHeight = Math.min(height, maxWindowHeight);
    lastRenderHeight = renderHeight;

    float maxScroll = Math.min(0F, -(height - renderHeight));
    if (targetScrollY > 0F) targetScrollY = 0F;
    if (targetScrollY < maxScroll) targetScrollY = maxScroll;
    scrollY = miau.util.math.MathUtil.lerp(scrollY, targetScrollY, 0.2f);
    if (scrollY > 0F) scrollY = 0F;
    if (scrollY < maxScroll) scrollY = maxScroll;

    Color themeAccent =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(new miau.util.vector.Vector2d(x, y));
    RenderUtil.drawOutLineRect(0F, 0F, PANEL_WIDTH, renderHeight, 1F, BG_COLOR, themeAccent);

    Font titleFont = FontRepository.getHudFont(15);
    titleFont.draw(category.toLowerCase(), 5, 3, -1);

    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    scissor(x, y + TITLE_HEIGHT, PANEL_WIDTH, renderHeight - TITLE_HEIGHT);

    float moduleY = 11F + scrollY;
    if (modules != null && expand) {
      for (Module module : modules) {
        boolean expanded = isModuleExpanded(module);
        Color c1 =
            miau.util.render.Themes.getCurrentTheme()
                .getAccentColor(new miau.util.vector.Vector2d(x, y + moduleY));
        Color c2 =
            miau.util.render.Themes.getCurrentTheme()
                .getAccentColor(
                    new miau.util.vector.Vector2d(x + PANEL_WIDTH, y + moduleY + MODULE_HEIGHT));

        if (!expanded) {
          drawRect(3, moduleY, PANEL_WIDTH - 5, MODULE_HEIGHT, MODULE_BG);
          if (module.isEnabled()) {
            RenderUtil.drawHorizontalGradientRect(
                3, moduleY, 3 + PANEL_WIDTH - 5, moduleY + MODULE_HEIGHT, c1.getRGB(), c2.getRGB());
          }
        }

        if (isInPanelBounds(moduleY, MODULE_HEIGHT)
            && mouseHovered(x, y + moduleY, PANEL_WIDTH, MODULE_HEIGHT, mouseX, mouseY)) {
          if (!expanded && !module.isEnabled()) {
            drawRect(3, moduleY, PANEL_WIDTH - 5, MODULE_HEIGHT, new Color(255, 255, 255, 50));
          }
          if (Mouse.isButtonDown(1)) {
            if (!rightMouseClicked) {
              if (!module.getValues().isEmpty()) toggleModuleExpanded(module);
              rightMouseClicked = true;
            }
          } else {
            rightMouseClicked = false;
          }
          if (Mouse.isButtonDown(0)) {
            if (!leftMouseClicked) {
              module.toggle();
              leftMouseClicked = true;
            }
          } else {
            leftMouseClicked = false;
          }
        }

        Font moduleFont = FontRepository.getHudFont(13);
        int textColor;
        if (module.isEnabled()) {
          textColor = expanded ? c1.getRGB() : RenderUtil.getContrastTextColor(c1);
        } else {
          textColor = new Color(160, 160, 160).getRGB();
        }
        moduleFont.draw(
            module.getName().toLowerCase(),
            PANEL_WIDTH - 3 - moduleFont.width(module.getName().toLowerCase()),
            moduleY + 2,
            textColor);

        if (expanded) {
          for (Property<?> value : module.getValues()) {
            if (!value.isVisible()) continue;
            moduleY += MODULE_HEIGHT;
            renderValue(value, moduleY, mouseX, mouseY);
          }
        }
        moduleY += MODULE_HEIGHT;
      }
    }

    GL11.glDisable(GL11.GL_SCISSOR_TEST);

    if (mouseHovered(x, y, PANEL_WIDTH, TITLE_HEIGHT, mouseX, mouseY)) {
      if (Mouse.isButtonDown(1)) {
        if (!rightMouseClicked) {
          rightMouseClicked = true;
          expand = !expand;
        }
      } else {
        rightMouseClicked = false;
      }
    }

    GL11.glPopMatrix();
  }

  private void renderValue(Property<?> value, float moduleY, int mouseX, int mouseY) {
    if (!value.isVisible()) return;

    if (value instanceof BooleanProperty) {
      renderBoolean((BooleanProperty) value, moduleY, mouseX, mouseY);
    } else if (value instanceof ModeProperty) {
      renderMode((ModeProperty) value, moduleY, mouseX, mouseY);
    } else if (value instanceof FloatProperty) {
      renderFloat((FloatProperty) value, moduleY, mouseX, mouseY);
    } else if (value instanceof IntProperty) {
      renderInt((IntProperty) value, moduleY, mouseX, mouseY);
    } else if (value instanceof PercentProperty) {
      renderPercent((PercentProperty) value, moduleY, mouseX, mouseY);
    } else if (value instanceof ColorProperty) {
      renderColor((ColorProperty) value, moduleY, mouseX, mouseY);
    }
  }

  private void renderBoolean(BooleanProperty prop, float moduleY, int mouseX, int mouseY) {
    if (isInPanelBounds(moduleY, MODULE_HEIGHT)
        && mouseHovered(x, y + moduleY, PANEL_WIDTH, MODULE_HEIGHT, mouseX, mouseY)) {
      if (Mouse.isButtonDown(0)) {
        if (!leftMouseClicked) {
          prop.setValue(!prop.getValue());
          leftMouseClicked = true;
        }
      } else {
        leftMouseClicked = false;
      }
    }
    Color c1 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(new miau.util.vector.Vector2d(x, y + moduleY));
    if (prop.getValue()) {
      Color c2 =
          miau.util.render.Themes.getCurrentTheme()
              .getAccentColor(
                  new miau.util.vector.Vector2d(x + PANEL_WIDTH, y + moduleY + MODULE_HEIGHT));
      RenderUtil.drawHorizontalGradientRect(
          3, moduleY, 3 + PANEL_WIDTH - 5, moduleY + MODULE_HEIGHT, c1.getRGB(), c2.getRGB());
    }
    Font font = FontRepository.getHudFont(13);
    int textColor = prop.getValue() ? RenderUtil.getContrastTextColor(c1) : 0xffffffff;
    font.draw(prop.getName(), 5, moduleY + 2, textColor);
  }

  private void renderMode(ModeProperty prop, float moduleY, int mouseX, int mouseY) {
    if (isInPanelBounds(moduleY, MODULE_HEIGHT)
        && mouseHovered(x, y + moduleY, PANEL_WIDTH, MODULE_HEIGHT, mouseX, mouseY)) {
      if (Mouse.isButtonDown(0)) {
        if (!leftMouseClicked) {
          prop.nextMode();
          leftMouseClicked = true;
        }
      } else {
        leftMouseClicked = false;
      }
    }
    Font font = FontRepository.getHudFont(13);
    font.draw(prop.getName(), 5, moduleY + 2, 0xffffffff);
    font.draw(
        prop.getModeString(), PANEL_WIDTH - 5 - font.width(prop.getModeString()), moduleY + 2, -1);
  }

  private void renderFloat(FloatProperty prop, float moduleY, int mouseX, int mouseY) {
    Font font = FontRepository.getHudFont(13);
    float valueWidth = PANEL_WIDTH - 5;
    float ratio = (prop.getValue() - prop.getMinimum()) / (prop.getMaximum() - prop.getMinimum());
    Color c1 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(new miau.util.vector.Vector2d(x, y + moduleY));
    Color c2 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(
                new miau.util.vector.Vector2d(x + valueWidth * ratio, y + moduleY + VALUE_HEIGHT));
    boolean hovered =
        isInPanelBounds(moduleY, MODULE_HEIGHT)
            && mouseHovered(x, y + moduleY, valueWidth, MODULE_HEIGHT, mouseX, mouseY);

    if (hovered && Mouse.isButtonDown(0) && draggingPropertyName == null) {
      draggingPropertyName = prop.getName();
    }

    if (prop.getName().equals(draggingPropertyName) && Mouse.isButtonDown(0)) {
      float newRatio = Math.max(0, Math.min(1, (mouseX - x) / valueWidth));
      float newVal = prop.getMinimum() + newRatio * (prop.getMaximum() - prop.getMinimum());
      prop.setValue(newVal);
      ratio = newRatio;
    }

    RenderUtil.drawHorizontalGradientRect(
        3, moduleY, 3 + valueWidth * ratio, moduleY + VALUE_HEIGHT, c1.getRGB(), c2.getRGB());
    int textColor = ratio > 0.3f ? RenderUtil.getContrastTextColor(c1) : 0xffffffff;
    font.draw(prop.getName(), 5, moduleY + 2, textColor);
    font.drawCentered(
        FLOAT_POINT_FORMAT.format(prop.getValue()), valueWidth, moduleY + 2, textColor);
  }

  private void renderInt(IntProperty prop, float moduleY, int mouseX, int mouseY) {
    Font font = FontRepository.getHudFont(13);
    float valueWidth = PANEL_WIDTH - 5;
    float ratio =
        (float) (prop.getValue() - prop.getMinimum())
            / (float) (prop.getMaximum() - prop.getMinimum());
    Color c1 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(new miau.util.vector.Vector2d(x, y + moduleY));
    Color c2 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(
                new miau.util.vector.Vector2d(x + valueWidth * ratio, y + moduleY + VALUE_HEIGHT));
    boolean hovered =
        isInPanelBounds(moduleY, MODULE_HEIGHT)
            && mouseHovered(x, y + moduleY, valueWidth, MODULE_HEIGHT, mouseX, mouseY);

    if (hovered && Mouse.isButtonDown(0) && draggingPropertyName == null) {
      draggingPropertyName = prop.getName();
    }

    if (prop.getName().equals(draggingPropertyName) && Mouse.isButtonDown(0)) {
      float newRatio = Math.max(0, Math.min(1, (mouseX - x) / valueWidth));
      int newVal =
          Math.round(prop.getMinimum() + newRatio * (prop.getMaximum() - prop.getMinimum()));
      prop.setValue(newVal);
      ratio = newRatio;
    }

    RenderUtil.drawHorizontalGradientRect(
        3, moduleY, 3 + valueWidth * ratio, moduleY + VALUE_HEIGHT, c1.getRGB(), c2.getRGB());
    int textColor = ratio > 0.3f ? RenderUtil.getContrastTextColor(c1) : 0xffffffff;
    font.draw(prop.getName(), 5, moduleY + 2, textColor);
    font.drawCentered(String.valueOf(prop.getValue()), valueWidth, moduleY + 2, textColor);
  }

  private void renderPercent(PercentProperty prop, float moduleY, int mouseX, int mouseY) {
    Font font = FontRepository.getHudFont(13);
    float valueWidth = PANEL_WIDTH - 5;
    float ratio = prop.getValue() / 100.0F;
    Color c1 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(new miau.util.vector.Vector2d(x, y + moduleY));
    Color c2 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(
                new miau.util.vector.Vector2d(x + valueWidth * ratio, y + moduleY + VALUE_HEIGHT));
    boolean hovered =
        isInPanelBounds(moduleY, MODULE_HEIGHT)
            && mouseHovered(x, y + moduleY, valueWidth, MODULE_HEIGHT, mouseX, mouseY);

    if (hovered && Mouse.isButtonDown(0) && draggingPropertyName == null) {
      draggingPropertyName = prop.getName();
    }

    if (prop.getName().equals(draggingPropertyName) && Mouse.isButtonDown(0)) {
      float newRatio = Math.max(0, Math.min(1, (mouseX - x) / valueWidth));
      prop.setValue(newRatio * 100.0F);
      ratio = newRatio;
    }

    RenderUtil.drawHorizontalGradientRect(
        3, moduleY, 3 + valueWidth * ratio, moduleY + VALUE_HEIGHT, c1.getRGB(), c2.getRGB());
    int textColor = ratio > 0.3f ? RenderUtil.getContrastTextColor(c1) : 0xffffffff;
    font.draw(prop.getName(), 5, moduleY + 2, textColor);
    font.drawCentered(
        String.valueOf((int) prop.getValue()) + "%", valueWidth, moduleY + 2, textColor);
  }

  private void renderColor(ColorProperty prop, float moduleY, int mouseX, int mouseY) {
    Font font = FontRepository.getHudFont(13);
    drawRect(PANEL_WIDTH - 12, moduleY, 9, 9, new Color(prop.getValue()));
    font.draw(prop.getName(), 5, moduleY + 1, 0xffffffff);
  }

  private boolean isModuleExpanded(Module module) {
    String key = "faiths_expanded_" + module.getName();
    java.util.Map<String, Boolean> state = getExpandedState();
    return state.containsKey(key) && state.get(key);
  }

  private void toggleModuleExpanded(Module module) {
    String key = "faiths_expanded_" + module.getName();
    java.util.Map<String, Boolean> state = getExpandedState();
    state.put(key, !state.getOrDefault(key, false));
  }

  private java.util.Map<String, Boolean> getExpandedState() {
    return faithsExpandedState;
  }

  private static final java.util.Map<String, Boolean> faithsExpandedState =
      new java.util.HashMap<>();

  public void mouseReleased(int mouseX, int mouseY, int state) {
    dragging = false;
    draggingPropertyName = null;
  }

  public String getCategory() {
    return category;
  }
}
