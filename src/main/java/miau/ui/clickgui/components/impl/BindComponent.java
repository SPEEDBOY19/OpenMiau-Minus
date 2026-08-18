package miau.ui.clickgui.components.impl;

import java.awt.Color;
import miau.ui.clickgui.components.Component;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import miau.util.render.Themes;
import miau.util.vector.Vector2d;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class BindComponent extends Component {
  private static final String EYE_ICON_PATH = "/assets/keystrokesmod/textures/gui/eye.png";
  private static final String EYE_OFF_ICON_PATH = "/assets/keystrokesmod/textures/gui/eye_off.png";
  private static final int EYE_ICON_PADDING = 2;

  public boolean isBinding;
  public ModuleComponent moduleComponent;
  public float o;
  public float x;
  private float y;
  public float xOffset;

  public BindComponent(ModuleComponent moduleComponent, float o) {
    this.moduleComponent = moduleComponent;
    this.x =
        moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
    this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
    this.o = o;
  }

  public void updateHeight(float n) {
    this.o = n;
  }

  @Override
  public float getOffset() {
    return o;
  }

  @Override
  public boolean isBaseVisible() {
    return true;
  }

  public void render() {
    Font renderer = FontRepository.getMinecraftFont();
    GL11.glPushMatrix();
    GL11.glScaled(0.5D, 0.5D, 0.5D);
    this.drawString(
        renderer,
        this.isBinding ? "Press a key..." : "Current bind: '\u00a7e" + getKeyAsStr() + "\u00a7r'");
    GL11.glPopMatrix();

    int iconSize = getEyeIconSize();
    float iconX = getEyeIconX(iconSize);
    float textHeight = renderer.getFontHeight() * 0.5f;
    float iconY = getRenderTextY() + (textHeight - iconSize) / 2f;

    int themeColor =
        !moduleComponent.mod.isHidden()
            ? Themes.getCurrentTheme().getAccentColor(new Vector2d(this.x, this.y)).getRGB()
            : Color.GRAY.getRGB();

    String iconPath = moduleComponent.mod.isHidden() ? EYE_OFF_ICON_PATH : EYE_ICON_PATH;
    RenderUtil.drawIcon(RenderUtil.getIcon(iconPath), iconX, iconY, iconSize, themeColor);
  }

  public void drawScreen(int x, int y) {
    this.y = moduleComponent.categoryComponent.getModuleY() + o;
    this.x = moduleComponent.categoryComponent.getX();
  }

  public boolean onClick(int x, int y, int button) {
    if (!overSetting(x, y) || !moduleComponent.isOpened || !moduleComponent.isVisible(this))
      return false;
    if (button == 0 && overEyeIcon(x, y)) {
      moduleComponent.mod.setHidden(!moduleComponent.mod.isHidden());
      return true;
    }
    if (button == 0 && overBindText(x, y)) {
      isBinding = !isBinding;
      return true;
    }
    if (button > 1 && isBinding) {
      moduleComponent.mod.setKey(button + 1000);
      isBinding = false;
      return true;
    }
    return false;
  }

  private boolean overEyeIcon(int x, int y) {
    int iconSize = getEyeIconSize();
    float iconX = getEyeIconX(iconSize);
    float iconY = getEyeIconY(iconSize);
    return x >= iconX && x < iconX + iconSize && y >= iconY && y < iconY + iconSize;
  }

  private float getBindTextX() {
    return moduleComponent.categoryComponent.getX() + 4f + (xOffset * 0.5f);
  }

  private float getBindTextY() {
    return moduleComponent.categoryComponent.getModuleY() + o + 3f;
  }

  private float getRenderTextY() {
    return moduleComponent.categoryComponent.getY() + o + 3f;
  }

  private String getBindDisplayString() {
    return isBinding ? "Press a key..." : "Current bind: '\u00a7e" + getKeyAsStr() + "\u00a7r'";
  }

  private boolean overBindText(int mouseX, int mouseY) {
    String text = getBindDisplayString();
    Font renderer = FontRepository.getMinecraftFont();

    float left = getBindTextX();
    float top = getBindTextY();
    float width = renderer.getStringWidth(text) * 0.5f;
    float height = renderer.getFontHeight() * 0.5f;

    return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
  }

  private int getEyeIconSize() {
    int fontH = Math.round(FontRepository.getMinecraftFont().getFontHeight() * 0.5f);
    return Math.max(6, fontH - 1);
  }

  private float getEyeIconX(int iconSize) {
    return moduleComponent.categoryComponent.getX()
        + moduleComponent.categoryComponent.getWidth()
        - iconSize
        - EYE_ICON_PADDING;
  }

  private float getEyeIconY(int iconSize) {
    float textY = getBindTextY();
    float textHeight = FontRepository.getMinecraftFont().getFontHeight() * 0.5f;
    return textY + (textHeight - iconSize) / 2f;
  }

  public void onScroll(int scroll) {
    if (!isBinding || scroll == 0) return;
    moduleComponent.mod.setKey(scroll > 0 ? 1069 : 1070);
    isBinding = false;
  }

  public void keyTyped(char t, int keybind) {
    if (!isBinding) return;
    if (keybind == Keyboard.KEY_0 || keybind == Keyboard.KEY_ESCAPE) {
      moduleComponent.mod.setKey(0);
    } else {
      moduleComponent.mod.setKey(keybind);
    }
    isBinding = false;
  }

  public boolean overSetting(int mouseX, int mouseY) {
    float rowX = moduleComponent.categoryComponent.getX();
    float rowY = moduleComponent.categoryComponent.getModuleY() + o;
    float rowW = moduleComponent.categoryComponent.getWidth();
    return mouseX > rowX && mouseX < rowX + rowW && mouseY > rowY - 1 && mouseY < rowY + 12;
  }

  public String getKeyAsStr() {
    int key = moduleComponent.mod.getKey();
    return key >= 1000
        ? ((key == 1069 || key == 1070) ? getScroll(key) : "M" + (key - 1000))
        : Keyboard.getKeyName(key);
  }

  public String getScroll(int key) {
    if (key == 1069) return "MScrollUp";
    if (key == 1070) return "MScrollDown";
    return "&cERROR";
  }

  @Override
  public float getHeightF() {
    return 16f;
  }

  @Override
  public int getHeight() {
    return Math.round(getHeightF());
  }

  private void drawString(Font renderer, String s) {
    int color = Themes.getCurrentTheme().getAccentColor(new Vector2d(this.x, this.y)).getRGB();
    renderer.draw(
        s,
        (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset,
        (float) ((this.moduleComponent.categoryComponent.getY() + this.o + 3) * 2),
        color,
        true);
  }

  public void onGuiClosed() {
    isBinding = false;
  }
}
