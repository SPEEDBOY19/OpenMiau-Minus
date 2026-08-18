package miau.module.modules.ghost;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.LeftClickMouseEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.player.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

public class SmartClicking extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private EntityLivingBase lastTarget = null;
  private long lastAttackTime = 0L;
  private boolean shouldClick = true;
  private double lastDamage = 0.0;
  private long lastTimePlayerHurt = 0L;

  public final FloatProperty attackRange = new FloatProperty("attack-range", 3.0F, 2.0F, 6.0F);
  public final FloatProperty searchRange = new FloatProperty("search-range", 3.5F, 2.0F, 8.0F);
  public final IntProperty selfPredictionTicks = new IntProperty("self-pred-ticks", 1, 0, 5);
  public final IntProperty targetPredictionTicks = new IntProperty("target-pred-ticks", 3, 0, 5);
  public final BooleanProperty baseHurtTimeOnPing = new BooleanProperty("base-hurttime-on-ping", true);
  public final IntProperty validHurtTimeStart = new IntProperty("valid-hurttime-start", 2, 0, 10);
  public final IntProperty tradeTimeoutTicks = new IntProperty("trade-timeout-ticks", 5, 0, 20);
  public final IntProperty midTradeHurtTimeStart = new IntProperty("mid-trade-start", 1, 0, 10);
  public final IntProperty midTradeHurtTimeEnd = new IntProperty("mid-trade-end", 2, 0, 10);

  public SmartClicking() {
    super("SmartClicking", false);
  }

  @Override
  public void onEnabled() {
    lastTarget = null;
    shouldClick = true;
    lastDamage = 0.0;
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!this.isEnabled()) return;
    if (event.getTarget() instanceof EntityLivingBase) {
      lastTarget = (EntityLivingBase) event.getTarget();
      lastAttackTime = System.currentTimeMillis();
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;

    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    EntityLivingBase currentTarget = null;
    if (killAura != null && killAura.isEnabled()) {
      currentTarget = killAura.getTarget();
    }
    if (currentTarget == null) {
      currentTarget = lastTarget;
    }

    if (currentTarget != null) {
      double dist = mc.thePlayer.getDistanceToEntity(currentTarget);
      if (dist > this.searchRange.getValue()) {
        currentTarget = null;
        lastTarget = null;
      }
    }

    if (mc.thePlayer.hurtTime > 0) {
      lastTimePlayerHurt = System.currentTimeMillis();
    }

    if (currentTarget != null) {
      shouldClick = calculateShouldClick(currentTarget);
    } else {
      shouldClick = true;
    }
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (!this.isEnabled()) return;

    if (!shouldClick) {
      double currentDamage = getPlayerDamage();
      if (currentDamage > lastDamage) {
        lastDamage = currentDamage;
      } else {
        event.setCancelled(true);
      }
    } else {
      lastDamage = getPlayerDamage();
    }
  }

  private double getPlayerDamage() {
    if (mc.thePlayer == null) return 0.0;
    double baseDamage = 1.0;
    if (mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage) != null) {
      baseDamage = mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
    }
    ItemStack held = mc.thePlayer.getHeldItem();
    float enchantDamage = 0.0F;
    if (held != null) {
      enchantDamage = EnchantmentHelper.getModifierForCreature(held, net.minecraft.entity.EnumCreatureAttribute.UNDEFINED);
    }
    boolean isCrit = mc.thePlayer.fallDistance > 0.0F && !mc.thePlayer.onGround && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater() && !mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.blindness) && mc.thePlayer.ridingEntity == null;
    return (baseDamage + enchantDamage) * (isCrit ? 1.5 : 1.0);
  }

  private boolean calculateShouldClick(EntityLivingBase target) {
    SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
    int simHurtTime = mc.thePlayer.hurtTime;

    int selfPred = selfPredictionTicks.getValue();
    for (int i = 0; i < selfPred; i++) {
      sim.tick();
      if (simHurtTime > 0) simHurtTime--;
    }

    if (simHurtTime <= 0) {
      ItemStack targetHeld = target.getHeldItem();
      int knockbackLevel = 0;
      if (targetHeld != null) {
        knockbackLevel = EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.knockback.effectId, targetHeld);
      }
      double kb = knockbackLevel + (target.isSprinting() ? 1.0 : 0.0);
      float yawHead = target.rotationYawHead;
      sim.motionX += -MathHelper.sin(yawHead * (float) Math.PI / 180.0F) * kb * 0.5;
      sim.motionZ += MathHelper.cos(yawHead * (float) Math.PI / 180.0F) * kb * 0.5;
      sim.motionY += 0.1;
    }

    int pingTicks = 0;
    if (baseHurtTimeOnPing.getValue()) {
      int ping = 0;
      if (mc.getNetHandler() != null && mc.thePlayer != null) {
        net.minecraft.client.network.NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info != null) ping = info.getResponseTime();
      }
      pingTicks = ping / 50;
    }

    int optimalHurtTime = validHurtTimeStart.getValue() + pingTicks;
    boolean targetHittable = target.hurtTime <= optimalHurtTime;

    long timeSinceHurt = System.currentTimeMillis() - lastTimePlayerHurt;
    if (timeSinceHurt <= (long) (tradeTimeoutTicks.getValue() * 50)) {
      int ticksForGround = 5;
      SimulatedPlayer landSim = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
      for (int i = 1; i <= 5; i++) {
        landSim.tick();
        if (landSim.onGround) {
          ticksForGround = i;
          break;
        }
      }

      double start = midTradeHurtTimeStart.getValue();
      double end = midTradeHurtTimeEnd.getValue();
      double midTradeHurtTime = start + (end - start) * (ticksForGround / 5.0);

      if (target.hurtTime > midTradeHurtTime && target.hurtTime < 10) {
        return false;
      }
    }

    boolean targetOnFire = target.isBurning();
    return targetHittable || targetOnFire;
  }
}
