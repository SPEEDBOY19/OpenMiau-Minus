package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.SafeWalkEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;

public class SafeWalk extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty onlyGround = new BooleanProperty("only-ground", false);
  public final BooleanProperty pitchLimit = new BooleanProperty("pitch", false);
  public final FloatProperty pitchBound =
      new FloatProperty("pitch-bound", 0.0F, 90.0F, 0.0F, 90.0F, this.pitchLimit::getValue);

  public SafeWalk() {
    super("SafeWalk", false);
  }

  private boolean canSafeWalk() {
    if (mc.thePlayer == null) {
      return false;
    }
    if (this.onlyGround.getValue() && !mc.thePlayer.onGround) {
      return false;
    }
    return !this.pitchLimit.getValue()
        || mc.thePlayer.rotationPitch < this.pitchBound.getSecondValue()
            && mc.thePlayer.rotationPitch > this.pitchBound.getValue();
  }

  @EventTarget
  public void onMove(SafeWalkEvent event) {
    if (this.isEnabled() && canSafeWalk()) {
      event.setSafeWalk(true);
    }
  }
}
