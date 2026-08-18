package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;

public class VoidBouncer extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final FloatProperty bounceFactor = new FloatProperty("BounceFactor", 1.0f, 0.0f, 100.0f);
  private boolean bounced = false;

  public VoidBouncer() {
    super("VoidBouncer", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      if (!mc.thePlayer.onGround
          && mc.thePlayer.posY < -64.0
          && mc.thePlayer.hurtTime != 0
          && !this.bounced) {
        mc.thePlayer.motionY *= -this.bounceFactor.getValue();
        this.bounced = true;
      }
      if (mc.thePlayer.onGround || mc.thePlayer.posY >= -64.0) {
        this.bounced = false;
      }
    }
  }
}