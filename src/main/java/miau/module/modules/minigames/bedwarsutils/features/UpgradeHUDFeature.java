package miau.module.modules.minigames.bedwarsutils.features;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
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
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.EnumChatFormatting;

public class UpgradeHUDFeature implements BedwarsComponent {
  private final BedwarsUtils parent;

  public final BooleanProperty upgradeHud = new BooleanProperty("Upgrade HUD", false);
  public final DragProperty drag =
      new DragProperty("Upgrade HUD", new miau.util.vector.Vector2d(10, 10), true);
  public final BooleanProperty shortNames =
      new BooleanProperty("Short names", false, this.upgradeHud::getValue);
  public final FloatProperty scale =
      new FloatProperty("Scale", 1.0f, 0.5f, 1.5f, this.upgradeHud::getValue);
  public final BooleanProperty showSharpness =
      new BooleanProperty("Show Sharpness", true, this.upgradeHud::getValue);
  public final BooleanProperty showProtection =
      new BooleanProperty("Show Protection", true, this.upgradeHud::getValue);
  public final BooleanProperty showTraps =
      new BooleanProperty("Show Traps", true, this.upgradeHud::getValue);
  public final BooleanProperty showFeatherFalling =
      new BooleanProperty("Show Feather Falling", true, this.upgradeHud::getValue);
  public final BooleanProperty showHealPool =
      new BooleanProperty("Show Heal Pool", true, this.upgradeHud::getValue);
  public final BooleanProperty showForge =
      new BooleanProperty("Show Forge", true, this.upgradeHud::getValue);

  private final Queue<String> TRAP_QUEUE = new ArrayDeque<>();
  private final String FALSE_ICON = EnumChatFormatting.RED + "✗";
  private int sharpnessLevel = 0;
  private int sharpnessLevelCached = 0;
  private int protectionLevel = 0;
  private int protectionLevelCached = 0;
  private String trapName = "";
  private String trapNameCached = "";
  private int featherFallingLevel = 0;
  private int featherFallingLevelCached = 0;
  private boolean healPoolEnabled = false;
  private boolean healPoolEnabledCached = false;
  private String forgeLevel = "";
  private String forgeLevelCached = "";

  public UpgradeHUDFeature(BedwarsUtils parent) {
    this.parent = parent;
  }

  @Override
  public List<Property<?>> getProperties() {
    List<Property<?>> props = new ArrayList<>();
    props.add(upgradeHud);
    props.add(drag);
    props.add(shortNames);
    props.add(scale);
    props.add(showSharpness);
    props.add(showProtection);
    props.add(showTraps);
    props.add(showFeatherFalling);
    props.add(showHealPool);
    props.add(showForge);
    return props;
  }

