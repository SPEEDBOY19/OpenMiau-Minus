package miau.module.modules.movement.speeds;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import miau.event.impl.LivingUpdateEvent;
import miau.module.modules.movement.Speed;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.player.MoveUtil;

public class LowHopSpeed extends SpeedMode {
  public final FloatProperty sevenTickSpeed =
      new FloatProperty("lowhop-speed", 2.0F, 0.8F, 2.5F, () -> parent.mode.getValue() == 2);
  public final BooleanProperty liquidDisable =
      new BooleanProperty("disable-in-liquid", true, () -> parent.mode.getValue() == 2);
  public final BooleanProperty sneakDisable =
      new BooleanProperty("disable-while-sneaking", true, () -> parent.mode.getValue() == 2);
  public final BooleanProperty jumpMoving =
      new BooleanProperty("only-jump-when-moving", true, () -> parent.mode.getValue() == 2);
  private boolean hopping;

  public LowHopSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(sevenTickSpeed, liquidDisable, sneakDisable, jumpMoving);
  }

  private boolean canSevenTick() {
    if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.capabilities.isFlying) {
      return false;
    }
    if (this.liquidDisable.getValue() && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) {
      return false;
    }
    return !this.sneakDisable.getValue() || !mc.thePlayer.isSneaking();
  }

  private double randomizeDouble(double min, double max) {
    return min + (max - min) * ThreadLocalRandom.current().nextDouble();
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.canSevenTick()) {
      return;
    }

    if (mc.thePlayer.onGround && (!this.jumpMoving.getValue() || MoveUtil.isMoving())) {
      mc.thePlayer.jump();

      double speed = this.sevenTickSpeed.getValue() - 0.52D;
      int speedAmplifier = MoveUtil.getSpeedLevel();
      if (speedAmplifier == 1) {
        speed += 0.02D;
      } else if (speedAmplifier == 2) {
        speed += 0.04D;
      } else if (speedAmplifier >= 3) {
        speed += 0.1D;
      }

      if (MoveUtil.isMoving()) {
        MoveUtil.setSpeed(speed - this.randomizeDouble(0.0001D, 0.0003D), MoveUtil.getMoveYaw());
      }
      this.hopping = true;
    }

    if (!mc.thePlayer.onGround) {
      this.hopping = false;
    }
    mc.thePlayer.movementInput.jump = false;
  }

  @Override
  public void onDisable() {
    this.hopping = false;
  }
}
