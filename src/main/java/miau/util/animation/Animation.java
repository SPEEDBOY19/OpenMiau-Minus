package miau.util.animation;

/**
 * Animation utility for smooth value transitions. Uses easing functions to provide smooth
 * interpolation between values.
 */
public class Animation {
  private Easing easing;
  private long millis;
  protected long duration;
  private long startTime;

  private float startValue;
  private float destinationValue;
  private float value;
  private boolean finished;

  public Animation(Easing easing, long duration) {
    this.easing = easing;
    this.startTime = System.currentTimeMillis();
    this.duration = duration;
    this.startValue = 0f;
    this.destinationValue = 0f;
    this.value = 0f;
    this.finished = true;
  }

  public miau.util.time.TimerUtil timerUtil = new miau.util.time.TimerUtil();
  protected double endPoint;
  protected Direction direction;

  public Animation(int ms, double endPoint) {
    this(ms, endPoint, Direction.FORWARDS);
  }

  public Animation(int ms, double endPoint, Direction direction) {
    this.duration = ms;
    this.endPoint = endPoint;
    this.direction = direction;
  }

  /**
   * Updates the animation by using the easing function and time
   *
   * @param destinationValue the value that the animation is going to reach
   */
  public void run(float destinationValue) {
    this.millis = System.currentTimeMillis();
    if (this.destinationValue != destinationValue) {
      this.destinationValue = destinationValue;
      this.reset();
    } else {
      this.finished =
          this.millis - this.duration > this.startTime || this.value == destinationValue;
      if (this.finished) {
        this.value = destinationValue;
        return;
      }
    }

    float result = (float) this.easing.apply(this.getProgress());
    if (this.value > destinationValue) {
      this.value = this.startValue - (this.startValue - destinationValue) * result;
    } else {
      this.value = this.startValue + (destinationValue - this.startValue) * result;
    }
  }

  /**
   * Returns the progress of the animation
   *
   * @return value between 0 and 1
   */
  public double getProgress() {
    return (double) (System.currentTimeMillis() - this.startTime) / (double) this.duration;
  }

  /** Resets the animation to the start value */
  public void reset() {
    this.startTime = System.currentTimeMillis();
    this.startValue = value;
    this.finished = false;

    if (timerUtil != null) {
      timerUtil.reset();
    }
  }

  public boolean finished(Direction direction) {
    return isDone() && this.direction.equals(direction);
  }

  public double getLinearOutput() {
    return 1 - ((timerUtil.getTime() / (double) duration) * endPoint);
  }

  public double getEndPoint() {
    return endPoint;
  }

  public void setEndPoint(double endPoint) {
    this.endPoint = endPoint;
  }

  public boolean isDone() {
    return timerUtil.hasTimeElapsed(duration);
  }

  public void changeDirection() {
    setDirection(direction.opposite());
  }

  public Direction getDirection() {
    return direction;
  }

  public Animation setDirection(Direction direction) {
    if (this.direction != direction) {
      this.direction = direction;
      timerUtil.setTime(
          System.currentTimeMillis() - (duration - Math.min(duration, timerUtil.getTime())));
    }
    return this;
  }

  protected boolean correctOutput() {
    return false;
  }

  public Double getOutput() {
    if (direction.forwards()) {
      if (isDone()) return endPoint;
      return getEquation(timerUtil.getTime() / (double) duration) * endPoint;
    } else {
      if (isDone()) return 0.0;
      if (correctOutput()) {
        double revTime = Math.min(duration, Math.max(0, duration - timerUtil.getTime()));
        return getEquation(revTime / (double) duration) * endPoint;
      }
      return (1 - getEquation(timerUtil.getTime() / (double) duration)) * endPoint;
    }
  }

  protected double getEquation(double x) {
    return x;
  }

  public Easing getEasing() {
    return easing;
  }

  public void setEasing(Easing easing) {
    this.easing = easing;
  }

  public long getDuration() {
    return duration;
  }

  public void setDuration(long duration) {
    this.duration = duration;
  }

  public float getValue() {
    return value;
  }

  public void setValue(float value) {
    this.value = value;
  }

  public boolean isFinished() {
    return finished;
  }

  public void setStartValue(float startValue) {
    this.startValue = startValue;
    this.value = startValue;
  }

  public float getStartValue() {
    return startValue;
  }

  public long getMillis() {
    return millis;
  }

  public void setMillis(long millis) {
    this.millis = millis;
  }
}