  @Override
  public void onReset() {
    sharpnessLevel = 0;
    sharpnessLevelCached = 0;
    protectionLevel = 0;
    protectionLevelCached = 0;
    trapName = "";
    trapNameCached = "";
    featherFallingLevel = 0;
    featherFallingLevelCached = 0;
    healPoolEnabled = false;
    healPoolEnabledCached = false;
    forgeLevel = "";
    forgeLevelCached = "";
    TRAP_QUEUE.clear();
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S02PacketChat) {
      S02PacketChat packet = (S02PacketChat) event.getPacket();
      String msg = packet.getChatComponent().getUnformattedText();

      if (msg.equals("You will respawn because you still have a bed!")) {
        sharpnessLevel = sharpnessLevelCached;
        protectionLevel = protectionLevelCached;
        trapName = trapNameCached;
        featherFallingLevel = featherFallingLevelCached;
        healPoolEnabled = healPoolEnabledCached;
        forgeLevel = forgeLevelCached;
      }

      if (msg.contains("purchased") && !msg.contains(":")) {
        if (msg.contains("Sharpened Swords")) {
          if (msg.contains("II")) {
            sharpnessLevel = 2;
            sharpnessLevelCached = 2;
          } else {
            sharpnessLevel = 1;
            sharpnessLevelCached = 1;
          }
        }
        if (msg.contains("Reinforced Armor")) {
          if (msg.contains("IV")) {
            protectionLevel = 4;
            protectionLevelCached = 4;
          } else if (msg.contains("III")) {
            protectionLevel = 3;
            protectionLevelCached = 3;
          } else if (msg.contains("II")) {
            protectionLevel = 2;
            protectionLevelCached = 2;
          } else if (msg.contains("I")) {
            protectionLevel = 1;
            protectionLevelCached = 1;
          }
        }
        if (msg.contains("Trap")) {
          if (msg.contains("Miner Fatigue")) {
            addTrap("Miner Fatigue");
          } else if (msg.contains("Blindness")) {
            addTrap("Blindness");
          } else if (msg.contains("Reveal")) {
            addTrap("Reveal");
          } else if (msg.contains("Counter-Offensive")) {
            addTrap("Counter-Offensive");
          }
        }
        if (msg.contains("Cushioned Boots")) {
          if (msg.contains("II")) {
            featherFallingLevel = 2;
            featherFallingLevelCached = 2;
          } else if (msg.contains("I")) {
            featherFallingLevel = 1;
            featherFallingLevelCached = 1;
          }
        }
        if (msg.contains("Heal Pool")) {
          healPoolEnabled = true;
          healPoolEnabledCached = true;
        }
        if (msg.contains("Forge")) {
          if (msg.contains("Iron")) {
            forgeLevel = "Iron";
            forgeLevelCached = "Iron";
          } else if (msg.contains("Golden")) {
            forgeLevel = "Golden";
            forgeLevelCached = "Golden";
          } else if (msg.contains("Emerald")) {
            forgeLevel = "Emerald";
            forgeLevelCached = "Emerald";
          } else if (msg.contains("Molten")) {
            forgeLevel = "Molten";
            forgeLevelCached = "Molten";
          }
        }
      }

      if (msg.contains("Trap was set off!") || msg.contains("Your Bed was destroyed")) {
        trapName = TRAP_QUEUE.poll();
        trapNameCached = trapName;
        if (trapName == null) {
          trapName = "";
          trapNameCached = "";
        }
      }
    }
  }

  private void addTrap(String trap) {
    if (trapName.isEmpty()) {
      trapName = trap;
      trapNameCached = trap;
      return;
    }
    TRAP_QUEUE.offer(trap);
  }

  private String getForgeLevel(String forgeLvl) {
    if (forgeLvl.equals("Iron")) return EnumChatFormatting.GRAY + forgeLvl;
    if (forgeLvl.equals("Golden")) return EnumChatFormatting.GOLD + forgeLvl;
    if (forgeLvl.equals("Emerald")) return EnumChatFormatting.DARK_GREEN + forgeLvl;
    if (forgeLvl.equals("Molten")) return EnumChatFormatting.DARK_RED + forgeLvl;
    return forgeLvl;
  }

  private String formatUpgradeName(String upgradeName) {
    if (this.shortNames.getValue()) {
      if (upgradeName.equals("Sharpness: ")) return "Sharp: ";
      if (upgradeName.equals("Protection: ")) return "Prot: ";
      if (upgradeName.equals("Feather Falling: ")) return "Feather: ";
      if (upgradeName.equals("Heal Pool: ")) return "Heal: ";
    }
    return upgradeName;
  }

  private List<String> getDisplayString() {
    List<String> lines = new ArrayList<>();
    if (this.showSharpness.getValue())
      lines.add(
          formatUpgradeName("Sharpness: ")
              + ((sharpnessLevel > 0)
                  ? (EnumChatFormatting.GREEN + String.valueOf(sharpnessLevel))
                  : FALSE_ICON));
    if (this.showProtection.getValue())
      lines.add(
          formatUpgradeName("Protection: ")
              + ((protectionLevel > 0)
                  ? (EnumChatFormatting.GREEN + String.valueOf(protectionLevel))
                  : FALSE_ICON));
    if (this.showTraps.getValue())
      lines.add(
          formatUpgradeName("Trap: ")
              + (trapName.isEmpty() ? FALSE_ICON : (EnumChatFormatting.GREEN + trapName)));
    if (this.showFeatherFalling.getValue())
      lines.add(
          formatUpgradeName("Feather Falling: ")
              + ((featherFallingLevel > 0)
                  ? (EnumChatFormatting.GREEN + String.valueOf(featherFallingLevel))
                  : FALSE_ICON));
    if (this.showHealPool.getValue())
      lines.add(
          formatUpgradeName("Heal Pool: ")
              + (healPoolEnabled ? (EnumChatFormatting.GREEN + "\u2713") : FALSE_ICON));
    if (this.showForge.getValue())
      lines.add(
          formatUpgradeName("Forge: ")
              + (forgeLevel.isEmpty() ? FALSE_ICON : getForgeLevel(forgeLevel)));
    return lines;
  }

  @Override
  public void onRender2D(Render2DEvent event) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (!this.upgradeHud.getValue()) return;

    Font font = FontRepository.getHudFont(18);

    List<String> lines = getDisplayString();
    lines.sort(
        Comparator.comparingInt(
            s ->
                font.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes((String) s))));
    java.util.Collections.reverse(lines);

    float x = (float) this.drag.position.x;
    float y = (float) this.drag.position.y;
    float sc = this.scale.getValue();

    GlStateManager.pushMatrix();
    GlStateManager.scale(sc, sc, sc);

    float maxWidth = 0;
    float startY = y;

    for (String line : lines) {
      font.drawWithShadow(line, x / sc, y / sc, -1);
      float w = font.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(line)) * sc;
      if (w > maxWidth) maxWidth = w;
      y += font.getFontHeight() * sc + 2 * sc;
    }
    this.drag.setScale(new miau.util.vector.Vector2d(maxWidth, y - startY));
    GlStateManager.popMatrix();
  }
}
