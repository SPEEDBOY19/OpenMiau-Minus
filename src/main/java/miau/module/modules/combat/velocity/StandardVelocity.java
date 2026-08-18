package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.mixin.IAccessorS12PacketEntityVelocity;
import miau.module.modules.combat.Velocity;
import miau.property.properties.PercentProperty;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class StandardVelocity extends VelocityMode {

  public final PercentProperty horizontal = new PercentProperty("horizontal", 0);
  public final PercentProperty vertical = new PercentProperty("vertical", 100);
  public final PercentProperty explosionHorizontal =
      new PercentProperty("explosions-horizontal", 100);
  public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100);

  public StandardVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;
    if (event.getType() == miau.event.types.EventType.RECEIVE && !event.isCancelled()) {
      if (event.getPacket() instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
          double horizontal = this.horizontal.getValue();
          double vertical = this.vertical.getValue();

          if (horizontal == 0) {
            IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
            accessor.setMotionX(0);
            if (vertical != 0) {
              accessor.setMotionY((int) (packet.getMotionY() * vertical / 100));
            } else {
              accessor.setMotionY(0);
            }
            accessor.setMotionZ(0);
            return;
          }

          IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
          accessor.setMotionX((int) (packet.getMotionX() * horizontal / 100));
          accessor.setMotionY((int) (packet.getMotionY() * vertical / 100));
          accessor.setMotionZ((int) (packet.getMotionZ() * horizontal / 100));
        }
      }
    }
  }
}
