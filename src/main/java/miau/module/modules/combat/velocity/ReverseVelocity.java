package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class ReverseVelocity extends VelocityMode {
  private long velocityTimer = 0;
  private boolean hasReceivedVelocity = false;

  public final FloatProperty reverseStrength = new FloatProperty("reverse-strength", 1f, 0.1f, 1f);
  public final BooleanProperty onLook = new BooleanProperty("on-look", false);
  public final FloatProperty range = new FloatProperty("range", 3f, 1f, 5f, () -> onLook.getValue());
  public final FloatProperty maxAngleDifference =
      new FloatProperty("max-angle-difference", 45f, 5f, 90f, () -> onLook.getValue());

  public ReverseVelocity(String name, Velocity parent) {
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
          velocityTimer = System.currentTimeMillis();
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
    if (!hasReceivedVelocity) return;

    EntityLivingBase nearby = VelocityUtil.getNearestEntityInRange(range.getValue());
    if (nearby != null) {
      if (!player.onGround) {
        if (onLook.getValue()
            && !VelocityUtil.isLookingOnEntities(nearby, maxAngleDifference.getValue())) {
          return;
        }
        double speed = VelocityUtil.getSpeed();
        double yaw =
            Math.atan2(player.motionZ, player.motionX) * 180.0 / Math.PI - 90.0;
        if (speed > 0) {
          player.motionX = -Math.sin(Math.toRadians(yaw)) * (speed * reverseStrength.getValue());
          player.motionZ = Math.cos(Math.toRadians(yaw)) * (speed * reverseStrength.getValue());
        } else {
          VelocityUtil.reduceXZ(reverseStrength.getValue());
        }
      } else if (System.currentTimeMillis() - velocityTimer >= 80) {
        hasReceivedVelocity = false;
      }
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}