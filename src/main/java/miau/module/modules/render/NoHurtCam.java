package miau.module.modules.render;

import miau.module.Module;
import miau.property.properties.PercentProperty;

public class NoHurtCam extends Module {
  public final PercentProperty multiplier = new PercentProperty("multiplier", 0);

  public NoHurtCam() {
    super("NoHurtCam", false, true);
  }
}
