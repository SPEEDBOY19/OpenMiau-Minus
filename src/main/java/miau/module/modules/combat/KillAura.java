package miau.module.modules.combat;

import com.google.common.base.CaseFormat;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.List;
import java.util.Locale;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.event.EventManager;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.Module;
import miau.module.modules.combat.killaura.autoblocks.*;
import miau.module.modules.combat.killaura.target.AttackData;
import miau.module.modules.combat.killaura.target.LastAttackData;
import miau.module.modules.movement.NoSlow;
import miau.module.modules.player.AutoBlockIn;
import miau.module.modules.player.AutoHead;
import miau.module.modules.player.BedNuker;
import miau.module.modules.player.Scaffold;
import miau.property.properties.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

public class KillAura extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final DecimalFormat df =
      new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
  private final TimerUtil timer = new TimerUtil();
  private AttackData target = null;
  private miau.module.modules.combat.killaura.render.TargetRenderer targetRenderer;
  private final miau.module.modules.combat.killaura.target.TargetManager targetManager =
      new miau.module.modules.combat.killaura.target.TargetManager(this);
  public final java.util.List<miau.module.modules.combat.killaura.rotation.RotationMode>
      rotationModes = new java.util.ArrayList<>();
  public int switchTick = 0;
  public boolean hitRegistered = false;
  public boolean blockingState = false;
  public boolean isBlocking = false;
  public boolean fakeBlockState = false;
  public boolean blinkReset = false;
  public boolean rightHoldActive = false;
  public long attackDelayMS = 0L;
  public int blockTick = 0;
  public boolean blockBlink = false;

  // Bổ sung biến watchdogTick để khớp với WatchdogAutoBlock
  public int watchdogTick = 0;

  public boolean cancelAttack = false;
  public int ticksSinceVelocity = 0;
  public double expandRange = 0.0;
  public final ModeProperty mode;
  public final IntProperty switchDelay;
  public final ModeProperty autoBlock;
  public final ModeProperty rotations;
  public final ModeProperty debugLog;
  public final ModeProperty moveFix;
  public final BooleanProperty rayCast;
  public final FloatProperty cps;
  public final FloatProperty autoBlockCps;
  public final FloatProperty swingRange;
  public final PercentProperty smoothing;
  public final IntProperty angleStep;
  public int lastTickProcessed = 0;
  public final List<AutoBlockMode> autoBlockModes = new ArrayList<>();
  public final BooleanProperty autoBlockRequirePress;
  public final BooleanProperty preventServersideBlocking;
  public final BooleanProperty fixNoSlowFlag;
  public final ModeProperty sort;
  public final FloatProperty attackRange;
  public final BooleanProperty throughWalls;
  public final BooleanProperty whileScaffold;

  public final BooleanProperty requirePress;
  public final BooleanProperty allowMining;
  public final ModeProperty showTarget;
  public final BooleanProperty targetPlayers = new BooleanProperty("target-players", true);
  public final BooleanProperty targetInvisibles =
      new BooleanProperty("target-invisibles", false, this.targetPlayers::getValue);
  public final BooleanProperty targetBosses = new BooleanProperty("target-bosses", false);
  public final BooleanProperty targetMobs = new BooleanProperty("target-mobs", false);
  public final BooleanProperty targetAnimals = new BooleanProperty("target-animals", false);
  public final BooleanProperty targetGolems = new BooleanProperty("target-golems", false);
  public final BooleanProperty targetSilverfish = new BooleanProperty("target-silverfish", false);
  public final BooleanProperty targetTeams = new BooleanProperty("target-teams", true);

  private long getAttackDelay() {
    float min = this.cps.getValue();
    float max = this.cps.getSecondValue();
    if (this.isBlocking) {
      min = this.autoBlockCps.getValue();
      max = this.autoBlockCps.getSecondValue();
    }
    return 1000L / miau.util.math.RandomUtil.nextLong((int) min, (int) max);
  }

  private boolean performAttack(float yaw, float pitch) {
    if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {
      if (this.shouldDelayHit()) {
        return false;
      } else if (this.attackDelayMS > 0L) {
        return false;
      } else {
        mc.thePlayer.swingItem();

        net.minecraft.util.MovingObjectPosition rayCastPos = null;
        boolean rayCastHit = false;

        boolean useRaycast = this.rayCast.getValue();

        if (this.rotations.getValue() != 0) {
          if (this.throughWalls.getValue()) {
            rayCastPos =
                miau.util.player.RayCastUtil.getEntityIntercept(
                    this.target.getEntity(), yaw, pitch, this.attackRange.getValue());
          } else {
            rayCastPos =
                miau.util.player.RayCastUtil.rayCast(yaw, pitch, this.attackRange.getValue());
          }
          if (rayCastPos != null && rayCastPos.entityHit == this.target.getEntity()) {
            rayCastHit = true;
          } else if (rayCastPos != null
              && rayCastPos.typeOfHit
                  == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY) {
            if (!(rayCastPos.entityHit instanceof net.minecraft.entity.projectile.EntityFireball
                    || rayCastPos.entityHit instanceof net.minecraft.entity.item.EntityItemFrame)
                && rayCastPos.entityHit instanceof net.minecraft.entity.EntityLivingBase
                && this.targetManager.isValid(
                    (net.minecraft.entity.EntityLivingBase) rayCastPos.entityHit)) {
              this.target =
                  new AttackData((net.minecraft.entity.EntityLivingBase) rayCastPos.entityHit);
            }
            rayCastHit = true;
          }
        } else if (useRaycast) {
          if (this.throughWalls.getValue()) {
            rayCastPos =
                miau.util.player.RayCastUtil.getEntityIntercept(
                    this.target.getEntity(), yaw, pitch, this.attackRange.getValue());
          } else {
            rayCastPos =
                miau.util.player.RayCastUtil.rayCast(yaw, pitch, this.attackRange.getValue());
          }

          if (rayCastPos != null && rayCastPos.entityHit == this.target.getEntity()) {
            rayCastHit = true;
          } else if (rayCastPos != null
              && rayCastPos.typeOfHit
                  == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY) {
            if (!(rayCastPos.entityHit instanceof net.minecraft.entity.projectile.EntityFireball
                    || rayCastPos.entityHit instanceof net.minecraft.entity.item.EntityItemFrame)
                && rayCastPos.entityHit instanceof net.minecraft.entity.EntityLivingBase
                && this.targetManager.isValid(
                    (net.minecraft.entity.EntityLivingBase) rayCastPos.entityHit)) {
              this.target =
                  new AttackData((net.minecraft.entity.EntityLivingBase) rayCastPos.entityHit);
            }
            rayCastHit = true;
          }
        } else if (mc.thePlayer.getDistanceToEntity(this.target.getEntity())
            <= this.attackRange.getValue()) {
          rayCastHit = true;
        }

        if ((this.rotations.getValue() != 0
                || !this.targetManager.isBoxInAttackRange(this.target.getBox()))
            && !rayCastHit) {
          return false;
        } else {
          AttackEvent event = new AttackEvent(this.target.getEntity());
          EventManager.call(event);
          ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
          PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
          if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(this.target.getEntity());
          }
          if (this.isBlocking && ItemUtil.isHoldingSword()) {
            this.sendUseItem();
          }

          LastAttackData lastAttack = this.targetMap.get(this.target.getEntity().getEntityId());
          if (lastAttack == null) {
            this.targetMap.put(
                this.target.getEntity().getEntityId(),
                new LastAttackData(this.getDamage(this.target.getEntity())));
          } else {
            lastAttack.reset(true, this.getDamage(this.target.getEntity()));
          }
          this.hitRegistered = true;
          return true;
        }
      }
    } else {
      return false;
    }
  }

  public void sendUseItem() {
    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    this.startBlock(mc.thePlayer.getHeldItem());
  }

  public void startBlock(ItemStack itemStack) {
    PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
    mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
    this.blockingState = true;
  }

  public void stopBlock() {
    PacketUtil.sendPacket(
        new C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    mc.thePlayer.stopUsingItem();
    this.blockingState = false;
  }

  public void setRightHold(boolean pressed) {
    int useKey = mc.gameSettings.keyBindUseItem.getKeyCode();
    if (pressed) {
      KeyBindUtil.setKeyBindState(useKey, true);
      this.rightHoldActive = true;
    } else if (this.rightHoldActive) {
      KeyBindUtil.updateKeyState(useKey);
      this.rightHoldActive = false;
    }
  }

  private void interactAttack(float yaw, float pitch) {
    this.interactAttack(yaw, pitch, true);
  }

  private void interactAttack(float yaw, float pitch, boolean sendInteractAt) {
    if (this.target != null) {
      net.minecraft.util.Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
      net.minecraft.util.Vec3 lookVec =
          miau.util.player.RayCastUtil.getVectorForRotation(pitch, yaw);
      net.minecraft.util.Vec3 targetPos =
          eyePos.addVector(lookVec.xCoord * 8.0, lookVec.yCoord * 8.0, lookVec.zCoord * 8.0);
      net.minecraft.util.MovingObjectPosition mop =
          this.target.getBox().calculateIntercept(eyePos, targetPos);
      if (mop != null) {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        if (sendInteractAt) {
          PacketUtil.sendPacket(
              new C02PacketUseEntity(
                  this.target.getEntity(),
                  new Vec3(
                      mop.hitVec.xCoord - this.target.getX(),
                      mop.hitVec.yCoord - this.target.getY(),
                      mop.hitVec.zCoord - this.target.getZ())));
        }
        PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        mc.thePlayer.setItemInUse(
            mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
        this.blockingState = true;
      }
    }
  }

  private boolean canAttack() {
    if (mc.currentScreen instanceof GuiContainer) {
      return false;
    } else {
      if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
        return false;
      } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
        return false;
      } else {
        AutoHead autoHead = (AutoHead) Miau.moduleManager.modules.get(AutoHead.class);
        if (autoHead.isEnabled() && autoHead.isHealing()) {
          return false;
        } else {
          BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
          AutoBlockIn autoBlockIn = (AutoBlockIn) Miau.moduleManager.modules.get(AutoBlockIn.class);
          if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
          } else if (!this.whileScaffold.getValue()
              && Miau.moduleManager.modules.get(Scaffold.class).isEnabled()) {
            return false;
          } else if (autoBlockIn.isEnabled()) {
            return false;
          } else if (this.requirePress.getValue()) {
            return PlayerUtil.isAttacking();
          } else {
            return !this.allowMining.getValue()
                || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK)
                || !PlayerUtil.isAttacking();
          }
        }
      }
    }
  }

  private boolean canAutoBlock() {
    if (!ItemUtil.isHoldingSword()) {
      return false;
    } else {
      return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
    }
  }

  public AttackData getAttackData() {
    return this.target;
  }

  public int findEmptySlot(int currentSlot) {
    for (int i = 0; i < 9; i++) {
      if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) {
        return i;
      }
    }
    for (int i = 0; i < 9; i++) {
      if (i != currentSlot) {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
        if (stack != null && !stack.hasDisplayName()) {
          return i;
        }
      }
    }
    return Math.floorMod(currentSlot - 1, 9);
  }

  public int findSwordSlot(int currentSlot) {
    for (int i = 0; i < 9; i++) {
      if (i != currentSlot) {
        ItemStack item = mc.thePlayer.inventory.getStackInSlot(i);
        if (item != null && item.getItem() instanceof ItemSword) {
          return i;
        }
      }
    }
    return -1;
  }

  public KillAura() {
    super("KillAura", false);
    this.autoBlockModes.add(new NoneAutoBlock(this));
    this.autoBlockModes.add(new VanillaAutoBlock(this));
    this.autoBlockModes.add(new InteractAutoBlock(this));
    this.autoBlockModes.add(new LegitAutoBlock(this));
    this.autoBlockModes.add(new FakeAutoBlock(this));
    this.autoBlockModes.add(new WatchdogAutoBlock(this));
    this.autoBlockModes.add(new GrimAC18AutoBlock(this));
    this.autoBlockModes.add(new GrimAC112AutoBlock(this));

    this.rotationModes.add(new miau.module.modules.combat.killaura.rotation.LegitRotation(this));
    this.rotationModes.add(new miau.module.modules.combat.killaura.rotation.SilentRotation(this));
    this.rotationModes.add(new miau.module.modules.combat.killaura.rotation.LockViewRotation(this));
    this.rotationModes.add(new miau.module.modules.combat.killaura.rotation.PolarRotation(this));

    this.lastTickProcessed = 0;
    this.mode = new ModeProperty("Mode", 0, new String[] {"SINGLE", "SWITCH"});
    this.switchDelay = new IntProperty("switch-delay", 150, 0, 1000);
    this.sort = new ModeProperty("sort", 0, new String[] {"HEALTH", "HURT-TIME"});
    this.attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
    this.swingRange = new FloatProperty("swing-range", 3.5F, 3.0F, 6.0F);
    this.cps = new FloatProperty("aps", 14.0F, 14.0F, 1.0F, 20.0F);

    this.rayCast = new BooleanProperty("ray-cast", false);
    this.throughWalls = new BooleanProperty("through-walls", true);
    this.whileScaffold = new BooleanProperty("while-scaffold", false);
    this.requirePress = new BooleanProperty("require-press", false);
    this.allowMining = new BooleanProperty("allow-mining", true);
    this.autoBlock = new ModeProperty("auto-block", 0, new String[] {"NONE", "VANILLA", "INTERACT", "LEGIT", "FAKE", "WATCHDOG", "GRIMAC-1.8", "GRIMAC-1.12"});
    this.autoBlockRequirePress = new BooleanProperty("autoblock-require-press", false);
    this.preventServersideBlocking = new BooleanProperty("prevent-serverside-blocking", false);
    this.fixNoSlowFlag = new BooleanProperty("fix-noslow-flag", false, () -> this.autoBlock.getValue() == 5);
    this.autoBlockCps = new FloatProperty("autoblock-aps", 8.0F, 10.0F, 1.0F, 10.0F);
    this.rotations = new ModeProperty("rotations", 2, new String[] {"NONE", "LEGIT", "SILENT", "LOCK_VIEW", "POLAR"});
    this.smoothing = new PercentProperty("smoothing", 0);
    this.angleStep = new IntProperty("angle-step", 90, 30, 180);
    this.moveFix = new ModeProperty("move-fix", 0, new String[] {"OFF", "Normal"});
    this.showTarget = new ModeProperty("show-target", 0, new String[] {"NONE", "BOX", "SIGMA_RING"});
    this.debugLog = new ModeProperty("debug-log", 0, new String[] {"NONE", "HEALTH"});
  }

  public EntityLivingBase getTarget() {
    return this.target != null ? this.target.getEntity() : null;
  }

  public boolean isInRange(EntityLivingBase entity) {
    return this.targetManager.isInRange(entity);
  }

  private boolean shouldDelayHit() {
    return false;
  }

  public boolean isAttackAllowed() {
    Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
    if (!this.whileScaffold.getValue() && scaffold.isEnabled()) {
      return false;
    } else if (this.shouldDelayHit()) {
      return false;
    } else {
      return !this.requirePress.getValue()
          || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
    }
  }

  public boolean shouldAutoBlock() {
    if (this.isPlayerBlocking() && this.isBlocking) {
      return (this.autoBlock.getValue() == 2
              || this.autoBlock.getValue() == 3
              || this.autoBlock.getValue() == 4
              || this.autoBlock.getValue() == 5
              || this.autoBlock.getValue() == 6
              || this.autoBlock.getValue() == 7)
          && !mc.thePlayer.isInWater()
          && !mc.thePlayer.isInLava();
    } else {
      return false;
    }
  }

  public boolean isBlocking() {
    return this.fakeBlockState && ItemUtil.isHoldingSword();
  }

  public boolean isPlayerBlocking() {
    return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
  }

  public boolean isNoSlowAntiSwitchActive() {
    NoSlow noSlow = (NoSlow) Miau.moduleManager.modules.get(NoSlow.class);
    return noSlow.isEnabled() && noSlow.mode.getValue() == 3 && this.isPlayerBlocking();
  }

  @EventTarget(Priority.LOW)
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.POST && this.blinkReset) {
      this.blinkReset = false;
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      Miau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
    }
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      this.ticksSinceVelocity++;
      if (mc.thePlayer.ticksExisted % 20 == 0) {
        this.expandRange = 3.0 + Math.random() * 0.5;
      }
      if (this.attackDelayMS > 0L) {
        this.attackDelayMS -= 50L;
      }
      boolean attack = this.target != null && this.canAttack();
      boolean block = attack && this.canAutoBlock();
      if (!block) {
        Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.isBlocking = false;
        this.fakeBlockState = false;
        this.blockTick = 0;
      }
      if (attack) {
        boolean swap = false;
        boolean blocked = false;
        if (block) {
          this.cancelAttack = false;
          AutoBlockMode mode = this.autoBlockModes.get(this.autoBlock.getValue());
          if (mode != null) swap = mode.processBlock(attack, block);
          if (this.cancelAttack) attack = false;
          blocked = this.blockBlink;
          this.blockBlink = false;
        }
        boolean attacked = false;
        if (this.targetManager.isBoxInSwingRange(this.target.getBox())) {
          if (this.rotations.getValue() != 0) {
            float[] targetRots =
                RotationUtil.getRotationsWithBackup(
                    this.target.getEntity(),
                    100.0,
                    100.0,
                    event.getYaw(),
                    event.getPitch(),
                    this.attackRange.getValue(),
                    this.throughWalls.getValue(),
                    true);

            if (targetRots == null) {
              targetRots =
                  RotationUtil.calculate(
                      this.target.getEntity(), true, this.attackRange.getValue());
            }

            float[] lastRots = new float[] {event.getYaw(), event.getPitch()};

            double rotSpeed = (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
            float[] rotations = lastRots;

            miau.module.modules.combat.killaura.rotation.RotationMode currentMode =
                this.rotations.getValue() > 0
                        && this.rotations.getValue() <= this.rotationModes.size()
                    ? this.rotationModes.get(this.rotations.getValue() - 1)
                    : null;

            if (currentMode != null) {
              rotations = currentMode.processRotations(targetRots, lastRots, rotSpeed, event);
            }

            float[] quantized =
                RotationUtil.flexRotation(rotations[0], rotations[1], lastRots[0], lastRots[1]);

            event.setRotation(quantized[0], quantized[1], 1);
            if (this.rotations.getValue() != 2) {
              event.setPervRotation(quantized[0], 1);
            }
          }

          if (attack) {
            attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
          }
        }
        if (swap) {
          if (attacked) {
            this.interactAttack(event.getNewYaw(), event.getNewPitch());
          } else {
            this.sendUseItem();
          }
        }
        if (blocked) {
          Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
          Miau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }
      }
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      switch (event.getType()) {
        case PRE:
          if (this.target == null
              || !this.targetManager.isValidTarget(this.target.getEntity())
              || !this.targetManager.isBoxInAttackRange(this.target.getBox())
              || !this.targetManager.isBoxInSwingRange(this.target.getBox())
              || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
            this.timer.reset();
            java.util.List<EntityLivingBase> validTargets = this.targetManager.getValidTargets();
            if (validTargets.isEmpty()) {
              this.target = null;
            } else {
              this.target = this.targetManager.findBestTarget(validTargets);
            }
          }
          if (this.target != null) {
            this.target = new AttackData(this.target.getEntity());
          }
          break;
        case POST:
          for (AutoBlockMode mode : this.autoBlockModes) {
            mode.onPostUpdate();
          }
          if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
            mc.thePlayer.setItemInUse(
                mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
          }
          break;
        default:
          break;
      }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof net.minecraft.network.play.client.C0BPacketEntityAction) {
        net.minecraft.network.play.client.C0BPacketEntityAction packet =
            (net.minecraft.network.play.client.C0BPacketEntityAction) event.getPacket();
        switch (packet.getAction()) {
          default:
            break;
        }
      }
      if (this.preventServersideBlocking.getValue() && this.isPlayerBlocking()) {
        if (event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
          C08PacketPlayerBlockPlacement wrapper = (C08PacketPlayerBlockPlacement) event.getPacket();
          if (wrapper.getStack() != null && wrapper.getStack().getItem() instanceof ItemSword) {
            event.setCancelled(true);
          }
        } else if (event.getPacket() instanceof C07PacketPlayerDigging) {
          C07PacketPlayerDigging wrapper = (C07PacketPlayerDigging) event.getPacket();
          if (wrapper.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            event.setCancelled(true);
          }
        }
      }
    }
    if (this.isEnabled() && !event.isCancelled() && mc.thePlayer != null && mc.theWorld != null) {
      if (event.getPacket() instanceof C07PacketPlayerDigging) {
        C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
        if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
          this.blockingState = false;
        }
      }
      if (event.getPacket() instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) {
        net.minecraft.network.play.server.S12PacketEntityVelocity packet =
            (net.minecraft.network.play.server.S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
          this.ticksSinceVelocity = 0;
        }
      }
      if (event.getPacket() instanceof C09PacketHeldItemChange) {
        this.blockingState = false;
        if (this.isBlocking) {
          mc.thePlayer.stopUsingItem();
        }
      }
      if (this.debugLog.getValue() == 1 && mc.thePlayer != null) {
        if (event.getPacket() instanceof S06PacketUpdateHealth) {
          float packet =
              ((S06PacketUpdateHealth) event.getPacket()).getHealth() - mc.thePlayer.getHealth();
          if (packet != 0.0F && this.lastTickProcessed != mc.thePlayer.ticksExisted) {
            this.lastTickProcessed = mc.thePlayer.ticksExisted;
            ChatUtil.sendFormatted(
                String.format(
                    "%sHealth: %s&l%s&r (&otick: %d&r)&r",
                    Miau.clientName,
                    packet > 0.0F ? "&a" : "&c",
                    df.format(packet),
                    mc.thePlayer.ticksExisted));
          }
        }
      }
    }
  }

  @EventTarget
  public void onMove(MoveInputEvent event) {
    if (this.isEnabled()) {
      if (this.rotations.getValue() != 0 && RotationState.isActived()) {
        MoveUtil.fixMovement(RotationState.getRotationYawHead());
      }
      if (this.shouldAutoBlock()) {
        mc.thePlayer.movementInput.jump = false;
      }
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled() && RotationState.isActived() && this.target != null) {
      if (this.rotations.getValue() != 0) {
        event.setYaw(RotationState.getRotationYawHead());
      }
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (this.isEnabled() && RotationState.isActived() && this.target != null) {
      if (this.rotations.getValue() != 0) {
        event.setYaw(RotationState.getRotationYawHead());
      }
    }
  }

  @EventTarget
  public void onRender(Render3DEvent event) {
    if (this.targetRenderer == null) {
      this.targetRenderer = new miau.module.modules.combat.killaura.render.TargetRenderer(this);
    }
    this.targetRenderer.onRender(event);
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isBlocking) {
      event.setCancelled(true);
    } else {
      if (this.isEnabled() && this.target != null && this.canAttack()) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isBlocking) {
      event.setCancelled(true);
    } else {
      if (this.isEnabled() && this.target != null && this.canAttack()) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isBlocking) {
      event.setCancelled(true);
    } else {
      if (this.isEnabled() && this.target != null && this.canAttack()) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onCancelUse(CancelUseEvent event) {
    if (this.isBlocking) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onPostRaytrace(PostRaytraceEvent event) {
    if (!this.shouldOverrideMouseOver()) return;
    this.modifyMouseOverFromGetMouseOver(event.partialTicks);
  }

  public boolean shouldOverrideMouseOver() {
    if (!this.isEnabled()) return false;
    if (mc.thePlayer == null || mc.theWorld == null) return false;
    if (this.target == null || this.target.getEntity() == null) return false;
    if (this.target.getEntity().isDead) return false;
    double dist = RotationUtil.distanceToEntity(this.target.getEntity());
    if (dist > this.attackRange.getValue()) return false;
    if (mc.currentScreen instanceof GuiContainer) return false;
    return true;
  }

  public void modifyMouseOverFromGetMouseOver(float partialTicks) {
    if (this.target == null || this.target.getEntity() == null) return;

    Entity targetEntity = this.target.getEntity();
    Entity viewEntity = mc.getRenderViewEntity();
    if (viewEntity == null) return;

    Vec3 eyes = viewEntity.getPositionEyes(partialTicks);
    Vec3 look = viewEntity.getLook(partialTicks);
    double reach = this.attackRange.getValue();
    Vec3 rayEnd = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);

    float border = targetEntity.getCollisionBorderSize();
    AxisAlignedBB bb = targetEntity.getEntityBoundingBox().expand(border, border, border);
    MovingObjectPosition intercept = bb.calculateIntercept(eyes, rayEnd);
    boolean inside = bb.isVecInside(eyes);
    if (!inside && intercept == null) return;

    Vec3 hitVec = inside ? (intercept == null ? eyes : intercept.hitVec) : intercept.hitVec;

    if (!this.throughWalls.getValue()) {
      MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, hitVec, false, false, true);
      if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
        return;
      }
    }

    mc.objectMouseOver = new MovingObjectPosition(targetEntity, hitVec);
    mc.pointedEntity = targetEntity;
  }

  @Override
  public void onEnabled() {
    this.target = null;
    this.switchTick = 0;
    this.hitRegistered = false;
    this.attackDelayMS = 0L;
    this.blockTick = 0;
    this.watchdogTick = 0;
    this.rightHoldActive = false;
  }

  @Override
  public void onDisabled() {
    for (AutoBlockMode mode : this.autoBlockModes) {
      mode.onDisable();
    }
    this.targetMap.clear();
    Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    this.blockingState = false;
    this.isBlocking = false;
    this.fakeBlockState = false;
  }

  @Override
  public void verifyValue(String value) {}

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }

  public final java.util.Map<Integer, LastAttackData> targetMap = new java.util.HashMap<>();

  private double getDamage(EntityLivingBase target) {
    float baseDamage = 1.0F;
    if (mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage) != null) {
      baseDamage =
          (float)
              mc.thePlayer
                  .getEntityAttribute(SharedMonsterAttributes.attackDamage)
                  .getAttributeValue();
    }
    float enchantmentBonus = 0.0F;
    if (mc.thePlayer.getHeldItem() != null) {
      enchantmentBonus =
          EnchantmentHelper.getModifierForCreature(
              mc.thePlayer.getHeldItem(),
              target != null ? target.getCreatureAttribute() : EnumCreatureAttribute.UNDEFINED);
    }
    boolean isCritical =
        mc.thePlayer.fallDistance > 0.0F
            && !mc.thePlayer.onGround
            && !mc.thePlayer.isOnLadder()
            && !mc.thePlayer.isInWater()
            && !mc.thePlayer.isPotionActive(Potion.blindness)
            && mc.thePlayer.ridingEntity == null;
    if (isCritical && baseDamage > 0.0F) {
      baseDamage *= 1.5F;
    }
    baseDamage += enchantmentBonus;
    return baseDamage;
  }
}