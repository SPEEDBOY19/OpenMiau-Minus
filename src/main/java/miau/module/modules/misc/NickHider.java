package miau.module.modules.misc;

import java.util.regex.Matcher;
import miau.enums.ChatColors;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.TextProperty;
import net.minecraft.client.Minecraft;

public class NickHider extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final TextProperty protectName = new TextProperty("name", "miau");
  public final BooleanProperty scoreboard = new BooleanProperty("scoreboard", true);
  public final BooleanProperty level = new BooleanProperty("level", true);

  public NickHider() {
    super("NickHider", false, true);
  }

  public String replaceNick(String input) {
    if (input != null && mc.thePlayer != null) {
      if (this.scoreboard.getValue() && input.matches("§7\\d{2}/\\d{2}/\\d{2}(?:\\d{2})?  ?§8.*")) {
        input = input.replaceAll("§8", "§8§k").replaceAll("[^\\x00-\\x7F§]", "?");
      }
      return input.replaceAll(
          mc.thePlayer.getName(),
          Matcher.quoteReplacement(ChatColors.formatColor(this.protectName.getValue())));
    } else {
      return input;
    }
  }
}
