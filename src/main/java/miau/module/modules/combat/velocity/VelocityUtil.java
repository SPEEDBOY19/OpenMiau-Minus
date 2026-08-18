package miau.module.modules.combat.velocity;

import miau.mixin.IAccessorEntity;
import miau.mixin.IAccessorMinecraft;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MathHelper;

public final class VelocityUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private VelocityUtil() {}

  public static float normalizeAngle(float angle) {
    return ((angle % 360) + 360) % 360;
  }

  public static void reduceXZ(double factor, Integer hurtTimeMin, Integer hurtTimeMax) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (hurtTimeMin == null
        || (player.hurtTime >= hurtTimeMin && (hurtTimeMax == null || player.hurtTime <= hurtTimeMax))) {
      player.motionX *= factor;
      player.motionZ *= factor;
    }
  }

  public static void reduceXZ(double factor) {
    reduceXZ(factor, null, null);
  }

  public static void reduceY(double factor, Integer hurtTimeMin, Integer hurtTimeMax) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (hurtTimeMin == null
        || (player.hurtTime >= hurtTimeMin && (hurtTimeMax == null || player.hurtTime <= hurtTimeMax))) {
      player.motionY *= factor;
    }
  }

  public static void reduceY(double factor) {
    reduceY(factor, null, null);
  }

  public static void setMotion(Double x, Double y, Double z) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (x != null) player.motionX = x;
    if (y != null) player.motionY = y;
    if (z != null) player.motionZ = z;
  }

  public static void changeSprint(boolean setState, boolean sendPacketToServer) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    player.setSprinting(setState);
    if (!sendPacketToServer) return;
    PacketUtil.sendPacket(
        new C0BPacketEntityAction(
            player,
            setState
                ? C0BPacketEntityAction.Action.START_SPRINTING
                : C0BPacketEntityAction.Action.STOP_SPRINTING));
  }

  public static void changeSprint(boolean setState) {
    changeSprint(setState, true);
  }

  public static void changeTimer(float speed) {
    ((IAccessorMinecraft) mc).getTimer().timerSpeed = speed;
  }

  public static void setSprintSafely(boolean value) {
    EntityPlayer player = mc.thePlayer;
    if (player == null || player.isSprinting() == value) return;
    player.setSprinting(value);
  }

  public static void tryJump() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (!mc.gameSettings.keyBindJump.isKeyDown()) {
      player.jump();
    }
  }

  public static boolean isInBadEnvironment() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return false;
    return ((IAccessorEntity) player).getIsInWeb()
        || player.isInLava()
        || player.isBurning()
        || player.isInWater()
        || player.isRiding();
  }

  public static boolean isMoving() {
    EntityPlayer player = mc.thePlayer;
    return player != null && (player.moveForward != 0f || player.moveStrafing != 0f);
  }

  public static boolean isMovingBackwards() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return false;
    double motionX = player.motionX;
    double motionZ = player.motionZ;
    if (Math.sqrt(motionX * motionX + motionZ * motionZ) < 0.1) return true;
    float moveAngle = normalizeAngle((float) Math.toDegrees(Math.atan2(motionX, motionZ)));
    float lookAngle = normalizeAngle(player.rotationYaw);
    float angleDiff = Math.min(Math.abs(moveAngle - lookAngle), 360 - Math.abs(moveAngle - lookAngle));
    return angleDiff >= 60;
  }

  public static double getDirection() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return 0.0;
    float moveYaw = player.rotationYaw;
    if (player.moveForward != 0f && player.moveStrafing == 0f) {
      moveYaw += player.moveForward > 0 ? 0 : 180;
    } else if (player.moveForward != 0f && player.moveStrafing != 0f) {
      if (player.moveForward > 0) {
        moveYaw += player.moveStrafing > 0 ? -45 : 45;
      } else {
        moveYaw -= player.moveStrafing > 0 ? -45 : 45;
      }
      moveYaw += player.moveForward > 0 ? 0 : 180;
    } else if (player.moveStrafing != 0f && player.moveForward == 0f) {
      moveYaw += player.moveStrafing > 0 ? -90 : 90;
    }
    return Math.floorMod((int) moveYaw, 360);
  }

  public static EntityLivingBase getNearestEntityInRange(float range) {
    EntityPlayer player = mc.thePlayer;
    if (player == null || mc.theWorld == null) return null;
    EntityLivingBase best = null;
    double bestDist = Double.MAX_VALUE;
    for (Entity entity : mc.theWorld.playerEntities) {
      if (!(entity instanceof EntityLivingBase) || entity == player) continue;
      double dist = player.getDistanceToEntity(entity);
      if (dist <= range && dist < bestDist) {
        bestDist = dist;
        best = (EntityLivingBase) entity;
      }
    }
    return best;
  }

  public static double getSpeed() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return 0.0;
    return Math.hypot(player.motionX, player.motionZ);
  }

  public static int randomInt(int startInclusive, int endExclusive) {
    if (endExclusive - startInclusive <= 0) return startInclusive;
    return startInclusive + (int) (Math.random() * (endExclusive - startInclusive));
  }

  public static float randomFloat(float startInclusive, float endInclusive) {
    if (startInclusive == endInclusive || endInclusive - startInclusive <= 0f) return startInclusive;
    return startInclusive + (float) ((endInclusive - startInclusive) * Math.random());
  }

  public static boolean isLookingOnEntities(Entity entity, double maxAngle) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return false;
    double dx = entity.posX - player.posX;
    double dz = entity.posZ - player.posZ;
    float yaw = normalizeAngle((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
    float yawTo = normalizeAngle(player.rotationYaw);
    float diff = Math.min(Math.abs(yaw - yawTo), Math.abs(yaw - yawTo + 360));
    diff = Math.min(diff, 360 - diff);
    return diff <= maxAngle;
  }
}