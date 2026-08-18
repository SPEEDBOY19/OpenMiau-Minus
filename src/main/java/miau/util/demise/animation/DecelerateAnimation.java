package miau.util.demise.animation;

public class DecelerateAnimation extends Animation {

  public DecelerateAnimation(int ms, double endPoint) {
    super(ms, endPoint);
  }

  public DecelerateAnimation(int ms, double endPoint, Direction direction) {
    super(ms, endPoint, direction);
  }

  @Override
  protected double getEquation(double x) {
    return 1 - ((1 - x) * (1 - x));
  }
}
