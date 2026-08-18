package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.JumpEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public class Strafe extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Vanilla", "Matrix"});
  public final FloatProperty strength = new FloatProperty("Strength", 0.5F, 0F, 1F, () -> this.mode.getModeString().equals("Vanilla"));
  public final BooleanProperty noMoveStop = new BooleanProperty("NoMoveStop", false, () -> this.mode.getModeString().equals("Vanilla"));
  public final BooleanProperty onGroundStrafe = new BooleanProperty("OnGroundStrafe", false, () -> this.mode.getModeString().equals("Vanilla"));
  public final BooleanProperty allDirectionsJump = new BooleanProperty("AllDirectionsJump", false);

  private boolean wasDown = false;
  private boolean jump = false;

  public Strafe() {
    super("Strafe", false);
  }

  private double getDirection() {
    float yaw = mc.thePlayer.rotationYaw;
    float forward = 1f;
    if (mc.thePlayer.movementInput.moveForward < 0f) {
      yaw += 180f;
      forward = -0.5f;
    } else if (mc.thePlayer.movementInput.moveForward > 0f) {
      forward = 0.5f;
    }
    if (mc.thePlayer.movementInput.moveStrafe < 0f) {
      yaw += 90f * forward;
    } else if (mc.thePlayer.movementInput.moveStrafe > 0f) {
      yaw -= 90f * forward;
    }
    return Math.toRadians(yaw);
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;
    if (this.jump) {
      mc.thePlayer.motionY = 0.0;
    }
  }

  @Override
  public void onEnabled() {
    this.wasDown = false;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (mc.thePlayer.onGround && mc.gameSettings.keyBindJump.isKeyDown() && this.allDirectionsJump.getValue() && MoveUtil.isMoving() && !(mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || mc.thePlayer.isOnLadder() || ((IAccessorEntity) mc.thePlayer).getIsInWeb())) {
      if (mc.gameSettings.keyBindJump.isKeyDown()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        this.wasDown = true;
      }
      float yaw = mc.thePlayer.rotationYaw;
      mc.thePlayer.rotationYaw = (float) Math.toDegrees(this.getDirection());
      if (!mc.gameSettings.keyBindJump.isKeyDown()) {
        mc.thePlayer.jump();
      }
      mc.thePlayer.rotationYaw = yaw;
      this.jump = true;
      if (this.wasDown) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
        this.wasDown = false;
      }
    } else {
      this.jump = false;
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    if (this.mode.getModeString().equals("Vanilla")) {
      this.handleLiquidBounceStrafe();
    } else {
      this.handleMatrixStrafe();
    }
  }

  private void handleLiquidBounceStrafe() {
    if (!MoveUtil.isMoving()) {
      if (this.noMoveStop.getValue()) {
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionZ = 0.0;
      }
      return;
    }

    double shotSpeed = MoveUtil.getSpeed();
    double speed = shotSpeed * this.strength.getValue();
    double motionX = mc.thePlayer.motionX * (1 - this.strength.getValue());
    double motionZ = mc.thePlayer.motionZ * (1 - this.strength.getValue());

    if (!mc.thePlayer.onGround || this.onGroundStrafe.getValue()) {
      double yaw = this.getDirection();
      mc.thePlayer.motionX = -Math.sin(yaw) * speed + motionX;
      mc.thePlayer.motionZ = Math.cos(yaw) * speed + motionZ;
    }
  }

  private void handleMatrixStrafe() {
    if (!MoveUtil.isMoving()) {
      return;
    }

    double currentSpeed = MoveUtil.getSpeed();
    if (currentSpeed <= 0.0) {
      return;
    }

    float yaw = mc.thePlayer.rotationYaw;
    if (mc.thePlayer.moveForward < 0.0F) {
      yaw += 180.0F;
    } else {
      float forwardMultiplier;
      if (mc.thePlayer.moveForward < 0.0F) {
        forwardMultiplier = -0.5F;
      } else if (mc.thePlayer.moveForward > 0.0F) {
        forwardMultiplier = 0.5F;
      } else {
        forwardMultiplier = 1.0F;
      }

      if (mc.thePlayer.moveStrafing > 0.0F) {
        yaw -= 90.0F * forwardMultiplier;
      }

      if (mc.thePlayer.moveStrafing < 0.0F) {
        yaw += 90.0F * forwardMultiplier;
      }
    }

    double direction = Math.toRadians(yaw);
    mc.thePlayer.motionX = -Math.sin(direction) * currentSpeed;
    mc.thePlayer.motionZ = Math.cos(direction) * currentSpeed;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}