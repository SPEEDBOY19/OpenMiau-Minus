package miau.module.modules.ghost;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.player.RotationUtil;
import miau.util.render.RenderUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class AutoLadderClutch extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public final FloatProperty fallDistance = new FloatProperty("Fall Distance", 3.0f, 2.0f, 10.0f);
  public final FloatProperty reach = new FloatProperty("Reach", 4.5f, 2.0f, 5.5f);
  public final FloatProperty placeDelay = new FloatProperty("Place Delay (ms)", 50f, 0f, 200f);
  public final FloatProperty maxFov = new FloatProperty("FOV", 180f, 0f, 180f);
  public final BooleanProperty wallBuilder = new BooleanProperty("Wall Builder", true);
  public final BooleanProperty autoSneak = new BooleanProperty("Auto Sneak", true);
  public final BooleanProperty legitAutoCenter = new BooleanProperty("Legit Auto Center", true);
  public final BooleanProperty esp = new BooleanProperty("ESP", true);
  public final BooleanProperty debug = new BooleanProperty("Debug", false);

  private final Set<String> BUILDING_BLOCKS =
      new HashSet<>(
          Arrays.asList(
              "wool",
              "planks",
              "wood",
              "log",
              "log2",
              "stone",
              "cobblestone",
              "glass",
              "stained_glass",
              "clay",
              "hardened_clay",
              "stained_hardened_clay",
              "end_stone",
              "obsidian"));

  private static final int ACTION_NONE = 0;
  private static final int ACTION_BASE = 1;
  private static final int ACTION_LADDER = 2;

  private int originalSlot = -1;
  private int actionSlot = -1;
  private int queuedAction = ACTION_NONE;
  private int pendingBaseTicks = 0;

  private boolean isClutching = false;
  private boolean placeQueued = false;
  private boolean hasAim = false;
  private boolean waitingForBase = false;
  private boolean wasCentering = false;

  private long lastPlaceTime = 0L;

  private float aimYaw = 0f;
  private float aimPitch = 0f;
  private float serverYaw = 0f;
  private float serverPitch = 0f;

  private double lockX = -1;
  private double lockZ = -1;

  private BlockPos hitAt = null;
  private Vec3 hitVec = null;
  private EnumFacing hitSide = null;
  private BlockPos targetCell = null;
  private BlockPos pendingBase = null;

  private BlockPos ladderEsp = null;
  private BlockPos baseEsp = null;

  public AutoLadderClutch() {
    super("LadderClutch", false);
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer != null) {
      serverYaw = mc.thePlayer.rotationYaw;
      serverPitch = mc.thePlayer.rotationPitch;
    }
    resetState();
  }

  @Override
  public void onDisabled() {
    resetState();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
      C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
      if (packet.getRotating()) {
        serverYaw = packet.getYaw();
        serverPitch = packet.getPitch();
      }
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() != EventType.PRE) return;

    if (mc.thePlayer == null || mc.theWorld == null) {
      resetState();
      return;
    }

    if (!isActivationValid()) {
      resetState();
      return;
    }

    if (originalSlot == -1) {
      originalSlot = mc.thePlayer.inventory.currentItem;
    }

    boolean activelyClutching = (placeQueued || waitingForBase || isClutching);
    if (activelyClutching && legitAutoCenter.getValue()) {
      Vec3 pos = mc.thePlayer.getPositionVector();

      if (lockX == -1) {
        lockX = Math.floor(pos.xCoord) + 0.5;
        lockZ = Math.floor(pos.zCoord) + 0.5;
        wasCentering = true;
      }

      double diffX = lockX - pos.xCoord;
      double diffZ = lockZ - pos.zCoord;
      double distSq = diffX * diffX + diffZ * diffZ;

      if (distSq > 0.015) {
        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;
        float yawDiff = wrapYawDelta(mc.thePlayer.rotationYaw, targetYaw);

        setKeyBindState(mc.gameSettings.keyBindForward, Math.abs(yawDiff) < 65);
        setKeyBindState(mc.gameSettings.keyBindBack, Math.abs(yawDiff) > 115);
        setKeyBindState(mc.gameSettings.keyBindLeft, yawDiff < -25 && yawDiff > -155);
        setKeyBindState(mc.gameSettings.keyBindRight, yawDiff > 25 && yawDiff < 155);
      } else {
        idleKeys();
      }
    } else {
      if (wasCentering) {
        idleKeys();
        wasCentering = false;
      }
      lockX = -1;
      lockZ = -1;
    }

    if (waitingForBase) {
      pendingBaseTicks++;
      if (pendingBaseTicks > 8) {
        waitingForBase = false;
        pendingBase = null;
        pendingBaseTicks = 0;
      }
    }

    Float[] rots = getRotations();
    if (rots != null) {
      event.setRotation(rots[0], rots[1], 10);
      RotationUtil.serverYaw = rots[0];
      RotationUtil.serverPitch = rots[1];
    }

    if (!placeQueued) return;
    if (System.currentTimeMillis() - lastPlaceTime < placeDelay.getValue().longValue()) return;

    placeQueued = false;
    if (actionSlot < 0 || actionSlot > 8 || hitAt == null || hitVec == null || hitSide == null) {
      clearQueuedAction();
      return;
    }

    mc.thePlayer.inventory.currentItem = actionSlot;
    ItemStack stack = mc.thePlayer.getHeldItem();

    if (mc.playerController.onPlayerRightClick(
        mc.thePlayer, mc.theWorld, stack, hitAt, hitSide, hitVec)) {
      mc.thePlayer.swingItem();
      lastPlaceTime = System.currentTimeMillis();

      if (queuedAction == ACTION_BASE) {
        waitingForBase = true;
        pendingBase = targetCell;
        pendingBaseTicks = 0;
        baseEsp = targetCell;
        debugPrint("Structural wall base created.");
      } else if (queuedAction == ACTION_LADDER) {
        ladderEsp = targetCell;
        waitingForBase = false;
        pendingBase = null;
        pendingBaseTicks = 0;

        if (autoSneak.getValue()) {
          setKeyBindState(mc.gameSettings.keyBindSneak, true);
          isClutching = true;
        }

        debugPrint("Ladder clutch executed.");
      }
    }

    if (originalSlot != -1) {
      mc.thePlayer.inventory.currentItem = originalSlot;
    }

    clearQueuedAction();
  }

  private Float[] getRotations() {
    if (placeQueued && hasAim) {
      return new Float[] {aimYaw, aimPitch};
    }

    clearQueuedAction();

    if (waitingForBase && pendingBase != null) {
      Block baseBlock = BlockUtil.getBlock(pendingBase);
      if (BlockUtil.isSolid(baseBlock)) {
        int ladderSlot = findLadderSlot();
        if (ladderSlot != -1 && prepareLadderOnBase(pendingBase, ladderSlot)) {
          return new Float[] {aimYaw, aimPitch};
        }
      }
      return null;
    }

    int ladderSlot = findLadderSlot();
    if (ladderSlot == -1) return null;

    double r = reach.getValue().doubleValue();
    MovingObjectPosition wallRay =
        raycastBlock(r, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);

    if (wallRay != null && wallRay.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
      BlockPos wall = wallRay.getBlockPos();
      EnumFacing face = wallRay.sideHit;

      if (isHorizontal(face)) {
        BlockPos ladderCell = wall.offset(face);
        if (isAir(ladderCell)
            && prepareRayPlacement(wallRay, ladderCell, ladderSlot, ACTION_LADDER)) {
          return new Float[] {aimYaw, aimPitch};
        }
      }
    }

    if (prepareNearbyWallLadder(ladderSlot, r)) {
      return new Float[] {aimYaw, aimPitch};
    }

    if (!wallBuilder.getValue()) return null;

    BlockPos playerCell = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    BlockPos ladderCell = playerCell.offset(EnumFacing.DOWN, 2);

    int blockSlot = findBuildingBlockSlot();
    if (blockSlot == -1) return null;

    EnumFacing preferred = getPlayerFacing(mc.thePlayer.rotationYaw);
    EnumFacing[] faces = orderedHorizontalFaces(preferred);

    BlockPos bestStructureCell = null;
    Object[] bestSupport = null;

    for (EnumFacing face : faces) {
      BlockPos cell = ladderCell.offset(face);

      if (!isAir(cell)) continue;

      Object[] support = findPlacementSupport(cell);
      if (support != null) {
        bestStructureCell = cell;
        bestSupport = support;
        break;
      }
    }

    if (bestStructureCell == null) return null;

    BlockPos supportBlock = (BlockPos) bestSupport[0];
    EnumFacing supportFace = (EnumFacing) bestSupport[1];
    baseEsp = bestStructureCell;

    if (prepareManualPlacement(
        supportBlock, supportFace, bestStructureCell, blockSlot, ACTION_BASE)) {
      return new Float[] {aimYaw, aimPitch};
    }

    return null;
  }

  // ---------------------------------------------------------
  // SILENT 180 ROTATIONS & GRID ALGORITHM
  // ---------------------------------------------------------

  private float normYaw(float yaw) {
    yaw = ((yaw % 360f) + 360f) % 360f;
    return (yaw > 180f) ? (yaw - 360f) : yaw;
  }

  private float wrapYawDelta(float base, float target) {
    float d = target - base;
    while (d <= -180f) d += 360f;
    while (d > 180f) d -= 360f;
    return d;
  }

  private float unwrapYaw(float yaw, float prevYaw) {
    return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
  }

  private float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
    double dx = tx - eye.xCoord, dy = ty - eye.yCoord, dz = tz - eye.zCoord;
    double hd = Math.sqrt(dx * dx + dz * dz);
    float yawWrapped = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    yawWrapped = normYaw(yawWrapped);
    float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
    return new float[] {yawWrapped, pitch};
  }

  private Object[] findBestRotation(BlockPos support, EnumFacing face) {
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

    float curYawW = normYaw(serverYaw);
    float curPit = serverPitch;
    float cliYawW = normYaw(mc.thePlayer.rotationYaw);
    float cliPit = mc.thePlayer.rotationPitch;

    double INSET = 0.05,
        STEP = 0.2,
        JIT = 0.2,
        insetTop = 1 - INSET - 1e-3,
        insetBot = INSET + 1e-3;
    int GRID = (int) Math.round(1 / STEP);
    float fov = maxFov.getValue();

    ArrayList<Object[]> cands = new ArrayList<>();

    for (int rr = 0; rr <= GRID; rr++) {
      boolean ltr = (rr & 1) == 0;
      double v = rr * STEP + (Math.random() * STEP * JIT * 2 - STEP * JIT);
      if (v < 0) v = 0;
      else if (v > 1) v = 1;

      for (int cc = 0; cc <= GRID; cc++) {
        double cu = cc * STEP + (Math.random() * STEP * JIT * 2 - STEP * JIT);
        if (cu < 0) cu = 0;
        else if (cu > 1) cu = 1;
        double u = ltr ? cu : 1 - cu;

        double px = support.getX(), py = support.getY(), pz = support.getZ();

        if (face == EnumFacing.UP) {
          px += u;
          pz += v;
          py += insetTop;
        } else if (face == EnumFacing.DOWN) {
          px += u;
          pz += v;
          py += insetBot;
        } else if (face == EnumFacing.SOUTH) {
          px += u;
          py += v;
          pz += insetTop;
        } else if (face == EnumFacing.NORTH) {
          px += u;
          py += v;
          pz += insetBot;
        } else if (face == EnumFacing.EAST) {
          pz += u;
          py += v;
          px += insetTop;
        } else if (face == EnumFacing.WEST) {
          pz += u;
          py += v;
          px += insetBot;
        } else continue;

        float[] rotW = getRotationsWrapped(eye, px, py, pz);
        float yawW = rotW[0], pit = rotW[1];

        if (Math.abs(wrapYawDelta(cliYawW, yawW)) > fov) continue;
        if (Math.abs(pit - cliPit) > 90f) continue;
        if (Math.abs(pit) > 90f) continue;

        double cost =
            Math.abs((double) wrapYawDelta(curYawW, yawW)) + Math.abs((double) (pit - curPit));
        cands.add(new Object[] {cost, yawW, pit, new Vec3(px, py, pz)});
      }
    }

    if (cands.isEmpty()) return null;

    cands.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
    double r = reach.getValue().doubleValue();

    for (Object[] cand : cands) {
      float yawW = (Float) cand[1];
      float pit = (Float) cand[2];
      float yawUnwrapped = unwrapYaw(yawW, serverYaw);
      Vec3 hitPoint = (Vec3) cand[3];

      MovingObjectPosition verified = raycastBlock(r, yawUnwrapped, pit);
      if (verified != null
          && verified.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
          && verified.getBlockPos().equals(support)
          && face == verified.sideHit) {
        return new Object[] {yawUnwrapped, pit, hitPoint};
      }
    }

    return null;
  }

  // ---------------------------------------------------------
  // PLACEMENT / CLUTCH PREPARATIONS
  // ---------------------------------------------------------

  private boolean prepareManualPlacement(
      BlockPos block, EnumFacing face, BlockPos placeCell, int slot, int action) {
    Object[] bestRot = findBestRotation(block, face);
    if (bestRot == null) return false;

    hitAt = block;
    aimYaw = (Float) bestRot[0];
    aimPitch = (Float) bestRot[1];
    hitVec = (Vec3) bestRot[2];
    hitSide = face;

    targetCell = placeCell;
    actionSlot = slot;
    queuedAction = action;
    hasAim = true;
    placeQueued = true;
    return true;
  }

  private boolean prepareRayPlacement(
      MovingObjectPosition ray, BlockPos placeCell, int slot, int action) {
    return prepareManualPlacement(ray.getBlockPos(), ray.sideHit, placeCell, slot, action);
  }

  private boolean prepareNearbyWallLadder(int ladderSlot, double reach) {
    Vec3 playerPosition = mc.thePlayer.getPositionVector();
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

    int centerX = (int) Math.floor(playerPosition.xCoord);
    int centerY = (int) Math.floor(playerPosition.yCoord);
    int centerZ = (int) Math.floor(playerPosition.zCoord);
    int radius = (int) Math.ceil(reach);

    ArrayList<Object[]> candidates = new ArrayList<>();
    EnumFacing[] faces =
        new EnumFacing[] {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    for (int y = centerY + 1; y >= centerY - 3; y--) {
      for (int x = centerX - radius; x <= centerX + radius; x++) {
        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
          BlockPos support = new BlockPos(x, y, z);
          Block block = BlockUtil.getBlock(support);
          if (!BlockUtil.isSolid(block)) continue;

          for (EnumFacing face : faces) {
            BlockPos ladderCell = support.offset(face);
            if (!isAir(ladderCell)) continue;

            if (ladderCell.getX() != centerX || ladderCell.getZ() != centerZ) {
              continue;
            }

            Vec3 point = facePoint(support, face);
            double distanceSq = eye.squareDistanceTo(point);
            if (distanceSq > reach * reach) continue;

            double verticalPenalty = Math.abs(ladderCell.getY() - playerPosition.yCoord) * 0.35;
            double score = distanceSq + verticalPenalty;
            candidates.add(new Object[] {score, support, face, ladderCell});
          }
        }
      }
    }

    if (candidates.isEmpty()) return false;

    candidates.sort(
        (a, b) -> Double.compare(((Number) a[0]).doubleValue(), ((Number) b[0]).doubleValue()));

    for (Object[] candidate : candidates) {
      BlockPos support = (BlockPos) candidate[1];
      EnumFacing face = (EnumFacing) candidate[2];
      BlockPos ladderCell = (BlockPos) candidate[3];

      if (prepareManualPlacement(support, face, ladderCell, ladderSlot, ACTION_LADDER)) {
        return true;
      }
    }

    return false;
  }

  private boolean prepareLadderOnBase(BlockPos base, int ladderSlot) {
    EnumFacing preferred = faceTowardPlayer(base);
    EnumFacing[] faces = orderedHorizontalFaces(preferred);

    for (EnumFacing face : faces) {
      BlockPos ladderCell = base.offset(face);
      if (!isAir(ladderCell)) continue;

      if (prepareManualPlacement(base, face, ladderCell, ladderSlot, ACTION_LADDER)) {
        return true;
      }
    }

    return false;
  }

  private Object[] findPlacementSupport(BlockPos target) {
    EnumFacing[] directions =
        new EnumFacing[] {
          EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
        };

    for (EnumFacing direction : directions) {
      BlockPos support = target.offset(direction);
      Block block = BlockUtil.getBlock(support);
      if (!BlockUtil.isSolid(block)) continue;

      EnumFacing clickedFace = direction.getOpposite();
      return new Object[] {support, clickedFace};
    }
    return null;
  }

  // ---------------------------------------------------------
  // UTILS
  // ---------------------------------------------------------

  private int findLadderSlot() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack == null || stack.stackSize <= 0) continue;
      if (stack.getItem().getUnlocalizedName().contains("ladder")) return i;
    }
    return -1;
  }

  private int findBuildingBlockSlot() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack == null
          || stack.stackSize < 3
          || !(stack.getItem() instanceof net.minecraft.item.ItemBlock)) continue;
      if (stack.getItem().getUnlocalizedName().contains("ladder")) continue;

      for (String allowed : BUILDING_BLOCKS) {
        if (stack.getItem().getUnlocalizedName().contains(allowed)) return i;
      }
    }
    return -1;
  }

  private boolean isActivationValid() {
    if (mc.currentScreen != null) return false;

    if (mc.thePlayer.motionY >= -0.1) return false;
    if (mc.thePlayer.onGround) return false;
    if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) return false;

    return mc.thePlayer.fallDistance >= fallDistance.getValue();
  }

  private boolean isAir(BlockPos position) {
    Block block = BlockUtil.getBlock(position);
    return block instanceof BlockAir;
  }

  private boolean isHorizontal(EnumFacing face) {
    return face == EnumFacing.NORTH
        || face == EnumFacing.SOUTH
        || face == EnumFacing.EAST
        || face == EnumFacing.WEST;
  }

  private EnumFacing getPlayerFacing(float yaw) {
    float normalized = ((yaw % 360f) + 360f) % 360f;
    if (normalized < 45f || normalized >= 315f) return EnumFacing.SOUTH;
    if (normalized < 135f) return EnumFacing.WEST;
    if (normalized < 225f) return EnumFacing.NORTH;
    return EnumFacing.EAST;
  }

  private EnumFacing faceTowardPlayer(BlockPos base) {
    double dx = mc.thePlayer.posX - (base.getX() + 0.5);
    double dz = mc.thePlayer.posZ - (base.getZ() + 0.5);

    if (Math.abs(dx) > Math.abs(dz)) {
      return dx >= 0 ? EnumFacing.EAST : EnumFacing.WEST;
    }
    return dz >= 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
  }

  private EnumFacing[] orderedHorizontalFaces(EnumFacing preferred) {
    if (preferred == EnumFacing.NORTH)
      return new EnumFacing[] {
        EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH
      };
    if (preferred == EnumFacing.SOUTH)
      return new EnumFacing[] {
        EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.NORTH
      };
    if (preferred == EnumFacing.EAST)
      return new EnumFacing[] {
        EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.WEST
      };
    return new EnumFacing[] {EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST};
  }

  private Vec3 facePoint(BlockPos block, EnumFacing face) {
    return new Vec3(
        block.getX() + 0.5 + face.getFrontOffsetX() * 0.5,
        block.getY() + 0.5 + face.getFrontOffsetY() * 0.5,
        block.getZ() + 0.5 + face.getFrontOffsetZ() * 0.5);
  }

  private void clearQueuedAction() {
    placeQueued = false;
    hasAim = false;
    actionSlot = -1;
    queuedAction = ACTION_NONE;
    hitAt = null;
    hitVec = null;
    hitSide = null;
    targetCell = null;
  }

  private void resetState() {
    if (originalSlot != -1 && mc.thePlayer != null) {
      mc.thePlayer.inventory.currentItem = originalSlot;
    }

    if (isClutching) {
      setKeyBindState(mc.gameSettings.keyBindSneak, false);
    }

    if (wasCentering) {
      idleKeys();
    }

    originalSlot = -1;
    isClutching = false;
    waitingForBase = false;
    wasCentering = false;
    pendingBase = null;
    pendingBaseTicks = 0;
    ladderEsp = null;
    baseEsp = null;
    lockX = -1;
    lockZ = -1;
    clearQueuedAction();
  }

  private void idleKeys() {
    setKeyBindState(mc.gameSettings.keyBindForward, false);
    setKeyBindState(mc.gameSettings.keyBindBack, false);
    setKeyBindState(mc.gameSettings.keyBindLeft, false);
    setKeyBindState(mc.gameSettings.keyBindRight, false);
  }

  private void setKeyBindState(KeyBinding binding, boolean pressed) {
    KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
  }

  private MovingObjectPosition raycastBlock(double distance, float yaw, float pitch) {
    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 lookVec = getVectorForRotation(pitch, yaw);
    Vec3 targetPos =
        eyePos.addVector(
            lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
    return mc.theWorld.rayTraceBlocks(eyePos, targetPos, false, false, true);
  }

  private Vec3 getVectorForRotation(float pitch, float yaw) {
    float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
    float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
    float f2 = -MathHelper.cos(-pitch * 0.017453292F);
    float f3 = MathHelper.sin(-pitch * 0.017453292F);
    return new Vec3(f1 * f2, f3, f * f2);
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled()) return;
    if (!esp.getValue()) return;

    if (baseEsp != null) {
      RenderUtil.drawBlockBoundingBox(
          baseEsp, 1.0, 204, 255, 153, 153,
          2.0f); // 0xCCFF9900 = Alpha CC (204), R FF (255), G 99 (153), B 00 (0) --> Wait, 00 is 0.
      // Let's adjust color
      RenderUtil.drawBlockBox(baseEsp, 1.0, 255, 153, 0); // Filled box
    }
    if (ladderEsp != null) {
      RenderUtil.drawBlockBoundingBox(
          ladderEsp, 1.0, 51, 255, 85, 204,
          2.0f); // 0xCC33FF55 = Alpha CC (204), R 33 (51), G FF (255), B 55 (85)
      RenderUtil.drawBlockBox(ladderEsp, 1.0, 51, 255, 85);
    }
  }

  private void debugPrint(String message) {
    if (debug.getValue()) {
      miau.util.client.ChatUtil.display("\u00a78[\u00a7bAutoLadder\u00a78] " + message);
    }
  }
}
