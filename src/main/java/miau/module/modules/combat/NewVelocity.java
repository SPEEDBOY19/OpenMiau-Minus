package miau.module.modules.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.JumpEvent;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.mixin.IAccessorS12PacketEntityVelocity;
import miau.mixin.IAccessorS27PacketExplosion;
import miau.module.Module;
import miau.module.modules.movement.KeepSprint;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.TextProperty;
import miau.util.math.RandomUtil;
import miau.util.misc.CPSCounter;
import miau.util.misc.SomeUtil;
import miau.util.network.BlinkUtil;
import miau.util.network.PacketUtil;
import miau.util.misc.BackTrackUtil;
import miau.util.player.ItemUtil;
import miau.util.player.RayCastUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.WorldSettings;
import net.minecraft.block.BlockSoulSand;

public class NewVelocity extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final String[] TAG_MODES = {"Normal", "Custom", "None"};
  private static final String[] MODES = {
    "AttackReduce",
    "Intave",
    "Intave2",
    "IntaveSafe",
    "OldIntave",
    "Matrix",
    "PolarJump",
    "Delay",
    "LegitClick",
    "LegitClick2",
    "OldGrim",
    "JumpReset",
    "AirJumpReset",
    "FakeJump",
    "MineBerryNew",
    "MineMenClub",
    "NoC0F",
    "GrimExempt117",
    "Prediction",
    "Tatako0.9.6.1To0.9.7.3-a2",
    "XZSwitch"
  };
  private static final String[] WORK_MODES = {"OnGround+InAir", "OnGround", "InAir", "OnlySprinting"};
  private static final String[] COOLDOWN_MODES = {"ReceivedHit", "Tick", "Both"};
  private static final String[] SWING_MODES = {"Off", "Normal", "Packet"};

  public static boolean canCancelHitSlow = false;

  public final ModeProperty tagMode = new ModeProperty("TagMode", 0, TAG_MODES);
  public final TextProperty customText =
      new TextProperty("CustomText", "", () -> tagMode.getModeString().equals("Custom"));
  public final ModeProperty mode = new ModeProperty("Mode", 11, MODES);

  private final IntProperty blinkTicks =
      new IntProperty("BlinkTicks", 5, 1, 10, () -> mode.getModeString().equals("Prediction"));
  private boolean preBlinking = false;
  private boolean preShouldBlink = false;
  private boolean preShouldAttack = false;

  private final IntProperty mineBerryMinWorkHurtTime =
      new IntProperty("MinWorkHurtTime", 1, 1, 10, () -> mode.getModeString().equals("MineBerryNew"));
  private boolean mineBerryFirstReduce = false;

  private final FloatProperty grimrange =
      new FloatProperty("OldGrimWorkRange", 3.5f, 0f, 6f, () -> mode.getModeString().equals("OldGrim"));
  private final IntProperty attackCountValue =
      new IntProperty("Attack Counts", 12, 1, 16, () -> mode.getModeString().equals("OldGrim"));
  private final BooleanProperty fireCheckValue =
      new BooleanProperty("FireCheck", false, () -> mode.getModeString().equals("OldGrim"));
  private final BooleanProperty waterCheckValue =
      new BooleanProperty("WaterCheck", false, () -> mode.getModeString().equals("OldGrim"));
  private final BooleanProperty fallCheckValue =
      new BooleanProperty("FallCheck", false, () -> mode.getModeString().equals("OldGrim"));
  private final BooleanProperty consumecheck =
      new BooleanProperty("ConsumableCheck", false, () -> mode.getModeString().equals("OldGrim"));
  private final BooleanProperty raycastValue =
      new BooleanProperty("Ray cast", false, () -> mode.getModeString().equals("OldGrim"));
  private Entity entity = null;
  private int velX = 0;
  private int velY = 0;
  private int velZ = 0;

  private final FloatProperty blinkWorkMaxDistance =
      new FloatProperty("BlinkWorkMaxDistance", 3.5f, 0.0f, 6.0f, () -> mode.getModeString().equals("Intave"));
  private final IntProperty maxBlinkTicks =
      new IntProperty("MaxBlinkTicks", 10, 0, 10, () -> mode.getModeString().equals("Intave"));
  private final BooleanProperty intaveJumpReset =
      new BooleanProperty("IntaveJumpReset", true, () -> mode.getModeString().equals("Intave"));
  private final BooleanProperty intaveJumpResetSprint =
      new BooleanProperty(
          "ForceSprintJump", false, () -> mode.getModeString().equals("Intave") && intaveJumpReset.getValue());
  private final BooleanProperty intaveJumpResetNeedForward =
      new BooleanProperty(
          "ForceSprintJumpNeedForward",
          true,
          () ->
              mode.getModeString().equals("Intave")
                  && intaveJumpReset.getValue()
                  && intaveJumpResetSprint.getValue());
  private final BooleanProperty extraC0APerReduce =
      new BooleanProperty("ExtraC0APerReduce", false, () -> mode.getModeString().equals("Intave"));
  private final IntProperty extraPacketCount =
      new IntProperty(
          "ExtraC0APacketCount",
          1,
          1,
          5,
          () -> mode.getModeString().equals("Intave") && extraC0APerReduce.getValue());
  private final BooleanProperty moreReduce =
      new BooleanProperty("MoreReduce", false, () -> mode.getModeString().equals("Intave"));
  private final IntProperty maxMoreReduce =
      new IntProperty(
          "MaxMoreReduceCount", 3, 1, 6, () -> moreReduce.getValue() && mode.getModeString().equals("Intave"));
  private final BooleanProperty onlyWhenNeed =
      new BooleanProperty("OnlyWhenNeed", true, () -> mode.getModeString().equals("Intave"));
  private final BooleanProperty intaveSafe =
      new BooleanProperty("IntaveSafe", true, () -> mode.getModeString().equals("Intave"));
  private boolean hasReceivedVelocity = false;

  private enum IntavePhase {
    PHASE_1,
    PHASE_2,
    PHASE_3,
    PHASE_4,
    PHASE_5,
    PHASE_6
  }

  private final java.util.Set<IntavePhase> triggeredPhases = new java.util.HashSet<>();
  private int previousTimerState = 0;
  private boolean intaveReversed = false;
  private int timerState = 0;
  private boolean boosting = true;
  private boolean slowing = false;
  private int intaveClickTimes = 0;
  public int moreReduceTimes = 0;
  private boolean canOutPutMessage = false;
  private boolean shouldBlink = false;
  private boolean lastBlinkState = false;
  public int intaveReduceTimes = 0;

  private final FloatProperty minFactor =
      new FloatProperty("MinFactor", 0.4f, 0f, 1f, () -> mode.getModeString().equals("Intave2"));
  private int intave2ReduceCounter = 0;

  private final BooleanProperty matrixBoost =
      new BooleanProperty("BoostAfterReduce", false, () -> mode.getModeString().equals("Matrix"));
  private final FloatProperty matrixBoostFactor =
      new FloatProperty(
          "BoostFactor",
          0.33f,
          0.0f,
          5.0f,
          () -> mode.getModeString().equals("Matrix") && matrixBoost.getValue());
  private final IntProperty matrixBoostDelay =
      new IntProperty(
          "BoostCooldown",
          0,
          0,
          2000,
          () -> mode.getModeString().equals("Matrix") && matrixBoost.getValue());
  private final TimerUtil matrixBoostTimer = new TimerUtil();
  private boolean matrixMotionYReduce = false;

  private final IntProperty clicks =
      new IntProperty("Clicks", 1, 1, 20, () -> mode.getModeString().equals("LegitClick"));
  private final IntProperty durationHurtTime =
      new IntProperty("DurationHurtTimes", 1, 1, 9, () -> mode.getModeString().equals("LegitClick"));
  private final IntProperty clickDelayTicks =
      new IntProperty("ClickCooldownTicks", 0, 0, 10, () -> mode.getModeString().equals("LegitClick"));
  private final FloatProperty clickChancePerClick =
      new FloatProperty(
          "ClickChancePerClick", 1.0f, 0.0f, 1.0f, () -> mode.getModeString().equals("LegitClick"));
  private final BooleanProperty whenFacingEnemyOnly =
      new BooleanProperty("WhenFacingEnemyOnly", true, () -> mode.getModeString().equals("LegitClick"));
  private final BooleanProperty ignoreBlocking =
      new BooleanProperty("IgnoreBlocking", false, () -> mode.getModeString().equals("LegitClick"));
  private final FloatProperty clickRange =
      new FloatProperty("ClickRange", 3f, 1f, 6f, () -> mode.getModeString().equals("LegitClick"));
  private final ModeProperty swingMode =
      new ModeProperty(
          "SwingMode",
          1,
          SWING_MODES,
          () -> mode.getModeString().equals("LegitClick"));
  private final BooleanProperty modifyMotionWhenClick =
      new BooleanProperty(
          "ModifyMotionWhenClick", false, () -> mode.getModeString().equals("LegitClick"));
  private final BooleanProperty makeVanillaAttackNotStopSprint =
      new BooleanProperty(
          "MakeVanillaAttackNotStopSprint",
          false,
          () -> modifyMotionWhenClick.getValue() && mode.getModeString().equals("LegitClick"));
  private final FloatProperty modifyMotionFactor =
      new FloatProperty(
          "XZFactor",
          0.6f,
          -1.0f,
          1.0f,
          () -> modifyMotionWhenClick.getValue() && mode.getModeString().equals("LegitClick"));
  private int attackStartHurtTime = 0;
  private int clickDelayTick = 0;

  private final IntProperty click2MaxTimes =
      new IntProperty(
          "LegitClick2MaxClickTimes", 3, 1, 20, () -> mode.getModeString().equals("LegitClick2"));
  private final IntProperty addClicksPerUserClick =
      new IntProperty(
          "AddClicksPerUserClick", 1, 1, 20, () -> mode.getModeString().equals("LegitClick2"));
  private int legitClick2Times = 0;

  private final IntProperty mineMenClubDelay =
      new IntProperty("PacketCancelDelay", 20, 0, 20, () -> mode.getModeString().equals("MineMenClub"));
  private int minemenClubCounter = 0;

  private final IntProperty delayTicks =
      new IntProperty("DelayTicks", 3, 1, 20, () -> mode.getModeString().equals("Delay"));
  private final IntProperty delayChance =
      new IntProperty("DelayChance", 100, 0, 100, () -> mode.getModeString().equals("Delay"));
  private final FloatProperty delayHorizontal =
      new FloatProperty("DelayHorizontal", 0F, -1F, 1F, () -> mode.getModeString().equals("Delay"));
  private final FloatProperty delayVertical =
      new FloatProperty("DelayVertical", 0F, -1F, 1F, () -> mode.getModeString().equals("Delay"));
  private final BooleanProperty delayAttackReduce =
      new BooleanProperty("DelayAttackReduce", false, () -> mode.getModeString().equals("Delay"));
  private final BooleanProperty delayFakeCheck =
      new BooleanProperty("DelayFakeCheck", true, () -> mode.getModeString().equals("Delay"));
  private int delayChanceCounter = 0;
  private boolean delayActive = false;
  private boolean delayReverseFlag = false;
  private boolean delayPendingExplosion = false;
  private boolean delayAllowNext = true;
  private final Map<Packet<?>, Long> delayedPackets = new LinkedHashMap<>();
  private final TimerUtil delayTimer = new TimerUtil();
  private int delayTickCounter = 0;

  private final FloatProperty attackReduceFactor =
      new FloatProperty(
          "AttackXZFactor", 0.6f, 0.0f, 1.0f, () -> mode.getModeString().equals("AttackReduce"));
  private final IntProperty attackHurtTime =
      new IntProperty(
          "AttackHurtTime", 9, 1, 10, () -> mode.getModeString().equals("AttackReduce"));

  private final BooleanProperty jumpReset =
      new BooleanProperty(
          "JumpReset",
          false,
          () ->
              !mode.getModeString().equals("Intave")
                  && !mode.getModeString().equals("JumpReset")
                  && !mode.getModeString().equals("AirJumpReset")
                  && !mode.getModeString().equals("PolarJump")
                  && !mode.getModeString().equals("IntaveSafe"));

  private final IntProperty jumpResetChance =
      new IntProperty("JumpChance", 100, 0, 100, this::displayJumpResetChoices);
  private final ModeProperty jumpCooldownMode =
      new ModeProperty("JumpCooldownMode", 0, COOLDOWN_MODES, this::displayJumpResetChoices);
  private final IntProperty jumpCooldownTick =
      new IntProperty(
          "JumpCooldownTicks",
          4,
          0,
          20,
          () -> displayJumpResetChoices() && (jumpCooldownMode.getValue() == 1 || jumpCooldownMode.getValue() == 2));
  private final IntProperty jumpCooldownReceivedHit =
      new IntProperty(
          "JumpCooldownReceivedHit",
          1,
          0,
          5,
          () -> displayJumpResetChoices() && (jumpCooldownMode.getValue() == 0 || jumpCooldownMode.getValue() == 2));
  private final BooleanProperty checkUserSprint =
      new BooleanProperty("CheckUserIsSprinting", true, this::displayJumpResetChoices);
  private final BooleanProperty matrixJumpTest =
      new BooleanProperty("MatrixJumpReset", false, this::displayJumpResetChoices);
  private int jumpCooldownTickCounter = 0;
  private int jumpCooldownReceivedHitCounter = 0;

  private int polarHurtTime = RandomUtil.nextInt(7, 9);

  private final BooleanProperty pauseOnExplosion = new BooleanProperty("PauseOnExplosion", false);
  private final IntProperty pauseTicksProp =
      new IntProperty("PauseTicks", 20, 0, 100, () -> pauseOnExplosion.getValue());
  private int pausedTicks = 0;

  private final ModeProperty allowWorkWhen = new ModeProperty("AllowWorkWhen", 0, WORK_MODES);

  public final BooleanProperty debugMessage =
      new BooleanProperty(
          "DebugMessage",
          false,
          () -> {
            String m = mode.getModeString();
            return m.equals("Intave")
                || m.equals("IntaveSafe")
                || m.equals("Intave2")
                || m.equals("LegitClick")
                || m.equals("JumpReset")
                || m.equals("AirJumpReset")
                || m.equals("OldGrim")
                || m.equals("Delay")
                || m.equals("MineBerryNew")
                || m.equals("FakeJump");
          });
  private final BooleanProperty smartJumpReset = new BooleanProperty("SmartJumpReset", false);
  private boolean shouldCancelAttack = false;
  private int shouldAttackCount = 0;

  private boolean hasJumpReset = false;

  public double packetMotionX = 0.0;
  private double packetMotionY = 0.0;
  public double packetMotionZ = 0.0;

  private EntityLivingBase globalTarget = null;

  private final TimerUtil sprintTimer = new TimerUtil();
  private boolean serverSprintState = false;

  public NewVelocity() {
    super("NewVelocity", false);
  }

  private boolean modeIs(String... names) {
    String current = mode.getModeString();
    for (String name : names) {
      if (current.equals(name)) return true;
    }
    return false;
  }

  private boolean doNotNeedReduce() {
    return mc.thePlayer == null
        || mc.thePlayer.hurtTime == 0
        || knockBackIsNegated(packetMotionX, packetMotionZ);
  }

  @Override
  public String[] getSuffix() {
    String tagModeString = tagMode.getModeString();
    if (tagModeString.equals("None")) return new String[0];
    if (tagModeString.equals("Custom")) return new String[] {customText.getValue()};
    return new String[] {mode.getModeString()};
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (event.getTarget() instanceof EntityLivingBase) {
      globalTarget = (EntityLivingBase) event.getTarget();
    } else {
      globalTarget = null;
    }
    if (!canWorkNow()) return;
    String currentMode = mode.getModeString();
    if (currentMode.equals("Intave")) {
      switch (mc.thePlayer.hurtTime) {
        case 10:
          intaveReduce(0, intaveSafe.getValue());
          break;
        case 9:
        case 6:
        case 3:
          intaveReduce(1, intaveSafe.getValue());
          break;
        case 8:
        case 5:
        case 2:
          intaveReduce(2, intaveSafe.getValue());
          break;
        case 7:
        case 4:
        case 1:
          intaveReduce(3, intaveSafe.getValue());
          break;
        default:
          break;
      }
    } else if (currentMode.equals("MineBerryNew")) {
      if (mc.thePlayer.hurtTime >= mineBerryMinWorkHurtTime.getValue()) {
        if (CPSCounter.getCPS(CPSCounter.MouseButton.LEFT) > 22) return;
        int attackCount = mineBerryFirstReduce ? 3 : 1;
        PacketUtil.sendPacket(new C0APacketAnimation());
        SomeUtil.runAttack(
            false, 3.0f, attackCount, null, true, "Packet", false, debugMessage.getValue(), "Attacked", true, null, null, 1.0f);
        if (mineBerryFirstReduce) mineBerryFirstReduce = false;
        PacketUtil.sendPacket(new C0APacketAnimation());
      }
    } else if (currentMode.equals("AttackReduce")) {
      if (mc.thePlayer.hurtTime == attackHurtTime.getValue()) {
        SomeUtil.reduceXZ(attackReduceFactor.getValue().doubleValue());
      }
    } else if (currentMode.equals("Intave2")) {
      double reduceFactor =
          Math.max(1 - (intave2ReduceCounter * 0.1), minFactor.getValue().doubleValue());
      SomeUtil.reduceXZ(reduceFactor);
      debugMessage("Intave2Reduce");
      intave2ReduceCounter++;
    } else if (currentMode.equals("OldIntave")) {
      if (mc.thePlayer.hurtTime >= 2 && mc.thePlayer.hurtTime <= 10) SomeUtil.reduceXZ(0.75);
      if (mc.thePlayer.hurtTime >= 1 && mc.thePlayer.hurtTime <= 4) {
        if (mc.thePlayer.motionY > 0) SomeUtil.reduceY(0.9);
        else SomeUtil.reduceY(1.1);
      }
    } else if (currentMode.equals("IntaveSafe")) {
      switch (mc.thePlayer.hurtTime) {
        case 9:
          SomeUtil.reduceXZ(0.6);
          debugMessage("IntaveSafeReduce");
          break;
        case 8:
          SomeUtil.reduceXZ(0.8);
          debugMessage("IntaveSafeReduce");
          break;
        default:
          break;
      }
    } else if (currentMode.equals("LegitClick2")) {
      extraJumpReset();
      if (mc.thePlayer.hurtTime > 0) {
        if (legitClick2Times < click2MaxTimes.getValue()) {
          PacketUtil.sendPacketNoEvent(new C02PacketUseEntity(mc.pointedEntity, C02PacketUseEntity.Action.ATTACK));
          mc.thePlayer.swingItem();
          legitClick2Times = legitClick2Times + addClicksPerUserClick.getValue();
        }
      }
    } else if (currentMode.equals("OldGrim")) {
      mc.getNetHandler().getNetworkManager().sendPacket(new C0APacketAnimation());
      mc.getNetHandler().getNetworkManager().sendPacket(new C02PacketUseEntity(event.getTarget(), C02PacketUseEntity.Action.ATTACK));
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer != null && mc.thePlayer.hurtTime == 0 && hasReceivedVelocity) {
      hasReceivedVelocity = false;
    }
    if (clickDelayTick != 0) clickDelayTick--;
    if (!canWorkNow()) return;
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;

    shouldCancelAttack = shouldJumpReset(false, null, null, null, null, false);
    updateJumpResetCooldown();

    if (!shouldCancelAttack && shouldAttackCount != 0 && hasJumpReset) {
      SomeUtil.runAttack(
          false, 3.0f, shouldAttackCount, null, true, "Packet", true, false, "Attacked", true, null, null, 1.0f);
      shouldAttackCount = 0;
    }

    String currentMode = mode.getModeString();
    if (currentMode.equals("Intave")) {
      boolean shouldStop = (mc.thePlayer.hurtTime == 10 - maxBlinkTicks.getValue()) || maxBlinkTicks.getValue() == 0;
      if (shouldStop && lastBlinkState) {
        stopIntaveBlink();
      }
      boolean checkSprint = !intaveJumpResetSprint.getValue();
      if (intaveJumpReset.getValue()) {
        if (shouldJumpReset(checkSprint, true, intaveJumpResetNeedForward.getValue(), true, intaveJumpResetNeedForward.getValue(), false)) {
          if (intaveJumpResetSprint.getValue()) {
            SomeUtil.changeSprint(true, true, true);
            serverSprintState = true;
          }
          player.jump();
          debugMessage("Jump | " + mc.thePlayer.hurtTime);
        }
      }
      if (packetMotionValid()
          && triggeredPhases.contains(IntavePhase.PHASE_1)
          && triggeredPhases.contains(IntavePhase.PHASE_2)
          && triggeredPhases.contains(IntavePhase.PHASE_3)
          && mc.thePlayer.hurtTime != 0
          && canOutPutMessage) {
        debugMessage(packetMotionX + " " + packetMotionZ + " -> " + playerNowMotionOutPut(true, false, true));
        canOutPutMessage = false;
      }
    } else if (currentMode.equals("FakeJump")) {
      double e = mc.thePlayer.motionY;
      if (mc.thePlayer.hurtTime == 9
          && !mc.gameSettings.keyBindJump.isKeyDown()
          && mc.thePlayer.motionY != 0.42) {
        mc.thePlayer.jump();
        debugMessage("Jump");
        if (e != mc.thePlayer.motionY) mc.thePlayer.motionY = e;
      }
    } else if (currentMode.equals("Prediction")) {
      preShouldBlink = mc.thePlayer.hurtTime >= (10 - blinkTicks.getValue()) && mc.thePlayer.hurtTime <= 10 && hasReceivedVelocity;
    } else if (currentMode.equals("IntaveSafe")) {
      if (shouldJumpReset(true, null, null, null, null, false) && mc.thePlayer.ticksExisted % 2 == 0) {
        player.jump();
      }
      debugMessage("Jump");
    } else if (currentMode.equals("JumpReset")) {
      if (shouldJumpReset(checkUserSprint.getValue(), false, null, null, null, false)) {
        player.jump();
        if (matrixJumpTest.getValue()) mc.thePlayer.motionY = packetMotionY;
        debugMessage("Jump");
        hasReceivedVelocity = false;
        resetJumpCooldownCounter();
      }
    } else if (currentMode.equals("AirJumpReset")) {
      if (shouldJumpReset(false, false, null, null, null, false)) {
        player.jump();
        debugMessage("Jump");
        hasReceivedVelocity = false;
        resetJumpCooldownCounter();
      }
    } else if (currentMode.equals("Matrix")
        || currentMode.equals("LegitClick")
        || currentMode.equals("LegitClick2")
        || currentMode.equals("MineMenClub")
        || currentMode.equals("NoC0F")
        || currentMode.equals("GrimExempt117")
        || currentMode.equals("XZSwitch")
        || currentMode.equals("OldIntave")
        || currentMode.equals("AttackReduce")
        || currentMode.equals("MineBerryNew")) {
      extraJumpReset();
      if (currentMode.equals("LegitClick")) handleLegitClick();
      if (currentMode.equals("MineMenClub")) minemenClubCounter++;
    } else if (currentMode.equals("Delay")) {
      if (delayReverseFlag
          && (canDelay() || isInLiquidOrWeb() || delayTickCounter >= delayTicks.getValue())) {
        applyDelayedVelocity();
        delayReverseFlag = false;
        delayTickCounter = 0;
        delayTimer.reset();
      }
      if (delayReverseFlag) {
        delayTickCounter++;
      }
      if (delayActive) {
        double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        if (speed > 0.1) {
          double yaw = Math.toDegrees(Math.atan2(player.motionZ, player.motionX)) - 90.0f;
          player.motionX = -Math.sin(Math.toRadians(yaw)) * speed;
          player.motionZ = Math.cos(Math.toRadians(yaw)) * speed;
        }
        delayActive = false;
      } else {
        extraJumpReset();
      }
    } else if (currentMode.equals("PolarJump")) {
      if (shouldJumpReset(true, true, true, true, null, true)) {
        if (!mc.gameSettings.keyBindJump.isKeyDown()) player.jump();
        polarHurtTime = RandomUtil.nextInt(7, 9);
        if (mc.thePlayer.hurtTime == 0) hasReceivedVelocity = false;
      }
    } else if (currentMode.equals("OldGrim")) {
      if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.onGround) {
        mc.thePlayer.addVelocity(-1.3E-10, -1.3E-10, -1.3E-10);
        mc.thePlayer.setSprinting(false);
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!canWorkNow()) return;
    if (event.isCancelled()) return;
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    Packet<?> packet = event.getPacket();
    if (isAttackPacketAndSwingPacket(packet) && shouldCancelAttack && smartJumpReset.getValue() && !hasJumpReset) {
      event.setCancelled(true);
      if (isAttackPacket(packet)) shouldAttackCount++;
    }
    if (packet instanceof S27PacketExplosion && pauseOnExplosion.getValue()) {
      pausedTicks = pauseTicksProp.getValue();
      return;
    }

    if (mode.getModeString().equals("NoC0F")
        && ((packet instanceof C0FPacketConfirmTransaction && mc.thePlayer.hurtTime > 0)
            || (packet instanceof S12PacketEntityVelocity && isValidS12Packet((S12PacketEntityVelocity) packet))
            || packet instanceof S27PacketExplosion)) {
      event.setCancelled(true);
      return;
    }

    if (packet instanceof S12PacketEntityVelocity && isValidS12Packet((S12PacketEntityVelocity) packet)) {
      S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) packet;
      packetMotionX = SomeUtil.roundToPlacesIfNeeded(realMotionX(s12));
      packetMotionY = SomeUtil.roundToPlacesIfNeeded(realMotionY(s12));
      packetMotionZ = SomeUtil.roundToPlacesIfNeeded(realMotionZ(s12));
      sprintTimer.reset();
      String currentMode = mode.getModeString();
      if (currentMode.equals("PolarJump")
          || currentMode.equals("JumpReset")
          || currentMode.equals("AirJumpReset")
          || currentMode.equals("LegitClick")) {
        hasReceivedVelocity = true;
      } else if (currentMode.equals("MineBerryNew")) {
        mineBerryFirstReduce = true;
      } else if (currentMode.equals("OldGrim")) {
        handleOldGrim(s12, event);
      } else if (currentMode.equals("LegitClick2")) {
        hasReceivedVelocity = true;
        legitClick2Times = 0;
      } else if (currentMode.equals("Intave")) {
        hasReceivedVelocity = true;
        triggeredPhases.clear();
        intaveClickTimes = 0;
        moreReduceTimes = 0;
        canOutPutMessage = true;
        shouldBlink = true;
        intaveReversed = false;
        timerState = 0;
        boosting = false;
        slowing = false;
        intaveReduceTimes = 0;
      } else if (currentMode.equals("Intave2")) {
        intave2ReduceCounter = 0;
      } else if (currentMode.equals("XZSwitch")) {
        event.setCancelled(true);
        SomeUtil.setMotion(realMotionZ(s12), realMotionY(s12), realMotionX(s12));
      } else if (currentMode.equals("GrimExempt117")) {
        PacketUtil.sendPacketNoEvent(
            new C03PacketPlayer.C06PacketPlayerPosLook(
                player.posX,
                player.posY,
                player.posZ,
                player.rotationYaw,
                player.rotationPitch,
                player.onGround));
        PacketUtil.sendPacketNoEvent(
            new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                player.getPosition(),
                EnumFacing.DOWN));
        event.setCancelled(true);
      } else if (currentMode.equals("Matrix")) {
        handleMatrixVelocity(s12, event);
      } else if (currentMode.equals("Delay")) {
        handleDelayVelocity(s12, event);
      } else if (currentMode.equals("MineMenClub")) {
        if (minemenClubCounter > mineMenClubDelay.getValue()) {
          event.setCancelled(true);
          minemenClubCounter = 0;
        } else {
          hasReceivedVelocity = true;
        }
      }
    }

    if (packet instanceof S27PacketExplosion) {
      String currentMode = mode.getModeString();
      if (currentMode.equals("Delay")) {
        handleExplosionDelay((S27PacketExplosion) packet, event);
      } else if (currentMode.equals("MineMenClub")) {
        if (minemenClubCounter > mineMenClubDelay.getValue()) {
          event.setCancelled(true);
          minemenClubCounter = 0;
        }
      }
    }

    if (packet instanceof C0BPacketEntityAction) {
      serverSprintState =
          ((C0BPacketEntityAction) packet).getAction() == C0BPacketEntityAction.Action.START_SPRINTING;
    }

    String currentMode = mode.getModeString();
    if (currentMode.equals("Intave")) {
      boolean blinkDistanceCheck;
      if (blinkWorkMaxDistance.getValue() == 0.0f) {
        blinkDistanceCheck = true;
      } else {
        KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
        EntityLivingBase target = killAura != null ? killAura.getTarget() : null;
        blinkDistanceCheck = target != null && getDistance(target) <= blinkWorkMaxDistance.getValue();
      }
      if (shouldBlink
          && mc.thePlayer.hurtTime > 0
          && blinkDistanceCheck
          && maxBlinkTicks.getValue() > 0) {
        BlinkUtil.blink(
            event,
            true,
            true,
            p ->
                p instanceof S12PacketEntityVelocity
                    || p instanceof C03PacketPlayer
                    || p instanceof C02PacketUseEntity
                    || p instanceof C08PacketPlayerBlockPlacement
                    || p instanceof C07PacketPlayerDigging,
            Integer.MAX_VALUE,
            null);
        if (!lastBlinkState) debugMessage("Blinking");
        lastBlinkState = true;
      } else if (!blinkDistanceCheck && BlinkUtil.isBlinking()) {
        BlinkUtil.unblink();
        if (lastBlinkState) debugMessage("Out of range,stop blink");
        lastBlinkState = false;
      }
    } else if (currentMode.equals("Prediction")) {
      if (packet instanceof S12PacketEntityVelocity
          && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()
          && isValidS12Packet((S12PacketEntityVelocity) packet)) {
        preShouldAttack = true;
        hasReceivedVelocity = true;
      }
      if (packet instanceof S12PacketEntityVelocity
          && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()
          && mc.thePlayer.onGround
          && mc.thePlayer.isSprinting()
          && !mc.gameSettings.keyBindJump.isKeyDown()) {
        mc.thePlayer.jump();
      }
      if (preShouldBlink) {
        BlinkUtil.blink(event, true, false);
        preBlinking = true;
        if (preShouldAttack) {
          SomeUtil.runAttack(
              false, 3.0f, blinkTicks.getValue(), null, true, "Packet", true, false, "Attacked", true, null, null, 1.0f);
          preShouldAttack = false;
        }
      }
      if (!preShouldBlink && preBlinking) BlinkUtil.unblink();
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (pausedTicks > 0) {
      pausedTicks--;
      return;
    }
    if (!canWorkNow()) return;
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (mode.getModeString().equals("Delay")) {
      if (delayReverseFlag && delayTimer.hasTimeElapsed(50L * delayTicks.getValue())) {
        applyDelayedVelocity();
        delayReverseFlag = false;
        delayTickCounter = 0;
        delayTimer.reset();
      }
      if (player.hurtTime == 0) {
        delayPendingExplosion = false;
        delayAllowNext = true;
      }
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    hasJumpReset = mc.thePlayer != null && mc.thePlayer.hurtTime == 9 && mc.thePlayer.isSprinting();
  }

  @EventTarget
  public void onWorld(LoadWorldEvent event) {
    pausedTicks = 0;
  }

  @Override
  public void onDisabled() {
    hasReceivedVelocity = false;
    matrixBoostTimer.reset();
    jumpCooldownTickCounter = 0;
    jumpCooldownReceivedHitCounter = 0;
    if (mode.getModeString().equals("Delay")) {
      resetDelayState();
    }
    delayedPackets.clear();
  }

  @Override
  public void onEnabled() {
    hasReceivedVelocity = false;
    pausedTicks = 0;
    resetDelayState();
    triggeredPhases.clear();
    intaveClickTimes = 0;
    moreReduceTimes = 0;
    timerState = 0;
    boosting = false;
    slowing = false;
    previousTimerState = 0;
    matrixBoostTimer.reset();
    minemenClubCounter = 0;
    legitClick2Times = 0;
    attackStartHurtTime = 0;
    polarHurtTime = RandomUtil.nextInt(7, 9);
    jumpCooldownTickCounter = 0;
    jumpCooldownReceivedHitCounter = 0;
    delayedPackets.clear();
    serverSprintState = mc.thePlayer != null && mc.thePlayer.isSprinting();
  }

  private void extraJumpReset() {
    if (jumpReset.getValue()
        && shouldJumpReset(checkUserSprint.getValue(), true, null, false, null, false)
        && RandomUtil.nextInt(0, 99) <= jumpResetChance.getValue()
        && passedJumpCooldown()) {
      if (matrixJumpTest.getValue()) {
        SomeUtil.changeSprint(true, false, true);
      }
      mc.thePlayer.jump();
      if (matrixJumpTest.getValue()) mc.thePlayer.motionY = packetMotionY;
      if (hasReceivedVelocity) hasReceivedVelocity = false;
      resetJumpCooldownCounter();
    }
  }

  private void handleLegitClick() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;

    if (player.hurtTime == 0) {
      attackStartHurtTime = 0;
      return;
    }

    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    boolean blocking = player.isBlocking() || (killAura != null && killAura.isBlocking());
    if (ignoreBlocking.getValue() && blocking) {
      return;
    }

    if (attackStartHurtTime == 0 && player.hurtTime > 0) {
      attackStartHurtTime = player.hurtTime;
    }

    int currentHurtTimeOffset = attackStartHurtTime - player.hurtTime;
    if (currentHurtTimeOffset >= durationHurtTime.getValue()) {
      return;
    }

    if (clickDelayTick > 0) {
      return;
    }

    Entity target = mc.objectMouseOver != null ? mc.objectMouseOver.entityHit : null;

    if (target == null) {
      if (whenFacingEnemyOnly.getValue()) {
        MovingObjectPosition result =
            RayCastUtil.rayCast(player.rotationYaw, player.rotationPitch, clickRange.getValue(), 0.0f);
        if (result != null && result.entityHit != null && SomeUtil.isSelected(result.entityHit)) {
          target = result.entityHit;
        }
      } else {
        Entity nearest = getNearestEntityInRange(clickRange.getValue());
        if (nearest != null && SomeUtil.isSelected(nearest)) {
          target = nearest;
        }
      }
    }

    if (target == null) return;

    int hits = RandomUtil.nextInt(1, 20);
    hits = Math.min(hits, 2);
    boolean keepSprint = modifyMotionWhenClick.getValue() && makeVanillaAttackNotStopSprint.getValue();

    SomeUtil.runAttack(
        keepSprint,
        clickRange.getValue(),
        hits,
        target,
        true,
        swingMode.getModeString(),
        true,
        debugMessage.getValue(),
        "Attacked",
        false,
        null,
        null,
        clickChancePerClick.getValue());

    if (clickDelayTicks.getValue() > 0) {
      clickDelayTick = clickDelayTicks.getValue();
    }

    if (modifyMotionWhenClick.getValue()) SomeUtil.reduceXZ(modifyMotionFactor.getValue().doubleValue());
  }

  private void updateJumpResetCooldown() {
    if (jumpCooldownTickCounter > 0) jumpCooldownTickCounter--;
    boolean receivedHitSelected = jumpCooldownMode.getValue() == 0 || jumpCooldownMode.getValue() == 2;
    if (receivedHitSelected
        && ((mode.getModeString().equals("JumpReset"))
            || (!mode.getModeString().equals("Intave") && jumpReset.getValue()))
        && jumpCooldownReceivedHitCounter < jumpCooldownReceivedHit.getValue()
        && mc.thePlayer != null
        && mc.thePlayer.hurtTime > 0) {
      jumpCooldownReceivedHitCounter++;
    }
  }

  private boolean shouldJumpReset(
      Boolean checkSprint,
      Boolean checkOnGround,
      Boolean checkMoving,
      Boolean needReceivedS12,
      Boolean needForward,
      boolean polarMode) {
    if (mc.thePlayer == null) return false;
    int jumpHurtTime = polarMode ? polarHurtTime : 9;
    return mc.thePlayer.hurtTime == jumpHurtTime
        && (!Boolean.TRUE.equals(needReceivedS12) || hasReceivedVelocity)
        && (!Boolean.TRUE.equals(checkSprint) || mc.thePlayer.isSprinting())
        && (!Boolean.TRUE.equals(checkOnGround) || mc.thePlayer.onGround)
        && (!Boolean.TRUE.equals(checkMoving) || isMoving())
        && (!Boolean.TRUE.equals(needForward) || mc.thePlayer.moveForward > 0.707f)
        && !mc.gameSettings.keyBindJump.isKeyDown();
  }

  private boolean passedJumpCooldown() {
    boolean tickPassed = true;
    boolean receivedHitPassed = true;
    boolean tickSelected = jumpCooldownMode.getValue() == 1 || jumpCooldownMode.getValue() == 2;
    boolean receivedHitSelected = jumpCooldownMode.getValue() == 0 || jumpCooldownMode.getValue() == 2;
    if (tickSelected) {
      tickPassed = jumpCooldownTickCounter == 0;
    }
    if (receivedHitSelected) {
      receivedHitPassed = jumpCooldownReceivedHitCounter >= jumpCooldownReceivedHit.getValue();
    }
    return tickPassed && receivedHitPassed;
  }

  private void resetJumpCooldownCounter() {
    boolean tickSelected = jumpCooldownMode.getValue() == 1 || jumpCooldownMode.getValue() == 2;
    boolean receivedHitSelected = jumpCooldownMode.getValue() == 0 || jumpCooldownMode.getValue() == 2;
    if (tickSelected) {
      jumpCooldownTickCounter = jumpCooldownTick.getValue();
    }
    if (receivedHitSelected) {
      jumpCooldownReceivedHitCounter = 0;
    }
  }

  private Entity getNearestEntityInRange(float range) {
    EntityPlayer player = mc.thePlayer;
    if (player == null || mc.theWorld == null) return null;
    Entity best = null;
    double bestDist = Double.MAX_VALUE;
    for (Object o : mc.theWorld.loadedEntityList) {
      if (!(o instanceof Entity)) continue;
      Entity e = (Entity) o;
      if (!SomeUtil.isSelected(e)) continue;
      double d = BackTrackUtil.getDistanceToEntityBox(e);
      if (d <= range && d < bestDist) {
        bestDist = d;
        best = e;
      }
    }
    return best;
  }

  private void handleOldGrim(S12PacketEntityVelocity packet, PacketEvent event) {
    if (mc.thePlayer.isDead) return;
    if (mc.currentScreen instanceof GuiGameOver) return;
    if (mc.playerController.getCurrentGameType() == WorldSettings.GameType.SPECTATOR) return;
    if (mc.thePlayer.isOnLadder()) return;
    if (mc.thePlayer.isBurning() && fireCheckValue.getValue()) return;
    if (mc.thePlayer.isInWater() && waterCheckValue.getValue()) return;
    if (mc.thePlayer.fallDistance > 1.5 && fallCheckValue.getValue()) return;
    if (ItemUtil.isEating() && consumecheck.getValue()) return;
    if (soulSandCheck()) return;
    if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
      double horizontalStrength =
          Math.sqrt(
              Math.pow(((IAccessorS12PacketEntityVelocity) packet).getMotionX(), 2)
                  + Math.pow(((IAccessorS12PacketEntityVelocity) packet).getMotionZ(), 2));
      if (horizontalStrength <= 1000) return;
      MovingObjectPosition mouse = mc.objectMouseOver;
      Entity targetEntity = null;

      if (mouse != null
          && mouse.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
          && mouse.entityHit instanceof EntityLivingBase
          && BackTrackUtil.getDistanceToEntityBox(mouse.entityHit) <= killAuraRange()) {
        targetEntity = mouse.entityHit;
      }

      if (targetEntity == null && !raycastValue.getValue()) {
        KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
        EntityLivingBase target = killAura != null ? killAura.getTarget() : null;
        if (target != null && BackTrackUtil.getDistanceToEntityBox(target) <= grimrange.getValue()) {
          targetEntity = target;
        }
      }

      boolean state = serverSprintState;
      if (targetEntity != null) {
        if (!state) {
          PacketUtil.sendPacketNoEvent(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
        }
        for (int i = 0; i < attackCountValue.getValue(); i++) {
          mc.getNetHandler().getNetworkManager().sendPacket(new C0APacketAnimation());
          mc.getNetHandler().getNetworkManager().sendPacket(new C02PacketUseEntity(targetEntity, C02PacketUseEntity.Action.ATTACK));
        }
        if (!state) {
          PacketUtil.sendPacketNoEvent(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
        }
        velX = ((IAccessorS12PacketEntityVelocity) packet).getMotionX();
        velY = ((IAccessorS12PacketEntityVelocity) packet).getMotionY();
        velZ = ((IAccessorS12PacketEntityVelocity) packet).getMotionZ();
        event.setCancelled(true);
      }
    }
  }

  private float killAuraRange() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    return killAura != null ? killAura.attackRange.getValue() : 3.0f;
  }

  private void applyDelayedVelocity() {
    boolean shouldPerformSpecialJumpReset = false;

    for (java.util.Iterator<Map.Entry<Packet<?>, Long>> it = delayedPackets.entrySet().iterator();
        it.hasNext(); ) {
      Map.Entry<Packet<?>, Long> entry = it.next();
      if (entry.getKey() instanceof S12PacketEntityVelocity) {
        applyVelocityReduction((S12PacketEntityVelocity) entry.getKey());
        shouldPerformSpecialJumpReset = true;
        it.remove();
      }
    }

    if (shouldPerformSpecialJumpReset
        && mc.thePlayer != null
        && mc.thePlayer.onGround
        && mc.thePlayer.isSprinting()
        && jumpReset.getValue()
        && shouldJumpReset(checkUserSprint.getValue(), true, null, false, null, false)
        && RandomUtil.nextInt(0, 99) <= jumpResetChance.getValue()
        && passedJumpCooldown()) {
      mc.thePlayer.jump();
      if (matrixJumpTest.getValue()) mc.thePlayer.motionY = packetMotionY;
      if (hasReceivedVelocity) hasReceivedVelocity = false;
      resetJumpCooldownCounter();
      debugMessage("Special Jump Reset triggered after delayed velocity");
    }
    if (delayAttackReduce.getValue()) {
      SomeUtil.runAttack(
          false, 3.0f, 5, null, true, "Packet", false, debugMessage.getValue(), "DelayAttackReduced", false, null, null, 1.0f);
    }
  }

  private void applyVelocityReduction(S12PacketEntityVelocity packet) {
    EntityPlayer thePlayer = mc.thePlayer;
    if (thePlayer == null) return;

    double motionX = realMotionX(packet);
    double motionZ = realMotionZ(packet);
    double motionY = realMotionY(packet);

    if (delayHorizontal.getValue() != 0f) {
      motionX *= delayHorizontal.getValue();
      motionZ *= delayHorizontal.getValue();
    }

    if (delayVertical.getValue() != 0f) {
      motionY *= delayVertical.getValue();
    }

    thePlayer.motionX = motionX;
    thePlayer.motionZ = motionZ;
    thePlayer.motionY = motionY;
  }

  private void handleMatrixVelocity(S12PacketEntityVelocity packet, PacketEvent event) {
    hasReceivedVelocity = true;
    event.setCancelled(true);

    if (Math.abs(realMotionY(packet)) >= 0.1f) {
      mc.thePlayer.motionY = realMotionY(packet);
      matrixMotionYReduce = true;

      if (!isMoving()) {
        double reducedSpeed = Math.max(packetBpt(packet) * 0.1, SomeUtil.bpt());
        if (packetBpt(packet) > 0) {
          mc.thePlayer.motionX = realMotionX(packet) / packetBpt(packet) * reducedSpeed;
          mc.thePlayer.motionZ = realMotionZ(packet) / packetBpt(packet) * reducedSpeed;
        }
      } else if (matrixBoost.getValue() && matrixBoostTimer.hasTimeElapsed(matrixBoostDelay.getValue())) {
        SomeUtil.reduceXZ(matrixBoostFactor.getValue().doubleValue() + 1);
        matrixBoostTimer.reset();
      }
    }
  }

  private void handleDelayVelocity(S12PacketEntityVelocity packet, PacketEvent event) {
    if (!delayReverseFlag
        && !canDelay()
        && !isInLiquidOrWeb()
        && !delayPendingExplosion
        && (!delayAllowNext || !delayFakeCheck.getValue())) {
      delayChanceCounter = delayChanceCounter % 100 + delayChance.getValue();
      if (delayChanceCounter >= 100) {
        delayedPackets.put(packet, System.currentTimeMillis());
        event.setCancelled(true);
        delayReverseFlag = true;
        delayActive = true;
        delayTimer.reset();
        return;
      }
    }

    applyVelocityReduction(packet);
    event.setCancelled(true);
  }

  private void handleExplosionDelay(S27PacketExplosion packet, PacketEvent event) {
    delayPendingExplosion = true;
    if (delayHorizontal.getValue() == 0f || delayVertical.getValue() == 0f) {
      event.setCancelled(true);
    } else {
      IAccessorS27PacketExplosion explosion = (IAccessorS27PacketExplosion) packet;
      explosion.setMotionX(explosion.getMotionX() * delayHorizontal.getValue());
      explosion.setMotionY(explosion.getMotionY() * delayVertical.getValue());
      explosion.setMotionZ(explosion.getMotionZ() * delayHorizontal.getValue());
    }
  }

  private void resetDelayState() {
    delayChanceCounter = 0;
    delayActive = false;
    delayReverseFlag = false;
    delayPendingExplosion = false;
    delayAllowNext = true;
    delayTickCounter = 0;
    delayTimer.reset();
  }

  private boolean canDelay() {
    EntityPlayer thePlayer = mc.thePlayer;
    if (thePlayer == null) return false;
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    boolean killAuraBlocking = killAura != null && killAura.isEnabled() && killAura.isBlocking();
    return thePlayer.onGround && (!killAuraBlocking);
  }

  private boolean isInLiquidOrWeb() {
    EntityPlayer thePlayer = mc.thePlayer;
    if (thePlayer == null) return false;
    return thePlayer.isInWater() || thePlayer.isInLava() || ((IAccessorEntity) thePlayer).getIsInWeb();
  }

  private boolean isValidS12Packet(S12PacketEntityVelocity packet) {
    return realMotionX(packet) != 0.0
        && realMotionY(packet) != 0.0
        && realMotionZ(packet) != 0.0
        && packet.getEntityID() == mc.thePlayer.getEntityId();
  }

  private boolean canWorkNow() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return false;
    boolean onGround = player.onGround;
    boolean inAir = !onGround;

    String work = allowWorkWhen.getModeString();
    if (work.equals("OnlySprinting") && !mc.thePlayer.isSprinting()) return false;
    if (pausedTicks > 0) return false;
    if (work.equals("OnGround+InAir")) return true;
    if (work.equals("OnGround")) return onGround;
    if (work.equals("InAir")) return inAir;
    return true;
  }

  private boolean displayJumpResetChoices() {
    String currentMode = mode.getModeString();
    return currentMode.equals("JumpReset")
        || (!currentMode.equals("Intave")
            && !currentMode.equals("IntaveSafe")
            && !currentMode.equals("PolarJump")
            && !currentMode.equals("AirJumpReset")
            && !currentMode.equals("Prediction")
            && jumpReset.getValue());
  }

  private boolean soulSandCheck() {
    if (mc.thePlayer == null || mc.theWorld == null) return false;
    net.minecraft.util.AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().contract(0.001, 0.001, 0.001);
    int minX = MathHelper.floor_double(box.minX);
    int maxX = MathHelper.floor_double(box.maxX + 1.0);
    int minY = MathHelper.floor_double(box.minY);
    int maxY = MathHelper.floor_double(box.maxY + 1.0);
    int minZ = MathHelper.floor_double(box.minZ);
    int maxZ = MathHelper.floor_double(box.maxZ + 1.0);
    for (int x = minX; x < maxX; x++) {
      for (int y = minY; y < maxY; y++) {
        for (int z = minZ; z < maxZ; z++) {
          if (mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof BlockSoulSand) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void debugMessage(String message) {
    if (!debugMessage.getValue()) return;
    if (message == null) return;
    miau.util.client.ChatUtil.display("%s", message);
  }

  private boolean packetMotionValid() {
    return packetMotionX != 0.0 && packetMotionZ != 0.0 && packetMotionY != 0.0;
  }

  private String playerNowMotionOutPut(boolean x, boolean y, boolean z) {
    StringBuilder sb = new StringBuilder();
    double mx = SomeUtil.roundToPlacesIfNeeded(mc.thePlayer.motionX);
    double my = SomeUtil.roundToPlacesIfNeeded(mc.thePlayer.motionY);
    double mz = SomeUtil.roundToPlacesIfNeeded(mc.thePlayer.motionZ);
    if (x) sb.append(mx);
    if (y) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(my);
    }
    if (z) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(mz);
    }
    return sb.toString();
  }

  private boolean knockBackIsNegated(double xMotion, double zMotion) {
    double motionX = mc.thePlayer.motionX;
    double motionZ = mc.thePlayer.motionZ;
    boolean isXNegated = Math.signum(motionX) != Math.signum(xMotion);
    boolean isZNegated = Math.signum(motionZ) != Math.signum(zMotion);
    return isXNegated && isZNegated;
  }

  private void stopIntaveBlink() {
    if (shouldBlink && BlinkUtil.isBlinking()) {
      SomeUtil.runAttack(
          false, 3.0f, 1, null, true, "Packet", true, false, "Attacked", true, null, null, 1.0f);
      BlinkUtil.unblink();
      shouldBlink = false;
      lastBlinkState = false;
      debugMessage("Unblink | " + mc.thePlayer.hurtTime);
    }
  }

  private double getDistance(EntityLivingBase target) {
    return BackTrackUtil.getDistanceToEntityBox(target);
  }

  private void intaveReduce(int phase, boolean safe) {
    if (safe && !(globalTarget instanceof EntityPlayer)) return;
    stopIntaveBlink();
    if (knockBackIsNegated(packetMotionX, packetMotionZ) && onlyWhenNeed.getValue()) {
      if (intaveReversed && intaveReduceTimes >= 2 && intaveReduceTimes <= 4) {
        canCancelHitSlow = false;
        SomeUtil.reduceXZ(0.6);
        return;
      }
      KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
      KeepSprint keepSprint = (KeepSprint) Miau.moduleManager.modules.get(KeepSprint.class);
      boolean keepSprintModule = keepSprint != null && keepSprint.isEnabled();
      boolean killAuraKeepSprint = killAura != null && killAura.isEnabled();
      if (!keepSprintModule && !killAuraKeepSprint) {
        canCancelHitSlow = true;
        return;
      }
      return;
    } else {
      canCancelHitSlow = false;
    }
    if (!intaveReversed && intaveReduceTimes == 1 && mc.thePlayer.hurtTime >= 1 && mc.thePlayer.hurtTime <= 9) {
      if (knockBackIsNegated(packetMotionX, packetMotionZ)) return;
      if (SomeUtil.bps() > 2.805) {
        if (SomeUtil.calculateAngleDifference() > 15.0) {
          SomeUtil.setBPSTo(-Math.min(5.612, SomeUtil.bps() * 0.6));
        }
        intaveReversed = true;
        debugMessage("IntaveReverse | " + mc.thePlayer.hurtTime);
      }
    } else if (mc.thePlayer.hurtTime != 10) {
      intaveReduceTimes++;
    } else {
      intaveReduceTimes = 1;
    }
    if (intaveReduceTimes > 5 && intaveReversed) return;
    switch (phase) {
      case 0:
        SomeUtil.runAttack(
            false, 3.0f, 1, null, true, "Packet", false, false, "Attacked", true, null, null, 1.0f);
        debugMessage("IntaveReduce | " + mc.thePlayer.hurtTime);
        break;
      case 1:
        if (!getTriggeredPhase(1)) {
          if (extraC0APerReduce.getValue()) {
            for (int i = 0; i < extraPacketCount.getValue(); i++) {
              PacketUtil.sendPacket(new C0APacketAnimation());
            }
          }
          SomeUtil.reduceXZ(0.6);
          debugMessage("IntaveReduce | " + mc.thePlayer.hurtTime);
          intaveReduceTrigger(1);
          return;
        } else if (!getTriggeredPhase(4)) {
          if (extraC0APerReduce.getValue()) {
            for (int i = 0; i < extraPacketCount.getValue(); i++) {
              PacketUtil.sendPacket(new C0APacketAnimation());
            }
          }
          if (SomeUtil.calculateAngleDifference() < 15.0) SomeUtil.reduceXZ(1.5);
          else SomeUtil.reduceXZ(0.6);
          debugMessage("IntaveReduce | " + mc.thePlayer.hurtTime);
          intaveReduceTrigger(4);
          return;
        }
        if (moreReduceTimes < maxMoreReduce.getValue() && moreReduce.getValue()) {
          moreReduceTimes++;
          SomeUtil.reduceXZ(getMoreReduceFactor(moreReduceTimes));
        }
        break;
      case 2:
        if (!getTriggeredPhase(2)) {
          if (extraC0APerReduce.getValue()) {
            for (int i = 0; i < extraPacketCount.getValue(); i++) {
              PacketUtil.sendPacket(new C0APacketAnimation());
            }
          }
          SomeUtil.reduceXZ(0.36);
          debugMessage("IntaveReduce | " + mc.thePlayer.hurtTime);
          intaveReduceTrigger(2);
          return;
        } else if (!getTriggeredPhase(5)) {
          if (extraC0APerReduce.getValue()) {
            for (int i = 0; i < extraPacketCount.getValue(); i++) {
              PacketUtil.sendPacket(new C0APacketAnimation());
            }
          }
          if (SomeUtil.calculateAngleDifference() < 15.0) SomeUtil.reduceXZ(1.5);
          else SomeUtil.reduceXZ(0.6);
          debugMessage("IntaveReduce | " + mc.thePlayer.hurtTime);
          intaveReduceTrigger(5);
          return;
        }
        if (moreReduceTimes < maxMoreReduce.getValue() && moreReduce.getValue()) {
          moreReduceTimes++;
          SomeUtil.reduceXZ(getMoreReduceFactor(moreReduceTimes));
        }
        break;
      case 3:
        if (!getTriggeredPhase(3)) {
          if (extraC0APerReduce.getValue()) {
            for (int i = 0; i < extraPacketCount.getValue(); i++) {
              PacketUtil.sendPacket(new C0APacketAnimation());
            }
          }
          SomeUtil.reduceXZ(0.216);
          debugMessage("IntaveReduce | " + mc.thePlayer.hurtTime);
          intaveReduceTrigger(3);
          return;
        } else if (!getTriggeredPhase(6)) {
          if (extraC0APerReduce.getValue()) {
            for (int i = 0; i < extraPacketCount.getValue(); i++) {
              PacketUtil.sendPacket(new C0APacketAnimation());
            }
          }
          if (SomeUtil.calculateAngleDifference() < 15.0) SomeUtil.reduceXZ(1.5);
          else SomeUtil.reduceXZ(0.6);
          debugMessage("IntaveReduce | " + mc.thePlayer.hurtTime);
          intaveReduceTrigger(6);
          return;
        }
        if (moreReduceTimes < maxMoreReduce.getValue() && moreReduce.getValue()) {
          moreReduceTimes++;
          SomeUtil.reduceXZ(getMoreReduceFactor(moreReduceTimes));
        }
        break;
      default:
        break;
    }
  }

  private double getMoreReduceFactor(int reduceCount) {
    switch (reduceCount) {
      case 1:
        return 0.5 / 0.6;
      case 2:
        return 0.75;
      default:
        double baseFactor = 0.7;
        double reduction = (reduceCount - 3) * 0.05;
        return Math.max(0.0, baseFactor - reduction);
    }
  }

  private boolean getTriggeredPhase(int phase) {
    switch (phase) {
      case 1:
        return triggeredPhases.contains(IntavePhase.PHASE_1);
      case 2:
        return triggeredPhases.contains(IntavePhase.PHASE_2);
      case 3:
        return triggeredPhases.contains(IntavePhase.PHASE_3);
      case 4:
        return triggeredPhases.contains(IntavePhase.PHASE_4);
      case 5:
        return triggeredPhases.contains(IntavePhase.PHASE_5);
      case 6:
        return triggeredPhases.contains(IntavePhase.PHASE_6);
      default:
        return false;
    }
  }

  private void intaveReduceTrigger(int phase) {
    switch (phase) {
      case 1:
        triggeredPhases.add(IntavePhase.PHASE_1);
        break;
      case 2:
        triggeredPhases.add(IntavePhase.PHASE_2);
        break;
      case 3:
        triggeredPhases.add(IntavePhase.PHASE_3);
        break;
      case 4:
        triggeredPhases.add(IntavePhase.PHASE_4);
        break;
      case 5:
        triggeredPhases.add(IntavePhase.PHASE_5);
        break;
      case 6:
        triggeredPhases.add(IntavePhase.PHASE_6);
        break;
      default:
        break;
    }
  }

  private boolean isMoving() {
    return mc.thePlayer != null
        && (mc.thePlayer.moveForward != 0f || mc.thePlayer.moveStrafing != 0f);
  }

  private static boolean isAttackPacket(Packet<?> packet) {
    return packet instanceof C02PacketUseEntity
        && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK;
  }

  private static boolean isSwingPacket(Packet<?> packet) {
    return packet instanceof C0APacketAnimation;
  }

  private static boolean isAttackPacketAndSwingPacket(Packet<?> packet) {
    return isAttackPacket(packet) || isSwingPacket(packet);
  }

  private static double realMotionX(S12PacketEntityVelocity packet) {
    return ((IAccessorS12PacketEntityVelocity) packet).getMotionX() / 8000.0;
  }

  private static double realMotionY(S12PacketEntityVelocity packet) {
    return ((IAccessorS12PacketEntityVelocity) packet).getMotionY() / 8000.0;
  }

  private static double realMotionZ(S12PacketEntityVelocity packet) {
    return ((IAccessorS12PacketEntityVelocity) packet).getMotionZ() / 8000.0;
  }

  private static double packetBpt(S12PacketEntityVelocity packet) {
    return Math.hypot(realMotionX(packet), realMotionZ(packet));
  }
}
