package miau.module.modules.combat.velocity;

import miau.event.impl.AttackEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class Intave14Velocity extends VelocityMode {
  public final BooleanProperty onlyWhenBackward = new BooleanProperty("only-when-backward", false);
  public final BooleanProperty finalReverse = new BooleanProperty("final-reverse", false);
  public final FloatProperty finalReverseFactor = new FloatProperty("final-reverse-factor", 0.9f, 0f, 2f);
  public final BooleanProperty finalReverseStrict = new BooleanProperty("final-reverse-strict", false);
  public final BooleanProperty yReduceTest = new BooleanProperty("y-reduce-test", false);
  public final FloatProperty yReduceCount = new FloatProperty("y-reduce-count", 0.2f, 0f, 1f);
  public final IntProperty yReduceMaxTimes = new IntProperty("y-reduce-max-times", 1, 1, 20);
  public final IntProperty firstReduce = new IntProperty("first-reduce", 8, 1, 10);
  public final IntProperty secondReduce = new IntProperty("second-reduce", 7, 1, 10);
  public final IntProperty thirdReduce = new IntProperty("third-reduce", 6, 1, 10);
  public final BooleanProperty applyDiffFactorOnGroundOrInAir =
      new BooleanProperty("apply-diff-factor", false);
  public final IntProperty triggerTimes = new IntProperty("trigger-times", 2, 1, 3);
  public final BooleanProperty intaveMoreReduce = new BooleanProperty("more-reduce", false);
  public final FloatProperty intaveMoreReduceFactor = new FloatProperty("more-reduce-factor", 0.4f, 0f, 1f);
  public final FloatProperty intaveMoreReduceAnotherFactor =
      new FloatProperty("more-reduce-another-factor", 0.8f, 0f, 1f);
  public final IntProperty intaveMoreReduceMaxTimes = new IntProperty("more-reduce-max-times", 1, 1, 20);
  public final BooleanProperty intaveMoreReduceExtraReduce =
      new BooleanProperty("more-reduce-extra-reduce", false);
  public final BooleanProperty intaveTimerTest = new BooleanProperty("timer-test", false);
  public final BooleanProperty intave14Debugger = new BooleanProperty("debug", false);

  private boolean hasReceivedVelocity = false;
  private boolean onGroundTri = false;
  private boolean notTriggered1 = true;
  private boolean notTriggered2 = true;
  private boolean notTriggered3 = true;
  private boolean notTriggeredA = true;
  private boolean finalReverseTriggered = false;
  private int yReduceTriggeredTimes = 0;
  private int finalReverseCondition = 0;
  private int intaveMoreReduceTimes = 0;
  private String reduceCondition = "";

  public Intave14Velocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == player.getEntityId()) {
        finalReverseTriggered = false;
        hasReceivedVelocity = true;
        notTriggered1 = true;
        notTriggered2 = true;
        notTriggered3 = true;
        notTriggeredA = true;
        yReduceTriggeredTimes = 0;
        finalReverseCondition = 0;
        intaveMoreReduceTimes = 0;
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime >= 9 && hasReceivedVelocity) {
      onGroundTri = player.onGround;
    }
    if (player.hurtTime == 0 && hasReceivedVelocity) {
      hasReceivedVelocity = false;
    }
    int finalReverseHurtTime =
        triggerTimes.getValue() == 3
            ? thirdReduce.getValue() - 1
            : triggerTimes.getValue() == 2 ? secondReduce.getValue() - 1 : firstReduce.getValue() - 1;
    if (finalReverse.getValue()
        && finalReverseHurtTime == player.hurtTime
        && player.hurtTime != 0) {
      if (!VelocityUtil.isMovingBackwards()) return;
      if (!hasReceivedVelocity) return;
      if (finalReverseStrict.getValue() && triggerTimes.getValue() == 2) {
        if (finalReverseCondition < 2) return;
      } else if (finalReverseStrict.getValue() && triggerTimes.getValue() == 3) {
        if (finalReverseCondition < 3) return;
      }
      VelocityUtil.reduceXZ(-finalReverseFactor.getValue());
      if (intave14Debugger.getValue()) {
        ChatUtil.display("FinalReversed [" + finalReverseCondition + "/" + triggerTimes.getValue() + "]");
      }
      finalReverseTriggered = true;
    }
    if (intaveTimerTest.getValue()) {
      if (player.hurtTime >= 8) {
        VelocityUtil.changeTimer(0.3f);
      } else if (player.hurtTime > 2) {
        VelocityUtil.changeTimer(5.0f);
      } else if (player.hurtTime == 2) {
        VelocityUtil.changeTimer(1.0f);
      } else if (player.hurtTime == 0) {
        VelocityUtil.changeTimer(1.0f);
      }
    }
  }

  @Override
  public void onAttack(AttackEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (onlyWhenBackward.getValue()) {
      if (!VelocityUtil.isMovingBackwards()) return;
    }
    if (!hasReceivedVelocity) return;
    int finalReverseHurtTime =
        triggerTimes.getValue() == 3
            ? thirdReduce.getValue() - 1
            : triggerTimes.getValue() == 2 ? secondReduce.getValue() - 1 : firstReduce.getValue() - 1;

    if (player.hurtTime == firstReduce.getValue()
        && triggerTimes.getValue() >= 1
        && notTriggered1) {
      VelocityUtil.reduceXZ(0.6);
      reduceCondition = onGroundTri ? "OnGround" : "InAir";
      yReduce();
      notTriggered1 = false;
      finalReverseCondition++;
      notTriggeredA = false;
      if (intave14Debugger.getValue()) {
        ChatUtil.display("Reduce | Phase1 | " + reduceCondition + " | 60%");
      }
      return;
    }
    if (player.hurtTime == secondReduce.getValue()
        && triggerTimes.getValue() >= 2
        && notTriggered2) {
      if (notTriggeredA) {
        VelocityUtil.reduceXZ(0.6);
        notTriggeredA = false;
      } else {
        VelocityUtil.reduceXZ(0.35);
      }
      yReduce();
      reduceCondition = onGroundTri ? "OnGround" : "InAir";
      notTriggered2 = false;
      finalReverseCondition++;
      if (intave14Debugger.getValue()) {
        ChatUtil.display("Reduce | Phase2 | " + reduceCondition + " | "
            + (notTriggeredA ? "60%" : "35%"));
      }
      return;
    }
    if (player.hurtTime == thirdReduce.getValue()
        && triggerTimes.getValue() >= 3
        && notTriggered3) {
      if (notTriggeredA) {
        VelocityUtil.reduceXZ(0.6);
        notTriggeredA = false;
      } else {
        double factor = applyDiffFactorOnGroundOrInAir.getValue() && !onGroundTri ? 0.5 : 0.15;
        VelocityUtil.reduceXZ(factor);
      }
      yReduce();
      reduceCondition = onGroundTri ? "OnGround" : "InAir";
      notTriggered3 = false;
      finalReverseCondition++;
      if (intave14Debugger.getValue()) {
        ChatUtil.display("Reduce | Phase3 | " + reduceCondition
            + " | " + (notTriggeredA ? "60%" : applyDiffFactorOnGroundOrInAir.getValue() ? "50%" : "15%"));
      }
      return;
    }
    if (player.hurtTime == finalReverseHurtTime) {
      if (onlyWhenBackward.getValue()) {
        if (!VelocityUtil.isMovingBackwards()) return;
      }
      if (!finalReverse.getValue()) return;
      if (finalReverseStrict.getValue() && triggerTimes.getValue() == 2) {
        if (finalReverseCondition < 2) return;
      } else if (finalReverseStrict.getValue() && triggerTimes.getValue() == 3) {
        if (finalReverseCondition < 3) return;
      }
      if (player.hurtTime == 0) return;
      VelocityUtil.reduceXZ(-finalReverseFactor.getValue());
      if (intave14Debugger.getValue()) {
        ChatUtil.display("FinalReversed [" + finalReverseCondition + "/" + triggerTimes.getValue() + "]");
      }
      finalReverseTriggered = true;
      return;
    }
    if (intaveMoreReduce.getValue()) {
      int moreReduceHurtTime =
          triggerTimes.getValue() == 3
              ? thirdReduce.getValue() - 1
              : triggerTimes.getValue() == 2 ? secondReduce.getValue() - 1 : firstReduce.getValue() - 1;
      if (player.hurtTime <= moreReduceHurtTime
          && player.hurtTime > 0
          && intaveMoreReduceTimes < intaveMoreReduceMaxTimes.getValue()
          && !finalReverseTriggered) {
        double factor =
            intaveMoreReduceExtraReduce.getValue()
                    && notTriggered1
                    && notTriggered2
                    && notTriggered3
                ? intaveMoreReduceAnotherFactor.getValue()
                : intaveMoreReduceFactor.getValue();
        VelocityUtil.reduceXZ(factor);
        intaveMoreReduceTimes++;
        if (intave14Debugger.getValue()) {
          ChatUtil.display("IntaveMoreReduce");
        }
      }
    }
  }

  @Override
  public void onStrafe(StrafeEvent event) {}

  private void yReduce() {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player != null && yReduceTest.getValue() && yReduceTriggeredTimes < yReduceMaxTimes.getValue()) {
      player.motionY -= yReduceCount.getValue();
      yReduceTriggeredTimes++;
      if (intave14Debugger.getValue()) {
        ChatUtil.display("YReduced");
      }
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
    onGroundTri = false;
    notTriggered1 = true;
    notTriggered2 = true;
    notTriggered3 = true;
    notTriggeredA = true;
    finalReverseTriggered = false;
    yReduceTriggeredTimes = 0;
    finalReverseCondition = 0;
    intaveMoreReduceTimes = 0;
    VelocityUtil.changeTimer(1.0f);
  }
}