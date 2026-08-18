package miau.module.modules.misc;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.TextProperty;
import miau.util.client.ChatUtil;
import net.minecraft.network.play.server.S02PacketChat;

/**
 * @author meowtils
 */
public class AutoGG extends Module {
  public final BooleanProperty sendFirstMessage = new BooleanProperty("First message", true);
  public final TextProperty firstMessage =
      new TextProperty("First msg Text", "gg", this.sendFirstMessage::getValue);
  public final IntProperty autoggDelay = new IntProperty("AutoGG Delay (ms)", 0, 0, 1000);

  public final BooleanProperty sendSecondMessage = new BooleanProperty("Second message", false);
  public final TextProperty secondMessage =
      new TextProperty("Second msg Text", "<3", this.sendSecondMessage::getValue);
  public final IntProperty autoggSecondDelay =
      new IntProperty("Second Delay (ms)", 0, 0, 1000, this.sendSecondMessage::getValue);

  public final BooleanProperty autoglEnabled = new BooleanProperty("AutoGL Enabled", false);
  public final TextProperty autoglMessage =
      new TextProperty("AutoGL Text", "glhf", this.autoglEnabled::getValue);
  public final IntProperty autoglDelay =
      new IntProperty("AutoGL Send at (s)", 5, 1, 15, this.autoglEnabled::getValue);

  private static final String[] GAME_END_MESSAGES = {
    "1st Killer -",
    "1st Place -",
    "Winner:",
    "- Damage Dealt -",
    "Winning Team -",
    "1st -",
    "Winners:",
    "Winning Team:",
    " won the game!",
    "Top Seeker:",
    "1st Place:",
    "Last team standing!",
    "Winner #1 (",
    "Top Survivors",
    "Winners -",
    "Sumo Duel -",
    "WINNER",
    "1st Killer"
  };

  private boolean activated = false;
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  public AutoGG() {
    super("AutoGG", false);
  }

  @Override
  public void onEnabled() {
    super.onEnabled();
    activated = false;
  }

  @Override
  public void onDisabled() {
    super.onDisabled();
    activated = false;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;

    if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S02PacketChat) {
      S02PacketChat packet = (S02PacketChat) event.getPacket();
      String msg = packet.getChatComponent().getUnformattedText();

      if (this.sendFirstMessage.getValue()
          && Arrays.stream(GAME_END_MESSAGES).anyMatch(msg::contains)
          && !msg.contains(":")
          && !activated) {
        int firstDelay = this.autoggDelay.getValue();
        int secondDelay = this.autoggSecondDelay.getValue();

        if (!this.firstMessage.getValue().isEmpty()) {
          executor.schedule(
              () -> ChatUtil.sendRaw("/ac " + this.firstMessage.getValue()),
              firstDelay,
              TimeUnit.MILLISECONDS);
        }

        if (this.sendSecondMessage.getValue() && !this.secondMessage.getValue().isEmpty()) {
          executor.schedule(
              () -> ChatUtil.sendRaw("/ac " + this.secondMessage.getValue()),
              secondDelay,
              TimeUnit.MILLISECONDS);
        }

        activated = true;
      }

      String end = (this.autoglDelay.getValue() == 1) ? " second!" : " seconds!";
      if (this.autoglEnabled.getValue()
          && msg.contains("The game starts in " + this.autoglDelay.getValue() + end)
          && !msg.contains(":")) {
        if (!this.autoglMessage.getValue().isEmpty()) {
          ChatUtil.sendRaw("/ac " + this.autoglMessage.getValue());
        }
      }
    }
  }
}
