package miau.module.modules.player;

import java.awt.*;
import java.util.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import miau.*;
import miau.enums.*;
import miau.event.*;
import miau.event.impl.*;
import miau.event.types.*;
import miau.management.*;
import miau.module.*;
import miau.module.modules.movement.*;
import miau.module.modules.player.scaffold.*;
import miau.module.modules.player.scaffold.ScaffoldPlacementUtil.*;
import miau.module.modules.player.scaffold.features.*;
import miau.module.modules.player.scaffold.rotations.*;
import miau.module.modules.render.*;
import miau.property.*;
import miau.property.properties.*;
import miau.util.client.*;
import miau.util.font.*;
import miau.util.math.*;
import miau.util.player.*;
import miau.util.shader.*;
import miau.util.world.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.settings.*;
import net.minecraft.item.*;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraft.world.WorldSettings.*;

public class Scaffold extends Module {
  public static final Minecraft mc = Minecraft.getMinecraft();
  private static final int ROTATION_BETA = 4;
  public static final double[] placeOffsets =
      new double[] {
        0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875, 0.53125, 0.59375,
        0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
      };

  public final RotationHandler rotationHandler = new RotationHandler(this);
  public final KeepYFeature keepYFeature = new KeepYFeature(this);
  public final TowerFeature towerFeature = new TowerFeature(this);
  public final SneakFeature sneakFeature = new SneakFeature(this);
  public final SafeWalkFeature safeWalkFeature = new SafeWalkFeature(this);
  public final BetaFeature betaFeature = new BetaFeature(this);
  public final MultiPlaceFeature multiPlaceFeature = new MultiPlaceFeature(this);

  public final BlockRenderFeature blockRenderFeature = new BlockRenderFeature(this);
  private final List<ScaffoldComponent> components = new ArrayList<>();

  public final ScaffoldOptions options = new ScaffoldOptions();

  public class ScaffoldOptions {

    private boolean isKeepYActive() {
      int val = keepYFeature.keepY.getValue();
      return val == 3 || val == 4 || val == 5;
    }

    public final FloatProperty tellystartrotationminspeed =
        new FloatProperty(
            "telly-start-rotation-min-speed", 40.0F, 1.0F, 180.0F, this::isKeepYActive);
    public final FloatProperty tellystartrotationmaxspeed =
        new FloatProperty(
            "telly-start-rotation-max-speed", 95.0F, 1.0F, 180.0F, this::isKeepYActive);
    public final FloatProperty tellynormalrotationminspeed =
        new FloatProperty(
            "telly-normal-rotation-min-speed", 30.0F, 1.0F, 180.0F, this::isKeepYActive);
    public final FloatProperty tellynormalrotationmaxspeed =
        new FloatProperty(
            "telly-normal-rotation-max-speed", 35.0F, 1.0F, 180.0F, this::isKeepYActive);
    public final BooleanProperty movementCorrection =
        new BooleanProperty("movement-correction", true);
        
    public final ModeProperty sprintMode =
        new ModeProperty("sprint", 0, new String[] {"VANILLA", "OFF_GROUND", "ON_GROUND", "NONE", "HypixelBeta", "Hypixel", "OldIntave"});
        
    public final BooleanProperty jumpWhenUse =
        new BooleanProperty("jump-when-use", true, () -> {
            int mode = sprintMode.getValue();
            return mode == 0 || mode == 1 || mode == 2 || mode == 5; 
        });

    public final ModeProperty swingMode =
        new ModeProperty("swing-mode", 0, new String[] {"SILENT", "NORMAL", "NONE"});

    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
    public final FloatProperty placeDelay = new FloatProperty("place-delay", 0.0F, 0.0F, 5.0F);

    public List<Property<?>> getProperties() {
      List<Property<?>> list = new ArrayList<>();
      list.add(tellystartrotationminspeed);
      list.add(tellystartrotationmaxspeed);
      list.add(tellynormalrotationminspeed);
      list.add(tellynormalrotationmaxspeed);
      list.add(movementCorrection);
      list.add(sprintMode);
      list.add(jumpWhenUse);
      list.add(swingMode);

      list.add(speedMotion);
      list.add(blockCounter);
      list.add(placeDelay);
      return list;
    }
  }

