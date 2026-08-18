package miau.management.drag;

public class Snap {
  public double position, distance;
  public Orientation orientation;
  public boolean center, right, left;

  public Snap(
      double position,
      double distance,
      Orientation orientation,
      boolean center,
      boolean right,
      boolean left) {
    this.position = position;
    this.distance = distance;
    this.orientation = orientation;
    this.center = center;
    this.right = right;
    this.left = left;
  }
}
