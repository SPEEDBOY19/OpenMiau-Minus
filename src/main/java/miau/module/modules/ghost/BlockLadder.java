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
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class BlockLadder extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private final Set<String> PLACE_THROUGH =
      new HashSet<>(
          Arrays.asList(
              "air", "water", "flowing_water", "lava", "flowing_lava", "fire"));

  private final Set<String> BLOCKS =
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

  public final FloatProperty fallDistance = new FloatProperty("Fall Distance (blocks)", 4.0f, 2.0f, 10.0f);
  public final FloatProperty reach = new FloatProperty("Reach (blocks)", 4.5f, 2.0f, 4.5f);
  public final FloatProperty placeDelay = new FloatProperty("Place Delay (ms)", 70f, 0f, 200f);
  public final BooleanProperty autoCenter = new BooleanProperty("Auto Center", true);
  public final BooleanProperty esp = new BooleanProperty("ESP", true);

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
  private long nextRandomDelay = 0L;

  private float aimYaw = 0f;
  private float aimPitch = 0f;
  private float serverYaw = 0f;
  private float serverPitch = 0f;

  private Vec3 hitAt = null;
  private Vec3 hitVec = null;
  private EnumFacing hitSide = null;
  private Vec3 targetCell = null;
  private Vec3 pendingBase = null;

  private Vec3 lockedStructureCell = null;
  private Object[] lockedSupport = null;
  private Vec3 lockedLandingESP = null;

  private Vec3 blueLandingESP = null;
  private Vec3 orangeSupportESP = null;
  private Vec3 greenLadderESP = null;

  public BlockLadder() {
    super("BlockLadder", false);
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer != null) {
      serverYaw = mc.thePlayer.rotationYaw;
      serverPitch = mc.thePlayer.rotationPitch;
    }
    nextRandomDelay = placeDelay.getValue().longValue();
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

    if (findLadderSlot() == -1) {
      if (wasCentering || waitingForBase || placeQueued || isClutching || lockedStructureCell != null) {
        resetState();
      }
      return;
    }

    if (originalSlot == -1) originalSlot = mc.thePlayer.inventory.currentItem;

    double fallThreshold = fallDistance.getValue().doubleValue();
    boolean isFalling =
        mc.thePlayer.fallDistance >= fallThreshold
            && !mc.thePlayer.onGround
            && !mc.thePlayer.isInWater()
            && !mc.thePlayer.isInLava();

    if (!isFalling) {
      if (wasCentering || waitingForBase || placeQueued || isClutching || lockedStructureCell != null) {
        resetState();
      }
      return;
    }

    Float[] rots = getRotations();
    if (rots != null) {
      event.setRotation(rots[0], rots[1], 10);
      RotationUtil.serverYaw = rots[0];
      RotationUtil.serverPitch = rots[1];
    }

    if (lockedLandingESP != null) {
      double dx = mc.thePlayer.posX - (lockedLandingESP.xCoord + 0.5);
      double dz = mc.thePlayer.posZ - (lockedLandingESP.zCoord + 0.5);
      if (dx * dx + dz * dz > 6.25) {
        lockedLandingESP = null;
        blueLandingESP = null;
        lockedStructureCell = null;
        lockedSupport = null;
      }
    }

    if (lockedLandingESP == null) {
      Vec3 predictedLanding = null;
      double px = Math.floor(mc.thePlayer.posX);
      double py = Math.floor(mc.thePlayer.posY);
      double pz = Math.floor(mc.thePlayer.posZ);
      double vx = mc.thePlayer.motionX;
      double vy = mc.thePlayer.motionY;
      double vz = mc.thePlayer.motionZ;

      for (int t = 0; t < 100; t++) {
        vy -= 0.08;
        px += vx;
        py += vy;
        pz += vz;
        vy *= 0.9800000190734863;
        vx *= 0.91;
        vz *= 0.91;

        if (py < 0 || isSolid(worldBlock(new BlockPos(px, py, pz)))) {
          predictedLanding = new Vec3(Math.floor(px) + 0.5, py, Math.floor(pz) + 0.5);
          break;
        }
      }

      if (predictedLanding != null) {
        blueLandingESP =
            new Vec3(
                Math.floor(predictedLanding.xCoord),
                Math.floor(predictedLanding.yCoord),
                Math.floor(predictedLanding.zCoord));
      } else {
        blueLandingESP = null;
      }
    } else {
      blueLandingESP = lockedLandingESP;
    }

    if (blueLandingESP == null) return;

    Vec3 candidateSupport = null;
    Vec3 candidateLadder = null;
    EnumFacing candidateFace = null;

    if (lockedStructureCell == null) {
      double staticPy = Math.floor(mc.thePlayer.posY);

      for (int drop = 1; drop <= 6; drop++) {
        Vec3 scanCell =
            new Vec3(blueLandingESP.xCoord, staticPy - drop, blueLandingESP.zCoord);
        EnumFacing[] faces = orderedHorizontalFaces(getPlayerFacing(mc.thePlayer.rotationYaw));

        for (EnumFacing face : faces) {
          Vec3 adjCell = offsetCell(scanCell, face);
          if (!isAirCell(adjCell)) continue;

          Object[] support = findPlacementSupport(adjCell);
          if (support != null) {
            candidateSupport = (Vec3) support[0];
            candidateLadder = adjCell;
            candidateFace = (EnumFacing) support[1];
            break;
          }
        }
        if (candidateSupport != null) break;
      }
    }

    if (candidateSupport != null && lockedStructureCell == null) {
      double deltaX = Math.abs(candidateLadder.xCoord - blueLandingESP.xCoord);
      double deltaZ = Math.abs(candidateLadder.zCoord - blueLandingESP.zCoord);

      if (deltaX <= 1.0 && deltaZ <= 1.0 && (deltaX + deltaZ) <= 2.0) {
        orangeSupportESP = candidateSupport;
        greenLadderESP = candidateLadder;
        lockedStructureCell = candidateLadder;
        lockedSupport = new Object[] {candidateSupport, candidateFace};
        lockedLandingESP = blueLandingESP;
      }
    }

    if (autoCenter.getValue()) {
      Vec3 alignTarget = blueLandingESP;
      if (alignTarget != null) {
        double targetX = alignTarget.xCoord + 0.5;
        double targetZ = alignTarget.zCoord + 0.5;

        if (lockedSupport != null) {
          Vec3 supportBlock = (Vec3) lockedSupport[0];
          double dxSup = targetX - (supportBlock.xCoord + 0.5);
          double dzSup = targetZ - (supportBlock.zCoord + 0.5);
          double lenSup = Math.sqrt(dxSup * dxSup + dzSup * dzSup);
          if (lenSup > 0) {
            targetX += (dxSup / lenSup) * 0.4;
            targetZ += (dzSup / lenSup) * 0.4;
          }
        }

        double diffX = targetX - Math.floor(mc.thePlayer.posX);
        double diffZ = targetZ - Math.floor(mc.thePlayer.posZ);
        double distSq = diffX * diffX + diffZ * diffZ;

        if (distSq > 0.04) {
          double dist = Math.sqrt(distSq);
          double motionX = mc.thePlayer.motionX;
          double motionZ = mc.thePlayer.motionZ;
          double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);

          float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;
          float yawDiff = wrapYawDelta(mc.thePlayer.rotationYaw, targetYaw);

          boolean pressF = yawDiff > -90 && yawDiff < 90;
          boolean pressB = yawDiff >= 90 || yawDiff <= -90;
          boolean pressR = yawDiff > 0;
          boolean pressL = yawDiff < 0;

          if (dist < speed * 3.0 && speed > 0.05) {
            float motionYaw = (float) Math.toDegrees(Math.atan2(motionZ, motionX)) - 90f;
            float motionYawDiff = wrapYawDelta(mc.thePlayer.rotationYaw, motionYaw);

            pressF = false;
            pressB = false;
            pressL = false;
            pressR = false;
            if (motionYawDiff >= 135 || motionYawDiff <= -135) pressF = true;
            else if (motionYawDiff >= 45 && motionYawDiff < 135) pressL = true;
            else if (motionYawDiff <= -45 && motionYawDiff > -135) pressR = true;
            else pressB = true;
          }

          setKeyBindState(mc.gameSettings.keyBindSprint, false);
          setKeyBindState(mc.gameSettings.keyBindForward, pressF);
          setKeyBindState(mc.gameSettings.keyBindBack, pressB);
          setKeyBindState(mc.gameSettings.keyBindLeft, pressL);
          setKeyBindState(mc.gameSettings.keyBindRight, pressR);
          wasCentering = true;
        } else {
          resetWSAD();
        }
      }
    }

    if (waitingForBase) {
      pendingBaseTicks++;
      if (pendingBaseTicks > 20) {
        waitingForBase = false;
        pendingBase = null;
        pendingBaseTicks = 0;
        lockedStructureCell = null;
        lockedSupport = null;
        lockedLandingESP = null;
      }
    }

    if (!placeQueued) return;
    if (System.currentTimeMillis() - lastPlaceTime < nextRandomDelay) return;

    placeQueued = false;
    if (actionSlot < 0 || actionSlot > 8 || hitAt == null || hitVec == null || hitSide == null) {
      clearQueuedAction();
      return;
    }

    mc.thePlayer.inventory.currentItem = actionSlot;
    ItemStack stack = mc.thePlayer.getHeldItem();
    boolean placed =
        mc.playerController.onPlayerRightClick(
            mc.thePlayer, mc.theWorld, stack, toBlockPos(hitAt), hitSide, hitVec);

    if (placed) {
      mc.thePlayer.swingItem();
      lastPlaceTime = System.currentTimeMillis();

      if (queuedAction == ACTION_BASE) {
        waitingForBase = true;
        pendingBase = targetCell;
        pendingBaseTicks = 0;
        nextRandomDelay = 0L;
      } else if (queuedAction == ACTION_LADDER) {
        waitingForBase = false;
        pendingBase = null;
        pendingBaseTicks = 0;
        lockedStructureCell = null;
        lockedSupport = null;
        lockedLandingESP = null;

        double baseDelay = placeDelay.getValue().doubleValue();
        nextRandomDelay = (long) (baseDelay + (Math.random() * 40.0 - 15.0));

        setKeyBindState(mc.gameSettings.keyBindSneak, true);
        isClutching = true;
      }
    }

    if (originalSlot != -1) mc.thePlayer.inventory.currentItem = originalSlot;
    clearQueuedAction();
  }

  private Float[] getRotations() {
    if (mc.thePlayer == null) return null;

    if (findLadderSlot() == -1) return null;

    if (placeQueued && hasAim) {
      return new Float[] {aimYaw, aimPitch};
    }
    clearQueuedAction();

    if (waitingForBase && pendingBase != null) {
      int targetItemSlot = findLadderSlot();
      if (targetItemSlot != -1 && computeSecondaryPlacement(pendingBase, targetItemSlot)) {
        return new Float[] {aimYaw, aimPitch};
      }

      if (lockedSupport != null && findBuildingBlockSlot() != -1) {
        Vec3 primaryNode = (Vec3) lockedSupport[0];
        EnumFacing nodeFace = (EnumFacing) lockedSupport[1];
        if (calculateVectorAngle(
            primaryNode, nodeFace, pendingBase, findBuildingBlockSlot(), ACTION_BASE)) {
          return new Float[] {aimYaw, aimPitch};
        }
      }
      return null;
    }

    double maxReach = reach.getValue();
    double fallThreshold = fallDistance.getValue().doubleValue();
    boolean fallThresholdMet = mc.thePlayer.fallDistance >= fallThreshold;

    if (fallThresholdMet) {
      if (lockedStructureCell != null) {
        Vec3 eyePos =
            new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);
        Vec3 primaryNode = (Vec3) lockedSupport[0];
        EnumFacing nodeFace = (EnumFacing) lockedSupport[1];
        double currentDist = eyePos.distanceTo(facePoint(primaryNode, nodeFace));

        if (currentDist <= maxReach) {
          int primaryItemSlot = findBuildingBlockSlot();
          if (primaryItemSlot != -1
              && calculateVectorAngle(
                  primaryNode, nodeFace, lockedStructureCell, primaryItemSlot, ACTION_BASE)) {
            return new Float[] {aimYaw, aimPitch};
          }
        }
      }

      int targetItemSlot = findLadderSlot();
      if (targetItemSlot != -1) {
        if (computePrimaryPlacement(targetItemSlot, maxReach)) {
          return new Float[] {aimYaw, aimPitch};
        }
      }
    }
    return null;
  }

  private float normYaw(float rawAngle) {
    rawAngle = ((rawAngle % 360f) + 360f) % 360f;
    return (rawAngle > 180f) ? (rawAngle - 360f) : rawAngle;
  }

  private float wrapYawDelta(float angleBase, float angleTarget) {
    float deltaValue = angleTarget - angleBase;
    while (deltaValue <= -180f) deltaValue += 360f;
    while (deltaValue > 180f) deltaValue -= 360f;
    return deltaValue;
  }

  private float[] getAngles(Vec3 originEye, double tx, double ty, double tz) {
    double dx = tx - originEye.xCoord, dy = ty - originEye.yCoord, dz = tz - originEye.zCoord;
    double planarDist = Math.sqrt(dx * dx + dz * dz);
    float processedYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    processedYaw = normYaw(processedYaw);
    float processedPitch = (float) Math.toDegrees(-Math.atan2(dy, planarDist));
    return new float[] {processedYaw, processedPitch};
  }

  private Object[] evalGridCoordinates(Vec3 nodeBlock, EnumFacing faceOrientation) {
    Vec3 eyeCoordinate =
        new Vec3(
            mc.thePlayer.posX,
            mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
            mc.thePlayer.posZ);
    float baseTrackYaw = normYaw(serverYaw);
    float baseTrackPitch = serverPitch;
    float clientInputYaw = normYaw(mc.thePlayer.rotationYaw);
    float clientInputPitch = mc.thePlayer.rotationPitch;

    double pad = 0.05, step = 0.2, jitter = 0.2;
    double max = 1 - pad - 1e-3, min = pad + 1e-3;
    int res = (int) Math.round(1 / step);

    ArrayList<Object[]> perms = new ArrayList<>();

    for (int r = 0; r <= res; r++) {
      boolean dir = (r & 1) == 0;
      double v = r * step + (Math.random() * 2.0 - 1.0) * step * jitter;
      if (v < 0) v = 0;
      else if (v > 1) v = 1;

      for (int c = 0; c <= res; c++) {
        double rawU = c * step + (Math.random() * 2.0 - 1.0) * step * jitter;
        if (rawU < 0) rawU = 0;
        else if (rawU > 1) rawU = 1;
        double u = dir ? rawU : 1 - rawU;

        double x = nodeBlock.xCoord, y = nodeBlock.yCoord, z = nodeBlock.zCoord;

        if (faceOrientation == EnumFacing.UP) {
          x += u;
          z += v;
          y += max;
        } else if (faceOrientation == EnumFacing.DOWN) {
          x += u;
          z += v;
          y += min;
        } else if (faceOrientation == EnumFacing.SOUTH) {
          x += u;
          y += v;
          z += max;
        } else if (faceOrientation == EnumFacing.NORTH) {
          x += u;
          y += v;
          z += min;
        } else if (faceOrientation == EnumFacing.EAST) {
          z += u;
          y += v;
          x += max;
        } else if (faceOrientation == EnumFacing.WEST) {
          z += u;
          y += v;
          x += min;
        } else continue;

        float[] angles = getAngles(eyeCoordinate, x, y, z);
        float pYaw = angles[0], pPitch = angles[1];

        if (Math.abs(pPitch - clientInputPitch) > 90f) continue;
        if (Math.abs(pPitch) > 90f) continue;

        double score =
            Math.abs((double) wrapYawDelta(baseTrackYaw, pYaw))
                + Math.abs((double) (pPitch - baseTrackPitch));
        perms.add(new Object[] {score, pYaw, pPitch, new Vec3(x, y, z)});
      }
    }

    if (perms.isEmpty()) return null;
    perms.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
    double reachDist = reach.getValue();

    for (Object[] perm : perms) {
      float oYaw = (Float) perm[1];
      float oPitch = (Float) perm[2];
      float realYaw = serverYaw + wrapYawDelta(serverYaw, oYaw);
      Vec3 hitVec = (Vec3) perm[3];

      MovingObjectPosition result = raycastBlock(reachDist, realYaw, oPitch);
      if (result != null
          && result.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
          && sameCell(toVec3(result.getBlockPos()), nodeBlock)
          && faceOrientation == result.sideHit) {
        return new Object[] {realYaw, oPitch, hitVec};
      }
    }
    return null;
  }

  private boolean calculateVectorAngle(
      Vec3 blockTarget,
      EnumFacing blockFace,
      Vec3 placementCell,
      int targetHotbarSlot,
      int internalActionCode) {
    Object[] evaluationResult = evalGridCoordinates(blockTarget, blockFace);
    if (evaluationResult == null) return false;

    hitAt = blockTarget;
    aimYaw = (Float) evaluationResult[0];
    aimPitch = (Float) evaluationResult[1];
    hitVec = (Vec3) evaluationResult[2];
    hitSide = blockFace;
    targetCell = placementCell;
    actionSlot = targetHotbarSlot;
    queuedAction = internalActionCode;
    hasAim = true;
    placeQueued = true;
    return true;
  }

  private boolean computePrimaryPlacement(int inventorySlotIndex, double distanceBound) {
    if (blueLandingESP == null) return false;

    Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    Vec3 eyePos =
        new Vec3(
            mc.thePlayer.posX,
            mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
            mc.thePlayer.posZ);

    int tX = (int) Math.floor(blueLandingESP.xCoord);
    int tZ = (int) Math.floor(blueLandingESP.zCoord);
    int tY = (int) Math.floor(blueLandingESP.yCoord);

    ArrayList<Object[]> nodes = new ArrayList<>();
    EnumFacing[] faces =
        new EnumFacing[] {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    for (int y = tY + 2; y >= tY - 2; y--) {
      for (int x = tX - 1; x <= tX + 1; x++) {
        for (int z = tZ - 1; z <= tZ + 1; z++) {
          BlockPos blockPos = new BlockPos(x, y, z);
          Block b = worldBlock(blockPos);
          if (!isSolid(b)) continue;

          for (EnumFacing face : faces) {
            BlockPos ladderCell = blockPos.offset(face);
            if (!isAir(ladderCell)) continue;

            if ((int) Math.floor(ladderCell.getX()) != tX
                || (int) Math.floor(ladderCell.getZ()) != tZ) continue;

            Vec3 facePt = facePoint(toVec3(blockPos), face);
            double distSq = eyePos.squareDistanceTo(facePt);
            if (distSq > distanceBound * distanceBound) continue;

            double weight = Math.abs(ladderCell.getY() - playerPos.yCoord) * 0.35;
            double score = distSq + weight;
            nodes.add(new Object[] {score, blockPos, face, ladderCell});
          }
        }
      }
    }

    if (nodes.isEmpty()) return false;
    nodes.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));

    for (Object[] node : nodes) {
      if (calculateVectorAngle(
          toVec3((BlockPos) node[1]),
          (EnumFacing) node[2],
          toVec3((BlockPos) node[3]),
          inventorySlotIndex,
          ACTION_LADDER)) {
        return true;
      }
    }
    return false;
  }

  private boolean computeSecondaryPlacement(Vec3 structureBasePosition, int slotMappingIndex) {
    EnumFacing dir = faceTowardPlayer(structureBasePosition);
    EnumFacing[] faces = orderedHorizontalFaces(dir);

    for (EnumFacing f : faces) {
      Vec3 ladderCell = offsetCell(structureBasePosition, f);
      if (!isAirCell(ladderCell)) continue;
      if (calculateVectorAngle(structureBasePosition, f, ladderCell, slotMappingIndex, ACTION_LADDER)) {
        return true;
      }
    }
    return false;
  }

  private Object[] findPlacementSupport(Vec3 blockGridCoordinate) {
    EnumFacing[] dirs =
        new EnumFacing[] {
          EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
        };
    for (EnumFacing d : dirs) {
      Vec3 node = offsetCell(blockGridCoordinate, d);
      Block b = worldBlock(node);
      if (!isSolid(b)) continue;
      return new Object[] {node, d.getOpposite()};
    }
    return null;
  }

  private int findLadderSlot() {
    for (int i = 0; i < 9; i++) {
      ItemStack item = mc.thePlayer.inventory.getStackInSlot(i);
      if (item == null || item.stackSize <= 0) continue;
      if (item.getItem().getUnlocalizedName().toLowerCase().contains("ladder")) return i;
    }
    return -1;
  }

  private int findBuildingBlockSlot() {
    for (int i = 0; i < 9; i++) {
      ItemStack item = mc.thePlayer.inventory.getStackInSlot(i);
      if (item == null || item.stackSize < 1 || !(item.getItem() instanceof ItemBlock)) continue;
      if (item.getItem().getUnlocalizedName().toLowerCase().contains("ladder")) continue;
      String name = item.getItem().getUnlocalizedName().toLowerCase();
      for (String id : BLOCKS) {
        if (name.contains(id)) return i;
      }
    }
    return -1;
  }

  private Block worldBlock(Vec3 pos) {
    return BlockUtil.getBlock(new BlockPos(pos.xCoord, pos.yCoord, pos.zCoord));
  }

  private Block worldBlock(BlockPos pos) {
    return BlockUtil.getBlock(pos);
  }

  private boolean isAirCell(Vec3 pos) {
    Block b = worldBlock(pos);
    return b instanceof BlockAir;
  }

  private boolean isAir(BlockPos pos) {
    Block b = worldBlock(pos);
    return b instanceof BlockAir;
  }

  private boolean isSolid(Block b) {
    if (b == null) return false;
    if (b instanceof BlockAir) return false;
    if (b instanceof BlockLiquid) return false;
    if (b instanceof BlockFire) return false;
    return true;
  }

  private boolean isHorizontal(EnumFacing s) {
    return s == EnumFacing.NORTH
        || s == EnumFacing.SOUTH
        || s == EnumFacing.EAST
        || s == EnumFacing.WEST;
  }

  private Vec3 sideOffset(EnumFacing s) {
    return new Vec3(s.getFrontOffsetX(), s.getFrontOffsetY(), s.getFrontOffsetZ());
  }

  private Vec3 offsetCell(Vec3 pos, EnumFacing s) {
    return pos.addVector(s.getFrontOffsetX(), s.getFrontOffsetY(), s.getFrontOffsetZ());
  }

  private EnumFacing getPlayerFacing(float yaw) {
    float a = ((yaw % 360f) + 360f) % 360f;
    if (a < 45f || a >= 315f) return EnumFacing.SOUTH;
    if (a < 135f) return EnumFacing.WEST;
    if (a < 225f) return EnumFacing.NORTH;
    return EnumFacing.EAST;
  }

  private EnumFacing faceTowardPlayer(Vec3 blockPos) {
    double dx = mc.thePlayer.posX - (blockPos.xCoord + 0.5);
    double dz = mc.thePlayer.posZ - (blockPos.zCoord + 0.5);
    if (Math.abs(dx) > Math.abs(dz)) {
      return dx >= 0 ? EnumFacing.EAST : EnumFacing.WEST;
    }
    return dz >= 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
  }

  private EnumFacing[] orderedHorizontalFaces(EnumFacing primary) {
    if (primary == EnumFacing.NORTH)
      return new EnumFacing[] {
        EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH
      };
    if (primary == EnumFacing.SOUTH)
      return new EnumFacing[] {
        EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.NORTH
      };
    if (primary == EnumFacing.EAST)
      return new EnumFacing[] {
        EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.WEST
      };
    return new EnumFacing[] {EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST};
  }

  private Vec3 facePoint(Vec3 pos, EnumFacing face) {
    return new Vec3(
        pos.xCoord + 0.5 + face.getFrontOffsetX() * 0.5,
        pos.yCoord + 0.5 + face.getFrontOffsetY() * 0.5,
        pos.zCoord + 0.5 + face.getFrontOffsetZ() * 0.5);
  }

  private boolean sameCell(Vec3 a, Vec3 b) {
    if (a == null || b == null) return false;
    return (int) Math.floor(a.xCoord) == (int) Math.floor(b.xCoord)
        && (int) Math.floor(a.yCoord) == (int) Math.floor(b.yCoord)
        && (int) Math.floor(a.zCoord) == (int) Math.floor(b.zCoord);
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

  private void resetWSAD() {
    setKeyBindState(mc.gameSettings.keyBindSprint, false);
    setKeyBindState(mc.gameSettings.keyBindForward, false);
    setKeyBindState(mc.gameSettings.keyBindBack, false);
    setKeyBindState(mc.gameSettings.keyBindLeft, false);
    setKeyBindState(mc.gameSettings.keyBindRight, false);
  }

  private void resetState() {
    if (originalSlot != -1 && mc.thePlayer != null) {
      mc.thePlayer.inventory.currentItem = originalSlot;
    }
    if (isClutching) setKeyBindState(mc.gameSettings.keyBindSneak, false);
    if (wasCentering) resetWSAD();

    originalSlot = -1;
    isClutching = false;
    waitingForBase = false;
    wasCentering = false;
    pendingBase = null;
    pendingBaseTicks = 0;

    lockedLandingESP = null;
    blueLandingESP = null;
    orangeSupportESP = null;
    greenLadderESP = null;

    lockedStructureCell = null;
    lockedSupport = null;
    clearQueuedAction();
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled()) return;
    if (!esp.getValue()) return;

    if (blueLandingESP != null) drawEsp(blueLandingESP, 0x220000FF, false, true);
    if (orangeSupportESP != null) drawEsp(orangeSupportESP, 0x22FF8C00, false, true);
    if (greenLadderESP != null) drawEsp(greenLadderESP, 0x9900FF00, true, false);
  }

  private void drawEsp(Vec3 pos, int rgb, boolean fill, boolean outline) {
    BlockPos blockPos = new BlockPos(pos.xCoord, pos.yCoord, pos.zCoord);
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    int a = (rgb >> 24) & 0xFF;
    if (fill) RenderUtil.drawBlockBox(blockPos, 1.0, r, g, b);
    if (outline) RenderUtil.drawBlockBoundingBox(blockPos, 1.0, r, g, b, a, 1.0f);
  }

  private Vec3 toVec3(BlockPos pos) {
    return new Vec3(pos.getX(), pos.getY(), pos.getZ());
  }

  private BlockPos toBlockPos(Vec3 pos) {
    return new BlockPos(pos.xCoord, pos.yCoord, pos.zCoord);
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

  private void setKeyBindState(KeyBinding binding, boolean pressed) {
    KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
  }
}
