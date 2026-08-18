package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import miau.Miau;
import miau.command.Command;
import miau.util.client.ChatUtil;

public class HelpCommand extends Command {
  public HelpCommand() {
    super(new ArrayList<>(Arrays.asList("help", "commands")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    if (!Miau.moduleManager.modules.isEmpty()) {
      ChatUtil.display("%sCommands:&r");
      for (Command command : Miau.commandManager.commands) {
        if (!(command instanceof ModuleCommand)) {
          ChatUtil.display("&7»&r .%s&r", String.join(" &7/&r .", command.names));
        }
      }
    }
  }
}
