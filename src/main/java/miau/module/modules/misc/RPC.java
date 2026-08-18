package miau.module.modules.misc;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;

public class RPC extends Module {
  public final BooleanProperty showServer = new BooleanProperty("show-server", true);
  public final BooleanProperty showModulesCount = new BooleanProperty("show-modules-count", true);

  public RPC() {
    super("RPC", true, false);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      if (!Miau.discordRichPresence.isRunning()) {
        Miau.discordRichPresence.start(this);
      } else {
        Miau.discordRichPresence.update(this);
      }
    }
  }

  @Override
  public void onEnabled() {
    Miau.discordRichPresence.start(this);
  }

  @Override
  public void onDisabled() {
    Miau.discordRichPresence.stop();
  }
}
