package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.world.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;

public class IceSpeed extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"NCP", "AAC", "Spartan"});

  public IceSpeed() {
    super("IceSpeed", false);
  }

  @Override
  public void onEnabled() {
    if (this.mode.getModeString().equals("NCP")) {
      Blocks.ice.slipperiness = 0.39f;
      Blocks.packed_ice.slipperiness = 0.39f;
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    String mode = this.mode.getModeString();
    if (mode.equals("NCP")) {
      Blocks.ice.slipperiness = 0.39f;
      Blocks.packed_ice.slipperiness = 0.39f;
    } else {
      Blocks.ice.slipperiness = 0.98f;
      Blocks.packed_ice.slipperiness = 0.98f;
    }

    if (!mc.thePlayer.onGround || mc.thePlayer.isOnLadder() || mc.thePlayer.isSneaking() || !mc.thePlayer.isSprinting() || !MoveUtil.isMoving()) {
      return;
    }

    if (BlockUtil.getBlock(mc.thePlayer.getPosition().down()) != Blocks.ice && BlockUtil.getBlock(mc.thePlayer.getPosition().down()) != Blocks.packed_ice) {
      return;
    }

    if (mode.equals("AAC")) {
      mc.thePlayer.motionX *= 1.342;
      mc.thePlayer.motionZ *= 1.342;
      Blocks.ice.slipperiness = 0.6f;
      Blocks.packed_ice.slipperiness = 0.6f;
    } else if (mode.equals("Spartan")) {
      BlockPos upBlock = new BlockPos(mc.thePlayer).up(2);

      if (BlockUtil.getBlock(upBlock) != Blocks.air) {
        mc.thePlayer.motionX *= 1.342;
        mc.thePlayer.motionZ *= 1.342;
      } else {
        mc.thePlayer.motionX *= 1.18;
        mc.thePlayer.motionZ *= 1.18;
      }

      Blocks.ice.slipperiness = 0.6f;
      Blocks.packed_ice.slipperiness = 0.6f;
    }
  }

  @Override
  public void onDisabled() {
    Blocks.ice.slipperiness = 0.98f;
    Blocks.packed_ice.slipperiness = 0.98f;
  }
}