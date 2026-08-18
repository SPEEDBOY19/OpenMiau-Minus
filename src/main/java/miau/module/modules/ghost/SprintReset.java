package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import java.util.concurrent.ThreadLocalRandom;

public class SprintReset extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private long lastAttackTime = 0L;
  private EntityLivingBase lastTarget = null;
  private boolean serverSprintState = false;
  private boolean shouldReset = false;
  private int resetDelayTicks = 0;
  private int tickCounter = 0;
  private int attackSelfHurtTime = 0;

  public final BooleanProperty notWhenHurt = new BooleanProperty("not-when-hurt", true);
  public final IntProperty minTargetTicksFromGround = new IntProperty("min-target-ticks-ground", 0, 0, 10);

  public SprintReset() {
    super("SprintReset", false);
  }

  @Override
  public void onEnabled() {
    shouldReset = false;
    resetDelayTicks = 0;
    tickCounter = 0;
  }

  @EventTarget
  public void onPacket(PacketEvent e) {
    if (!this.isEnabled()) return;

    if (e.getType() == EventType.SEND) {
      if (e.getPacket() instanceof C0BPacketEntityAction) {
        C0BPacketEntityAction packet = (C0BPacketEntityAction) e.getPacket();
        if (packet.getAction() == C0BPacketEntityAction.Action.START_SPRINTING) {
          serverSprintState = true;
        } else if (packet.getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING) {
          serverSprintState = false;
        }
      }
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!this.isEnabled()) return;
    if (event.getTarget() instanceof EntityLivingBase) {
      lastTarget = (EntityLivingBase) event.getTarget();
      lastAttackTime = System.currentTimeMillis();
      attackSelfHurtTime = mc.thePlayer.hurtTime;
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;

    if (lastTarget != null) {
      int ping = 0;
      if (mc.getNetHandler() != null && mc.thePlayer != null) {
        net.minecraft.client.network.NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info != null) ping = info.getResponseTime();
      }

      long attackWindow = 200L + ping;
      boolean attackRecently = (System.currentTimeMillis() - lastAttackTime) <= attackWindow;

      if (lastTarget.hurtTime == 10 && attackRecently && mc.thePlayer.isSprinting() && serverSprintState) {
        shouldReset = true;
        resetDelayTicks = ThreadLocalRandom.current().nextInt(2, 5); // [2, 4] delay
        tickCounter = 0;
      }
    }

    if (shouldReset) {
      tickCounter++;
      if (tickCounter >= resetDelayTicks) {
        shouldReset = false;
      }
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled()) return;

    if (shouldReset() && mc.thePlayer.movementInput != null) {
      mc.thePlayer.movementInput.moveForward = 0.0F;
      mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }
  }

  private boolean shouldReset() {
    if (!shouldReset) return false;
    if (lastTarget == null) return false;

    if (attackSelfHurtTime != 0 && mc.thePlayer.hurtTime <= 2) {
      return false;
    }

    boolean inCritFall = mc.thePlayer.fallDistance > 0.0F 
        && !mc.thePlayer.onGround 
        && !mc.thePlayer.isOnLadder() 
        && !mc.thePlayer.isInWater();
    if (inCritFall) {
      return false;
    }

    if (mc.thePlayer.hurtTime != 0 && notWhenHurt.getValue()) {
      return false;
    }

    if (mc.thePlayer.hurtTime != 0) {
      double targetDistToGround = lastTarget.posY - MoveUtil.findGround(lastTarget);
      double ticksToGround = targetDistToGround / 0.5;
      if (ticksToGround < minTargetTicksFromGround.getValue()) {
        return false;
      }
    }

    return true;
  }
}
