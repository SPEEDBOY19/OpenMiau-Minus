package miau.module.modules.ghost;

import com.google.common.base.CaseFormat;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;

public class MoreKB extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"WTAP"});
  public final IntProperty reSprintDelay = new IntProperty("resprint-delay", 0, 0, 9);
  public final BooleanProperty notWhenHurt = new BooleanProperty("not-when-hurt", false);
  public final IntProperty minTargetTicksFromGround =
      new IntProperty("min-target-ticks-from-ground", 5, 0, 12);

  private final TimerUtil attackTimer = new TimerUtil();
  private EntityLivingBase target;
  private final TimerUtil reSprintTimer = new TimerUtil();
  private boolean resyncNeeded;
  private int nextSprintTime;
  private int selfHurtTimeOnAttack;
  private int targetOffGroundTicks;

  public MoreKB() {
    super("MoreKB", false);
  }

  @Override
  public void onEnabled() {
    target = null;
    resyncNeeded = false;
    targetOffGroundTicks = 0;
  }

  @Override
  public void onDisabled() {
    target = null;
    resyncNeeded = false;
  }

  @EventTarget
  public void onAttack(AttackEvent e) {
    if (!isEnabled()) return;
    if (e.getTarget() instanceof EntityLivingBase) {
      if (e.getTarget() == target) {
        attackTimer.reset();
      } else {
        target = (EntityLivingBase) e.getTarget();
        attackTimer.reset();
      }
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent e) {
    if (!isEnabled()) return;
    if (e.getType() == EventType.PRE) {
      if (target == null || mc.thePlayer.getDistanceToEntity(target) > 4.5) {
        target = getTarget(4.5);
      }

      if (target != null) {
        if (target.onGround) {
          targetOffGroundTicks = 0;
        } else {
          targetOffGroundTicks++;
        }

        if (target.hurtTime == 10
            && !attackTimer.hasTimeElapsed(200L + getPing())
            && mc.thePlayer.isSprinting()) {
          selfHurtTimeOnAttack = mc.thePlayer.hurtTime;
          resyncNeeded = true;
          nextSprintTime = reSprintDelay.getValue();
          reSprintTimer.reset();
        }
      }
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent e) {
    if (!isEnabled()) return;
    if (shouldReset()) {
      mc.thePlayer.movementInput.moveForward = 0.0f;
      mc.thePlayer.movementInput.moveStrafe = 0.0f;
    }
  }

  private boolean shouldReset() {
    if (resyncNeeded && reSprintTimer.hasTimeElapsed(nextSprintTime * 50L)) {
      resyncNeeded = false;

      boolean hurtTime = selfHurtTimeOnAttack != 0 && mc.thePlayer.hurtTime <= 2;
      boolean crit =
          mc.thePlayer.fallDistance > 0.0F
              && !mc.thePlayer.onGround
              && !mc.thePlayer.isOnLadder()
              && !mc.thePlayer.isInWater()
              && !mc.thePlayer.isPotionActive(Potion.blindness)
              && mc.thePlayer.ridingEntity == null;
      boolean strictHurtTime = mc.thePlayer.hurtTime != 0 && notWhenHurt.getValue();
      boolean targetAir =
          mc.thePlayer.hurtTime != 0 && targetOffGroundTicks < minTargetTicksFromGround.getValue();

      if (hurtTime || crit || strictHurtTime || targetAir) {
        return false;
      }

      return true;
    }

    return false;
  }

  private int getPing() {
    if (mc.isSingleplayer() || mc.getNetHandler() == null || mc.thePlayer == null) return 0;
    net.minecraft.client.network.NetworkPlayerInfo info =
        mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
    return info != null ? info.getResponseTime() : 0;
  }

  private EntityLivingBase getTarget(double range) {
    EntityLivingBase bestTarget = null;
    double bestDistance = range;
    for (net.minecraft.entity.Entity entity : mc.theWorld.loadedEntityList) {
      if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
        double dist = mc.thePlayer.getDistanceToEntity(entity);
        if (dist <= bestDistance) {
          bestDistance = dist;
          bestTarget = (EntityLivingBase) entity;
        }
      }
    }
    return bestTarget;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }

  @Override
  public void verifyValue(String value) {}
}
