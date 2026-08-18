package miau.module.modules.combat.killaura.rotation;

import miau.component.RotationComponent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Random;

public class IntaveRotation extends RotationMode {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final Random RNG = new Random();
  private long tick = 0;
  private double noiseSeed;
  private double yawNoisePhase;
  private double pitchNoisePhase;
  private double yawNoiseFreq = 0.07;
  private double pitchNoiseFreq = 0.11;
  private double yawBias;
  private double pitchBias;
  private double yawBiasTarget;
  private double pitchBiasTarget;
  private int biasChangeCooldown;
  private int gcdBreakTimer = 0;
  private int gcdBreakInterval = 3;
  private boolean gcdOffsetPositive = true;
  private double overshootYaw;
  private double overshootPitch;
  private int correctionTicks;
  private double currentSpeedMultiplier = 1.0;
  private double speedPhase;
  private int swingSkipCounter = 0;

  public IntaveRotation(KillAura killAura) {
    super(killAura, "INTAVE");
    this.noiseSeed = RNG.nextDouble() * 1000;
    this.yawNoisePhase = RNG.nextDouble() * 100;
    this.pitchNoisePhase = RNG.nextDouble() * 100;
    this.yawBiasTarget = (RNG.nextDouble() - 0.5) * 4.0;
    this.pitchBiasTarget = (RNG.nextDouble() - 0.5) * 3.0;
  }

