package miau.port.dew;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.stats.StatList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Faithful port of Dew's MovementUtil. */
public class MovementUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static boolean mcJumpNoBoost = false;

  public static void fakeJump() {
    mc.thePlayer.isAirBorne = true;
    mc.thePlayer.triggerAchievement(StatList.jumpStat);
  }

  public static boolean isBlockUnderPlayer(EntityPlayer player, int distance, boolean slabIsNotBlock) {
    return isBlockUnderPlayer(player, distance, 0.6, slabIsNotBlock);
  }

  public static boolean isBlockUnderPlayer(
      EntityPlayer player, int distance, double horizontal, boolean slabIsNotBlock) {
    World world = player.worldObj;
    AxisAlignedBB bb = player.getEntityBoundingBox();

    double centerX = (bb.minX + bb.maxX) / 2.0;
    double centerZ = (bb.minZ + bb.maxZ) / 2.0;

    double minX = centerX - horizontal;
    double maxX = centerX + horizontal;
    double minZ = centerZ - horizontal;
    double maxZ = centerZ + horizontal;

    for (int i = 1; i <= distance; i++) {
      double checkY = bb.minY - i;
      if (checkY < 0) break;

      for (double x = minX; x <= maxX; x += 0.3) {
        for (double z = minZ; z <= maxZ; z += 0.3) {
          BlockPos pos = new BlockPos(x, checkY, z);
          net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
          boolean isOk =
              !slabIsNotBlock
                  || !(block instanceof net.minecraft.block.BlockSlab)
                      && !(block instanceof net.minecraft.block.BlockStairs);
          if (!world.isAirBlock(pos) && isOk) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isBlockAbovePlayer(EntityPlayer player, int distance) {
    return isBlockAbovePlayer(player, distance, 0.6);
  }

  public static boolean isBlockAbovePlayer(EntityPlayer player, int distance, double horizontal) {
    World world = player.worldObj;
    AxisAlignedBB bb = player.getEntityBoundingBox();

    double centerX = (bb.minX + bb.maxX) / 2.0;
    double centerZ = (bb.minZ + bb.maxZ) / 2.0;

    double minX = centerX - horizontal;
    double maxX = centerX + horizontal;
    double minZ = centerZ - horizontal;
    double maxZ = centerZ + horizontal;

    for (int i = 1; i <= distance; i++) {
      double checkY = bb.maxY + i;
      if (checkY >= 256) break;

      for (double x = minX; x <= maxX; x += 0.3) {
        for (double z = minZ; z <= maxZ; z += 0.3) {
          BlockPos pos = new BlockPos(x, checkY, z);
          if (!world.isAirBlock(pos)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static float getSpeed() {
    return (float) getSpeed(mc.thePlayer.motionX, mc.thePlayer.motionZ);
  }

  public static void setSpeed(double moveSpeed) {
    setSpeed(
        moveSpeed,
        mc.thePlayer.rotationYaw,
        mc.thePlayer.movementInput.moveStrafe,
        mc.thePlayer.movementInput.moveForward);
  }

  public static double getSpeed(double velocityX, double velocityZ) {
    return Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
  }

  public static double getBaseMoveSpeed(double customSpeed) {
    double baseSpeed = customSpeed;
    if (mc.thePlayer != null && mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
      PotionEffect effect = mc.thePlayer.getActivePotionEffect(Potion.moveSpeed);
      if (effect != null) {
        int amplifier = effect.getAmplifier();
        baseSpeed *= 1.0 + 0.2 * (amplifier + 1);
      }
    }
    return baseSpeed;
  }

  public static int getSpeedEffect() {
    if (mc.thePlayer != null && mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
      PotionEffect effect = mc.thePlayer.getActivePotionEffect(Potion.moveSpeed);
      if (effect != null) return effect.getAmplifier() + 1;
    }
    return 0;
  }

  public static float getAngleDifference(float a, float b) {
    return ((a - b) % 360f + 540f) % 360f - 180f;
  }

  public static boolean areTwoOrMoreKeysPressed(int... keyCodes) {
    int count = 0;
    for (int keyCode : keyCodes) {
      if (Keyboard.isKeyDown(keyCode)) {
        count++;
        if (count >= 2) return true;
      }
    }
    return false;
  }

  public static void stopMovingQuickly() {
    mc.thePlayer.motionX = 0.0;
    mc.thePlayer.motionZ = 0.0;
  }

  public static void stopMovingSlowly() {
    strafe(0.2);
  }

  public static void stopYMotion() {
    mc.thePlayer.motionY = 0.0;
  }

  public static boolean hasMotionHorizontal() {
    return mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0;
  }

  public static boolean isMoving() {
    return mc.gameSettings.keyBindForward.isKeyDown()
        || mc.gameSettings.keyBindBack.isKeyDown()
        || mc.gameSettings.keyBindRight.isKeyDown()
        || mc.gameSettings.keyBindLeft.isKeyDown()
        || mc.thePlayer != null
            && (mc.thePlayer.movementInput.moveForward != 0f
                || mc.thePlayer.movementInput.moveStrafe != 0f);
  }

  public static double getDirection() {
    if (mc.thePlayer == null) return 0f;

    float yaw = mc.thePlayer.rotationYaw;
    boolean forward = mc.gameSettings.keyBindForward.isKeyDown();
    boolean back = mc.gameSettings.keyBindBack.isKeyDown();
    boolean left = mc.gameSettings.keyBindLeft.isKeyDown();
    boolean right = mc.gameSettings.keyBindRight.isKeyDown();

    float result = 0f;

    if (forward) {
      if (left && !right) result = -45f;
      else if (right && !left) result = 45f;
      else result = 0f;
    } else if (back) {
      if (left && !right) result = -135f;
      else if (right && !left) result = 135f;
      else result = 180f;
    } else {
      if (left && !right) result = -90f;
      else if (right && !left) result = 90f;
    }

    float direction = yaw + result;
    direction = (direction % 360 + 360) % 360;

    return direction;
  }

  public static void strafe(double speed) {
    float forward = 0f;
    if (mc.gameSettings.keyBindForward.isKeyDown()) forward += 1f;
    if (mc.gameSettings.keyBindBack.isKeyDown()) forward -= 1f;

    float strafe = 0f;
    if (mc.gameSettings.keyBindRight.isKeyDown()) strafe += 1f;
    if (mc.gameSettings.keyBindLeft.isKeyDown()) strafe -= 1f;

    if (!isMoving()) {
      stopMovingQuickly();
      return;
    }

    float yaw = mc.thePlayer.rotationYaw;

    TargetStrafe ts = DewCommon.moduleManager.getModule(TargetStrafe.class);
    Aura aura = DewCommon.moduleManager.getModule(Aura.class);
    if (ts != null
        && ts.isEnabled()
        && ts.shouldActivate()
        && aura != null
        && aura.isEnabled()
        && aura.target != null) {
      yaw = getTargetStrafeYawDirection(aura.target, ts.getDistance());
    }

    double inputAngle = Math.atan2(strafe, forward);
    double moveAngle = Math.toRadians(yaw) + inputAngle;

    mc.thePlayer.motionX = -Math.sin(moveAngle) * speed;
    mc.thePlayer.motionZ = Math.cos(moveAngle) * speed;
  }

  public static boolean isDiagonal(float threshold) {
    double yaw = getDirection();
    yaw = Math.abs(((yaw + 360) % 360));
    boolean isNorth = Math.abs(yaw) < threshold || Math.abs(yaw - 360) < threshold;
    boolean isSouth = Math.abs(yaw - 180) < threshold;
    boolean isEast = Math.abs(yaw - 90) < threshold;
    boolean isWest = Math.abs(yaw - 270) < threshold;
    return (!isNorth && !isSouth && !isEast && !isWest);
  }

  public static float getTargetStrafeYawDirection(Entity entity, double desiredDistance) {
    double dx = entity.posX - mc.thePlayer.posX;
    double dz = entity.posZ - mc.thePlayer.posZ;
    double distance = Math.sqrt(dx * dx + dz * dz);
    float baseYaw =
        DewCommon.rotationManager.getRotationsTo(
            entity.posX, entity.posY + (entity.getEyeHeight() / 2.0), entity.posZ)[0];
    int direction = DewCommon.moduleManager.getModule(TargetStrafe.class).getDirection();

    if (distance > desiredDistance + 0.4) {
      if (distance > desiredDistance + 1) {
        return baseYaw;
      } else {
        return baseYaw + (direction == 1 ? 60f : -60f);
      }
    } else if (distance < desiredDistance - 0.4) {
      if (distance < desiredDistance - 1) {
        return baseYaw + 180f;
      } else {
        return baseYaw + 180f + (direction == 1 ? 60f : -60f);
      }
    } else {
      return baseYaw + (direction == 1 ? 85f : -85f);
    }
  }

  public static void setSpeed(double moveSpeed, float yaw, double strafe, double forward) {
    if (forward != 0.0D) {
      yaw +=
          (strafe > 0.0D)
              ? (forward > 0.0D ? -45 : 45)
              : (strafe < 0.0D) ? (forward > 0.0D ? 45 : -45) : 0;
      strafe = 0.0D;
      forward = (forward > 0.0D) ? 1.0D : -1.0D;
    }
    if (strafe != 0.0D) {
      strafe = (strafe > 0.0D) ? 1.0D : -1.0D;
    }
    double radianYaw = Math.toRadians(yaw + 90.0F);
    double cosYaw = Math.cos(radianYaw);
    double sinYaw = Math.sin(radianYaw);
    mc.thePlayer.motionX = forward * moveSpeed * cosYaw + strafe * moveSpeed * sinYaw;
    mc.thePlayer.motionZ = forward * moveSpeed * sinYaw - strafe * moveSpeed * cosYaw;
  }
}
