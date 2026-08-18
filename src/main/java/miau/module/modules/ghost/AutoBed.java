package miau.module.modules.ghost;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import miau.event.EventTarget;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class AutoBed extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final String[] BLOCK_NAMES = new String[] {"Wool", "Wood", "Endstone", "Clay", "Glass"};
  private static final Block[] BLOCKS = new Block[] {
      Blocks.wool,
      Blocks.planks,
      Blocks.end_stone,
      Blocks.hardened_clay,
      Blocks.glass
  };

  public final ModeProperty block1Inside = new ModeProperty("Block 1 Inside", 1, BLOCK_NAMES);
  public final ModeProperty block2Outside = new ModeProperty("Block 2 Outside", 2, BLOCK_NAMES);
  
  public final ModeProperty layoutMode = new ModeProperty("Layout Mode", 2, new String[] {"Basic 1", "Basic 2", "Basic 3"});
  
  public final FloatProperty range = new FloatProperty("range", 4.5f, 3.0f, 6.0f);
  public final IntProperty speed = new IntProperty("speed", 30, 5, 100);
  public final IntProperty smooth = new IntProperty("smooth", 50, 0, 100);
  public final IntProperty placeDelay = new IntProperty("place-delay", 50, 0, 300);
  public final IntProperty rotationTolerance = new IntProperty("rotation-tolerance", 5, 1, 45);
  public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT", "STRICT"});
  
  public final BooleanProperty autoSneak = new BooleanProperty("auto-sneak", true);
  public final BooleanProperty stopSprint = new BooleanProperty("stop-sprint", true);
  public final BooleanProperty autoDisable = new BooleanProperty("auto-disable", true);
  public final BooleanProperty autoSwitch = new BooleanProperty("auto-switch", true);
  public final BooleanProperty rayCastCheck = new BooleanProperty("ray-cast", true);
  public final BooleanProperty silentSwing = new BooleanProperty("silent-swing", false);
  public final BooleanProperty fallbackToWool = new BooleanProperty("fallback-wool", true);

  private final List<BlockPos> bedPositions = new ArrayList<>();
  private final List<BlockPos> targetQueue = new ArrayList<>();
  private final Set<BlockPos> layer1Positions = new HashSet<>();
  
  private long lastPlaceTime = 0;
  private int originalSlot = -1;

  private float serverYaw;
  private float serverPitch;
  private float aimYaw;
  private float aimPitch;
  private BlockPos targetBlock;
  private EnumFacing targetFacing;
  private Vec3 targetHitVec;
  private boolean isSneakingSent = false;
  private boolean shouldPlaceNextTick = false;

  private static final double INSET = 0.08;
  private static final double STEP = 0.2;
  private static final double JIT = STEP * 0.05;

  public AutoBed() {
    super("AutoBed", false);
  }

  @Override
  public void onEnabled() {
    bedPositions.clear();
    targetQueue.clear();
    layer1Positions.clear();
    lastPlaceTime = 0;
    targetBlock = null;
    targetFacing = null;
    targetHitVec = null;
    isSneakingSent = false;
    shouldPlaceNextTick = false;
    
    if (mc.thePlayer != null) {
      originalSlot = mc.thePlayer.inventory.currentItem;
      serverYaw = mc.thePlayer.rotationYaw;
      serverPitch = mc.thePlayer.rotationPitch;
      aimYaw = serverYaw;
      aimPitch = serverPitch;

      sendBlockRequirementMessage();
    }
    
    findBeds();
    buildTargetStructure();
  }

  private void sendBlockRequirementMessage() {
    String b1Name = BLOCK_NAMES[block1Inside.getValue()];
    String b2Name = BLOCK_NAMES[block2Outside.getValue()];
    String msg = "";

    switch (layoutMode.getValue()) {
      case 0:
        msg = EnumChatFormatting.BLUE + "[AutoBed] " + EnumChatFormatting.WHITE + "Need 8 " + b1Name;
        break;
      case 1:
        msg = EnumChatFormatting.BLUE + "[AutoBed] " + EnumChatFormatting.WHITE + "Need 18 " + b1Name + " and 8 " + b2Name;
        break;
      case 2:
        msg = EnumChatFormatting.BLUE + "[AutoBed] " + EnumChatFormatting.WHITE + "Need 22 " + b1Name + " and 8 " + b2Name;
        break;
    }

    mc.thePlayer.addChatMessage(new ChatComponentText(msg));
  }

  @Override
  public void onDisabled() {
    stopSneakingIfNeeded();
    if (originalSlot >= 0 && originalSlot < 9 && mc.thePlayer != null) {
      if (mc.thePlayer.inventory.currentItem != originalSlot) {
        mc.thePlayer.inventory.currentItem = originalSlot;
      }
    }
    bedPositions.clear();
    targetQueue.clear();
    layer1Positions.clear();
    targetBlock = null;
    targetFacing = null;
    targetHitVec = null;
    shouldPlaceNextTick = false;
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent event) {
    if (!isEnabled()) return;
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

    serverYaw = event.getYaw();
    serverPitch = event.getPitch();

    if (stopSprint.getValue() && !targetQueue.isEmpty()) {
      mc.thePlayer.setSprinting(false);
    }

    if (bedPositions.isEmpty()) {
      findBeds();
      if (!bedPositions.isEmpty()) {
        buildTargetStructure();
      }
    }

    if (targetQueue.isEmpty()) {
      stopSneakingIfNeeded();
      if (autoDisable.getValue()) {
        this.toggle();
      }
      return;
    }

    targetQueue.removeIf(pos -> !canReplace(pos));
    if (targetQueue.isEmpty()) {
      stopSneakingIfNeeded();
      return;
    }

    findBestPlacement();

    if (targetBlock != null && targetFacing != null && targetHitVec != null) {
      BlockPos destinationPos = targetBlock.offset(targetFacing);
      Block requiredBlock = getRequiredBlock(destinationPos);
      
      if (autoSwitch.getValue()) {
        int slot = findBlockSlot(requiredBlock);
        
        if (slot == -1 && fallbackToWool.getValue()) {
          slot = findBlockSlot(Blocks.wool);
        }
        
        if (slot == -1) {
          slot = findAnyBlockSlot();
        }

        if (slot != -1 && mc.thePlayer.inventory.currentItem != slot) {
          mc.thePlayer.inventory.currentItem = slot;
        }
      }

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
      float smoothVal = smooth.getValue().floatValue() / 100.0f;
      float smoothedYawDiff = yawDiff * (1.0f - smoothVal * 0.7f);
      float smoothedPitchDiff = pitchDiff * (1.0f - smoothVal * 0.7f);

      float yawStep = MathHelper.clamp_float(smoothedYawDiff, -maxTurn, maxTurn);
      float pitchStep = MathHelper.clamp_float(smoothedPitchDiff, -maxTurn, maxTurn);

      aimYaw = serverYaw + yawStep;
      aimPitch = MathHelper.clamp_float(serverPitch + pitchStep, -90.0f, 90.0f);

      event.setRotation(aimYaw, aimPitch, 6);
      event.setPervRotation(this.moveFix.getValue() != 0 ? aimYaw : mc.thePlayer.rotationYaw, 6);

      boolean placingOnBed = autoSneak.getValue() && (mc.theWorld.getBlockState(targetBlock).getBlock() instanceof BlockBed);
      if (placingOnBed) {
        startSneaking();
      } else {
        stopSneakingIfNeeded();
      }

      shouldPlaceNextTick = withinRotationTolerance(aimYaw, aimPitch);
    } else {
      shouldPlaceNextTick = false;
      stopSneakingIfNeeded();
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
    if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

    if (shouldPlaceNextTick && targetBlock != null && targetFacing != null && targetHitVec != null) {
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastPlaceTime >= placeDelay.getValue()) {

        MovingObjectPosition mop = rayTraceBlock(serverYaw, serverPitch, range.getValue().doubleValue());

        boolean isValidHit = mop != null
            && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && mop.getBlockPos().equals(targetBlock)
            && mop.sideHit == targetFacing;

        if (rayCastCheck.getValue()) {
          if (!isValidHit || mop.hitVec.squareDistanceTo(targetHitVec) > 0.1D) {
            return;
          }
        }

        if (isValidHit) {
          ItemStack heldStack = mc.thePlayer.inventory.getCurrentItem();
          if (heldStack != null && heldStack.getItem() instanceof ItemBlock) {
            
            if (mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, heldStack, targetBlock, targetFacing, mop.hitVec)) {
              
              if (silentSwing.getValue()) {
                mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
              } else {
                mc.thePlayer.swingItem();
              }

              lastPlaceTime = currentTime;

              targetQueue.remove(targetBlock.offset(targetFacing));
              targetBlock = null;
              targetFacing = null;
              targetHitVec = null;
              shouldPlaceNextTick = false;
            }
          }
        }
      }
    }
  }

  private void startSneaking() {
    if (!isSneakingSent) {
      mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
      isSneakingSent = true;
    }
  }

  private void stopSneaking() {
    if (isSneakingSent) {
      mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
      isSneakingSent = false;
    }
  }

  private void stopSneakingIfNeeded() {
    if (isSneakingSent) {
      stopSneaking();
    }
  }

  private void findBestPlacement() {
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    double reach = range.getValue().doubleValue();

    targetBlock = null;
    targetFacing = null;
    targetHitVec = null;

    targetQueue.sort(Comparator.comparingDouble(pos -> getEyeDistanceSq(pos)));

    for (BlockPos candidate : new ArrayList<>(targetQueue)) {
      if (getEyeDistanceSq(candidate) > reach * reach) continue;

      for (EnumFacing facing : EnumFacing.values()) {
        BlockPos support = candidate.offset(facing);

        if (!isSolid(support)) continue;

        EnumFacing placeFacing = facing.getOpposite();
        if (tryPlaceOnBlock(support, eye, reach, placeFacing)) {
          return;
        }
      }
    }
  }

  private boolean tryPlaceOnBlock(BlockPos supportBlock, Vec3 eye, double reach, EnumFacing facing) {
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

    return false;
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

  private void findBeds() {
    bedPositions.clear();
    BlockPos pPos = new BlockPos(mc.thePlayer);
    for (int x = -5; x <= 5; x++) {
      for (int y = -3; y <= 3; y++) {
        for (int z = -5; z <= 5; z++) {
          BlockPos pos = pPos.add(x, y, z);
          if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed) {
            bedPositions.add(pos);
          }
        }
      }
    }
  }

  private void buildTargetStructure() {
    targetQueue.clear();
    layer1Positions.clear();
    if (bedPositions.isEmpty()) return;

    Set<BlockPos> l1Set = new LinkedHashSet<>();
    Set<BlockPos> l2Set = new LinkedHashSet<>();

    if (layoutMode.getValue() == 2) {
      BlockPos bed1 = bedPositions.get(0);
      BlockPos bed2 = bedPositions.size() > 1 ? bedPositions.get(1) : bed1;

      EnumFacing bedFacing = EnumFacing.NORTH;
      if (bedPositions.size() > 1) {
        if (bed2.getX() > bed1.getX()) bedFacing = EnumFacing.EAST;
        else if (bed2.getX() < bed1.getX()) bedFacing = EnumFacing.WEST;
        else if (bed2.getZ() > bed1.getZ()) bedFacing = EnumFacing.SOUTH;
        else if (bed2.getZ() < bed1.getZ()) bedFacing = EnumFacing.NORTH;
      }

      EnumFacing left = bedFacing.rotateYCCW();
      EnumFacing right = bedFacing.rotateY();
      EnumFacing back = bedFacing.getOpposite();

      l1Set.add(bed1.offset(back));
      l1Set.add(bed2.offset(bedFacing));
      l1Set.add(bed1.offset(left));
      l1Set.add(bed1.offset(right));
      l1Set.add(bed2.offset(left));
      l1Set.add(bed2.offset(right));
      
      l1Set.add(bed1.offset(back).offset(left));
      l1Set.add(bed1.offset(back).offset(right));
      l1Set.add(bed2.offset(bedFacing).offset(left));
      l1Set.add(bed2.offset(bedFacing).offset(right));

      l2Set.add(bed1.offset(back, 2));
      l2Set.add(bed2.offset(bedFacing, 2));
      l2Set.add(bed1.offset(left, 2));
      l2Set.add(bed1.offset(right, 2));
      l2Set.add(bed2.offset(left, 2));
      l2Set.add(bed2.offset(right, 2));

      l1Set.add(bed1.up());
      l1Set.add(bed2.up());

      l1Set.add(bed1.offset(back).up());
      l1Set.add(bed2.offset(bedFacing).up());
      l1Set.add(bed1.offset(left).up());
      l1Set.add(bed1.offset(right).up());
      l1Set.add(bed2.offset(left).up());
      l1Set.add(bed2.offset(right).up());

      l1Set.add(bed1.offset(back).offset(left).up());
      l1Set.add(bed1.offset(back).offset(right).up());
      l1Set.add(bed2.offset(bedFacing).offset(left).up());
      l1Set.add(bed2.offset(bedFacing).offset(right).up());

      l2Set.add(bed1.up(2));
      l2Set.add(bed2.up(2));

      l1Set.removeAll(bedPositions);
      l2Set.removeAll(l1Set);
      l2Set.removeAll(bedPositions);

      layer1Positions.addAll(l1Set);

      List<BlockPos> allPositions = new ArrayList<>();
      allPositions.addAll(l1Set);
      allPositions.addAll(l2Set);

      allPositions.sort(Comparator.comparingInt(BlockPos::getY)
          .thenComparingDouble(pos -> getEyeDistanceSq(pos)));

      targetQueue.addAll(allPositions);

    } else {
      for (BlockPos b : bedPositions) {
        l1Set.add(b.up());
        for (EnumFacing f : EnumFacing.HORIZONTALS) {
          l1Set.add(b.offset(f));
        }
      }

      if (layoutMode.getValue() == 1) {
        for (BlockPos l1 : l1Set) {
          l2Set.add(l1.up());
          for (EnumFacing f : EnumFacing.HORIZONTALS) {
            l2Set.add(l1.offset(f));
          }
        }
        l2Set.removeAll(l1Set);
        l2Set.removeAll(bedPositions);
      }

      layer1Positions.addAll(l1Set);

      List<BlockPos> allPositions = new ArrayList<>();
      allPositions.addAll(l1Set);
      allPositions.addAll(l2Set);
      allPositions.sort(Comparator.comparingInt(BlockPos::getY));

      targetQueue.addAll(allPositions);
    }
  }

  private boolean isSolid(BlockPos pos) {
    Block b = mc.theWorld.getBlockState(pos).getBlock();
    return !b.isReplaceable(mc.theWorld, pos) && (b.isFullBlock() || b instanceof BlockBed || b.isOpaqueCube());
  }

  private boolean canReplace(BlockPos pos) {
    return mc.theWorld.getBlockState(pos).getBlock().isReplaceable(mc.theWorld, pos);
  }

  private Block getRequiredBlock(BlockPos pos) {
    Block primary = BLOCKS[block1Inside.getValue()];
    Block secondary = BLOCKS[block2Outside.getValue()];

    return layer1Positions.contains(pos) ? primary : secondary;
  }

  private int findBlockSlot(Block target) {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.getItem() instanceof ItemBlock) {
        if (((ItemBlock) stack.getItem()).getBlock() == target) {
          return i;
        }
      }
    }
    return -1;
  }

  private int findAnyBlockSlot() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.getItem() instanceof ItemBlock) {
        return i;
      }
    }
    return -1;
  }

  private double getEyeDistanceSq(BlockPos pos) {
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
    Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    return eye.squareDistanceTo(target);
  }

  @Override
  public String[] getSuffix() {
    return new String[]{
        BLOCK_NAMES[block1Inside.getValue()] + " + " + BLOCK_NAMES[block2Outside.getValue()],
        layoutMode.getModeString()
    };
  }
}