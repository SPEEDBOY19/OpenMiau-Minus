package miau.module.modules.misc.killsults;

import java.util.Random;

public class CSGOMode implements KillSultMode {
  private final String[] messages = {
    "Missed %s due to correction",
    "Missed %s due to spread",
    "Missed %s due to prediction error",
    "Missed %s due to invalid backtrack",
    "Missed %s due to ?",
    "Shot at head, and missed head, but hit anyways because of spread (lol)",
    "Missed %s due to resolver"
  };
  private final Random random = new Random();

  @Override
  public String getMessage(String targetName) {
    return String.format(messages[random.nextInt(messages.length)], targetName);
  }
}
