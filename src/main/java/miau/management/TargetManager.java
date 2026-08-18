package miau.management;

import java.awt.*;
import java.io.File;
import miau.Miau;
import miau.enums.ChatColors;

public class TargetManager extends PlayerFileManager {
  public TargetManager() {
    super(new File("./config/Miau/", "enemies.txt"), new Color(ChatColors.DARK_RED.toAwtColor()));
  }

  @Override
  public String add(String name) {
    if (Miau.friendManager.isFriend(name)) {
      return null;
    }
    return super.add(name);
  }
}
