package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntityPlayerSP;
import miau.module.Module;
import net.minecraft.client.Minecraft;

public class PerfectHorseJump extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public PerfectHorseJump() {
    super("PerfectHorseJump", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      ((IAccessorEntityPlayerSP) mc.thePlayer).setHorseJumpPowerCounter(9);
      ((IAccessorEntityPlayerSP) mc.thePlayer).setHorseJumpPower(1.0f);
    }
  }
}