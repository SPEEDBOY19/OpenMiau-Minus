package miau.module.modules.player;

import com.google.common.base.CaseFormat;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import miau.Miau;
import miau.enums.ChatColors;
import miau.enums.DelayModules;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.Module;
import miau.module.modules.render.BedESP;
import miau.module.modules.render.HUD;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class BedNuker extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final long WHITELIST_SCAN_DELAY_MS = 1000L;
  private final TimerUtil timer = new TimerUtil();
  private final ArrayList<BlockPos> bedWhitelist = new ArrayList<BlockPos>();
  private final Color colorRed = new Color(ChatColors.RED.toAwtColor());
  private final Color colorYellow = new Color(ChatColors.YELLOW.toAwtColor());
  private final Color colorGreen = new Color(ChatColors.GREEN.toAwtColor());
  private BlockPos targetBed = null;
  private int breakStage = 0;
  private int tickCounter = 0;
  private float breakProgress = 0.0F;
  private boolean isBed = false;
  private int savedSlot = -1;
  private boolean readyToBreak = false;
  private boolean breaking = false;
  private boolean waitingForStart = false;
  private long whitelistScanAt = -1L;
  private final ArrayDeque<BlockPos> bfsQueue = new ArrayDeque<BlockPos>(64);
  private final HashSet<BlockPos> bfsVisited = new HashSet<BlockPos>(128);
  private final ArrayList<BlockPos> bfsCandidates = new ArrayList<BlockPos>(32);
  private BlockPos[] currentBedPair = null;
  private int bypassState = 0; // 0=idle, 1=waiting for bed direct hit, 2=timed out, trying BFS
  private long bypassStateSince = -1L;
  private static final long WAIT_TIMEOUT_MS = 3500L;
  private static final long TIMEDOUT_COOLDOWN_MS = 2000L;

  public final ModeProperty mode =
      new ModeProperty("mode", 0, new String[] {"NORMAL", "SWAP", "BYPASS"});
  public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 6.0F);
  public final PercentProperty speed = new PercentProperty("speed", 0);
  public final BooleanProperty groundSpeed = new BooleanProperty("ground-spoof", false);
  public final ModeProperty ignoreVelocity =
      new ModeProperty("ignore-velocity", 0, new String[] {"NONE", "CANCEL", "DELAY"});
  public final BooleanProperty surroundings = new BooleanProperty("surroundings", true);
  public final BooleanProperty toolCheck = new BooleanProperty("tool-check", true);
  public final BooleanProperty whiteList = new BooleanProperty("whitelist", true);
  public final BooleanProperty swing = new BooleanProperty("swing", true);
  public final ModeProperty moveFix =
      new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT", "STRICT"});
  public final ModeProperty showTarget =
      new ModeProperty("show-target", 1, new String[] {"NONE", "DEFAULT", "HUD"});
  public final ModeProperty showProgress =
      new ModeProperty("show-progress", 1, new String[] {"NONE", "DEFAULT", "HUD"});
  public final BooleanProperty stopOnAttack = new BooleanProperty("stop-on-attack", true);

  private void resetBreaking() {
    if (this.targetBed != null && mc.theWorld != null && mc.thePlayer != null) {
      mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), this.targetBed, -1);
    }
    this.targetBed = null;
    this.breakStage = 0;
    this.tickCounter = 0;
    this.breakProgress = 0.0F;
    this.isBed = false;
    this.readyToBreak = false;
    this.breaking = false;
    this.bfsQueue.clear();
    this.bfsVisited.clear();
    this.bfsCandidates.clear();
    this.currentBedPair = null;
    this.bypassState = 0;
    this.bypassStateSince = -1L;
  }

  private void scheduleWhitelistScan() {
    this.whitelistScanAt = System.currentTimeMillis() + WHITELIST_SCAN_DELAY_MS;
  }

  private void runPendingWhitelistScan() {
    if (this.whitelistScanAt == -1L || System.currentTimeMillis() < this.whitelistScanAt) {
      return;
    }
    this.whitelistScanAt = -1L;
    this.bedWhitelist.clear();
    if (mc.theWorld == null || mc.thePlayer == null) {
      return;
    }

    int sX = MathHelper.floor_double(mc.thePlayer.posX);
    int sY = MathHelper.floor_double(mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
    int sZ = MathHelper.floor_double(mc.thePlayer.posZ);
    for (int i = sX - 25; i <= sX + 25; i++) {
      for (int j = sY - 25; j <= sY + 25; j++) {
        for (int k = sZ - 25; k <= sZ + 25; k++) {
          BlockPos blockPos = new BlockPos(i, j, k);
          Block block = mc.theWorld.getBlockState(blockPos).getBlock();
          if (block instanceof BlockBed) {
            this.bedWhitelist.add(blockPos);
          }
        }
      }
    }
  }

  private float calcProgress() {
    if (this.targetBed == null) {
      return 0.0F;
    } else {
      float progress = this.breakProgress;
      if (this.groundSpeed.getValue()) {
        int slot =
            ItemUtil.findInventorySlot(
                mc.thePlayer.inventory.currentItem,
                mc.theWorld.getBlockState(this.targetBed).getBlock());
        progress =
            (float) this.tickCounter
                * this.getBreakDelta(
                    mc.theWorld.getBlockState(this.targetBed), this.targetBed, slot, true);
      }
      return Math.min(
          1.0F, progress / (1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)));
    }
  }

  private void restoreSlot() {
    if (this.savedSlot != -1) {
      mc.thePlayer.inventory.currentItem = this.savedSlot;
      this.syncHeldItem();
      this.savedSlot = -1;
    }
  }

  private void syncHeldItem() {
    int currentPlayerItem =
        ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
    if (mc.thePlayer.inventory.currentItem != currentPlayerItem) {
      mc.thePlayer.stopUsingItem();
    }
    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
  }

  private boolean hasProperTool(Block block) {
    Material material = block.getMaterial();
    if (material != Material.iron && material != Material.anvil && material != Material.rock) {
      return true;
    } else {
      for (int i = 0; i < 9; i++) {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
        if (stack != null) {
          Item item = stack.getItem();
          if (item instanceof ItemPickaxe) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private EnumFacing getHitFacing(BlockPos blockPos) {
    double x = (double) blockPos.getX() + 0.5 - mc.thePlayer.posX;
    double y =
        (double) blockPos.getY() + 0.25 - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
    double z = (double) blockPos.getZ() + 0.5 - mc.thePlayer.posZ;
    float[] rotations =
        RotationUtil.getRotationsTo(x, y, z, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], 8.0, 1.0F);
    return mop == null ? EnumFacing.UP : mop.sideHit;
  }

  private float getDigSpeed(IBlockState iBlockState, int slot, boolean boolean5) {
    ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
    float digSpeed = item == null ? 1.0F : item.getItem().getDigSpeed(item, iBlockState);
    if (digSpeed > 1.0F) {
      int enchantmentLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, item);
      if (enchantmentLevel > 0) {
        digSpeed += (float) (enchantmentLevel * enchantmentLevel + 1);
      }
    }
    if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
      digSpeed *=
          1.0F
              + (float) (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1)
                  * 0.2F;
    }
    if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
      switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
        case 0:
          digSpeed *= 0.3F;
          break;
        case 1:
          digSpeed *= 0.09F;
          break;
        case 2:
          digSpeed *= 0.0027F;
          break;
        default:
          digSpeed *= 8.1E-4F;
      }
    }
    if (mc.thePlayer.isInsideOfMaterial(Material.water)
        && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
      digSpeed /= 5.0F;
    }
    if (!boolean5) {
      digSpeed /= 5.0F;
    }
    return digSpeed;
  }

  boolean canHarvest(Block block, int slot) {
    if (block.getMaterial().isToolNotRequired()) {
      return true;
    } else {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
      return stack != null && stack.canHarvestBlock(block);
    }
  }

  private float getBreakDelta(
      IBlockState iBlockState, BlockPos blockPos, int slot, boolean boolean5) {
    Block block = iBlockState.getBlock();
    float hardness = block.getBlockHardness(mc.theWorld, blockPos);
    float boost = this.canHarvest(block, slot) ? 30.0F : 100.0F;
    return hardness < 0.0F
        ? 0.0F
        : this.getDigSpeed(iBlockState, slot, boolean5) / hardness / boost;
  }

  private float calcBlockStrength(BlockPos blockPos) {
    IBlockState blockState = mc.theWorld.getBlockState(blockPos);
    int slot =
        ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, blockState.getBlock());
    return this.getBreakDelta(blockState, blockPos, slot, mc.thePlayer.onGround);
  }

  private static class HitResult {
    final Vec3 hitVec;
    final EnumFacing facing;
    final double distance;

    HitResult(Vec3 hitVec, EnumFacing facing, double distance) {
      this.hitVec = hitVec;
      this.facing = facing;
      this.distance = distance;
    }
  }

  private static final double FACE_EPSILON = 0.005;

  private Vec3 closestPointOnFace(AxisAlignedBB aabb, EnumFacing face, Vec3 point) {
    double cx = clamp(point.xCoord, aabb.minX + FACE_EPSILON, aabb.maxX - FACE_EPSILON);
    double cy = clamp(point.yCoord, aabb.minY + FACE_EPSILON, aabb.maxY - FACE_EPSILON);
    double cz = clamp(point.zCoord, aabb.minZ + FACE_EPSILON, aabb.maxZ - FACE_EPSILON);
    switch (face) {
      case DOWN:
        return new Vec3(cx, aabb.minY, cz);
      case UP:
        return new Vec3(cx, aabb.maxY, cz);
      case NORTH:
        return new Vec3(cx, cy, aabb.minZ);
      case SOUTH:
        return new Vec3(cx, cy, aabb.maxZ);
      case WEST:
        return new Vec3(aabb.minX, cy, cz);
      case EAST:
        return new Vec3(aabb.maxX, cy, cz);
      default:
        return new Vec3(cx, cy, cz);
    }
  }

  private static double clamp(double val, double min, double max) {
    return val < min ? min : (val > max ? max : val);
  }

  private HitResult computeBestHit(BlockPos pos) {
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    double maxRange = this.range.getValue().doubleValue();
    double maxRangeSq = maxRange * maxRange;
    IBlockState state = mc.theWorld.getBlockState(pos);
    Block block = state.getBlock();
    AxisAlignedBB aabb = block.getCollisionBoundingBox(mc.theWorld, pos, state);
    if (aabb == null) {
      aabb = new AxisAlignedBB(pos, pos.add(1, 1, 1));
    }
    HitResult best = null;
    double bestDistSq = Double.MAX_VALUE;
    for (EnumFacing face : EnumFacing.values()) {
      BlockPos adjacent = pos.offset(face);
      if (!(mc.theWorld.getBlockState(adjacent).getBlock() instanceof BlockAir)
          && !(mc.theWorld.getBlockState(adjacent).getBlock() instanceof BlockBed)
          && mc.theWorld.getBlockState(adjacent).getBlock().isFullBlock()) {
        continue;
      }
      Vec3 facePoint = closestPointOnFace(aabb, face, eyes);
      double distSq = eyes.squareDistanceTo(facePoint);
      if (distSq > maxRangeSq || distSq < 0.001) continue;
      float[] rots = RotationUtil.calculate(facePoint);
      MovingObjectPosition mop = RayCastUtil.rayCast(rots[0], rots[1], maxRange, 0.0f);
      if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK && mop.getBlockPos().equals(pos)) {
        if (distSq < bestDistSq) {
          bestDistSq = distSq;
          Vec3 hitVec = (mop.hitVec != null) ? mop.hitVec : facePoint;
          best = new HitResult(hitVec, mop.sideHit, Math.sqrt(distSq));
        }
      }
    }
    return best;
  }

  private HitResult computeFallbackHit(BlockPos pos) {
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    ArrayList<Vec3> visiblePoints = this.getVisiblePoints(pos);
    if (!visiblePoints.isEmpty()) {
      Vec3 bestPoint = visiblePoints.get(0);
      double bestDistSq = eye.squareDistanceTo(bestPoint);
      for (int i = 1; i < visiblePoints.size(); i++) {
        double distSq = eye.squareDistanceTo(visiblePoints.get(i));
        if (distSq < bestDistSq) {
          bestDistSq = distSq;
          bestPoint = visiblePoints.get(i);
        }
      }
      EnumFacing facing = EnumFacing.UP;
      double cx = pos.getX() + 0.5, cy = pos.getY() + 0.28125, cz = pos.getZ() + 0.5;
      double dx = Math.abs(bestPoint.xCoord - cx);
      double dy = Math.abs(bestPoint.yCoord - cy);
      double dz = Math.abs(bestPoint.zCoord - cz);
      if (dy > dx && dy > dz) {
        facing = bestPoint.yCoord > cy ? EnumFacing.UP : EnumFacing.DOWN;
      } else if (dx > dz) {
        facing = bestPoint.xCoord > cx ? EnumFacing.EAST : EnumFacing.WEST;
      } else {
        facing = bestPoint.zCoord > cz ? EnumFacing.SOUTH : EnumFacing.NORTH;
      }
      return new HitResult(bestPoint, facing, Math.sqrt(bestDistSq));
    }

    double maxRange = this.range.getValue().doubleValue();
    double maxRangeSq = maxRange * maxRange;
    HitResult bestHit = null;
    double bestDistSq = Double.MAX_VALUE;
    for (EnumFacing face : EnumFacing.values()) {
      Block adj = mc.theWorld.getBlockState(pos.offset(face)).getBlock();
      if (adj instanceof BlockAir) {
        Vec3 hitVec =
            new Vec3(
                pos.getX() + 0.5 + face.getFrontOffsetX() * 0.49,
                pos.getY() + 0.5 + face.getFrontOffsetY() * 0.49,
                pos.getZ() + 0.5 + face.getFrontOffsetZ() * 0.49);
        double distSq = eye.squareDistanceTo(hitVec);
        if (distSq < bestDistSq && distSq <= maxRangeSq) {
          bestDistSq = distSq;
          bestHit = new HitResult(hitVec, face, Math.sqrt(distSq));
        }
      }
    }
    return bestHit;
  }

  private ArrayList<Vec3> buildRaytraceSamplePoints(AxisAlignedBB aabb) {
    ArrayList<Vec3> points = new ArrayList<Vec3>();
    final double INSET = 0.01;
    double minX = aabb.minX, minY = aabb.minY, minZ = aabb.minZ;
    double maxX = aabb.maxX, maxY = aabb.maxY, maxZ = aabb.maxZ;
    double cx = (minX + maxX) * 0.5, cy = (minY + maxY) * 0.5, cz = (minZ + maxZ) * 0.5;
    // DOWN face
    points.add(new Vec3(minX + INSET, minY, minZ + INSET));
    points.add(new Vec3(maxX - INSET, minY, minZ + INSET));
    points.add(new Vec3(minX + INSET, minY, maxZ - INSET));
    points.add(new Vec3(maxX - INSET, minY, maxZ - INSET));
    points.add(new Vec3(cx, minY, cz));
    // UP face
    points.add(new Vec3(minX + INSET, maxY, minZ + INSET));
    points.add(new Vec3(maxX - INSET, maxY, minZ + INSET));
    points.add(new Vec3(minX + INSET, maxY, maxZ - INSET));
    points.add(new Vec3(maxX - INSET, maxY, maxZ - INSET));
    points.add(new Vec3(cx, maxY, cz));
    // NORTH face
    points.add(new Vec3(minX + INSET, minY + INSET, minZ));
    points.add(new Vec3(maxX - INSET, minY + INSET, minZ));
    points.add(new Vec3(minX + INSET, maxY - INSET, minZ));
    points.add(new Vec3(maxX - INSET, maxY - INSET, minZ));
    points.add(new Vec3(cx, cy, minZ));
    // SOUTH face
    points.add(new Vec3(minX + INSET, minY + INSET, maxZ));
    points.add(new Vec3(maxX - INSET, minY + INSET, maxZ));
    points.add(new Vec3(minX + INSET, maxY - INSET, maxZ));
    points.add(new Vec3(maxX - INSET, maxY - INSET, maxZ));
    points.add(new Vec3(cx, cy, maxZ));
    // WEST face
    points.add(new Vec3(minX, minY + INSET, minZ + INSET));
    points.add(new Vec3(minX, maxY - INSET, minZ + INSET));
    points.add(new Vec3(minX, minY + INSET, maxZ - INSET));
    points.add(new Vec3(minX, maxY - INSET, maxZ - INSET));
    points.add(new Vec3(minX, cy, cz));
    // EAST face
    points.add(new Vec3(maxX, minY + INSET, minZ + INSET));
    points.add(new Vec3(maxX, maxY - INSET, minZ + INSET));
    points.add(new Vec3(maxX, minY + INSET, maxZ - INSET));
    points.add(new Vec3(maxX, maxY - INSET, maxZ - INSET));
    points.add(new Vec3(maxX, cy, cz));
    return points;
  }

  private BlockPos findRaytraceObstruction(BlockPos target) {
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    double maxRangeSq = this.range.getValue().doubleValue() * this.range.getValue().doubleValue();
    IBlockState state = mc.theWorld.getBlockState(target);
    Block block = state.getBlock();
    AxisAlignedBB aabb = block.getCollisionBoundingBox(mc.theWorld, target, state);
    if (aabb == null) {
      aabb = new AxisAlignedBB(target, target.add(1, 1, 1));
    }
    ArrayList<Vec3> candidates = this.buildRaytraceSamplePoints(aabb);
    for (Vec3 candidate : candidates) {
      if (eyes.squareDistanceTo(candidate) > maxRangeSq) continue;
      MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, candidate);
      if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) return null;
      BlockPos hitPos = mop.getBlockPos();
      if (hitPos.equals(target)) return null;
      Block hitBlock = mc.theWorld.getBlockState(hitPos).getBlock();
      if (hitBlock instanceof BlockAir
          || hitBlock instanceof BlockBed
          || hitBlock.getBlockHardness(mc.theWorld, hitPos) < 0) return null;
      return hitPos;
    }
    return null;
  }

  private boolean tryClearObstruction() {
    if (this.targetBed == null) return false;
    BlockPos obstruction = this.findRaytraceObstruction(this.targetBed);
    if (obstruction != null
        && PlayerUtil.canReach(obstruction, this.range.getValue().doubleValue())) {
      this.targetBed = obstruction;
      this.breakStage = 0;
      this.tickCounter = 0;
      this.breakProgress = 0.0F;
      this.isBed = false;
      return true;
    }
    HitResult fallbackHit = this.computeFallbackHit(this.targetBed);
    if (fallbackHit != null) {
      return true;
    }
    return false;
  }

  private BlockPos[] resolveBedPair(BlockPos bedPos) {
    IBlockState state = mc.theWorld.getBlockState(bedPos);
    if (!(state.getBlock() instanceof BlockBed)) return null;
    BlockBed.EnumPartType part = state.getValue(BlockBed.PART);
    EnumFacing facing = state.getValue(BlockBed.FACING);
    BlockPos foot =
        (part == BlockBed.EnumPartType.FOOT) ? bedPos : bedPos.offset(facing.getOpposite());
    BlockPos head = foot.offset(facing);
    return new BlockPos[] {foot, head};
  }

  private boolean isBedExposed(BlockPos[] pair) {
    for (BlockPos bp : pair) {
      for (EnumFacing f : EnumFacing.values()) {
        if (mc.theWorld.getBlockState(bp.offset(f)).getBlock() instanceof BlockAir) {
          return true;
        }
      }
    }
    return false;
  }

  private Vec3 bedCenter(BlockPos[] pair) {
    double minX = Math.min(pair[0].getX(), pair[1].getX());
    double minY = Math.min(pair[0].getY(), pair[1].getY());
    double minZ = Math.min(pair[0].getZ(), pair[1].getZ());
    double maxX = Math.max(pair[0].getX(), pair[1].getX()) + 1.0;
    double maxY = Math.max(pair[0].getY(), pair[1].getY()) + 1.0;
    double maxZ = Math.max(pair[0].getZ(), pair[1].getZ()) + 1.0;
    return new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
  }

  private ArrayList<Vec3> getVisiblePoints(BlockPos pos) {
    ArrayList<Vec3> visiblePoints = new ArrayList<Vec3>();
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    double maxDist = this.range.getValue().doubleValue();
    IBlockState state = mc.theWorld.getBlockState(pos);
    Block block = state.getBlock();
    AxisAlignedBB aabb = block.getCollisionBoundingBox(mc.theWorld, pos, state);
    if (aabb == null) {
      aabb = new AxisAlignedBB(pos, pos.add(1, 1, 1));
    }
    double minX = aabb.minX, minY = aabb.minY, minZ = aabb.minZ;
    double maxX = aabb.maxX, maxY = aabb.maxY, maxZ = aabb.maxZ;
    double cx = (minX + maxX) * 0.5, cy = (minY + maxY) * 0.5, cz = (minZ + maxZ) * 0.5;
    Vec3[] points =
        new Vec3[] {
          new Vec3(cx, cy, cz),
          new Vec3(cx, maxY - 0.01, cz),
          new Vec3(cx, minY + 0.01, cz),
          new Vec3(minX + 0.05, cy, cz),
          new Vec3(maxX - 0.05, cy, cz),
          new Vec3(cx, cy, minZ + 0.05),
          new Vec3(cx, cy, maxZ - 0.05),
          new Vec3(minX + 0.05, maxY - 0.01, minZ + 0.05),
          new Vec3(maxX - 0.05, maxY - 0.01, minZ + 0.05),
          new Vec3(minX + 0.05, maxY - 0.01, maxZ - 0.05),
          new Vec3(maxX - 0.05, maxY - 0.01, maxZ - 0.05)
        };
    for (Vec3 point : points) {
      if (eyes.distanceTo(point) > maxDist) continue;
      MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, point);
      if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK && mop.getBlockPos().equals(pos)) {
        visiblePoints.add(point);
      }
    }
    return visiblePoints;
  }

  /**
   * Checks if at least one bed of the pair is visible on the client side (raytrace to multiple
   * points).
   */
  private boolean isBedVisible(BlockPos[] pair) {
    for (BlockPos bp : pair) {
      if (!this.getVisiblePoints(bp).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private BlockPos bfsFindOutermostDefenseBlock(BlockPos[] bedPair) {
    this.bfsQueue.clear();
    this.bfsVisited.clear();
    this.bfsCandidates.clear();
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    double rangeSq = this.range.getValue().doubleValue() * this.range.getValue().doubleValue();
    for (BlockPos bp : bedPair) {
      if (this.bfsVisited.add(bp)) {
        this.bfsQueue.add(bp);
      }
    }
    int maxLayers = 10;
    for (int layer = 0; layer < maxLayers && !this.bfsQueue.isEmpty(); layer++) {
      int layerSize = this.bfsQueue.size();
      for (int i = 0; i < layerSize; i++) {
        BlockPos current = this.bfsQueue.poll();
        if (current == null) break;
        for (EnumFacing f : EnumFacing.values()) {
          BlockPos neighbor = current.offset(f);
          if (neighbor.getY() < bedPair[0].getY()) continue;
          if (!this.bfsVisited.add(neighbor)) continue;
          Block nb = mc.theWorld.getBlockState(neighbor).getBlock();
          if (nb instanceof BlockAir) continue;
          if (nb instanceof BlockBed) {
            this.bfsQueue.add(neighbor);
            continue;
          }
          if (nb.getBlockHardness(mc.theWorld, neighbor) < 0) continue;
          boolean hasExposedFace = false;
          for (EnumFacing check : EnumFacing.values()) {
            Block adj = mc.theWorld.getBlockState(neighbor.offset(check)).getBlock();
            if (adj instanceof BlockAir) {
              hasExposedFace = true;
              break;
            }
          }
          if (hasExposedFace) {
            Vec3 hitVec =
                new Vec3(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5);
            if (eye.squareDistanceTo(hitVec) <= rangeSq) {
              this.bfsCandidates.add(neighbor);
            }
            this.bfsQueue.add(neighbor);
          } else {
            this.bfsQueue.add(neighbor);
          }
        }
      }
      if (!this.bfsCandidates.isEmpty()) {
        return this.pickBestCandidate(this.bfsCandidates, bedPair);
      }
    }
    return null;
  }

  private BlockPos pickBestCandidate(ArrayList<BlockPos> candidates, BlockPos[] bedPair) {
    BlockPos bestPos = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (int i = 0, n = candidates.size(); i < n; i++) {
      BlockPos pos = candidates.get(i);
      double score = this.scoreBlockTarget(pos, false, 0);
      score += this.getLoSScoreModifier(pos, bedPair);
      if (score < bestScore) {
        bestScore = score;
        bestPos = pos;
      }
    }
    return bestPos;
  }

  private double getLoSScoreModifier(BlockPos candidate, BlockPos[] bedPair) {
    if (bedPair == null) return 0;
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 bc = this.bedCenter(bedPair);
    Vec3 candCenter =
        new Vec3(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
    Vec3 los = bc.subtract(eyes);
    double losLen = los.lengthVector();
    if (losLen < 1e-6) return 0;
    Vec3 toCand = candCenter.subtract(eyes);
    Vec3 cross =
        new Vec3(
            los.yCoord * toCand.zCoord - los.zCoord * toCand.yCoord,
            los.zCoord * toCand.xCoord - los.xCoord * toCand.zCoord,
            los.xCoord * toCand.yCoord - los.yCoord * toCand.xCoord);
    double crossMag =
        Math.sqrt(
            cross.xCoord * cross.xCoord
                + cross.yCoord * cross.yCoord
                + cross.zCoord * cross.zCoord);
    double perpDist = crossMag / losLen;
    double dot =
        toCand.xCoord * los.xCoord + toCand.yCoord * los.yCoord + toCand.zCoord * los.zCoord;
    if (dot < 0) return 100;
    return perpDist * 0.3;
  }

  private double scoreBlockTarget(BlockPos pos, boolean isBedBlock, float currentProgress) {
    Block block = mc.theWorld.getBlockState(pos).getBlock();
    int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, block);
    if (slot == -1) slot = mc.thePlayer.inventory.currentItem;
    float digRate = this.getDigSpeed(mc.theWorld.getBlockState(pos), slot, mc.thePlayer.onGround);
    float hardness = block.getBlockHardness(mc.theWorld, pos);
    if (hardness < 0) return Double.POSITIVE_INFINITY;
    float boost = this.canHarvest(block, slot) ? 30.0F : 100.0F;
    float rate = digRate / hardness / boost;
    if (rate <= 0) return Double.POSITIVE_INFINITY;
    double timeEst = 1.0 / rate;
    if (pos.equals(this.targetBed) && currentProgress > 0.02f) {
      timeEst -= currentProgress * 12.0;
    }
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    timeEst +=
        eye.squareDistanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
            * 0.002;
    return timeEst;
  }

  private BlockPos pickBestBedBlock(BlockPos[] pair) {
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    double rangeSq = this.range.getValue().doubleValue() * this.range.getValue().doubleValue();
    BlockPos bestPos = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (BlockPos bp : pair) {
      Vec3 center = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
      if (eye.squareDistanceTo(center) > rangeSq) continue;
      HitResult hit = this.computeBestHit(bp);
      if (hit == null) {
        hit = this.computeFallbackHit(bp);
      }
      if (hit == null) continue;
      double score =
          this.scoreBlockTarget(bp, true, bp.equals(this.targetBed) ? this.breakProgress : 0);
      if (score < bestScore) {
        bestScore = score;
        bestPos = bp;
      }
    }
    if (bestPos == null) {
      for (BlockPos bp : pair) {
        BlockPos obstruction = this.findRaytraceObstruction(bp);
        if (obstruction != null
            && PlayerUtil.canReach(obstruction, this.range.getValue().doubleValue())) {
          return obstruction;
        }
      }
      BlockPos closestBlock = null;
      double closestDistSq = Double.MAX_VALUE;
      for (BlockPos bp : pair) {
        Vec3 center = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        double distSq = eye.squareDistanceTo(center);
        if (distSq <= rangeSq && distSq < closestDistSq) {
          for (EnumFacing f : EnumFacing.values()) {
            if (mc.theWorld.getBlockState(bp.offset(f)).getBlock() instanceof BlockAir) {
              closestDistSq = distSq;
              closestBlock = bp;
              break;
            }
          }
        }
      }
      return closestBlock;
    }
    return bestPos;
  }

  private BlockPos validateBedPlacement(BlockPos bedPosition) {
    IBlockState blockState = mc.theWorld.getBlockState(bedPosition);
    if (blockState.getBlock() instanceof BlockBed) {
      ArrayList<BlockPos> pos = new ArrayList<>();
      EnumPartType partType = blockState.getValue(BlockBed.PART);
      EnumFacing facing = blockState.getValue(BlockBed.FACING);
      for (BlockPos blockPos :
          Arrays.asList(
              bedPosition,
              bedPosition.offset(partType == EnumPartType.HEAD ? facing.getOpposite() : facing))) {
        for (EnumFacing enumFacing :
            Arrays.asList(
                EnumFacing.UP,
                EnumFacing.NORTH,
                EnumFacing.EAST,
                EnumFacing.SOUTH,
                EnumFacing.WEST)) {
          Block block = mc.theWorld.getBlockState(blockPos.offset(enumFacing)).getBlock();
          if (BlockUtil.isReplaceable(blockPos.offset(enumFacing))) {
            return null;
          }
          if (!(block instanceof BlockBed)) {
            pos.add(blockPos.offset(enumFacing));
          }
        }
      }
      if (!pos.isEmpty()) {
        pos.sort(
            (blockPos, blockPos2) -> {
              int o =
                  Float.compare(
                      this.calcBlockStrength(blockPos2), this.calcBlockStrength(blockPos));
              return o != 0
                  ? o
                  : Double.compare(
                      blockPos.distanceSqToCenter(
                          mc.thePlayer.posX,
                          mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                          mc.thePlayer.posZ),
                      blockPos2.distanceSqToCenter(
                          mc.thePlayer.posX,
                          mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                          mc.thePlayer.posZ));
            });
        return pos.get(0);
      }
    }
    return null;
  }

  private BlockPos findNearestBed() {
    return this.findTargetBed(
        mc.thePlayer.posX,
        mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
        mc.thePlayer.posZ);
  }

  private BlockPos findTargetBed(double x, double y, double z) {
    if (this.mode.getValue() == 2) {
      return this.findTargetBedBypass(x, y, z);
    }
    ArrayList<BlockPos> targets = new ArrayList<>();
    int sX = MathHelper.floor_double(x);
    int sY = MathHelper.floor_double(y);
    int sZ = MathHelper.floor_double(z);
    for (int i = sX - 6; i <= sX + 6; i++) {
      for (int j = sY - 6; j <= sY + 6; j++) {
        for (int k = sZ - 6; k <= sZ + 6; k++) {
          BlockPos newPos = new BlockPos(i, j, k);
          if (!(Boolean) this.whiteList.getValue() || !this.bedWhitelist.contains(newPos)) {
            Block block = mc.theWorld.getBlockState(newPos).getBlock();
            if (block instanceof BlockBed
                && PlayerUtil.isBlockWithinReach(
                    newPos, x, y, z, this.range.getValue().doubleValue())) {
              targets.add(newPos);
            }
          }
        }
      }
    }
    if (targets.isEmpty()) {
      return null;
    } else {
      targets.sort(
          Comparator.comparingDouble(
              blockPos ->
                  blockPos.distanceSqToCenter(
                      mc.thePlayer.posX,
                      mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                      mc.thePlayer.posZ)));
      for (BlockPos blockPos : targets) {
        if (this.surroundings.getValue()) {
          BlockPos pos = this.validateBedPlacement(blockPos);
          if (pos != null) {
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (this.toolCheck.getValue() && !this.hasProperTool(block)) {
              continue;
            }
            return pos;
          }
        }
        return blockPos;
      }
      return null;
    }
  }

  private BlockPos findTargetBedBypass(double x, double y, double z) {
    ArrayList<BlockPos[]> bedPairs = new ArrayList<BlockPos[]>();
    int sX = MathHelper.floor_double(x);
    int sY = MathHelper.floor_double(y);
    int sZ = MathHelper.floor_double(z);
    int rangeInt = (int) Math.ceil(this.range.getValue().doubleValue()) + 2;
    HashSet<Long> seenPairs = new HashSet<Long>();
    for (int i = sX - rangeInt; i <= sX + rangeInt; i++) {
      for (int j = sY - rangeInt; j <= sY + rangeInt; j++) {
        for (int k = sZ - rangeInt; k <= sZ + rangeInt; k++) {
          BlockPos newPos = new BlockPos(i, j, k);
          if (this.whiteList.getValue() && this.bedWhitelist.contains(newPos)) continue;
          Block block = mc.theWorld.getBlockState(newPos).getBlock();
          if (!(block instanceof BlockBed)) continue;
          BlockPos[] pair = this.resolveBedPair(newPos);
          if (pair == null) continue;
          long pairKey = ((long) pair[0].hashCode() << 32) | (pair[1].hashCode() & 0xFFFFFFFFL);
          if (!seenPairs.add(pairKey)) continue;
          if (!PlayerUtil.isBlockWithinReach(pair[0], x, y, z, this.range.getValue().doubleValue())
              && !PlayerUtil.isBlockWithinReach(
                  pair[1], x, y, z, this.range.getValue().doubleValue())) {
            continue;
          }
          bedPairs.add(pair);
        }
      }
    }
    // Sort beds by distance
    bedPairs.sort(
        Comparator.comparingDouble(
            pair -> {
              Vec3 c = this.bedCenter(pair);
              return c.squareDistanceTo(new Vec3(x, y, z));
            }));
    for (BlockPos[] pair : bedPairs) {
      // CRITICAL: If the bed is VISIBLE on client-side (raytrace hits bed directly), target the bed
      // itself — but ONLY if we have a direct hit point (good angle), else enter waiting state
      if (this.isBedVisible(pair)) {
        // Try direct hit on each bed block
        for (BlockPos bp : pair) {
          HitResult hit = this.computeBestHit(bp);
          if (hit == null) hit = this.computeFallbackHit(bp);
          if (hit != null) {
            this.currentBedPair = pair;
            this.bypassState = 0; // not waiting, proceed directly
            return bp;
          }
        }
        // Bed is visible but NO hit point from current angle → enter waiting state
        // Return the nearest bed block so isBed=true, but don't mine until player finds angle
        BlockPos nearest =
            pair[0].distanceSqToCenter(x, y, z) <= pair[1].distanceSqToCenter(x, y, z)
                ? pair[0]
                : pair[1];
        if (PlayerUtil.isBlockWithinReach(nearest, x, y, z, this.range.getValue().doubleValue())) {
          this.currentBedPair = pair;
          this.bypassState = 1; // waiting for player to find direct hit angle
          this.bypassStateSince = System.currentTimeMillis();
          return nearest;
        }
      }
      // If covered, use BFS layer-by-layer to find the outermost defense block
      BlockPos target = this.bfsFindOutermostDefenseBlock(pair);
      if (target != null) {
        this.currentBedPair = pair;
        return target;
      }
    }
    return null;
  }

  private void doSwing() {
    if (this.swing.getValue()) {
      mc.thePlayer.swingItem();
    } else {
      PacketUtil.sendPacket(new C0APacketAnimation());
    }
  }

  private Color getProgressColor(int mode) {
    switch (mode) {
      case 1:
        float progress = this.calcProgress();
        if (progress <= 0.5F) {
          return ColorUtil.interpolate(progress / 0.5F, this.colorRed, this.colorYellow);
        }
        return ColorUtil.interpolate((progress - 0.5F) / 0.5F, this.colorYellow, this.colorGreen);
      case 2:
        return ((HUD) Miau.moduleManager.modules.get(HUD.class))
            .getColor(System.currentTimeMillis());
      default:
        return new Color(-1);
    }
  }

  public BedNuker() {
    super("BedNuker", false);
  }

  public boolean isReady() {
    return this.targetBed != null && this.readyToBreak;
  }

  public boolean isBreaking() {
    return this.targetBed != null && this.breaking;
  }

  @EventTarget(Priority.HIGH)
  public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
      this.runPendingWhitelistScan();
      if (!this.isEnabled()) {
        return;
      }
      AutoBlockIn autoBlockIn = (AutoBlockIn) Miau.moduleManager.modules.get(AutoBlockIn.class);
      if (autoBlockIn.isEnabled()) return;
      if (this.targetBed != null) {
        if (mc.theWorld.isAirBlock(this.targetBed)
            || !PlayerUtil.canReach(this.targetBed, this.range.getValue().doubleValue())) {
          this.restoreSlot();
          this.resetBreaking();
        } else if (!this.isBed) {
          BlockPos nearestBed = this.findNearestBed();
          if (nearestBed != null
              && mc.theWorld.getBlockState(nearestBed).getBlock() instanceof BlockBed) {
            this.resetBreaking();
          }
        }
      }
      if (this.targetBed != null) {
        // BYPASS mode: waiting state where bed is exposed but player has no direct hit angle
        if (this.mode.getValue() == 2) {
          if (this.bypassState == 1 && this.isBed) {
            // Each tick, try to find a direct hit — player may have moved to a good angle
            HitResult hit = this.computeBestHit(this.targetBed);
            if (hit == null) hit = this.computeFallbackHit(this.targetBed);
            if (hit != null) {
              // Player found a good angle — exit waiting, restart mining
              this.bypassState = 0;
              this.bypassStateSince = -1L;
              this.breakStage = 0;
              this.tickCounter = 0;
              this.breakProgress = 0.0F;
            } else {
              // Still no good angle — check timeout
              long elapsed = System.currentTimeMillis() - this.bypassStateSince;
              if (elapsed >= WAIT_TIMEOUT_MS) {
                // Timed out waiting for player → enter cooldown state so we can find a different
                // angle
                this.bypassState = 2;
                this.bypassStateSince = System.currentTimeMillis();
                // Don't mine or re-target during cooldown
              }
              return; // stay in waiting state, don't mine
            }
          } else if (this.bypassState == 2) {
            // Cooldown after waiting timeout — don't mine, wait for re-target
            long elapsed = System.currentTimeMillis() - this.bypassStateSince;
            if (elapsed >= TIMEDOUT_COOLDOWN_MS) {
              // Cooldown expired — allow re-target via the normal timer path
              this.restoreSlot();
              this.resetBreaking();
            }
            return;
          }
        }
        // Stop-on-attack: if enabled and player is being hit OR hitting someone else, pause mining
        // This preserves all state (targetBed, bypassState, etc.) so mining resumes when safe
        if (this.stopOnAttack.getValue()
            && (mc.thePlayer.hurtTime > 0 || Miau.playerStateManager.attacking)) {
          return;
        }
        int slot =
            ItemUtil.findInventorySlot(
                mc.thePlayer.inventory.currentItem,
                mc.theWorld.getBlockState(this.targetBed).getBlock());
        if ((this.mode.getValue() == 0 || this.mode.getValue() == 2) && this.savedSlot == -1) {
          this.savedSlot = mc.thePlayer.inventory.currentItem;
          mc.thePlayer.inventory.currentItem = slot;
          this.syncHeldItem();
        }
        switch (this.breakStage) {
          case 0:
            if (!mc.thePlayer.isUsingItem()) {
              this.doSwing();
              PacketUtil.sendPacket(
                  new C07PacketPlayerDigging(
                      Action.START_DESTROY_BLOCK,
                      this.targetBed,
                      this.getHitFacing(this.targetBed)));
              this.doSwing();
              mc.effectRenderer.addBlockHitEffects(
                  this.targetBed, this.getHitFacing(this.targetBed));
              this.breakStage = 1;
            }
            break;
          case 1:
            if (this.mode.getValue() == 1) {
              this.readyToBreak = false;
            }
            // BYPASS mode: only ready when the bed block itself is visible on client side
            if (this.mode.getValue() == 2 && this.isBed) {
              if (this.currentBedPair == null || !this.isBedVisible(this.currentBedPair)) {
                break;
              }
            }
            this.breaking = true;
            this.tickCounter++;
            this.breakProgress =
                this.breakProgress
                    + this.getBreakDelta(
                        mc.theWorld.getBlockState(this.targetBed),
                        this.targetBed,
                        slot,
                        mc.thePlayer.onGround);
            float tick = (float) this.tickCounter;
            IBlockState blockState = mc.theWorld.getBlockState(this.targetBed);
            boolean canBreak = mc.thePlayer.onGround && this.groundSpeed.getValue();
            BlockPos target = this.targetBed;
            float delta = tick * this.getBreakDelta(blockState, target, slot, canBreak);
            mc.effectRenderer.addBlockHitEffects(this.targetBed, this.getHitFacing(this.targetBed));
            if (this.breakProgress
                    >= 1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)
                || delta >= 1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F)) {
              if (this.mode.getValue() == 1 || this.mode.getValue() == 2) {
                this.readyToBreak = true;
                this.savedSlot = mc.thePlayer.inventory.currentItem;
                mc.thePlayer.inventory.currentItem = slot;
                this.syncHeldItem();
                if (mc.thePlayer.isUsingItem()) {
                  this.savedSlot = mc.thePlayer.inventory.currentItem;
                  mc.thePlayer.inventory.currentItem = (mc.thePlayer.inventory.currentItem + 1) % 9;
                  this.syncHeldItem();
                }
              }
              this.breaking = false;
              PacketUtil.sendPacket(
                  new C07PacketPlayerDigging(
                      Action.STOP_DESTROY_BLOCK,
                      this.targetBed,
                      this.getHitFacing(this.targetBed)));
              this.doSwing();
              IBlockState blockState_ = mc.theWorld.getBlockState(this.targetBed);
              Block block = blockState_.getBlock();
              if (block.getMaterial() != Material.air) {
                mc.theWorld.playAuxSFX(2001, this.targetBed, Block.getStateId(blockState_));
                mc.theWorld.setBlockToAir(this.targetBed);
              }
              if (block instanceof BlockBed) {
                this.timer.reset();
              }
              this.breakStage = 2;
            }
            break;
          case 2:
            this.restoreSlot();
            this.resetBreaking();
        }
        if (this.targetBed != null) {
          return;
        }
      }
      if (mc.thePlayer.capabilities.allowEdit && this.timer.hasTimeElapsed(500)) {
        this.targetBed = this.findNearestBed();
        this.breakStage = 0;
        this.tickCounter = 0;
        this.breakProgress = 0.0F;
        this.isBed =
            this.targetBed != null
                && mc.theWorld.getBlockState(this.targetBed).getBlock() instanceof BlockBed;
        this.restoreSlot();
        if (this.targetBed != null) {
          this.readyToBreak = true;
        }
      }
      if (this.targetBed == null) {
        Miau.delayManager.setDelayState(false, DelayModules.BED_NUKER);
      }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      AutoBlockIn autoBlockIn = (AutoBlockIn) Miau.moduleManager.modules.get(AutoBlockIn.class);
      if (autoBlockIn.isEnabled()) return;
      if (this.isReady()) {
        // BYPASS mode: use HitResult-based smart aiming (like old Legit)
        if (this.mode.getValue() == 2) {
          HitResult hit = this.computeBestHit(this.targetBed);
          if (hit == null) {
            hit = this.computeFallbackHit(this.targetBed);
          }
          if (hit != null) {
            float[] rots = RotationUtil.calculate(hit.hitVec);
            event.setRotation(rots[0], rots[1], 5);
            event.setPervRotation(
                this.moveFix.getValue() != 0 ? rots[0] : mc.thePlayer.rotationYaw, 5);
          } else {
            double x = (double) this.targetBed.getX() + 0.5 - mc.thePlayer.posX;
            double y =
                (double) this.targetBed.getY()
                    + 0.5
                    - mc.thePlayer.posY
                    - (double) mc.thePlayer.getEyeHeight();
            double z = (double) this.targetBed.getZ() + 0.5 - mc.thePlayer.posZ;
            float[] rots = RotationUtil.getRotationsTo(x, y, z, event.getYaw(), event.getPitch());
            event.setRotation(rots[0], rots[1], 5);
            event.setPervRotation(
                this.moveFix.getValue() != 0 ? rots[0] : mc.thePlayer.rotationYaw, 5);
          }
        } else {
          double x = (double) this.targetBed.getX() + 0.5 - mc.thePlayer.posX;
          double y =
              (double) this.targetBed.getY()
                  + 0.5
                  - mc.thePlayer.posY
                  - (double) mc.thePlayer.getEyeHeight();
          double z = (double) this.targetBed.getZ() + 0.5 - mc.thePlayer.posZ;
          float[] rots = RotationUtil.getRotationsTo(x, y, z, event.getYaw(), event.getPitch());
          event.setRotation(rots[0], rots[1], 5);
          event.setPervRotation(
              this.moveFix.getValue() != 0 ? rots[0] : mc.thePlayer.rotationYaw, 5);
        }
      }
    }
  }

  @EventTarget
  public void onPlayerUpdate(PlayerUpdateEvent event) {
    if (this.isEnabled()) {
      if (this.isBreaking()
          && !Miau.playerStateManager.attacking
          && !Miau.playerStateManager.digging
          && !Miau.playerStateManager.placing
          && !Miau.playerStateManager.swinging) {
        this.doSwing();
      }
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled()) {
      if (this.moveFix.getValue() == 1
          && RotationState.isActived()
          && RotationState.getPriority() == 5.0F
          && MoveUtil.isForwardPressed()) {
        MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
      }
    }
  }

  @EventTarget(Priority.HIGH)
  public void onKnockback(KnockbackEvent event) {
    if (this.isEnabled() && !event.isCancelled() && !(event.getY() <= 0.0)) {
      if (this.ignoreVelocity.getValue() == 1 && this.targetBed != null) {
        event.setCancelled(true);
        event.setX(mc.thePlayer.motionX);
        event.setY(mc.thePlayer.motionY);
        event.setZ(mc.thePlayer.motionZ);
      }
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (this.isEnabled()) {
      if (this.targetBed != null && (!this.isBed || !this.surroundings.getValue())) {
        if (this.showProgress.getValue() != 0) {
          HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
          float scale = hud.scale.getValue();
          String text = String.format("%d%%", (int) (this.calcProgress() * 100.0F));
          GlStateManager.pushMatrix();
          GlStateManager.scale(scale, scale, 0.0F);
          GlStateManager.disableDepth();
          GlStateManager.enableBlend();
          GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
          int width = mc.fontRendererObj.getStringWidth(text);
          mc.fontRendererObj.drawString(
              text,
              (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / scale
                  - (float) width / 2.0F,
              (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 2.0F / scale,
              this.getProgressColor(this.showProgress.getValue()).getRGB() & 16777215 | -1090519040,
              hud.shadow.getValue());
          GlStateManager.disableBlend();
          GlStateManager.enableDepth();
          GlStateManager.popMatrix();
        }
      }
    }
  }

  @EventTarget(Priority.LOW)
  public void onRender3D(Render3DEvent event) {
    if (this.isEnabled() && this.targetBed != null && !mc.theWorld.isAirBlock(this.targetBed)) {
      mc.theWorld.sendBlockBreakProgress(
          mc.thePlayer.getEntityId(), this.targetBed, (int) (this.calcProgress() * 10.0F) - 1);
      if (this.showTarget.getValue() != 0) {
        BedESP bedESP = (BedESP) Miau.moduleManager.modules.get(BedESP.class);
        Color color = this.getProgressColor(this.showTarget.getValue());
        RenderUtil.enableRenderState();
        BlockPos target = this.targetBed;
        double newHeight = this.isBed ? bedESP.getBlockHeight() : 1.0;
        int r = color.getRed();
        int g = color.getBlue();
        int b = color.getGreen();
        RenderUtil.drawBlockBox(target, newHeight, r, b, g);
        RenderUtil.disableRenderState();
      }
    }
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    this.waitingForStart = false;
    this.whitelistScanAt = -1L;
    this.bedWhitelist.clear();
    this.resetBreaking();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!event.isCancelled()) {
      if (event.getPacket() instanceof S02PacketChat) {
        String text = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
        if (text.contains("§e§lProtect your bed and destroy the enemy bed")
            || text.contains("§e§lDestroy the enemy bed and then eliminate them")) {
          this.waitingForStart = true;
        }
      }
      if (event.getPacket() instanceof S08PacketPlayerPosLook && this.waitingForStart) {
        this.waitingForStart = false;
        this.bedWhitelist.clear();
        this.scheduleWhitelistScan();
      }
      if (this.isEnabled()
          && this.targetBed != null
          && this.ignoreVelocity.getValue() == 2
          && Miau.delayManager.getDelayModule() != DelayModules.BED_NUKER) {
        if (event.getPacket() instanceof S12PacketEntityVelocity) {
          S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
          if (packet.getEntityID() == mc.thePlayer.getEntityId() && packet.getMotionY() > 0) {
            Miau.delayManager.delay(DelayModules.BED_NUKER);
            Miau.delayManager.delayedPacket.offer(packet);
            event.setCancelled(true);
          }
        }
        if (event.getPacket() instanceof S27PacketExplosion) {
          S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
          if (explosion.func_149149_c() != 0.0F
              || explosion.func_149144_d() != 0.0F
              || explosion.func_149147_e() != 0.0F) {
            Miau.delayManager.delay(DelayModules.BED_NUKER);
            Miau.delayManager.delayedPacket.offer(explosion);
            event.setCancelled(true);
          }
        }
      }
    }
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isEnabled()) {
      if (this.isReady()
          || this.targetBed != null
              && mc.objectMouseOver != null
              && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) {
      if (this.isReady()) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isEnabled()) {
      if (this.isReady()
          || this.targetBed != null
              && mc.objectMouseOver != null
              && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onSwap(SwapItemEvent event) {
    if (this.isEnabled()) {
      if (this.savedSlot != -1) {
        event.setCancelled(true);
      }
    }
  }

  @Override
  public void onDisabled() {
    this.resetBreaking();
    this.savedSlot = -1;
    this.waitingForStart = false;
    this.whitelistScanAt = -1L;
    this.bedWhitelist.clear();
    Miau.delayManager.setDelayState(false, DelayModules.BED_NUKER);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }
}
