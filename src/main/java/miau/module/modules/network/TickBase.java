package miau.module.modules.network;

import java.util.List;
import java.util.stream.Collectors;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;

public class TickBase extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty onlyKillaura = new BooleanProperty("Only KillAura", true);
  public final FloatProperty lagRange = new FloatProperty("Lag Range", 8f, 5f, 15f);
  public final FloatProperty targetRange = new FloatProperty("Target Range", 20f, 5f, 50f);
  public final FloatProperty minRange = new FloatProperty("Min Range", 3f, 1f, 10f);
  public final FloatProperty revertRange = new FloatProperty("Revert Range", 4f, 1f, 10f);
  public final IntProperty maxBalance = new IntProperty("Max Balance", 50, 10, 200);
  public final IntProperty timeMultiplier = new IntProperty("Time Multiplier", 25, 10, 100);
  public final IntProperty ticksToReduce = new IntProperty("Ticks To Reduce", 1, 1, 10);

  private Mode mode = Mode.NONE;
  private long time, balance;
  private double range, distance;
  private Entity target;

  public TickBase() {
    super("TickBase", false);
  }

  private EntityLivingBase getTarget(double range) {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (killAura == null) return null;

    if (onlyKillaura.getValue()) {
      if (!killAura.isEnabled()) return null;
      EntityLivingBase kTarget = killAura.getTarget();
      if (kTarget != null && mc.thePlayer.getDistanceToEntity(kTarget) <= range) {
        return kTarget;
      }
      return null;
    }

    List<EntityLivingBase> entities =
        mc.theWorld.loadedEntityList.stream()
            .filter(entity -> entity instanceof EntityLivingBase)
            .map(entity -> (EntityLivingBase) entity)
            .filter(entity -> entity != mc.thePlayer)
            .filter(entity -> !entity.isDead && entity.getHealth() > 0)
            .filter(
                entity -> {
                  if (entity instanceof EntityPlayer && !killAura.targetPlayers.getValue())
                    return false;
                  if (entity.isInvisible() && !killAura.targetInvisibles.getValue()) return false;
                  if (entity instanceof net.minecraft.entity.monster.EntityMob
                      || entity instanceof net.minecraft.entity.monster.EntitySlime) {
                    if (!killAura.targetMobs.getValue()) return false;
                  }
                  if (entity instanceof net.minecraft.entity.passive.EntityAnimal
                      || entity instanceof net.minecraft.entity.passive.EntitySquid
                      || entity instanceof net.minecraft.entity.passive.EntityBat
                      || entity instanceof net.minecraft.entity.passive.EntityVillager) {
                    if (!killAura.targetAnimals.getValue()) return false;
                  }
                  return true;
                })
            .filter(entity -> mc.thePlayer.getDistanceToEntity(entity) <= range)
            .collect(Collectors.toList());

    return entities.isEmpty() ? null : entities.get(0);
  }

  @Override
  public void onDisabled() {
    mode = Mode.NONE;
    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0f;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() != EventType.PRE) return;

    if (target != null && mc.thePlayer != null) {
      distance = PlayerUtil.calculatePerfectRangeToEntity(target);
    }

    if (mode == Mode.REDUCING) {
      return;
    }

    target = getTarget(targetRange.getValue());
    if (target == null) return;

    distance = PlayerUtil.calculatePerfectRangeToEntity(target);
    double currentRange = distance;

    if (currentRange > minRange.getValue()
        && balance >= maxBalance.getValue()
        && mode == Mode.BASING) {
      balance -= maxBalance.getValue();
      ((IAccessorMinecraft) mc).getTimer().elapsedTicks += ticksToReduce.getValue();
    } else {
      if (balance != 0) {
        ChatUtil.display("Balance " + balance + " " + currentRange);
      }
      balance = 0;
      mode = Mode.NONE;
    }

    if (currentRange < lagRange.getValue()
        && this.range >= lagRange.getValue()
        && mode == Mode.NONE) {
      mode = Mode.REDUCING;
      time = System.currentTimeMillis();
      balance = 0;
    }

    this.range = currentRange;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() == EventType.RECEIVE) {
      if (mode == Mode.REDUCING) {}
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled()) return;
    if (mode != Mode.REDUCING || target == null) return;

    if (distance <= revertRange.getValue()
        || System.currentTimeMillis() - time
            >= ((range / (mc.thePlayer.isPotionActive(Potion.moveSpeed) ? 0.36 : 0.25))
                    * timeMultiplier.getValue())
                + timeMultiplier.getValue()) {
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1;
      mode = Mode.BASING;
      balance = System.currentTimeMillis() - time;
      return;
    }

    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0;
  }

  private enum Mode {
    REDUCING,
    BASING,
    NONE
  }
}
