package miau.port.dew;

/** Glue stub mirroring Dew's SpeedModule (isEnabled reflects Miau's Speed module). */
public class SpeedModule {
  public boolean isEnabled() {
    miau.module.Module m = DewCommon.moduleManager.bound(SpeedModule.class);
    return m != null && m.isEnabled();
  }
}
