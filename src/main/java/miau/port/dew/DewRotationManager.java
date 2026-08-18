package miau.port.dew;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Faithful port of Dew's RotationManager (BouncyCastle random replaced with ThreadLocalRandom). */
public class DewRotationManager {
  private static final long ROTATION_TIMEOUT = 450L;
  private final Minecraft mc = Minecraft.getMinecraft();
  private float clientYaw, clientPitch;
  private float prevClientYaw, prevClientPitch;
  private float prevRenderYaw, renderYaw;
  private float prevRenderHeadPitch, renderHeadPitch;
  private long lastRotationUpdate = 0L;
  private boolean isRotating = false;
  private boolean isReturning = false;

  public DewRotationManager() {
    this.clientYaw = 114514f;
    this.clientPitch = 114514f;
    this.prevClientYaw = 114514f;
    this.prevClientPitch = 114514f;
    this.renderYaw = 114514f;
    this.renderHeadPitch = 114514f;
    this.prevRenderYaw = 114514f;
    this.prevRenderHeadPitch = 114514f;
  }

  public void tick() {
    if (isRotating) {
      if (System.currentTimeMillis() - lastRotationUpdate > 0) {
        this.updateRotations();
      }
      if (System.currentTimeMillis() - lastRotationUpdate > ROTATION_TIMEOUT) {
        isRotating = false;
        isReturning = true;
      } else {
        return;
      }
    }

    if (isReturning && mc.thePlayer != null) {
      float targetYaw = mc.thePlayer.rotationYaw;
      float targetPitch = mc.thePlayer.rotationPitch;

      float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - this.clientYaw);
      float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - this.clientPitch);

      float returnSpeed = 40.0f;
      yawDiff = MathHelper.clamp_float(yawDiff, -returnSpeed, returnSpeed);
      pitchDiff = MathHelper.clamp_float(pitchDiff, -returnSpeed, returnSpeed);

      yawDiff += getSecureRandom() * 0.3f;
      pitchDiff += getSecureRandom() * 0.3f;

