package miau.util.dragging;

import miau.util.time.TimerUtil;

public class Drag {
  public float positionX, positionY;
  public float targetPositionX, targetPositionY;
  public float scaleX, scaleY;
  public float offsetX, offsetY;
  public boolean dragging = false;
  public TimerUtil stopWatch = new TimerUtil();
  private boolean wasButtonDown = false;

  public Drag(float initialX, float initialY, float scaleX, float scaleY) {
    this.positionX = this.targetPositionX = initialX;
    this.positionY = this.targetPositionY = initialY;
    this.scaleX = scaleX;
    this.scaleY = scaleY;
  }

  public void onClick(int mouseButton) {
    float[] mouse = MouseUtil.getMouse();
    float mouseX = mouse[0];
    float mouseY = mouse[1];

    if (mouseOver(mouseX, mouseY) && mouseButton == 0) {
      dragging = true;
      offsetX = targetPositionX - mouseX;
      offsetY = targetPositionY - mouseY;
    }
  }

  public void interpolate() {
    if (Math.abs(positionX - targetPositionX) > 0.01F
        || Math.abs(positionY - targetPositionY) > 0.01F) {
      long elapsed = stopWatch.getElapsedTime();
      for (int i = 0; i <= elapsed; ++i) {
        positionX = (positionX * 38.0F + targetPositionX) / 39.0F;
        positionY = (positionY * 38.0F + targetPositionY) / 39.0F;
      }
    }
    stopWatch.reset();
  }

  public void render() {
    float[] mouse = MouseUtil.getMouse();
    float mouseX = mouse[0];
    float mouseY = mouse[1];

    boolean buttonDown = org.lwjgl.input.Mouse.isButtonDown(0);
    if (buttonDown && !wasButtonDown) {
      onClick(0);
    } else if (!buttonDown && wasButtonDown) {
      release();
    }
    wasButtonDown = buttonDown;

    if (dragging) {
      targetPositionX = mouseX + offsetX;
      targetPositionY = mouseY + offsetY;
    }

    interpolate();
  }

  public void release() {
    dragging = false;
  }

  public boolean mouseOver(float mouseX, float mouseY) {
    return mouseX >= positionX
        && mouseX <= positionX + scaleX
        && mouseY >= positionY
        && mouseY <= positionY + scaleY;
  }
}
