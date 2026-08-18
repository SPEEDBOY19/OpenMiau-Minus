package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntityPlayer;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class SmoothReverseVelocity extends VelocityMode {
  private long velocityTimer = 0;
  private boolean hasReceivedVelocity = false;
  private boolean reverseHurt = false;

  public final FloatProperty reverse2Strength = new FloatProperty("smooth-reverse-strength", 0.05f, 0.02f, 0.1f);
  public final BooleanProperty onLook = new BooleanProperty("on-look", false);
  public final FloatProperty range = new FloatProperty("range", 3f, 1f, 5f, () -> onLook.getValue());
  public final FloatProperty maxAngleDifference =
      new FloatProperty("max-angle-difference", 45f, 5f, 90f, () -> onLook.getValue());

  public SmoothReverseVelocity(String name, Velocity parent) {
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

    if (hasReceivedVelocity) {
      EntityLivingBase nearby = VelocityUtil.getNearestEntityInRange(range.getValue());
      miau.mixin.IAccessorEntityPlayer acc = (IAccessorEntityPlayer) player;
      if (nearby == null) {
        acc.setSpeedInAir(0.02F);
        reverseHurt = false;
      } else {
        if (onLook.getValue()
            && !VelocityUtil.isLookingOnEntities(nearby, maxAngleDifference.getValue())) {
          hasReceivedVelocity = false;
          acc.setSpeedInAir(0.02F);
          reverseHurt = false;
        } else {
          if (player.hurtTime > 0) {
            reverseHurt = true;
          }
          if (!player.onGround) {
            acc.setSpeedInAir(reverseHurt ? reverse2Strength.getValue() : 0.02F);
          } else if (System.currentTimeMillis() - velocityTimer >= 80) {
            hasReceivedVelocity = false;
            acc.setSpeedInAir(0.02F);
            reverseHurt = false;
          }
        }
      }
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
    reverseHurt = false;
    if (Velocity.mc.thePlayer != null) {
      ((IAccessorEntityPlayer) Velocity.mc.thePlayer).setSpeedInAir(0.02F);
    }
  }
}