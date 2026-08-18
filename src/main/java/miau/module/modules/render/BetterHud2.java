package miau.module.modules.render;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;

public class BetterHud2 extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private String clientname = "Custom Profile 1";
  private String builduser = "capybara";
  private String buildNumber = "1000";
  private String buildVersion = "Custom";

  private final Map<String, Color> potionColorMapping = new HashMap<>();

  private int auratargetcheck = 0, auratargetsent = 0, enemykilledsent = 1;
  private float enemy_currenthealth = 0, enemy_previoushealth = 0, healthdifference = 0;
  private EntityLivingBase lastTarget;
  private String enemyname = "";
  private int misssentcheck = 0, hurttimecounter = 0;
  private int ping = 100;

  private static final Color gray = new Color(180, 180, 180);
  private static final Color white = new Color(255, 255, 255);
  private static final Color red = new Color(255, 0, 0);

  private int prevWRed = -1, prevWGreen = -1, prevWBlue = -1;
  private Color usercolor = new Color(100, 100, 255);
  private Color background = new Color(0, 0, 0, 70);

  public final ModeProperty watermarkStyle =
      new ModeProperty("Watermark Style", 1, new String[] {"None", "Default", "CSGO"});
  public final BooleanProperty extraInfoCSGO = new BooleanProperty("Show Extra Info in CSGO", false);
  public final ModeProperty clientName =
      new ModeProperty(
          "Client Name",
          0,
          new String[] {
            "Raven B4", "BlowsyWare", "Custom Profile 1", "Custom Profile 2",
            "Custom Profile 3", "Custom Profile 4", "Custom Profile 5"
          });
  public final BooleanProperty showBuildInfo = new BooleanProperty("Show Build Info [/buildinfo]", true);
  public final ModeProperty buildInfoVersion =
      new ModeProperty("Build Info Version", 0, new String[] {"Release", "Beta", "Alpha", "Development", "Custom"});
  public final BooleanProperty showFps = new BooleanProperty("Show FPS", true);
  public final BooleanProperty showBps = new BooleanProperty("Show BPS", true);
  public final BooleanProperty showPing = new BooleanProperty("Show Ping", true);
  public final BooleanProperty showPotionEffects = new BooleanProperty("Show Potion Effects", true);
  public final BooleanProperty showCoordinates = new BooleanProperty("Show Coordinates", true);
  public final BooleanProperty auraHitlog = new BooleanProperty("Aura Hitlog", true);
  public final IntProperty redValue = new IntProperty("R", 100, 0, 255);
  public final IntProperty greenValue = new IntProperty("G", 100, 0, 255);
  public final IntProperty blueValue = new IntProperty("B", 255, 0, 255);

  public BetterHud2() {
    super("BetterHud2", false, true);
    this.initializeColorMapping();
  }

  private void initializeColorMapping() {
    this.potionColorMapping.put("Speed", new Color(122, 181, 240));
    this.potionColorMapping.put("Slowness", new Color(105, 95, 89));
    this.potionColorMapping.put("Haste", new Color(219, 169, 81));
    this.potionColorMapping.put("Mining Fatigue", new Color(144, 212, 203));
    this.potionColorMapping.put("Strength", new Color(89, 12, 12));
    this.potionColorMapping.put("Jump Boost", new Color(134, 173, 173));
    this.potionColorMapping.put("Nausea", new Color(89, 120, 58));
    this.potionColorMapping.put("Regeneration", new Color(196, 73, 122));
    this.potionColorMapping.put("Resistance", new Color(85, 96, 99));
    this.potionColorMapping.put("Fire Resistance", new Color(196, 67, 16));
    this.potionColorMapping.put("Water Breathing", new Color(61, 109, 204));
    this.potionColorMapping.put("Invisibility", new Color(255, 255, 255));
    this.potionColorMapping.put("Blindness", new Color(59, 55, 54));
    this.potionColorMapping.put("Night Vision", new Color(19, 26, 69));
    this.potionColorMapping.put("Hunger", new Color(92, 62, 46));
    this.potionColorMapping.put("Weakness", new Color(89, 78, 72));
    this.potionColorMapping.put("Poison", new Color(104, 115, 61));
    this.potionColorMapping.put("Wither", new Color(0, 0, 0));
    this.potionColorMapping.put("Health Boost", new Color(219, 66, 66));
    this.potionColorMapping.put("Absorption", new Color(219, 173, 66));
    this.potionColorMapping.put("Saturation", new Color(125, 75, 31));
  }

  private String formatPotionName(String potionEffectName) {
    Map<String, String> nameMap = new HashMap<>();
    nameMap.put("potion.confusion", "Nausea");
    nameMap.put("potion.waterBreathing", "Water Breathing");
    nameMap.put("potion.jump", "Jump Boost");
    nameMap.put("potion.nightVision", "Night Vision");
    nameMap.put("potion.fireResistance", "Fire Resistance");
    nameMap.put("potion.moveSpeed", "Speed");
    nameMap.put("potion.digSlowDown", "Mining Fatigue");
    nameMap.put("potion.damageBoost", "Strength");
    nameMap.put("potion.healthBoost", "Health Boost");
    nameMap.put("potion.digSpeed", "Haste");

    String formattedName = nameMap.getOrDefault(potionEffectName, potionEffectName);
    formattedName = formattedName.replace("potion.", "");
    if (!formattedName.isEmpty()) {
      formattedName =
          Character.toUpperCase(formattedName.charAt(0)) + formattedName.substring(1);
    }
    return formattedName;
  }

  @EventTarget
  public void onRenderTick(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    int w_red = this.redValue.getValue();
    int w_green = this.greenValue.getValue();
    int w_blue = this.blueValue.getValue();

    if (w_red != this.prevWRed || w_green != this.prevWGreen || w_blue != this.prevWBlue) {
      this.usercolor = new Color(w_red, w_green, w_blue);
      this.background = new Color(0, 0, 0, 70);
      this.prevWRed = w_red;
      this.prevWGreen = w_green;
      this.prevWBlue = w_blue;
    }

    if (mc.currentScreen != null) {
      return;
    }

    ScaledResolution size = new ScaledResolution(mc);

    this.updatePing();

    String selectedBuildVersion = this.getSelectedBuildVersion();

    int watermarkStyle = this.watermarkStyle.getValue();

    if (watermarkStyle == 1) {
      this.drawDefaultWatermark(size);
    }

    if (watermarkStyle == 2) {
      String watermarkname = this.getWatermarkName();
      String watermarktext = watermarkname + " [" + selectedBuildVersion.toUpperCase() + "]";
      if (this.extraInfoCSGO.getValue()) {
        watermarktext =
            watermarkname
                + " ["
                + selectedBuildVersion.toUpperCase()
                + "] | "
                + this.builduser
                + " | ping: "
                + this.ping
                + "ms | version: 1.8.9";
      }
      mc.fontRendererObj.drawStringWithShadow(watermarktext, 10, 11, -1);
    }

    int margin = 3;
    int rightBaseX = size.getScaledWidth() - margin;
    int leftBaseX = 3;
    int lineHeight = mc.fontRendererObj.FONT_HEIGHT + 1;
    int baseY = size.getScaledHeight() - margin - lineHeight;

    if (this.showCoordinates.getValue()) {
      String coordsText =
          (int) mc.thePlayer.posX + ", " + (int) mc.thePlayer.posY + ", " + (int) mc.thePlayer.posZ;
      mc.fontRendererObj.drawStringWithShadow(coordsText, margin, baseY, -1);
      baseY -= lineHeight;
    }

    if (this.showBps.getValue()) {
      double blocksPerSecond = this.getPlayerSpeed() * 20;
      String bpsText = String.format("%.2f blocks/sec", blocksPerSecond);
      mc.fontRendererObj.drawStringWithShadow(bpsText, margin, baseY, -1);
      baseY -= lineHeight;
    }

    if (this.showFps.getValue()) {
      String fpsText = "FPS: " + Minecraft.getDebugFPS();
      mc.fontRendererObj.drawStringWithShadow(fpsText, margin, baseY, -1);
      baseY -= lineHeight;
    }

    int rightBaseY = size.getScaledHeight() - margin - lineHeight;

    if (this.showBuildInfo.getValue()) {
      String buildseparator = " - ";
      int buildnameWidth = mc.fontRendererObj.getStringWidth(selectedBuildVersion);
      int buildseparatorWidth = mc.fontRendererObj.getStringWidth(buildseparator);
      int buildnumberWidth = mc.fontRendererObj.getStringWidth(this.buildNumber);
      int builduserWidth = mc.fontRendererObj.getStringWidth(this.builduser);

      float userX = size.getScaledWidth() - margin - builduserWidth;
      float separator2X = userX - buildseparatorWidth;
      float numberX = separator2X - buildnumberWidth;
      float separator1X = numberX - buildseparatorWidth;
      float nameX = separator1X - buildnameWidth;

      mc.fontRendererObj.drawStringWithShadow(this.builduser, (int) userX, rightBaseY, this.gray.getRGB());
      mc.fontRendererObj.drawStringWithShadow(buildseparator, (int) separator2X, rightBaseY, this.gray.getRGB());
      mc.fontRendererObj.drawStringWithShadow(this.buildNumber, (int) numberX, rightBaseY, -1);
      mc.fontRendererObj.drawStringWithShadow(buildseparator, (int) separator1X, rightBaseY, this.gray.getRGB());
      mc.fontRendererObj.drawStringWithShadow(selectedBuildVersion, (int) nameX, rightBaseY, this.gray.getRGB());
      rightBaseY -= lineHeight;
    }

    if (this.showPing.getValue()) {
      String pingValue = this.ping + "ms";
      String pingText = "Ping: ";
      int pingValueWidth = mc.fontRendererObj.getStringWidth(pingValue);
      int pingTextWidth = mc.fontRendererObj.getStringWidth(pingText);
      float pingX = size.getScaledWidth() - margin - pingValueWidth;
      float pingTextX = pingX - pingTextWidth;
      mc.fontRendererObj.drawStringWithShadow(pingText, (int) pingTextX, rightBaseY, -1);
      mc.fontRendererObj.drawStringWithShadow(pingValue, (int) pingX, rightBaseY, this.gray.getRGB());
      rightBaseY -= lineHeight;
    }

    if (this.showPotionEffects.getValue()) {
      for (Object obj : mc.thePlayer.getActivePotionEffects()) {
        if (!(obj instanceof PotionEffect)) continue;
        PotionEffect effect = (PotionEffect) obj;
        String potionName = this.formatPotionName(effect.getEffectName());
        int amplifier = effect.getAmplifier();
        int durationTicks = effect.getDuration();
        int minutes = durationTicks / 1200;
        int seconds = (durationTicks / 20) % 60;
        String durationFormatted = String.format("%d:%02d", minutes, seconds);
        String amplifierText = amplifier > 0 ? " " + (amplifier + 1) : "";
        String fullEffectText = potionName + amplifierText + " - " + durationFormatted;
        int fullTextWidth = mc.fontRendererObj.getStringWidth(fullEffectText);
        Color effectColor =
            this.potionColorMapping.getOrDefault(potionName, new Color(255, 255, 255));
        mc.fontRendererObj.drawStringWithShadow(
            potionName,
            size.getScaledWidth() - margin - fullTextWidth,
            rightBaseY,
            effectColor.getRGB());
        int grayTextStartX =
            size.getScaledWidth() - margin - fullTextWidth
                + mc.fontRendererObj.getStringWidth(potionName);
        mc.fontRendererObj.drawStringWithShadow(
            amplifierText + " - " + durationFormatted,
            grayTextStartX,
            rightBaseY,
            this.gray.getRGB());
        rightBaseY -= lineHeight;
      }
    }

    this.renderAuraHitlog(size, watermarkStyle);
  }

  private void drawDefaultWatermark(ScaledResolution size) {
    int startX = 5;
    int clientNameIndex = this.clientName.getValue();
    String[] prefixes = {"R", "Blowsy", "C", "C", "C", "C", "C"};
    String[] suffixes = {"aven B4", "Ware", "ustom Profile 1", "ustom Profile 2", "ustom Profile 3", "ustom Profile 4", "ustom Profile 5"};
    String prefix = prefixes[clientNameIndex];
    String suffix = suffixes[clientNameIndex];
    int widthR = mc.fontRendererObj.getStringWidth(prefix);
    mc.fontRendererObj.drawStringWithShadow(prefix, startX, 5, this.usercolor.getRGB());
    mc.fontRendererObj.drawStringWithShadow(suffix, startX + widthR, 5, this.white.getRGB());
  }

  private String getWatermarkName() {
    switch (this.clientName.getValue()) {
      case 0:
        return "Raven B4";
      case 1:
        return "BlowsyWare";
      case 2:
        return this.clientname;
      case 3:
        return "Custom Profile 2";
      case 4:
        return "Custom Profile 3";
      case 5:
        return "Custom Profile 4";
      case 6:
        return "Custom Profile 5";
      default:
        return "Raven B4";
    }
  }

  private String getSelectedBuildVersion() {
    switch (this.buildInfoVersion.getValue()) {
      case 0:
        return "Release";
      case 1:
        return "Beta";
      case 2:
        return "Alpha";
      case 3:
        return "Development";
      case 4:
        return this.buildVersion;
      default:
        return "Release";
    }
  }

  private void updatePing() {
    try {
      if (mc.getNetHandler() != null) {
        NetworkPlayerInfo info =
            mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info != null) {
          this.ping = info.getResponseTime();
        }
      }
    } catch (Exception ignored) {
    }
  }

  private double getPlayerSpeed() {
    return MathHelper.sqrt_double(
        mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
  }

  private void renderAuraHitlog(ScaledResolution size, int watermarkStyle) {
    if (!this.auraHitlog.getValue()) return;

    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    EntityLivingBase killAuraTarget = killAura != null ? killAura.getTarget() : null;

    if (killAuraTarget != null) {
      double distance = mc.thePlayer.getDistanceToEntity(killAuraTarget);
      double swingProgress = mc.thePlayer.swingProgress;

      if (swingProgress != 0
          && this.enemy_previoushealth == this.enemy_currenthealth
          && killAuraTarget.hurtTime == 0
          && distance < 3
          && this.misssentcheck == 0
          && this.hurttimecounter >= 50) {
        this.misssentcheck = 1;
        this.hurttimecounter = 0;
        mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
        ChatUtil.display(
            "&7[&dR&7]&f Missed swing on " + this.enemyname + "&f due to &ccorrection&f.");
      }
      if (killAuraTarget.hurtTime != 0) this.misssentcheck = 0;
      if (killAuraTarget.hurtTime == 0 && swingProgress != 0 && distance < 2.99) {
        this.hurttimecounter++;
      }

      if (this.lastTarget != killAuraTarget) this.enemykilledsent = 0;
      this.lastTarget = killAuraTarget;
      this.enemyname = killAuraTarget.getName();
      this.enemy_previoushealth = killAuraTarget.getHealth();

      if (this.auratargetcheck == 0 && this.auratargetsent == 0) this.auratargetcheck = 1;
      if (this.auratargetcheck == 1 && this.auratargetsent == 0) {
        this.enemy_currenthealth = killAuraTarget.getHealth();
        this.auratargetsent = 1;
      }

      if (this.enemy_previoushealth < this.enemy_currenthealth) {
        this.healthdifference = this.enemy_previoushealth - this.enemy_currenthealth;
        this.enemy_currenthealth = this.enemy_previoushealth;
        ChatUtil.display(
            "&7[&dR&7]&f Hit "
                + this.enemyname
                + "&f for &c"
                + String.format("%.2f", Math.abs(this.healthdifference))
                + "HP &f(&c"
                + String.format("%.2f", this.enemy_currenthealth)
                + "HP&f remaining)");
      }

      if (this.lastTarget != null && this.lastTarget.isDead && this.enemykilledsent == 0) {
        ChatUtil.display("&7[&dR&7]&f Enemy " + this.enemyname + "&f killed.");
        this.enemykilledsent = 1;
      }

      if (watermarkStyle != 0) {
        String prefix = "Target: ";
        int prefixWidth = mc.fontRendererObj.getStringWidth(prefix);
        int enemyWidth = mc.fontRendererObj.getStringWidth(this.enemyname);
        int totalWidth = prefixWidth + enemyWidth;
        int centerX = (size.getScaledWidth() - totalWidth) / 2;
        mc.fontRendererObj.drawStringWithShadow(prefix, centerX, 5, this.white.getRGB());
        mc.fontRendererObj.drawStringWithShadow(
            this.enemyname, centerX + prefixWidth, 5, this.red.getRGB());
      }
    } else {
      if (this.lastTarget != null && this.lastTarget.isDead && this.enemykilledsent == 0) {
        ChatUtil.display("&7[&dR&7]&f Enemy " + this.enemyname + "&f killed.");
        this.enemykilledsent = 1;
      }
      this.auratargetcheck = 0;
      this.auratargetsent = 0;
    }
  }

  @EventTarget
  public void onPacketSent(PacketEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.SEND
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }
    if (event.getPacket() instanceof C01PacketChatMessage) {
      C01PacketChatMessage c01 = (C01PacketChatMessage) event.getPacket();
      if (!c01.getMessage().startsWith("/")) return;
      String[] parts = c01.getMessage().split(" ", 2);
      String command = parts[0].substring(1).toLowerCase();

      if (parts.length == 1 || parts[1].trim().isEmpty()) {
        if (command.equals("buildinfo")) {
          ChatUtil.display(
              "&7[&dR&7] &r&lBuild Info:"
                  + "\n&7[&dR&7] - /buildinfo user <name>"
                  + "\n&7[&dR&7] - /buildinfo id <id>"
                  + "\n&7[&dR&7] - /buildinfo version <version>");
          event.setCancelled(true);
          return;
        } else if (command.equals("watermark")) {
          ChatUtil.display("&7[&dR&7] &cUsage: /watermark <name>");
          event.setCancelled(true);
          return;
        }
      }

      if (parts.length > 1) {
        String argument = parts[1].trim();
        if (command.equals("buildinfo")) {
          String[] args = argument.split(" ", 2);
          if (args.length < 2) {
            ChatUtil.display("&7[&dR&7] &cInvalid command usage.");
            event.setCancelled(true);
            return;
          }
          switch (args[0].toLowerCase()) {
            case "user":
              this.builduser = args[1];
              ChatUtil.display("&7[&dR&7] &aBuild user set to " + this.builduser);
              break;
            case "id":
              this.buildNumber = args[1];
              ChatUtil.display("&7[&dR&7] &aBuild ID set to " + this.buildNumber);
              break;
            case "version":
              this.buildVersion = args[1];
              ChatUtil.display("&7[&dR&7] &aBuild version set to " + this.buildVersion);
              break;
            default:
              ChatUtil.display("&7[&dR&7] &cInvalid subcommand.");
              event.setCancelled(true);
              return;
          }
          event.setCancelled(true);
          return;
        } else if (command.equals("watermark")) {
          if (argument.isEmpty()) {
            ChatUtil.display("&7[&dR&7] &cUsage: /watermark <name>");
            event.setCancelled(true);
            return;
          }
          this.clientname = argument;
          ChatUtil.display("&7[&dR&7] &aWatermark set to " + this.clientname);
          event.setCancelled(true);
          return;
        }
      }
    }
  }
}
