package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;

public class AACZeroVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;

  public AACZeroVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime > 0) {
      if (!hasReceivedVelocity || player.onGround || player.fallDistance > 2F) return;
      player.motionY -= 1.0;
      player.isAirBorne = true;
      player.onGround = true;
    } else {
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
      if (event.getPacket() instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) {
        net.minecraft.network.play.server.S12PacketEntityVelocity packet =
            (net.minecraft.network.play.server.S12PacketEntityVelocity) event.getPacket();
        if (Velocity.mc.thePlayer != null
            && packet.getEntityID() == Velocity.mc.thePlayer.getEntityId()) {
          hasReceivedVelocity = true;
        }
      }
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}