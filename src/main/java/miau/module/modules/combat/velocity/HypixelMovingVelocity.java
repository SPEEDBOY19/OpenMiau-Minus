package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class HypixelMovingVelocity extends VelocityMode {
  public HypixelMovingVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (!VelocityUtil.isMoving()) return;
    if (event.getPacket() instanceof C0FPacketConfirmTransaction
        || event.getPacket() instanceof S12PacketEntityVelocity) {
      event.setCancelled(true);
    }
  }
}