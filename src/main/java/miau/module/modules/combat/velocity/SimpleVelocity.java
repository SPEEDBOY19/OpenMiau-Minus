package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.PercentProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class SimpleVelocity extends VelocityMode {
  public final PercentProperty horizontal = new PercentProperty("horizontal", 0);
  public final PercentProperty vertical = new PercentProperty("vertical", 0);
  public final BooleanProperty limitMaxMotion = new BooleanProperty("limit-max-motion", false);
  public final FloatProperty maxXZMotion = new FloatProperty("max-xz-motion", 0.4f, 0f, 1.9f, () -> limitMaxMotion.getValue());
  public final FloatProperty maxYMotion = new FloatProperty("max-y-motion", 0.36f, 0f, 0.46f, () -> limitMaxMotion.getValue());

  public SimpleVelocity(String name, Velocity parent) {
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
        event.setCancelled(true);
        double h = horizontal.getValue() / 100.0;
        double v = vertical.getValue() / 100.0;
        double mX = packet.getMotionX() / 8000.0 * h;
        double mZ = packet.getMotionZ() / 8000.0 * h;
        double mY = packet.getMotionY() / 8000.0 * v;

        if (limitMaxMotion.getValue()) {
          double distXZ = Math.sqrt(mX * mX + mZ * mZ);
          if (distXZ > maxXZMotion.getValue()) {
            double ratio = maxXZMotion.getValue() / distXZ;
            mX *= ratio;
            mZ *= ratio;
          }
          mY = Math.min(mY, maxYMotion.getValue() + 0.00075);
        }
        player.motionX = mX;
        player.motionY = mY;
        player.motionZ = mZ;
      }
    }
  }
}