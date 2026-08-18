package miau.event.impl;

import miau.event.Event;

public class PickEvent implements Event {
  private double range;

  public PickEvent(double double1) {
    this.range = double1;
  }

  public double getRange() {
    return this.range;
  }

  public void setRange(double double1) {
    this.range = double1;
  }
}