  public int rotationTick = 0;
  public int lastSlot = -1;
  public int blockCount = -1;
  public float animationProgress = 0f;
  public long lastFrame = System.currentTimeMillis();
  public float yaw = -180.0F;
  public float pitch = 0.0F;
  public boolean canRotate = false;
  public int towerTick = 0;
  public int towerDelay = 0;
  public int stage = 0;
  public boolean shouldKeepY = false;
  public boolean placedThisTick = false;

  public boolean towering = false;
  public EnumFacing targetFacing = null;
  public int startY = 0;
  public float placeYaw, placePitch;
  public float bridgeYaw = Float.NaN;
  public float lastMoveFixPacketYaw = Float.NaN;
  public float lastPlacedAbsPacketYawDelta = Float.NaN;
  public int duplicatePlaceRotNudgeSign = 1;

  public final BooleanProperty vulcanDisabler = new BooleanProperty("vulcan-disabler", false, () -> rotationHandler.rotationMode.getValue() == 5);

  public Scaffold() {
    super("Scaffold", false);
    components.add(sneakFeature);
    components.add(keepYFeature);
    components.add(multiPlaceFeature);
    components.add(safeWalkFeature);
    components.add(towerFeature);
    components.add(betaFeature);
    components.add(blockRenderFeature);
  }

  public int getSlot() {
    return this.lastSlot;
  }

  public int getBlockCount() {
    return this.blockCount;
  }

  public float getSpeed() {
    return (float) options.speedMotion.getValue() / 100.0F;
  }

  public float getCurrentYaw() {
    return MoveUtil.adjustYaw(
        mc.thePlayer.rotationYaw,
        (float) MoveUtil.getForwardValue(),
        (float) MoveUtil.getLeftValue());
  }

