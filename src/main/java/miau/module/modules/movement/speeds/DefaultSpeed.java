package miau.module.modules.movement.speeds;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.StrafeEvent;
import miau.module.modules.movement.Speed;
import miau.property.Property;
import miau.property.properties.FloatProperty;
import miau.property.properties.PercentProperty;
import miau.util.player.MoveUtil;

public class DefaultSpeed extends SpeedMode {
  public final FloatProperty multiplier =
      new FloatProperty("multiplier", 1.0F, 0.0F, 10.0F, () -> parent.mode.getValue() == 0);
  public final FloatProperty friction =
      new FloatProperty("friction", 1.0F, 0.0F, 10.0F, () -> parent.mode.getValue() == 0);
  public final PercentProperty strafe =
      new PercentProperty("strafe", 0, () -> parent.mode.getValue() == 0);

  public DefaultSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(multiplier, friction, strafe);
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    if (parent.canBoost()) {
      if (mc.thePlayer.onGround) {
        mc.thePlayer.motionY = 0.42F;
        MoveUtil.setSpeed(
            MoveUtil.getJumpMotion() * (double) this.multiplier.getValue().floatValue(),
            MoveUtil.getMoveYaw());
      } else {
        if (this.friction.getValue() != 1.0F) {
          event.setFriction(event.getFriction() * this.friction.getValue());
        }
        if (this.strafe.getValue() > 0) {
          double speed = MoveUtil.getSpeed();
          MoveUtil.setSpeed(
              speed * (double) ((float) (100 - this.strafe.getValue()) / 100.0F),
              MoveUtil.getDirectionYaw());
          MoveUtil.addSpeed(
              speed * (double) ((float) this.strafe.getValue().intValue() / 100.0F),
              MoveUtil.getMoveYaw());
          MoveUtil.setSpeed(speed);
        }
      }
    }
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (parent.canBoost()) {
      mc.thePlayer.movementInput.jump = false;
    }
  }
}
