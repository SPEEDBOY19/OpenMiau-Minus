package miau.module.modules.combat.velocity;

import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;

public class HylexVelocity extends VelocityMode {
  public HylexVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onAttack(AttackEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    switch (player.hurtTime) {
      case 9:
        VelocityUtil.reduceXZ(0.8);
        break;
      case 8:
        VelocityUtil.reduceXZ(0.11);
        break;
      case 7:
        VelocityUtil.reduceXZ(0.4);
        break;
      case 4:
        VelocityUtil.reduceXZ(0.37);
        break;
    }
    lastAttackTime = System.currentTimeMillis();
  }

  private long lastAttackTime = 0;

  @Override
  public void onEnable() {
    lastAttackTime = 0;
  }

  @Override
  public void onDisable() {
    lastAttackTime = 0;
  }
}