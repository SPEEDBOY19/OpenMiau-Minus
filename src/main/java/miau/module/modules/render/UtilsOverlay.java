package miau.module.modules.render;

import java.awt.Color;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;

public class UtilsOverlay extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty lowercase = new BooleanProperty("Lowercase", false);
  public final BooleanProperty dropShadow = new BooleanProperty("Drop shadow", true);
  public final ModeProperty theme =
      new ModeProperty(
          "Theme",
          0,
          new String[] {
            "Cherry", "Cotton", "Flare", "Flower", "Gold", "Grayscale", "Royal", "Sky", "Vine"
          });

  private boolean hasSharp = false;
  private boolean hasProt = false;
  private boolean hasTrap = false;
  private boolean isLowercase = false;
  private String trapType = "";
  private int protLevel = 0;

  public UtilsOverlay() {
    super("UtilsOverlay", false);
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S02PacketChat) {
      S02PacketChat packet = (S02PacketChat) event.getPacket();
      String msg = packet.getChatComponent().getUnformattedText();

      if (msg.contains("Reward Summary")
          || msg.contains("joined the lobby")
          || msg.contains("has joined (")) {
        hasSharp = false;
        hasProt = false;
        hasTrap = false;
        trapType = "";
        protLevel = 0;
      }

      if (msg.contains("was set off!")) {
        hasTrap = false;
        trapType = "";
      }

      if (msg.contains("purchased Sharpened")) {
        hasSharp = true;
      }
      if (msg.contains("purchased Reinforced Armor")) {
        hasProt = true;
        protLevel++;
      }
      if (msg.contains("purchased Miner Fatigue Trap")) {
        hasTrap = true;
        trapType = "Mining Fatigue";
      }
      if (msg.contains("purchased It's a trap")) {
        hasTrap = true;
        trapType = "Blindness+Slowness";
      }
      if (msg.contains("purchased Counter-Offensive Trap")) {
        hasTrap = true;
        trapType = "Counter-Offensive";
      }
      if (msg.contains("purchased Alarm Trap")) {
        hasTrap = true;
        trapType = "Alarm";
      }
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;

    isLowercase = this.lowercase.getValue();
    boolean isShadow = this.dropShadow.getValue();

    if (hasSharp) {
      String text = isLowercase ? "sharpness" : "Sharpness";
      mc.fontRendererObj.drawString(text, 15, 15, this.getChroma(), isShadow);
    }
    if (hasProt) {
      String text = isLowercase ? "protection " + protLevel : "Protection " + protLevel;
      mc.fontRendererObj.drawString(text, 15, 25, this.getChroma(), isShadow);
    }
    if (hasTrap) {
      String text =
          isLowercase ? "trap: " + trapType.toLowerCase() : "Trap: " + trapType;
      mc.fontRendererObj.drawString(text, 15, 35, this.getChroma(), isShadow);
    }
  }

  private int convert(Color color, Color color2, double n) {
    double n2 = 1.0 - n;
    return new Color(
            (int) (color.getRed() * n + color2.getRed() * n2),
            (int) (color.getGreen() * n + color2.getGreen() * n2),
            (int) (color.getBlue() * n + color2.getBlue() * n2))
        .getRGB();
  }

  private int getChroma() {
    int color1 = new Color(99, 249, 255).getRGB();
    int color2 = new Color(255, 104, 204).getRGB();
    double themeValue = this.theme.getValue();
    if (themeValue == 0.0) {
      color1 = new Color(255, 200, 200).getRGB();
      color2 = new Color(243, 58, 106).getRGB();
    } else if (themeValue == 1.0) {
      color1 = new Color(99, 249, 255).getRGB();
      color2 = new Color(255, 104, 204).getRGB();
    } else if (themeValue == 2.0) {
      color1 = new Color(231, 39, 24).getRGB();
      color2 = new Color(245, 173, 49).getRGB();
    } else if (themeValue == 3.0) {
      color1 = new Color(215, 166, 231).getRGB();
      color2 = new Color(211, 90, 232).getRGB();
    } else if (themeValue == 4.0) {
      color1 = new Color(255, 215, 0).getRGB();
      color2 = new Color(240, 159, 0).getRGB();
    } else if (themeValue == 5.0) {
      color1 = new Color(240, 240, 240).getRGB();
      color2 = new Color(110, 110, 110).getRGB();
    } else if (themeValue == 6.0) {
      color1 = new Color(125, 204, 241).getRGB();
      color2 = new Color(30, 71, 170).getRGB();
    } else if (themeValue == 7.0) {
      color1 = new Color(160, 230, 225).getRGB();
      color2 = new Color(15, 190, 220).getRGB();
    } else if (themeValue == 8.0) {
      color1 = new Color(17, 192, 45).getRGB();
      color2 = new Color(201, 234, 198).getRGB();
    }
    return convert(
        new Color(color1),
        new Color(color2),
        (Math.sin(System.currentTimeMillis() / 1.0E8 * 0.5 * 400000.0 + 20 * 0.550000011920929)
                + 1.0)
            * 0.5);
  }
}
