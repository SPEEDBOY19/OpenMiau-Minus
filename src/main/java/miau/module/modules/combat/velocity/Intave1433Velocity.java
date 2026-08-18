package miau.module.modules.combat.velocity;

import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;

public class Intave1433Velocity extends VelocityMode {
  public Intave1433Velocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime == 10) {
      VelocityUtil.reduceXZ(-1.0);
    } else if (player.hurtTime == 9 && player.onGround) {
      VelocityUtil.reduceXZ(0.9);
    }
  }
}