package miau.event.impl;

import miau.event.Event;

public class PostRaytraceEvent implements Event {
  public final float partialTicks;

  public PostRaytraceEvent(float partialTicks) {
    this.partialTicks = partialTicks;
  }
}