      if (Math.abs(yawDiff) < 1f && Math.abs(pitchDiff) < 1f) {
        this.updateRotations();
        mc.thePlayer.prevRenderArmYaw = this.clientYaw;
        mc.thePlayer.renderArmYaw = this.clientYaw;
        mc.thePlayer.rotationYaw = this.clientYaw;
        this.clientYaw = mc.thePlayer.rotationYaw;
        this.clientPitch = mc.thePlayer.rotationPitch;
        isReturning = false;
      } else {
        this.updateRotations();
        this.clientYaw += applyGCDFix(yawDiff);
        this.clientPitch += applyGCDFix(pitchDiff);
      }
    } else if (mc.thePlayer != null) {
      this.updateRotations();
      this.clientYaw = mc.thePlayer.rotationYaw;
      this.clientPitch = mc.thePlayer.rotationPitch;
    }
  }

  public void postMotionVisualTick() {
    if (!this.isRotating()) {
      if (mc.thePlayer == null) {
        prevRenderHeadPitch = 0f;
        renderHeadPitch = 0f;
        prevRenderYaw = 0f;
        renderYaw = 0f;
      } else {
        prevRenderHeadPitch = renderHeadPitch;
        renderHeadPitch = mc.thePlayer.rotationPitch;
        prevRenderYaw = renderYaw;
        renderYaw = mc.thePlayer.rotationYaw;
      }
      return;
    }

    prevRenderHeadPitch = renderHeadPitch;
    renderHeadPitch = this.getClientPitch();
    prevRenderYaw = renderYaw;
    renderYaw = this.getClientYaw();
  }

  private float getSecureRandom() {
    return ThreadLocalRandom.current().nextFloat() - 0.5f;
  }

  public void resetRotationsInstantly() {
    if (isRotating) {
      isRotating = false;
      isReturning = false;
      if (mc.thePlayer != null) {
        this.updateRotations();
        this.clientYaw = mc.thePlayer.rotationYaw;
        this.clientPitch = mc.thePlayer.rotationPitch;
      }
    }
  }

  public void setRotationsInstantly(float yaw, float pitch) {
    this.updateRotations();
    this.clientYaw = yaw;
    this.clientPitch = pitch;
  }

  private void updateRotations() {
    this.prevClientYaw = this.clientYaw;
    this.prevClientPitch = this.clientPitch;
  }

  private float getGCDValue() {
    double sensitivity = mc.gameSettings.mouseSensitivity;
    double f = sensitivity * 0.6F + 0.2F;
    return (float) (f * f * f * 8.0);
  }

  private float applyGCDFix(float delta) {
    float gcd = getGCDValue();
    return Math.round(delta / gcd) * gcd;
  }

  public void facePosition(double x, double y, double z, float rotationSpeed) {
    float[] rotations = getRotationsTo(x, y, z);
    rotateToward(rotations[0], rotations[1], rotationSpeed, false);
  }

  public boolean faceEntity(
      Entity entity, float rotationSpeed, boolean noRotationJitters, boolean reducedPitchRotation, double maxRange) {
    Entity pingEntity = entity;

    final double xDif = entity.posX - mc.thePlayer.posX;
    final double zDif = entity.posZ - mc.thePlayer.posZ;

    final double TO_RADS = 180.0D / StrictMath.PI;

    final AxisAlignedBB entityBB = entity.getEntityBoundingBox().expand(0.1F, 0.1F, 0.1F);
    final double playerEyePos = (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
    final double yDif =
        playerEyePos > entityBB.maxY
            ? entityBB.maxY - playerEyePos
            : playerEyePos < entityBB.minY ? entityBB.minY - playerEyePos : 0;

    final double fDist = MathHelper.sqrt_double(xDif * xDif + zDif * zDif);

    float targetYaw = (float) (StrictMath.atan2(zDif, xDif) * TO_RADS) - 90.0F;
    float targetPitch = (float) (-(StrictMath.atan2(yDif, fDist) * TO_RADS));

    float currentPitch = getClientPitch();

    if (reducedPitchRotation) {
      rotateToward(targetYaw, currentPitch, rotationSpeed, noRotationJitters);
      if (canHitEntityAtRotation(entity, getClientYaw(), getClientPitch(), maxRange)) {
        return true;
      }
    }

    rotateToward(targetYaw, targetPitch, rotationSpeed, noRotationJitters);
    return canHitEntityAtRotation(entity, getClientYaw(), getClientPitch(), maxRange);
  }

  public boolean faceBlock(BlockPos pos, float rotationSpeed, boolean noRotationJitters, double maxRange) {
    double x = pos.getX() + 0.5;
    double y = pos.getY() + 0.5;
    double z = pos.getZ() + 0.5;
    float[] rotations = getRotationsTo(x, y, z);
    rotateToward(rotations[0], rotations[1], rotationSpeed, noRotationJitters);
    return canHitBlockAtRotationWithoutFacing(pos, getClientYaw(), getClientPitch(), maxRange);
  }

  public boolean faceBlockWithFacing(
      BlockPos pos, EnumFacing facing, float rotationSpeed, boolean simple, boolean noRotationJitters, boolean antiBackSprint) {
    if (simple) {
      Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);

      IBlockState blockState = mc.theWorld.getBlockState(pos);
      AxisAlignedBB boundingBox =
          blockState.getBlock().getCollisionBoundingBox(mc.theWorld, pos, blockState);
      if (boundingBox == null) {
        boundingBox =
            new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
      }

      double clampedX = MathHelper.clamp_double(eyePos.xCoord, boundingBox.minX, boundingBox.maxX);
      double clampedY = MathHelper.clamp_double(eyePos.yCoord, boundingBox.minY, boundingBox.maxY);
      double clampedZ = MathHelper.clamp_double(eyePos.zCoord, boundingBox.minZ, boundingBox.maxZ);

      Vec3 hitVec = new Vec3(clampedX, clampedY, clampedZ);

      double diffX = hitVec.xCoord - eyePos.xCoord;
      double diffY = hitVec.yCoord - eyePos.yCoord;
      double diffZ = hitVec.zCoord - eyePos.zCoord;

      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

      float targetYaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
      float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

      targetYaw = MathHelper.wrapAngleTo180_float(targetYaw + (antiBackSprint ? 80f : 0f));
      targetPitch = MathHelper.wrapAngleTo180_float(targetPitch);

      rotateToward(targetYaw, targetPitch, rotationSpeed, noRotationJitters);
    } else {
      double x = pos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
      double y = pos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
      double z = pos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;

      double diffX = x - mc.thePlayer.posX;
      double diffY = y - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
      double diffZ = z - mc.thePlayer.posZ;

      double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

      float targetYaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f;
      float targetPitch = (float) -(Math.atan2(diffY, dist) * 180.0 / Math.PI);

      rotateToward(targetYaw, targetPitch, rotationSpeed, noRotationJitters);
    }

    return canHitBlockAtRotation(pos, facing, getClientYaw(), getClientPitch());
  }

  public void faceBlockHypixelSafe(float rotationSpeed, boolean slowdown, boolean rotate) {
    float yaw = snapToHypYaw((float) MovementUtil.getDirection(), slowdown);
    if (rotate) {
      rotateToward(yaw, 80f, rotationSpeed, true);
    }
  }

  public float tellySwap(float yaw) {
    float snappedBase = Math.round(yaw / 45.0f) * 45.0f;

    float lowerOffset;
    float upperOffset;

    if (Math.abs(snappedBase % 90.0f) < 0.001f) {
      lowerOffset = 0f;
      upperOffset = 0f;
    } else {
      lowerOffset = 45f;
      upperOffset = 45f;
    }

    float lowerCandidate = snappedBase - lowerOffset;
    float upperCandidate = snappedBase + upperOffset;

    return Math.abs(yaw - lowerCandidate) <= Math.abs(upperCandidate - yaw)
        ? lowerCandidate
        : upperCandidate;
  }

  private float snapToHypYaw(float yaw, boolean slowdown) {
    float snappedBase = Math.round(yaw / 45.0f) * 45.0f;

    float lowerOffset;
    float upperOffset;

    if (Math.abs(snappedBase % 90.0f) < 0.001f) {
      lowerOffset = 111f;
      upperOffset = 111f;
    } else {
      lowerOffset = 137f;
      upperOffset = 137f;
      if (slowdown) {
        MovementUtil.strafe(0.01f);
      }
    }

    float lowerCandidate = snappedBase - lowerOffset;
    float upperCandidate = snappedBase + upperOffset;

    return Math.abs(yaw - lowerCandidate) <= Math.abs(upperCandidate - yaw)
        ? lowerCandidate
        : upperCandidate;
  }

  public void rotateToward(float targetYaw, float targetPitch, float rotationSpeed, boolean noRotationJitters) {
    float randomDelta = noRotationJitters ? 0f : getSecureRandom() * 20f;
    float adjustedSpeed = rotationSpeed + randomDelta;

    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - this.clientYaw);
    float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - this.clientPitch);

    float jitter = 20f;

    if (!noRotationJitters) {
      yawDiff += getSecureRandom() * jitter;
      pitchDiff += getSecureRandom() * (jitter / 2);
    }

    yawDiff = MathHelper.clamp_float(yawDiff, -adjustedSpeed, adjustedSpeed);
    pitchDiff = MathHelper.clamp_float(pitchDiff, -adjustedSpeed, adjustedSpeed);

    yawDiff = applyGCDFix(yawDiff);
    pitchDiff = applyGCDFix(pitchDiff);

    this.updateRotations();

    this.clientYaw = this.clientYaw + yawDiff;
    this.clientPitch = MathHelper.clamp_float(this.clientPitch + pitchDiff, -90.0f, 90.0f);
    this.lastRotationUpdate = System.currentTimeMillis();
    this.isRotating = true;
  }

  public boolean canHitEntityAtRotation(Entity target, float yaw, float pitch, double maxRange) {
    double reach = maxRange;

    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 lookVec = getLookVecFromRotations(yaw, pitch);
    Vec3 reachVec =
        eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);

    AxisAlignedBB box = target.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
    MovingObjectPosition hit = box.calculateIntercept(eyePos, reachVec);

    return hit != null;
  }

  public boolean canHitBlockAtRotationWithoutFacing(
      BlockPos targetPos, float yaw, float pitch, double maxRange) {
    double reach = maxRange;

    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 lookVec = getLookVecFromRotations(yaw, pitch);
    Vec3 reachVec =
        eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);

    MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyePos, reachVec, false, false, false);

    return hit != null
        && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        && hit.getBlockPos().equals(targetPos);
  }

  public boolean canHitBlockAtRotation(BlockPos targetPos, EnumFacing facing, float yaw, float pitch) {
    boolean canHit = false;
    double reach = mc.playerController.getBlockReachDistance();
    MovingObjectPosition mop = mc.thePlayer.rayTrace(reach, 1.0f);

    if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
      if (mop.getBlockPos().equals(targetPos) && mop.sideHit == facing) {
        canHit = true;
      }
    }

    return canHit;
  }

  private Vec3 getLookVecFromRotations(float yaw, float pitch) {
    float yawRad = (float) Math.toRadians(yaw);
    float pitchRad = (float) Math.toRadians(pitch);

    float x = -MathHelper.cos(pitchRad) * MathHelper.sin(yawRad);
    float y = -MathHelper.sin(pitchRad);
    float z = MathHelper.cos(pitchRad) * MathHelper.cos(yawRad);

    return new Vec3(x, y, z);
  }

  public float[] getRotationsTo(double x, double y, double z) {
    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);

    double diffX = x - eyePos.xCoord;
    double diffY = y - eyePos.yCoord;
    double diffZ = z - eyePos.zCoord;

    double distXZ = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);

    float yaw = (float) (Math.atan2(diffZ, diffX) * (180D / Math.PI)) - 90F;
    float pitch = (float) -(Math.atan2(diffY, distXZ) * (180D / Math.PI));

    yaw = mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
    pitch =
        mc.thePlayer.rotationPitch
            + MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch);

    return new float[] {yaw, pitch};
  }

  public float[] getAngleDifferenceTo(Entity entity) {
    if (mc.thePlayer == null || entity == null) return new float[] {0f, 0f};

    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);

    double x = entity.posX;
    double y = entity.posY + entity.getEyeHeight() / 2.0;
    double z = entity.posZ;

    double diffX = x - eyePos.xCoord;
    double diffY = y - eyePos.yCoord;
    double diffZ = z - eyePos.zCoord;

    double distXZ = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);

    float targetYaw = (float) (Math.atan2(diffZ, diffX) * (180D / Math.PI)) - 90F;
    float targetPitch = (float) -(Math.atan2(diffY, distXZ) * (180D / Math.PI));

    float currentYaw = mc.thePlayer.rotationYaw;
    float currentPitch = mc.thePlayer.rotationPitch;

    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
    float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);

    return new float[] {Math.abs(yawDiff), Math.abs(pitchDiff)};
  }

  public float getInterpolatedYaw(float partialTicks) {
    return prevRenderYaw + (renderYaw - prevRenderYaw) * partialTicks;
  }

  public float getInterpolatedPitch(float partialTicks) {
    return prevRenderHeadPitch + (renderHeadPitch - prevRenderHeadPitch) * partialTicks;
  }

  public boolean isRotating() {
    return isRotating || isReturning;
  }

  public boolean isReturning() {
    return !isRotating && isReturning;
  }

  public float getClientYaw() {
    return clientYaw;
  }

  public float getClientPitch() {
    return clientPitch;
  }

  public float getPrevClientYaw() {
    return prevClientYaw;
  }

  public float getPrevClientPitch() {
    return prevClientPitch;
  }
}
