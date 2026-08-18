package miau.ui.clickgui.animation;

import miau.util.animation.Easing;

public class ScrollOffsetAnimation {
  private float from;
  private float to;
  private long startMs;
  private final long durationMs;
  private final Easing easing;
  private boolean finished;

  public ScrollOffsetAnimation(long durationMs) {
    this(durationMs, Easing.EASE_OUT_EXPO);
  }

  public ScrollOffsetAnimation(long durationMs, Easing easing) {
    this.durationMs = durationMs;
    this.easing = easing;
    this.from = 0f;
    this.to = 0f;
    this.startMs = 0L;
    this.finished = true;
  }

  public void reset(float value) {
    this.from = value;
    this.to = value;
    this.startMs = 0L;
    this.finished = true;
  }

  public void setTarget(float newTarget) {
    this.from = getValue();
    this.to = newTarget;
    this.startMs = System.currentTimeMillis();
    this.finished = false;
  }

  public void extend(float delta) {
    this.from = getValue();
    this.to += delta;
    this.startMs = System.currentTimeMillis();
    this.finished = false;
  }

  public void clampTarget(float min, float max) {
    this.to = Math.max(min, Math.min(max, this.to));
  }

  public float getValue() {
    if (startMs == 0L) {
      return to;
    }
    long elapsed = System.currentTimeMillis() - startMs;
    if (elapsed >= durationMs) {
      startMs = 0L;
      from = to;
      finished = true;
      return to;
    }

    float t = (float) elapsed / (float) durationMs;
    float eased = (float) easing.apply(t);

    return from + (to - from) * eased;
  }

  public boolean isAnimating() {
    return !finished;
  }

  public float getTarget() {
    return to;
  }

  public Easing getEasing() {
    return easing;
  }

  public boolean isFinished() {
    return finished;
  }
}
