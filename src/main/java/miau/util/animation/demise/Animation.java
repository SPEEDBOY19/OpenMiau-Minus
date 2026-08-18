package miau.util.animation.demise;

public abstract class Animation {
  public TimerUtils timerUtils = new TimerUtils();
  protected int duration;
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

  public boolean finished(Direction direction) {
    return isDone() && this.direction.equals(direction);
  }

  public double getLinearOutput() {
    return 1 - ((timerUtils.getTime() / (double) duration) * endPoint);
  }

  public void reset() {
    timerUtils.reset();
  }

  public boolean isDone() {
    return timerUtils.hasTimeElapsed(duration);
  }

  public void changeDirection() {
    setDirection(direction.opposite());
  }

  public Animation setDirection(Direction direction) {
    if (this.direction != direction) {
      this.direction = direction;
      timerUtils.setTime(
          System.currentTimeMillis() - (duration - Math.min(duration, timerUtils.getTime())));
    }
    return this;
  }

  protected boolean correctOutput() {
    return false;
  }

  public double getOutput() {
    if (direction.forwards()) {
      if (isDone()) {
        return endPoint;
      }

      return getEquation(timerUtils.getTime() / (double) duration) * endPoint;
    } else {
      if (isDone()) {
        return 0.0;
      }

      if (correctOutput()) {
        double revTime = Math.min(duration, Math.max(0, duration - timerUtils.getTime()));
        return getEquation(revTime / duration) * endPoint;
      }

      return (1 - getEquation(timerUtils.getTime() / (double) duration)) * endPoint;
    }
  }

  public void setDuration(int duration) {
    this.duration = duration;
  }

  public double getEndPoint() {
    return endPoint;
  }

  public void setEndPoint(double endPoint) {
    this.endPoint = endPoint;
  }

  public Direction getDirection() {
    return direction;
  }

  // This is where the animation equation should go, for example, a logistic function. Output should
  // range from 0 - 1.
  // This will take the timer's time as an input, x.
  protected abstract double getEquation(double x);
}
