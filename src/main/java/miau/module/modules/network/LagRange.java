package miau.module.modules.network;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.mixin.IAccessorRenderManager;
import miau.module.Module;
import miau.module.modules.player.BedNuker;
import miau.module.modules.player.Scaffold;
import miau.module.modules.render.HUD;
import miau.property.properties.*;
import miau.util.player.ItemUtil;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public class LagRange extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final double MINIMUM_DISTANCE = 3.0;

  public final IntProperty delay = new IntProperty("delay", 150, 0, 1000);
  public final FloatProperty range = new FloatProperty("range", 10.0F, 3.0F, 100.0F);
  public final ModeProperty mode =
      new ModeProperty("mode", 0, new String[] {"Delay", "Test", "Lag"});
  public final IntProperty blinkTick =
      new IntProperty("blink-tick", 3, 0, 10, () -> this.mode.getValue() == 2);
  public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
  public final BooleanProperty allowTools =
      new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
  public final BooleanProperty botCheck = new BooleanProperty("bot-check", true);
  public final BooleanProperty teams = new BooleanProperty("teams", true);
  public final ModeProperty showPosition =
      new ModeProperty("show-position", 0, new String[] {"NONE", "DEFAULT", "HUD"});

  public final BooleanProperty sprintReset =
      new BooleanProperty("sprint-reset", true, () -> this.mode.getValue() == 1);
  public final BooleanProperty blockSword =
      new BooleanProperty("block-sword", true, () -> this.mode.getValue() == 1);
  public final BooleanProperty splashPotion =
      new BooleanProperty("splash-potion", true, () -> this.mode.getValue() == 1);

  private int tickIndex = -1;
  private long delayCounter = 0L;
  private boolean hasTarget = false;
  private Vec3 lastPosition = null;
  private Vec3 currentPosition = null;

  private boolean isLagging = false;
  private int lastSelfHurtTime = 0;
  private int lastTargetHurtTime = 0;
  private int hitMarkedEntityId = -1;
  private boolean lastSprintState = false;
  private boolean lastBlockingState = false;
  private double lastDistSq = -1;
  private EntityPlayer currentTarget = null;
  private long lagStartTime = 0L;
  private final java.util.Set<Packet<?>> packetFastTrack =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
  private Vec3 indicatorFrom = null;
  private Vec3 indicatorTo = null;
  private long indicatorStartMs = 0L;

  private boolean isValidTarget(EntityPlayer entityPlayer) {
    if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
      if (entityPlayer == mc.getRenderViewEntity()
          || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
        return false;
      } else if (entityPlayer.deathTime > 0) {
        return false;
      } else if (TeamUtil.isFriend(entityPlayer)) {
        return false;
      } else {
        return (!this.teams.getValue() || !TeamUtil.isSameTeam(entityPlayer))
            && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
      }
    } else {
      return false;
    }
  }

  private boolean shouldResetOnPacket(Packet<?> packet) {
    if (packet instanceof C02PacketUseEntity) {
      return true;
    } else if (packet instanceof C07PacketPlayerDigging) {
      return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
    } else if (packet instanceof C08PacketPlayerBlockPlacement) {
      ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
      return item == null || !(item.getItem() instanceof ItemSword);
    } else {
      return false;
    }
  }

  public LagRange() {
    super("LagRange", false);
  }

  private void resetOldMiau() {
    this.tickIndex = -1;
    if (this.mode.getValue() == 2) {
      Miau.blinkManager.setBlinkState(false, miau.enums.BlinkModules.BLINK);
    }
  }

  private void tickOldMiau() {
    if (this.mode.getValue() == 0) Miau.lagManager.setDelay(0);
    this.hasTarget = false;

    Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
    if (scaffold != null && scaffold.isEnabled()) {
      resetOldMiau();
      return;
    }

    BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
    if ((bedNuker.isEnabled() && bedNuker.isReady())
        || ((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
        || (mc.thePlayer.isUsingItem() && !mc.thePlayer.isBlocking())) {
      resetOldMiau();
      return;
    }

    boolean weaponOk =
        !this.weaponsOnly.getValue()
            || ItemUtil.hasRawUnbreakingEnchant()
            || this.allowTools.getValue() && ItemUtil.isHoldingTool();
    if (!weaponOk) {
      resetOldMiau();
      return;
    }

    List<EntityPlayer> players =
        mc.theWorld.loadedEntityList.stream()
            .filter(entity -> entity instanceof EntityPlayer)
            .map(entity -> (EntityPlayer) entity)
            .filter(this::isValidTarget)
            .collect(Collectors.toList());

    if (players.isEmpty()) {
      resetOldMiau();
      return;
    }

    double height = mc.thePlayer.getEyeHeight();
    Vec3 eyePosition = Miau.lagManager.getLastPosition().addVector(0.0, height, 0.0);
    Vec3 targetEyePosition =
        new Vec3(
            mc.thePlayer.lastTickPosX,
            mc.thePlayer.lastTickPosY + height,
            mc.thePlayer.lastTickPosZ);
    Vec3 playerEyePosition =
        new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);

    for (EntityPlayer player : players) {
      double distance = RotationUtil.distanceToBox(player, playerEyePosition);
      if (!(distance > (double) this.range.getValue())) {
        double targetDist = RotationUtil.distanceToBox(player, targetEyePosition);
        double eyeDist = RotationUtil.distanceToBox(player, eyePosition);
        if (distance < targetDist || distance < eyeDist) {
          if (this.tickIndex < 0) {
            this.tickIndex = 0;
            for (this.delayCounter = this.delayCounter + (long) this.delay.getValue();
                this.delayCounter > 0L;
                this.delayCounter = this.delayCounter - 50) {
              this.tickIndex++;
            }
          }
          if (this.mode.getValue() == 2) {
            Miau.blinkManager.setBlinkState(true, miau.enums.BlinkModules.BLINK);
            if (Miau.blinkManager.countMovement() > this.blinkTick.getValue().longValue()) {
              Miau.blinkManager.setBlinkState(false, miau.enums.BlinkModules.BLINK);
            }
          } else if (this.mode.getValue() == 0) {
            Miau.lagManager.setDelay(this.tickIndex);
          }
          this.hasTarget = true;
          return;
        }
      }
    }
    resetOldMiau();
  }

  private EntityPlayer getMouseOverTarget(double rangeSq) {
    if (mc.objectMouseOver != null
        && mc.objectMouseOver.typeOfHit
            == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY
        && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) mc.objectMouseOver.entityHit;
      if (isValidTarget(player)) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double distSq = RotationUtil.distanceToBox(player, eyePos);
        if (distSq <= rangeSq) {
          return player;
        }
      }
    }
    return null;
  }

  private void startLag() {
    if (!isLagging) {
      this.isLagging = true;
      this.lagStartTime = System.currentTimeMillis();
      Miau.lagManager.fastTrackSet = null;
    }
    Miau.lagManager.setDelayMs(this.delay.getValue());
  }

  private void flushLag() {
    if (!isLagging) return;
    Miau.lagManager.fastTrackSet = this.packetFastTrack;
    Miau.lagManager.packetQueue.forEach(lagPacket -> packetFastTrack.add(lagPacket.packet));
    Miau.lagManager.resetDelay();
    this.tickIndex = -1;
    this.delayCounter = 0L;
    this.isLagging = false;
    this.lagStartTime = 0L;
    clearIndicator();
  }

  private boolean sameTarget(EntityPlayer nextTarget) {
    if (currentTarget == null || nextTarget == null) {
      return currentTarget == nextTarget;
    }
    return currentTarget.getEntityId() == nextTarget.getEntityId();
  }

  private boolean isMoving() {
    return mc.thePlayer.moveForward != 0.0f || mc.thePlayer.moveStrafing != 0.0f;
  }

  private void clearIndicator() {
    indicatorFrom = null;
    indicatorTo = null;
    indicatorStartMs = 0L;
  }

  private void clearAggroState() {
    currentTarget = null;
    lastDistSq = -1;
    isLagging = false;
    lastSelfHurtTime = 0;
    lastTargetHurtTime = 0;
    hitMarkedEntityId = -1;
    lastSprintState = false;
    lastBlockingState = false;
    lagStartTime = 0L;
    packetFastTrack.clear();
    if (Miau.lagManager.fastTrackSet == this.packetFastTrack) {
      Miau.lagManager.fastTrackSet = null;
    }
    clearIndicator();
  }

  private void tickAggressive() {
    Miau.lagManager.resetDelay();
    this.hasTarget = false;

    Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
    if (scaffold != null && scaffold.isEnabled()) {
      if (isLagging) flushLag();
      return;
    }

    BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
    if ((bedNuker.isEnabled() && bedNuker.isReady())
        || ((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
        || (mc.thePlayer.isUsingItem() && !mc.thePlayer.isBlocking())) {
      if (isLagging) flushLag();
      return;
    }

    boolean weaponOk =
        !this.weaponsOnly.getValue()
            || ItemUtil.hasRawUnbreakingEnchant()
            || this.allowTools.getValue() && ItemUtil.isHoldingTool();
    if (!weaponOk) {
      if (isLagging) flushLag();
      return;
    }

    List<EntityPlayer> players =
        mc.theWorld.loadedEntityList.stream()
            .filter(entity -> entity instanceof EntityPlayer)
            .map(entity -> (EntityPlayer) entity)
            .filter(this::isValidTarget)
            .collect(Collectors.toList());

    if (players.isEmpty()) {
      if (isLagging) flushLag();
      this.currentTarget = null;
      this.lastDistSq = -1;
      return;
    }

    double rangeSq = (double) this.range.getValue();
    EntityPlayer nextTarget = getMouseOverTarget(rangeSq);
    double closestDist = Double.MAX_VALUE;

    if (nextTarget == null) {
      Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
      for (EntityPlayer player : players) {
        double dist = RotationUtil.distanceToBox(player, eyePos);
        if (dist < closestDist) {
          closestDist = dist;
          nextTarget = player;
        }
      }
    } else {
      closestDist = RotationUtil.distanceToBox(nextTarget, mc.thePlayer.getPositionEyes(1.0f));
    }

    if (nextTarget == null || closestDist > rangeSq) {
      if (isLagging) flushLag();
      this.currentTarget = null;
      this.lastDistSq = -1;
      return;
    }

    if (!sameTarget(nextTarget)) {
      if (isLagging) flushLag();
      this.lastDistSq = -1;
      this.hitMarkedEntityId = -1;
      this.lastTargetHurtTime = nextTarget.hurtTime;
    }
    this.currentTarget = nextTarget;

    double dist = closestDist;
    int selfHurtTime = mc.thePlayer.hurtTime;
    int targetHurtTime = currentTarget.hurtTime;
    boolean moving = isMoving();

    if (isLagging) {

      if (dist > (double) this.range.getValue()) {
        flushLag();
        lastDistSq = dist;
        lastTargetHurtTime = targetHurtTime;
        return;
      }

      if (this.lastDistSq >= 0 && dist >= this.lastDistSq) {
        boolean hitHold =
            this.hitMarkedEntityId == this.currentTarget.getEntityId()
                && dist <= MINIMUM_DISTANCE
                && selfHurtTime == 0;
        if (!hitHold) {
          flushLag();
          lastDistSq = dist;
          lastTargetHurtTime = targetHurtTime;
          return;
        }
      }

      if (selfHurtTime > this.lastSelfHurtTime) {
        flushLag();
        this.hitMarkedEntityId = -1;
        this.lastSelfHurtTime = selfHurtTime;
        lastDistSq = dist;
        lastTargetHurtTime = targetHurtTime;
        return;
      }
      this.lastSelfHurtTime = selfHurtTime;

      if (!weaponOk) {
        flushLag();
        lastDistSq = dist;
        lastTargetHurtTime = targetHurtTime;
        return;
      }

      if (this.sprintReset.getValue()) {
        boolean sprintingNow = mc.thePlayer.isSprinting();
        if (sprintingNow && !this.lastSprintState) {
          flushLag();
          this.lastSprintState = sprintingNow;
          lastDistSq = dist;
          lastTargetHurtTime = targetHurtTime;
          return;
        }
        this.lastSprintState = sprintingNow;
      }

      if (this.blockSword.getValue()) {
        boolean blockingNow = mc.thePlayer.isBlocking();
        if (blockingNow && !this.lastBlockingState) {
          flushLag();
          this.lastBlockingState = blockingNow;
          lastDistSq = dist;
          lastTargetHurtTime = targetHurtTime;
          return;
        }
        this.lastBlockingState = blockingNow;
      }

      if (this.splashPotion.getValue() && mc.thePlayer.isUsingItem()) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null
            && held.getItem() instanceof ItemPotion
            && ItemPotion.isSplash(held.getMetadata())) {
          flushLag();
          lastDistSq = dist;
          lastTargetHurtTime = targetHurtTime;
          return;
        }
      }

      long elapsedMs = System.currentTimeMillis() - this.lagStartTime;
      if (elapsedMs >= (long) this.delay.getValue()) {
        flushLag();
        lastDistSq = dist;
        lastTargetHurtTime = targetHurtTime;
        return;
      }

      this.lastDistSq = dist;
      this.lastTargetHurtTime = targetHurtTime;
      startLag();
      this.hasTarget = true;
      return;
    }

    if (selfHurtTime > this.lastSelfHurtTime) {
      this.hitMarkedEntityId = -1;
    }
    this.lastSelfHurtTime = selfHurtTime;
    this.lastSprintState = mc.thePlayer.isSprinting();
    this.lastBlockingState = mc.thePlayer.isBlocking();

    if (selfHurtTime == 0 && this.lastTargetHurtTime == 0 && targetHurtTime > 0) {
      this.hitMarkedEntityId = this.currentTarget.getEntityId();
    }
    this.lastTargetHurtTime = targetHurtTime;

    boolean closing = this.lastDistSq >= 0 && dist < this.lastDistSq;
    boolean outsideMinDist = dist > MINIMUM_DISTANCE;
    boolean hitMarkedHere = this.hitMarkedEntityId == this.currentTarget.getEntityId();
    boolean hitStart =
        hitMarkedHere && dist <= MINIMUM_DISTANCE && selfHurtTime == 0 && moving && weaponOk;

    this.lastDistSq = dist;

    boolean shouldStartLag =
        (selfHurtTime == 0 && weaponOk && moving && (closing && outsideMinDist || hitStart));

    if (shouldStartLag) {
      startLag();
      this.hasTarget = true;
    }
  }

  @EventTarget(Priority.LOW)
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      switch (event.getType()) {
        case PRE:
          if (this.mode.getValue() == 1) {
            tickAggressive();
          } else {
            tickOldMiau();
          }
          break;
        case POST:
          Vec3 savedPosition = Miau.lagManager.getLastPosition();
          if (this.currentPosition == null) {
            this.lastPosition = savedPosition;
          } else {
            this.lastPosition = this.currentPosition;
          }
          this.currentPosition = savedPosition;
          break;
        default:
          break;
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      if (event.getType() != EventType.SEND) return;

      if (this.mode.getValue() == 1) {
        Packet<?> packet = event.getPacket();
        if (this.shouldResetOnPacket(packet)) {
          if (isLagging) {
            flushLag();
          }
          Miau.lagManager.resetDelay();
        }
      } else {
        if (this.shouldResetOnPacket(event.getPacket())) {
          Miau.lagManager.setDelay(0);
          this.tickIndex = -1;
        }
      }
    }
  }

  @EventTarget(Priority.HIGH)
  public void onRender3D(Render3DEvent event) {
    if (this.isEnabled()
        && this.hasTarget
        && this.lastPosition != null
        && this.currentPosition != null) {

      if (this.showPosition.getValue() != 0 && mc.gameSettings.thirdPersonView != 0) {
        Color color = new Color(-1);
        switch (this.showPosition.getValue()) {
          case 1:
            color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
            break;
          case 2:
            color =
                ((HUD) Miau.moduleManager.modules.get(HUD.class))
                    .getColor(System.currentTimeMillis());
            break;
        }
        renderBox(event, color);
      }
    }
  }

  private void renderBox(Render3DEvent event, Color color) {
    double x =
        RenderUtil.lerpDouble(
            this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
    double y =
        RenderUtil.lerpDouble(
            this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
    double z =
        RenderUtil.lerpDouble(
            this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());
    float size = mc.thePlayer.getCollisionBorderSize();
    AxisAlignedBB aabb =
        new AxisAlignedBB(
                x - (double) mc.thePlayer.width / 2.0,
                y,
                z - (double) mc.thePlayer.width / 2.0,
                x + (double) mc.thePlayer.width / 2.0,
                y + (double) mc.thePlayer.height,
                z + (double) mc.thePlayer.width / 2.0)
            .expand(size, size, size)
            .offset(
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
    RenderUtil.enableRenderState();
    RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
    RenderUtil.disableRenderState();
  }

  @Override
  public void onDisabled() {
    if (this.mode.getValue() == 1) {
      flushLag();
      clearAggroState();
    }
    if (this.mode.getValue() == 2) {
      Miau.blinkManager.setBlinkState(false, miau.enums.BlinkModules.BLINK);
    }
    Miau.lagManager.setDelay(0);
    this.tickIndex = -1;
    this.delayCounter = 0L;
    this.hasTarget = false;
    this.lastPosition = null;
    this.currentPosition = null;
  }

  @Override
  public String[] getSuffix() {
    if (this.mode.getValue() == 2) {
      return new String[] {this.blinkTick.getValue().toString()};
    }
    return new String[] {String.format("%dms", this.delay.getValue())};
  }
}
