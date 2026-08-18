package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.WebSlowDownEvent;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.player.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;

public class NoWeb extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"POLAR", "VANILLA"});
  public final IntProperty hurtTime = new IntProperty("hurt-time", 1, 0, 10);
  public final FloatProperty xzMotion = new FloatProperty("xz-motion", 0.62F, 0.0F, 1.0F);
  public final FloatProperty yMotion = new FloatProperty("y-motion", 0.89F, 0.0F, 1.0F);

  public NoWeb() {
    super("NoWeb", false);
  }

  @EventTarget
  public void onWebSlowDown(WebSlowDownEvent e) {
    if (!this.isEnabled()) return;

    if (this.mode.getValue() == 0 /* POLAR */) {
      if (mc.thePlayer.hurtTime >= this.hurtTime.getValue()) {
        boolean jumpHeld = mc.gameSettings.keyBindJump.isKeyDown();
        e.setCancelled(jumpHeld);
      } else {
        BlockPos below = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0D, mc.thePlayer.posZ);
        Block blockBelow = mc.theWorld.getBlockState(below).getBlock();
        if (!blockBelow.isFullBlock() && !blockBelow.isOpaqueCube()) {
          boolean sneaking = mc.gameSettings.keyBindSneak.isKeyDown();
          if (!sneaking) {
            e.setMotionY(0.080399998158216F);
          } else {
            e.setMotionY(-10.0D);
          }

          SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
          sim.rotationYaw = miau.management.RotationState.isActived() ? miau.management.RotationState.getRotationYawHead() : mc.thePlayer.rotationYaw;
          sim.tick();

          if (sim.isInWeb() && MoveUtil.isMoving()) {
            double moveDirection = MoveUtil.getMoveDirection();
            e.setMotionX(-Math.sin(moveDirection) * 0.1D);
            e.setMotionZ(Math.cos(moveDirection) * 0.1D);
          }
        }
      }
    } else if (this.mode.getValue() == 1 /* VANILLA */) {
      e.setCancelled(true);
    }
  }
}
