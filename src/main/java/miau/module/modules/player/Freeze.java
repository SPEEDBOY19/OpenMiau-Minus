package miau.module.modules.player;

import miau.event.EventTarget;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Freeze extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private double savedMotionX;
  private double savedMotionY;
  private double savedMotionZ;

  private int tickCounter;
  private int phase; // 0 = stasis, 1 = release
  private static final int STASIS_TICKS = 45;
  private static final int RELEASE_TICKS = 1;

  public Freeze() {
    super("Freeze", false);
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer != null) {
      savedMotionX = mc.thePlayer.motionX;
      savedMotionY = mc.thePlayer.motionY;
      savedMotionZ = mc.thePlayer.motionZ;
    }
    tickCounter = 0;
    phase = 0;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled()) return;

    tickCounter++;

    if (phase == 0 && tickCounter >= STASIS_TICKS) {
      phase = 1;
      tickCounter = 0;
      mc.thePlayer.motionX = savedMotionX;
      mc.thePlayer.motionY = savedMotionY;
      mc.thePlayer.motionZ = savedMotionZ;
    } else if (phase == 1 && tickCounter >= RELEASE_TICKS) {
      phase = 0;
      tickCounter = 0;
      savedMotionX = mc.thePlayer.motionX;
      savedMotionY = mc.thePlayer.motionY;
      savedMotionZ = mc.thePlayer.motionZ;
    }

    if (phase == 0) {
      mc.thePlayer.motionX = 0.0;
      mc.thePlayer.motionZ = 0.0;
      mc.thePlayer.motionY = 0.0;
    }
    if (mc.thePlayer != null && mc.thePlayer.onGround) {
      this.setEnabled(false);
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled() && phase == 0) {
      mc.thePlayer.movementInput.moveForward = 0.0f;
      mc.thePlayer.movementInput.moveStrafe = 0.0f;
      mc.thePlayer.movementInput.jump = false;
      mc.thePlayer.movementInput.sneak = false;
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (this.isEnabled() && phase == 0) {
      mc.thePlayer.motionX = 0.0;
      mc.thePlayer.motionY = 0.0;
      mc.thePlayer.motionZ = 0.0;
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled() && phase == 0) {
      event.setForward(0.0f);
      event.setStrafe(0.0f);
      event.setFriction(0.0f);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) return;
    if (!(event.getPacket() instanceof C03PacketPlayer)) return;

    if (phase == 1) return;

    if (mc.thePlayer == null || mc.thePlayer.hurtTime != 0) return;

    if (!(event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
      event.setCancelled(true);
    }
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null) {
      mc.thePlayer.motionX = savedMotionX;
      mc.thePlayer.motionY = savedMotionY;
      mc.thePlayer.motionZ = savedMotionZ;
    }
    tickCounter = 0;
    phase = 0;
  }
}
