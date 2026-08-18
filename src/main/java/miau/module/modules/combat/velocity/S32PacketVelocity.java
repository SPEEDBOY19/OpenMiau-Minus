package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

public class S32PacketVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;

  public S32PacketVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == player.getEntityId()) {
        hasReceivedVelocity = true;
        event.setCancelled(true);
      }
    }
    if (event.getPacket() instanceof S32PacketConfirmTransaction) {
      if (!hasReceivedVelocity) return;
      event.setCancelled(true);
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}