package miau.module.modules.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.mixin.IAccessorMinecraft;
import miau.mixin.IAccessorS27PacketExplosion;
import miau.module.Module;
import miau.module.modules.combat.velocity.VelocityUtil;
import miau.module.modules.network.BackTrack;
import miau.notification.NotificationType;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.ChatUtil;
import miau.util.math.RandomUtil;
import miau.util.misc.BackTrackUtil;
import miau.util.misc.SomeUtil;
import miau.util.network.BlinkUtil;
import miau.util.player.RotationUtil;
import miau.util.player.SimulatedPlayer;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C12PacketUpdateSign;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public class TimerRange extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private int playerTicks = 0;
  private int smartTick = 0;
  private int cooldownTick = 0;
  private float randomRange = 0f;

  private boolean blinked = false;

  // Condition to confirm
  private boolean shouldReset = false;
  private boolean confirmTick = false;
  private boolean confirmStop = false;

  // Condition to prevent getting timer speed stuck
  private boolean confirmAttack = false;

  public final ModeProperty timerBoostMode =
      new ModeProperty("TimerMode", 2, new String[] {"Normal", "Smart", "Modern"});

  public final IntProperty ticksValue = new IntProperty("Ticks", 10, 1, 20);

  // Min & Max Boost Delay Settings
  public final FloatProperty timerBoostValue = new FloatProperty("TimerBoost", 1.5f, 0.01f, 35f);
  public final FloatProperty boostDelay = new FloatProperty("BoostDelay", 0.5f, 0.55f, 0.1f, 1f);

  // Min & Max Charged Delay Settings
  public final FloatProperty timerChargedValue = new FloatProperty("TimerCharged", 0.45f, 0.05f, 5f);
  public final FloatProperty chargedDelay = new FloatProperty("ChargedDelay", 0.75f, 0.9f, 0.1f, 1f);

  // Normal Mode Settings
  public final FloatProperty rangeValue =
      new FloatProperty("Range", 3.5f, 1f, 5f, () -> timerBoostMode.getModeString().equals("Normal"));
  public final IntProperty cooldownTickValue =
      new IntProperty(
          "CooldownTick", 10, 1, 50, () -> timerBoostMode.getModeString().equals("Normal"));

  // Smart & Modern Mode Range
  public final FloatProperty range =
      new FloatProperty(
          "Range", 2.5f, 3f, 2f, 8f, () -> !timerBoostMode.getModeString().equals("Normal"));

  public final FloatProperty scanRange =
      new FloatProperty(
          "ScanRange", 8f, 2f, 12f, () -> !timerBoostMode.getModeString().equals("Normal"));

  // Min & Max Tick Delay
  public final FloatProperty tickDelay =
      new FloatProperty(
          "TickDelay", 30f, 60f, 1f, 200f, () -> !timerBoostMode.getModeString().equals("Normal"));

  // Blink Option
  public final BooleanProperty blink = new BooleanProperty("Blink", false);

  // Prediction Settings
  public final IntProperty predictClientMovement = new IntProperty("PredictClientMovement", 2, 0, 5);
  public final FloatProperty predictEnemyPosition =
      new FloatProperty("PredictEnemyPosition", 1.5f, -1f, 2f);

  public final FloatProperty maxAngleDifference =
      new FloatProperty(
          "MaxAngleDifference", 5f, 5f, 90f, () -> timerBoostMode.getModeString().equals("Modern"));

  // Mark Option
  public final ModeProperty markMode =
      new ModeProperty(
          "Mark",
          0,
          new String[] {"Off", "Box", "Platform"},
          () -> timerBoostMode.getModeString().equals("Modern"));
  public final BooleanProperty outline =
      new BooleanProperty(
          "Outline",
          false,
          () ->
              timerBoostMode.getModeString().equals("Modern")
                  && markMode.getModeString().equals("Box"));

  // Optional
  public final BooleanProperty onWeb = new BooleanProperty("OnWeb", false);
  public final BooleanProperty onLiquid = new BooleanProperty("onLiquid", false);
  public final BooleanProperty onForwardOnly = new BooleanProperty("OnForwardOnly", true);
  public final BooleanProperty resetOnlagBack = new BooleanProperty("ResetOnLagback", false);
  public final BooleanProperty resetOnKnockback = new BooleanProperty("ResetOnKnockback", false);
  public final BooleanProperty chatDebug =
      new BooleanProperty(
          "ChatDebug", true, () -> resetOnlagBack.getValue() || resetOnKnockback.getValue());
  public final BooleanProperty notificationDebug =
      new BooleanProperty(
          "NotificationDebug",
          false,
          () -> resetOnlagBack.getValue() || resetOnKnockback.getValue());

  public TimerRange() {
    super("TimerRange", false);
  }

  @Override
  public void onDisabled() {
    shouldResetTimer();
    BlinkUtil.unblink();

    smartTick = 0;
    cooldownTick = 0;
    playerTicks = 0;

    shouldReset = false;
    blinked = false;

    confirmTick = false;
    confirmStop = false;
    confirmAttack = false;
  }

  /**
   * Attack event (Normal & Smart Mode)
   */
  @EventTarget
  public void onAttack(AttackEvent event) {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return;

    Entity targetEntity = event.getTarget();

    if (!(targetEntity instanceof EntityLivingBase) && playerTicks >= 1) {
      shouldResetTimer();
      return;
    } else {
      confirmAttack = true;
    }

    if (targetEntity == null) return;

    double entityDistance = BackTrackUtil.getDistanceToEntityBox(targetEntity);
    int randomTickDelay = randomInt(tickDelay.getValue().intValue(), tickDelay.getSecondValue().intValue());
    boolean shouldReturn =
        BackTrack.runWithNearestTrackedDistance(targetEntity, () -> !updateDistance(targetEntity));

    if (shouldReturn || (isInWeb(player) && !onWeb.getValue()) || (isInLiquid(player) && !onLiquid.getValue())) {
      return;
    }

    smartTick++;
    cooldownTick++;

    String mode = timerBoostMode.getModeString();
    boolean shouldSlowed;
    if (mode.equals("Normal")) {
      shouldSlowed =
          cooldownTick >= cooldownTickValue.getValue() && entityDistance <= rangeValue.getValue();
    } else if (mode.equals("Smart")) {
      shouldSlowed = smartTick >= randomTickDelay && entityDistance <= randomRange;
    } else {
      shouldSlowed = false;
    }

    if (shouldSlowed && confirmAttack) {
      if (updateDistance(targetEntity)) {
        confirmAttack = false;
        playerTicks = ticksValue.getValue();
        cooldownTick = 0;
        smartTick = 0;
      }
    } else {
      shouldResetTimer();
    }
  }

  /**
   * Move event (Modern Mode) + timer update
   */
  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return;

    if (timerBoostMode.getModeString().equals("Modern")) {
      handleModernMove(player);
    }

    handleTimerUpdate();
  }

  /**
   * Update event (blink flush on POST)
   */
  @EventTarget
  public void onPostUpdate(UpdateEvent event) {
    if (event.getType() != EventType.POST) return;
    if (blink.getValue()) {
      BlinkUtil.syncSent();
    }
  }

  /**
   * World event (clear packets on disconnect)
   */
  @EventTarget
  public void onWorld(LoadWorldEvent event) {
    if (blink.getValue()) {
      BlinkUtil.clear();
    }
  }

  private void handleModernMove(EntityPlayerSP player) {
    Entity nearbyEntity = getNearestEntityInRange();
    if (nearbyEntity == null) return;

    int randomTickDelay = randomInt(tickDelay.getValue().intValue(), tickDelay.getSecondValue().intValue());

    boolean shouldReturn =
        BackTrack.runWithNearestTrackedDistance(nearbyEntity, () -> !updateDistance(nearbyEntity));

    if (shouldReturn
        || (isInWeb(player) && !onWeb.getValue())
        || (isInLiquid(player) && !onLiquid.getValue())) {
      return;
    }

    if (isPlayerMoving()) {
      smartTick++;

      if (smartTick >= randomTickDelay) {
        confirmTick = true;
        smartTick = 0;
      }
    } else {
      smartTick = 0;
    }

    if (isPlayerMoving() && !confirmStop) {
      if (VelocityUtil.isLookingOnEntities(nearbyEntity, maxAngleDifference.getValue())) {
        double entityDistance = BackTrackUtil.getDistanceToEntityBox(nearbyEntity);
        if (confirmTick && entityDistance >= randomRange && entityDistance <= range.getSecondValue()) {
          if (updateDistance(nearbyEntity)) {
            playerTicks = ticksValue.getValue();
            confirmTick = false;
          }
        }
      } else {
        shouldResetTimer();
      }
    } else {
      shouldResetTimer();
    }
  }

  private void handleTimerUpdate() {
    // Randomize the timer & charged delay a bit, to bypass some AntiCheat
    float timerBoost = randomFloat(boostDelay.getValue(), boostDelay.getSecondValue());
    float charged = randomFloat(chargedDelay.getValue(), chargedDelay.getSecondValue());

    if (mc.thePlayer != null && mc.theWorld != null) {
      randomRange = randomFloat(range.getValue(), range.getSecondValue());
    }

    if (playerTicks <= 0 || confirmStop) {
      shouldResetTimer();

      if (blink.getValue() && blinked) {
        BlinkUtil.unblink();
        blinked = false;
      }

      return;
    }

    double tickProgress = (double) playerTicks / (double) ticksValue.getValue();
    float playerSpeed;
    if (tickProgress < timerBoost) {
      playerSpeed = timerBoostValue.getValue();
    } else if (tickProgress < charged) {
      playerSpeed = timerChargedValue.getValue();
    } else {
      playerSpeed = 1f;
    }

    float speedAdjustment = playerSpeed >= 0 ? playerSpeed : 1f + ticksValue.getValue() - playerTicks;
    float adjustedTimerSpeed = Math.max(speedAdjustment, 0f);

    ((IAccessorMinecraft) mc).getTimer().timerSpeed = adjustedTimerSpeed;

    playerTicks--;
  }

  /**
   * Render event (Mark)
   */
  @EventTarget
  public void onRender3D(Render3DEvent event) {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return;

    if (!timerBoostMode.getModeString().equals("Modern")) return;

    Entity nearbyEntity = getNearestEntityInRange();
    if (nearbyEntity == null) return;

    double entityDistance = BackTrackUtil.getDistanceToEntityBox(nearbyEntity);

    if (entityDistance > getScanRange()) return;

    Color color =
        VelocityUtil.isLookingOnEntities(nearbyEntity, maxAngleDifference.getValue())
            ? new Color(37, 126, 255, 70)
            : new Color(210, 60, 60, 70);

    String mark = markMode.getModeString();
    if (!mark.equals("Off")) {
      if (mark.equals("Box")) {
        drawBoxMark(nearbyEntity, color, outline.getValue());
      } else if (mark.equals("Platform")) {
        drawPlatformMark(nearbyEntity, color);
      }
    }
  }

  /**
   * Check if player is moving
   */
  private boolean isPlayerMoving() {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return false;
    if (!onForwardOnly.getValue()) {
      return player.moveForward != 0f || player.moveStrafing != 0f;
    }
    return player.moveForward != 0f && player.moveStrafing == 0f;
  }

  /**
   * Find the nearest entity in range.
   */
  private Entity getNearestEntityInRange() {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return null;

    Entity best = null;
    double bestDist = Double.MAX_VALUE;
    for (EntityLivingBase entity : getTargets()) {
      double dist = BackTrackUtil.getDistanceToEntityBox(entity);
      if (dist < bestDist) {
        bestDist = dist;
        best = entity;
      }
    }
    return best;
  }

  private List<EntityLivingBase> getTargets() {
    List<EntityLivingBase> targets = new ArrayList<>();
    if (mc.theWorld == null || mc.thePlayer == null) return targets;

    for (Entity entity : mc.theWorld.loadedEntityList) {
      if (!(entity instanceof EntityLivingBase)) continue;
      EntityLivingBase living = (EntityLivingBase) entity;
      if (!SomeUtil.isSelected(living)) continue;

      boolean inRange =
          BackTrack.runWithNearestTrackedDistance(
              living,
              () -> {
                double dist = BackTrackUtil.getDistanceToEntityBox(living);
                String mode = timerBoostMode.getModeString();
                if (mode.equals("Normal")) {
                  return dist <= rangeValue.getValue();
                }
                if (mode.equals("Smart") || mode.equals("Modern")) {
                  return dist <= getScanRange() + randomRange;
                }
                return false;
              });

      if (inRange) {
        targets.add(living);
      }
    }
    return targets;
  }

  private boolean updateDistance(Entity entity) {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null || entity == null) return false;

    Vec3 prediction = predictEntityPosition(entity);

    AxisAlignedBB boundingBox = entity.getEntityBoundingBox().offset(prediction.xCoord, prediction.yCoord, prediction.zCoord);

    Vec3 currPos = new Vec3(player.posX, player.posY, player.posZ);
    Vec3 oldPos = new Vec3(player.prevPosX, player.prevPosY, player.prevPosZ);

    SimulatedPlayer simPlayer = SimulatedPlayer.fromClientPlayer(player.movementInput);

    for (int i = 0; i < predictClientMovement.getValue() + 1; i++) {
      simPlayer.tick();
    }

    BackTrackUtil.setPositionAndPrevious(player, simPlayer.getPos());

    AxisAlignedBB originalBox = entity.getEntityBoundingBox();
    boolean result;
    try {
      entity.setEntityBoundingBox(boundingBox);

      float lookRange;
      String mode = timerBoostMode.getModeString();
      if (mode.equals("Normal")) {
        lookRange = rangeValue.getValue();
      } else {
        lookRange = randomRange;
      }

      Reach reach = (Reach) Miau.moduleManager.modules.get(Reach.class);
      float attackRange = reach != null && reach.isEnabled() ? reach.range.getValue() : 3f;

      result =
          RotationUtil.hasValidAimPoint(
              entity, 50, 50, Math.max(lookRange, attackRange), false, false);
    } finally {
      entity.setEntityBoundingBox(originalBox);
      BackTrackUtil.setPositionAndPrevious(player, currPos, oldPos);
    }

    return result;
  }

  private Vec3 predictEntityPosition(Entity entity) {
    double multiplier = 2 + predictEnemyPosition.getValue();
    return new Vec3(
        (entity.posX - entity.prevPosX) * multiplier,
        (entity.posY - entity.prevPosY) * multiplier,
        (entity.posZ - entity.prevPosZ) * multiplier);
  }

  /**
   * Separate condition to make it cleaner
   */
  private void shouldResetTimer() {
    Entity nearestEntity = getNearestEntityInRange();

    if (nearestEntity == null || nearestEntity.isDead) {
      if (!shouldReset) {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
        shouldReset = true;
      }
    } else {
      if (!shouldReset && ((IAccessorMinecraft) mc).getTimer().timerSpeed != 1f) {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
        shouldReset = true;
      } else {
        shouldReset = false;
      }
    }
  }

  /**
   * Lagback Reset is Inspired from Nextgen TimerRange
   * Reset Timer on Lagback & Knockback.
   */
  @EventTarget
  public void onPacket(PacketEvent event) {
    if (mc.thePlayer == null || mc.thePlayer.isDead) return;

    Packet<?> packet = event.getPacket();

    if (blink.getValue()) {
      if (playerTicks > 0 && !blinked) {
        BlinkUtil.blink(event, false, true);
        blinked = true;
      }

      if (blinked) {
        if (packet instanceof S08PacketPlayerPosLook
            || packet instanceof C07PacketPlayerDigging
            || packet instanceof C12PacketUpdateSign
            || packet instanceof C19PacketResourcePackStatus) {
          BlinkUtil.unblink();
          return;
        }

        if (packet instanceof S27PacketExplosion) {
          S27PacketExplosion explosion = (S27PacketExplosion) packet;
          IAccessorS27PacketExplosion accessor = (IAccessorS27PacketExplosion) explosion;
          if (accessor.getMotionX() != 0f || accessor.getMotionY() != 0f || accessor.getMotionZ() != 0f) {
            BlinkUtil.unblink();
            return;
          }
        }

        if (packet instanceof S06PacketUpdateHealth) {
          S06PacketUpdateHealth health = (S06PacketUpdateHealth) packet;
          if (health.getHealth() < mc.thePlayer.getHealth()) {
            BlinkUtil.unblink();
            return;
          }
        }
      }
    }

    // Check for lagback
    if (resetOnlagBack.getValue() && packet instanceof S08PacketPlayerPosLook) {
      shouldResetTimer();

      if (shouldReset) {
        if (chatDebug.getValue()) {
          ChatUtil.display("%s", "Lagback Received | Timer Reset");
        }
        if (notificationDebug.getValue()) {
          Miau.notificationManager
              .builder(NotificationType.INFO)
              .duration(1000)
              .title(this.getName())
              .description("Lagback Received - Resetting Timer")
              .buildAndPublish();
        }

        shouldReset = false;
      }
    }

    // Check for knockback
    if (resetOnKnockback.getValue()
        && packet instanceof S12PacketEntityVelocity
        && mc.thePlayer.getEntityId() == ((S12PacketEntityVelocity) packet).getEntityID()) {
      shouldResetTimer();

      if (shouldReset) {
        if (chatDebug.getValue()) {
          ChatUtil.display("%s", "Knockback Received | Timer Reset");
        }
        if (notificationDebug.getValue()) {
          Miau.notificationManager
              .builder(NotificationType.INFO)
              .duration(1000)
              .title(this.getName())
              .description("Knockback Received - Resetting Timer")
              .buildAndPublish();
        }

        shouldReset = false;
      }
    }
  }

  private float getScanRange() {
    return Math.max(scanRange.getValue(), range.getSecondValue());
  }

  private void drawBoxMark(Entity entity, Color color, boolean outline) {
    RenderUtil.drawEntityBox(entity, color.getRed(), color.getGreen(), color.getBlue());
    if (outline) {
      RenderUtil.drawEntityBoundingBox(
          entity, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), 2.0f, 0.1);
    }
  }

  private void drawPlatformMark(Entity entity, Color color) {
    AxisAlignedBB box = entity.getEntityBoundingBox();
    double vx = mc.getRenderManager().viewerPosX;
    double vy = mc.getRenderManager().viewerPosY;
    double vz = mc.getRenderManager().viewerPosZ;
    AxisAlignedBB renderBox = box.offset(-vx, -vy, -vz);
    AxisAlignedBB platform =
        new AxisAlignedBB(
            renderBox.minX,
            renderBox.maxY + 0.2,
            renderBox.minZ,
            renderBox.maxX,
            renderBox.maxY + 0.26,
            renderBox.maxZ);
    RenderUtil.drawFilledBox(platform, color.getRed(), color.getGreen(), color.getBlue());
  }

  private static boolean isInWeb(EntityPlayerSP player) {
    return ((IAccessorEntity) player).getIsInWeb();
  }

  private static boolean isInLiquid(EntityPlayerSP player) {
    return player.isInWater() || player.isInLava();
  }

  private static int randomInt(int min, int max) {
    if (max <= min) return min;
    return RandomUtil.nextInt(min, max);
  }

  private static float randomFloat(float min, float max) {
    if (max <= min) return min;
    return RandomUtil.nextFloat(min, max);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {timerBoostMode.getModeString()};
  }
}
