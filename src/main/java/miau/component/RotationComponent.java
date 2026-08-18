package miau.component;

import miau.event.EventTarget;
import miau.event.impl.JumpEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PlayerUpdateEvent;
import miau.event.impl.StrafeEvent;
import miau.management.RotationState;
import miau.util.player.MoveUtil;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;

public final class RotationComponent {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private static boolean active = false;
  private static boolean smoothed = false;

  public static float[] rotations;
  public static float[] lastRotations = new float[] {0, 0};
  public static float[] targetRotations;
  public static float[] lastServerRotations;

  private static int correctMovement;

  public static boolean isActive() {
    return active;
  }

  public static boolean isSmoothed() {
    return smoothed;
  }

  public static void setActive(boolean active, int correctMovement) {
    RotationComponent.active = active;
    RotationComponent.correctMovement = correctMovement;
  }

  public static void markSmoothed(float[] newRotations) {
    RotationComponent.rotations = newRotations;
    RotationComponent.smoothed = true;
    RotationState.applyState(true, newRotations[0], newRotations[1], lastRotations[0], 999);
  }

  public static void correctDisabledRotations() {
    if (rotations == null) return;
    float[] current = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
    float[] fixed =
        RotationUtil.applySensitivityPatch(
            current[0], current[1], lastRotations[0], lastRotations[1]);
    float[] finalRot = RotationUtil.resetRotation(fixed);

    mc.thePlayer.rotationYaw = finalRot[0];
    mc.thePlayer.rotationPitch = finalRot[1];
  }

  public static void reset() {
    active = false;
    smoothed = false;
    rotations = null;
    targetRotations = null;
    correctMovement = 0;
  }

  @EventTarget
  public void onPlayerUpdate(PlayerUpdateEvent event) {
    if (active && rotations != null) {
      final float yaw = rotations[0];
      final float pitch = rotations[1];

      RotationState.applyState(true, yaw, pitch, lastRotations[0], 999);

      mc.thePlayer.rotationYawHead = yaw;
      mc.thePlayer.renderYawOffset = yaw;

      lastServerRotations = new float[] {yaw, pitch};

      if (correctMovement == 3) {
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (forward == 0 && strafe == 0)
          ;
        else if (Math.abs(
                yaw % 360
                    - Math.toDegrees(MoveUtil.direction(mc.thePlayer.rotationYaw, forward, strafe))
                        % 360)
            > 45) {
          KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
          mc.thePlayer.setSprinting(false);
        }
      }

      float currentYaw = mc.thePlayer.rotationYaw;
      float currentPitch = mc.thePlayer.rotationPitch;
      if (Math.abs(MathHelper.wrapAngleTo180_float(yaw - currentYaw)) < 1
          && Math.abs(pitch - currentPitch) < 1) {
        active = false;
        correctDisabledRotations();
      }

      lastRotations = rotations;
    } else {
      lastRotations = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
    }

    if (rotations == null) {
      rotations = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
    }
    targetRotations = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
    smoothed = false;
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (active && correctMovement == 1 && rotations != null) {
      MoveUtil.fixMovement(rotations[0]);
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (active && (correctMovement == 1 || correctMovement == 2) && rotations != null) {
      event.setYaw(rotations[0]);
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (active
        && (correctMovement == 1 || correctMovement == 2 || correctMovement == 3)
        && rotations != null) {
      event.setYaw(rotations[0]);
    }
  }
}
