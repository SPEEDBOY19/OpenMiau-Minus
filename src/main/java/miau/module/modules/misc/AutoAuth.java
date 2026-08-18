package miau.module.modules.misc;

import java.util.Locale;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.IntProperty;
import miau.property.properties.TextProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;

public class AutoAuth extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final String[] PASSWORD_TOKENS = {"password", "pass"};

  public final TextProperty password = new TextProperty("password", "12341234");
  public final IntProperty delay = new IntProperty("delay", 1000, 100, 5000);

  private String queuedCommand;
  private long queuedAt;

  public AutoAuth() {
    super("AutoAuth", false);
  }

  @Override
  public void onEnabled() {
    clearQueue();
  }

  @Override
  public void onDisabled() {
    clearQueue();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.RECEIVE
        || !(event.getPacket() instanceof S02PacketChat)) {
      return;
    }

    S02PacketChat packet = (S02PacketChat) event.getPacket();
    String message = packet.getChatComponent().getUnformattedText();
    AuthPrompt prompt = AuthPrompt.from(message);
    if (prompt == null) {
      return;
    }

    int repeat = Math.max(1, countPasswordSlots(message));
    queue(prompt.buildCommand(this.password.getValue(), repeat));
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE || this.queuedCommand == null) {
      return;
    }

    if (System.currentTimeMillis() - this.queuedAt >= this.delay.getValue()) {
      if (mc.thePlayer != null) {
        mc.thePlayer.sendChatMessage(this.queuedCommand);
      }
      clearQueue();
    }
  }

  @EventTarget
  public void onWorld(LoadWorldEvent event) {
    clearQueue();
  }

  private void queue(String command) {
    this.queuedCommand = command;
    this.queuedAt = System.currentTimeMillis();
  }

  private void clearQueue() {
    this.queuedCommand = null;
    this.queuedAt = 0L;
  }

  private int countPasswordSlots(String message) {
    String normalized = message.toLowerCase(Locale.ROOT);
    int count = 0;
    for (String token : PASSWORD_TOKENS) {
      int index = 0;
      while ((index = normalized.indexOf(token, index)) != -1) {
        count++;
        index += token.length();
      }
    }
    return count;
  }

  private enum AuthPrompt {
    REGISTER("/register ", "/register"),
    SHORT_REGISTER("/reg ", "/reg"),
    LOGIN("/login ", "/login");

    private final String trigger;
    private final String command;

    AuthPrompt(String trigger, String command) {
      this.trigger = trigger;
      this.command = command;
    }

    private static AuthPrompt from(String message) {
      String normalized = message.toLowerCase(Locale.ROOT);
      for (AuthPrompt prompt : values()) {
        if (normalized.contains(prompt.trigger)) {
          return prompt;
        }
      }
      return null;
    }

    private String buildCommand(String password, int repeat) {
      StringBuilder builder = new StringBuilder(this.command);
      for (int i = 0; i < repeat; i++) {
        builder.append(' ').append(password);
      }
      return builder.toString();
    }
  }
}
