package miau.management;

import java.awt.*;
import java.io.File;
import miau.Miau;
import miau.enums.ChatColors;

public class FriendManager extends PlayerFileManager {
  public FriendManager() {
    super(new File("./config/Miau/", "friends.txt"), new Color(ChatColors.DARK_GREEN.toAwtColor()));
  }

  @Override
  public String add(String name) {
    if (Miau.targetManager.isFriend(name)) {
      return null;
    }
    return super.add(name);
  }
}
