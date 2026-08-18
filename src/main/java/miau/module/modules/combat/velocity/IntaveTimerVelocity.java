package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;

public class IntaveTimerVelocity extends VelocityMode {
  public IntaveTimerVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime != 0
        && event.getPacket() instanceof C03PacketPlayer
        && !(event.getPacket() instanceof C03PacketPlayer.C04PacketPlayerPosition)
        && !(event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)
        && !(event.getPacket() instanceof C03PacketPlayer.C06PacketPlayerPosLook)) {
      event.setCancelled(true);
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime >= 8) {
      VelocityUtil.changeTimer(0.3f);
    } else if (player.hurtTime > 2) {
      VelocityUtil.changeTimer(5.0f);
    } else if (player.hurtTime == 2) {
      VelocityUtil.changeTimer(1.0f);
    } else if (player.hurtTime == 0) {
      VelocityUtil.changeTimer(1.0f);
    }
  }

  @Override
  public void onDisable() {
    VelocityUtil.changeTimer(1.0f);
  }
}