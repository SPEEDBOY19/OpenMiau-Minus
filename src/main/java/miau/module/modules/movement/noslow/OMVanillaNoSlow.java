package miau.module.modules.movement.noslow;

import miau.event.impl.UpdateEvent;
import miau.module.modules.movement.NoSlow;

public class OMVanillaNoSlow extends NoSlowMode {
  public OMVanillaNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (this.getParent().isAnyActive()) {
      float multiplier = this.getParent().getMotionMultiplier();
      mc.thePlayer.movementInput.moveForward *= multiplier;
      mc.thePlayer.movementInput.moveStrafe *= multiplier;
      if (!this.getParent().canSprint()) {
        mc.thePlayer.setSprinting(false);
      }
    }
  }
}
