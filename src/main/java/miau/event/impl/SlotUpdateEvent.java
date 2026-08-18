package miau.event.impl;

import miau.event.callables.EventCancellable;

public class SlotUpdateEvent extends EventCancellable {
  public int slot;

  public SlotUpdateEvent(int slot) {
    this.slot = slot;
  }
}
