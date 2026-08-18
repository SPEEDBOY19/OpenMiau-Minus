package miau.module.modules.player.scaffold.rotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.property.Property;
import miau.property.properties.ModeProperty;
import miau.util.math.RandomUtil;
import miau.util.player.RotationUtil;
import net.minecraft.util.MathHelper;

public class RotationHandler {

  private final Scaffold scaffold;
  private final Map<Integer, IRotationLogic> rotationLogics = new HashMap<>();

  public final ModeProperty rotationMode =
      new ModeProperty(
          "rotations", 2, new String[] {
              "NONE", "Normal", "Backwards", "Strict", "Precise", "Constant", "Sideways", "Beta", "Telly"
          });

  public List<Property<?>> getProperties() {
    return Arrays.asList(rotationMode);
  }

  public RotationHandler(Scaffold scaffold) {
    this.scaffold = scaffold;
    rotationLogics.put(1, new DefaultRotation());
    rotationLogics.put(2, new BackwardsRotation());
    rotationLogics.put(3, new DefaultRotation());
    rotationLogics.put(4, new DefaultRotation());
    rotationLogics.put(5, new DefaultRotation());
    rotationLogics.put(6, new SidewaysRotation());
    rotationLogics.put(7, new BetaRotation());
    rotationLogics.put(8, new SnapRotation());
  }

  public void handleInitialRotation(
      UpdateEvent event, float currentYaw, float yawDiffTo180, float diagonalYaw) {
    IRotationLogic logic = rotationLogics.get(rotationMode.getValue());
    if (logic != null) {
      logic.handleInitialRotation(scaffold, event, currentYaw, yawDiffTo180, diagonalYaw);
    }
  }

  public void handleUpdateRotation(
      UpdateEvent event,
      float yawDiffTo180,
      float diagonalYaw,
      boolean towerRotating,
      boolean willPlaceThisTick) {

    int mode = rotationMode.getValue();
    boolean betaMode = mode == 7;
    boolean snapMode = mode == 8;

    if (mode == 0) return;

    float targetYaw = scaffold.yaw;
    float targetPitch = scaffold.pitch;

    if (!betaMode && !snapMode
        && scaffold.towering
        && (Scaffold.mc.thePlayer.motionY > 0.0
            || Scaffold.mc.thePlayer.posY > (double) (scaffold.startY + 1))) {
      handleTowerRotation(event, towerRotating);
      targetYaw = scaffold.yaw;
      targetPitch = scaffold.pitch;
    }

    if (!snapMode
        && scaffold.towering
        && (Scaffold.mc.thePlayer.motionY > 0.0
            || Scaffold.mc.thePlayer.posY > (double) (scaffold.startY + 1))) {
      float yawDiff = MathHelper.wrapAngleTo180_float(scaffold.yaw - event.getYaw());
      float tolerance =
          scaffold.rotationTick >= 2
              ? RandomUtil.nextFloat(
                  scaffold.options.tellystartrotationminspeed.getValue(),
                  scaffold.options.tellystartrotationmaxspeed.getValue())
              : RandomUtil.nextFloat(
                  scaffold.options.tellynormalrotationminspeed.getValue(),
                  scaffold.options.tellynormalrotationmaxspeed.getValue());
      if (Math.abs(yawDiff) > tolerance) {
        float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
        scaffold.yaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
        scaffold.rotationTick = Math.max(scaffold.rotationTick, 1);
      }
    }

    float placeYaw, placePitch;

    if (betaMode) {
      float[] pipelineResult =
          ((BetaRotation) rotationLogics.get(mode))
              .handleBetaUpdate(
                  scaffold, event, yawDiffTo180, diagonalYaw, towerRotating, willPlaceThisTick);
      placeYaw = pipelineResult[0];
      placePitch = pipelineResult[1];
      scaffold.placeYaw = placeYaw;
      scaffold.placePitch = placePitch;
    } else if (snapMode) {
      ((SnapRotation) rotationLogics.get(mode)).updateRotation(scaffold, event);
      placeYaw = scaffold.yaw;
      placePitch = scaffold.pitch;
      scaffold.placeYaw = placeYaw;
      scaffold.placePitch = placePitch;
    } else {
      float[] placeGcd =
          RotationUtil.flexRotation(targetYaw, targetPitch, event.getYaw(), event.getPitch());
      placeYaw = placeGcd[0];
      placePitch = placeGcd[1];
      scaffold.placeYaw = placeYaw;
      scaffold.placePitch = placePitch;
    }

    boolean moveFix = scaffold.options.movementCorrection.getValue();
    float packetYaw = placeYaw;
    float packetPitch = placePitch;
    
    // SỬA LỖI 1: Áp dụng Movement Correction cho cả Mode 2 (Backwards) VÀ Mode 3 (Strict)
    if (moveFix && (mode == 2 || mode == 3) && !Float.isNaN(scaffold.bridgeYaw) && !willPlaceThisTick) {
      float bridgePitch = !Float.isNaN(scaffold.placePitch) ? scaffold.placePitch : targetPitch;
      float[] bridgeGcd =
          RotationUtil.flexRotation(
              scaffold.bridgeYaw, bridgePitch, event.getYaw(), event.getPitch());
      packetYaw = bridgeGcd[0];
      packetPitch = bridgeGcd[1];
    }

    targetYaw = packetYaw;
    targetPitch = packetPitch;

    // SỬA LỖI 2: Tắt duplicatePlaceRotNudge khi ở Strict (mode 3) để giữ đường đi chéo thẳng tắp
    if (!betaMode && !snapMode && mode != 3 && willPlaceThisTick) {
      float deltaX = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw()));
      if (deltaX > 2.0F
          && !Float.isNaN(scaffold.lastPlacedAbsPacketYawDelta)
          && Math.abs(deltaX - scaffold.lastPlacedAbsPacketYawDelta) < 0.0001F) {
        double gcdStep = RotationUtil.mouseGcdStepMultiplier();
        if (gcdStep >= 0.01) {
          targetYaw += (float) (scaffold.duplicatePlaceRotNudgeSign * gcdStep);
          scaffold.duplicatePlaceRotNudgeSign = -scaffold.duplicatePlaceRotNudgeSign;
        }
      }
      scaffold.lastPlacedAbsPacketYawDelta = deltaX;
    }

    event.setRotation(targetYaw, targetPitch, 3);
    scaffold.lastMoveFixPacketYaw = targetYaw;
    if (moveFix) {
      event.setPervRotation(targetYaw, 3);
    }
  }

  private void handleTowerRotation(UpdateEvent event, boolean towerRotating) {
    float yawDiff = MathHelper.wrapAngleTo180_float(scaffold.yaw - event.getYaw());
    float tolerance =
        scaffold.rotationTick >= 2
            ? RandomUtil.nextFloat(
                scaffold.options.tellystartrotationminspeed.getValue(),
                scaffold.options.tellystartrotationmaxspeed.getValue())
            : RandomUtil.nextFloat(
                scaffold.options.tellynormalrotationminspeed.getValue(),
                scaffold.options.tellynormalrotationmaxspeed.getValue());
    if (Math.abs(yawDiff) > tolerance) {
      float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
      scaffold.yaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
      scaffold.rotationTick = Math.max(scaffold.rotationTick, 1);
    }

    if (towerRotating && scaffold.isTowering()) {
      float yawDelta =
          MathHelper.wrapAngleTo180_float(Scaffold.mc.thePlayer.rotationYaw - event.getYaw());
      scaffold.yaw =
          RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
      scaffold.pitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
    }
    scaffold.rotationTick = 3;
    scaffold.towering = true;
  }
}