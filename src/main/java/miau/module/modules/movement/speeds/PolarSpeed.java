package miau.module.modules.movement.speeds;

import java.util.Collections;
import java.util.List;
import miau.event.impl.MoveInputEvent;
import miau.module.modules.movement.Speed;
import miau.property.Property;
import miau.util.player.MoveUtil;

public class PolarSpeed extends SpeedMode {
  private int offGroundTicks = 0;

  public PolarSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public List<Property<?>> getProperties() {
    return Collections.emptyList();
  }

  @Override
  public void onEnable() {
    offGroundTicks = 0;
  }

  @Override
  public void onDisable() {
    offGroundTicks = 0;
  }

  @Override
  public void onMoveInput(MoveInputEvent event) {
    if (!MoveUtil.isMoving()) {
      offGroundTicks = 0;
      return;
    }

    if (mc.thePlayer.onGround) {
      offGroundTicks = 0;
      mc.thePlayer.movementInput.jump = true;
    } else {
      offGroundTicks++;
      mc.thePlayer.movementInput.jump = false;

      float multiplier = offGroundTicks == 1 ? 1.0020001F : 1.0030001F;
      mc.thePlayer.motionX *= multiplier;
      mc.thePlayer.motionZ *= multiplier;

      if (offGroundTicks == 5) {
        mc.thePlayer.motionY -= 0.008F;
      }
    }
  }
}
