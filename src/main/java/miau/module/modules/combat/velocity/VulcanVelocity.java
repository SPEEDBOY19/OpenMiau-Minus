package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.util.network.PacketUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

public class VulcanVelocity extends VelocityMode {
  private boolean transaction = false;

  public VulcanVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S32PacketConfirmTransaction) {
      event.setCancelled(true);
      PacketUtil.sendPacketNoEvent(
          new C0FPacketConfirmTransaction(
              transaction ? 1 : -1, (short) (transaction ? -1 : 1), transaction));
      transaction = !transaction;
    }
  }

  @Override
  public void onDisable() {
    transaction = false;
  }
}