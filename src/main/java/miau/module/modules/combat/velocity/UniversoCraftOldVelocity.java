package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class UniversoCraftOldVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;

  public UniversoCraftOldVelocity(String name, Velocity parent) {
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
      }
    }
    if (hasReceivedVelocity) {
      if (event.getPacket() instanceof S12PacketEntityVelocity
          || event.getPacket() instanceof S27PacketExplosion) {
        event.setCancelled(true);
        player.motionY += Math.random() / 100;
      }
      if (player.hurtTime == 0) {
        hasReceivedVelocity = false;
      }
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}