  public boolean isTowering() {
    if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
      boolean keepYActive =
          keepYFeature.keepY.getValue() == 3 || keepYFeature.keepY.getValue() == 4;
      boolean towerActive = towerFeature.tower.getValue() == 3;
      return keepYActive && this.stage > 0
          || towerActive && mc.gameSettings.keyBindJump.isKeyDown();
    }
    return false;
  }

  public boolean isRightClickHeld() {
    return mc.gameSettings != null && mc.gameSettings.keyBindUseItem.isKeyDown();
  }

  private boolean canPlace() {
    BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
    if (bedNuker.isEnabled() && bedNuker.isReady()) return false;
    LongJump longJump = (LongJump) Miau.moduleManager.modules.get(LongJump.class);
    return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
  }

  private boolean shouldStopSprint() {
    if (betaFeature.isBetaMode() && !betaFeature.isBetaTellyMode()) return true;
    if (isTowering()) return false;
    int k = keepYFeature.keepY.getValue();
    boolean stageActive = k == 1 || k == 2 || k == 3 || k == 5;
    int sprint = options.sprintMode.getValue();
    
    if ((!stageActive || this.stage <= 0) && sprint == 3) return true;
    if (sprint == 5 || sprint == 2) return !mc.thePlayer.onGround; 
    if (sprint == 1) return mc.thePlayer.onGround; 
    
    return false;
  }

  private void applySprintMode() {
    if (shouldStopSprint()) return;
    int sprint = options.sprintMode.getValue();
    if (sprint != 3) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
    }
  }

  private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
    double offset = 0.0;
    EnumFacing enumFacing = null;
    for (EnumFacing facing : EnumFacing.VALUES) {
      if (facing != EnumFacing.DOWN) {
        BlockPos pos = blockPos1.offset(facing);
        if (pos.getY() <= blockPos3.getY()) {
          double distance =
              pos.distanceSqToCenter(
                  (double) blockPos3.getX() + 0.5,
                  (double) blockPos3.getY() + 0.5,
                  (double) blockPos3.getZ() + 0.5);
          if (enumFacing == null
              || distance < offset
              || (distance == offset && facing == EnumFacing.UP)) {
            offset = distance;
            enumFacing = facing;
          }
        }
      }
    }
    return enumFacing;
  }

  public BlockData getBlockData() {
    int sy = MathHelper.floor_double(mc.thePlayer.posY);
    BlockPos targetPos =
        new BlockPos(
            MathHelper.floor_double(mc.thePlayer.posX),
            (this.stage != 0 && !this.shouldKeepY ? Math.min(sy, this.startY) : sy) - 1,
            MathHelper.floor_double(mc.thePlayer.posZ));
    if (!BlockUtil.isReplaceable(targetPos)) return null;

    ArrayList<BlockPos> positions = new ArrayList<>();
    for (int x = -4; x <= 4; x++) {
      for (int y = -4; y <= 0; y++) {
        for (int z = -4; z <= 4; z++) {
          BlockPos pos = targetPos.add(x, y, z);
          if (!BlockUtil.isReplaceable(pos)
              && !BlockUtil.isInteractable(pos)
              && !(mc.thePlayer.getDistance(
                      (double) pos.getX() + 0.5,
                      (double) pos.getY() + 0.5,
                      (double) pos.getZ() + 0.5)
                  > (double) mc.playerController.getBlockReachDistance())
              && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
            for (EnumFacing facing : EnumFacing.VALUES) {
              if (facing != EnumFacing.DOWN && BlockUtil.isReplaceable(pos.offset(facing))) {
                positions.add(pos);
                break;
              }
            }
          }
        }
      }
    }
    if (positions.isEmpty()) return null;
    positions.sort(
        Comparator.comparingDouble(
            o ->
                o.distanceSqToCenter(
                    (double) targetPos.getX() + 0.5,
                    (double) targetPos.getY() + 0.5,
                    (double) targetPos.getZ() + 0.5)));
    BlockPos bpos = positions.get(0);
    EnumFacing facing = getBestFacing(bpos, targetPos);
    return facing == null ? null : new BlockData(bpos, facing);
  }

  private MovingObjectPosition getPlacementMop(BlockData blockData, float yaw, float pitch) {
    return ScaffoldPlacementUtil.verifyPlacement(blockData, yaw, pitch);
  }

  public void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
    if (!betaFeature.canBetaPlaceNow()) return;
    ItemStack activeItem = Miau.slotComponent.getItemStack();
    if (activeItem != null && ItemUtil.isBlock(activeItem) && this.blockCount > 0) {
      if (mc.playerController.onPlayerRightClick(
          mc.thePlayer, mc.theWorld, activeItem, blockPos, enumFacing, vec3)) {
        
        int swing = options.swingMode.getValue();
        if (swing == 0) {
          mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        } else if (swing == 1) {
          mc.thePlayer.swingItem();
        }

        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) this.blockCount--;
        this.placedThisTick = true;
        blockRenderFeature.markPlaced(blockPos.offset(enumFacing));

        if (betaFeature.isBetaMode()) {
          betaFeature.betaPlaceCooldown = 1;
          betaFeature.betaPlaceTicks = 0;
        }
        sneakFeature.placements--;
        for (ScaffoldComponent comp : components) comp.onBlockPlaced();
      }
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!isEnabled()) return;
    for (ScaffoldComponent comp : components) {
      comp.onRender3D(event);
    }
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent event) {
    if (!isEnabled() || event.getType() != EventType.PRE) return;

    this.placedThisTick = false;
    betaFeature.onUpdate(event);

    if (this.rotationTick > 0) this.rotationTick--;

    if (mc.thePlayer.onGround) sneakFeature.ticksOnAir = 0;
    else sneakFeature.ticksOnAir++;
    sneakFeature.calculateSneaking();

    towerFeature.onUpdate(event);
    keepYFeature.onUpdate(event);

    if (!canPlace()) return;

    // TỰ TÌM VÀ SET Ô BLOCK SILENT QUA Miau.slotComponent (KHÔNG ĐỔI CẦM TAY TRÊN CLIENT)
    ItemStack stack = Miau.slotComponent.getItemStack();
    int count = (stack != null && stack.getItem() instanceof ItemBlock) ? stack.stackSize : 0;
    this.blockCount = Math.min(this.blockCount, count);
    if (this.blockCount <= 0) {
      int slot = Miau.slotComponent.getItemIndex();
      if (this.blockCount == 0) slot--;
      for (int i = slot; i > slot - 9; i--) {
        int hotbarSlot = (i % 9 + 9) % 9;
        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
        if (candidate != null && candidate.getItem() instanceof ItemBlock) {
          Miau.slotComponent.setSlot(hotbarSlot);
          this.blockCount = candidate.stackSize;
          break;
        }
      }
    }

    float currentYaw = getCurrentYaw();
    float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
    float diagonalYaw =
        ScaffoldUtils.isDiagonal(currentYaw)
            ? yawDiffTo180
            : RotationUtil.wrapAngleDiff(
                currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                event.getYaw());

    int rotMode = rotationHandler.rotationMode.getValue();
    boolean betaMode = rotMode == ROTATION_BETA;

    if (!this.canRotate) {
      rotationHandler.handleInitialRotation(event, currentYaw, yawDiffTo180, diagonalYaw);
    }

    BlockData blockData = getBlockData();
    Vec3 hitVec = null;

    if (blockData != null) {
      float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
      PlacementAim aim =
          ScaffoldPlacementUtil.resolveAim(blockData, baseYaw, this.pitch, placeOffsets);
      if (aim != null) {
        this.yaw = aim.yaw;
        this.pitch = aim.pitch;
        hitVec = aim.hitVec;
        this.canRotate = true;
      } else if (betaMode) {
        this.canRotate = false;
      }
    }

    boolean towerRotating = this.towering || isTowering();

    if (this.canRotate
        && MoveUtil.isForwardPressed()
        && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F
        && blockData != null
        && (rotMode == 2 || rotMode == 3)) {
      float styleYaw = rotMode == 2 ? yawDiffTo180 : diagonalYaw;
      float[] bridgeGcd =
          RotationUtil.flexRotation(styleYaw, this.pitch, event.getYaw(), event.getPitch());
      this.bridgeYaw = bridgeGcd[0];
      this.yaw = bridgeGcd[0];
      this.pitch = bridgeGcd[1];
      PlacementAim styled =
          ScaffoldPlacementUtil.resolveAim(blockData, bridgeGcd[0], bridgeGcd[1], placeOffsets);
      if (styled == null) {
        styled =
            ScaffoldPlacementUtil.resolveAim(
                blockData, event.getYaw(), event.getPitch(), placeOffsets);
      }
      if (styled != null) {
        this.yaw = styled.yaw;
        this.pitch = styled.pitch;
        this.placePitch = styled.pitch;
        hitVec = styled.hitVec;
      } else {
        hitVec = null;
      }
    }

    boolean willPlaceThisTick =
        blockData != null
            && hitVec != null
            && this.rotationTick <= 0
            && this.sneakFeature.ticksOnAir
                >= RandomUtil.nextFloat(
                    options.placeDelay.getValue(), options.placeDelay.getSecondValue());
    rotationHandler.handleUpdateRotation(
        event, yawDiffTo180, diagonalYaw, towerRotating, willPlaceThisTick);

    if (betaMode && blockData != null && hitVec != null) {
      MovingObjectPosition verifiedMop = getPlacementMop(blockData, this.placeYaw, this.placePitch);
      if (verifiedMop == null) {
        verifiedMop = getPlacementMop(blockData, this.yaw, this.pitch);
      }
      hitVec = verifiedMop != null ? verifiedMop.hitVec : null;
    }

    if (willPlaceThisTick) {
      place(blockData.blockPos, blockData.facing, hitVec);
      if (multiPlaceFeature.multiplace.getValue()) {
        for (int i = 0; i < 3; i++) {
          blockData = getBlockData();
          if (blockData == null) break;
          MovingObjectPosition mop = getPlacementMop(blockData, this.yaw, this.pitch);
          if (mop != null) {
            place(blockData.blockPos, blockData.facing, mop.hitVec);
          } else {
            hitVec = BlockUtil.getClickVec(blockData.blockPos, blockData.facing);
            double dx = hitVec.xCoord - mc.thePlayer.posX;
            double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
            double dz = hitVec.zCoord - mc.thePlayer.posZ;
            float[] rots =
                RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
            if (!(Math.abs(rots[0] - this.yaw) < 120.0F)
                || !(Math.abs(rots[1] - this.pitch) < 60.0F)) break;
            mop = getPlacementMop(blockData, rots[0], rots[1]);
            if (mop == null) break;
            place(blockData.blockPos, blockData.facing, mop.hitVec);
          }
        }
      }
    }

    if (this.targetFacing != null) {
      if (betaMode) this.targetFacing = null;
      else if (this.rotationTick <= 0 && !this.placedThisTick) {
        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos belowPlayer = new BlockPos(px, py - 1, pz);
        hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
        place(belowPlayer, this.targetFacing, hitVec);
      }
      this.targetFacing = null;
    } else if ((keepYFeature.keepY.getValue() == 2
            || keepYFeature.keepY.getValue() == 3
            || keepYFeature.keepY.getValue() == 4)
        && this.stage > 0
        && !mc.thePlayer.onGround) {
      int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
      if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
        this.shouldKeepY = true;
        if (keepYFeature.keepY.getValue() != 4) {
          blockData = getBlockData();
          if (blockData != null && this.rotationTick <= 0 && !this.placedThisTick) {
            MovingObjectPosition mop = getPlacementMop(blockData, this.yaw, this.pitch);
            if (mop != null) place(blockData.blockPos, blockData.facing, mop.hitVec);
          }
        }
      }
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (!isEnabled()) return;
    if (betaFeature.isBetaMode() && !betaFeature.isBetaTellyMode()) {
      this.towerTick = 0;
      this.towerDelay = 0;
      if (!(keepYFeature.keepY.getValue() == 3 || keepYFeature.keepY.getValue() == 4)) return;
    }
    towerFeature.onStrafe(event);
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (!isEnabled()) return;
    betaFeature.onMoveInput(event);
    boolean applyMoveFix = options.movementCorrection.getValue();
    if (applyMoveFix
        && RotationState.isActived()
        && RotationState.getPriority() == 3.0F
        && MoveUtil.isForwardPressed()) {
      float strafeRef =
          Float.isNaN(this.lastMoveFixPacketYaw)
              ? RotationState.getSmoothedYaw()
              : this.lastMoveFixPacketYaw;
      MoveUtil.fixStrafe(strafeRef);
    }
    if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
      mc.thePlayer.movementInput.jump = true;
    }
    if (sneakFeature.slow-- > 0) {
      mc.thePlayer.movementInput.moveForward = 0.0F;
      mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!isEnabled()) return;
    betaFeature.onLivingUpdate(event);

    float speed = betaFeature.isBetaMode() && !betaFeature.isBetaTellyMode() ? 1.0F : getSpeed();
    if (speed != 1.0F) {
      if (mc.thePlayer.movementInput.moveForward != 0.0F
          && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
        mc.thePlayer.movementInput.moveForward *= (1.0F / (float) Math.sqrt(2.0));
        mc.thePlayer.movementInput.moveStrafe *= (1.0F / (float) Math.sqrt(2.0));
      }
      mc.thePlayer.movementInput.moveForward *= speed;
      mc.thePlayer.movementInput.moveStrafe *= speed;
    }
    if (shouldStopSprint()) mc.thePlayer.setSprinting(false);
    else applySprintMode();
  }

  @EventTarget
  public void onSafeWalk(SafeWalkEvent event) {
    safeWalkFeature.onSafeWalk(event);
  }

  @EventTarget
  public void onRender(Render2DEvent event) {
    if (mc.thePlayer == null) return;

    long currentFrame = System.currentTimeMillis();
    float delta = (currentFrame - lastFrame) / 1000f;
    lastFrame = currentFrame;

    boolean shouldShow = this.isEnabled() && options.blockCounter.getValue();

    float target = shouldShow ? 1f : 0f;
    animationProgress += (target - animationProgress) * 12f * delta;
    animationProgress = Math.max(0f, Math.min(1f, animationProgress));

    if (animationProgress <= 0.01f) return;

    ItemStack itemStack = null;
    int count = 0;
    ItemStack held = Miau.slotComponent.getItemStack();
    if (held != null && held.getItem() instanceof ItemBlock) {
      itemStack = held;
    }

    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.stackSize > 0) {
        Item item = stack.getItem();
        if (item instanceof ItemBlock) {
          Block block = ((ItemBlock) item).getBlock();
          if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
            count += stack.stackSize;
            if (itemStack == null) {
              itemStack = stack;
            }
          }
        }
      }
    }

    if (itemStack == null) return;

    ScaledResolution sr = new ScaledResolution(mc);
    String amount = String.valueOf(count);
    String info = "Blocks: " + amount;

    float textWidth = FontRepository.getHudFont(18).width(info);
    float width = 16f + 8f + textWidth + 8f;
    float height = 22f;
    float x = (sr.getScaledWidth() - width) / 2f;
    float y = sr.getScaledHeight() - 90f;

    GlStateManager.pushMatrix();

    float centerX = x + width / 2f;
    float centerY = y + height / 2f;
    GlStateManager.translate(centerX, centerY, 0);
    GlStateManager.scale(animationProgress, animationProgress, 1f);
    GlStateManager.translate(-centerX, -centerY, 0);

    HUD hud = (HUD) Miau.moduleManager.getModule(HUD.class);

    int bgAlpha = (int) (150 * animationProgress);
    RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, bgAlpha));

    GlStateManager.pushMatrix();
    RenderHelper.enableGUIStandardItemLighting();
    mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, (int) x + 4, (int) y + 3);
    RenderHelper.disableStandardItemLighting();
    GlStateManager.popMatrix();

    GlStateManager.enableBlend();
    int textAlpha = (int) (255 * animationProgress);
    float fontY = y + (height / 2f) - (FontRepository.getHudFont(18).height() / 2f);
    float textX = x + 24f;

    FontRepository.getHudFont(18)
        .drawWithShadow(info, textX, fontY, new Color(200, 200, 200, textAlpha).getRGB());

    GlStateManager.popMatrix();
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onSwap(SwapItemEvent event) {
    if (this.isEnabled()) {
      this.lastSlot = event.setSlot(this.lastSlot);
      event.setCancelled(true);
    }
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer != null) {
      this.lastSlot = Miau.slotComponent.getItemIndex();
      
      int sMode = options.sprintMode.getValue();
      if (mc.thePlayer.onGround && options.jumpWhenUse.getValue() && (sMode == 0 || sMode == 1 || sMode == 2 || sMode == 5)) {
        mc.thePlayer.jump();
      }
    } else {
      this.lastSlot = -1;
    }
    this.blockCount = -1;
    this.rotationTick = 3;
    this.yaw = -180.0F;
    this.pitch = 0.0F;
    this.canRotate = false;
    this.towerTick = 0;
    this.towerDelay = 0;
    this.towering = false;
    this.sneakFeature.sneakingTicks = -1;
    this.sneakFeature.placements = 0;
    this.sneakFeature.pause = 0;
    this.sneakFeature.slow = 0;
    this.sneakFeature.ticksOnAir = 0;
    this.betaFeature.betaAirTicks = 0;
    this.betaFeature.betaGroundTicks = 0;
    this.betaFeature.betaPlaceCooldown = 0;
    this.lastPlacedAbsPacketYawDelta = Float.NaN;
    this.duplicatePlaceRotNudgeSign = 1;
    this.betaFeature.lastBetaSentYaw = Float.NaN;
    this.betaFeature.lastBetaSentPitch = Float.NaN;
    this.betaFeature.lastBetaPitchQuotient = 0L;
    this.betaFeature.betaPlaceTicks = 999;
    for (ScaffoldComponent comp : components) comp.onEnable();
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null && this.lastSlot != -1) {
      mc.thePlayer.inventory.currentItem = this.lastSlot;
    }
    Miau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    this.sneakFeature.sneakingTicks = -1;
    this.sneakFeature.placements = 0;
    this.sneakFeature.pause = 0;
    this.sneakFeature.slow = 0;
    this.sneakFeature.ticksOnAir = 0;
    this.betaFeature.betaAirTicks = 0;
    this.betaFeature.betaGroundTicks = 0;
    this.betaFeature.betaPlaceCooldown = 0;
    this.betaFeature.lastBetaSentYaw = Float.NaN;
    this.betaFeature.lastBetaSentPitch = Float.NaN;
    this.betaFeature.lastBetaPitchQuotient = 0L;
    this.betaFeature.betaPlaceTicks = 999;
    this.lastPlacedAbsPacketYawDelta = Float.NaN;
    this.duplicatePlaceRotNudgeSign = 1;
    for (ScaffoldComponent comp : components) comp.onDisable();

    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    props.addAll(rotationHandler.getProperties());
    for (Property<?> prop : options.getProperties()) {
      java.util.function.BooleanSupplier original = prop.getVisibleChecker();
      if (original != null) prop.setVisibleChecker(original);
      props.add(prop);
    }
    for (ScaffoldComponent comp : components) {
      for (Property<?> prop : comp.getProperties()) {
        java.util.function.BooleanSupplier original = prop.getVisibleChecker();
        if (original != null) prop.setVisibleChecker(original);
        props.add(prop);
      }
    }
    return props;
  }

  public static class BlockData {
    public final BlockPos blockPos;
    public final EnumFacing facing;

    public BlockData(BlockPos blockPos, EnumFacing facing) {
      this.blockPos = blockPos;
      this.facing = facing;
    }
  }
}