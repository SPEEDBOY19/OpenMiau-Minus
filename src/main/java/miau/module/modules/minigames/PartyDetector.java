package miau.module.modules.minigames;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.client.ChatUtil;
import miau.util.client.SoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;

/**
 * @author meowtils
 */
public class PartyDetector extends Module {
  public final BooleanProperty sound = new BooleanProperty("Ping sound", true);
  public final BooleanProperty showMissed = new BooleanProperty("Show missed players", true);
  public final BooleanProperty twos = new BooleanProperty("Bedwars 2s", true);
  public final BooleanProperty threes = new BooleanProperty("Bedwars 3s", true);
  public final BooleanProperty foursNormal = new BooleanProperty("Bedwars 4s", true);
  public final BooleanProperty foursTwo = new BooleanProperty("Bedwars 4v4", true);

  private int playerCounter = 0;
  private long lastJoinTime = 0L;
  private boolean countingPlayers = false;
  private int missedCounter = 0;
  private boolean alertedMissed = false;
  private int tickCounter = 0;
  private boolean gameStarted = false;

  private final Set<EntityPlayer> knownPlayers = new HashSet<>();
  private final Minecraft mc = Minecraft.getMinecraft();

  public PartyDetector() {
    super("PartyDetector", false, true);
  }

  @Override
  public void onEnabled() {
    super.onEnabled();
    onReset();
  }

  @Override
  public void onDisabled() {
    super.onDisabled();
  }

  private void onReset() {
    playerCounter = 0;
    lastJoinTime = 0L;
    countingPlayers = false;
    alertedMissed = false;
    missedCounter = 0;
    tickCounter = 0;
    gameStarted = false;
    knownPlayers.clear();
  }

  private int getBedwarsMode() {
    if (mc.theWorld == null || mc.theWorld.getScoreboard() == null) return 0;
    ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
    if (objective == null) return 0;

    String title = EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName());
    if (title == null || !title.contains("BED WARS")) return 0;

    for (ScorePlayerTeam team : mc.theWorld.getScoreboard().getTeams()) {
      String prefix = team.getColorPrefix() != null ? team.getColorPrefix() : "";
      String suffix = team.getColorSuffix() != null ? team.getColorSuffix() : "";
      String line = EnumChatFormatting.getTextWithoutFormattingCodes(prefix + suffix).toLowerCase();

      if (line.contains("4v4v4v4") && this.foursNormal.getValue()) return 4;
      if (line.contains("3v3v3v3") && this.threes.getValue()) return 3;
      if (line.contains("doubles") && this.twos.getValue()) return 2;
      if (line.contains("4v4") && this.foursTwo.getValue()) return 4;
    }
    return 0;
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() != EventType.POST) return;
    if (mc.thePlayer == null || mc.theWorld == null) {
      onReset();
      return;
    }

    int mode = getBedwarsMode();
    if (mode == 0) return;

    List<EntityPlayer> currentPlayers = mc.theWorld.playerEntities;

    for (EntityPlayer player : currentPlayers) {
      if (player != mc.thePlayer && !knownPlayers.contains(player)) {
        knownPlayers.add(player);

        long now = System.currentTimeMillis();
        if (!countingPlayers) {
          lastJoinTime = now;
        }

        if (now - lastJoinTime <= 1000L) {
          playerCounter++;
          countingPlayers = true;
        } else {
          countingPlayers = false;
          lastJoinTime = 0L;
          playerCounter = 0;
        }
      }
    }

    knownPlayers.retainAll(currentPlayers);

    if (playerCounter != 0 && playerCounter >= mode) {
      if (!gameStarted) {
        ChatUtil.sendFormatted(
            String.format(
                "%s%s: &cWarning: &e%d&f players joined! &8(&9Party&8)",
                Miau.clientName, this.getName(), mode));
        if (this.sound.getValue()) {
          SoundUtil.playSound("note.pling");
        }
      }
      playerCounter = 0;
      lastJoinTime = 0L;
      countingPlayers = false;
    }

    if (this.showMissed.getValue() && !alertedMissed && !gameStarted) {
      tickCounter++;
      if (tickCounter >= 10) {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
          if (player != null && player != mc.thePlayer && player.getUniqueID().version() == 2) {
            missedCounter++;
          }
        }

        if (missedCounter != 0) {
          ChatUtil.sendFormatted(
              String.format(
                  "%s%s: Missed players: &e%d", Miau.clientName, this.getName(), missedCounter));
        }

        alertedMissed = true;
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S02PacketChat) {
      S02PacketChat packet = (S02PacketChat) event.getPacket();
      String msg = packet.getChatComponent().getUnformattedText();

      if (msg.contains("The game starts in 1 second!")) {
        gameStarted = true;
      }
    }
  }
}
