package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import miau.Miau;
import miau.command.Command;
import miau.enums.ChatColors;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

public class ItemCommand extends Command {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public ItemCommand() {
    super(new ArrayList<>(Arrays.asList("itemname", "item")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
    if (stack != null) {
      String display = stack.getDisplayName().replace('§', '&');
      String registryName = stack.getItem().getRegistryName();
      String compound =
          stack.hasTagCompound() ? stack.getTagCompound().toString().replace('§', '&') : "";
      ChatUtil.sendRaw(
          String.format(
              "%s%s (%s) %s",
              ChatColors.formatColor(Miau.clientName), display, registryName, compound));
    }
  }
}
