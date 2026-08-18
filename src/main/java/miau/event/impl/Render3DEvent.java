package miau.event.impl;

import miau.event.Event;

public class Render3DEvent implements Event {
  private final float partialTicks;

  public Render3DEvent(float partialTicks) {
    this.partialTicks = partialTicks;
  }

  public float getPartialTicks() {
    return this.partialTicks;
  }
}
