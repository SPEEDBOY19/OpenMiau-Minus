package miau.module.modules.movement;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntityPlayer;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockSlime;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

public class BufferSpeed extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty speedLimit = new BooleanProperty("SpeedLimit", true);
  public final FloatProperty maxSpeed = new FloatProperty("MaxSpeed", 2f, 1f, 5f, () -> this.speedLimit.getValue());
  public final BooleanProperty buffer = new BooleanProperty("Buffer", true);
  public final BooleanProperty stairs = new BooleanProperty("Stairs", true);
  public final ModeProperty stairsMode = new ModeProperty("StairsMode", 1, new String[] {"Old", "New"}, () -> this.stairs.getValue());
  public final FloatProperty stairsBoost = new FloatProperty("StairsBoost", 1.87f, 1f, 2f, () -> this.stairs.getValue() && this.stairsMode.getModeString().equals("Old"));
  public final BooleanProperty slabs = new BooleanProperty("Slabs", true);
  public final ModeProperty slabsMode = new ModeProperty("SlabsMode", 1, new String[] {"Old", "New"}, () -> this.slabs.getValue());
  public final FloatProperty slabsBoost = new FloatProperty("SlabsBoost", 1.87f, 1f, 2f, () -> this.slabs.getValue() && this.slabsMode.getModeString().equals("Old"));
  public final BooleanProperty ice = new BooleanProperty("Ice", false);
  public final FloatProperty iceBoost = new FloatProperty("IceBoost", 1.342f, 1f, 2f, () -> this.ice.getValue());
  public final BooleanProperty snow = new BooleanProperty("Snow", true);
  public final FloatProperty snowBoost = new FloatProperty("SnowBoost", 1.87f, 1f, 2f, () -> this.snow.getValue());
  public final BooleanProperty snowPort = new BooleanProperty("SnowPort", true, () -> this.snow.getValue());
  public final BooleanProperty wall = new BooleanProperty("Wall", true);
  public final ModeProperty wallMode = new ModeProperty("WallMode", 1, new String[] {"Old", "New"}, () -> this.wall.getValue());
  public final FloatProperty wallBoost = new FloatProperty("WallBoost", 1.87f, 1f, 2f, () -> this.wall.getValue() && this.wallMode.getModeString().equals("Old"));
  public final BooleanProperty headBlock = new BooleanProperty("HeadBlock", true);
  public final FloatProperty headBlockBoost = new FloatProperty("HeadBlockBoost", 1.87f, 1f, 2f, () -> this.headBlock.getValue());
  public final BooleanProperty slime = new BooleanProperty("Slime", true);
  public final BooleanProperty airStrafe = new BooleanProperty("AirStrafe", false);
  public final BooleanProperty noHurt = new BooleanProperty("NoHurt", true);
  private double speed = 0.0;
  private boolean down = false;
  private boolean forceDown = false;
  private boolean fastHop = false;
  private boolean hadFastHop = false;
  private boolean legitHop = false;

  public BufferSpeed() {
    super("BufferSpeed", false);
  }

  private void reset() {
    if (mc.thePlayer == null) {
      return;
    }
    this.legitHop = true;
    this.speed = 0.0;
    if (this.hadFastHop) {
      ((IAccessorEntityPlayer) mc.thePlayer).setSpeedInAir(0.02f);
      this.hadFastHop = false;
    }
  }

  private void boost(float boost) {
    mc.thePlayer.motionX *= boost;
    mc.thePlayer.motionZ *= boost;
    this.speed = MoveUtil.getSpeed();
    if (this.speedLimit.getValue() && this.speed > this.maxSpeed.getValue()) {
      this.speed = this.maxSpeed.getValue();
    }
  }

  private boolean isNearBlock() {
    BlockPos[] blocks = {
      new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1, mc.thePlayer.posZ - 0.7),
      new BlockPos(mc.thePlayer.posX + 0.7, mc.thePlayer.posY + 1, mc.thePlayer.posZ),
      new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1, mc.thePlayer.posZ + 0.7),
      new BlockPos(mc.thePlayer.posX - 0.7, mc.thePlayer.posY + 1, mc.thePlayer.posZ)
    };
    for (BlockPos blockPos : blocks) {
      IBlockState blockState = mc.theWorld.getBlockState(blockPos);
      AxisAlignedBB collisionBoundingBox =
          blockState.getBlock().getCollisionBoundingBox(mc.theWorld, blockPos, blockState);
      if (((collisionBoundingBox == null || collisionBoundingBox.maxX == collisionBoundingBox.minY + 1)
              && !blockState.getBlock().isTranslucent()
              && blockState.getBlock() == Blocks.water
              && !(blockState.getBlock() instanceof BlockSlab))
          || blockState.getBlock() == Blocks.barrier) {
        return true;
      }
    }
    return false;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      if (Miau.moduleManager.modules.get(Speed.class).isEnabled()
          || (this.noHurt.getValue() && mc.thePlayer.hurtTime > 0)) {
        this.reset();
        return;
      }
      BlockPos blockPos = new BlockPos(mc.thePlayer);
      if (this.forceDown || (this.down && mc.thePlayer.motionY == 0.0)) {
        mc.thePlayer.motionY = -1.0;
        this.down = false;
        this.forceDown = false;
      }
      if (this.fastHop) {
        ((IAccessorEntityPlayer) mc.thePlayer).setSpeedInAir(0.0211f);
        this.hadFastHop = true;
      } else if (this.hadFastHop) {
        ((IAccessorEntityPlayer) mc.thePlayer).setSpeedInAir(0.02f);
        this.hadFastHop = false;
      }
      if (!MoveUtil.isMoving()
          || mc.thePlayer.isSneaking()
          || mc.thePlayer.isInWater()
          || mc.gameSettings.keyBindJump.isKeyDown()) {
        this.reset();
        return;
      }
      if (mc.thePlayer.onGround) {
        this.fastHop = false;
        if (this.slime.getValue()
            && (BlockUtil.getBlock(blockPos.down()) instanceof BlockSlime
                || BlockUtil.getBlock(blockPos) instanceof BlockSlime)) {
          mc.thePlayer.jump();
          mc.thePlayer.motionX = mc.thePlayer.motionY * 1.132;
          mc.thePlayer.motionY = 0.08;
          mc.thePlayer.motionZ = mc.thePlayer.motionY * 1.132;
          this.down = true;
          return;
        }
        if (this.slabs.getValue() && BlockUtil.getBlock(blockPos) instanceof BlockSlab) {
          if (this.slabsMode.getModeString().equalsIgnoreCase("old")) {
            this.boost(this.slabsBoost.getValue());
            return;
          } else {
            this.fastHop = true;
            if (this.legitHop) {
              mc.thePlayer.jump();
              mc.thePlayer.onGround = false;
              this.legitHop = false;
              return;
            }
            mc.thePlayer.onGround = false;
            MoveUtil.strafe(0.375);
            mc.thePlayer.jump();
            mc.thePlayer.motionY = 0.41;
            return;
          }
        }
        if (this.stairs.getValue()
            && (BlockUtil.getBlock(blockPos.down()) instanceof BlockStairs
                || BlockUtil.getBlock(blockPos) instanceof BlockStairs)) {
          if (this.stairsMode.getModeString().equalsIgnoreCase("old")) {
            this.boost(this.stairsBoost.getValue());
            return;
          } else {
            this.fastHop = true;
            if (this.legitHop) {
              mc.thePlayer.jump();
              mc.thePlayer.onGround = false;
              this.legitHop = false;
              return;
            }
            mc.thePlayer.onGround = false;
            MoveUtil.strafe(0.375);
            mc.thePlayer.jump();
            mc.thePlayer.motionY = 0.41;
            return;
          }
        }
        this.legitHop = true;
        if (this.headBlock.getValue() && BlockUtil.getBlock(blockPos.up(2)) != Blocks.air) {
          this.boost(this.headBlockBoost.getValue());
          return;
        }
        if (this.ice.getValue()
            && (BlockUtil.getBlock(blockPos.down()) == Blocks.ice
                || BlockUtil.getBlock(blockPos.down()) == Blocks.packed_ice)) {
          this.boost(this.iceBoost.getValue());
          return;
        }
        if (this.snow.getValue()
            && BlockUtil.getBlock(blockPos) == Blocks.snow_layer
            && (this.snowPort.getValue()
                || mc.thePlayer.posY - (int) mc.thePlayer.posY >= 0.12500)) {
          if (mc.thePlayer.posY - (int) mc.thePlayer.posY >= 0.12500) {
            this.boost(this.snowBoost.getValue());
          } else {
            mc.thePlayer.jump();
            this.forceDown = true;
          }
          return;
        }
        if (this.wall.getValue()) {
          if (this.wallMode.getModeString().equalsIgnoreCase("old")) {
            if ((mc.thePlayer.isCollidedVertically && this.isNearBlock())
                || BlockUtil.getBlock(new BlockPos(mc.thePlayer).up(2)) != Blocks.air) {
              this.boost(this.wallBoost.getValue());
              return;
            }
          } else {
            if (this.isNearBlock() && !mc.thePlayer.movementInput.jump) {
              mc.thePlayer.jump();
              mc.thePlayer.motionY = 0.08;
              mc.thePlayer.motionX *= 0.99;
              mc.thePlayer.motionZ *= 0.99;
              this.down = true;
              return;
            }
          }
        }
        if (this.buffer.getValue() && this.speed > 0.2) {
          this.speed /= 1.0199999809265137;
          MoveUtil.strafe();
        }
      } else {
        this.speed = 0.0;
        if (this.airStrafe.getValue()) {
          MoveUtil.strafe();
        }
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled() && event.getType() == EventType.RECEIVE) {
      if (event.getPacket() instanceof S08PacketPlayerPosLook) {
        this.speed = 0.0;
      }
    }
  }

  @Override
  public void onEnabled() {
    this.reset();
  }

  @Override
  public void onDisabled() {
    this.reset();
  }
}