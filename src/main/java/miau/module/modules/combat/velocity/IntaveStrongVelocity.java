package miau.module.modules.combat.velocity;

import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;

public class IntaveStrongVelocity extends VelocityMode {
  public IntaveStrongVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onAttack(AttackEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime > 0) {
      VelocityUtil.reduceXZ(0.6);
    }
  }
}