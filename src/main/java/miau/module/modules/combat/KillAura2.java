package miau.module.modules.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.math.RandomUtil;
import miau.util.network.PacketUtil;
import miau.util.player.ItemUtil;
import miau.util.player.PlayerUtil;
import miau.util.player.RayCastUtil;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

public class KillAura2 extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final TimerUtil attackTimer = new TimerUtil();
  private final TimerUtil switchTimer = new TimerUtil();
  private EntityLivingBase target;
  private int targetIndex;
  private float lastYaw;
  private float lastPitch;
  private boolean blocking;
  private boolean wasBlocking;
  private int blockingTime;
  private int cpsValue;

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"SINGLE", "SWITCH"});
  public final IntProperty switchDelay = new IntProperty("switch-delay", 150, 0, 1000);
  public final ModeProperty sortMode = new ModeProperty("sort", 0, new String[]{"HEALTH", "HURT-TIME", "DISTANCE", "YAW"});
  public final FloatProperty attackRange = new FloatProperty("attack-range", 3.2F, 3.0F, 6.0F);
  public final FloatProperty swingRange = new FloatProperty("swing-range", 3.5F, 3.0F, 8.0F);
  public final FloatProperty preAimRange = new FloatProperty("preaim-range", 6.0F, 3.0F, 12.0F);
  public final FloatProperty minCps = new FloatProperty("min-cps", 10.0F, 1.0F, 20.0F);
  public final FloatProperty maxCps = new FloatProperty("max-cps", 10.0F, 1.0F, 20.0F);
  public final ModeProperty rotationMode = new ModeProperty("rotation", 1, new String[]{"NONE", "SILENT"});
  public final ModeProperty moveFix = new ModeProperty("move-fix", 0, new String[]{"OFF", "NORMAL"});
  public final FloatProperty rotationSpeed = new FloatProperty("rotation-speed", 5.0F, 0.0F, 5.0F);
  public final ModeProperty autoBlock = new ModeProperty("auto-block", 1, new String[]{"MANUAL", "VANILLA", "POST", "SWAP", "INTERACT_A", "INTERACT_B", "FAKE", "PARTIAL", "WATCHDOG", "GRIMAC-1.8", "GRIMAC-1.12"});
  public final BooleanProperty fixNoSlowFlag = new BooleanProperty("fix-noslow-flag", true);
  public final IntProperty postDelay = new IntProperty("post-delay", 10, 1, 20);
  public final BooleanProperty hitThroughWalls = new BooleanProperty("through-walls", true);
  public final BooleanProperty rayCast = new BooleanProperty("ray-cast", true);
  public final BooleanProperty weaponOnly = new BooleanProperty("weapon-only", false);
  public final BooleanProperty targetPlayers = new BooleanProperty("target-players", true);
  public final BooleanProperty targetMobs = new BooleanProperty("target-mobs", false);
  public final BooleanProperty targetAnimals = new BooleanProperty("target-animals", false);
  public final BooleanProperty targetInvisible = new BooleanProperty("target-invisible", false);
  public final BooleanProperty targetBosses = new BooleanProperty("target-bosses", false);
  public final BooleanProperty targetGolems = new BooleanProperty("target-golems", false);
  public final BooleanProperty targetSilverfish = new BooleanProperty("target-silverfish", false);
  public final BooleanProperty targetTeams = new BooleanProperty("target-teams", true);
  public final BooleanProperty silentSwing = new BooleanProperty("silent-swing", false);

  public KillAura2() {
    super("KillAura2", false);
  }

  @Override
  public void onEnabled() {
    target = null;
    targetIndex = 0;
    blocking = false;
    wasBlocking = false;
    blockingTime = 0;
  }

  @Override
  public void onDisabled() {
    target = null;
    if (wasBlocking) {
      PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
      wasBlocking = false;
      blocking = false;
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!isEnabled() || event.getType() != EventType.PRE) return;

    List<EntityLivingBase> targets = getTargets();
    if (targets.isEmpty()) {
      target = null;
      return;
    }
    switch (mode.getValue()) {
      case 0:
        target = targets.get(0);
        break;
      case 1:
        if (switchTimer.hasTimeElapsed(switchDelay.getValue().longValue(), true)) {
          targetIndex = (targetIndex + 1) % targets.size();
        }
        if (targetIndex < targets.size()) target = targets.get(targetIndex);
        else target = null;
        break;
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!isEnabled()) return;
    if (event.getType() == EventType.POST) {
      if (blocking
          && autoBlock.getValue() == 9
          && mc.thePlayer.getHeldItem() != null
          && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
      }
      return;
    }
    if (event.getType() != EventType.PRE) return;

    if (target == null) return;
    if (weaponOnly.getValue() && !ItemUtil.isHoldingSword()) return;

    boolean canAutoBlock = ItemUtil.isHoldingSword() && target != null;
    blocking = canAutoBlock && autoBlock.getValue() > 0;

    if (rotationMode.getValue() == 1) {
      float[] rots = RotationUtil.calculate(target, true, attackRange.getValue());
      if (rots != null) {
        float speed = rotationSpeed.getValue();
        if (speed > 0) {
          rots[0] = rotMove(rots[0], lastYaw, speed);
          rots[1] = rotMove(rots[1], lastPitch, speed);
        }
        event.setRotation(rots[0], rots[1], 1);
        lastYaw = rots[0];
        lastPitch = rots[1];
      }
    }

    boolean shouldRayCast = rayCast.getValue();
    if (shouldRayCast) {
      MovingObjectPosition mop;
      if (hitThroughWalls.getValue()) {
        mop = RayCastUtil.getEntityIntercept(target, lastYaw, lastPitch, attackRange.getValue());
      } else {
        mop = RayCastUtil.rayCast(lastYaw, lastPitch, attackRange.getValue());
      }
      if (mop == null || mop.entityHit != target) return;
    }

    if (attackTimer.hasTimeElapsed(getCpsDelay(), true)) {
      performAttack();
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!isEnabled() || event.getType() != EventType.SEND) return;

    if (event.getPacket() instanceof C07PacketPlayerDigging) {
      C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
      if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
        blocking = false;
        wasBlocking = false;
      }
    }
  }

  private void performAttack() {
    if (target == null) return;

    boolean silentSwingActive = silentSwing.getValue() && blocking;
    if (!silentSwingActive) {
      mc.thePlayer.swingItem();
    } else {
      PacketUtil.sendPacket(new C0APacketAnimation());
    }

    PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
    PlayerUtil.attackEntity(target);

    if (mc.thePlayer.fallDistance > 0.0f && !mc.thePlayer.onGround && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.thePlayer.ridingEntity == null) {
      mc.thePlayer.onCriticalHit(target);
    }
    if (mc.thePlayer.getHeldItem() != null && EnchantmentHelper.getModifierForCreature(mc.thePlayer.getHeldItem(), target.getCreatureAttribute()) > 0.0f) {
      mc.thePlayer.onEnchantmentCritical(target);
    }

    handleAutoBlock();
  }

  private void handleAutoBlock() {
    if (!blocking || !ItemUtil.isHoldingSword()) return;
    int mode = autoBlock.getValue();

    if (fixNoSlowFlag.getValue() && blockingTime > postDelay.getValue()) {
      unBlock();
      blockingTime = 0;
      return;
    }

    switch (mode) {
      case 0:
        break;
      case 1:
        sendBlock();
        wasBlocking = true;
        break;
      case 2:
        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        wasBlocking = true;
        break;
      case 3:
      case 4:
      case 5:
      case 8:
        PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
        sendBlock();
        wasBlocking = true;
        break;
      case 6:
        wasBlocking = true;
        break;
      case 9:
        wasBlocking = true;
        break;
      case 10:
        if (mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
          PacketUtil.sendPacket(new C0FPacketConfirmTransaction(RandomUtil.nextInt(0, 2147483647), (short) RandomUtil.nextInt(-32767, 0), true));
          PacketUtil.sendPacket(new C0APacketAnimation());
          mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
        }
        wasBlocking = true;
        break;
    }
    blockingTime++;
  }

  private void sendBlock() {
    PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
  }

  private void unBlock() {
    if (!ItemUtil.isHoldingSword()) return;
    PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    wasBlocking = false;
    blocking = false;
  }

  private List<EntityLivingBase> getTargets() {
    List<EntityLivingBase> list = new ArrayList<>();
    for (Entity entity : mc.theWorld.getLoadedEntityList()) {
      if (!(entity instanceof EntityLivingBase)) continue;
      EntityLivingBase e = (EntityLivingBase) entity;
      if (e == mc.thePlayer || e.isDead) continue;
      double dist = mc.thePlayer.getDistanceToEntity(e);
      if (dist > preAimRange.getValue()) continue;
      if (!isValidTarget(e)) continue;
      list.add(e);
    }
    switch (sortMode.getValue()) {
      case 0:
        list.sort(Comparator.comparingDouble(EntityLivingBase::getHealth));
        break;
      case 1:
        list.sort(Comparator.comparingInt(e -> e.hurtTime));
        break;
      case 2:
        list.sort(Comparator.comparingDouble(mc.thePlayer::getDistanceToEntity));
        break;
      case 3:
        list.sort((a, b) -> Float.compare(Math.abs(a.rotationYaw), Math.abs(b.rotationYaw)));
        break;
    }
    return list;
  }

  private boolean isValidTarget(EntityLivingBase entity) {
    if (entity == null || mc.theWorld == null || mc.thePlayer == null) return false;
    if (!mc.theWorld.getLoadedEntityList().contains(entity)) return false;
    if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity) return false;
    if (entity == mc.getRenderViewEntity() || entity == mc.getRenderViewEntity().ridingEntity) return false;
    if (entity.deathTime > 0) return false;
    if (entity instanceof EntityPlayer) return isValidPlayer((EntityPlayer) entity);
    if (entity instanceof EntityDragon || entity instanceof EntityWither) return targetBosses.getValue();
    if (entity instanceof EntityMob || entity instanceof EntitySlime) {
      if (entity instanceof EntitySilverfish) return targetSilverfish.getValue() && allowTeamColor(entity);
      return targetMobs.getValue();
    }
    if (entity instanceof EntityAnimal || entity instanceof EntityBat || entity instanceof EntitySquid || entity instanceof EntityVillager) return targetAnimals.getValue();
    if (entity instanceof EntityIronGolem) return targetGolems.getValue() && allowTeamColor(entity);
    return false;
  }

  private boolean isValidPlayer(EntityPlayer player) {
    if (!targetPlayers.getValue()) return false;
    boolean isInvisible = player.isInvisible();
    if (isInvisible && !targetInvisible.getValue()) return false;
    if (TeamUtil.isFriend(player)) return false;
    return allowSameTeam(player) && (isInvisible || !miau.module.modules.misc.AntiBot.isBot(player));
  }

  private boolean allowTeamColor(EntityLivingBase entityLivingBase) {
    return targetTeams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase);
  }

  private boolean allowSameTeam(EntityPlayer player) {
    return targetTeams.getValue() || !TeamUtil.isSameTeam(player);
  }

  private long getCpsDelay() {
    float min = minCps.getValue();
    float max = maxCps.getValue();
    if (min > max) {
      float tmp = min;
      min = max;
      max = tmp;
    }
    return (long) (1000.0 / RandomUtil.nextDouble(min, max));
  }

  private float rotMove(float target, float current, float speed) {
    float delta = MathHelper.wrapAngleTo180_float(target - current);
    if (speed >= 5.0f) return target;
    float maxStep = speed * 10.0f;
    if (Math.abs(delta) <= maxStep) return target;
    return current + (delta > 0 ? maxStep : -maxStep);
  }

  @Override
  public String[] getSuffix() {
    return new String[]{mode.getModeString()};
  }
}