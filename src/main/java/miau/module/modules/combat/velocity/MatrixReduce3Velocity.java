package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class MatrixReduce3Velocity extends VelocityMode {
  private long boostTimer = 0;

  public final BooleanProperty boostAfterReduce = new BooleanProperty("boost-after-reduce", false);
  public final FloatProperty boostFactor = new FloatProperty("boost-factor", 0.33f, 0.0f, 5.0f, () -> boostAfterReduce.getValue());
  public final IntProperty boostCooldown = new IntProperty("boost-cooldown", 0, 0, 2000, () -> boostAfterReduce.getValue());

  public MatrixReduce3Velocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    boostTimer = 0;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() != player.getEntityId()) return;
      event.setCancelled(true);
      double realMotionY = packet.getMotionY() / 8000.0;
      if (Math.abs(realMotionY) >= 0.1f) {
        player.motionY = realMotionY;
        double currentSpeed = Math.hypot(player.motionX, player.motionZ);
        double knockbackX = packet.getMotionX() / 8000.0;
        double knockbackZ = packet.getMotionZ() / 8000.0;
        double knockbackSpeed = Math.hypot(knockbackX, knockbackZ);
        if (!VelocityUtil.isMoving()) {
          double reducedSpeed = Math.max(knockbackSpeed * 0.1, currentSpeed);
          if (knockbackSpeed > 0) {
            player.motionX = knockbackX / knockbackSpeed * reducedSpeed;
            player.motionZ = knockbackZ / knockbackSpeed * reducedSpeed;
          }
        } else if (boostAfterReduce.getValue()
            && System.currentTimeMillis() - boostTimer >= boostCooldown.getValue()) {
          VelocityUtil.reduceXZ(boostFactor.getValue() + 1);
          boostTimer = System.currentTimeMillis();
        }
      }
    }
  }
}