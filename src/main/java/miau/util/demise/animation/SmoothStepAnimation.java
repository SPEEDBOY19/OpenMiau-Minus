package miau.util.demise.animation;

public class SmoothStepAnimation extends Animation {

  public SmoothStepAnimation(int ms, double endPoint) {
    super(ms, endPoint);
  }

  public SmoothStepAnimation(int ms, double endPoint, Direction direction) {
    super(ms, endPoint, direction);
  }

  @Override
  protected double getEquation(double x) {
    return x * x * (3 - 2 * x);
  }
}
