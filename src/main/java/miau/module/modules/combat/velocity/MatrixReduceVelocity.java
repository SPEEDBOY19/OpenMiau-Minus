package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorS12PacketEntityVelocity;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class MatrixReduceVelocity extends VelocityMode {
  public MatrixReduceVelocity(String name, Velocity parent) {
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
        IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
        accessor.setMotionX((int) (packet.getMotionX() * 0.33));
        accessor.setMotionZ((int) (packet.getMotionZ() * 0.33));
        if (player.onGround) {
          accessor.setMotionX((int) (packet.getMotionX() * 0.86));
          accessor.setMotionZ((int) (packet.getMotionZ() * 0.86));
        }
      }
    }
  }
}