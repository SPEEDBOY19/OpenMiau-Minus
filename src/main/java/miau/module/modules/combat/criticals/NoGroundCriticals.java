package miau.module.modules.combat.criticals;

import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Criticals;

public class NoGroundCriticals extends CriticalsMode {
  private boolean attacked;

  public NoGroundCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    attacked = false;
  }

  @Override
  public void onDisable() {
    attacked = false;
  }

  @Override
  public void onAttack(AttackEvent event) {
    if (mc.thePlayer.isInWater()) {
      return;
    }
    attacked = true;
  }

  @Override
  public void onPacket(miau.event.impl.PacketEvent event) {
    if (attacked
        && event.getType() == miau.event.types.EventType.SEND
        && event.getPacket() instanceof net.minecraft.network.play.client.C03PacketPlayer) {
      ((miau.mixin.IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
      attacked = false;
    }
  }
}
