package miau.ui.clickgui.components.impl;

import java.awt.Color;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import miau.module.Module;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.property.properties.TextProperty;
import miau.ui.clickgui.ClickGui;
import miau.ui.clickgui.components.Component;
import miau.util.animation.AnimationTimer;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class ModuleComponent extends Component {
  public Module mod;
  public CategoryComponent categoryComponent;
  public float yPos;
  public ArrayList<Component> settings;
  public boolean isOpened;
  private boolean hovering;
  private AnimationTimer hoverTimer;
  private boolean hoverStarted;
  private AnimationTimer smoothTimer;
  private float enableAlpha = 0f;
  private float smoothingY = 16f;
  private float animationStartY = 16f;
  private float animationTargetY = 16f;

  private static final IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(16);
  private static final int ORIGINAL_HOVER_ALPHA = 120;
  private static final int HOVER_COLOR = new Color(0, 0, 0, ORIGINAL_HOVER_ALPHA).getRGB();
  private static final int ENABLED_COLOR = new Color(24, 154, 255).getRGB();
  private static final int DISABLED_COLOR = new Color(192, 192, 192).getRGB();
  private final boolean categoryManager;

  public ModuleComponent(Module mod, CategoryComponent p, float yPos) {
    this(mod, p, yPos, false);
  }

  public ModuleComponent(Module mod, CategoryComponent p, float yPos, boolean categoryManager) {
    this.mod = mod;
    this.categoryComponent = p;
    this.yPos = yPos;
    this.settings = new ArrayList<>();
    this.categoryManager = categoryManager;
    this.isOpened = categoryManager;
    float collapsedHeight = getCollapsedHeight();
    this.smoothingY = collapsedHeight;
    this.animationStartY = collapsedHeight;
    this.animationTargetY = collapsedHeight;
    rebuildSettingsList();
  }

  private void rebuildSettingsList() {
    this.settings = new ArrayList<>();
    float y = yPos + getSettingStartOffset();
    if (mod != null) {
      ArrayList<Property<?>> props = miau.Miau.propertyManager.properties.get(mod.getClass());
      if (props != null) {
        for (int i = 0; i < props.size(); i++) {
          Property<?> v = props.get(i);
          if (!v.isVisible()) {
            continue;
          }
          if (v instanceof BooleanProperty && v.getName().startsWith("target-")) {
            java.util.List<BooleanProperty> groupProps = new java.util.ArrayList<>();
            groupProps.add((BooleanProperty) v);
            for (int j = i + 1; j < props.size(); j++) {
              Property<?> next = props.get(j);
              if (next instanceof BooleanProperty
                  && next.getName().startsWith("target-")
                  && next.isVisible()) {
                groupProps.add((BooleanProperty) next);
                i = j;
              } else {
                break;
              }
            }
            for (BooleanProperty prop : groupProps) {
              ButtonComponent c = new ButtonComponent(mod, prop, this, y);
              this.settings.add(c);
              y += 12;
            }
            continue;
          }
          if (v instanceof BooleanProperty) {
            ButtonComponent c = new ButtonComponent(mod, (BooleanProperty) v, this, y);
            this.settings.add(c);
            y += 12;
          } else if (v instanceof FloatProperty
              || v instanceof IntProperty
              || v instanceof PercentProperty
              || v instanceof ModeProperty) {
            SliderComponent s = new SliderComponent(v, this, y);
            this.settings.add(s);
            y += 12;
          } else if (v instanceof ColorProperty) {
            ColorComponent cc = new ColorComponent((ColorProperty) v, this, y);
            this.settings.add(cc);
            y += 12;
          } else if (v instanceof TextProperty) {
            StringComponent sc = new StringComponent((TextProperty) v, this, y);
            this.settings.add(sc);
            y += 12;
          }
        }
      }
    }
    if (!categoryManager) {
      this.settings.add(new BindComponent(this, y));
    }
  }

  public void reloadSettings() {
    boolean wasOpened = this.isOpened;
    Map<String, Boolean> groupExpandedStates = new HashMap<>();
    Map<Property<?>, Boolean> sliderHeldStates = new HashMap<>();
    Map<Property<?>, Boolean> sliderMinStates = new HashMap<>();
    Map<Property<?>, Boolean> sliderMaxStates = new HashMap<>();
    Map<Property<?>, Boolean> colorExpandedStates = new HashMap<>();
    Map<Property<?>, Boolean> modeExpandedStates = new HashMap<>();

    for (Component component : this.settings) {
      if (component instanceof GroupComponent) {
        groupExpandedStates.put(
            ((GroupComponent) component).getGroupName(), ((GroupComponent) component).isExpanded());
      } else if (component instanceof SliderComponent) {
        SliderComponent sliderComponent = (SliderComponent) component;
        sliderHeldStates.put(sliderComponent.property, sliderComponent.heldDown);
        sliderMinStates.put(sliderComponent.property, sliderComponent.draggingMin);
        sliderMaxStates.put(sliderComponent.property, sliderComponent.draggingMax);
        if (sliderComponent.property instanceof ModeProperty) {
          modeExpandedStates.put(sliderComponent.property, sliderComponent.isExpanded);
        }
      } else if (component instanceof ColorComponent) {
        ColorComponent colorComponent = (ColorComponent) component;
        colorExpandedStates.put(colorComponent.property, colorComponent.expanded);
      }
    }

    rebuildSettingsList();
    for (Component component : this.settings) {
      if (component instanceof SliderComponent) {
        SliderComponent sliderComponent = (SliderComponent) component;
        Boolean wasHeldDown = sliderHeldStates.get(sliderComponent.property);
        if (wasHeldDown != null) sliderComponent.heldDown = wasHeldDown;
        Boolean wasDraggingMin = sliderMinStates.get(sliderComponent.property);
        if (wasDraggingMin != null) sliderComponent.draggingMin = wasDraggingMin;
        Boolean wasDraggingMax = sliderMaxStates.get(sliderComponent.property);
        if (wasDraggingMax != null) sliderComponent.draggingMax = wasDraggingMax;
        if (sliderComponent.property instanceof ModeProperty) {
          Boolean wasExpanded = modeExpandedStates.get(sliderComponent.property);
          if (wasExpanded != null) {
            sliderComponent.restoreModeDropdownState(wasExpanded);
          }
        }
      } else if (component instanceof ColorComponent) {
        ColorComponent colorComponent = (ColorComponent) component;
        Boolean wasExpanded = colorExpandedStates.get(colorComponent.property);
        if (wasExpanded != null) {
          colorComponent.restoreExpandedState(wasExpanded);
        }
      } else if (component instanceof GroupComponent) {
        GroupComponent groupComponent = (GroupComponent) component;
        Boolean wasExpanded = groupExpandedStates.get(groupComponent.getGroupName());
        if (wasExpanded != null) {
          groupComponent.restoreExpandedState(wasExpanded);
        }
      }
    }
    restoreOpenState(wasOpened);
    updateSettingPositions();
  }

  public void restoreOpenState(boolean opened) {
    this.isOpened = categoryManager || opened;
    this.smoothTimer = null;
    float height = this.isOpened ? getHeightF() : getCollapsedHeight();
    this.smoothingY = height;
    this.animationStartY = height;
    this.animationTargetY = height;
  }

  public void updateAnimationState() {
    if (smoothTimer != null) {
      if (System.currentTimeMillis() - smoothTimer.last >= 280) {
        smoothTimer = null;
        smoothingY = animationTargetY;
        animationStartY = animationTargetY;
      } else {
        smoothingY = smoothTimer.getValueFloat(animationStartY, animationTargetY, 1);
        if (smoothingY == animationTargetY) {
          smoothTimer = null;
          animationStartY = animationTargetY;
        }
      }
    }
  }

  public void updateHeight(float newY) {
    this.yPos = newY;
    float y = this.yPos + getCollapsedHeight();
    for (Component co : this.settings) {
      if (!isVisibleBase(co)) {
        continue;
      }
      co.updateHeight(y);
      if (co instanceof SliderComponent) {
        ((SliderComponent) co).xOffset = 0;
      } else if (co instanceof ButtonComponent) {
        ((ButtonComponent) co).xOffset = 0;
      } else if (co instanceof BindComponent) {
        ((BindComponent) co).xOffset = 0;
      } else if (co instanceof ColorComponent) {
        ((ColorComponent) co).xOffset = 0;
      } else if (co instanceof GroupComponent) {
        ((GroupComponent) co).xOffset = 0;
      }
      y += getBaseComponentHeightF(co);
    }
  }

  public void render() {
    boolean isEnabled = this.mod != null && this.mod.isEnabled();
    enableAlpha = isEnabled ? 1f : 0f;

    if (hasModuleHeader() && (hovering || hoverTimer != null)) {
      double hoverAlpha =
          (hovering && hoverTimer != null)
              ? hoverTimer.getValueFloat(0, ORIGINAL_HOVER_ALPHA, 1)
              : (hoverTimer != null && !hovering)
                  ? ORIGINAL_HOVER_ALPHA - hoverTimer.getValueFloat(0, ORIGINAL_HOVER_ALPHA, 1)
                  : ORIGINAL_HOVER_ALPHA;
      if (hoverAlpha == 0) {
        hoverTimer = null;
      }
      // Removed module hover background as requested
    }

    if (hasModuleHeader() && enableAlpha > 0.01f) {
      // Removed module active background as requested
    }

    int r = (int) (192 + (255 - 192) * enableAlpha);
    int g = (int) (192 + (255 - 192) * enableAlpha);
    int b = (int) (192 + (255 - 192) * enableAlpha);
    int button_rgb = new Color(r, g, b).getRGB();

    Font titleRenderer = FontRepository.getMinecraftFont();

    if (hasModuleHeader()) {
      float textX = this.categoryComponent.getX() + 6;
      float textY = this.categoryComponent.getY() + this.yPos + 5;
      titleRenderer.draw(this.mod.getName(), textX, textY, button_rgb, true);
    }
    boolean scissorRequired = smoothTimer != null || this.isOpened;
    if (scissorRequired) {
      ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
      int scale = sr.getScaleFactor();
      double guiScale = ClickGui.getActiveRenderScale();
      float scrollOffset = this.categoryComponent.getModuleY() - this.categoryComponent.getY();
      double sx = this.categoryComponent.getX() - 2;
      double sy = this.categoryComponent.getY() + this.yPos + scrollOffset;
      double sw = this.categoryComponent.getWidth() + 4;
      double sh = smoothingY;

      if (ClickGui.openingScale != 1.0f) {
        double scaleFactor = ClickGui.openingScale;
        double centerX = sr.getScaledWidth() / 2.0;
        double centerY = sr.getScaledHeight() / 2.0;
        sx = centerX + (sx - centerX) * scaleFactor;
        sy = centerY + (sy - centerY) * scaleFactor;
        sw *= scaleFactor;
        sh *= scaleFactor;
      }

      int scissorX = (int) Math.floor(sx * guiScale * scale);
      int scissorY = (int) Math.floor((sr.getScaledHeight() - (sy + sh) * guiScale) * scale);
      int scissorW = (int) Math.ceil(sw * guiScale * scale);
      int scissorH = (int) Math.ceil(sh * guiScale * scale);
      pushScissor(scissorX, scissorY, scissorW, scissorH);
    }

    if (this.isOpened || smoothTimer != null) {
      renderSettings();
    }

    if (scissorRequired) {
      popScissor();
    }
  }

  private void renderSettings() {
    for (Component c : settings) {
      if (isVisibleBase(c)) {
        c.render();
      }
    }
  }

  public void renderOverlays() {
    for (Component c : settings) {
      if (c instanceof SliderComponent && isVisibleBase(c)) {
        SliderComponent slider = (SliderComponent) c;
        if (slider.isModeDropdownActive()) {
          ClickGui.activeModeDropdown = slider;
        }
      }
    }
  }

  @Override
  public float getHeightF() {
    if (smoothTimer != null) {
      return smoothingY;
    }
    if (!this.isOpened) {
      return getCollapsedHeight();
    }
    float h = getCollapsedHeight();
    for (Component c : this.settings) {
      h += getAnimatedComponentHeightF(c);
    }
    return h;
  }

  @Override
  public int getHeight() {
    return Math.round(getHeightF());
  }

  public void onSliderChange() {
    for (Component c : this.settings) {
      if (c instanceof SliderComponent) {
        ((SliderComponent) c).onSliderChange();
      }
    }
  }

  public float getScrollExtentHeightF() {
    if (isOpened || (smoothTimer != null && animationTargetY > 16f)) {
      float h = getCollapsedHeight();
      for (Component c : settings) {
        if (!isVisibleBase(c)) continue;
        h += getBaseComponentHeightF(c);
      }
      return h;
    }
    return getHeightF();
  }

  public void drawScreen(int x, int y) {
    for (Component c : this.settings) {
      c.drawScreen(x, y);
    }
    if (hasModuleHeader() && overModuleName(x, y) && this.categoryComponent.opened) {
      hovering = true;
      if (hoverTimer == null) {
        (hoverTimer = new AnimationTimer(75)).start();
        hoverStarted = true;
      }
    } else {
      if (hovering && hoverStarted) {
        (hoverTimer = new AnimationTimer(75)).start();
      }
      hoverStarted = false;
      hovering = false;
    }
  }

  public boolean onClick(int x, int y, int mouse) {
    if (hasModuleHeader() && this.overModuleName(x, y) && mouse == 0) {
      this.mod.toggle();
      return true;
    }

    if (hasModuleHeader() && this.overModuleName(x, y) && mouse == 1) {
      float currentHeight = smoothTimer != null ? smoothingY : (isOpened ? getHeightF() : 18f);
      this.animationStartY = currentHeight;
      this.isOpened = !this.isOpened;
      float targetHeight;
      if (this.isOpened) {
        float h = getCollapsedHeight();
        for (Component c : this.settings) {
          h += getAnimatedComponentHeightF(c);
        }
        targetHeight = h;
      } else {
        targetHeight = getCollapsedHeight();
      }
      this.animationTargetY = targetHeight;
      (this.smoothTimer = new AnimationTimer(250)).start();
      return true;
    }

    SliderComponent activeDropdown = getActiveModeDropdown();
    if (activeDropdown != null) {
      if (activeDropdown.onClick(x, y, mouse)) {
        return true;
      }
      if (!activeDropdown.isMouseOverModeDropdown(x, y)) {
        activeDropdown.collapseModeDropdown();
      }
      return true;
    }

    for (Component settingComponent : this.settings) {
      if (settingComponent.onClick(x, y, mouse)) {
        return true;
      }
    }
    return false;
  }

  public SliderComponent getActiveModeDropdown() {
    for (int i = this.settings.size() - 1; i >= 0; i--) {
      Component component = this.settings.get(i);
      if (component instanceof SliderComponent && isVisibleBase(component)) {
        SliderComponent slider = (SliderComponent) component;
        if (slider.isModeDropdownActive()) {
          return slider;
        }
      }
    }
    return null;
  }

  public void mouseReleased(int x, int y, int m) {
    for (Component c : this.settings) {
      c.mouseReleased(x, y, m);
    }
  }

  public void keyTyped(char t, int k) {
    for (Component c : this.settings) {
      c.keyTyped(t, k);
    }
  }

  public void onScroll(int scroll) {
    for (Component component : this.settings) {
      component.onScroll(scroll);
    }
  }

  public void onGuiClosed() {
    for (Component c : this.settings) {
      c.onGuiClosed();
    }
    smoothTimer = null;
    hoverTimer = null;
    float finalHeight = isOpened ? getHeightF() : getCollapsedHeight();
    smoothingY = finalHeight;
    animationStartY = finalHeight;
    animationTargetY = finalHeight;
  }

  public boolean overModuleName(int x, int y) {
    if (!hasModuleHeader()) {
      return false;
    }
    return x > this.categoryComponent.getX()
        && x < this.categoryComponent.getX() + this.categoryComponent.getWidth()
        && y > this.categoryComponent.getModuleY() + this.yPos
        && y < this.categoryComponent.getModuleY() + 18 + this.yPos;
  }

  public void updateSettingPositions() {
    this.categoryComponent.updateHeight();
  }

  public boolean isVisible(Component component) {
    return isVisibleBase(component);
  }

  private float getBaseComponentHeightF(Component component) {
    if (component instanceof SliderComponent) {
      return 16f;
    }
    if (component instanceof ColorComponent) {
      ColorComponent cc = (ColorComponent) component;
      float progress = cc.getAnimationProgress();
      return 12f + (cc.getExpandedHeight() - 12f) * progress;
    }
    if (component instanceof GroupComponent) {
      GroupComponent gc = (GroupComponent) component;
      return GroupComponent.GROUP_HEADER_HEIGHT
          + gc.getSubCount() * 12f * gc.getAnimationProgress();
    }
    return 12f;
  }

  private float getAnimatedComponentHeightF(Component component) {
    if (!isVisibleBase(component)) {
      return 0f;
    }
    return getBaseComponentHeightF(component);
  }

  private static final int MAX_SCISSOR_DEPTH = 4;
  private final int[][] scissorStack = new int[MAX_SCISSOR_DEPTH][5];
  private int scissorDepth = 0;

  private void pushScissor(int x, int y, int w, int h) {
    boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    int[] saved = scissorStack[scissorDepth++];
    if (wasEnabled) {
      SCISSOR_BOX.clear();
      GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
      saved[0] = 1;
      saved[1] = SCISSOR_BOX.get(0);
      saved[2] = SCISSOR_BOX.get(1);
      saved[3] = SCISSOR_BOX.get(2);
      saved[4] = SCISSOR_BOX.get(3);
      int ix = Math.max(saved[1], x);
      int iy = Math.max(saved[2], y);
      int iw = Math.max(0, Math.min(saved[1] + saved[3], x + w) - ix);
      int ih = Math.max(0, Math.min(saved[2] + saved[4], y + h) - iy);
      GL11.glScissor(ix, iy, iw, ih);
    } else {
      saved[0] = 0;
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(x, y, w, h);
    }
  }

  private void popScissor() {
    int[] saved = scissorStack[--scissorDepth];
    if (saved[0] == 1) {
      GL11.glScissor(saved[1], saved[2], saved[3], saved[4]);
    } else {
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
  }

  private boolean isVisibleBase(Component component) {
    return component.isBaseVisible();
  }

  private boolean hasModuleHeader() {
    return !categoryManager;
  }

  private float getCollapsedHeight() {
    return hasModuleHeader() ? 18f : 0f;
  }

  private float getSettingStartOffset() {
    return hasModuleHeader() ? 12f : 0f;
  }

  private boolean hasActualProperties() {
    if (this.mod == null) return false;
    ArrayList<Property<?>> props = miau.Miau.propertyManager.properties.get(this.mod.getClass());
    if (props == null || props.isEmpty()) return false;
    for (Property<?> p : props) {
      if (p.isVisible()) return true;
    }
    return false;
  }
}
