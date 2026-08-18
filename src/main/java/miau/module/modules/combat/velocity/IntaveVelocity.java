package miau.module.modules.combat.velocity;

import java.util.concurrent.ThreadLocalRandom;
import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Velocity;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;

public class IntaveVelocity extends VelocityMode {

  public final ModeProperty intaveMode =
      new ModeProperty("intave-mode", 0, new String[] {"Tick Reduce", "Reduce"});
  public final FloatProperty mHurtTime =
      new FloatProperty("min-hurt-time", 9.0F, 1.0F, 10.0F, () -> this.intaveMode.getValue() == 0);
  public final FloatProperty mmHurtTime =
      new FloatProperty("max-hurt-time", 10.0F, 1.0F, 10.0F, () -> this.intaveMode.getValue() == 0);
  public final FloatProperty rFactorMin =
      new FloatProperty("factor-min", 0.6F, 0.0F, 1.0F, () -> true);
  public final FloatProperty rFactorMax =
      new FloatProperty("factor-max", 0.6F, 0.0F, 1.0F, () -> true);

  public IntaveVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onAttack(AttackEvent event) {
    if (this.intaveMode.getValue() == 0) {
      int hurtTime = mc.thePlayer.hurtTime;
      if (hurtTime >= this.mHurtTime.getValue().intValue()
          && hurtTime <= this.mmHurtTime.getValue().intValue()) {
        double factor =
            this.rFactorMin.getValue()
                + (this.rFactorMax.getValue() - this.rFactorMin.getValue())
                    * ThreadLocalRandom.current().nextDouble();
        mc.thePlayer.motionX *= factor;
        mc.thePlayer.motionZ *= factor;
      }
    }
  }
}
