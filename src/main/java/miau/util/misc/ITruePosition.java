package miau.util.misc;

public interface ITruePosition {
  double getTrueX();

  double getTrueY();

  double getTrueZ();

  void setTrueX(double trueX);

  void setTrueY(double trueY);

  void setTrueZ(double trueZ);

  boolean isTruePos();

  void setTruePos(boolean truePos);
}
