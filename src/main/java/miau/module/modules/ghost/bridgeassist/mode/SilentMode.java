package miau.module.modules.ghost.bridgeassist.mode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.ghost.BridgeAssist;
import miau.property.Property;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

public class SilentMode {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final BridgeAssist parent;
  private static final EnumFacing[] SIDES = {
    EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
  };

  public SilentMode(BridgeAssist parent) {
    this.parent = parent;
  }

  public List<Property<?>> getProperties() {
    return Arrays.asList();
  }

  public void onDisabled() {}

  public void onMoveInput(MoveInputEvent event) {}

  public void onUpdate(UpdateEvent e) {
    if (e.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return;

    ItemStack held = mc.thePlayer.getHeldItem();
    if (held == null || !(held.getItem() instanceof ItemBlock)) return;
    if (mc.thePlayer.rotationPitch < 70f) return;
    if (mc.thePlayer.movementInput.moveForward > 0f) return;

    float basePitch = RotationUtil.serverPitch;
    double reach = mc.playerController.getBlockReachDistance();

    TargetResult target = findTarget(basePitch, reach);
    if (target == null) return;

    float baseYaw = RotationUtil.serverYaw;
    float[] sm = RotationUtil.smoothRotation(baseYaw, basePitch, target.yaw, target.pitch, 15, 20f);

    e.setRotation(sm[0], sm[1], 2);
  }

  private TargetResult findTarget(float currentPitch, double reach) {
    float yaw = mc.thePlayer.rotationYaw;

    AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox();
    int standY = MathHelper.floor_double(bbox.minY) - 1;
    int minX = MathHelper.floor_double(bbox.minX);
    int maxX = MathHelper.floor_double(bbox.maxX);
    int minZ = MathHelper.floor_double(bbox.minZ);
    int maxZ = MathHelper.floor_double(bbox.maxZ);

    ArrayList<FaceTarget> targets = new ArrayList<>();
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        BlockPos standBlock = new BlockPos(x, standY, z);
        if (replaceable(standBlock)) continue;
        for (EnumFacing face : SIDES) {
          BlockPos placed = standBlock.offset(face);
          if (!replaceable(placed)) continue;
          targets.add(new FaceTarget(standBlock, face));
        }
      }
    }
    if (targets.isEmpty()) return null;

    float bestDelta = Float.MAX_VALUE;
    float bestPitch = Float.NaN;
    BlockPos bestSupport = null;
    EnumFacing bestFace = null;
    float randScale = 0.2f;

    for (float pitch = 60f; pitch <= 90f; ) {
      float step = 1.0f + (float) (Math.random() * 2 - 1) * (0.3f + randScale * 0.4f);
      if (step < 0.4f) step = 0.4f;
      if (step > 1.8f) step = 1.8f;
      pitch += step;
      float samplePitch = Math.min(pitch, 90f);
      MovingObjectPosition mop = RotationUtil.rayCastBlock(reach, yaw, samplePitch);
      if (mop == null) continue;
      EnumFacing hitFace = mop.sideHit;
      if (hitFace == EnumFacing.UP || hitFace == EnumFacing.DOWN) continue;

      BlockPos hitBlock = mop.getBlockPos();
      for (FaceTarget t : targets) {
        if (hitBlock.equals(t.block) && hitFace == t.face) {
          float delta = Math.abs(samplePitch - currentPitch);
          if (delta < bestDelta) {
            bestDelta = delta;
            bestPitch = samplePitch;
            bestSupport = t.block;
            bestFace = t.face;
          }
          break;
        }
      }
      if (pitch >= 90f) break;
    }

    if (bestSupport == null || bestFace == null || Float.isNaN(bestPitch)) return null;
    return new TargetResult(yaw, bestPitch, bestSupport, bestFace);
  }

  private boolean replaceable(BlockPos pos) {
    return mc.theWorld.getBlockState(pos).getBlock().isReplaceable(mc.theWorld, pos);
  }

  private static class FaceTarget {
    final BlockPos block;
    final EnumFacing face;

    FaceTarget(BlockPos block, EnumFacing face) {
      this.block = block;
      this.face = face;
    }
  }

  private static class TargetResult {
    final float yaw, pitch;
    final BlockPos support;
    final EnumFacing face;

    TargetResult(float yaw, float pitch, BlockPos support, EnumFacing face) {
      this.yaw = yaw;
      this.pitch = pitch;
      this.support = support;
      this.face = face;
    }
  }
}
