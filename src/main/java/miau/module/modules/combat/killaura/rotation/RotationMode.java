package miau.module.modules.combat.killaura.rotation;

import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;

public abstract class RotationMode {
  protected final KillAura killAura;
  private final String name;

  public RotationMode(KillAura killAura, String name) {
    this.killAura = killAura;
    this.name = name;
  }

  public String getName() {
    return this.name;
  }

  public abstract float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event);
}
