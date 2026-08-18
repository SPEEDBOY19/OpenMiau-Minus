package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import miau.util.player.MoveUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockVine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;

public class FastClimb extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty modeValue = new ModeProperty("Mode", 0, new String[] {"Vanilla", "Delay", "Clip", "AAC3.0.0", "AAC3.0.5", "SAAC3.1.2", "AAC3.1.2", "GrizzlyLatest"});
  public final FloatProperty speed = new FloatProperty("Speed", 1f, 0.01f, 5f, () -> this.modeValue.getModeString().equals("Vanilla"));
  public final FloatProperty climbSpeed = new FloatProperty("ClimbSpeed", 1f, 0.01f, 5f, () -> this.modeValue.getModeString().equals("Delay"));
  public final IntProperty tickDelay = new IntProperty("TickDelay", 10, 1, 20, () -> this.modeValue.getModeString().equals("Delay"));
  private final int climbDelay = this.tickDelay.getValue();
  private int climbCount = 0;

  public FastClimb() {
    super("FastClimb", false);
  }

  private void playerClimb() {
    mc.thePlayer.motionY = 0.0;
    ((IAccessorEntity) mc.thePlayer).setIsInWeb(true);
    mc.thePlayer.onGround = true;
    ((IAccessorEntity) mc.thePlayer).setIsInWeb(false);
  }

  private boolean intersectLadderOrVine(AxisAlignedBB bb) {
    int minX = MathHelper.floor_double(bb.minX);
    int maxX = MathHelper.floor_double(bb.maxX + 1.0);
    int minY = MathHelper.floor_double(bb.minY);
    int maxY = MathHelper.floor_double(bb.maxY + 1.0);
    int minZ = MathHelper.floor_double(bb.minZ);
    int maxZ = MathHelper.floor_double(bb.maxZ + 1.0);
    for (int x = minX; x < maxX; x++) {
      for (int y = minY; y < maxY; y++) {
        for (int z = minZ; z < maxZ; z++) {
          Block block = BlockUtil.getBlock(new BlockPos(x, y, z));
          if (block instanceof BlockLadder || block instanceof BlockVine) {
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
      if (this.modeValue.getModeString().equals("GrizzlyLatest")
          && mc.thePlayer.isCollidedHorizontally
          && mc.thePlayer.isOnLadder()) {
        mc.thePlayer.motionY = 0.19;
        if (mc.thePlayer.ticksExisted % 2 == 1) {
          double yaw = MoveUtil.getMoveDirection();
          mc.thePlayer.motionX = 0.0;
          mc.thePlayer.motionZ = 0.0;
          mc.thePlayer.motionX += MathHelper.sin((float) yaw) * 0.15f;
          mc.thePlayer.motionZ -= MathHelper.cos((float) yaw) * 0.15f;
        }
      }
    }
  }

  @EventTarget
  public void onMove(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.POST) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      String mode = this.modeValue.getModeString();
      if (mode.equals("Vanilla") && mc.thePlayer.isCollidedHorizontally && mc.thePlayer.isOnLadder()) {
        mc.thePlayer.motionY = this.speed.getValue();
      } else if (mode.equals("Delay")
          && mc.thePlayer.isCollidedHorizontally
          && mc.thePlayer.isOnLadder()) {
        if (this.climbCount >= this.climbDelay) {
          this.playerClimb();
          mc.thePlayer.motionY = this.climbSpeed.getValue();
          PacketUtil.sendPacket(
              new C04PacketPlayerPosition(
                  mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, true));
          this.climbCount = 0;
        } else {
          mc.thePlayer.posY = mc.thePlayer.prevPosY;
          this.playerClimb();
          this.climbCount++;
        }
      } else if (mode.equals("AAC3.0.0") && mc.thePlayer.isCollidedHorizontally) {
        double x = 0.0;
        double z = 0.0;
        switch (mc.thePlayer.getHorizontalFacing()) {
          case NORTH:
            z = -0.99;
            break;
          case EAST:
            x = 0.99;
            break;
          case SOUTH:
            z = 0.99;
            break;
          case WEST:
            x = -0.99;
            break;
          default:
            break;
        }
        Block block =
            BlockUtil.getBlock(new BlockPos(mc.thePlayer.posX + x, mc.thePlayer.posY, mc.thePlayer.posZ + z));
        if (block instanceof BlockLadder || block instanceof BlockVine) {
          mc.thePlayer.motionY = 0.5;
        }
      } else if (mode.equals("AAC3.0.5")
          && mc.gameSettings.keyBindForward.isKeyDown()
          && this.intersectLadderOrVine(mc.thePlayer.getEntityBoundingBox())) {
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.5;
        mc.thePlayer.motionZ = 0.0;
      } else if (mode.equals("SAAC3.1.2")
          && mc.thePlayer.isCollidedHorizontally
          && mc.thePlayer.isOnLadder()) {
        mc.thePlayer.motionY = 0.1649;
      } else if (mode.equals("AAC3.1.2")
          && mc.thePlayer.isCollidedHorizontally
          && mc.thePlayer.isOnLadder()) {
        mc.thePlayer.motionY = 0.1699;
      } else if (mode.equals("Clip")
          && mc.thePlayer.isOnLadder()
          && mc.gameSettings.keyBindForward.isKeyDown()) {
        for (int i = (int) mc.thePlayer.posY; i <= (int) mc.thePlayer.posY + 8; i++) {
          Block block = BlockUtil.getBlock(new BlockPos(mc.thePlayer.posX, i, mc.thePlayer.posZ));
          if (!(block instanceof BlockLadder)) {
            double x = 0.0;
            double z = 0.0;
            switch (mc.thePlayer.getHorizontalFacing()) {
              case NORTH:
                z = -1.0;
                break;
              case EAST:
                x = 1.0;
                break;
              case SOUTH:
                z = 1.0;
                break;
              case WEST:
                x = -1.0;
                break;
              default:
                break;
            }
            mc.thePlayer.setPosition(mc.thePlayer.posX + x, i, mc.thePlayer.posZ + z);
            break;
          } else {
            mc.thePlayer.setPosition(mc.thePlayer.posX, i, mc.thePlayer.posZ);
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