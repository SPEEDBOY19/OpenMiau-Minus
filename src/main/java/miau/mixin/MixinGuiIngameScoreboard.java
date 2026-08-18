package miau.mixin;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import miau.Miau;
import miau.module.modules.render.HUD;
import miau.module.modules.render.Scoreboard;
import miau.util.shader.BlurUtils;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngameScoreboard {

  @Shadow
  protected abstract void renderScoreboard(ScoreObjective objective, ScaledResolution scaledRes);

  @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
  private void onRenderScoreboardPre(
      ScoreObjective objective, ScaledResolution scaledRes, CallbackInfo ci) {
    if (Miau.moduleManager == null) return;

    Scoreboard scoreboardMod = (Scoreboard) Miau.moduleManager.getModule(Scoreboard.class);
    if (scoreboardMod == null || !scoreboardMod.isEnabled()) {
      return;
    }

    // Recalculate bounds every frame
    scoreboardMod.updateBounds(scaledRes);

    // Render shaders (bloom + blur) when enabled
    if (scoreboardMod.shaders.getValue()) {
      float cardX = scoreboardMod.defaultX;
      float cardY = scoreboardMod.defaultY;
      float cardWidth = (float) scoreboardMod.drag.scale.x;
      float cardHeight = (float) scoreboardMod.drag.scale.y;

      HUD hud = (HUD) Miau.moduleManager.getModule(HUD.class);
      boolean hudShaders = hud != null && hud.isEnabled() && hud.shaders.getValue();

      if (hudShaders) {
        // Bloom pass — glow border
        BlurUtils.prepareBloom();
        RoundedUtils.drawRound(
            cardX - 1,
            cardY - 1,
            cardWidth + 2,
            cardHeight + 2,
            2f,
            true,
            new Color(81, 99, 149, 80));
        BlurUtils.bloomEnd(3, 2f);

        // Blur pass — blur background
        BlurUtils.prepareBlur();
        RoundedUtils.drawRound(
            cardX, cardY, cardWidth, cardHeight, 2f, true, new Color(0, 0, 0, 150));
        BlurUtils.blurEnd(2, 3f);
      }
    }

    renderOpalScoreboard(objective, scaledRes, scoreboardMod, false);
    ci.cancel();
  }

  /** Renders the opal-style scoreboard card with text. */
  private void renderOpalScoreboard(
      ScoreObjective objective,
      ScaledResolution scaledRes,
      Scoreboard scoreboardMod,
      boolean transparencyOnly) {
    net.minecraft.scoreboard.Scoreboard scoreboard = objective.getScoreboard();
    Collection<Score> collection = scoreboard.getSortedScores(objective);
    List<Score> list = new ArrayList<>();
    for (Score score : collection) {
      if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
        list.add(score);
      }
    }
    if (list.size() > 15) {
      list = list.subList(list.size() - 15, list.size());
    }

    float cardX = scoreboardMod.defaultX;
    float cardY = scoreboardMod.defaultY;
    float cardWidth = (float) scoreboardMod.drag.scale.x;
    float cardHeight = (float) scoreboardMod.drag.scale.y;
    float radius = 2.0f;

    boolean shadow = scoreboardMod.textShadow.getValue();

    // --- Draw card background (if not in transparency-only mode) ---
    if (!transparencyOnly) {
      RoundedUtils.drawRound(
          cardX, cardY, cardWidth, cardHeight, radius, new Color(0x80, 0x09, 0x09, 0x09));
    }

    // --- Title ---
    String title = objective.getDisplayName();
    float titleX = cardX + cardWidth / 2.0F - getStringWidth(title) / 2.0F;
    float titleY = cardY + 2.0F;
    drawString(title, titleX, titleY, -1, shadow);

    // --- Entries ---
    int lineCount = 0;
    int totalLines = list.size();
    for (Score score : list) {
      lineCount++;
      ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
      String playerName = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
      boolean isLastLine = (lineCount == totalLines);

      float entryY =
          cardY + mc.fontRendererObj.FONT_HEIGHT + 2 + lineCount * mc.fontRendererObj.FONT_HEIGHT;
      float leftPad = cardX + 4.0F;

      // Only replace last line with website when toggle is enabled
      String formattedName = scoreboardMod.formatScoreboardLine(playerName, isLastLine);
      int nameColor = scoreboardMod.getLineColor(playerName, isLastLine, -1);
      drawString(formattedName, leftPad, entryY, nameColor, shadow);

      String scoreText;
      if (scoreboardMod.redNumbers.getValue()) {
        scoreText = EnumChatFormatting.RED + "" + score.getScorePoints();
      } else {
        scoreText = " " + score.getScorePoints();
      }
      float rightEdge = cardX + cardWidth - 4.0F;
      
      // Only replace last line score with website when toggle is enabled
      String formattedScore = scoreboardMod.formatScoreboardLine(scoreText, isLastLine);
      int scoreColor = scoreboardMod.getLineColor(scoreText, isLastLine, -1);
      drawString(formattedScore, rightEdge - getStringWidth(formattedScore), entryY, scoreColor, shadow);
    }
  }

  private static final Minecraft mc = Minecraft.getMinecraft();

  private int getStringWidth(String text) {
    return mc.fontRendererObj.getStringWidth(text);
  }

  private void drawString(String text, double x, double y, int color, boolean shadow) {
    if (shadow) {
      mc.fontRendererObj.drawStringWithShadow(text, (float) x, (float) y, color);
    } else {
      mc.fontRendererObj.drawString(text, (int) x, (int) y, color);
    }
  }
}
