package miau.module.modules.combat;

import java.awt.Color;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.UpdateEvent;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.TextProperty;
import miau.util.client.ChatUtil;
import miau.util.misc.BackTrackUtil;
import miau.util.misc.CPSCounter;
import miau.util.misc.SomeUtil;
import miau.util.network.BlinkUtil;
import miau.util.render.RenderUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class TimerRangeV2 extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final ModeProperty tagMode =
      new ModeProperty(
          "TagMode", 0, new String[] {"WorkRange", "IsWorking", "MinSpeed-MaxSpeed", "Custom"});
  private final TextProperty customText =
      new TextProperty(
          "CustomTagText", "", () -> tagMode.getModeString().equals("Custom"));
  private final ModeProperty workMode =
      new ModeProperty("Mode", 1, new String[] {"SlowFirst", "BoostFirst"});
  private final FloatProperty maxRange = new FloatProperty("MaxRange", 4.0f, 0.0f, 8.0f);
  private final FloatProperty minRange = new FloatProperty("MinRange", 3.0f, 0.0f, 8.0f);
  private final FloatProperty boostTimer = new FloatProperty("BoostTimerSpeed", 2.0f, 1.0f, 10.0f);
  private final IntProperty boostTime = new IntProperty("BoostTime", 100, 0, 3000);
  private final FloatProperty slowTimer = new FloatProperty("SlowTimerSpeed", 0.5f, 0.01f, 1.0f);
  private final IntProperty slowTime = new IntProperty("SlowTime", 100, 0, 3000);
  private final IntProperty cooldownTime = new IntProperty("CooldownTime", 100, 0, 3000);
  private final BooleanProperty attackWhenBoosting =
      new BooleanProperty("AttackWhenChangingTimer", false);
  private final BooleanProperty attackOnBoosting =
      new BooleanProperty(
          "AttackOnBoosting", false, () -> attackWhenBoosting.getValue());
  private final BooleanProperty attackOnSlowing =
      new BooleanProperty(
          "AttackOnSlowing", true, () -> attackWhenBoosting.getValue());
  private final IntProperty attackCount =
      new IntProperty("TotalAttackCount", 1, 1, 5, () -> attackWhenBoosting.getValue());
  private final BooleanProperty onlyAttackWhenNotReachedCPSLimit =
      new BooleanProperty(
          "OnlyAttackWhenNotReachedCPSLimit", false, () -> attackWhenBoosting.getValue());
  private final IntProperty CPSLimit =
      new IntProperty(
          "CPSLimit",
          20,
          1,
          100,
          () -> attackWhenBoosting.getValue() && onlyAttackWhenNotReachedCPSLimit.getValue());
  private final FloatProperty attackMaxRange =
      new FloatProperty(
          "AttackMaxRange", 3.0f, 0.0f, 8.0f, () -> attackWhenBoosting.getValue());
  private final ModeProperty swingMode =
      new ModeProperty(
          "AttackSwingMode",
          0,
          new String[] {"Normal", "Packet"},
          () -> attackWhenBoosting.getValue());
  private final BooleanProperty keepSprint =
      new BooleanProperty("KeepSprint", false, () -> attackWhenBoosting.getValue());
  private final FloatProperty allowKeepSprintHurtTime =
      new FloatProperty(
          "AllowKeepSprintHurtTime",
          0f,
          10f,
          0f,
          10f,
          () -> attackWhenBoosting.getValue() && keepSprint.getValue());
  private final BooleanProperty boostMotion = new BooleanProperty("BoostMotion", false);
  private final BooleanProperty boostOnBoosting =
      new BooleanProperty("BoostOnBoosting", false, () -> boostMotion.getValue());
  private final BooleanProperty boostOnSlowing =
      new BooleanProperty("BoostOnSlowing", true, () -> boostMotion.getValue());
  private final FloatProperty boostBoostingFactor =
      new FloatProperty(
          "BoostFactorBoosting",
          0.0f,
          0.0f,
          2.0f,
          () -> boostOnBoosting.getValue() && boostMotion.getValue());
  private final FloatProperty boostSlowingFactor =
      new FloatProperty(
          "BoostFactorSlowing",
          0.0f,
          0.0f,
          2.0f,
          () -> boostOnSlowing.getValue() && boostMotion.getValue());
  private final BooleanProperty stopBoostingWhenHurting =
      new BooleanProperty("StopBoostingWhenHurt", false);
  private final BooleanProperty blinkOnWorking = new BooleanProperty("BlinkOnBoosting", false);
  private final BooleanProperty cancelC03 =
      new BooleanProperty(
          "CancelC03WhenWorking", false, () -> blinkOnWorking.getValue());
  private final BooleanProperty onlyForward = new BooleanProperty("OnlyForward", false);
  private final BooleanProperty debugMessage = new BooleanProperty("DebugMessage", false);

  private final BooleanProperty safe = new BooleanProperty("Safe", false);

  private final BooleanProperty visualPrediction = new BooleanProperty("VisualPrediction", false);
  private final BooleanProperty predictionBox =
      new BooleanProperty("PredictionBox", true, () -> visualPrediction.getValue());
  private final ColorProperty predictionBoxColor =
      new ColorProperty(
          "PredictionBoxColor",
          new Color(255, 0, 0).getRGB(),
          () -> predictionBox.getValue());
  private final BooleanProperty predictionLine =
      new BooleanProperty("PredictionLine", true, () -> visualPrediction.getValue());
  private final ColorProperty predictionLineColor =
      new ColorProperty(
          "PredictionLineColor",
          new Color(255, 255, 0).getRGB(),
          () -> predictionLine.getValue());
  private final FloatProperty predictionLineWidth =
      new FloatProperty(
          "PredictionLineWidth", 2.0f, 0.5f, 5.0f, () -> predictionLine.getValue());
  private final BooleanProperty showCurrentPos =
      new BooleanProperty("ShowCurrentPos", true, () -> visualPrediction.getValue());
  private final ColorProperty currentPosColor =
      new ColorProperty(
          "CurrentPosColor",
          new Color(0, 255, 0).getRGB(),
          () -> showCurrentPos.getValue());
  private final IntProperty predictionDuration =
      new IntProperty(
          "PredictionDuration", 200, 50, 1000, () -> visualPrediction.getValue());

  private boolean isBoosting = false;
  private final TimerUtil boostedTime = new TimerUtil();
  private final TimerUtil slowedTime = new TimerUtil();
  private final TimerUtil cooldownTimer = new TimerUtil();
  private int attackCounter = 0;
  private boolean hasSlowed = false;
  private boolean hasBoosted = false;
  private boolean shouldBlink = false;
  private boolean hasBlink = false;

  private Vec3 predictedPlayerPosition = null;
  private boolean shouldShowPrediction = false;

  public TimerRangeV2() {
    super("TimerRangeV2", false);
  }

  private float effectiveMaxRange() {
    return Math.max(maxRange.getValue(), minRange.getValue());
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (mc.theWorld == null) return;
    boolean timerChanged =
        ((IAccessorMinecraft) mc).getTimer().timerSpeed == boostTimer.getValue()
            || ((IAccessorMinecraft) mc).getTimer().timerSpeed == slowTimer.getValue();
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    Entity target = killAura == null ? null : killAura.getTarget();
    if (target == null) {
      if (timerChanged) {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0f;
      }
      predictedPlayerPosition = null;
      shouldShowPrediction = false;
      return;
    }
    if (onlyForward.getValue() && !mc.gameSettings.keyBindForward.isKeyDown()) {
      predictedPlayerPosition = null;
      shouldShowPrediction = false;
      return;
    }

    if (stopBoostingWhenHurting.getValue()
        && SomeUtil.isHurting()
        && ((IAccessorMinecraft) mc).getTimer().timerSpeed == boostTimer.getValue()) {
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
      if (debugMessage.getValue()) {
        debugMessage("CancelledTimerChange");
      }
      predictedPlayerPosition = null;
      shouldShowPrediction = false;
      return;
    }
    if (killAura == null || !killAura.isEnabled()) {
      if (timerChanged) {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
      }
      predictedPlayerPosition = null;
      shouldShowPrediction = false;
      return;
    }

    double distance = BackTrackUtil.getDistanceToEntityBox(target);

    if (distance < minRange.getValue() || distance > effectiveMaxRange()) {
      if (timerChanged) {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
      }
      predictedPlayerPosition = null;
      shouldShowPrediction = false;
      return;
    }

    boolean justStartedBoosting = !isBoosting && cooldownTimer.hasTimeElapsed(cooldownTime.getValue());

    if (justStartedBoosting) {
      isBoosting = true;
      boostedTime.reset();
      slowedTime.reset();
      attackCounter = 0;
      hasSlowed = false;
      hasBoosted = false;

      if (visualPrediction.getValue()) {
        calculateBoostPrediction(player, target);
        shouldShowPrediction = true;
      }
    }

    if (isBoosting) {
      if (workMode.getModeString().equals("BoostFirst")) {
        if (!boostedTime.hasTimeElapsed(boostTime.getValue())) {
          if (blinkOnWorking.getValue()) {
            shouldBlink = true;
          }
          ((IAccessorMinecraft) mc).getTimer().timerSpeed = boostTimer.getValue();
          hasBoosted = true;
          slowedTime.reset();
          debugMessage("Boosting");
          if (attackWhenBoosting.getValue() && attackOnBoosting.getValue()) {
            if (attackCounter < attackCount.getValue()) {
              runAttack();
            }
          }
          if (boostMotion.getValue() && boostOnBoosting.getValue()) {
            SomeUtil.reduceXZ(boostBoostingFactor.getValue() + 1.0);
          }
        } else if (!slowedTime.hasTimeElapsed(slowTime.getValue())) {
          if (blinkOnWorking.getValue() && shouldBlink) {
            shouldBlink = false;
          }
          ((IAccessorMinecraft) mc).getTimer().timerSpeed = slowTimer.getValue();
          hasSlowed = true;
          debugMessage("Slowing");
          if (attackWhenBoosting.getValue()) {
            if (attackCounter < attackCount.getValue() && attackOnSlowing.getValue()) {
              runAttack();
            }
          }
          if (boostMotion.getValue() && boostOnSlowing.getValue()) {
            SomeUtil.reduceXZ(boostSlowingFactor.getValue() + 1.0);
          }
          shouldShowPrediction = false;
        } else {
          if (safe.getValue() && hasBoosted && !hasSlowed) {
            if (blinkOnWorking.getValue() && shouldBlink) {
              shouldBlink = false;
            }

            slowedTime.reset();
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = slowTimer.getValue();
            hasSlowed = true;
            debugMessage("Safe mode: Forcing slow timer");
          } else {
            isBoosting = false;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
            cooldownTimer.reset();
            attackCounter = 0;
            predictedPlayerPosition = null;
            shouldShowPrediction = false;
          }
        }
      } else {
        if (!slowedTime.hasTimeElapsed(slowTime.getValue())) {
          if (blinkOnWorking.getValue() && shouldBlink) {
            shouldBlink = false;
          }

          ((IAccessorMinecraft) mc).getTimer().timerSpeed = slowTimer.getValue();
          hasSlowed = true;
          boostedTime.reset();
          debugMessage("Slowing");
          if (attackWhenBoosting.getValue()) {
            if (attackCounter < attackCount.getValue() && attackOnSlowing.getValue()) {
              runAttack();
            }
          }
          if (boostMotion.getValue() && boostOnSlowing.getValue()) {
            SomeUtil.reduceXZ(boostSlowingFactor.getValue() + 1.0);
          }
        } else if (!boostedTime.hasTimeElapsed(boostTime.getValue())) {
          if (blinkOnWorking.getValue()) {
            shouldBlink = true;
          }
          ((IAccessorMinecraft) mc).getTimer().timerSpeed = boostTimer.getValue();
          hasBoosted = true;
          debugMessage("Boosting");

          if (visualPrediction.getValue() && !shouldShowPrediction) {
            calculateBoostPrediction(player, target);
            shouldShowPrediction = true;
          }

          if (attackWhenBoosting.getValue()) {
            if (attackCounter < attackCount.getValue() && attackOnBoosting.getValue()) {
              runAttack();
            }
          }
          if (boostMotion.getValue() && boostOnBoosting.getValue()) {
            SomeUtil.reduceXZ(boostBoostingFactor.getValue() + 1.0);
          }
        } else {
          if (safe.getValue() && hasBoosted && !hasSlowed) {
            slowedTime.reset();
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = slowTimer.getValue();
            hasSlowed = true;
            debugMessage("Safe mode: Forcing slow timer");
          } else {
            isBoosting = false;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
            cooldownTimer.reset();
            attackCounter = 0;
            predictedPlayerPosition = null;
            shouldShowPrediction = false;
          }
        }
      }
    } else {
      if (((IAccessorMinecraft) mc).getTimer().timerSpeed != 1f) {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
      }
      predictedPlayerPosition = null;
      shouldShowPrediction = false;
    }

    if (shouldShowPrediction && boostedTime.hasTimeElapsed(predictionDuration.getValue())) {
      shouldShowPrediction = false;
    }
  }

  @EventTarget
  public void onRender(Render3DEvent event) {
    if (!visualPrediction.getValue() || !shouldShowPrediction) return;

    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    Vec3 predictedPos = predictedPlayerPosition;
    if (predictedPos == null) return;

    GL11.glPushMatrix();
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

    double viewerX = mc.getRenderManager().viewerPosX;
    double viewerY = mc.getRenderManager().viewerPosY;
    double viewerZ = mc.getRenderManager().viewerPosZ;

    if (showCurrentPos.getValue()) {
      Vec3 currentPos = new Vec3(player.posX, player.posY, player.posZ);
      drawPlayerBox(currentPos, viewerX, viewerY, viewerZ, currentPosColor.getValue(), 100, "Current");
    }

    if (predictionLine.getValue()) {
      drawPredictionLine(player, predictedPos, viewerX, viewerY, viewerZ);
    }

    if (predictionBox.getValue()) {
      drawPlayerBox(predictedPos, viewerX, viewerY, viewerZ, predictionBoxColor.getValue(), 100, "Boosted");
    }

    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glPopMatrix();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getPacket() instanceof C03PacketPlayer && cancelC03.getValue()) {
      if (cancelC03.getValue() && isBoosting) {
        event.setCancelled(true);
      }
    } else if (blinkOnWorking.getValue()) {
      if (shouldBlink && isBoosting && !hasBlink) {
        BlinkUtil.blink(event, true, false, null, Integer.MAX_VALUE, null);
        if (!hasBlink) {
          debugMessage("StartBlink");
        }
        hasBlink = true;
      } else if (hasBlink && !isBoosting) {
        debugMessage("StopBlink");
        BlinkUtil.unblink();
        hasBlink = false;
      }
    }
  }

  @Override
  public void onEnabled() {
    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
    isBoosting = false;
    boostedTime.reset();
    slowedTime.reset();
    cooldownTimer.reset();
    hasSlowed = false;
    hasBoosted = false;
    predictedPlayerPosition = null;
    shouldShowPrediction = false;
  }

  @Override
  public void onDisabled() {
    float current = ((IAccessorMinecraft) mc).getTimer().timerSpeed;
    if (current == boostTimer.getValue() || current == slowTimer.getValue()) {
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
    }
    predictedPlayerPosition = null;
    shouldShowPrediction = false;
  }

  @Override
  public String[] getSuffix() {
    switch (tagMode.getModeString()) {
      case "WorkRange":
        return new String[] {
          String.format("%s - %s", minRange.getValue(), effectiveMaxRange())
        };
      case "IsWorking":
        return new String[] {isBoosting ? "Working" : "Idle"};
      case "MinSpeed-MaxSpeed":
        return new String[] {
          String.format("%sx - %sx", slowTimer.getValue(), boostTimer.getValue())
        };
      case "Custom":
        return new String[] {customText.getValue()};
      default:
        return new String[0];
    }
  }

  private void debugMessage(String message) {
    if (debugMessage.getValue()) {
      ChatUtil.display(message);
    }
  }

  private void runAttack() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    Entity aimedTarget = mc.objectMouseOver == null ? null : mc.objectMouseOver.entityHit;
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    Entity target = aimedTarget != null ? aimedTarget : (killAura == null ? null : killAura.getTarget());
    if (target == null) return;

    double dist = BackTrackUtil.getDistanceToEntityBox(target);
    if (dist > attackMaxRange.getValue()) return;

    if (onlyAttackWhenNotReachedCPSLimit.getValue()
        && CPSCounter.getCPS(CPSCounter.MouseButton.LEFT) >= CPSLimit.getValue()) {
      return;
    }

    if (attackCounter >= attackCount.getValue()) return;

    boolean shouldKeepSprint =
        keepSprint.getValue()
            && player.hurtTime >= allowKeepSprintHurtTime.getValue()
            && player.hurtTime <= allowKeepSprintHurtTime.getSecondValue();

    SomeUtil.runAttack(
        shouldKeepSprint,
        attackMaxRange.getValue(),
        1,
        target,
        true,
        swingMode.getModeString(),
        false,
        false,
        "Attacked",
        false,
        null,
        null,
        1.0f);

    attackCounter++;
    debugMessage("Attacked");
  }

  private void calculateBoostPrediction(EntityPlayer player, Entity target) {
    double toTargetX = target.posX - player.posX;
    double toTargetZ = target.posZ - player.posZ;
    double distance = Math.sqrt(toTargetX * toTargetX + toTargetZ * toTargetZ);

    double dirX = toTargetX / distance;
    double dirZ = toTargetZ / distance;

    double ticks = boostTime.getValue() / 50.0;
    double totalMoveDistance = 0.1 * boostTimer.getValue() * ticks;

    double maxMoveDistance = Math.min(distance, totalMoveDistance);

    double predictedX = player.posX + dirX * maxMoveDistance;
    double predictedY = player.posY;
    double predictedZ = player.posZ + dirZ * maxMoveDistance;

    predictedPlayerPosition = new Vec3(predictedX, predictedY, predictedZ);
  }

  private void drawPredictionLine(
      EntityPlayer player, Vec3 predictedPos, double viewerX, double viewerY, double viewerZ) {
    Vec3 currentPos = new Vec3(player.posX, player.posY, player.posZ);

    GL11.glLineWidth(predictionLineWidth.getValue());
    Color color = new Color(predictionLineColor.getValue());
    GL11.glColor4f(
        color.getRed() / 255f,
        color.getGreen() / 255f,
        color.getBlue() / 255f,
        150 / 255f);

    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex3d(
        currentPos.xCoord - viewerX,
        currentPos.yCoord - viewerY,
        currentPos.zCoord - viewerZ);
    GL11.glVertex3d(
        predictedPos.xCoord - viewerX,
        predictedPos.yCoord - viewerY,
        predictedPos.zCoord - viewerZ);
    GL11.glEnd();
  }

  private void drawPlayerBox(
      Vec3 position, double viewerX, double viewerY, double viewerZ, int colorInt, int alpha, String label) {
    double x = position.xCoord - viewerX;
    double y = position.yCoord - viewerY;
    double z = position.zCoord - viewerZ;

    Color color = new Color(colorInt);
    GL11.glColor4f(
        color.getRed() / 255f,
        color.getGreen() / 255f,
        color.getBlue() / 255f,
        alpha / 255f);

    double width = 0.3;
    double height = 1.8;

    RenderUtil.drawBoundingBox(
        new AxisAlignedBB(x - width, y, z - width, x + width, y + height, z + width),
        color.getRed(),
        color.getGreen(),
        color.getBlue(),
        color.getAlpha(),
        1f);
  }
}