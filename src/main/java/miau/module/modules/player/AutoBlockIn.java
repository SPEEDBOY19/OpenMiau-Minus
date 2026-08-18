package miau.module.modules.player;

import java.util.*;
import java.util.List;
import java.util.Queue;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.ItemUtil;
import miau.util.player.MoveUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

public class AutoBlockIn extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
  private long lastPlaceTime = 0;

  public final FloatProperty range = new FloatProperty("range", 4.5f, 3.0f, 6.0f);
  public final IntProperty speed = new IntProperty("speed", 20, 5, 100);
  public final IntProperty placeDelay = new IntProperty("place-delay", 50, 0, 200);
  public final IntProperty rotationTolerance = new IntProperty("rotation-tolerance", 25, 5, 100);
  public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", true);
  public final BooleanProperty showProgress = new BooleanProperty("show-progress", true);
  public final ModeProperty moveFix =
      new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT", "STRICT"});
  public final BooleanProperty disableOnMove = new BooleanProperty("disable-on-move", false);
  public final BooleanProperty autoDisable = new BooleanProperty("auto-disable", true);

  private BlockPos startPos;
  private float serverYaw;
  private float serverPitch;
  private float progress;
  private float aimYaw;
  private float aimPitch;
  private BlockPos targetBlock;
  private EnumFacing targetFacing;
  private Vec3 targetHitVec;
  private int lastSlot = -1;
  private float animStartProgress = 0F;
  private float animTargetProgress = 0F;
  private long animStartTime = 0L;
  private float lastProgress = -1;

  private static final int[][] DIRS = {{1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}};
  private static final double INSET = 0.05;
  private static final double STEP = 0.2;
  private static final double JIT = STEP * 0.1;

  public AutoBlockIn() {
    super("AutoBlockIn", false);

    BLOCK_SCORE.put("obsidian", 0);
    BLOCK_SCORE.put("end_stone", 1);
    BLOCK_SCORE.put("planks", 2);
    BLOCK_SCORE.put("log", 2);
    BLOCK_SCORE.put("glass", 3);
    BLOCK_SCORE.put("stained_glass", 3);
    BLOCK_SCORE.put("hardened_clay", 4);
    BLOCK_SCORE.put("stained_hardened_clay", 4);
    BLOCK_SCORE.put("cloth", 5);
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer != null) {
      serverYaw = mc.thePlayer.rotationYaw;
      serverPitch = mc.thePlayer.rotationPitch;
      aimYaw = serverYaw;
      aimPitch = serverPitch;
      progress = 0;
      lastSlot = mc.thePlayer.inventory.currentItem;
      targetBlock = null;
      targetFacing = null;
      targetHitVec = null;
      lastPlaceTime = 0;
      startPos = mc.thePlayer.getPosition();
    }
  }

  @Override
  public void onDisabled() {
    if (lastSlot != -1 && mc.thePlayer != null && mc.thePlayer.inventory.currentItem != lastSlot) {
      mc.thePlayer.inventory.currentItem = lastSlot;
    }
    progress = 0;
    targetBlock = null;
    targetFacing = null;
    targetHitVec = null;
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent event) {
    if (!isEnabled()) return;
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.currentScreen != null) {
      return;
    }

    serverYaw = event.getYaw();
    serverPitch = event.getPitch();

    if (disableOnMove.getValue()
        && startPos != null
        && !mc.thePlayer.getPosition().equals(startPos)) {
      this.toggle();
      return;
    }

    updateProgress();

    if (autoDisable.getValue() && progress >= 1.0f) {
      this.toggle();
      return;
    }

    // Don't switch off a sword — player expects right-click to block, not place a block
    if (!ItemUtil.isHoldingSword()) {
      // If we have a target, pick strong/weak block based on adjacency
      boolean adjacent = targetBlock != null && isTargetAdjacent(targetBlock);
      int blockSlot = (adjacent) ? findBestBlockSlot(true) : findBestBlockSlot(false);

      if (blockSlot != -1) {
        if (mc.thePlayer.inventory.currentItem != blockSlot) {
          mc.thePlayer.inventory.currentItem = blockSlot;
        }
      }
    }

    ItemStack currentHeld = mc.thePlayer.inventory.getCurrentItem();
    boolean holdingBlock = currentHeld != null && currentHeld.getItem() instanceof ItemBlock;
    if (!holdingBlock) {
      targetBlock = null;
      targetFacing = null;
      targetHitVec = null;
      return;
    }

    findBestPlacement();

    if (targetBlock != null && targetFacing != null && targetHitVec != null) {
      Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
      double dx = targetHitVec.xCoord - eyes.xCoord;
      double dy = targetHitVec.yCoord - eyes.yCoord;
      double dz = targetHitVec.zCoord - eyes.zCoord;
      double dist = Math.sqrt(dx * dx + dz * dz);

      float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
      float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

      targetYaw = MathHelper.wrapAngleTo180_float(targetYaw);

      float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
      float pitchDiff = targetPitch - serverPitch;

      float maxTurn = speed.getValue().floatValue();
      float yawStep = MathHelper.clamp_float(yawDiff, -maxTurn, maxTurn);
      float pitchStep = MathHelper.clamp_float(pitchDiff, -maxTurn, maxTurn);

      aimYaw = serverYaw + yawStep;
      aimPitch = MathHelper.clamp_float(serverPitch + pitchStep, -90.0f, 90.0f);

      event.setRotation(aimYaw, aimPitch, 6);
      event.setPervRotation(this.moveFix.getValue() != 0 ? aimYaw : mc.thePlayer.rotationYaw, 6);
    }
  }

  @EventTarget
  public void onMove(MoveInputEvent event) {
    if (this.isEnabled()) {
      if (this.moveFix.getValue() == 1
          && RotationState.isActived()
          && RotationState.getPriority() == 6
          && MoveUtil.isForwardPressed()) {
        MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
      }
    }
  }

  @EventTarget(Priority.HIGH)
  public void onTick(TickEvent event) {
    if (!isEnabled()) return;
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.currentScreen != null) {
      return;
    }

    if (targetBlock != null && targetFacing != null && targetHitVec != null) {
      if (!withinRotationTolerance(aimYaw, aimPitch)) {
        return;
      }

      long currentTime = System.currentTimeMillis();
      if (currentTime - lastPlaceTime >= placeDelay.getValue()) {
        lastPlaceTime = currentTime;

        MovingObjectPosition mop = rayTraceBlock(aimYaw, aimPitch, range.getValue());

        if (mop != null
            && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && mop.getBlockPos().equals(targetBlock)
            && mop.sideHit == targetFacing) {

          ItemStack heldStack = mc.thePlayer.inventory.getCurrentItem();
          if (heldStack != null && heldStack.getItem() instanceof ItemBlock) {
            mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, heldStack, targetBlock, targetFacing, mop.hitVec);
            mc.thePlayer.swingItem();

            targetBlock = null;
            targetFacing = null;
            targetHitVec = null;
          }
        }
      }
    }
  }

  @EventTarget
  public void onSwap(SwapItemEvent event) {
    if (this.isEnabled()) {
      lastSlot = event.setSlot(lastSlot);
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!isEnabled() || mc.currentScreen != null) return;
    if (!showProgress.getValue() || mc.fontRendererObj == null) return;

    // Animate progress changes
    if (progress != lastProgress) {
      animStartProgress = progress;
      animTargetProgress = progress;
      animStartTime = System.currentTimeMillis();
      lastProgress = progress;
    }

    long elapsed = System.currentTimeMillis() - animStartTime;
    final long animDuration = 250L;
    float displayProgress;
    if (elapsed < animDuration) {
      float t = (float) elapsed / (float) animDuration;
      displayProgress = quadInOutEasing(t);
    } else {
      displayProgress = 1.0F;
    }

    ScaledResolution sr = new ScaledResolution(mc);
    float radius = 10F;
    float thickness = 3F;
    float cx = sr.getScaledWidth() / 2F - 1F;
    float cy = sr.getScaledHeight() / 2F;

    // Background circle (translucent)
    drawCircle(cx, cy, radius, 80, thickness, 0F, 0F, 0F, 0.4F);

    // Full green if complete
    if (progress >= 0.999F) {
      drawCircle(cx, cy, radius, 80, thickness, 0F, 1F, 0F, 1F);
      return;
    }

    // Partial arc — red→green gradient
    float ratio = Math.max(0F, Math.min(1F, progress * displayProgress));
    float startAngle = 90F;
    float endAngle = startAngle + ratio * 360F + 0.5F;
    int r = (int) ((1F - ratio) * 255F + 0.5F);
    int g = (int) (ratio * 255F + 0.5F);
    r = Math.max(0, Math.min(255, r));
    g = Math.max(0, Math.min(255, g));
    int color = (255 << 24) | (r << 16) | (g << 8) | 0;

    drawCircleArc(cx, cy, radius, startAngle, endAngle, thickness, color);
  }

  /**
   * Find the best block slot in hotbar.
   *
   * @param preferStrong true = lowest score (obsidian/end_stone) for adjacent placements, false =
   *     highest score (wool) for distant placements to save resources
   */
  private int findBestBlockSlot(boolean preferStrong) {
    int bestSlot = -1;
    int bestScore = preferStrong ? Integer.MAX_VALUE : Integer.MIN_VALUE;

    for (int slot = 0; slot <= 8; slot++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
      if (stack == null || stack.stackSize == 0) continue;

      if (stack.getItem() instanceof ItemBlock) {
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        String blockName = block.getUnlocalizedName().replace("tile.", "");

        Integer score = BLOCK_SCORE.get(blockName);
        if (score != null) {
          if (preferStrong ? score < bestScore : score > bestScore) {
            bestScore = score;
            bestSlot = slot;
            if (preferStrong && score == 0) break;
          }
        }
      }
    }

    return bestSlot;
  }

  private void findBestPlacement() {
    Vec3 playerPos = mc.thePlayer.getPositionVector();
    BlockPos feetPos = new BlockPos(playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);

    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    double reach = range.getValue().doubleValue();
    double reachSq = reach * reach;
    double rp12 = (reach + 1) * (reach + 1);

    BlockPos roofTarget = feetPos.up(2);

    if (!isAir(roofTarget)) {
      sidesAim(eye, reach, feetPos);
      return;
    }

    List<BlockData> supports = new ArrayList<>();

    int minX = (int) Math.floor(eye.xCoord - reach);
    int maxX = (int) Math.floor(eye.xCoord + reach);
    int minY = (int) Math.floor(eye.yCoord - 1);
    int maxY = (int) Math.floor(eye.yCoord + reach);
    int minZ = (int) Math.floor(eye.zCoord - reach);
    int maxZ = (int) Math.floor(eye.zCoord + reach);

    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          BlockPos p = new BlockPos(x, y, z);
          if (isAir(p)) continue;

          double dx = (x + 0.5) - eye.xCoord;
          double dy = (y + 0.5) - eye.yCoord;
          double dz = (z + 0.5) - eye.zCoord;
          if (dx * dx + dy * dy + dz * dz > rp12) continue;

          double d2 = dist2PointAABB(eye, x, y, z);
          if (d2 > reachSq) continue;

          Vec3 mid = new Vec3(x + 0.5, y + 0.5, z + 0.5);
          MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eye, mid, false, false, false);
          if (mop == null) continue;
          if (!mop.getBlockPos().equals(p)) continue;

          supports.add(new BlockData(p, d2));
        }
      }
    }
    if (supports.isEmpty()) {
      sidesAim(eye, reach, feetPos);
      return;
    }
    supports.sort(Comparator.comparingDouble(a -> a.distance));
    for (BlockData bd : supports) {
      if (tryPlaceOnBlock(bd.pos, eye, reach, roofTarget)) {
        return;
      }
    }
    Queue<BlockPos> q = new LinkedList<>();
    Map<BlockPos, BlockPos> parent = new HashMap<>();
    Set<BlockPos> visited = new HashSet<>();
    for (BlockData bd : supports) {
      BlockPos sup = bd.pos;
      for (EnumFacing f : EnumFacing.values()) {
        BlockPos node = sup.offset(f);
        if (!isAir(node)) continue;
        if (visited.contains(node)) continue;
        visited.add(node);
        parent.put(node, null);
        q.add(node);
      }
    }
    BlockPos endNode = null;
    int nodesSeen = 0;
    while (!q.isEmpty() && nodesSeen < 8964) {
      BlockPos cur = q.poll();
      nodesSeen++;
      if (cur.distanceSq(roofTarget) <= 1.5) {
        endNode = cur;
        break;
      }
      for (EnumFacing f : EnumFacing.values()) {
        BlockPos nxt = cur.offset(f);
        if (visited.contains(nxt)) continue;
        if (!isAir(nxt)) continue;
        visited.add(nxt);
        parent.put(nxt, cur);
        q.add(nxt);
      }
    }

    if (endNode == null) {
      sidesAim(eye, reach, feetPos);
      return;
    }

    List<BlockPos> path = new ArrayList<>();
    for (BlockPos cur = endNode; cur != null; cur = parent.get(cur)) {
      path.add(cur);
    }
    Collections.reverse(path);

    for (BlockPos place : path) {
      if (!isAir(place)) continue;

      boolean placedThis = false;
      for (BlockData bd : supports) {
        BlockPos sup = bd.pos;
        if (!isAdjacent(sup, place)) continue;
        if (tryPlaceOnBlock(sup, eye, reach, place)) {
          return;
        }
      }
      for (EnumFacing f : EnumFacing.values()) {
        BlockPos sup = place.offset(f);
        if (isAir(sup)) continue;
        if (tryPlaceOnBlock(sup, eye, reach, place)) {
          return;
        }
      }
      if (placedThis) break;
    }
    sidesAim(eye, reach, feetPos);
  }

  private boolean isAdjacent(BlockPos a, BlockPos b) {
    int dx = Math.abs(a.getX() - b.getX());
    int dy = Math.abs(a.getY() - b.getY());
    int dz = Math.abs(a.getZ() - b.getZ());
    return (dx + dy + dz) == 1;
  }

  private boolean tryPlaceOnBlock(
      BlockPos supportBlock, Vec3 eye, double reach, BlockPos targetPos) {
    for (EnumFacing facing : EnumFacing.values()) {
      BlockPos placementPos = supportBlock.offset(facing);

      if (!placementPos.equals(targetPos)) continue;

      int n = (int) Math.round(1 / STEP);

      for (int r = 0; r <= n; r++) {
        double v = r * STEP + (Math.random() * JIT * 2 - JIT);
        if (v < 0) v = 0;
        else if (v > 1) v = 1;

        for (int c = 0; c <= n; c++) {
          double u = c * STEP + (Math.random() * JIT * 2 - JIT);
          if (u < 0) u = 0;
          else if (u > 1) u = 1;

          Vec3 hitPos = getHitPosOnFace(supportBlock, facing, u, v);
          float[] rot = getRotationsWrapped(eye, hitPos.xCoord, hitPos.yCoord, hitPos.zCoord);

          MovingObjectPosition mop = rayTraceBlock(rot[0], rot[1], reach);
          if (mop != null
              && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
              && mop.getBlockPos().equals(supportBlock)
              && mop.sideHit == facing) {

            targetBlock = supportBlock;
            targetFacing = facing;
            targetHitVec = mop.hitVec;
            aimYaw = rot[0];
            aimPitch = rot[1];
            return true;
          }
        }
      }
    }

    return false;
  }

  private void sidesAim(Vec3 eye, double reach, BlockPos feetPos) {
    List<BlockPos> goals = new ArrayList<>();

    for (int[] d : DIRS) {
      BlockPos headPos = feetPos.add(d[0], 1, d[2]);
      if (isAir(headPos)) {
        goals.add(headPos);
      }
    }

    for (int[] d : DIRS) {
      BlockPos feetGoal = feetPos.add(d[0], 0, d[2]);
      if (isAir(feetGoal)) {
        goals.add(feetGoal);
      }
    }

    if (goals.isEmpty()) return;

    // Enemy-aware sorting: prioritize blocks nearer to enemies
    EntityPlayer enemy = getClosestEnemy();
    if (enemy != null) {
      goals.sort(java.util.Comparator.comparingDouble(p -> p.distanceSq(enemy.getPosition())));
    }

    findBestForGoals(goals, eye, reach);
  }

  private void findBestForGoals(List<BlockPos> goals, Vec3 eye, double reach) {
    for (BlockPos goal : goals) {
      for (EnumFacing facing : EnumFacing.values()) {
        BlockPos support = goal.offset(facing);

        if (isAir(support)) continue;

        Vec3 center = new Vec3(support.getX() + 0.5, support.getY() + 0.5, support.getZ() + 0.5);
        if (eye.distanceTo(center) > reach) continue;

        int n = (int) Math.round(1 / STEP);
        for (int r = 0; r <= n; r++) {
          double v = r * STEP + (Math.random() * JIT * 2 - JIT);
          if (v < 0) v = 0;
          else if (v > 1) v = 1;

          for (int c = 0; c <= n; c++) {
            double u = c * STEP + (Math.random() * JIT * 2 - JIT);
            if (u < 0) u = 0;
            else if (u > 1) u = 1;

            Vec3 hitPos = getHitPosOnFace(support, facing.getOpposite(), u, v);
            float[] rot = getRotationsWrapped(eye, hitPos.xCoord, hitPos.yCoord, hitPos.zCoord);

            MovingObjectPosition mop = rayTraceBlock(rot[0], rot[1], reach);
            if (mop != null
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(support)
                && mop.sideHit == facing.getOpposite()) {

              targetBlock = support;
              targetFacing = facing.getOpposite();
              targetHitVec = mop.hitVec;
              aimYaw = rot[0];
              aimPitch = rot[1];
              return;
            }
          }
        }
      }
    }
  }

  private Vec3 getHitPosOnFace(BlockPos block, EnumFacing face, double u, double v) {
    double x = block.getX() + 0.5;
    double y = block.getY() + 0.5;
    double z = block.getZ() + 0.5;

    switch (face) {
      case DOWN:
        y = block.getY() + INSET;
        x = block.getX() + u;
        z = block.getZ() + v;
        break;
      case UP:
        y = block.getY() + 1.0 - INSET;
        x = block.getX() + u;
        z = block.getZ() + v;
        break;
      case NORTH:
        z = block.getZ() + INSET;
        x = block.getX() + u;
        y = block.getY() + v;
        break;
      case SOUTH:
        z = block.getZ() + 1.0 - INSET;
        x = block.getX() + u;
        y = block.getY() + v;
        break;
      case WEST:
        x = block.getX() + INSET;
        z = block.getZ() + u;
        y = block.getY() + v;
        break;
      case EAST:
        x = block.getX() + 1.0 - INSET;
        z = block.getZ() + u;
        y = block.getY() + v;
        break;
    }

    return new Vec3(x, y, z);
  }

  private boolean isAir(BlockPos pos) {
    Block block = mc.theWorld.getBlockState(pos).getBlock();
    return block == Blocks.air
        || block == Blocks.water
        || block == Blocks.flowing_water
        || block == Blocks.lava
        || block == Blocks.flowing_lava
        || block == Blocks.fire;
  }

  private void updateProgress() {
    Vec3 playerPos = mc.thePlayer.getPositionVector();
    BlockPos feetPos = new BlockPos(playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);

    int filled = 0;
    int total = 9;

    if (!isAir(feetPos.up(2))) {
      filled++;
    }

    for (int[] d : DIRS) {
      if (!isAir(feetPos.add(d[0], 0, d[2]))) {
        filled++;
      }
      if (!isAir(feetPos.add(d[0], 1, d[2]))) {
        filled++;
      }
    }

    progress = (float) filled / (float) total;
  }

  private MovingObjectPosition rayTraceBlock(float yaw, float pitch, double range) {
    float yawRad = (float) Math.toRadians(yaw);
    float pitchRad = (float) Math.toRadians(pitch);

    double x = -Math.sin(yawRad) * Math.cos(pitchRad);
    double y = -Math.sin(pitchRad);
    double z = Math.cos(yawRad) * Math.cos(pitchRad);

    Vec3 start = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 end = start.addVector(x * range, y * range, z * range);

    return mc.theWorld.rayTraceBlocks(start, end);
  }

  private boolean withinRotationTolerance(float targetYaw, float targetPitch) {
    float dy = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - serverYaw));
    float dp = Math.abs(MathHelper.wrapAngleTo180_float(targetPitch - serverPitch));
    return dy <= rotationTolerance.getValue() && dp <= rotationTolerance.getValue();
  }

  private double dist2PointAABB(Vec3 p, int x, int y, int z) {
    double minX = x, maxX = x + 1;
    double minY = y, maxY = y + 1;
    double minZ = z, maxZ = z + 1;

    double cx = clamp(p.xCoord, minX, maxX);
    double cy = clamp(p.yCoord, minY, maxY);
    double cz = clamp(p.zCoord, minZ, maxZ);

    double dx = p.xCoord - cx;
    double dy = p.yCoord - cy;
    double dz = p.zCoord - cz;

    return dx * dx + dy * dy + dz * dz;
  }

  private double clamp(double v, double lo, double hi) {
    return v < lo ? lo : (v > hi ? hi : v);
  }

  private float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
    double dx = tx - eye.xCoord;
    double dy = ty - eye.yCoord;
    double dz = tz - eye.zCoord;
    double hd = Math.sqrt(dx * dx + dz * dz);

    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
    yaw = normYaw(yaw);

    float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));

    return new float[] {yaw, pitch};
  }

  private float normYaw(float yaw) {
    yaw = ((yaw % 360f) + 360f) % 360f;
    return (yaw > 180f) ? (yaw - 360f) : yaw;
  }

  /**
   * Checks if a target block position is directly adjacent to the player (feet+2 or N/S/E/W at feet
   * or head level), meaning a strong block is preferred.
   */
  private boolean isTargetAdjacent(BlockPos target) {
    Vec3 feet = mc.thePlayer.getPositionVector();
    int fx = (int) Math.floor(feet.xCoord);
    int fy = (int) Math.floor(feet.yCoord);
    int fz = (int) Math.floor(feet.zCoord);
    int tx = target.getX();
    int ty = target.getY();
    int tz = target.getZ();
    if (tx == fx && tz == fz && ty == fy + 2) return true;
    for (int[] d : DIRS) {
      if (tx == fx + d[0] && tz == fz + d[2] && (ty == fy || ty == fy + 1)) return true;
    }
    return false;
  }

  /** Finds the nearest enemy player within 10 blocks. */
  private EntityPlayer getClosestEnemy() {
    Vec3 myPos = mc.thePlayer.getPositionVector();
    double boxSize = 10;
    EntityPlayer best = null;
    double bestDist = Double.POSITIVE_INFINITY;

    for (Object obj : mc.theWorld.playerEntities) {
      EntityPlayer p = (EntityPlayer) obj;
      if (p == mc.thePlayer || p.getHealth() <= 0) continue;

      double dx = p.posX - myPos.xCoord;
      if (dx > boxSize || dx < -boxSize) continue;
      double dy = p.posY - myPos.yCoord;
      if (dy > boxSize || dy < -boxSize) continue;
      double dz = p.posZ - myPos.zCoord;
      if (dz > boxSize || dz < -boxSize) continue;

      double d2 = dx * dx + dy * dy + dz * dz;
      if (d2 < bestDist) {
        bestDist = d2;
        best = p;
      }
    }
    return best;
  }


  private static float quadInOutEasing(float t) {
    if (t < 0.5F) return 2F * t * t;
    return -1F + (4F - 2F * t) * t;
  }


  private static void drawCircle(
      float cx,
      float cy,
      float radius,
      int segments,
      float lineWidth,
      float r,
      float g,
      float b,
      float a) {
    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    GL11.glColor4f(r, g, b, a);
    GL11.glLineWidth(lineWidth);

    GL11.glBegin(GL11.GL_LINE_LOOP);
    for (int i = 0; i <= segments; i++) {
      double theta = 2 * Math.PI * i / segments;
      float x = (float) (radius * Math.cos(theta)) + cx;
      float y = (float) (radius * Math.sin(theta)) + cy;
      GL11.glVertex2f(x, y);
    }
    GL11.glEnd();

    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glColor4f(1, 1, 1, 1);
    GL11.glPopMatrix();
  }

  private static void drawCircleArc(
      float cx,
      float cy,
      float radius,
      float startAngle,
      float endAngle,
      float lineWidth,
      int color) {
    float r = ((color >> 16) & 0xFF) / 255F;
    float g = ((color >> 8) & 0xFF) / 255F;
    float b = (color & 0xFF) / 255F;
    float a = ((color >> 24) & 0xFF) / 255F;

    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    GL11.glColor4f(r, g, b, a);
    GL11.glLineWidth(lineWidth);

    GL11.glBegin(GL11.GL_LINE_STRIP);
    for (float angle = startAngle; angle <= endAngle; angle += 1F) {
      double theta = Math.toRadians(angle + 180);
      float x = (float) (radius * Math.cos(theta)) + cx;
      float y = (float) (radius * Math.sin(theta)) + cy;
      GL11.glVertex2f(x, y);
    }
    GL11.glEnd();

    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glColor4f(1, 1, 1, 1);
    GL11.glLineWidth(1);
    GL11.glPopMatrix();
  }

  public int getSlot() {
    return lastSlot;
  }

  private static class BlockData {
    BlockPos pos;
    double distance;

    BlockData(BlockPos pos, double distance) {
      this.pos = pos;
      this.distance = distance;
    }
  }
}
