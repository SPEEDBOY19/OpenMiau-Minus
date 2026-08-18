package miau.event.impl;

import miau.event.Event;

public class JumpEvent implements Event {
  private float yaw;
  private float jumpoff;

  public JumpEvent(float yaw) {
    this.yaw = yaw;
  }

  public float getYaw() {
    return this.yaw;
  }

  public void setYaw(float yaw) {
    this.yaw = yaw;
  }

  public float getJumpoff() {
    return this.jumpoff;
  }

  public void setJumpoff(float jumpoff) {
    this.jumpoff = jumpoff;
  }
}
