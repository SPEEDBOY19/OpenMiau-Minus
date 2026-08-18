package miau.module.modules.misc.killsults;

import java.util.Random;

public class EnglishMode implements KillSultMode {
  private final String[] messages = {
    "%s Skill issue.",
    "%s , Let touch grass bro",
    "%s , Cry about it.",
    "%s , haizzz, use your brain bro"
  };
  private final Random random = new Random();

  @Override
  public String getMessage(String targetName) {
    return String.format(messages[random.nextInt(messages.length)], targetName);
  }
}
