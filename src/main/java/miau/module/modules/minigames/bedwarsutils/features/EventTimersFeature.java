package miau.module.modules.minigames.bedwarsutils.features;

import java.util.ArrayList;
import java.util.List;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.types.EventType;
import miau.module.modules.minigames.BedwarsUtils;
import miau.module.modules.minigames.bedwarsutils.BedwarsComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.FloatProperty;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.EnumChatFormatting;

public class EventTimersFeature implements BedwarsComponent {
  private final BedwarsUtils parent;

  public final BooleanProperty eventTimers = new BooleanProperty("Event Timers", false);
  public final DragProperty eventDrag =
      new DragProperty("Event Timers", new miau.util.vector.Vector2d(10, 60), true);
  public final FloatProperty eventScale =
      new FloatProperty("Event Scale", 1.0f, 0.5f, 1.5f, this.eventTimers::getValue);
  public final BooleanProperty eventTime =
      new BooleanProperty("Events Enabled", true, this.eventTimers::getValue);
  public final BooleanProperty onlyNext =
      new BooleanProperty("Show next events only", false, this.eventTimers::getValue);
  public final BooleanProperty romanNumerals =
      new BooleanProperty("Roman numerals", false, this.eventTimers::getValue);
  public final BooleanProperty eventDynamicColor =
      new BooleanProperty("Dynamic color", false, this.eventTimers::getValue);
  public final BooleanProperty diamondTimer =
      new BooleanProperty("Diamond Timer", true, this.eventTimers::getValue);
  public final BooleanProperty emeraldTimer =
      new BooleanProperty("Emerald Timer", true, this.eventTimers::getValue);
  public final BooleanProperty bedGoneTimer =
      new BooleanProperty("Bed Gone Timer", true, this.eventTimers::getValue);
  public final BooleanProperty suddenDeathTimer =
      new BooleanProperty("Sudden Death Timer", true, this.eventTimers::getValue);
  public final BooleanProperty gameEndTimer =
      new BooleanProperty("Game End Timer", true, this.eventTimers::getValue);
  public final BooleanProperty emeraldTime =
      new BooleanProperty("Emeralds Enabled", true, this.eventTimers::getValue);
  public final DragProperty emeraldDrag =
      new DragProperty("Emerald Timers", new miau.util.vector.Vector2d(10, 110), true);
  public final BooleanProperty emeraldDynamicColor =
      new BooleanProperty("Emerald Dynamic color", true, this.eventTimers::getValue);

  private static final EmeraldEntry EIGHT_TEAMS_MODE_DATA = new EmeraldEntry(65, 50, 35, 4);
  private static final EmeraldEntry FOUR_TEAMS_MODE_DATA = new EmeraldEntry(55, 40, 27, 2);
  private static final ItemStack EMERALD_ICON = new ItemStack(Items.emerald);
  private long gameStartTime = 0L;
  private boolean gameStarted = false;
  private EmeraldEntry currentModeData = FOUR_TEAMS_MODE_DATA;

  public EventTimersFeature(BedwarsUtils parent) {
    this.parent = parent;
  }

  @Override
  public List<Property<?>> getProperties() {
    List<Property<?>> props = new ArrayList<>();
    props.add(eventTimers);
    props.add(eventDrag);
    props.add(eventScale);
    props.add(eventTime);
    props.add(onlyNext);
    props.add(romanNumerals);
    props.add(eventDynamicColor);
    props.add(diamondTimer);
    props.add(emeraldTimer);
    props.add(bedGoneTimer);
    props.add(suddenDeathTimer);
    props.add(gameEndTimer);
    props.add(emeraldTime);
    props.add(emeraldDrag);
    props.add(emeraldDynamicColor);
    return props;
  }

