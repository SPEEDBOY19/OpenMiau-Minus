package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.JumpEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class ReverseStep extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final FloatProperty motion = new FloatProperty("Motion", 1f, 0.21f, 4f);

  private boolean jumped = false;

  public ReverseStep() {
    super("ReverseStep", false);
  }

  private boolean collideBlock(AxisAlignedBB axisAlignedBB) {
    int y = MathHelper.floor_double(axisAlignedBB.minY);
    int startX = MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minX);
    int endX = MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().maxX) + 1;
    int startZ = MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minZ);
    int endZ = MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().maxZ) + 1;
    for (int x = startX; x < endX; x++) {
      for (int z = startZ; z < endZ; z++) {
        Block block = mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
        if (!(block instanceof BlockLiquid)) {
          return false;
        }
      }
    }
    return true;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.thePlayer.onGround) {
      this.jumped = false;
    }

    if (mc.thePlayer.motionY > 0) {
      this.jumped = true;
    }

    if (this.collideBlock(mc.thePlayer.getEntityBoundingBox()) || this.collideBlock(AxisAlignedBB.fromBounds(mc.thePlayer.getEntityBoundingBox().maxX, mc.thePlayer.getEntityBoundingBox().maxY, mc.thePlayer.getEntityBoundingBox().maxZ, mc.thePlayer.getEntityBoundingBox().minX, mc.thePlayer.getEntityBoundingBox().minY - 0.01, mc.thePlayer.getEntityBoundingBox().minZ))) {
      return;
    }

    if (!mc.gameSettings.keyBindJump.isKeyDown() && !mc.thePlayer.onGround && !mc.thePlayer.movementInput.jump && mc.thePlayer.motionY <= 0.0 && mc.thePlayer.fallDistance <= 1f && !this.jumped) {
      mc.thePlayer.motionY = -this.motion.getValue();
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    this.jumped = true;
  }
}