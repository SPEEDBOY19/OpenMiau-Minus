package miau.port.dew;

/** Glue stub for Dew's TargetStrafe. */
public class TargetStrafe {
  public boolean isEnabled() {
    miau.module.Module m = DewCommon.moduleManager.bound(TargetStrafe.class);
    return m != null && m.isEnabled();
  }

  public boolean shouldActivate() {
    return true;
  }

  public double getDistance() {
    return 2.0;
  }

  public int getDirection() {
    return 1;
  }
}
