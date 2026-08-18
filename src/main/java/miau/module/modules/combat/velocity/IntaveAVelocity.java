package miau.module.modules.combat.velocity;

import miau.event.impl.KnockbackEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;

/** Port of the Avocado client IntaveA velocity mode. */
public class IntaveAVelocity extends VelocityMode {

  public IntaveAVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onKnockback(KnockbackEvent event) {
    if (event.getY() > 0 && (event.getX() != 0 || event.getZ() != 0)) {
      parent.hasReceivedVelocity = true;
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) {
      return;
    }
    if (mc.thePlayer == null) return;

    if (parent.hasReceivedVelocity) {
      parent.intaveTick++;
      if (mc.thePlayer.hurtTime == 2) {
        parent.intaveDamageTick++;
        if (mc.thePlayer.onGround
            && parent.intaveTick % 2 == 0
            && parent.intaveDamageTick <= 10) {
          mc.thePlayer.jump();
          parent.intaveTick = 0;
        }
        parent.hasReceivedVelocity = false;
      }
    }
  }
}