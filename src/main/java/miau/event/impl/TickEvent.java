package miau.event.impl;

import miau.event.callables.EventCancellable;
import miau.event.types.EventType;

public class TickEvent extends EventCancellable {
  private final EventType type;

  public TickEvent(EventType type) {
    this.type = type;
  }

  public EventType getType() {
    return this.type;
  }
}
