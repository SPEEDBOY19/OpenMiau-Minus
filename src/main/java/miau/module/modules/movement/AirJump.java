package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class AirJump extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"VanillaJump", "Motion"});
  public final IntProperty cooldown = new IntProperty("CoolDown", 5, 0, 20);

  private int cooldownCounter = 0;

  public AirJump() {
    super("AirJump", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (mc.gameSettings.keyBindJump.isKeyDown() || mc.gameSettings.keyBindJump.isPressed()) {
      if (this.cooldownCounter != 0) {
        return;
      }
      if (this.mode.getModeString().equals("VanillaJump")) {
        mc.thePlayer.jump();
      } else {
        mc.thePlayer.motionY = 0.42;
      }
      this.cooldownCounter = this.cooldown.getValue();
    }
    if (this.cooldownCounter != 0) this.cooldownCounter--;
  }
}