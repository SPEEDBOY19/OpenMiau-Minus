package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import miau.Miau;
import miau.command.Command;
import miau.enums.ChatColors;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

public class PlayerCommand extends Command {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public PlayerCommand() {
    super(new ArrayList<>(Arrays.asList("playerlist", "players")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    ArrayList<String> players = new ArrayList<>();
    for (NetworkPlayerInfo playerInfo : mc.getNetHandler().getPlayerInfoMap()) {
      players.add(playerInfo.getGameProfile().getName().replace("§", "&"));
    }
    if (players.isEmpty()) {
      ChatUtil.display(("%sNo players&r"));
    } else {
      ChatUtil.sendRaw(
          String.format(
              ChatColors.formatColor("%sPlayers:&r %s"),
              ChatColors.formatColor(Miau.clientName),
              String.join(", ", players)));
    }
  }
}
