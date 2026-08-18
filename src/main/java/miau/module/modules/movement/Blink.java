package miau.module.modules.movement;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;

public class Blink extends Module {
  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"DEFAULT", "PULSE"});
  public final IntProperty ticks = new IntProperty("ticks", 20, 0, 1200);

  public Blink() {
    super("Blink", false);
  }

  @EventTarget(Priority.LOWEST)
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.POST) {
      if (!Miau.blinkManager.getBlinkingModule().equals(BlinkModules.BLINK)) {
        this.setEnabled(false);
      } else {
        if (this.ticks.getValue() > 0
            && Miau.blinkManager.countMovement() > (long) this.ticks.getValue()) {
          switch (this.mode.getValue()) {
            case 0:
              this.setEnabled(false);
              break;
            case 1:
              Miau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
              Miau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
          }
        }
      }
    }
  }

  @EventTarget
  public void onWorldLoad(LoadWorldEvent event) {
    this.setEnabled(false);
  }

  @Override
  public void onEnabled() {
    Miau.blinkManager.setBlinkState(false, Miau.blinkManager.getBlinkingModule());
    Miau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
  }

  @Override
  public void onDisabled() {
    Miau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
  }
}
