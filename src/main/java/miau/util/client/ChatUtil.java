package miau.util.client;

import java.util.IllegalFormatException;
import java.util.MissingFormatArgumentException;
import miau.Miau;
import miau.enums.ChatColors;
import miau.util.animation.*;
import miau.util.animation.impl.DecelerateAnimation;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

public class ChatUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public static Animation openingAnimation = new DecelerateAnimation(175, 1, Direction.BACKWARDS);

  public static void display(String message, Object... objects) {
    if (mc.thePlayer != null) {
      String text = formatMessage(message, objects);
      if (!hasClientPrefix(text)) {
        text = Miau.clientName + text;
      }

      try {
        String dynamicPrefix = miau.enums.ChatColors.getDynamicPrefix();
        String formattedStatic = ChatColors.formatColor(Miau.clientName);
        if (text.startsWith(Miau.clientName)) {
          text = dynamicPrefix + text.substring(Miau.clientName.length());
        } else if (text.startsWith(formattedStatic)) {
          text = dynamicPrefix + text.substring(formattedStatic.length());
        }
      } catch (Exception e) {
      }

      String finalMessage = ChatColors.formatColor(text);
      mc.thePlayer.addChatMessage(new ChatComponentText(finalMessage));
    }
  }

  private static String formatMessage(String message, Object[] objects) {
    try {
      return String.format(message, objects);
    } catch (MissingFormatArgumentException e) {
      if (message.startsWith("%s")) {
        try {
          return String.format(message, prependClientName(objects));
        } catch (IllegalFormatException ignored) {
        }
      }
      return message;
    } catch (IllegalFormatException e) {
      return message;
    }
  }

  private static boolean hasClientPrefix(String message) {
    String formattedPrefix = ChatColors.formatColor(Miau.clientName);
    return message.startsWith(Miau.clientName)
        || message.startsWith(formattedPrefix)
        || message.startsWith(ChatColors.PREFIX_CLEAN);
  }

  private static Object[] prependClientName(Object[] objects) {
    Object[] result = new Object[objects.length + 1];
    result[0] = Miau.clientName;
    System.arraycopy(objects, 0, result, 1, objects.length);
    return result;
  }

  public static void displayNoPrefix(String message, Object... objects) {
    if (mc.thePlayer != null) {
      String text = String.format(message, objects);
      mc.thePlayer.addChatMessage(new ChatComponentText(ChatColors.formatColor(text)));
    }
  }

  public static void sendRaw(String message) {
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(new ChatComponentText(ChatColors.formatColor(message)));
    }
  }

  public static void sendMessage(String message) {
    if (mc.thePlayer != null) {
      mc.thePlayer.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
    }
  }

  public static void send(String message) {
    if (mc.thePlayer != null) {
      mc.thePlayer.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
    }
  }

  public static void send(IChatComponent component) {
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(component);
    }
  }

  public static void sendFormatted(String message) {
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(new ChatComponentText(ChatColors.formatColor(message)));
    }
  }

  public static void sendFormatted(IChatComponent component) {
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(component);
    }
  }
}
