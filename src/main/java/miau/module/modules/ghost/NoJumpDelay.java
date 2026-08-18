package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.mixin.IAccessorEntityLivingBase;
import miau.module.Module;
import miau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

public class NoJumpDelay extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final IntProperty delay = new IntProperty("delay", 3, 0, 8);

  public NoJumpDelay() {
    super("NoJumpDelay", false);
  }

  @EventTarget(Priority.HIGHEST)
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      ((IAccessorEntityLivingBase) mc.thePlayer)
          .setJumpTicks(
              Math.min(
                  ((IAccessorEntityLivingBase) mc.thePlayer).getJumpTicks(),
                  this.delay.getValue() + 1));
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.delay.getValue().toString()};
  }
}
