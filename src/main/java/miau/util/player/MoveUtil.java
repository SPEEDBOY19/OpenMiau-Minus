package miau.util.player;

import miau.Miau;
import miau.event.impl.StrafeEvent;
import miau.management.RotationState;
import miau.module.modules.combat.TargetStrafe;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class MoveUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static final double HEAD_HITTER_MOTION = -0.0784000015258789;

  public static boolean isForwardPressed() {
    if (MoveUtil.mc.gameSettings.keyBindForward.isKeyDown()
        != MoveUtil.mc.gameSettings.keyBindBack.isKeyDown()) return true;
    return MoveUtil.mc.gameSettings.keyBindLeft.isKeyDown()
        != MoveUtil.mc.gameSettings.keyBindRight.isKeyDown();
  }

  public static boolean isMoving() {
    return mc.thePlayer != null
        && (mc.thePlayer.movementInput.moveForward != 0.0f
            || mc.thePlayer.movementInput.moveStrafe != 0.0f);
  }

  public static boolean isMovingStraight() {
    return isMoving()
        && mc.thePlayer.movementInput.moveForward != 0.0F
        && mc.thePlayer.movementInput.moveStrafe == 0.0F;
  }

  public static int getForwardValue() {
    int forwardValue = 0;
    if (MoveUtil.mc.gameSettings.keyBindForward.isKeyDown()) {
      ++forwardValue;
    }
    if (MoveUtil.mc.gameSettings.keyBindBack.isKeyDown()) {
      --forwardValue;
    }
    return forwardValue;
  }

  public static int getLeftValue() {
    int leftValue = 0;
    if (MoveUtil.mc.gameSettings.keyBindLeft.isKeyDown()) {
      ++leftValue;
    }
    if (MoveUtil.mc.gameSettings.keyBindRight.isKeyDown()) {
      --leftValue;
    }
    return leftValue;
  }

  public static float getMoveYaw() {
    return MoveUtil.adjustYaw(
        RotationState.isActived()
            ? RotationState.getSmoothedYaw()
            : MoveUtil.mc.thePlayer.rotationYaw,
        MoveUtil.mc.thePlayer.movementInput.moveForward,
        MoveUtil.mc.thePlayer.movementInput.moveStrafe);
  }

  public static float adjustYaw(float yaw, float forward, float strafe) {
    TargetStrafe targetStrafe = (TargetStrafe) Miau.moduleManager.modules.get(TargetStrafe.class);
    if (targetStrafe.isEnabled()) {
      if (!Float.isNaN(targetStrafe.getTargetYaw())) {
        return targetStrafe.getTargetYaw();
      }
    }
    if (forward < 0.0f) {
      yaw += 180.0f;
    }
    if (strafe != 0.0f) {
      float multiplier = forward == 0.0f ? 1.0f : 0.5f * Math.signum(forward);
      yaw += -90.0f * multiplier * Math.signum(strafe);
    }
    return MathHelper.wrapAngleTo180_float(yaw);
  }

  public static float getDirectionYaw() {
    if (MoveUtil.getSpeed() == 0.0) {
      return MathHelper.wrapAngleTo180_float(MoveUtil.mc.thePlayer.rotationYaw);
    }
    return MathHelper.wrapAngleTo180_float(
        (float)
                Math.toDegrees(
                    Math.atan2(MoveUtil.mc.thePlayer.motionZ, MoveUtil.mc.thePlayer.motionX))
            - 90.0f);
  }

  public static double getBaseMoveSpeed() {
    double baseSpeed = 0.28015;
    if (MoveUtil.getSpeedTime() > 0) {
      baseSpeed = 0.28015 * (1.0 + 0.15 * (double) MoveUtil.getSpeedLevel());
    }
    return baseSpeed;
  }

  public static double getBaseJumpHigh(int speedLevel) {
    double jumpHeight = 0.452;
    if (speedLevel == 1) {
      jumpHeight = 0.49720000000000003;
    } else if (speedLevel >= 2) {
      jumpHeight *= 1.2;
    }
    return jumpHeight;
  }

  public static double getJumpMotion() {
    int speedLevel = 0;
    if (MoveUtil.getSpeedTime() > 0) {
      speedLevel = MoveUtil.getSpeedLevel();
    }
    return MoveUtil.getBaseJumpHigh(speedLevel);
  }

  public static double getSpeed() {
    return MoveUtil.getSpeed(MoveUtil.mc.thePlayer.motionX, MoveUtil.mc.thePlayer.motionZ);
  }

  public static double getSpeed(double motionX, double motionZ) {
    return Math.hypot(motionX, motionZ);
  }

  public static double speed() {
    return getSpeed();
  }

  public static void strafe() {
    strafe(speed());
  }

  public static void strafe(final double speed) {
    if (!isMoving()) return;
    final double yaw = getMoveDirection();
    mc.thePlayer.motionX = -MathHelper.sin((float) yaw) * speed;
    mc.thePlayer.motionZ = MathHelper.cos((float) yaw) * speed;
  }

  public static void strafe(final double speed, final Entity entity) {
    if (!isMoving()) return;
    final double yaw = getMoveDirection();
    entity.motionX = -MathHelper.sin((float) yaw) * speed;
    entity.motionZ = MathHelper.cos((float) yaw) * speed;
  }

  public static void strafe(final double speed, final float yaw) {
    if (!isMoving()) return;
    final double rad = Math.toRadians(yaw);
    mc.thePlayer.motionX = -MathHelper.sin((float) rad) * speed;
    mc.thePlayer.motionZ = MathHelper.cos((float) rad) * speed;
  }

  /** Stops the player's horizontal movement. */
  public static void stop() {
    mc.thePlayer.motionX = 0;
    mc.thePlayer.motionZ = 0;
  }

  /** Gets the movement direction yaw in radians, based on the player's movement input. */
  public static double getMoveDirection() {
    float rotationYaw = mc.thePlayer.rotationYaw;
    if (mc.thePlayer.moveForward < 0) rotationYaw += 180;
    float forward = 1;
    if (mc.thePlayer.moveForward < 0) forward = -0.5F;
    else if (mc.thePlayer.moveForward > 0) forward = 0.5F;
    if (mc.thePlayer.moveStrafing > 0) rotationYaw -= 90 * forward;
    if (mc.thePlayer.moveStrafing < 0) rotationYaw += 90 * forward;
    return Math.toRadians(rotationYaw);
  }

  /** Calculates the predicted motion after the specified number of ticks. */
  public static double predictedMotion(final double motion, final int ticks) {
    if (ticks <= 0) return motion;
    double predicted = motion;
    for (int i = 0; i < ticks; i++) {
      predicted = (predicted - 0.08) * 0.98F;
    }
    return predicted;
  }

  public static double getbaseMoveSpeed() {
    double baseSpeed = 0.2873;
    if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
      baseSpeed *=
          1.0 + 0.2 * (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
    }
    return baseSpeed;
  }

  /** Prevents diagonal speed increase by reducing motion when moving diagonally. */
  public static void preventDiagonalSpeed() {
    final net.minecraft.client.settings.KeyBinding[] gameSettings =
        new net.minecraft.client.settings.KeyBinding[] {
          mc.gameSettings.keyBindForward, mc.gameSettings.keyBindRight,
          mc.gameSettings.keyBindBack, mc.gameSettings.keyBindLeft
        };
    int down = 0;
    for (net.minecraft.client.settings.KeyBinding kb : gameSettings) {
      if (kb.isKeyDown()) down++;
    }
    if (down == 1) return;
    final double groundIncrease = (0.1299999676734952 - 0.12739998266255503) + 1E-7 - 1E-8;
    final double airIncrease = (0.025999999334873708 - 0.025479999685988748) - 1E-8;
    final double increase = mc.thePlayer.onGround ? groundIncrease : airIncrease;
    moveFlying(-increase);
  }

  /** Adjusts the player's motion in the movement direction by the given increase. */
  public static void moveFlying(double increase) {
    if (!isMoving()) return;
    final double yaw = getMoveDirection();
    mc.thePlayer.motionX += -MathHelper.sin((float) yaw) * increase;
    mc.thePlayer.motionZ += MathHelper.cos((float) yaw) * increase;
  }

  public static void setSpeed(double speed) {
    MoveUtil.setSpeed(speed, MoveUtil.getDirectionYaw());
  }

  public static void setSpeed(double speed, float yaw) {
    MoveUtil.mc.thePlayer.motionX = -Math.sin(Math.toRadians(yaw)) * speed;
    MoveUtil.mc.thePlayer.motionZ = Math.cos(Math.toRadians(yaw)) * speed;
  }

  public static void addSpeed(double speed, float yaw) {
    MoveUtil.mc.thePlayer.motionX += -Math.sin(Math.toRadians(yaw)) * speed;
    MoveUtil.mc.thePlayer.motionZ += Math.cos(Math.toRadians(yaw)) * speed;
  }

  public static int getSpeedLevel() {
    int speedLevel = 0;
    if (MoveUtil.mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
      speedLevel =
          (MoveUtil.mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
    }
    return speedLevel;
  }

  public static int getSpeedTime() {
    if (MoveUtil.mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
      return MoveUtil.mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getDuration();
    }
    return 0;
  }

  public static float getAllowedHorizontalDistance() {
    float slipperiness =
        MoveUtil.mc
                .thePlayer
                .worldObj
                .getBlockState(
                    new BlockPos(
                        MathHelper.floor_double(MoveUtil.mc.thePlayer.posX),
                        MathHelper.floor_double(MoveUtil.mc.thePlayer.getEntityBoundingBox().minY)
                            - 1,
                        MathHelper.floor_double(MoveUtil.mc.thePlayer.posZ)))
                .getBlock()
                .slipperiness
            * 0.91f;
    return MoveUtil.mc.thePlayer.getAIMoveSpeed()
        * (0.16277136f / (slipperiness * slipperiness * slipperiness));
  }

  public static double[] predictMovement() {
    float strafeInput = (float) MoveUtil.getLeftValue() * 0.98f;
    float forwardInput = (float) MoveUtil.getForwardValue() * 0.98f;
    float inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput;
    if (inputMagnitude >= 1.0E-4f) {
      inputMagnitude = MathHelper.sqrt_float(inputMagnitude);
      if (inputMagnitude < 1.0f) {
        inputMagnitude = 1.0f;
      }
      inputMagnitude = MoveUtil.getAllowedHorizontalDistance() / inputMagnitude;
      float sinYaw = MathHelper.sin(MoveUtil.mc.thePlayer.rotationYaw * (float) Math.PI / 180.0f);
      float cosYaw = MathHelper.cos(MoveUtil.mc.thePlayer.rotationYaw * (float) Math.PI / 180.0f);
      strafeInput *= inputMagnitude;
      forwardInput *= inputMagnitude;
      return new double[] {
        strafeInput * cosYaw - forwardInput * sinYaw, forwardInput * cosYaw + strafeInput * sinYaw
      };
    }
    return new double[] {0.0, 0.0};
  }

  public static void fixStrafe(float targetYaw) {
    float angle =
        MathHelper.wrapAngleTo180_float(
            MoveUtil.adjustYaw(
                    MoveUtil.mc.thePlayer.rotationYaw,
                    MoveUtil.getForwardValue(),
                    MoveUtil.getLeftValue())
                - targetYaw
                + 22.5f);
    switch ((int) (angle + 180.0f) / 45 % 8) {
      case 0:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = -1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 0.0f;
          break;
        }
      case 1:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = -1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 1.0f;
          break;
        }
      case 2:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 0.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 1.0f;
          break;
        }
      case 3:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 1.0f;
          break;
        }
      case 4:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 0.0f;
          break;
        }
      case 5:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = -1.0f;
          break;
        }
      case 6:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 0.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = -1.0f;
          break;
        }
      case 7:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = -1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = -1.0f;
          break;
        }
    }
    if (MoveUtil.mc.thePlayer.movementInput.sneak) {
      MoveUtil.mc.thePlayer.movementInput.moveForward *= 0.3f;
      MoveUtil.mc.thePlayer.movementInput.moveStrafe *= 0.3f;
    }
  }

  public static void fixMovement(final float yaw) {
    final float forward = mc.thePlayer.movementInput.moveForward;
    final float strafe = mc.thePlayer.movementInput.moveStrafe;
    if (forward == 0 && strafe == 0) return;

    final double angle =
        MathHelper.wrapAngleTo180_double(
            Math.toDegrees(direction(mc.thePlayer.rotationYaw, forward, strafe)));
    float closestForward = 0, closestStrafe = 0;
    double closestDifference = Double.MAX_VALUE;

    for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
      for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
        if (predictedStrafe == 0 && predictedForward == 0) continue;

        final double predictedAngle =
            MathHelper.wrapAngleTo180_double(
                Math.toDegrees(direction(yaw, predictedForward, predictedStrafe)));
        final double difference = wrappedDifference(angle, predictedAngle);

        if (difference < closestDifference) {
          closestDifference = difference;
          closestForward = predictedForward;
          closestStrafe = predictedStrafe;
        }
      }
    }

    mc.thePlayer.movementInput.moveForward = closestForward;
    mc.thePlayer.movementInput.moveStrafe = closestStrafe;
    if (mc.thePlayer.movementInput.sneak) {
      mc.thePlayer.movementInput.moveForward *= 0.3f;
      mc.thePlayer.movementInput.moveStrafe *= 0.3f;
    }
  }

  public static double direction(
      float rotationYaw, final double moveForward, final double moveStrafing) {
    if (moveForward < 0F) rotationYaw += 180F;
    float forward = 1F;
    if (moveForward < 0F) forward = -0.5F;
    else if (moveForward > 0F) forward = 0.5F;
    if (moveStrafing > 0F) rotationYaw -= 90F * forward;
    if (moveStrafing < 0F) rotationYaw += 90F * forward;
    return Math.toRadians(rotationYaw);
  }

  public static double wrappedDifference(double number1, double number2) {
    return Math.min(
        Math.abs(number1 - number2),
        Math.min(
            Math.abs(number1 - 360) - Math.abs(number2 - 0),
            Math.abs(number2 - 360) - Math.abs(number1 - 0)));
  }

  public static void silentMoveFix(StrafeEvent event) {
    int dif =
        (int)
            ((MathHelper.wrapAngleTo180_float(
                        mc.thePlayer.rotationYaw - RotationUtil.serverYaw - 23.5F - 135.0F)
                    + 180.0F)
                / 45.0F);
    float yaw = RotationUtil.serverYaw;
    float strafe = event.getStrafe();
    float forward = event.getForward();
    float friction = event.getFriction();
    float calcForward = 0.0F;
    float calcStrafe = 0.0F;
    switch (dif) {
      case 0:
        calcForward = forward;
        calcStrafe = strafe;
        break;
      case 1:
        calcForward += forward;
        calcStrafe -= forward;
        calcForward += strafe;
        calcStrafe += strafe;
        break;
      case 2:
        calcForward = strafe;
        calcStrafe = -forward;
        break;
      case 3:
        calcForward -= forward;
        calcStrafe -= forward;
        calcForward += strafe;
        calcStrafe -= strafe;
        break;
      case 4:
        calcForward = -forward;
        calcStrafe = -strafe;
        break;
      case 5:
        calcForward -= forward;
        calcStrafe += forward;
        calcForward -= strafe;
        calcStrafe -= strafe;
        break;
      case 6:
        calcForward = -strafe;
        calcStrafe = forward;
        break;
      case 7:
        calcForward += forward;
        calcStrafe += forward;
        calcForward -= strafe;
        calcStrafe += strafe;
    }

    if (calcForward > 1.0F
        || (calcForward < 0.9F && calcForward > 0.3F)
        || calcForward < -1.0F
        || (calcForward > -0.9F && calcForward < -0.3F)) {
      calcForward *= 0.5F;
    }

    if (calcStrafe > 1.0F
        || (calcStrafe < 0.9F && calcStrafe > 0.3F)
        || calcStrafe < -1.0F
        || (calcStrafe > -0.9F && calcStrafe < -0.3F)) {
      calcStrafe *= 0.5F;
    }

    float d = calcStrafe * calcStrafe + calcForward * calcForward;
    if (d >= 1.0E-4F) {
      d = MathHelper.sqrt_float(d);
      if (d < 1.0F) {
        d = 1.0F;
      }

      d = friction / d;
      calcStrafe *= d;
      calcForward *= d;
      float yawSin = MathHelper.sin((float) ((double) yaw * Math.PI / 180.0));
      float yawCos = MathHelper.cos((float) ((double) yaw * Math.PI / 180.0));
      mc.thePlayer.motionX += (double) (calcStrafe * yawCos - calcForward * yawSin);
    }
  }

  public static double findGround(net.minecraft.entity.Entity entity) {
    for (double y = entity.posY; y > 0; y--) {
      BlockPos pos = new BlockPos(entity.posX, y, entity.posZ);
      if (!mc.theWorld.isAirBlock(pos)) {
        return pos.getY() + 1.0;
      }
    }
    return 0.0;
  }

  public static boolean isMovingMotion(net.minecraft.entity.Entity entity) {
    return Math.abs(entity.motionX) > 0.005 || Math.abs(entity.motionZ) > 0.005;
  }
}
