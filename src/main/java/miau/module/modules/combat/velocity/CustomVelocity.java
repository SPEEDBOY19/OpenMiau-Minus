package miau.module.modules.combat.velocity;

import miau.Miau;
import miau.event.impl.AttackEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.KillAura;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.ChatUtil;
import miau.util.network.PacketUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import miau.util.network.PacketUtil;

public class CustomVelocity extends VelocityMode {
  // Sprint control
  public final ModeProperty SprintControl =
      new ModeProperty(
          "sprint-control",
          2,
          new String[] {"KeepSprint", "StopSprint", "NoControl"});
  public final BooleanProperty tryForward = new BooleanProperty("try-forward", false);
  public final FloatProperty tryingForwardTime = new FloatProperty("try-forward-time", 50f, 0f, 1000f);
  public final BooleanProperty disableStrafeInput = new BooleanProperty("disable-strafe-input", false);
  public final FloatProperty disableStrafeInputTime = new FloatProperty("disable-strafe-input-time", 50f, 0f, 1000f);
  // Progressive
  public final ModeProperty progressivemode =
      new ModeProperty("progressive-mode", 0, new String[] {"Decrease", "Increase"});
  public final FloatProperty progressivestepfactor = new FloatProperty("progressive-step-factor", 0.02f, 0f, 1f);
  public final FloatProperty maxProgressiveFactor = new FloatProperty("max-progressive-factor", 1f, 0f, 2f);
  public final FloatProperty minProgressiveFactor = new FloatProperty("min-progressive-factor", 0.1f, 0f, 1f);
  // Attack reduce
  public final BooleanProperty attackHelper = new BooleanProperty("attack-helper", false);
  public final BooleanProperty customAttackReduce = new BooleanProperty("custom-attack-reduce", false);
  public final BooleanProperty checkRotation = new BooleanProperty("check-rotation", false);
  public final IntProperty allowHurtTimeMin = new IntProperty("allow-hurt-time-min", 1, 0, 10);
  public final IntProperty allowHurtTimeMax = new IntProperty("allow-hurt-time-max", 10, 0, 10);
  public final FloatProperty activeRange = new FloatProperty("active-range", 3f, 1f, 6f);
  public final BooleanProperty specialReduce = new BooleanProperty("special-reduce", false);
  public final IntProperty specialReduceHurtTime = new IntProperty("special-reduce-hurt-time", 7, 1, 10);
  public final FloatProperty specialReduceFactor = new FloatProperty("special-reduce-factor", 0.5f, 0f, 1f);
  public final BooleanProperty specialMultiReduce = new BooleanProperty("special-multi-reduce", false);
  public final BooleanProperty randomizeXZ = new BooleanProperty("randomize-xz", false);
  public final FloatProperty minXZReduce = new FloatProperty("min-xz-reduce", 0.1f, 0f, 1f);
  public final FloatProperty maxXZReduce = new FloatProperty("max-xz-reduce", 1f, 0f, 1f);
  public final BooleanProperty randomizeY = new BooleanProperty("randomize-y", false);
  public final FloatProperty minYReduce = new FloatProperty("min-y-reduce", 0.1f, 0f, 1f);
  public final FloatProperty maxYReduce = new FloatProperty("max-y-reduce", 1f, 0f, 1f);
  public final BooleanProperty multiReduce = new BooleanProperty("multi-reduce", false);
  public final IntProperty maxTriggerTimes = new IntProperty("max-trigger-times", 3, 1, 10);
  public final FloatProperty attackReduceFactor = new FloatProperty("attack-reduce-factor", 0.5f, 0f, 1f);
  public final FloatProperty attackReduceYFactor = new FloatProperty("attack-reduce-y-factor", 1f, 0f, 1f);
  public final BooleanProperty doubleReduceWhenFirstReduce = new BooleanProperty("double-reduce-when-first", false);
  public final FloatProperty doubleReduceFactor = new FloatProperty("double-reduce-factor", 0.5f, 0f, 1f);
  public final IntProperty enableWhy = new IntProperty("trigger-times", 1, 1, 5);
  public final BooleanProperty progressiveFactor = new BooleanProperty("progressive-factor", false);
  public final IntProperty attackReduceMinHurtTime = new IntProperty("attack-reduce-min-hurt-time", 1, 0, 10);
  public final IntProperty attackReduceMaxHurtTime = new IntProperty("attack-reduce-max-hurt-time", 10, 0, 10);
  public final BooleanProperty attackReduceOnlyWhenBackward =
      new BooleanProperty("attack-reduce-only-when-backward", false);
  // Timer
  public final BooleanProperty customTimer = new BooleanProperty("custom-timer", false);
  public final BooleanProperty customTimerOnlyWhenReceivedVelocity =
      new BooleanProperty("custom-timer-only-when-received", false);
  public final ModeProperty customTimerTimeMode =
      new ModeProperty("custom-timer-time-mode", 0, new String[] {"HurtTime", "MSTimer"});
  public final IntProperty customTimerLowMinHurtTime = new IntProperty("custom-timer-low-min-hurt-time", 2, 0, 10);
  public final IntProperty customTimerMinWorkHurtTime = new IntProperty("custom-timer-min-work-hurt-time", 7, 0, 10);
  public final FloatProperty customTimerLowTimer = new FloatProperty("custom-timer-low-timer", 0.8f, 0.1f, 2f);
  public final FloatProperty customTimerMaxTimer = new FloatProperty("custom-timer-max-timer", 1.1f, 0.1f, 2f);
  public final FloatProperty customTimerLowMSTimer = new FloatProperty("custom-timer-low-ms", 200f, 0f, 2000f);
  public final FloatProperty customTimerMinWorkMSTimer = new FloatProperty("custom-timer-min-work-ms", 500f, 0f, 2000f);
  public final BooleanProperty customTimerC03 = new BooleanProperty("custom-timer-c03", false);
  // Jump Reset
  public final BooleanProperty customJumpReset = new BooleanProperty("custom-jump-reset", false);
  public final IntProperty customChance = new IntProperty("custom-chance", 50, 0, 100);
  public final IntProperty jumpResetMinHurtTime = new IntProperty("jump-reset-min-hurt-time", 1, 0, 10);
  public final IntProperty jumpResetMaxHurtTime = new IntProperty("jump-reset-max-hurt-time", 10, 0, 10);
  public final BooleanProperty customJumpResetSafe = new BooleanProperty("custom-jump-reset-safe", false);
  public final BooleanProperty jumpResetOnlyOnSwing = new BooleanProperty("jump-reset-only-on-swing", false);
  public final ModeProperty afterJumpSprintControl =
      new ModeProperty("after-jump-sprint-control", 0, new String[] {"None", "Stop", "Sprint"});
  public final BooleanProperty debugger = new BooleanProperty("debugger", false);

