package miau.util.network;

import java.util.ArrayList;
import java.util.stream.Collectors;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

public class ServerUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static ArrayList<String> getScoreboardLines() {
    if (ServerUtil.mc.theWorld == null) {
      return new ArrayList<>();
    }
    Scoreboard scoreboard = ServerUtil.mc.theWorld.getScoreboard();
    if (scoreboard == null) {
      return new ArrayList<>();
    }
    ScoreObjective scoreObjective = scoreboard.getObjectiveInDisplaySlot(1);
    if (scoreObjective == null) {
      return new ArrayList<>();
    }
    return (ArrayList<String>)
        scoreboard.getSortedScores(scoreObjective).stream()
            .map(
                score ->
                    ScorePlayerTeam.formatPlayerName(
                        scoreboard.getPlayersTeam(score.getPlayerName()), score.getPlayerName()))
            .collect(Collectors.toList());
  }

  public static boolean isHypixel() {
    ArrayList<String> arrayList = ServerUtil.getScoreboardLines();
    if (arrayList.isEmpty()) return false;
    if (arrayList.get(0).equals("§ewww.hypixel.ne🎂§et")) return true;
    return arrayList.get(0).equals("§ewww.hypixel.ne§g§et");
  }

  public static boolean hasPlayerCountInfo() {
    for (String s : ServerUtil.getScoreboardLines()) {
      if (!s.matches(".*Players: §a\\d+/\\d+.*")) continue;
      return true;
    }
    return false;
  }
}
