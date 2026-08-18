package miau.event.impl;

import miau.event.callables.EventCancellable;

public class WindowClickEvent extends EventCancellable {
  private final int windowsId;
  private final int slotId;
  private final int mouseButtonClicked;
  private final int mode;

  public WindowClickEvent(int windowsId, int slotId, int mouseButtonClicked, int mode) {
    this.windowsId = windowsId;
    this.slotId = slotId;
    this.mouseButtonClicked = mouseButtonClicked;
    this.mode = mode;
  }

  public int getWindowsId() {
    return this.windowsId;
  }

  public int getSlotId() {
    return this.slotId;
  }

  public int getMouseButtonClicked() {
    return this.mouseButtonClicked;
  }

  public int getMode() {
    return this.mode;
  }
}
