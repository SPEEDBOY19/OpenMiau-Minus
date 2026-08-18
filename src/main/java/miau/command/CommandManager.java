package miau.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C01PacketChatMessage;

public class CommandManager {
  public ArrayList<Command> commands;

  public CommandManager() {
    this.commands = new ArrayList<>();
  }

  public void handleCommand(String string) {
    List<String> params = Arrays.asList(string.substring(1).trim().split("\\s+"));
    ArrayList<String> arrayList = new ArrayList<>(params);
    if (params.get(0).isEmpty()) {
      ChatUtil.display("%sUnknown command&r");
    } else {
      for (Command command : Miau.commandManager.commands) {
        for (String name : command.names) {
          if (params.get(0).equalsIgnoreCase(name)) {
            command.runCommand(arrayList);
            return;
          }
        }
      }
      ChatUtil.display("%sUnknown command (&o%s&r)&r", params.get(0));
    }
  }

  public boolean isTypingCommand(String string) {
    if (string == null || string.length() < 2) {
      return false;
    } else {
      return string.charAt(0) == '.' && Character.isLetterOrDigit(string.charAt(1));
    }
  }

  @EventTarget(Priority.HIGHEST)
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C01PacketChatMessage) {
      String msg = ((C01PacketChatMessage) event.getPacket()).getMessage();
      if (this.isTypingCommand(msg)) {
        event.setCancelled(true);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isCallingFromMinecraftThread()) {
          this.handleCommand(msg);
        } else {
          mc.addScheduledTask(() -> this.handleCommand(msg));
        }
      }
    }
  }
}
