package miau.module.modules.combat.velocity;

import miau.event.impl.HitSlowDownEvent;
import miau.module.modules.combat.Velocity;
import miau.property.properties.ModeProperty;

public class PolarVelocity extends VelocityMode {
  public final ModeProperty polarMode = new ModeProperty("polar-mode", 0, new String[]{"Reduce"});

  public PolarVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onHitSlowDown(HitSlowDownEvent event) {
    if (mc.thePlayer != null && mc.thePlayer.hurtTime != 0) {
      if (this.polarMode.getValue() == 0 /* Reduce */) {
        event.setSlowDown(0.59928D);
      }
    }
  }
}