  private boolean hasReceivedVelocity = false;
  private boolean hasReceivedVelocity2 = false;
  private boolean hasReceivedVelocity3 = false;
  private boolean doubleReduce = false;
  private boolean triggerTimesSpecial = false;
  private int triggerTimes = 0;
  private float progressiveXZFactor = 0f;
  private long timerChangeTime = 0;
  private long forwardTime = 0;
  private long strafeControlTime = 0;
  private boolean tryingForward = false;
  private boolean disablingStrafeInput = false;
  private int limitUntilJump = 0;

  public CustomVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    triggerTimes = 0;
    triggerTimesSpecial = false;
    progressiveXZFactor = attackReduceFactor.getValue();
    hasReceivedVelocity = false;
    hasReceivedVelocity2 = false;
    hasReceivedVelocity3 = false;
    doubleReduce = true;
    tryingForward = false;
    disablingStrafeInput = false;
    limitUntilJump = 0;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getType() == EventType.RECEIVE
        && event.getPacket() instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) {
      net.minecraft.network.play.server.S12PacketEntityVelocity packet =
          (net.minecraft.network.play.server.S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == player.getEntityId()) {
        triggerTimes = 0;
        progressiveXZFactor = attackReduceFactor.getValue();
        hasReceivedVelocity = true;
        hasReceivedVelocity2 = true;
        hasReceivedVelocity3 = true;
        doubleReduce = true;
        triggerTimesSpecial = false;
        timerChangeTime = System.currentTimeMillis();
      }
    }
    if (customTimerC03.getValue()
        && event.getPacket() instanceof C03PacketPlayer
        && !(event.getPacket() instanceof C03PacketPlayer.C04PacketPlayerPosition)
        && !(event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)
        && !(event.getPacket() instanceof C03PacketPlayer.C06PacketPlayerPosLook)) {
      event.setCancelled(true);
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (progressiveXZFactor > maxProgressiveFactor.getValue()) {
      progressiveXZFactor = maxProgressiveFactor.getValue();
    } else if (progressiveXZFactor < minProgressiveFactor.getValue()) {
      progressiveXZFactor = minProgressiveFactor.getValue();
    }

    if (hasReceivedVelocity2
        && player.hurtTime <= allowHurtTimeMax.getValue()
        && player.hurtTime >= allowHurtTimeMin.getValue()
        && attackHelper.getValue()
        && customAttackReduce.getValue()) {
      if (player.hurtTime == specialReduceHurtTime.getValue() && player.hurtTime != 0) {
        Entity target = findTarget();
        if (target != null && target instanceof EntityLivingBase) {
          player.swingItem();
          player.attackTargetEntityWithCurrentItem(target);
          if (debugger.getValue()) ChatUtil.display("AttackHelper | Attacked");
        }
      }
    }
    if (player.hurtTime == 0 && attackHelper.getValue()) {
      hasReceivedVelocity2 = false;
    }
    if (player.hurtTime == 0 && customTimer.getValue()) {
      hasReceivedVelocity3 = false;
    }

    if (customTimer.getValue()) {
      if (customTimerOnlyWhenReceivedVelocity.getValue()) {
        if (customTimerTimeMode.getValue() == 0) {
          if (player.hurtTime >= customTimerLowMinHurtTime.getValue() && hasReceivedVelocity3) {
            VelocityUtil.changeTimer(customTimerLowTimer.getValue());
          } else if (!player.onGround && player.hurtTime >= customTimerMinWorkHurtTime.getValue()) {
            VelocityUtil.changeTimer(customTimerMaxTimer.getValue());
          } else if (player.hurtTime == 0) {
            VelocityUtil.changeTimer(1f);
          }
        } else {
          if (hasReceivedVelocity3) {
            long elapsed = System.currentTimeMillis() - timerChangeTime;
            if (elapsed <= customTimerLowMSTimer.getValue() && player.hurtTime != 0) {
              VelocityUtil.changeTimer(customTimerLowTimer.getValue());
            } else if (elapsed >= customTimerLowMSTimer.getValue()
                && elapsed <= customTimerMinWorkMSTimer.getValue()
                && player.hurtTime != 0) {
              VelocityUtil.changeTimer(customTimerMaxTimer.getValue());
            } else if (player.hurtTime == 0) {
              VelocityUtil.changeTimer(1f);
            }
          }
        }
      } else {
        if (customTimerTimeMode.getValue() == 0) {
          if (player.hurtTime >= customTimerLowMinHurtTime.getValue()) {
            VelocityUtil.changeTimer(customTimerLowTimer.getValue());
          } else if (!player.onGround && player.hurtTime >= customTimerMinWorkHurtTime.getValue()) {
            VelocityUtil.changeTimer(customTimerMaxTimer.getValue());
          } else if (player.hurtTime == 0) {
            VelocityUtil.changeTimer(1f);
          }
        } else {
          long elapsed = System.currentTimeMillis() - timerChangeTime;
          if (elapsed <= customTimerLowMSTimer.getValue() && player.hurtTime != 0) {
            VelocityUtil.changeTimer(customTimerLowTimer.getValue());
          } else if (elapsed >= customTimerLowMSTimer.getValue()
              && elapsed <= customTimerMinWorkMSTimer.getValue()
              && player.hurtTime != 0) {
            VelocityUtil.changeTimer(customTimerMaxTimer.getValue());
          } else if (player.hurtTime == 0) {
            VelocityUtil.changeTimer(1f);
          }
        }
      }

      long elapsedForward = System.currentTimeMillis() - forwardTime;
      if (tryForward.getValue() && tryingForward && player.hurtTime != 0) {
        if (elapsedForward <= tryingForwardTime.getValue()) {
          ((net.minecraft.client.entity.EntityPlayerSP) player).movementInput.moveForward = 1.0f;
          if (SprintControl.getValue() == 0) VelocityUtil.changeSprint(true);
          else if (SprintControl.getValue() == 1) VelocityUtil.changeSprint(false);        }
      } else if (player.hurtTime == 0 || elapsedForward >= tryingForwardTime.getValue()) {
        tryingForward = false;
      }

      long elapsedStrafe = System.currentTimeMillis() - strafeControlTime;
      if (disableStrafeInput.getValue() && disablingStrafeInput && player.hurtTime != 0) {
        if (elapsedStrafe <= disableStrafeInputTime.getValue()) {
          ((net.minecraft.client.entity.EntityPlayerSP) player).movementInput.moveStrafe = 0.0f;
        }
      } else if (player.hurtTime == 0 || elapsedStrafe >= disableStrafeInputTime.getValue()) {
        disablingStrafeInput = false;
      }
    }
  }

  @Override
  public void onAttack(AttackEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (attackReduceOnlyWhenBackward.getValue()
        && customAttackReduce.getValue()
        && !VelocityUtil.isMovingBackwards()) {
      return;
    }
    if (player.hurtTime == specialReduceHurtTime.getValue()
        && specialReduce.getValue()
        && customAttackReduce.getValue()
        && !triggerTimesSpecial) {
      float finalXZ = specialReduceFactor.getValue();
      if (randomizeXZ.getValue()) {
        finalXZ *= VelocityUtil.randomFloat(minXZReduce.getValue(), maxXZReduce.getValue());
      }
      VelocityUtil.reduceXZ(finalXZ);
      triggerTimesSpecial = !specialMultiReduce.getValue();
      if (debugger.getValue()) ChatUtil.display("[SpecialReduce] XZ=" + finalXZ);
      if (disableStrafeInput.getValue()) {
        disablingStrafeInput = true;
        strafeControlTime = System.currentTimeMillis();
      }
      if (tryForward.getValue()) {
        tryingForward = true;
        forwardTime = System.currentTimeMillis();
      }
    }

    if (player.hurtTime <= attackReduceMaxHurtTime.getValue()
        && player.hurtTime >= attackReduceMinHurtTime.getValue()
        && hasReceivedVelocity
        && customAttackReduce.getValue()) {
      if (multiReduce.getValue()
          && triggerTimes < maxTriggerTimes.getValue()
          && !progressiveFactor.getValue()) {
        float finalXZ = attackReduceFactor.getValue();
        float finalY = attackReduceYFactor.getValue();
        if (randomizeXZ.getValue()) {
          finalXZ *= VelocityUtil.randomFloat(minXZReduce.getValue(), maxXZReduce.getValue());
        }
        if (randomizeY.getValue()) {
          finalY *= VelocityUtil.randomFloat(minYReduce.getValue(), maxYReduce.getValue());
        }
        VelocityUtil.reduceXZ(finalXZ);
        VelocityUtil.reduceY(finalY);
        triggerTimes++;
        if (doubleReduceWhenFirstReduce.getValue() && doubleReduce) {
          VelocityUtil.reduceXZ(finalXZ);
          doubleReduce = false;
        }
        if (debugger.getValue()) ChatUtil.display("[AttackReduce] XZ=" + finalXZ + " Trigs=" + triggerTimes + "/" + maxTriggerTimes.getValue());
        if (disableStrafeInput.getValue()) {
          disablingStrafeInput = true;
          strafeControlTime = System.currentTimeMillis();
        }
        if (tryForward.getValue()) {
          tryingForward = true;
          forwardTime = System.currentTimeMillis();
        }
        if (player.hurtTime < attackReduceMinHurtTime.getValue()) {
          hasReceivedVelocity = false;
          triggerTimes = 0;
        }
      } else if (!multiReduce.getValue()) {
        float finalXZ = attackReduceFactor.getValue();
        float finalY = attackReduceYFactor.getValue();
        if (randomizeXZ.getValue()) {
          finalXZ *= VelocityUtil.randomFloat(minXZReduce.getValue(), maxXZReduce.getValue());
        }
        if (randomizeY.getValue()) {
          finalY *= VelocityUtil.randomFloat(minYReduce.getValue(), maxYReduce.getValue());
        }
        VelocityUtil.reduceXZ(finalXZ);
        VelocityUtil.reduceY(finalY);
        if (doubleReduceWhenFirstReduce.getValue() && doubleReduce) {
          VelocityUtil.reduceXZ(finalXZ);
          doubleReduce = false;
        }
        if (debugger.getValue()) ChatUtil.display("[AttackReduce] XZ=" + finalXZ + " Y=" + finalY);
        if (disableStrafeInput.getValue()) {
          disablingStrafeInput = true;
          strafeControlTime = System.currentTimeMillis();
        }
        if (tryForward.getValue()) {
          tryingForward = true;
          forwardTime = System.currentTimeMillis();
        }
        hasReceivedVelocity = false;
      } else if (progressiveFactor.getValue()
          && multiReduce.getValue()
          && triggerTimes < maxTriggerTimes.getValue()) {
        if (triggerTimes == 0) {
          progressiveXZFactor = attackReduceFactor.getValue();
        }
        float finalProgressive = progressiveXZFactor;
        float finalY = attackReduceYFactor.getValue();
        if (randomizeXZ.getValue()) {
          finalProgressive *= VelocityUtil.randomFloat(minXZReduce.getValue(), maxXZReduce.getValue());
        }
        if (randomizeY.getValue()) {
          finalY *= VelocityUtil.randomFloat(minYReduce.getValue(), maxYReduce.getValue());
        }
        VelocityUtil.reduceXZ(finalProgressive);
        VelocityUtil.reduceY(finalY);
        triggerTimes++;
        if (doubleReduceWhenFirstReduce.getValue() && doubleReduce) {
          VelocityUtil.reduceXZ(finalProgressive);
          doubleReduce = false;
        }
        if (debugger.getValue()) ChatUtil.display("[ProgressiveReduce] XZ=" + finalProgressive + " Trigs=" + triggerTimes + "/" + maxTriggerTimes.getValue());
        progressiveXZFactor =
            progressivemode.getValue() == 0
                ? progressiveXZFactor - progressivestepfactor.getValue()
                : progressiveXZFactor + progressivestepfactor.getValue();
        progressiveXZFactor = Math.max(
            minProgressiveFactor.getValue(),
            Math.min(maxProgressiveFactor.getValue(), progressiveXZFactor));
        if (disableStrafeInput.getValue()) {
          disablingStrafeInput = true;
          strafeControlTime = System.currentTimeMillis();
        }
        if (tryForward.getValue()) {
          tryingForward = true;
          forwardTime = System.currentTimeMillis();
        }
      }
    }
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (customJumpReset.getValue() && hasReceivedVelocity) {
      boolean ready =
          VelocityUtil.randomInt(0, 100) < customChance.getValue()
              && player.onGround
              && player.hurtTime <= jumpResetMaxHurtTime.getValue()
              && player.hurtTime >= jumpResetMinHurtTime.getValue();
      if (ready) {
        if (customJumpResetSafe.getValue() && VelocityUtil.isInBadEnvironment()) return;
        if (jumpResetOnlyOnSwing.getValue() && !player.isSwingInProgress) return;
        VelocityUtil.tryJump();
        limitUntilJump = 0;
        if (afterJumpSprintControl.getValue() == 1) VelocityUtil.changeSprint(false);
        else if (afterJumpSprintControl.getValue() == 2) VelocityUtil.changeSprint(true);
        if (debugger.getValue()) ChatUtil.display("[JumpReset] Jumped | HurtTime=" + player.hurtTime);
      }
    }
  }

  private Entity findTarget() {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return null;
    Entity entity = Velocity.mc.objectMouseOver != null
        ? Velocity.mc.objectMouseOver.entityHit
        : null;
    if (entity == null && !checkRotation.getValue()) {
      return VelocityUtil.getNearestEntityInRange(activeRange.getValue());
    }
    return entity;
  }

  @Override
  public void onDisable() {
    triggerTimes = 0;
    triggerTimesSpecial = false;
    hasReceivedVelocity = false;
    hasReceivedVelocity2 = false;
    hasReceivedVelocity3 = false;
    doubleReduce = true;
    tryingForward = false;
    disablingStrafeInput = false;
    limitUntilJump = 0;
    VelocityUtil.changeTimer(1f);
  }
}