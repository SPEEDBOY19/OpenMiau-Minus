package miau.module.modules.movement.nofalls;

import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoFall;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class LegitNoFall extends NoFallMode {
  private int lastMlgSlot = -1;
  private boolean mlgPlaced = false;

  public LegitNoFall(String name, NoFall parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
      this.handleLegitMlg();
    }
  }

  private void handleLegitMlg() {
    if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;
    if (mc.thePlayer.onGround
        || mc.thePlayer.capabilities.isFlying
        || mc.thePlayer.isInWater()
        || mc.thePlayer.isOnLadder()) {
      this.resetLegitMlg();
      return;
    }
    if (mc.thePlayer.fallDistance < parent.distance.getValue() || mc.thePlayer.motionY >= -0.1D)
      return;

    int waterSlot = this.findWaterBucketSlot();
    if (waterSlot == -1) return;
    BlockPos target = this.findMlgTarget();
    if (target == null) return;

    if (this.lastMlgSlot == -1) {
      this.lastMlgSlot = mc.thePlayer.inventory.currentItem;
    }
    mc.thePlayer.inventory.currentItem = waterSlot;
    mc.playerController.updateController();
    mc.thePlayer.rotationPitch = 90.0F;

    if (!this.mlgPlaced
        && mc.thePlayer.getDistance(
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D)
            <= mc.playerController.getBlockReachDistance() + 1.5F) {
      Vec3 hitVec = new Vec3(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
      ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
      if (stack != null
          && mc.playerController.onPlayerRightClick(
              mc.thePlayer, mc.theWorld, stack, target, EnumFacing.UP, hitVec)) {
        mc.thePlayer.swingItem();
        this.mlgPlaced = true;
      }
    }
  }

  private int findWaterBucketSlot() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
      if (stack != null && stack.getItem() == Items.water_bucket) {
        return i;
      }
    }
    return -1;
  }

  private BlockPos findMlgTarget() {
    BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    for (int y = 1; y <= 6; y++) {
      BlockPos pos = playerPos.down(y);
      if (!mc.theWorld.isAirBlock(pos) && mc.theWorld.isAirBlock(pos.up())) {
        return pos;
      }
    }
    MovingObjectPosition ray =
        mc.theWorld.rayTraceBlocks(
            new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ),
            new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY - mc.playerController.getBlockReachDistance() - 2.0D,
                mc.thePlayer.posZ),
            false,
            true,
            false);
    return ray != null && ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        ? ray.getBlockPos()
        : null;
  }

  private void resetLegitMlg() {
    if (this.lastMlgSlot != -1 && mc.thePlayer != null) {
      mc.thePlayer.inventory.currentItem = this.lastMlgSlot;
      mc.playerController.updateController();
    }
    this.lastMlgSlot = -1;
    this.mlgPlaced = false;
  }

  @Override
  public void onDisable() {
    this.resetLegitMlg();
  }
}
