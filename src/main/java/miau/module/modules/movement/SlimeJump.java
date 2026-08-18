package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.JumpEvent;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.world.BlockUtil;
import net.minecraft.block.BlockSlime;
import net.minecraft.client.Minecraft;

public class SlimeJump extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final FloatProperty motion = new FloatProperty("Motion", 0.42f, 0.2f, 1f);
  public final ModeProperty mode = new ModeProperty("Mode", 1, new String[] {"Set", "Add"});

  public SlimeJump() {
    super("SlimeJump", false);
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    if (BlockUtil.getBlock(mc.thePlayer.getPosition().down()) instanceof BlockSlime) {
      if (this.mode.getModeString().equalsIgnoreCase("set")) {
        event.setJumpoff(this.motion.getValue());
      } else {
        mc.thePlayer.motionY = 0.0;
        event.setJumpoff(this.motion.getValue());
      }
    }
  }
}