  @Override
  public float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event) {
    if (rotSpeed == 0) return lastRots;

    RotationComponent.setActive(true, this.killAura.moveFix.getValue());
    tick++;

    EntityLivingBase target = killAura.getTarget();
    if (target == null) {
      RotationComponent.markSmoothed(lastRots);
      return lastRots;
    }

    speedPhase += 0.03 + (RNG.nextDouble() - 0.5) * 0.01;
    double speedWave = 0.7 + 0.3 * (0.5 + 0.5 * Math.sin(speedPhase));
    double yawDist = Math.abs(MathHelper.wrapAngleTo180_float(targetRots[0] - lastRots[0]));
    double pitchDist = Math.abs(targetRots[1] - lastRots[1]);
    double totalDist = Math.sqrt(yawDist * yawDist + pitchDist * pitchDist);
    double distanceFactor = 0.6 + 0.4 * Math.min(1.0, totalDist / 15.0);
    double effectiveSpeed = rotSpeed * speedWave * distanceFactor;
    if (effectiveSpeed < 2.0) effectiveSpeed = 2.0;
    if (effectiveSpeed > 180.0) effectiveSpeed = 180.0;

    yawNoisePhase += yawNoiseFreq + (RNG.nextDouble() - 0.5) * 0.008;
    pitchNoisePhase += pitchNoiseFreq + (RNG.nextDouble() - 0.5) * 0.008;

    if (tick % 100 == 0) {
      yawNoiseFreq = 0.04 + RNG.nextDouble() * 0.12;
      pitchNoiseFreq = 0.06 + RNG.nextDouble() * 0.15;
    }

    Vec3 aimPoint = computeNoisyAimPoint(target);
    if (aimPoint == null) {
      RotationComponent.markSmoothed(lastRots);
      return lastRots;
    }

    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
    double dx = aimPoint.xCoord - eyePos.xCoord;
    double dy = aimPoint.yCoord - eyePos.yCoord;
    double dz = aimPoint.zCoord - eyePos.zCoord;
    double horizDist = Math.sqrt(dx * dx + dz * dz);

    float baseYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    float basePitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));

    biasChangeCooldown--;
    if (biasChangeCooldown <= 0) {
      yawBiasTarget = (RNG.nextDouble() - 0.5) * 5.5;
      pitchBiasTarget = (RNG.nextDouble() - 0.5) * 6.0;
      biasChangeCooldown = 8 + RNG.nextInt(20);
    }
    yawBias += (yawBiasTarget - yawBias) * 0.15;
    pitchBias += (pitchBiasTarget - pitchBias) * 0.12;

    double yawOscillation = MathHelper.sin((float) yawNoisePhase) * 1.2;
    double pitchOscillation = MathHelper.sin((float) pitchNoisePhase) * 1.8;

    double yawJitter = (RNG.nextDouble() - 0.5) * 0.8;
    double pitchJitter = (RNG.nextDouble() - 0.5) * 1.2;

    if (correctionTicks > 0) {
      baseYaw += (float) overshootYaw;
      basePitch += (float) overshootPitch;
      correctionTicks--;

      overshootYaw *= 0.7;
      overshootPitch *= 0.7;
      if (Math.abs(overshootYaw) < 0.01) overshootYaw = 0;
      if (Math.abs(overshootPitch) < 0.01) overshootPitch = 0;
    } else if (RNG.nextDouble() < 0.08 && totalDist < 10) {
      overshootYaw = (RNG.nextDouble() - 0.5) * 2.5;
      overshootPitch = (RNG.nextDouble() - 0.5) * 2.0;
      correctionTicks = 3 + RNG.nextInt(5);
    }

    float targetYaw = baseYaw
        + (float) yawBias
        + (float) yawOscillation
        + (float) yawJitter;

    float targetPitch = basePitch
        + (float) pitchBias
        + (float) pitchOscillation
        + (float) pitchJitter;

    targetPitch = MathHelper.clamp_float(targetPitch, -90f, 90f);

    float yaw = lastRots[0];
    float pitch = lastRots[1];

    float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - yaw);
    float deltaPitch = targetPitch - pitch;

    double angularDist = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
    if (angularDist > 0.001) {
      double distYaw = Math.abs(deltaYaw / angularDist);
      double distPitch = Math.abs(deltaPitch / angularDist);
      double maxYawStep = effectiveSpeed * distYaw;
      double maxPitchStep = effectiveSpeed * distPitch;

      float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYawStep), -maxYawStep);
      float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitchStep), -maxPitchStep);

      yaw = lastRots[0] + moveYaw;
      pitch = lastRots[1] + movePitch;
    }

    gcdBreakTimer++;
    gcdBreakInterval = 5 + RNG.nextInt(8);

    float gcdYaw = yaw;
    float gcdPitch = pitch;

    if (gcdBreakTimer >= gcdBreakInterval) {
      gcdBreakTimer = 0;

      float sens = (float) (mc.gameSettings.mouseSensitivity * 0.6F + 0.2F);
      double mult = sens * sens * sens * 8.0F * 0.15D;
      gcdOffsetPositive = !gcdOffsetPositive;
      float offset = (float) (mult * (gcdOffsetPositive ? 1 : -1) * 0.5);
      gcdYaw += offset * 0.3f;
      gcdPitch += offset * 0.5f;
      gcdPitch = MathHelper.clamp_float(gcdPitch, -90f, 90f);
    }

    float[] fixedRotations = RotationUtil.applySensitivityPatch(
        gcdYaw, gcdPitch, lastRots[0], lastRots[1]);
    yaw = fixedRotations[0];
    pitch = MathHelper.clamp_float(fixedRotations[1], -90f, 90f);
    if (Math.abs(yaw - lastRots[0]) < 0.01 && angularDist > 0.5) {
      yaw += (RNG.nextFloat() - 0.5f) * 0.05f;
    }
    if (Math.abs(pitch - lastRots[1]) < 0.01 && angularDist > 0.5) {
      pitch += (RNG.nextFloat() - 0.5f) * 0.05f;
      pitch = MathHelper.clamp_float(pitch, -90f, 90f);
    }
    int iterations = (int) (Minecraft.getDebugFPS() / 20 + RNG.nextDouble() * 5);
    for (int i = 1; i <= iterations; i++) {
      if (Math.abs(yaw - lastRots[0]) + Math.abs(pitch - lastRots[1]) > 0.0001) {
        yaw += (float) ((RNG.nextDouble() - 0.5) / 1500.0);
        pitch -= (float) (RNG.nextDouble() / 300.0);
      }
      float[] fixPass = RotationUtil.applySensitivityPatch(yaw, pitch, lastRots[0], lastRots[1]);
      yaw = fixPass[0];
      pitch = Math.max(-90f, Math.min(90f, fixPass[1]));
    }

    float finalDeltaYaw = MathHelper.wrapAngleTo180_float(yaw - lastRots[0]);
    if (Math.abs(finalDeltaYaw) > 170) {
      yaw = lastRots[0] + Math.copySign(170, finalDeltaYaw);
    }

    float[] result = new float[] {yaw, pitch};
    RotationComponent.markSmoothed(result);
    return result;
  }

  private Vec3 computeNoisyAimPoint(EntityLivingBase target) {
    if (target == null) return null;

    AxisAlignedBB bb = target.getEntityBoundingBox();
    double bbWidth = bb.maxX - bb.minX;
    double bbHeight = bb.maxY - bb.minY;
    double bbDepth = bb.maxZ - bb.minZ;

    double baseX = (bb.minX + bb.maxX) / 2.0;
    double baseY = bb.minY + bbHeight * 0.75;
    double baseZ = (bb.minZ + bb.maxZ) / 2.0;

    double hNoise1 = improvedNoise(noiseSeed, yawNoisePhase * 0.5, 0);
    double hNoise2 = improvedNoise(noiseSeed + 100, pitchNoisePhase * 0.5, 0);
    double horizontalSpread = 0.35 * Math.min(0.6, bbWidth);
    double hOffsetX = hNoise1 * horizontalSpread;
    double hOffsetZ = hNoise2 * horizontalSpread;

    double vNoise = improvedNoise(noiseSeed + 200, pitchNoisePhase * 0.3, yawNoisePhase * 0.2);
    double targetSpeed = Math.hypot(
        target.posX - target.lastTickPosX,
        target.posZ - target.lastTickPosZ);
    double verticalSpread = 0.25 + 0.3 * Math.min(1.0, targetSpeed * 5.0);
    if (bbHeight > 1.5) {
      verticalSpread += 0.15;
    }
    double vOffset = vNoise * bbHeight * verticalSpread;

    double predX = target.posX + (target.posX - target.lastTickPosX) * 0.2;
    double predY = target.posY + (target.posY - target.lastTickPosY) * 0.2;
    double predZ = target.posZ + (target.posZ - target.lastTickPosZ) * 0.2;

    double aimX = predX + hOffsetX;
    double aimY = predY + baseY - target.posY + vOffset;
    double aimZ = predZ + hOffsetZ;

    double margin = 0.05;
    aimX = Math.max(bb.minX + margin, Math.min(bb.maxX - margin, aimX));
    aimY = Math.max(bb.minY + margin, Math.min(bb.maxY - margin, aimY));
    aimZ = Math.max(bb.minZ + margin, Math.min(bb.maxZ - margin, aimZ));

    return new Vec3(aimX, aimY, aimZ);
  }

  private double improvedNoise(double seed, double x, double y) {
    double value = Math.sin(seed * 127.1 + x * 311.7 + y * 74.7) * 43758.5453;
    value = value - Math.floor(value);
    return value * 2.0 - 1.0;
  }

  @Override
  public int hashCode() {
    return "INTAVE".hashCode();
  }
}
