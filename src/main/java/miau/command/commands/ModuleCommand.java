package miau.command.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import miau.Miau;
import miau.command.Command;
import miau.module.Module;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.util.client.ChatUtil;

public class ModuleCommand extends Command {
  public ModuleCommand() {
    super(
        new ArrayList<>(
            Miau.moduleManager.modules.values().stream()
                .<String>map(Module::getName)
                .collect(Collectors.<String>toList())));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    Module module = Miau.moduleManager.getModule(args.get(0));
    if (args.size() >= 2) {
      Property<?> property = Miau.propertyManager.getProperty(module, args.get(1));
      if (property == null) {
        ChatUtil.display("%s%s has no property &o%s&r", module.getName(), args.get(1));
      } else if (args.size() < 3 && !(property instanceof BooleanProperty)) {
        ChatUtil.display(
            "%s%s: &o%s&r is set to %s&r (%s)&r",
            module.getName(),
            property.getName(),
            property.formatValue(),
            property.getValuePrompt());
      } else {
        String newValue = args.size() < 3 ? null : String.join(" ", args.subList(2, args.size()));
        try {
          if (property.parseString(newValue)) {
            ChatUtil.display(
                "%s%s: &o%s&r has been set to %s&r",
                module.getName(), property.getName(), property.formatValue());
            return;
          }
        } catch (Exception e) {
        }
        ChatUtil.display(
            "%sInvalid value for property &o%s&r (%s)&r",
            property.getName(), property.getValuePrompt());
      }
    } else {
      List<Property<?>> properties = Miau.propertyManager.properties.get(module.getClass());
      if (properties != null) {
        List<Property<?>> visible =
            properties.stream().filter(Property::isVisible).collect(Collectors.toList());
        if (!visible.isEmpty()) {
          ChatUtil.display("%s%s:&r", module.formatModule());
          for (Property<?> property : visible) {
            ChatUtil.display("&7»&r %s: %s&r", property.getName(), property.formatValue());
          }
          return;
        }
      }
      ChatUtil.display("%s%s has no properties&r", module.formatModule());
    }
  }
}
