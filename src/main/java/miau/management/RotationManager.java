package miau.management;

import miau.event.EventTarget;
import miau.event.impl.Render3DEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

/**
 * RotationManager handles silent and visible rotation transitions.
 *
 * <p>Silent mode: Sets rotations via UpdateEvent (server rotations) while using RotationState for
 * visual-only rendering (client sees locked view but server receives different angles).
 *
 * <p>Normal mode: Directly modifies player rotation fields for "Lock View" style.
 */
public class RotationManager {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private float lastUpdate;
  private float yawDelta;
  private float pitchDelta;
  private int priority;
  private boolean rotated;

  // Silent rotation state
  private boolean silentMode;
  private float silentYaw;
  private float silentPitch;
  private boolean silentActive;

  public RotationManager() {
    this.lastUpdate = Float.NaN;
    this.yawDelta = Float.NaN;
    this.pitchDelta = Float.NaN;
    this.priority = Integer.MIN_VALUE;
    this.rotated = false;
    this.silentMode = false;
    this.silentActive = false;
  }

  /**
   * Sets a silent rotation that modifies only the visual (render) rotation via RotationState, while
   * leaving the actual player rotation unchanged. The server rotation is set through UpdateEvent in
   * KillAura.
   */
  public void setSilentRotation(float yaw, float pitch, int priority) {
    if (this.priority <= priority) {
      this.silentMode = true;
      this.silentYaw = yaw;
      this.silentPitch = pitch;
      this.silentActive = true;

      // Apply visual rotation state for rendering/movefix
      RotationState.applyState(true, yaw, pitch, yaw, priority);

      // Don't touch player rotation fields - those are server rotations
      this.rotated = true;
    }
  }

  /**
   * Sets a direct rotation that modifies the player's actual rotation fields. Used for Lock View
   * mode.
   */
  public void setRotation(float yaw, float pitch, int priority, boolean force) {
    if (this.priority <= priority) {
      this.silentMode = false;
      this.priority = priority;
      this.yawDelta = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
      this.pitchDelta = MathHelper.clamp_float(pitch - mc.thePlayer.rotationPitch, -90.0F, 90.0F);
      this.lastUpdate = 0.0F;
      this.rotated = force;
      this.applyRotation(0.0F);
    }
  }

  public boolean isRotated() {
    return this.rotated;
  }

  public boolean isSilentActive() {
    return this.silentActive;
  }

  public float getSilentYaw() {
    return this.silentYaw;
  }

  public float getSilentPitch() {
    return this.silentPitch;
  }

  private void applyRotation(float partialTicks) {
    if (Float.isNaN(this.lastUpdate)
        || Float.isNaN(this.yawDelta)
        || Float.isNaN(this.pitchDelta)) {
      return;
    }
    if (mc.thePlayer != null
        && !Float.isNaN(this.yawDelta)
        && !Float.isNaN(this.pitchDelta)
        && !Float.isNaN(this.lastUpdate)) {
      float yaw = this.yawDelta * (partialTicks - this.lastUpdate);
      if (yaw != 0.0F) {
        mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;
        mc.thePlayer.rotationYaw += yaw;
      }
      float pitch = this.pitchDelta * (partialTicks - this.lastUpdate);
      if (pitch != 0.0F) {
        mc.thePlayer.prevRotationPitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationPitch += pitch;
        mc.thePlayer.rotationPitch =
            MathHelper.clamp_float(mc.thePlayer.rotationPitch, -90.0F, 90.0F);
      }
      this.lastUpdate = partialTicks;
    }
  }

  private void resetRotationState() {
    this.lastUpdate = Float.NaN;
    this.yawDelta = Float.NaN;
    this.pitchDelta = Float.NaN;
    this.priority = Integer.MIN_VALUE;
    this.rotated = false;
    this.silentMode = false;
    this.silentActive = false;
  }

  @EventTarget(Priority.HIGHEST)
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) {
      return;
    }
    if (!this.silentMode) {
      this.applyRotation(1.0F);
    }
    this.resetRotationState();
  }

  @EventTarget(Priority.HIGHEST)
  public void onRender3D(Render3DEvent event) {
    if (!this.silentMode) {
      this.applyRotation(event.getPartialTicks());
    }
  }
}
