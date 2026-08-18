package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class MatrixReduce2Velocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;

  public MatrixReduce2Velocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
      EntityPlayer player = Velocity.mc.thePlayer;
      if (player == null) return;
      if (event.getPacket() instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() == player.getEntityId()) {
          hasReceivedVelocity = true;
        }
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (hasReceivedVelocity && player.hurtTime >= 9) {
      if (VelocityUtil.isMoving() && !(player.isBlocking() || player.isSneaking()
          || player.isEating() || !player.onGround)) {
        VelocityUtil.reduceXZ(0.0);
      } else if (!VelocityUtil.isMoving() || (player.isBlocking() || player.isSneaking()
          || player.isEating() || !player.onGround)) {
        VelocityUtil.reduceXZ(0.2);
      }
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}