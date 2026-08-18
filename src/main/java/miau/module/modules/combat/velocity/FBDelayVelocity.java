package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.mixin.IAccessorS12PacketEntityVelocity;
import miau.mixin.IAccessorS27PacketExplosion;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class FBDelayVelocity extends VelocityMode {
  public final IntProperty delayTicks = new IntProperty("delay-ticks", 3, 1, 20);
  public final IntProperty delayChance = new IntProperty("delay-chance", 100, 0, 100);
  public final FloatProperty delayHorizontal = new FloatProperty("delay-horizontal", 0f, -1f, 1f);
  public final FloatProperty delayVertical = new FloatProperty("delay-vertical", 0f, -1f, 1f);
  public final BooleanProperty delayFakeCheck = new BooleanProperty("fake-check", true);

  private int delayChanceCounter = 0;
  private boolean delayActive = false;
  private boolean delayReverseFlag = false;
  private boolean delayPendingExplosion = false;
  private boolean delayAllowNext = true;
  private int delayTickCounter = 0;
  private long delayTimer = 0;

  public FBDelayVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    delayChanceCounter = 0;
    delayActive = false;
    delayReverseFlag = false;
    delayPendingExplosion = false;
    delayAllowNext = true;
    delayTickCounter = 0;
    delayTimer = 0;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    S12PacketEntityVelocity velocityPacket = null;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == player.getEntityId()) {
        if (!delayReverseFlag
            && !canDelay()
            && !VelocityUtil.isInBadEnvironment()
            && !delayPendingExplosion
            && (!delayAllowNext || !delayFakeCheck.getValue())) {
          delayChanceCounter = delayChanceCounter % 100 + delayChance.getValue();
          if (delayChanceCounter >= 100) {
            event.setCancelled(true);
            delayReverseFlag = true;
            delayActive = true;
            delayTimer = System.currentTimeMillis();
            return;
          }
        }
        applyVelocityReduction(packet);
        event.setCancelled(true);
      }
    } else if (event.getPacket() instanceof S27PacketExplosion) {
      delayPendingExplosion = true;
      S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
      if (delayHorizontal.getValue() == 0f || delayVertical.getValue() == 0f) {
        event.setCancelled(true);
      } else {
        ((IAccessorS27PacketExplosion) explosion)
            .setMotionX(((IAccessorS27PacketExplosion) explosion).getMotionX() * delayHorizontal.getValue());
        ((IAccessorS27PacketExplosion) explosion)
            .setMotionY(((IAccessorS27PacketExplosion) explosion).getMotionY() * delayVertical.getValue());
        ((IAccessorS27PacketExplosion) explosion)
            .setMotionZ(((IAccessorS27PacketExplosion) explosion).getMotionZ() * delayHorizontal.getValue());
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (delayReverseFlag
        && (canDelay()
            || (player.isInWater() || player.isInLava()
                || ((miau.mixin.IAccessorEntity) player).getIsInWeb())
            || delayTickCounter >= delayTicks.getValue())) {
      delayReverseFlag = false;
      delayTickCounter = 0;
      delayTimer = 0;
    }
    if (delayReverseFlag) {
      delayTickCounter++;
    }
    if (delayActive) {
      double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
      if (speed > 0.1) {
        double yaw = Math.toDegrees(Math.atan2(player.motionZ, player.motionX)) - 90.0;
        player.motionX = -Math.sin(Math.toRadians(yaw)) * speed;
        player.motionZ = Math.cos(Math.toRadians(yaw)) * speed;
      }
      delayActive = false;
    }
  }

  @Override
  public void onTick(TickEvent event) {
    if (delayReverseFlag && System.currentTimeMillis() - delayTimer >= 50L * delayTicks.getValue()) {
      delayReverseFlag = false;
      delayTickCounter = 0;
      delayTimer = 0;
    }
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player != null && player.hurtTime == 0) {
      delayPendingExplosion = false;
      delayAllowNext = true;
    }
  }

  @Override
  public void onDisable() {
    delayChanceCounter = 0;
    delayActive = false;
    delayReverseFlag = false;
    delayPendingExplosion = false;
    delayAllowNext = true;
    delayTickCounter = 0;
    delayTimer = 0;
  }

  private boolean canDelay() {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return false;
    return player.onGround;
  }

  private void applyVelocityReduction(S12PacketEntityVelocity packet) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    double motionX = packet.getMotionX() / 8000.0;
    double motionZ = packet.getMotionZ() / 8000.0;
    double motionY = packet.getMotionY() / 8000.0;
    if (delayHorizontal.getValue() != 0f) {
      motionX *= delayHorizontal.getValue();
      motionZ *= delayHorizontal.getValue();
    }
    if (delayVertical.getValue() != 0f) {
      motionY *= delayVertical.getValue();
    }
    player.motionX = motionX;
    player.motionZ = motionZ;
    player.motionY = motionY;
  }
}