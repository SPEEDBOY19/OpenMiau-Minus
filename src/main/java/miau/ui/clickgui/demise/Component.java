package miau.ui.clickgui.demise;

import java.awt.*;
import miau.util.render.RenderUtil;

public class Component implements IComponent {
  private float x, y, width, height;
  private Color color = new Color(0x4CAF50);
  private int colorRGB = color.getRGB();

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

  public float getWidth() {
    return width;
  }

  public void setWidth(float width) {
    this.width = width;
  }

  public float getHeight() {
    return height;
  }

  public void setHeight(float height) {
    this.height = height;
  }

  public Color getColor() {
    return color;
  }

  public void setColor(Color color) {
    this.color = color;
  }

  public int getColorRGB() {
    return colorRGB;
  }

  public void setColorRGB(int colorRGB) {
    this.colorRGB = colorRGB;
  }

  public void drawBackground(Color color) {
    RenderUtil.drawRect(x, y, x + width, y + height, color.getRGB());
  }

  public boolean isHovered(float mouseX, float mouseY) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }

  public boolean isHovered(float mouseX, float mouseY, float height) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }

  public boolean isVisible() {
    return true;
  }

  public boolean isChild() {
    return false;
  }
}
