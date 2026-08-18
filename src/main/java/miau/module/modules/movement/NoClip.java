package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;

public class NoClip extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final FloatProperty speedValue = new FloatProperty("Speed", 0.5f, 0.0f, 10.0f);

  public NoClip() {
    super("NoClip", false);
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null) {
      mc.thePlayer.noClip = false;
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      MoveUtil.strafe(this.speedValue.getValue().doubleValue());
      mc.thePlayer.noClip = true;
      mc.thePlayer.onGround = false;
      mc.thePlayer.capabilities.isFlying = false;
      double ySpeed = 0.0;
      if (mc.gameSettings.keyBindJump.isKeyDown()) {
        ySpeed += this.speedValue.getValue();
      }
      if (mc.gameSettings.keyBindSneak.isKeyDown()) {
        ySpeed -= this.speedValue.getValue();
      }
      mc.thePlayer.motionY = ySpeed;
    }
  }
}