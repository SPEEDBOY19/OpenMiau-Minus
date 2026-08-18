package miau.module.modules.movement;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import miau.util.player.MoveUtil;
import miau.util.time.TimerUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.stats.StatList;
import net.minecraft.util.BlockPos;

public class Step extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty modeValue = new ModeProperty("Mode", 2, new String[] {"Vanilla", "Jump", "NCP", "MotionNCP", "OldNCP", "AAC", "LAAC", "AAC3.3.4", "Spartan", "Rewinside", "BlocksMCTimer", "Matrix"});
  public final FloatProperty height = new FloatProperty("Height", 1f, 0.6f, 10f, () -> !Step.usesStepHeight(this.modeValue.getModeString()));
  public final FloatProperty jumpHeight = new FloatProperty("JumpHeight", 0.42f, 0.37f, 0.42f, () -> this.modeValue.getModeString().equals("Jump"));
  public final IntProperty delay = new IntProperty("Delay", 0, 0, 500);
  public final BooleanProperty twoBlock = new BooleanProperty("2Block", true, () -> this.modeValue.getModeString().equals("Matrix"));
  public final BooleanProperty instant = new BooleanProperty("Instant", true, () -> this.modeValue.getModeString().equals("Matrix") && this.twoBlock.getValue());
  private boolean isStep = false;
  private double stepX = 0.0;
  private double stepY = 0.0;
  private double stepZ = 0.0;
  private int ncpNextStep = 0;
  private boolean spartanSwitch = false;
  private boolean isAACStep = false;
  private int matrixTicks = 0;
  private boolean matrixDoJump = false;
  private int timerPhase = 0;
  private final TimerUtil timer = new TimerUtil();

  public Step() {
    super("Step", false);
  }

  private static boolean usesStepHeight(String mode) {
    return mode.equals("Jump")
        || mode.equals("MotionNCP")
        || mode.equals("LAAC")
        || mode.equals("AAC3.3.4")
        || mode.equals("BlocksMCTimer")
        || mode.equals("Matrix");
  }

  private boolean isInLiquid() {
    return mc.thePlayer.isInsideOfMaterial(Material.water) || mc.thePlayer.isInsideOfMaterial(Material.lava);
  }

  private boolean isNearChest() {
    BlockPos pos = new BlockPos(mc.thePlayer);
    for (int x = -2; x <= 2; x++) {
      for (int y = -2; y <= 2; y++) {
        for (int z = -2; z <= 2; z++) {
          Block block = BlockUtil.getBlock(pos.add(x, y, z));
          if (block == Blocks.chest || block == Blocks.ender_chest || block == Blocks.trapped_chest) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void fakeJump() {
    mc.thePlayer.isAirBorne = true;
    mc.thePlayer.triggerAchievement(StatList.jumpStat);
  }

  private boolean couldStep() {
    if (mc.thePlayer == null) {
      return false;
    }
    if (mc.thePlayer.isSneaking() || mc.gameSettings.keyBindJump.isKeyDown()) {
      return false;
    }
    double yaw = MoveUtil.getMoveDirection();
    double heightOffset = 1.001335979112147;
    for (int i = -10; i <= 10; i++) {
      double adjustedYaw = yaw + i * Math.toRadians(8.0);
      double x = -Math.sin(adjustedYaw) * 0.2;
      double z = Math.cos(adjustedYaw) * 0.2;
      if (!mc.theWorld
          .getCollisionBoxes(mc.thePlayer.getEntityBoundingBox().offset(x, heightOffset, z))
          .isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private void sendStepPackets() {
    double x = mc.thePlayer.posX;
    double y = mc.thePlayer.posY;
    double z = mc.thePlayer.posZ;
    PacketUtil.sendPacket(new C04PacketPlayerPosition(x, y + 0.41999998688698, z, false));
    PacketUtil.sendPacket(new C04PacketPlayerPosition(x, y + 0.7531999805212, z, false));
    PacketUtil.sendPacket(new C04PacketPlayerPosition(x, y + 1.00133597911215, z, true));
    PacketUtil.sendPacket(new C04PacketPlayerPosition(x, y + 1.42133596599913, z, false));
    PacketUtil.sendPacket(new C04PacketPlayerPosition(x, y + 1.75453595963335, z, false));
    PacketUtil.sendPacket(new C04PacketPlayerPosition(x, y + 2.0026719582243, z, false));
  }

  private void stepConfirm() {
    if (mc.thePlayer == null || !this.isStep) {
      return;
    }
    if (mc.thePlayer.getEntityBoundingBox().minY - this.stepY > 0.6) {
      String mode = this.modeValue.getModeString();
      if (mode.equals("NCP") || mode.equals("AAC")) {
        this.fakeJump();
        PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 0.41999998688698, this.stepZ, false));
        PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 0.7531999805212, this.stepZ, false));
        this.timer.reset();
      } else if (mode.equals("Spartan")) {
        this.fakeJump();
        if (this.spartanSwitch) {
          PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 0.41999998688698, this.stepZ, false));
          PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 0.7531999805212, this.stepZ, false));
          PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 1.001335979112147, this.stepZ, false));
        } else {
          PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 0.6, this.stepZ, false));
        }
        this.spartanSwitch = !this.spartanSwitch;
        this.timer.reset();
      } else if (mode.equals("Rewinside")) {
        this.fakeJump();
        PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 0.41999998688698, this.stepZ, false));
        PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 0.7531999805212, this.stepZ, false));
        PacketUtil.sendPacket(new C04PacketPlayerPosition(this.stepX, this.stepY + 1.001335979112147, this.stepZ, false));
        this.timer.reset();
      }
    }
    this.isStep = false;
    this.stepX = 0.0;
    this.stepY = 0.0;
    this.stepZ = 0.0;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      String mode = this.modeValue.getModeString();
      if (mc.thePlayer.isOnLadder() || this.isInLiquid() || ((IAccessorEntity) mc.thePlayer).getIsInWeb()) {
        return;
      }
      if (!MoveUtil.isMoving()) {
        return;
      }
      if (mode.equals("Matrix")) {
        mc.thePlayer.stepHeight = this.twoBlock.getValue() ? 2.0f : 1.0f;
        if (this.matrixDoJump) {
          if ((this.matrixTicks > 0 && mc.thePlayer.onGround) || this.matrixTicks > 5) {
            this.matrixTicks = 0;
            this.matrixDoJump = false;
            return;
          }
          if (this.matrixTicks % 3 == 0) {
            mc.thePlayer.onGround = true;
            mc.thePlayer.jump();
          }
          this.matrixTicks++;
          return;
        }
        if (this.couldStep() && mc.thePlayer.onGround && mc.thePlayer.isCollidedHorizontally) {
          if (this.instant.getValue() && this.twoBlock.getValue()) {
            this.sendStepPackets();
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0f / 7f;
          } else if (this.twoBlock.getValue()) {
            this.matrixDoJump = true;
            this.matrixTicks = 0;
          } else {
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.33333f;
            PacketUtil.sendPacket(new C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.42f, mc.thePlayer.posZ, false));
            PacketUtil.sendPacket(new C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.42f, mc.thePlayer.posZ, true));
          }
        }
        return;
      }
      if (mode.equals("Jump")) {
        if (mc.thePlayer.isCollidedHorizontally
            && mc.thePlayer.onGround
            && !mc.gameSettings.keyBindJump.isKeyDown()) {
          this.fakeJump();
          mc.thePlayer.motionY = this.jumpHeight.getValue();
        }
      } else if (mode.equals("BlocksMCTimer")) {
        if (mc.thePlayer.onGround && mc.thePlayer.isCollidedHorizontally) {
          if (!this.couldStep() || this.isNearChest()) {
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
            this.timerPhase = 0;
            return;
          }
          this.fakeJump();
          mc.thePlayer.jump();
          switch (this.timerPhase) {
            case 0:
              ((IAccessorMinecraft) mc).getTimer().timerSpeed = 5f;
              this.timerPhase = 1;
              break;
            case 1:
              ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.2f;
              this.timerPhase = 2;
              break;
            case 2:
              ((IAccessorMinecraft) mc).getTimer().timerSpeed = 4f;
              this.timerPhase = 3;
              break;
            case 3:
              MoveUtil.strafe(0.27);
              ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1f;
              this.timerPhase = 0;
              break;
            default:
              break;
          }
        }
      } else if (mode.equals("LAAC")) {
        if (mc.thePlayer.isCollidedHorizontally) {
          if (mc.thePlayer.onGround && this.timer.hasTimeElapsed(this.delay.getValue())) {
            this.isStep = true;
            this.fakeJump();
            mc.thePlayer.motionY += 0.620000001490116;
            double yaw = MoveUtil.getMoveDirection();
            mc.thePlayer.motionX -= Math.sin(yaw) * 0.2;
            mc.thePlayer.motionZ += Math.cos(yaw) * 0.2;
            this.timer.reset();
          }
          mc.thePlayer.onGround = true;
        } else {
          this.isStep = false;
        }
      } else if (mode.equals("AAC3.3.4")) {
        if (mc.thePlayer.isCollidedHorizontally && MoveUtil.isMoving()) {
          if (mc.thePlayer.onGround && this.couldStep()) {
            mc.thePlayer.motionX *= 1.26;
            mc.thePlayer.motionZ *= 1.26;
            mc.thePlayer.jump();
            this.isAACStep = true;
          }
          if (this.isAACStep) {
            mc.thePlayer.motionY -= 0.015;
            if (!mc.thePlayer.isUsingItem() && mc.thePlayer.movementInput.moveStrafe == 0f) {
              mc.thePlayer.jumpMovementFactor = 0.3f;
            }
          }
        } else {
          this.isAACStep = false;
        }
      }
      if (mode.equals("MotionNCP")
          && mc.thePlayer.isCollidedHorizontally
          && !mc.gameSettings.keyBindJump.isKeyDown()) {
        if (mc.thePlayer.onGround && this.couldStep()) {
          this.fakeJump();
          mc.thePlayer.motionY = 0.41999998688698;
          this.ncpNextStep = 1;
        } else if (this.ncpNextStep == 1) {
          mc.thePlayer.motionY = 0.7531999805212 - 0.41999998688698;
          this.ncpNextStep = 2;
        } else if (this.ncpNextStep == 2) {
          double yaw = MoveUtil.getMoveDirection();
          mc.thePlayer.motionY = 1.001335979112147 - 0.7531999805212;
          mc.thePlayer.motionX = -Math.sin(yaw) * 0.7;
          mc.thePlayer.motionZ = Math.cos(yaw) * 0.7;
          this.ncpNextStep = 0;
        }
      }
      if (!Step.usesStepHeight(mode)) {
        if (Miau.moduleManager.modules.get(Fly.class).isEnabled()
            && mc.thePlayer.getHeldItem() == null) {
          mc.thePlayer.stepHeight = 0f;
        } else if (mc.thePlayer.onGround && this.timer.hasTimeElapsed(this.delay.getValue())) {
          float heightValue = this.height.getValue();
          mc.thePlayer.stepHeight = heightValue;
          if (heightValue > 0.6f) {
            this.isStep = true;
            this.stepX = mc.thePlayer.posX;
            this.stepY = mc.thePlayer.posY;
            this.stepZ = mc.thePlayer.posZ;
          }
        } else {
          mc.thePlayer.stepHeight = 0.6f;
        }
      }
    }
  }

  @EventTarget
  public void onUpdatePost(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.POST) {
      this.stepConfirm();
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled() && event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof C03PacketPlayer) {
        if (this.isStep && this.modeValue.getModeString().equals("OldNCP")) {
          mc.thePlayer.motionY += 0.07;
          this.isStep = false;
        }
      }
    }
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null) {
      mc.thePlayer.stepHeight = 0.6f;
    }
    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0f;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.modeValue.getModeString()};
  }
}