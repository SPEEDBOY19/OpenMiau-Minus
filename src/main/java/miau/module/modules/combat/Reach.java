package miau.module.modules.combat;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Random;
import miau.event.EventTarget;
import miau.event.impl.PickEvent;
import miau.event.impl.RaytraceEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.PercentProperty;

public class Reach extends Module {
  private static final DecimalFormat df =
      new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
  private final Random theRandom = new Random();
  private boolean expanding = true;
  public final FloatProperty range = new FloatProperty("range", 3.1F, 3.0F, 6.0F);
  public final PercentProperty chance = new PercentProperty("chance", 100);

  public Reach() {
    super("Reach", false);
  }

  @EventTarget
  public void onPick(PickEvent event) {
    if (this.isEnabled() && this.expanding) {
      event.setRange(this.range.getValue().doubleValue());
    }
  }

  @EventTarget
  public void onRaytrace(RaytraceEvent event) {
    if (this.isEnabled() && this.expanding) {
      event.setRange(Math.max(event.getRange(), this.range.getValue().doubleValue() + 0.5));
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      this.expanding = this.theRandom.nextDouble() <= (double) this.chance.getValue() / 100.0;
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {df.format(this.range.getValue())};
  }
}
