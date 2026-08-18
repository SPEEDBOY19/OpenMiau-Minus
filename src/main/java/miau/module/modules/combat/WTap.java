package miau.module.modules.combat;

import java.util.Random;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class WTap extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Normal", "Simple"});
  public final IntProperty chance = new IntProperty("Chance", 100, 0, 100, () -> this.mode.getValue() == 0);
  public final IntProperty delay = new IntProperty("Delay", 0, 0, 500, () -> this.mode.getValue() == 0);
  public final IntProperty targetHurtTime = new IntProperty("TargetHurtTime", 0, 0, 10, () -> this.mode.getValue() == 0);
  public final IntProperty ownHurtTime = new IntProperty("OwnHurtTime", 0, 0, 10, () -> this.mode.getValue() == 0);
  public final IntProperty ticksUntilBlock = new IntProperty("TicksUntilBlock", 0, 0, 2, () -> this.mode.getValue() == 0);
  public final IntProperty reSprintTicks = new IntProperty("ReSprintTicks", 1, 1, 2, () -> this.mode.getValue() == 0);
  public final IntProperty targetDistance = new IntProperty("TargetDistance", 3, 0, 5, () -> this.mode.getValue() == 0);
  public final BooleanProperty AllowJump = new BooleanProperty("AllowJump", false, () -> this.mode.getValue() == 0);
  public final BooleanProperty ADStrafe = new BooleanProperty("ADStrafe", false, () -> this.mode.getValue() == 0);
  public final IntProperty DurationTime = new IntProperty("ADStrafeDurationTick", 3, 1, 10, () -> this.ADStrafe.getValue() && this.mode.getValue() == 0);
  public final BooleanProperty restartForwardWhenBlockStop = new BooleanProperty("RestartForwardWhenBlockStop", false);
  public final IntProperty forwardTick = new IntProperty("ForwardTick", 1, 0, 10, () -> this.restartForwardWhenBlockStop.getValue());
  public final FloatProperty minEnemyRotDiffToIgnore = new FloatProperty("MinRotationDiffFromEnemyToIgnore", 180.0F, 0.0F, 180.0F, () -> this.mode.getValue() == 0);
  public final IntProperty stopDuration = new IntProperty("StopDuration", 1, 0, 10, () -> this.mode.getValue() == 1);
  public final BooleanProperty onlyGround = new BooleanProperty("OnlyGround", false);
  public final BooleanProperty onlyMove = new BooleanProperty("OnlyMove", true);
  public final BooleanProperty onlyMoveForward = new BooleanProperty("OnlyMoveForward", true);
  public final BooleanProperty onlyWhenTargetGoesBack = new BooleanProperty("OnlyWhenTargetGoesBack", false);
  public final BooleanProperty onlyWhenNotBlocking = new BooleanProperty("OnlyWhenNotBlocking", false);

  private final Random random = new Random();
  private final TimerUtil strafeTimer = new TimerUtil();
  public int forwardTicks = 0;
  private int blockInputTicks;
  private int blockTicksElapsed = 0;
  private boolean startWaiting = false;
  private boolean blockInput = false;
  private int allowInputTicks;
  private int ticksElapsed = 0;
  private int strafeDuration = 0;
  private boolean randomSide;
  private int simpleModeTicks = 0;
  private boolean wasBlockingInput = false;

  public WTap() {
    super("WTap", false);
    this.blockInputTicks = this.randomInRange(this.ticksUntilBlock);
    this.allowInputTicks = this.randomInRange(this.reSprintTicks);
    this.randomSide = this.random.nextBoolean();
  }

  @Override
  public void onEnabled() {
    this.resetState();
  }

  @Override
  public void onDisabled() {
    this.resetState();
  }

  private void resetState() {
    this.blockInput = false;
    this.startWaiting = false;
    this.blockTicksElapsed = 0;
    this.ticksElapsed = 0;
    this.forwardTicks = 0;
    this.wasBlockingInput = false;
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;
    if (!(event.getTarget() instanceof EntityLivingBase)) return;
    EntityLivingBase target = (EntityLivingBase) event.getTarget();
    double distance = this.getDistanceToEntityBox(target);

    if (!this.shouldActivateWTap(mc.thePlayer, target, this.mode.getModeString())) return;

    if (mc.thePlayer.isSprinting() && !this.blockInput && !this.startWaiting) {
      double delayMultiplier = 1.0 / (Math.abs(this.targetDistance.getValue() - distance) + 1.0);
      this.randomSide = this.random.nextBoolean();
      this.blockInputTicks = (int) (this.randomInRange(this.ticksUntilBlock) * delayMultiplier);

      this.blockInput = this.blockInputTicks == 0;

      if (!this.blockInput) {
        this.startWaiting = true;
      }

      this.allowInputTicks = (int) (this.randomInRange(this.reSprintTicks) * delayMultiplier);
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
    if (mc.thePlayer.hurtTime < this.ownHurtTime.getMinimum() || mc.thePlayer.hurtTime > this.ownHurtTime.getMaximum()) return;

    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    EntityLivingBase auraTarget = killAura != null ? killAura.getTarget() : null;
    int currentTargetHurtTime = auraTarget == null ? -1 : auraTarget.hurtTime;
    EntityLivingBase pointedEntity =
        mc.pointedEntity instanceof EntityLivingBase ? (EntityLivingBase) mc.pointedEntity : null;

    if (this.wasBlockingInput && !this.blockInput && this.restartForwardWhenBlockStop.getValue()) {
      this.forwardTicks = this.forwardTick.getValue();
    }
    this.wasBlockingInput = this.blockInput;

    if (this.forwardTicks > 0) {
      this.forwardTicks--;
    }

    if (this.blockInput) {
      if (this.ticksElapsed++ >= this.allowInputTicks) {
        this.blockInput = false;
        this.ticksElapsed = 0;
      }
    } else if (this.startWaiting) {
      this.blockInput = this.blockTicksElapsed++ >= this.blockInputTicks;
      if (this.blockInput) {
        this.startWaiting = false;
        this.blockTicksElapsed = 0;
      }
    }

    int hurtTimeToCheck;
    if (currentTargetHurtTime >= 0) {
      hurtTimeToCheck = currentTargetHurtTime;
    } else if (pointedEntity != null) {
      hurtTimeToCheck = pointedEntity.hurtTime;
    } else {
      return;
    }
    if (hurtTimeToCheck == 10) {
      this.simpleModeTicks = this.stopDuration.getValue();
    }
  }

  private boolean shouldActivateWTap(EntityPlayerSP player, EntityLivingBase target, String mode) {
    if (this.onlyGround.getValue() && !player.onGround) return false;

    if (this.onlyMove.getValue()
        && (!MoveUtil.isMoving() || (this.onlyMoveForward.getValue() && player.movementInput.moveStrafe != 0.0F))) {
      return false;
    }

    if (mode.equals("Normal")) {
      if (target.hurtTime < this.targetHurtTime.getMinimum() || target.hurtTime > this.targetHurtTime.getMaximum()) return false;
      if (player.hurtTime < this.ownHurtTime.getMinimum() || player.hurtTime > this.ownHurtTime.getMaximum()) return false;

      TimerUtil delayTimer = new TimerUtil();
      delayTimer.reset();
      if (!delayTimer.hasTimeElapsed(this.randomInRange(this.delay))) return false;
      if (this.random.nextInt(100) > this.chance.getValue()) return false;

      float rotationToPlayer = this.getYawToTarget(target);
      float angleDifferenceToPlayer = Math.abs(this.angleDifference(rotationToPlayer, target.rotationYaw));
      if (angleDifferenceToPlayer > this.minEnemyRotDiffToIgnore.getValue()
          && !target.getEntityBoundingBox().isVecInside(mc.thePlayer.getPositionEyes(1.0F))) {
        return false;
      }

      if (this.onlyWhenTargetGoesBack.getValue()) {
        Vec3 pos = new Vec3(
            target.posX - target.lastTickPosX,
            target.posY - target.lastTickPosY,
            target.posZ - target.lastTickPosZ);
        AxisAlignedBB box = target.getEntityBoundingBox().offset(pos.xCoord, pos.yCoord, pos.zCoord);
        double distanceBasedOnMotion = this.getDistanceToBox(box);
        if (distanceBasedOnMotion >= this.getDistanceToEntityBox(target)) return false;
      }
    }

    return true;
  }

  public boolean shouldBlockInput() {
    if (this.onlyWhenNotBlocking.getValue() && mc.thePlayer != null && mc.thePlayer.isBlocking()) return false;
    if (this.mode.getModeString().equals("Normal")) {
      if (this.isEnabled() && this.blockInput) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return false;
        if (this.strafeDuration == 0 && this.ADStrafe.getValue()) {
          this.strafeDuration = this.DurationTime.getValue();
          this.strafeTimer.reset();
          player.movementInput.moveStrafe = 0.0F;
        }
        if (this.strafeTimer.hasTimeElapsed(this.strafeDuration) && this.ADStrafe.getValue()) {
          player.movementInput.moveStrafe = 0.0F;
          this.strafeDuration = 0;
        } else if ((mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown()) && this.ADStrafe.getValue()) {
          if (mc.gameSettings.keyBindLeft.isKeyDown() && this.ADStrafe.getValue()) {
            player.movementInput.moveStrafe = -1.0F;
          } else if (mc.gameSettings.keyBindRight.isKeyDown() && this.ADStrafe.getValue()) {
            player.movementInput.moveStrafe = 1.0F;
          }
        } else if (this.randomSide && this.ADStrafe.getValue()) {
          player.movementInput.moveStrafe = -1.0F;
        } else if (this.ADStrafe.getValue()) {
          player.movementInput.moveStrafe = 1.0F;
        }
        if (this.AllowJump.getValue() && player.onGround) {
          player.jump();
        }
        return true;
      }
    } else if (this.mode.getModeString().equals("Simple")) {
      if (this.simpleModeTicks != 0) {
        this.simpleModeTicks--;
        return true;
      }
    }
    return false;
  }

  private int randomInRange(IntProperty property) {
    int min = property.getMinimum();
    int max = property.getMaximum();
    if (max <= min) return min;
    return min + this.random.nextInt(max - min + 1);
  }

  private double getDistanceToEntityBox(Entity entity) {
    return this.getDistanceToBox(entity.getEntityBoundingBox());
  }

  private double getDistanceToBox(AxisAlignedBB box) {
    if (mc.thePlayer == null) return Double.MAX_VALUE;
    double x = MathHelper.clamp_double(mc.thePlayer.posX, box.minX, box.maxX);
    double y = MathHelper.clamp_double(mc.thePlayer.posY, box.minY, box.maxY);
    double z = MathHelper.clamp_double(mc.thePlayer.posZ, box.minZ, box.maxZ);
    return Math.sqrt(mc.thePlayer.getDistanceSq(x, y, z));
  }

  private float angleDifference(float a, float b) {
    return MathHelper.wrapAngleTo180_float(a - b);
  }

  private float getYawToTarget(EntityLivingBase target) {
    AxisAlignedBB box = target.getEntityBoundingBox();
    double cx = (box.minX + box.maxX) / 2.0;
    double cz = (box.minZ + box.maxZ) / 2.0;
    double dx = cx - mc.thePlayer.posX;
    double dz = cz - mc.thePlayer.posZ;
    return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
  }
}