  @Override
  public void onReset() {
    gameStarted = false;
    gameStartTime = 0L;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S02PacketChat) {
      S02PacketChat packet = (S02PacketChat) event.getPacket();
      String formattedMsg = packet.getChatComponent().getFormattedText();

      if (formattedMsg.contains("§e§lProtect your bed and destroy the enemy bed")
          || formattedMsg.contains("§e§lDestroy the enemy bed and then eliminate them")) {
        gameStartTime = System.currentTimeMillis();
        gameStarted = true;
        if (formattedMsg.contains("Protect your bed")) {
          currentModeData = EIGHT_TEAMS_MODE_DATA;
        } else {
          currentModeData = FOUR_TEAMS_MODE_DATA;
        }
      }
    }
  }

  private void renderItemIcon(Minecraft mc, ItemStack stack, float x, float y, float scale) {
    if (stack == null) return;
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0.0F);
    GlStateManager.scale(scale, scale, scale);
    GlStateManager.enableRescaleNormal();
    RenderHelper.enableGUIStandardItemLighting();
    mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
    mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack, 0, 0, null);
    RenderHelper.disableStandardItemLighting();
    GlStateManager.disableRescaleNormal();
    GlStateManager.popMatrix();
  }

  private String formatTime(int seconds) {
    int m = seconds / 60;
    int s = seconds % 60;
    return String.format("%02d:%02d", m, s);
  }

  private boolean shouldShow(EventTypeTimer type) {
    switch (type) {
      case DIAMOND:
        return this.diamondTimer.getValue();
      case EMERALD:
        return this.emeraldTimer.getValue();
      case BED_GONE:
        return this.bedGoneTimer.getValue();
      case SUDDEN_DEATH:
        return this.suddenDeathTimer.getValue();
      case GAME_END:
        return this.gameEndTimer.getValue();
    }
    return true;
  }

  @Override
  public void onRender2D(Render2DEvent event) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (this.eventTimers.getValue() && gameStarted) {
      long now = System.currentTimeMillis();
      int elapsedSeconds = (int) Math.max(0L, (now - gameStartTime) / 1000L);
      Font font = FontRepository.getHudFont(18);

      if (this.eventTime.getValue()) {
        float x = (float) this.eventDrag.position.x;
        float y = (float) this.eventDrag.position.y;
        float sc = this.eventScale.getValue();
        float maxWidth = 0;
        float startY = y;
        boolean diamondShown = false;
        boolean emeraldShown = false;
        int shown = 0;

        String diamond2 =
            (this.eventDynamicColor.getValue() ? EnumChatFormatting.AQUA : EnumChatFormatting.WHITE)
                + "Diamond "
                + EnumChatFormatting.WHITE
                + (this.romanNumerals.getValue() ? "II" : "2");
        String diamond3 =
            (this.eventDynamicColor.getValue() ? EnumChatFormatting.AQUA : EnumChatFormatting.WHITE)
                + "Diamond "
                + EnumChatFormatting.WHITE
                + (this.romanNumerals.getValue() ? "III" : "3");
        String emerald2 =
            (this.eventDynamicColor.getValue()
                    ? EnumChatFormatting.DARK_GREEN
                    : EnumChatFormatting.WHITE)
                + "Emerald "
                + EnumChatFormatting.WHITE
                + (this.romanNumerals.getValue() ? "II" : "2");
        String emerald3 =
            (this.eventDynamicColor.getValue()
                    ? EnumChatFormatting.DARK_GREEN
                    : EnumChatFormatting.WHITE)
                + "Emerald "
                + EnumChatFormatting.WHITE
                + (this.romanNumerals.getValue() ? "III" : "3");
        String bedGone =
            (this.eventDynamicColor.getValue() ? EnumChatFormatting.GOLD : EnumChatFormatting.WHITE)
                + "Bed Gone";
        String suddenDeath =
            (this.eventDynamicColor.getValue()
                    ? EnumChatFormatting.DARK_PURPLE
                    : EnumChatFormatting.WHITE)
                + "Sudden Death";
        String gameEnd =
            (this.eventDynamicColor.getValue() ? EnumChatFormatting.RED : EnumChatFormatting.WHITE)
                + "Game End";

        EventEntry[] schedule = {
          new EventEntry(new ItemStack(Items.diamond), diamond2, 360, EventTypeTimer.DIAMOND),
          new EventEntry(new ItemStack(Items.emerald), emerald2, 720, EventTypeTimer.EMERALD),
          new EventEntry(new ItemStack(Items.diamond), diamond3, 1080, EventTypeTimer.DIAMOND),
          new EventEntry(new ItemStack(Items.emerald), emerald3, 1440, EventTypeTimer.EMERALD),
          new EventEntry(new ItemStack(Blocks.bed), bedGone, 1800, EventTypeTimer.BED_GONE),
          new EventEntry(
              new ItemStack(Blocks.dragon_egg), suddenDeath, 2400, EventTypeTimer.SUDDEN_DEATH),
          new EventEntry(new ItemStack(Blocks.portal), gameEnd, 3000, EventTypeTimer.GAME_END)
        };

        for (EventEntry entry : schedule) {
          if (!shouldShow(entry.type)) continue;
          int remainingSeconds = entry.targetSeconds - elapsedSeconds;
          if (remainingSeconds <= 0) continue;
          if (entry.type == EventTypeTimer.DIAMOND) {
            if (diamondShown) continue;
            diamondShown = true;
          }
          if (entry.type == EventTypeTimer.EMERALD) {
            if (emeraldShown) continue;
            emeraldShown = true;
          }

          renderItemIcon(mc, entry.icon, x, y, sc);
          font.drawWithShadow(entry.title, x + 18.0F * sc, y, -1);
          font.drawWithShadow(
              EnumChatFormatting.GRAY + formatTime(remainingSeconds),
              x + 18.0F * sc,
              y + (font.getFontHeight() + 2) * sc,
              -1);

          y +=
              Math.max((int) ((font.getFontHeight() * 2 + 4) * sc), (int) (16.0F * sc))
                  + (int) (4.0F * sc);

          float w1 =
              font.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(entry.title))
                      * sc
                  + 18.0F * sc;
          float w2 =
              font.getStringWidth(
                          EnumChatFormatting.getTextWithoutFormattingCodes(
                              EnumChatFormatting.GRAY + formatTime(remainingSeconds)))
                      * sc
                  + 18.0F * sc;
          if (w1 > maxWidth) maxWidth = w1;
          if (w2 > maxWidth) maxWidth = w2;

          if (this.onlyNext.getValue() && ++shown >= 2) {
            break;
          }
        }
        this.eventDrag.setScale(new miau.util.vector.Vector2d(maxWidth, y - startY));
      }

      if (this.emeraldTime.getValue()) {
        EmeraldEntry modeData = currentModeData;
        int nextSpawnTime = 31;
        int totalEmeralds = 0;
        int spawnTime = 31;

        while (elapsedSeconds >= nextSpawnTime) {
          nextSpawnTime += modeData.getSpawnInterval(nextSpawnTime);
        }
        while (elapsedSeconds >= spawnTime) {
          totalEmeralds += modeData.emeraldsPerSpawn;
          spawnTime += modeData.getSpawnInterval(spawnTime);
        }

        int nextEmeraldSpawn = Math.max(0, nextSpawnTime - elapsedSeconds);

        float x = (float) this.emeraldDrag.position.x;
        float y = (float) this.emeraldDrag.position.y;
        float sc = this.eventScale.getValue();

        EnumChatFormatting timeColor =
            !this.emeraldDynamicColor.getValue()
                ? EnumChatFormatting.GRAY
                : ((nextEmeraldSpawn < 3)
                    ? EnumChatFormatting.DARK_RED
                    : ((nextEmeraldSpawn < 5)
                        ? EnumChatFormatting.RED
                        : ((nextEmeraldSpawn < 9)
                            ? EnumChatFormatting.GOLD
                            : ((nextEmeraldSpawn < 12)
                                ? EnumChatFormatting.YELLOW
                                : EnumChatFormatting.DARK_GREEN))));

        String mainText =
            "Next Emerald: " + timeColor + nextEmeraldSpawn + EnumChatFormatting.GRAY + " s";
        String secondText =
            EnumChatFormatting.WHITE + "Total: " + EnumChatFormatting.DARK_GREEN + totalEmeralds;

        int textBlockHeight = (int) ((font.getFontHeight() * 2 + 2) * sc);
        int iconSize = (int) (16.0F * sc);
        float iconY = y + Math.max(0.0F, (textBlockHeight - iconSize) / 2.0F);

        renderItemIcon(mc, EMERALD_ICON, x, iconY, sc);
        font.drawWithShadow(mainText, x + 18.0F * sc, y, -1);
        font.drawWithShadow(secondText, x + 18.0F * sc, y + (font.getFontHeight() + 2) * sc, -1);

        float w1 =
            font.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(mainText)) * sc
                + 18.0F * sc;
        float w2 =
            font.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(secondText)) * sc
                + 18.0F * sc;
        this.emeraldDrag.setScale(
            new miau.util.vector.Vector2d(Math.max(w1, w2), (font.getFontHeight() * 2 + 4) * sc));
      }
    }
  }

  private enum EventTypeTimer {
    DIAMOND,
    EMERALD,
    BED_GONE,
    SUDDEN_DEATH,
    GAME_END;
  }

  private static class EventEntry {
    final ItemStack icon;
    final String title;
    final int targetSeconds;
    final EventTypeTimer type;

    private EventEntry(ItemStack icon, String title, int targetSeconds, EventTypeTimer type) {
      this.icon = icon;
      this.title = title;
      this.targetSeconds = targetSeconds;
      this.type = type;
    }
  }

  private static class EmeraldEntry {
    final int tierOneInterval;
    final int tierTwoInterval;
    final int tierThreeInterval;
    final int emeraldsPerSpawn;

    EmeraldEntry(
        int tierOneInterval, int tierTwoInterval, int tierThreeInterval, int emeraldsPerSpawn) {
      this.tierOneInterval = tierOneInterval;
      this.tierTwoInterval = tierTwoInterval;
      this.tierThreeInterval = tierThreeInterval;
      this.emeraldsPerSpawn = emeraldsPerSpawn;
    }

    int getSpawnInterval(int elapsedSeconds) {
      if (elapsedSeconds >= 1440) return this.tierThreeInterval;
      if (elapsedSeconds >= 720) return this.tierTwoInterval;
      return this.tierOneInterval;
    }
  }
}
