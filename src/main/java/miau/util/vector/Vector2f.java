package miau.util.vector;

public final class Vector2f {

  public float x, y;

  public Vector2f() {}

  public Vector2f(final float x, final float y) {
    this.x = x;
    this.y = y;
  }

  public Vector2f(final double x, final double y) {
    this.x = (float) x;
    this.y = (float) y;
  }

  public Vector2f offset(final float x, final float y) {
    return new Vector2f(this.x + x, this.y + y);
  }

  public float getX() {
    return this.x;
  }

  public float getY() {
    return this.y;
  }

  public void setX(final float x) {
    this.x = x;
  }

  public void setY(final float y) {
    this.y = y;
  }

  public Vector2f copy() {
    return new Vector2f(this.x, this.y);
  }
}
