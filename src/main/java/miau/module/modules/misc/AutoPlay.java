package miau.module.modules.misc;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.notification.NotificationType;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.time.TimerUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S02PacketChat;

public class AutoPlay extends Module {
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Hypixel", "None"});
  public final FloatProperty autoPlayDelay = new FloatProperty("AutoPlay Delay", 2.5f, 0f, 10f);

  private String queuedMode = null;
  private final TimerUtil timer = new TimerUtil();

  public AutoPlay() {
    super("AutoPlay", false);
  }

  @Override
  public void onEnabled() {
    queuedMode = null;
    super.onEnabled();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled()) return;
    if (queuedMode != null && timer.hasTimeElapsed((long) (autoPlayDelay.getValue() * 1000))) {
      net.minecraft.client.Minecraft.getMinecraft().thePlayer.sendChatMessage(queuedMode);
      queuedMode = null;
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.RECEIVE) return;

    Packet<?> packet = event.getPacket();

    if (packet instanceof S02PacketChat) {
      S02PacketChat chat = (S02PacketChat) packet;
      if (chat.isChat() && chat.getChatComponent() == null) return;

      if (mode.getValue() == 0) { // Hypixel
        String m = chat.getChatComponent().toString();
        if (m.contains("ClickEvent{action=RUN_COMMAND, value='/play ")) {
          try {
            String command = m.split("action=RUN_COMMAND, value='")[1].split("'\\}")[0];
            sendToGame(command);
          } catch (Exception ignored) {
          }
        }
      }
    }
  }

  private void sendToGame(String modeStr) {
    float delay = autoPlayDelay.getValue();
    Miau.notificationManager.pop(
        "AutoPlay",
        "Sending you to a new game" + (delay > 0 ? " in " + delay + "s" : "") + "!",
        (int) (delay * 1000),
        NotificationType.INFO);
    queuedMode = modeStr;
    timer.reset();
  }
}
