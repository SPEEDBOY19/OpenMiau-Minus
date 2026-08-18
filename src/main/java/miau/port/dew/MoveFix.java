package miau.port.dew;

/** Glue stub mirroring Dew's MoveFix module (isEnabled reflects Miau's MoveFix module). */
public class MoveFix {
  public boolean isEnabled() {
    miau.module.Module m = DewCommon.moduleManager.bound(MoveFix.class);
    return m != null && m.isEnabled();
  }
}
