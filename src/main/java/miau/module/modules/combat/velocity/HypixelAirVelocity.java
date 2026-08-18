package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class HypixelAirVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;

  public HypixelAirVelocity(String name, Velocity parent) {
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
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (hasReceivedVelocity) {
      if (player.onGround) {
        VelocityUtil.tryJump();
      }
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}