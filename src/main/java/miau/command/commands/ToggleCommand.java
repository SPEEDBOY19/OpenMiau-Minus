package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import miau.Miau;
import miau.command.Command;
import miau.module.Module;
import miau.util.client.ChatUtil;

public class ToggleCommand extends Command {
  public ToggleCommand() {
    super(new ArrayList<>(Arrays.asList("toggle", "t")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    if (args.size() < 2) {
      ChatUtil.display(
          String.format("%sUsage: .%s <&omodule&r>&r", args.get(0).toLowerCase(Locale.ROOT)));
    } else {
      Module module = Miau.moduleManager.getModule(args.get(1));
      if (module == null) {
        ChatUtil.display("%sModule not found (&o%s&r)&r", args.get(1));
      } else {
        boolean changed = true;
        if (args.size() >= 3) {
          if (args.get(2).equalsIgnoreCase("true")
              || args.get(2).equalsIgnoreCase("on")
              || args.get(2).equalsIgnoreCase("1")) {
            changed = !module.isEnabled();
          } else if (args.get(2).equalsIgnoreCase("false")
              || args.get(2).equalsIgnoreCase("off")
              || args.get(2).equalsIgnoreCase("0")) {
            changed = module.isEnabled();
          }
        }
        if (changed && module.toggle()) {
          ChatUtil.display(
              "%s%s: %s&r", module.getName(), module.isEnabled() ? "&a&lON" : "&c&lOFF");
        }
      }
    }
  }
}
