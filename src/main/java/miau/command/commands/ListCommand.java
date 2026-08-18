package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import miau.Miau;
import miau.command.Command;
import miau.module.Module;
import miau.util.client.ChatUtil;

public class ListCommand extends Command {
  public ListCommand() {
    super(new ArrayList<>(Arrays.asList("list", "l", "modules", "miau")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    if (!Miau.moduleManager.modules.isEmpty()) {
      ChatUtil.display(("%sModules:&r"));
      for (Module module : Miau.moduleManager.modules.values()) {
        ChatUtil.display("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule());
      }
    }
  }
}
