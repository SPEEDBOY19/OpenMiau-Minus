package miau.module.modules.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import miau.Miau;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.animation.Animation;
import miau.util.animation.Easing;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;

public class Scoreboard extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  /** Drag property — marked as structure=true so DragManager does NOT draw a border over it. */
  public final DragProperty drag = new DragProperty("Position", new Vector2d(0, 0), false, true);

  /** The actual computed card position (updated every frame). */
  public float defaultX = 0;

  public float defaultY = 0;

  public final IntProperty yOffset = new IntProperty("Y Offset", 0, -250, 250);
  public final BooleanProperty customFont = new BooleanProperty("Custom Font", false);
  public final BooleanProperty textShadow = new BooleanProperty("Text Shadow", true);
  public final BooleanProperty redNumbers = new BooleanProperty("Red Numbers", false);
  public final BooleanProperty shaders = new BooleanProperty("Shaders", false);

  // --- THÊM TÙY CHỌN LINK WEB & THỦ TỤC ĐỔI MÀU THEO THEME ---
  public final BooleanProperty changeWebLink = new BooleanProperty("Web Link", true);
  public final ModeProperty color = new ModeProperty("Color", 0, new String[] {"HUD", "CUSTOM"});
  public final ColorProperty customColor = new ColorProperty("Custom Color", new Color(59, 155, 240).getRGB());

  private static final String CLIENT_WEB_URL = "miauminus.netlify.app/";

  private final Animation autofitAnimation = new Animation(Easing.EASE_OUT_EXPO, 300);

  public Scoreboard() {
    super("Scoreboard", true, false);
  }

  public String getWebUrl() {
    return CLIENT_WEB_URL;
  }

  public int getThemeColor() {
    if (this.color.getValue() == 0) {
      try {
        HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
        if (hud != null) {
          return hud.getColor(System.currentTimeMillis()).getRGB();
        }
      } catch (Exception ignored) {}
      return new Color(59, 155, 240).getRGB();
    } else {
      try {
        Object val = customColor.getValue();
        if (val instanceof Color) {
          return ((Color) val).getRGB();
        } else if (val instanceof Number) {
          return ((Number) val).intValue();
        }
      } catch (Exception ignored) {}
      return new Color(59, 155, 240).getRGB();
    }
  }

  public boolean isServerIpLine(String line) {
    if (line == null) return false;
    String clean = EnumChatFormatting.getTextWithoutFormattingCodes(line).toLowerCase().trim();
    return clean.contains("www.") || clean.contains(".net") || clean.contains(".com") 
        || clean.contains(".org") || clean.contains(".io") || clean.contains("server")
        || clean.contains("play.") || clean.contains("mc.") || clean.contains("ccbluex");
  }

  public String formatScoreboardLine(String originalText, boolean isLastLine) {
    if (this.changeWebLink.getValue() && isLastLine) {
      return CLIENT_WEB_URL;
    }
    return originalText;
  }

  public int getLineColor(String originalText, boolean isLastLine, int defaultColor) {
    if (this.changeWebLink.getValue() && isLastLine) {
      return getThemeColor();
    }
    return defaultColor;
  }

  public void updateBounds(ScaledResolution scaledRes) {
    net.minecraft.scoreboard.Scoreboard sb = null;
    ScoreObjective objective = null;
    if (mc.theWorld != null) {
      sb = mc.theWorld.getScoreboard();
      if (sb != null) {
        objective = sb.getObjectiveInDisplaySlot(1);
      }
    }

    int size;
    int maxWidth;
    if (objective != null && sb != null) {
      Collection<Score> collection = sb.getSortedScores(objective);
      List<Score> list = new ArrayList<>();
      for (Score score : collection) {
        if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
          list.add(score);
        }
      }
      if (list.size() > 15) {
        list = list.subList(list.size() - 15, list.size());
      }
      size = list.size();
      maxWidth = mc.fontRendererObj.getStringWidth(objective.getDisplayName());
      for (Score score : list) {
        ScorePlayerTeam team = sb.getPlayersTeam(score.getPlayerName());
        String name =
            ScorePlayerTeam.formatPlayerName(team, score.getPlayerName())
                + ": "
                + score.getScorePoints();
        maxWidth = Math.max(maxWidth, mc.fontRendererObj.getStringWidth(name));
      }
    } else {
      size = 5;
      maxWidth = 80;
    }

    if (this.changeWebLink.getValue()) {
      maxWidth = Math.max(maxWidth, mc.fontRendererObj.getStringWidth(CLIENT_WEB_URL));
    }

    int padding = 8;
    int width = maxWidth + padding + 4;
    int height = size * mc.fontRendererObj.FONT_HEIGHT + 14;

    float baseX = scaledRes.getScaledWidth() - width - 2;
    float baseY = scaledRes.getScaledHeight() / 2 - height / 3 + yOffset.getValue();

    float autofitOffset = 0;
    HUD hud = (HUD) Miau.moduleManager.getModule(HUD.class);
    if (hud != null && hud.isEnabled() && hud.posX.getValue() == 1) {
      float moduleListHeight = hud.getModuleListHeight();
      if (moduleListHeight > 0) {
        float hudStartY;
        if (hud.posY.getValue() == 0) {
          hudStartY = hud.offsetY.getValue();
          if (hud.showWatermark.getValue()) {
            hudStartY += hud.getFont().getFontHeight() + 6.0F;
          }
        } else {
          hudStartY = scaledRes.getScaledHeight() - hud.offsetY.getValue() - moduleListHeight;
        }
        float hudBottom = hudStartY + moduleListHeight;
        if (hudBottom > baseY) {
          autofitOffset = hudBottom - baseY + 4;
        }
      }
    }

    this.defaultX = baseX;
    autofitAnimation.run(baseY + autofitOffset);
    this.defaultY = autofitAnimation.getValue();


    this.drag.position.x = baseX;
    this.drag.position.y = this.defaultY;
    this.drag.targetPosition.x = baseX;
    this.drag.targetPosition.y = this.defaultY;
    this.drag.scale.x = width;
    this.drag.scale.y = height;
  }
}
