package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import miau.Miau;
import miau.command.Command;
import miau.module.Module;
import miau.util.client.ChatUtil;
import miau.util.client.KeyBindUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class BindCommand extends Command {
  public BindCommand() {
    super(new ArrayList<>(Arrays.asList("bind", "b")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    if (args.size() < 3) {
      if (args.size() == 2
          && (args.get(1).equalsIgnoreCase("l") || args.get(1).equalsIgnoreCase("list"))) {
        List<Module> modules =
            Miau.moduleManager.modules.values().stream()
                .filter(module -> module.getKey() != 0)
                .collect(Collectors.toList());
        if (modules.isEmpty()) {
          ChatUtil.display("No binds&r");
        } else {
          ChatUtil.display("Binds:&r");
          for (Module module : modules) {
            ChatUtil.display("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule());
          }
        }
      } else {
        ChatUtil.display(
            "Usage: .%s <&omodule&r> <&okey&r>&r | .%s <&omodule&r> &onone&r | .%s &olist&r",
            args.get(0).toLowerCase(Locale.ROOT),
            args.get(0).toLowerCase(Locale.ROOT),
            args.get(0).toLowerCase(Locale.ROOT));
      }
    } else {
      String keyInput = args.get(2).toUpperCase();
      int keyIndex = 0;

      if (keyInput.equalsIgnoreCase("NONE")
          || keyInput.equalsIgnoreCase("NULL")
          || keyInput.equalsIgnoreCase("0")) {
        keyIndex = 0;
      } else {
        keyIndex = Keyboard.getKeyIndex(keyInput);

        if (keyIndex == 0) {
          int buttonIndex = getMouseButtonIndex(keyInput);
          if (buttonIndex != -1) {
            keyIndex = buttonIndex - 100;
          }
        }
      }

      if (!args.get(1).equals("*")) {
        Module module = Miau.moduleManager.getModule(args.get(1));
        if (module == null) {
          ChatUtil.display("Module not found (&o%s&r)&r", args.get(1));
        } else {
          module.setKey(keyIndex);
          if (keyIndex == 0) {
            ChatUtil.display("Unbind &o%s&r", module.getName());
          } else {
            ChatUtil.display(
                "Bound &o%s&r to &l[%s]&r", module.getName(), KeyBindUtil.getKeyName(keyIndex));
          }
        }
      } else {
        for (Module module : Miau.moduleManager.modules.values()) {
          module.setKey(keyIndex);
        }
        if (keyIndex == 0) {
          ChatUtil.display("Unbind all modules&r");
        } else {
          ChatUtil.display("Bind all modules to &l[%s]&r", KeyBindUtil.getKeyName(keyIndex));
        }
      }
    }
  }

  private int getMouseButtonIndex(String buttonName) {
    if (buttonName.startsWith("MOUSE")) {
      try {
        String numStr = buttonName.substring(5);
        int buttonNum = Integer.parseInt(numStr);
        if (buttonNum >= 0 && buttonNum < Mouse.getButtonCount()) {
          return buttonNum;
        }
      } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
      }
    }

    int buttonIndex = Mouse.getButtonIndex(buttonName);
    if (buttonIndex != -1) {
      return buttonIndex;
    }

    switch (buttonName) {
      case "LBUTTON":
      case "LMB":
      case "LEFTCLICK":
        return 0;
      case "RBUTTON":
      case "RMB":
      case "RIGHTCLICK":
        return 1;
      case "MBUTTON":
      case "MMB":
      case "MIDDLECLICK":
      case "SCROLLCLICK":
        return 2;
      case "MOUSE3":
      case "XBUTTON1":
      case "SIDEBUTTON1":
      case "BOTTOMSIDE":
        return 3;
      case "MOUSE4":
      case "XBUTTON2":
      case "SIDEBUTTON2":
      case "TOPSIDE":
        return 4;
      case "MOUSE5":
        return 5;
      case "MOUSE6":
        return 6;
      case "MOUSE7":
        return 7;
      default:
        return -1;
    }
  }
}
