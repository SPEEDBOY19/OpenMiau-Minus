package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

public class BoatFly extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty modeValue = new ModeProperty("Mode", 0, new String[] {"Motion", "Clip", "Velocity"});
  public final FloatProperty speedValue = new FloatProperty("Speed", 0.3f, 0.0f, 1.0f);

  public BoatFly() {
    super("BoatFly", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (!mc.thePlayer.isRiding()) {
        return;
      }
      Entity vehicle = mc.thePlayer.ridingEntity;
      double x = -Math.sin(MoveUtil.getMoveDirection()) * this.speedValue.getValue();
      double z = Math.cos(MoveUtil.getMoveDirection()) * this.speedValue.getValue();
      String mode = this.modeValue.getModeString().toLowerCase();
      if (mode.equals("motion")) {
        vehicle.motionX = x;
        vehicle.motionY = mc.gameSettings.keyBindJump.isKeyDown() ? this.speedValue.getValue().doubleValue() : 0.0;
        vehicle.motionZ = z;
      } else if (mode.equals("clip")) {
        vehicle.setPosition(vehicle.posX + x, vehicle.posY + (mc.gameSettings.keyBindJump.isKeyDown() ? this.speedValue.getValue().doubleValue() : 0.0), vehicle.posZ + z);
      } else if (mode.equals("velocity")) {
        vehicle.addVelocity(x, mc.gameSettings.keyBindJump.isKeyDown() ? this.speedValue.getValue().doubleValue() : 0.0, z);
      }
    }
  }
}
