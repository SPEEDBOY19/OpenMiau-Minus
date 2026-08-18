package miau.util.misc;

import java.util.function.BooleanSupplier;
import miau.Miau;
import miau.event.impl.PacketEvent;
import miau.mixin.IAccessorEntity;
import miau.module.modules.combat.KillAura;
import miau.module.modules.combat.NewVelocity;
import miau.module.modules.movement.KeepSprint;
import miau.util.client.ChatUtil;
import miau.util.network.PacketUtil;
import miau.util.player.CombatTargeting;
import miau.util.player.PlayerUtil;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;

/** Port of FB's SomeUtil used by NewVelocity/TimerRange. */
public final class SomeUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private SomeUtil() {}

  public static double roundToPlacesIfNeeded(double value) {
    return roundToPlacesIfNeeded(value, 5);
  }

  public static double roundToPlacesIfNeeded(double value, int places) {
    int scale = Math.max(0, Math.min(places, 15));
    if (Double.isNaN(value) || Double.isInfinite(value)) return value;
    if (Math.abs(value) < 1e-14) return value;
    if (Math.abs(value - 1.0) < 1e-14) return value;
    if (isAlreadyRounded(value, scale)) return value;
    double factor = Math.pow(10.0, scale);
    double scaled = value * factor;
    if (Double.isFinite(scaled)) {
      return Math.round(scaled) / factor;
    }
    return value;
  }

  private static boolean isAlreadyRounded(double value, int scale) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return true;
    double factor = Math.pow(10.0, scale);
    double scaled = value * factor;
    if (!Double.isFinite(scaled)) return true;
    double rounded = Math.round(scaled);
    return Math.abs(scaled - rounded) < 1e-8;
  }

  public static void reduceXZ(double factor) {
    reduceXZ(factor, null, null, null);
  }

  public static void reduceXZ(double factor, Integer hurtTimeMin, Integer hurtTimeMax) {
    reduceXZ(factor, hurtTimeMin, hurtTimeMax, null);
  }

  public static void reduceXZ(double factor, Integer hurtTimeMin, Integer hurtTimeMax, Integer setScale) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (hurtTimeMin == null
        || (player.hurtTime >= hurtTimeMin
            && (hurtTimeMax == null || player.hurtTime <= hurtTimeMax))) {
      double adjustedFactor = roundToPlacesIfNeeded(factor, setScale == null ? 5 : setScale);
      player.motionX *= adjustedFactor;
      player.motionZ *= adjustedFactor;
    }
  }

  public static void reduceY(double factor) {
    reduceY(factor, null, null, null);
  }

  public static void reduceY(double factor, Integer hurtTimeMin, Integer hurtTimeMax) {
    reduceY(factor, hurtTimeMin, hurtTimeMax, null);
  }

  public static void reduceY(double factor, Integer hurtTimeMin, Integer hurtTimeMax, Integer setScale) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (hurtTimeMin == null
        || (player.hurtTime >= hurtTimeMin
            && (hurtTimeMax == null || player.hurtTime <= hurtTimeMax))) {
      double adjustedFactor = roundToPlacesIfNeeded(factor, setScale == null ? 5 : setScale);
      player.motionY *= adjustedFactor;
    }
  }

  public static void setMotion(Double xMotion, Double yMotion, Double zMotion) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (xMotion != null) player.motionX = xMotion;
    if (yMotion != null) player.motionY = yMotion;
    if (zMotion != null) player.motionZ = zMotion;
  }

  public static void changeSprint(boolean setState, boolean sendPacketToServer) {
    changeSprint(setState, sendPacketToServer, false);
  }

  public static void changeSprint(boolean setState, boolean sendPacketToServer, boolean forceChange) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (forceChange) {
      if (sendPacketToServer) {
        if (player.isSprinting() != setState) {
          player.setSprinting(setState);
        }
        PacketUtil.sendPacket(
            new C0BPacketEntityAction(
                player,
                setState
                    ? C0BPacketEntityAction.Action.START_SPRINTING
                    : C0BPacketEntityAction.Action.STOP_SPRINTING));
      } else {
        if (player.isSprinting() != setState) {
          player.setSprinting(setState);
        }
      }
      return;
    }
    player.setSprinting(setState);
    if (!sendPacketToServer) return;
    PacketUtil.sendPacket(
        new C0BPacketEntityAction(
            player,
            setState
                ? C0BPacketEntityAction.Action.START_SPRINTING
                : C0BPacketEntityAction.Action.STOP_SPRINTING));
  }

  public static void changeTimer(float speed) {
    ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed = speed;
  }

  public static boolean keepingSprint() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    KeepSprint keepSprint = (KeepSprint) Miau.moduleManager.modules.get(KeepSprint.class);
    if (killAura != null && killAura.isEnabled()) return true;
    if (NewVelocity.canCancelHitSlow) return true;
    if (keepSprint != null && keepSprint.isEnabled()) return true;
    if (mc.thePlayer.hurtTime == 0) return false;
    if (isInBadEnvironment()) return false;
    if (mc.thePlayer.isPotionActive(Potion.moveSlowdown)) return false;
    if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) return false;
    if (mc.thePlayer.isSprinting()) return true;
    return true;
  }

  public static boolean runAttack() {
    return runAttack(
        false, 3.0f, 1, null, true, "Packet", false, false, "Attacked", false, null, null, 1.0f);
  }

  public static boolean runAttack(boolean keepSprint) {
    return runAttack(
        keepSprint, 3.0f, 1, null, true, "Packet", false, false, "Attacked", false, null, null, 1.0f);
  }

  public static boolean runAttack(boolean keepSprint, boolean fakeSwing, int attackCount, boolean silentAttack) {
    return runAttack(
        keepSprint, 3.0f, attackCount, null, true, "Packet", fakeSwing, false, "Attacked", silentAttack, null, null, 1.0f);
  }

  public static boolean runAttack(
      boolean keepSprint,
      float maxDistance,
      int attackCount,
      Entity attackTarget,
      boolean ignoreBlocking,
      String swingMode,
      boolean fakeSwing,
      boolean debugMessage,
      String debugMessageString,
      boolean silentAttack,
      Double extraReduceXZ,
      Double extraReduceY,
      float attackChance) {
    int trulyAttack = 0;
    boolean shouldSwingNormal = "Normal".equals(swingMode);
    boolean shouldSwingPacket = "Packet".equals(swingMode) || swingMode == null;
    boolean shouldSwingOff = "Off".equals(swingMode);

    Runnable swingAction =
        () -> {
          if (shouldSwingNormal) {
            if (mc.thePlayer != null) mc.thePlayer.swingItem();
          } else if (shouldSwingPacket) {
            PacketUtil.sendPacket(new C0APacketAnimation());
          }
        };

    final Entity target;
    if (attackTarget != null) {
      target = attackTarget;
    } else if (mc.objectMouseOver != null) {
      target = mc.objectMouseOver.entityHit;
    } else {
      target = null;
    }

    if (target == null) {
      if (fakeSwing) swingAction.run();
      return false;
    }

    double distance = RotationUtil.distanceFromEyeToClosestOnAABB(target);
    boolean withinRange = distance < maxDistance;

    boolean playerIsBlocking = mc.thePlayer.isBlocking();
    if (playerIsBlocking && !ignoreBlocking) {
      return false;
    }

    boolean attackPerformed = false;

    if (withinRange) {
      for (int i = 0; i < attackCount; i++) {
        if (Math.random() > attackChance) continue;
        if (silentAttack) {
          SilentAttackManager.withSilentAttack(
              () -> {
                attackEntityWithModifiedSprint(target, !keepSprint, swingAction);
                if (extraReduceXZ != null) reduceXZ(extraReduceXZ);
                if (extraReduceY != null) reduceY(extraReduceY);
              });
        } else {
          attackEntityWithModifiedSprint(target, !keepSprint, swingAction);
          if (extraReduceXZ != null) reduceXZ(extraReduceXZ);
          if (extraReduceY != null) reduceY(extraReduceY);
        }
        CPSCounter.registerClick(CPSCounter.MouseButton.LEFT);
        attackPerformed = true;
        trulyAttack++;
      }

      if (debugMessage) {
        ChatUtil.display("%s x%s", debugMessageString, trulyAttack);
      }
      return attackPerformed;
    } else if (fakeSwing) {
      swingAction.run();
    }
    return false;
  }

  private static void attackEntityWithModifiedSprint(Entity target, boolean cancelHitSlow, Runnable swingAction) {
    boolean wasSprinting = mc.thePlayer.isSprinting();
    if (cancelHitSlow || keepingSprint()) {
      mc.thePlayer.setSprinting(true);
    } else {
      mc.thePlayer.setSprinting(false);
    }
    PlayerUtil.attackEntity(target);
    swingAction.run();
    if (wasSprinting) {
      mc.thePlayer.setSprinting(true);
    }
  }

  public static boolean isHurting() {
    return mc.thePlayer != null && mc.thePlayer.hurtTime > 0;
  }

  public static boolean isHurting(Boolean checkPacket, PacketEvent event) {
    if (mc.thePlayer == null) return false;
    if (Boolean.TRUE.equals(checkPacket) && event != null) {
      return event.getPacket() instanceof S12PacketEntityVelocity
          && ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId()
          && mc.thePlayer.hurtTime > 0;
    }
    return mc.thePlayer.hurtTime > 0;
  }

  public static boolean isFalling() {
    return mc.thePlayer != null && !mc.thePlayer.onGround && mc.thePlayer.motionY < 0.0;
  }

  public static boolean isInBadEnvironment() {
    return ((IAccessorEntity) mc.thePlayer).getIsInWeb()
        || mc.thePlayer.isInLava()
        || mc.thePlayer.isBurning()
        || mc.thePlayer.isInWater()
        || mc.thePlayer.isRiding();
  }

  public static double bps() {
    return Math.sqrt(
            mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ)
        * 20.0;
  }

  public static double bpt() {
    return Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
  }

  public static double velocityX() {
    return mc.thePlayer.motionX;
  }

  public static double velocityY() {
    return mc.thePlayer.motionY;
  }

  public static double velocityZ() {
    return mc.thePlayer.motionZ;
  }

  public static void setBPSTo(double targetBPS) {
    if (bps() != 0.0) reduceXZ(targetBPS / bps());
  }

  public static double getCurrentWeaponDamage(boolean isCritical) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return 1.0;
    ItemStack heldItem = player.getHeldItem();
    if (heldItem == null) return 1.0;
    double attackBonus = getAttackDamage(heldItem);
    double damage = isCritical ? 1.5 + attackBonus * 1.5 : 1.0 + attackBonus;

    net.minecraft.potion.PotionEffect strengthEffect = player.getActivePotionEffect(Potion.damageBoost);
    if (strengthEffect != null) {
      int amplifier = strengthEffect.getAmplifier();
      double strengthMultiplier = 1.0 + (amplifier + 1) * 1.3;
      damage *= strengthMultiplier;
    }

    net.minecraft.potion.PotionEffect weaknessEffect = player.getActivePotionEffect(Potion.weakness);
    if (weaknessEffect != null) {
      damage -= (weaknessEffect.getAmplifier() + 1) * 0.5;
    }

    return Math.max(0.5, damage);
  }

  private static double getAttackDamage(ItemStack itemStack) {
    if (itemStack == null || itemStack.getItem() == null) return 0.0;
    if (itemStack.getItem() instanceof ItemSword) {
      return 4.0 + ((ItemSword) itemStack.getItem()).getDamageVsEntity();
    }
    return 1.0;
  }

  public static void safeJump(double jumpStrength) {
    if (mc.thePlayer == null) return;
    if (!mc.thePlayer.onGround || mc.gameSettings.keyBindJump.isKeyDown()) return;
    mc.thePlayer.jumpMovementFactor = (float) jumpStrength;
    mc.thePlayer.jump();
    mc.thePlayer.jumpMovementFactor = 0.2f;
  }

  public static double calculateAngleDifference() {
    return calculateAngleDifference(mc.thePlayer);
  }

  public static double calculateAngleDifference(EntityLivingBase entity) {
    if (entity == null) return 0.0;
    double motionX = entity.motionX;
    double motionZ = entity.motionZ;
    float playerYaw = entity.rotationYaw;
    double movementAngle = Math.toDegrees(Math.atan2(motionZ, motionX));

    double normalizedPlayerYaw = ((playerYaw % 360) + 360) % 360;

    double angleDifference = Math.abs(movementAngle - normalizedPlayerYaw);
    if (angleDifference > 180) {
      angleDifference = 360 - angleDifference;
    }
    return angleDifference;
  }

  public static boolean isSelected(Entity entity) {
    if (entity == null || entity == mc.thePlayer || entity.isDead) return false;
    if (!(entity instanceof EntityLivingBase)) return false;
    if (entity instanceof EntityPlayer) {
      return CombatTargeting.isTrackablePlayer((EntityPlayer) entity);
    }
    return ((EntityLivingBase) entity).getHealth() > 0.0f;
  }

  public static BooleanSupplier notNull() {
    return () -> true;
  }
}
