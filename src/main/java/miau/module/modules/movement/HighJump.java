package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.JumpEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.PlayerUpdateEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.BlockPane;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;

public class HighJump extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Vanilla", "FairFight0.6.0", "Damage", "AACv3", "DAC", "Mineplex", "Matrix"});
  public final FloatProperty height = new FloatProperty("Height", 2f, 1.1f, 5f, () -> this.mode.getModeString().equals("Vanilla") || this.mode.getModeString().equals("Damage"));
  public final FloatProperty matrixMotionY = new FloatProperty("Matrix-MotionY", 0.998f, 0.42f, 2f, () -> this.mode.getModeString().equalsIgnoreCase("Matrix"));
  public final IntProperty matrixTicks = new IntProperty("Matrix-Ticks", 4, 1, 20, () -> this.mode.getModeString().equalsIgnoreCase("Matrix"));
  public final BooleanProperty glass = new BooleanProperty("OnlyGlassPane", false);

  private boolean active = false;
  private boolean falling = false;
  private boolean moving = false;
  private int ticksSinceJump = 0;

  public HighJump() {
    super("HighJump", false);
  }

  @Override
  public void onEnabled() {
    if (this.mode.getModeString().equalsIgnoreCase("Matrix")) {
      this.ticksSinceJump = 0;
      this.falling = false;
      this.active = false;
      this.moving = MoveUtil.isMoving();
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (this.glass.getValue() && !(BlockUtil.getBlock(new BlockPos(mc.thePlayer)) instanceof BlockPane)) {
      return;
    }

    String mode = this.mode.getModeString().toLowerCase();

    if (mode.equals("damage")) {
      if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.onGround) {
        mc.thePlayer.motionY += 0.42f * this.height.getValue();
      }
    } else if (mode.equals("aacv3")) {
      if (!mc.thePlayer.onGround) mc.thePlayer.motionY += 0.059;
    } else if (mode.equals("dac")) {
      if (!mc.thePlayer.onGround) mc.thePlayer.motionY += 0.049999;
    } else if (mode.equals("mineplex")) {
      if (!mc.thePlayer.onGround) MoveUtil.strafe(0.35);
    } else if (mode.equals("matrix")) {
      if (!this.moving) {
        MoveUtil.strafe(0.16);
        this.moving = true;
      }

      if (mc.thePlayer.isCollidedVertically) {
        this.active = true;
      }

      if (this.ticksSinceJump == 1) {
        mc.thePlayer.motionY = this.matrixMotionY.getValue();
      }

      if (mc.thePlayer.isCollidedVertically && this.ticksSinceJump > this.matrixTicks.getValue()) {
        this.setEnabled(false);
      }

      if (!mc.thePlayer.onGround && this.ticksSinceJump >= 2) {
        mc.thePlayer.motionY += 0.0034999;
        if (!this.falling && mc.thePlayer.motionY < 0.0 && mc.thePlayer.motionY > -0.05) {
          mc.thePlayer.motionY = 0.0029999;
          this.falling = true;
          this.setEnabled(false);
        }
      }

      if (this.active) {
        this.ticksSinceJump++;
      }
    }

    if (!mc.thePlayer.onGround) {
      if (mode.equals("mineplex")) {
        mc.thePlayer.motionY += mc.thePlayer.fallDistance == 0f ? 0.0499 : 0.05;
      }
      if (mode.equals("fairfight0.6.0")) {
        if (mc.thePlayer.isInWater() && BlockUtil.getBlock(mc.thePlayer.getPosition().add(-0.5, 1.5, -0.5)) == Blocks.water && mc.thePlayer.fallDistance >= 2.0) {
          mc.thePlayer.motionY = 1.9;
        }
      }
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    if (this.glass.getValue() && !(BlockUtil.getBlock(new BlockPos(mc.thePlayer)) instanceof BlockPane)) {
      return;
    }
    String mode = this.mode.getModeString().toLowerCase();
    if (mode.equals("vanilla")) {
      event.setJumpoff(0.42f * this.height.getValue());
    } else if (mode.equals("mineplex")) {
      event.setJumpoff(0.47f);
    }
  }

  @EventTarget
  public void onMotion(PlayerUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    if (this.mode.getModeString().equalsIgnoreCase("Matrix")) {
      if (this.ticksSinceJump == 1) {
        mc.thePlayer.onGround = false;
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.RECEIVE) return;
    if (mc.thePlayer == null) return;
    if (this.mode.getModeString().equalsIgnoreCase("Matrix")) {
      if (event.getPacket() instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() == mc.thePlayer.getEntityId() && packet.getMotionY() < -500) {
          event.setCancelled(true);
        }
      }
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}