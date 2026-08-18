package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class WallClimb extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty modeValue = new ModeProperty("Mode", 0, new String[] {"Simple", "CheckerClimb", "Clip", "AAC3.3.12", "AACGlide", "Matrix", "Polar"});
  public final ModeProperty clipMode = new ModeProperty("ClipMode", 1, new String[] {"Jump", "Fast"}, () -> this.modeValue.getModeString().equals("Clip"));
  public final FloatProperty checkerClimbMotion = new FloatProperty("CheckerClimbMotion", 0f, 0f, 1f, () -> this.modeValue.getModeString().equals("CheckerClimb"));
  private boolean glitch = false;
  private int waited = 0;
  private int airTicks = 0;

  public WallClimb() {
    super("WallClimb", false);
  }

  private boolean isInLiquid() {
    return mc.thePlayer.isInsideOfMaterial(Material.water) || mc.thePlayer.isInsideOfMaterial(Material.lava);
  }

  private boolean collideBlockIntersects(AxisAlignedBB bb) {
    int minX = MathHelper.floor_double(bb.minX);
    int maxX = MathHelper.floor_double(bb.maxX + 1.0);
    int minY = MathHelper.floor_double(bb.minY);
    int maxY = MathHelper.floor_double(bb.maxY + 1.0);
    int minZ = MathHelper.floor_double(bb.minZ);
    int maxZ = MathHelper.floor_double(bb.maxZ + 1.0);
    for (int x = minX; x < maxX; x++) {
      for (int y = minY; y < maxY; y++) {
        for (int z = minZ; z < maxZ; z++) {
          if (BlockUtil.getBlock(new BlockPos(x, y, z)) != Blocks.air) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @EventTarget
  public void onMove(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      if (!mc.thePlayer.isCollidedHorizontally || mc.thePlayer.isOnLadder() || this.isInLiquid()) {
        return;
      }
      if (this.modeValue.getModeString().equals("Simple")) {
        mc.thePlayer.motionY = 0.2;
      }
    }
  }

  @EventTarget
  public void onMotion(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.POST) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      if (mc.thePlayer.onGround) {
        this.airTicks = 0;
      } else {
        this.airTicks++;
      }
      String mode = this.modeValue.getModeString().toLowerCase();
      if (mode.equals("clip")) {
        if (mc.thePlayer.motionY < 0) {
          this.glitch = true;
        }
        if (mc.thePlayer.isCollidedHorizontally) {
          if (this.clipMode.getModeString().equalsIgnoreCase("jump")) {
            if (mc.thePlayer.onGround) {
              mc.thePlayer.jump();
            }
          } else {
            if (mc.thePlayer.onGround) {
              mc.thePlayer.motionY = 0.42;
            } else if (mc.thePlayer.motionY < 0) {
              mc.thePlayer.motionY = -0.3;
            }
          }
        }
      } else if (mode.equals("matrix")) {
        if (mc.thePlayer.motionY < 0) {
          this.glitch = true;
        }
        if (mc.thePlayer.isCollidedHorizontally) {
          if (mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.42;
          } else if (mc.thePlayer.motionY < 0 && this.airTicks >= 2) {
            mc.thePlayer.motionY = -0.3;
          }
        }
      } else if (mode.equals("checkerclimb")) {
        boolean isInsideBlock = this.collideBlockIntersects(mc.thePlayer.getEntityBoundingBox());
        float motion = this.checkerClimbMotion.getValue();
        if (isInsideBlock && motion != 0f) {
          mc.thePlayer.motionY = motion;
        }
      } else if (mode.equals("aac3.3.12")) {
        if (mc.thePlayer.isCollidedHorizontally && !mc.thePlayer.isOnLadder()) {
          this.waited++;
          if (this.waited == 1) {
            mc.thePlayer.motionY = 0.43;
          }
          if (this.waited == 12) {
            mc.thePlayer.motionY = 0.43;
          }
          if (this.waited == 23) {
            mc.thePlayer.motionY = 0.43;
          }
          if (this.waited == 29) {
            mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.5, mc.thePlayer.posZ);
          }
          if (this.waited >= 30) {
            this.waited = 0;
          }
        } else if (mc.thePlayer.onGround) {
          this.waited = 0;
        }
      } else if (mode.equals("aacglide")) {
        if (!mc.thePlayer.isCollidedHorizontally || mc.thePlayer.isOnLadder()) {
          return;
        }
        mc.thePlayer.motionY = -0.19;
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled() && event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof C03PacketPlayer) {
        if (this.glitch) {
          double yaw = MoveUtil.getMoveDirection();
          mc.thePlayer.motionX -= Math.sin(yaw) * 0.00000001;
          mc.thePlayer.motionZ += Math.cos(yaw) * 0.00000001;
          this.glitch = false;
        }
      }
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.modeValue.getModeString()};
  }
}