package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.*;
import miau.util.client.KeyBindUtil;
import miau.util.math.RandomUtil;
import miau.util.network.PacketUtil;
import miau.util.player.*;
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
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KillAuraV2 extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int cps;
    private int targetIndex;
    private float lastYaw;
    private float lastPitch;
    private boolean aiming;
    private boolean blocking;
    private boolean wasBlocking;
    private EntityLivingBase target;
    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil switchTimer = new TimerUtil();
    private final List<EntityLivingBase> targets = new ArrayList<>();
    private int autoBlockWatchdogBlockingTime;

    public final BooleanProperty targetPlayers = new BooleanProperty("Target players", true);
    public final BooleanProperty targetAnimals = new BooleanProperty("Target animals", false);
    public final BooleanProperty targetMobs = new BooleanProperty("Target mobs", false);
    public final BooleanProperty targetInvisible = new BooleanProperty("Target invisible", false);
    public final BooleanProperty targetBosses = new BooleanProperty("Target bosses", false);
    public final BooleanProperty targetGolems = new BooleanProperty("Target golems", false);
    public final BooleanProperty targetSilverfish = new BooleanProperty("Target silverfish", false);
    public final BooleanProperty targetTeams = new BooleanProperty("Target teams", true);

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Single", "Switch"});
    public final IntProperty switchDelay = new IntProperty("Switch delay", 200, 0, 1000, () -> this.mode.getValue() == 1);

    public final FloatProperty rotationSpeed = new FloatProperty("Rotation speed", 20.0F, 2.0F, 20.0F);
    public final ModeProperty rotationMode = new ModeProperty("Rotation mode", 0, new String[]{"Instant", "Nearest"});
    public final ModeProperty moveFixMode = new ModeProperty("Move fix mode", 2, new String[]{"Off", "Normal", "Silent"});

    public final BooleanProperty autoBlock = new BooleanProperty("Auto block", false);
    public final ModeProperty autoBlockMode = new ModeProperty("Auto block mode", 0, new String[]{"Fake", "Watchdog", "GrimAC 1.8", "GrimAC 1.12"}, this.autoBlock::getValue);
    public final BooleanProperty fixNoSlowFlag = new BooleanProperty("Fix no slow flag", false, () -> this.autoBlock.getValue() && this.autoBlockMode.getValue() == 1);
    public final ModeProperty sortMode = new ModeProperty("Sort mode", 0, new String[]{"Distance", "Hurt Time", "Health", "Armor"});

    public final FloatProperty minCPS = new FloatProperty("Min CPS", 10.0F, 1.0F, 20.0F);
    public final FloatProperty maxCPS = new FloatProperty("Max CPS", 20.0F, 1.0F, 20.0F);
    public final FloatProperty preAimRange = new FloatProperty("Pre aim range", 3.5F, 3.0F, 10.0F);
    public final FloatProperty attackRange = new FloatProperty("Attack range", 3.2F, 3.0F, 6.0F);

    public final BooleanProperty throughWalls = new BooleanProperty("Through walls", false);
    public final BooleanProperty rayCast = new BooleanProperty("Ray cast", true);

    public KillAuraV2() {
        super("KillAuraV2", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[] { this.mode.getModeString() };
    }

    @Override
    public void onDisabled() {
        target = null;
        targets.clear();
        aiming = false;
        blocking = false;
        if (wasBlocking) {
            int autoBlock = this.autoBlockMode.getValue();
            switch (autoBlock) {
                case 2:
                case 3:
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    break;
                case 1:
                    PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                    break;
            }
        }
        wasBlocking = false;
        autoBlockWatchdogBlockingTime = 0;
        super.onDisabled();
    }

    private float rotMove(float target, float current, float speed) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        delta = MathHelper.clamp_float(delta, -speed, speed);
        return current + delta;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (minCPS.getValue() > maxCPS.getValue()) {
                float min = minCPS.getValue();
                float max = maxCPS.getValue();
                minCPS.setValue(Math.min(min, max));
                maxCPS.setValue(Math.max(min, max));
            }
            if (attackRange.getValue() > preAimRange.getValue()) {
                preAimRange.setValue(attackRange.getValue());
            }
            sortTargets();
            if (target == null) {
                lastYaw = event.getYaw();
                lastPitch = event.getPitch();
            }
            aiming = !targets.isEmpty();
            blocking = autoBlock.getValue() && aiming && ItemUtil.isHoldingSword();
            if (aiming) {
                switch (mode.getValue()) {
                    case 0:
                        if (!targets.isEmpty()) target = targets.get(0);
                        else target = null;
                        break;
                    case 1:
                        if (switchTimer.hasTimeElapsed(switchDelay.getValue(), true)) {
                            targetIndex = (targetIndex + 1) % targets.size();
                        }
                        if (targetIndex < targets.size()) target = targets.get(targetIndex);
                        else target = null;
                        break;
                }
                float yaw = lastYaw;
                float pitch = lastPitch;
                float rotSpeed = (float) RandomUtil.nextDouble(rotationSpeed.getValue(), rotationSpeed.getValue());
                switch (rotationMode.getValue()) {
                    case 0:
                        if (target != null) {
                            Vec3 eyePos = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
                            float[] rots = RotationUtil.calculate(eyePos);
                            yaw = rots[0];
                            pitch = rots[1];
                        }
                        break;
                    case 1:
                        if (target != null) {
                            float[] rots = RotationUtil.calculate(target);
                            yaw = rots[0];
                            pitch = rots[1];
                        }
                        break;
                }
                lastYaw = rotMove(yaw, lastYaw, rotSpeed);
                lastPitch = rotMove(pitch, lastPitch, rotSpeed);
                event.setRotation(lastYaw, lastPitch, 1);
                if (rayCast.getValue() && target != null) {
                    MovingObjectPosition mop = RayCastUtil.getEntityIntercept(target, lastYaw, lastPitch, attackRange.getValue());
                    if (mop != null || RotationUtil.distanceToEntity(target) <= attackRange.getValue()) {
                        if (attackTimer.hasTimeElapsed(cps)) {
                            int maxValue = (int) ((minCPS.getMaximum() - maxCPS.getValue()) * 5.0);
                            int minValue = (int) ((minCPS.getMaximum() - minCPS.getValue()) * 5.0);
                            cps = RandomUtil.nextInt(Math.min(minValue, maxValue), Math.max(minValue, maxValue));
                            attack();
                        }
                    }
                } else if (target != null) {
                    if (attackTimer.hasTimeElapsed(cps)) {
                        int maxValue = (int) ((minCPS.getMaximum() - maxCPS.getValue()) * 5.0);
                        int minValue = (int) ((minCPS.getMaximum() - minCPS.getValue()) * 5.0);
                        cps = RandomUtil.nextInt(Math.min(minValue, maxValue), Math.max(minValue, maxValue));
                        attack();
                    }
                }
            } else {
                attackTimer.reset();
                target = null;
            }
            int autoBlock = this.autoBlockMode.getValue();
            if (blocking) {
                switch (autoBlock) {
                    case 0:
                        break;
                    case 1:
                        if (autoBlockWatchdogBlockingTime < 10 || !fixNoSlowFlag.getValue()) {
                            PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
                            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                            wasBlocking = true;
                            autoBlockWatchdogBlockingTime++;
                        } else {
                            if (wasBlocking) PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                            wasBlocking = false;
                            autoBlockWatchdogBlockingTime = 0;
                        }
                        break;
                    case 3:
                        if (mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
                            PacketUtil.sendPacket(new C0FPacketConfirmTransaction(RandomUtil.nextInt(0, 2147483647), (short) RandomUtil.nextInt(-32767, 0), true));
                            PacketUtil.sendPacket(new C0APacketAnimation());
                            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                            wasBlocking = true;
                        }
                        break;
                }
            } else if (wasBlocking && (autoBlock == 2 || autoBlock == 3)) {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                wasBlocking = false;
            } else if (wasBlocking && autoBlock == 1) {
                PacketUtil.sendPacketNoEvent(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                wasBlocking = false;
            }
        } else if (event.getType() == EventType.POST) {
            if (blocking && autoBlockMode.getValue() == 2) {
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            }
        }
    }

    private void attack() {
        if (target == null) return;
        MovingObjectPosition mop = RayCastUtil.getEntityIntercept(target, lastYaw, lastPitch, attackRange.getValue());
        if (mop != null || RotationUtil.distanceToEntity(target) <= attackRange.getValue()) {
            if (mc.thePlayer.fallDistance > 0.0f && !mc.thePlayer.onGround && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.thePlayer.ridingEntity == null) {
                mc.thePlayer.onCriticalHit(target);
            }
            if (EnchantmentHelper.getModifierForCreature(mc.thePlayer.getHeldItem(), target.getCreatureAttribute()) > 0.0f) {
                mc.thePlayer.onEnchantmentCritical(target);
                PacketUtil.sendPacket(new C0APacketAnimation());
            }
            PlayerUtil.attackEntity(target);
            attackTimer.reset();
        }
    }

    private void sortTargets() {
        targets.clear();
        for (Entity entity : mc.theWorld.getLoadedEntityList()) {
            if (entity instanceof EntityLivingBase) {
                EntityLivingBase e = (EntityLivingBase) entity;
                if (mc.thePlayer.getDistanceToEntity(entity) <= preAimRange.getValue() && isValid(entity) && mc.thePlayer != e) {
                    targets.add(e);
                }
            }
        }
        switch (sortMode.getValue()) {
            case 0:
                targets.sort(Comparator.comparingDouble(mc.thePlayer::getDistanceToEntity));
                break;
            case 1:
                targets.sort(Comparator.comparingInt(entity -> entity.hurtTime));
                break;
            case 2:
                targets.sort(Comparator.comparingDouble(EntityLivingBase::getHealth));
                break;
            case 3:
                targets.sort(Comparator.comparingInt(EntityLivingBase::getTotalArmorValue));
                break;
        }
    }

    private boolean isValid(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || mc.theWorld == null || mc.thePlayer == null) return false;
        EntityLivingBase e = (EntityLivingBase) entity;
        if (!mc.theWorld.getLoadedEntityList().contains(e)) return false;
        if (e == mc.thePlayer || e == mc.thePlayer.ridingEntity) return false;
        if (e == mc.getRenderViewEntity() || e == mc.getRenderViewEntity().ridingEntity) return false;
        if (e.deathTime > 0) return false;
        if (e instanceof EntityPlayer) {
            if (!isValidPlayer((EntityPlayer) e)) return false;
            return true;
        }
        if (e instanceof EntityDragon || e instanceof EntityWither) return targetBosses.getValue();
        if (e instanceof EntityMob || e instanceof EntitySlime) {
            if (e instanceof EntitySilverfish) return targetSilverfish.getValue() && allowTeamColor(e);
            return targetMobs.getValue();
        }
        if (e instanceof EntityAnimal || e instanceof EntityBat || e instanceof EntitySquid || e instanceof EntityVillager) return targetAnimals.getValue();
        if (e instanceof EntityIronGolem) return targetGolems.getValue() && allowTeamColor(e);
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
}
