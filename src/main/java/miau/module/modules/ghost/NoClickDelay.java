package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import net.minecraft.client.Minecraft;

public class NoClickDelay extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public NoClickDelay() {
    super("NoClickDelay", true, true);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      if (mc.thePlayer != null && mc.theWorld != null) {
        ((IAccessorMinecraft) mc).setLeftClickCounter(0);
      }
    }
  }
}
