package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class LiquidWalk extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty modeValue = new ModeProperty("Mode", 1, new String[] {"Vanilla", "NCP", "AAC", "AAC3.3.11", "AACFly", "Spartan", "Dolphin", "TatakoLatest", "VulcanA", "VulcanB"});
  public final FloatProperty aacFly = new FloatProperty("AACFlyMotion", 0.5f, 0.1f, 1.0f, () -> this.modeValue.getModeString().equals("AACFly"));
  public final BooleanProperty noJump = new BooleanProperty("NoJump", false);
  private boolean nextTick = false;
  private boolean wasInWater = false;

  public LiquidWalk() {
    super("LiquidWalk", false);
  }

  private boolean collideLiquid(AxisAlignedBB bb) {
    int minX = MathHelper.floor_double(bb.minX);
    int maxX = MathHelper.floor_double(bb.maxX + 1.0);
    int minY = MathHelper.floor_double(bb.minY);
    int maxY = MathHelper.floor_double(bb.maxY + 1.0);
    int minZ = MathHelper.floor_double(bb.minZ);
    int maxZ = MathHelper.floor_double(bb.maxZ + 1.0);
    for (int x = minX; x < maxX; x++) {
      for (int y = minY; y < maxY; y++) {
        for (int z = minZ; z < maxZ; z++) {
          if (BlockUtil.getBlock(new BlockPos(x, y, z)) instanceof BlockLiquid) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @EventTarget
  public void onMotion(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      String mode = this.modeValue.getModeString();
      if (mc.thePlayer.isInWater()) {
        if (mode.equals("TatakoLatest")) {
          mc.thePlayer.motionY += 0.13;
        } else if (mode.equals("VulcanA")) {
          mc.thePlayer.motionY = 0.5;
          MoveUtil.strafe(0.36);
        } else if (mode.equals("VulcanB")) {
          MoveUtil.strafe(0.3f - (float) (Math.random() / 1000.0));
          mc.thePlayer.motionY = 0.5;
          this.wasInWater = true;
        } else if (mode.equals("AACFly")) {
          mc.thePlayer.motionY = this.aacFly.getValue();
        }
      }
      if (!mc.thePlayer.isInWater() && this.wasInWater && mode.equals("VulcanB")) {
        mc.thePlayer.motionY = -1.0;
      }
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.POST) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      if (mc.thePlayer.isSneaking()) return;
      String mode = this.modeValue.getModeString();
      if (mode.equals("NCP") || mode.equals("Vanilla")) {
        if (this.collideLiquid(mc.thePlayer.getEntityBoundingBox())
            && mc.thePlayer.isInsideOfMaterial(Material.air)) {
          mc.thePlayer.motionY = 0.08;
        }
      } else if (mode.equals("AAC")) {
        BlockPos blockPos = mc.thePlayer.getPosition().down();
        if ((!mc.thePlayer.onGround && BlockUtil.getBlock(blockPos) == Blocks.water)
            || mc.thePlayer.isInWater()) {
          mc.thePlayer.motionX *= 0.99999;
          mc.thePlayer.motionY *= 0.0;
          mc.thePlayer.motionZ *= 0.99999;
          if (mc.thePlayer.isCollidedHorizontally) {
            int trunc = (int) (mc.thePlayer.posY - 1.0);
            mc.thePlayer.motionY = (double) (((int) (mc.thePlayer.posY - trunc)) / 8.0f);
          }
          if (mc.thePlayer.fallDistance >= 4) {
            mc.thePlayer.motionY = -0.004;
          } else if (mc.thePlayer.isInWater()) {
            mc.thePlayer.motionY = 0.09;
          }
        }
        if (mc.thePlayer.hurtTime != 0) {
          mc.thePlayer.onGround = false;
        }
      } else if (mode.equals("Spartan")) {
        if (mc.thePlayer.isInWater()) {
          if (mc.thePlayer.isCollidedHorizontally) {
            mc.thePlayer.motionY += 0.15;
            return;
          }
          Block block = BlockUtil.getBlock(new BlockPos(mc.thePlayer).up());
          Block blockUp = BlockUtil.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1.1, mc.thePlayer.posZ));
          if (blockUp instanceof BlockLiquid) {
            mc.thePlayer.motionY = 0.1;
          } else if (block instanceof BlockLiquid) {
            mc.thePlayer.motionY = 0.0;
          }
          mc.thePlayer.onGround = true;
          mc.thePlayer.motionX *= 1.085;
          mc.thePlayer.motionZ *= 1.085;
        }
      } else if (mode.equals("AAC3.3.11")) {
        if (mc.thePlayer.isInWater()) {
          mc.thePlayer.motionX *= 1.17;
          mc.thePlayer.motionZ *= 1.17;
          if (mc.thePlayer.isCollidedHorizontally) {
            mc.thePlayer.motionY = 0.24;
          } else if (BlockUtil.getBlock(new BlockPos(mc.thePlayer).up()) != Blocks.air) {
            mc.thePlayer.motionY += 0.04;
          }
        }
      } else if (mode.equals("Dolphin")) {
        if (mc.thePlayer.isInWater()) {
          mc.thePlayer.motionY += 0.03999999910593033;
        }
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled() && event.getType() == EventType.SEND) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      if (!this.modeValue.getModeString().equals("NCP")) return;
      if (event.getPacket() instanceof C03PacketPlayer) {
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox();
        if (this.collideLiquid(
            AxisAlignedBB.fromBounds(bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.minY - 0.01, bb.minZ))) {
          this.nextTick = !this.nextTick;
          if (this.nextTick) {
            mc.thePlayer.motionY -= 0.001;
          }
        }
      }
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.modeValue.getModeString()};
  }
}