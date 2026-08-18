package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.util.network.PacketUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class BlocksMCVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;

  public BlocksMCVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof C0BPacketEntityAction) {
      if (hasReceivedVelocity) {
        hasReceivedVelocity = false;
        event.setCancelled(true);
      }
    }
    if (event.getType() != EventType.RECEIVE) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == player.getEntityId()) {
        hasReceivedVelocity = true;
        event.setCancelled(true);
        PacketUtil.sendPacket(new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SNEAKING));
        PacketUtil.sendPacket(new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SNEAKING));
      }
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}