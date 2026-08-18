package miau.module.modules.movement;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.BlockStairs;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

public class FastStairs extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 1, new String[] {"Step", "NCP", "AAC3.1.0", "AAC3.3.6", "AAC3.3.13"});
  public final BooleanProperty longJump = new BooleanProperty("LongJump", false, () -> this.mode.getModeString().startsWith("AAC"));

  private boolean canJump = false;
  private boolean walkingDown = false;

  public FastStairs() {
    super("FastStairs", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (!MoveUtil.isMoving() || Miau.moduleManager.modules.get(Speed.class).isEnabled()) {
      return;
    }

    if (mc.thePlayer.fallDistance > 0 && !this.walkingDown) {
      this.walkingDown = true;
    }
    if (mc.thePlayer.posY > mc.thePlayer.prevPosY) {
      this.walkingDown = false;
    }

    String mode = this.mode.getModeString();

    if (!mc.thePlayer.onGround) {
      return;
    }

    BlockPos blockPos = new BlockPos(mc.thePlayer);

    if (BlockUtil.getBlock(blockPos) instanceof BlockStairs && !this.walkingDown) {
      mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.5, mc.thePlayer.posZ);

      double motion;
      if (mode.equals("NCP")) {
        motion = 1.4;
      } else if (mode.equals("AAC3.1.0")) {
        motion = 1.5;
      } else if (mode.equals("AAC3.3.13")) {
        motion = 1.2;
      } else {
        motion = 1.0;
      }

      mc.thePlayer.motionX *= motion;
      mc.thePlayer.motionZ *= motion;
    }

    if (BlockUtil.getBlock(blockPos.down()) instanceof BlockStairs) {
      if (this.walkingDown) {
        if (mode.equals("NCP")) {
          mc.thePlayer.motionY = -1.0;
        } else if (mode.equals("AAC3.3.13")) {
          mc.thePlayer.motionY -= 0.014;
        }

        return;
      }

      double motion;
      if (mode.equals("AAC3.3.6")) {
        motion = 1.48;
      } else if (mode.equals("AAC3.3.13")) {
        motion = 1.52;
      } else {
        motion = 1.3;
      }

      mc.thePlayer.motionX *= motion;
      mc.thePlayer.motionZ *= motion;
      this.canJump = true;
    } else if (mode.startsWith("AAC") && this.canJump) {
      if (this.longJump.getValue()) {
        if (!mc.gameSettings.keyBindJump.isKeyDown()) {
          mc.thePlayer.jump();
        }
        mc.thePlayer.motionX *= 1.35;
        mc.thePlayer.motionZ *= 1.35;
      }

      this.canJump = false;
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}