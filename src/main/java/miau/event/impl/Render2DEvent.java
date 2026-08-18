package miau.event.impl;

import miau.event.Event;

public class Render2DEvent implements Event {
  private final float partialTicks;

  public Render2DEvent(float float1) {
    this.partialTicks = float1;
  }

  public float getPartialTicks() {
    return this.partialTicks;
  }
}
