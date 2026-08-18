package miau.util.animation;

public class AnimationTimer {
  public float updates;
  public long last;
  public float cached = Float.NaN;

  public AnimationTimer(float updates) {
    this.updates = updates;
  }

  public float getValueFloat(float begin, float end, int type) {
    if (!Float.isNaN(this.cached) && this.cached == end) {
      return this.cached;
    } else {
      float t = (float) (System.currentTimeMillis() - this.last) / this.updates;
      switch (type) {
        case 1:
          t = (float) Easing.EASE_OUT_EXPO.apply(t);
          break;
        case 2:
          t = (float) Easing.EASE_OUT_QUINT.apply(t);
          break;
        case 3:
          t = (float) Easing.EASE_OUT_ELASTIC.apply(t);
          break;
        case 4:
          t = (float) Easing.EASE_IN_OUT_QUAD.apply(t);
          break;
      }

      float value = begin + t * (end - begin);
      if ((end > begin && value > end) || (end < begin && value < end)) {
        value = end;
      }

      if (value == end) {
        this.cached = value;
      }

      return value;
    }
  }

  public int getValueInt(int begin, int end, int type) {
    return Math.round(this.getValueFloat((float) begin, (float) end, type));
  }

  public void start() {
    this.cached = Float.NaN;
    this.last = System.currentTimeMillis();
  }

  private float bounce(float t) {
    float i;
    double i2 = 7.5625D;
    double i3 = 2.75D;
    if ((double) t < 1.0D / i3) {
      i = (float) (i2 * (double) t * (double) t);
    } else if ((double) t < 2.0D / i3) {
      i = (float) (i2 * (double) (t = (float) ((double) t - 1.5D / i3)) * (double) t + 0.75D);
    } else if ((double) t < 2.5D / i3) {
      i = (float) (i2 * (double) (t = (float) ((double) t - 2.25D / i3)) * (double) t + 0.9375D);
    } else {
      i = (float) (i2 * (double) (t = (float) ((double) t - 2.625D / i3)) * (double) t + 0.984375D);
    }

    return i;
  }

  float quadInOut(float t) {
    if (t < 0.5f) {
      return 2 * t * t;
    } else {
      return -1 + (4 - 2 * t) * t;
    }
  }
}
