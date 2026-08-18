package miau.module.modules.player;

import miau.module.Module;
import miau.property.properties.BooleanProperty;

public class AntiDebuff extends Module {
  public final BooleanProperty blindness = new BooleanProperty("blindness", true);
  public final BooleanProperty nausea = new BooleanProperty("nausea", true);

  public AntiDebuff() {
    super("AntiDebuff", false);
  }
}
