package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import miau.Miau;
import miau.command.Command;
import miau.module.Module;
import miau.util.client.ChatUtil;

public class HideCommand extends Command {
  public HideCommand() {
    super(new ArrayList<>(Arrays.asList("hide", "h")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    if (args.size() < 2) {
      ChatUtil.display(
          String.format("%sUsage: .%s <&omodule&r>&r", args.get(0).toLowerCase(Locale.ROOT)));
    } else if (!args.get(1).equals("*")) {
      Module module = Miau.moduleManager.getModule(args.get(1));
      if (module == null) {
        ChatUtil.display("%sModule &o%s&r not found&r", args.get(1));
      } else if (module.isHidden()) {
        ChatUtil.display("%s&o%s&r is already hidden in HUD&r", module.getName());
      } else {
        module.setHidden(true);
        ChatUtil.display("%s&o%s&r has been hidden in HUD&r", module.getName());
      }
    } else {
      for (Module module : Miau.moduleManager.modules.values()) {
        module.setHidden(true);
      }
      ChatUtil.display(("%sAll modules have been hidden in HUD&r"));
    }
  }
}
