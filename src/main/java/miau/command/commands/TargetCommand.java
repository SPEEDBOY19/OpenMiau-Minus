package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import miau.Miau;
import miau.command.Command;
import miau.enums.ChatColors;
import miau.util.client.ChatUtil;

public class TargetCommand extends Command {
  public TargetCommand() {
    super(new ArrayList<>(Arrays.asList("enemy", "e", "target")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    if (args.size() >= 2) {
      String subCommand = args.get(1).toLowerCase(Locale.ROOT);
      switch (subCommand) {
        case "add":
          if (args.size() < 3) {
            ChatUtil.display("%sUsage: .%s add <&oname&r>&r", args.get(0).toLowerCase(Locale.ROOT));
            return;
          }
          String added = Miau.targetManager.add(args.get(2));
          if (added == null) {
            ChatUtil.display("%s&o%s&r is already in your enemy list&r", args.get(2));
            return;
          }
          ChatUtil.display("%sAdded &o%s&r to your enemy list&r", added);
          return;
        case "remove":
          if (args.size() < 3) {
            ChatUtil.display(
                "%sUsage: .%s remove <&oname&r>&r", args.get(0).toLowerCase(Locale.ROOT));
            return;
          }
          String removed = Miau.targetManager.remove(args.get(2));
          if (removed == null) {
            ChatUtil.display("%s&o%s&r is not in your enemy list&r", args.get(2));
            return;
          }
          ChatUtil.display("%sRemoved &o%s&r from your enemy list&r", removed);
          return;
        case "list":
          ArrayList<String> list = Miau.targetManager.getPlayers();
          if (list.isEmpty()) {
            ChatUtil.display(("%sNo enemies&r"));
            return;
          }
          ChatUtil.display(("%sEnemies:&r"));
          for (String player : list) {
            ChatUtil.sendRaw(String.format(ChatColors.formatColor("   &o%s&r"), player));
          }
          return;
        case "clear":
          Miau.targetManager.clear();
          ChatUtil.display(("%sCleared your enemy list&r"));
          return;
      }
    }
    ChatUtil.display(
        String.format(
            "%sUsage: .%s <&oadd&r/&oremove&r/&olist&r/&oclear&r>&r",
            args.get(0).toLowerCase(Locale.ROOT)));
  }
}
