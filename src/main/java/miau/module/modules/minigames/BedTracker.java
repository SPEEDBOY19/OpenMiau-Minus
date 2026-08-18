package miau.module.modules.minigames;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import miau.Miau;
import miau.enums.ChatColors;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.*;
import miau.util.client.ChatUtil;
import miau.util.client.SoundUtil;
import miau.util.player.TeamUtil;
import miau.util.render.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class BedTracker extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final long BED_SCAN_DELAY_MS = 3000L;
  private final LinkedHashMap<String, Long> alertCooldowns;
  private final LinkedHashSet<EntityEnderPearl> trackedPearls;
  private final LinkedHashSet<String> whitelistedPlayers;
  private final Color wBed;
  private final Color rBed;
  private final Color yBed;
  private final Color gBed;
  private BlockPos bedPos;
  private long lastMarcoTime;
  private boolean waiting;
  private long bedScanAt;
  public final BooleanProperty alerts;
  public final IntProperty alertRange;
  public final BooleanProperty alertOnPearl;
  public final ModeProperty alertSound;
  public final IntProperty alertFrequency;
  public final BooleanProperty marco;
  public final IntProperty marcoRange;
  public final BooleanProperty marcoOnPreal;
  public final TextProperty marcoText;
  public final IntProperty marcoDelay;
  public final BooleanProperty hud;
  public final DragProperty hudDrag;
  public final FloatProperty hudScale;
  public final BooleanProperty hudShadow;

  private void playAlertSound() {
    switch (this.alertSound.getValue()) {
      case 1:
        SoundUtil.playSound("mob.cat.meow");
        break;
      case 2:
        SoundUtil.playSound("random.anvil_land");
    }
  }

  private Color getHudColor(int distance) {
    if (distance < 0) {
      return this.wBed;
    } else if (distance <= 100) {
      return this.gBed;
    } else if (distance <= 114) {
      return ColorUtil.interpolate((float) (114 - distance) / 14.0F, this.yBed, this.gBed);
    } else {
      return distance <= 128
          ? ColorUtil.interpolate((float) (128 - distance) / 14.0F, this.rBed, this.yBed)
          : this.rBed;
    }
  }

  private boolean isBed(BlockPos blockPos) {
    return blockPos != null
        && mc.theWorld != null
        && mc.theWorld.getBlockState(blockPos).getBlock() == Blocks.bed;
  }

  public BedTracker() {
    super("BedTracker", false, true);
    this.alertCooldowns = new LinkedHashMap<>();
    this.trackedPearls = new LinkedHashSet<>();
    this.whitelistedPlayers = new LinkedHashSet<>();
    this.wBed = new Color(ChatColors.WHITE.toAwtColor());
    this.rBed = new Color(ChatColors.RED.toAwtColor());
    this.yBed = new Color(ChatColors.YELLOW.toAwtColor());
    this.gBed = new Color(ChatColors.GREEN.toAwtColor());
    this.bedPos = null;
    this.lastMarcoTime = -1L;
    this.waiting = false;
    this.bedScanAt = -1L;
    this.alerts = new BooleanProperty("alerts", true);
    this.alertRange = new IntProperty("alerts-range", 48, 8, 128, this.alerts::getValue);
    this.alertOnPearl = new BooleanProperty("alerts-on-pearl", true);
    this.alertSound =
        new ModeProperty(
            "alerts-sound",
            1,
            new String[] {"NONE", "MEOW", "ANVIL"},
            () -> this.alerts.getValue() || this.alertOnPearl.getValue());
    this.alertFrequency =
        new IntProperty(
            "alerts-frequency",
            5,
            1,
            30,
            () -> this.alerts.getValue() || this.alertOnPearl.getValue());
    this.marco = new BooleanProperty("macro", false);
    this.marcoRange = new IntProperty("macro-range", 24, 8, 128, this.marco::getValue);
    this.marcoOnPreal = new BooleanProperty("macro-on-pearl", false);
    this.marcoText =
        new TextProperty(
            "macro-text", "/lobby", () -> this.marco.getValue() || this.marcoOnPreal.getValue());
    this.marcoDelay =
        new IntProperty(
            "macro-delay", 1, 1, 10, () -> this.marco.getValue() || this.marcoOnPreal.getValue());
    this.hud = new BooleanProperty("hud", true);
    this.hudDrag = new DragProperty("BedTracker HUD", new miau.util.vector.Vector2d(10, 50), true);
    this.hudScale = new FloatProperty("hud-scale", 1.0F, 0.5F, 1.5F, this.hud::getValue);
    this.hudShadow = new BooleanProperty("hud-shadow", true, this.hud::getValue);
  }

  private void resetTracking() {
    this.alertCooldowns.clear();
    this.trackedPearls.clear();
    this.whitelistedPlayers.clear();
    this.bedPos = null;
    this.lastMarcoTime = -1L;
  }

  private void scheduleBedScan() {
    this.bedScanAt = System.currentTimeMillis() + BED_SCAN_DELAY_MS;
  }

  private void runPendingBedScan() {
    if (this.bedScanAt == -1L || System.currentTimeMillis() < this.bedScanAt) {
      return;
    }
    this.bedScanAt = -1L;
    if (mc.theWorld == null || mc.thePlayer == null) {
      return;
    }

    int x = MathHelper.floor_double(mc.thePlayer.posX);
    int y = MathHelper.floor_double(mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
    int z = MathHelper.floor_double(mc.thePlayer.posZ);
    for (int i = x - 25; i <= x + 25; i++) {
      for (int j = y - 25; j <= y + 25; j++) {
        for (int k = z - 25; k <= z + 25; k++) {
          BlockPos blockPos = new BlockPos(i, j, k);
          if (this.isBed(blockPos)) {
            this.bedPos = blockPos;
            ChatUtil.sendFormatted(
                String.format(
                    "%s%s: &fWhitelisted your bed at (%d, %d, %d) &a&l\u2714&r",
                    Miau.clientName,
                    this.getName(),
                    this.bedPos.getX(),
                    this.bedPos.getY(),
                    this.bedPos.getZ()));
            SoundUtil.playSound("note.pling");
            return;
          }
        }
      }
    }
  }

  private void pruneTrackedPearls() {
    if (mc.theWorld == null) {
      this.trackedPearls.clear();
      return;
    }

    Iterator<EntityEnderPearl> iterator = this.trackedPearls.iterator();
    while (iterator.hasNext()) {
      EntityEnderPearl pearl = iterator.next();
      if (pearl.isDead || !mc.theWorld.loadedEntityList.contains(pearl)) {
        iterator.remove();
      }
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.POST) {
      this.runPendingBedScan();
      this.pruneTrackedPearls();
      if (!this.isBed(this.bedPos)) {
        return;
      }

      long millis = System.currentTimeMillis();
      boolean pearl = false;
      boolean marco = false;
      for (Entity entity : mc.theWorld.loadedEntityList) {
        if (entity instanceof EntityEnderPearl) {
          EntityEnderPearl enderPearl = (EntityEnderPearl) entity;
          if (!this.trackedPearls.contains(enderPearl)) {
            this.trackedPearls.add(enderPearl);
            if (this.alertOnPearl.getValue()) {
              ChatUtil.sendFormatted(
                  String.format(
                      "%s%s: &fDetected &5Ender Pearl&r &e&l⚠&r", Miau.clientName, this.getName()));
              pearl = true;
            }
            if (this.marcoOnPreal.getValue()
                && this.lastMarcoTime + (long) this.marcoDelay.getValue() * 1000L <= millis) {
              this.lastMarcoTime = millis;
              marco = true;
            }
          }
        }
      }
      for (EntityPlayer player : mc.theWorld.playerEntities) {
        if (TeamUtil.isBot(player) || this.whitelistedPlayers.contains(player.getName())) continue;
        if (TeamUtil.isSameTeam(player)) {
          this.whitelistedPlayers.add(player.getName());
        } else {
          double distance =
              player.getDistance(
                  (double) this.bedPos.getX() + 0.5,
                  (double) this.bedPos.getY() + 0.5,
                  (double) this.bedPos.getZ() + 0.5);
          String name = player.getName();
          String text = player.getDisplayName().getFormattedText();
          ItemStack item = player.getHeldItem();
          boolean isPearl = item != null && item.getItem() instanceof ItemEnderPearl;
          if (this.alerts.getValue() && distance < (double) this.alertRange.getValue()) {
            Long cooldown = this.alertCooldowns.get(name);
            if (cooldown == null
                || cooldown + (long) this.alertFrequency.getValue() * 1000L <= millis) {
              this.alertCooldowns.put(name, millis);
              ChatUtil.sendFormatted(
                  String.format(
                      "%s%s: %s&r &fis %d blocks away from your bed &e&l⚠&r",
                      Miau.clientName, this.getName(), text, (int) distance + 1));
              pearl = true;
            }
          }
          if (this.alertOnPearl.getValue() && isPearl) {
            Long cooldown = this.alertCooldowns.get(name);
            if (cooldown == null
                || cooldown + (long) this.alertFrequency.getValue() * 1000L <= millis) {
              this.alertCooldowns.put(name, millis);
              ChatUtil.sendFormatted(
                  String.format(
                      "%s%s: %s&r &fhas &5Ender Pearl&r &e&l⚠&r",
                      Miau.clientName, this.getName(), text));
              pearl = true;
            }
          }
          if ((this.marco.getValue() && distance < (double) this.marcoRange.getValue()
                  || this.marcoOnPreal.getValue() && isPearl)
              && this.lastMarcoTime + (long) this.marcoDelay.getValue() * 1000L <= millis) {
            this.lastMarcoTime = millis;
            marco = true;
          }
        }
      }
      if (pearl) {
        this.playAlertSound();
      }
      if (marco) {
        ChatUtil.sendRaw(
            String.format(
                ChatColors.formatColor("%s%s: &fRunning &6%s&r"),
                ChatColors.formatColor(Miau.clientName),
                this.getName(),
                this.marcoText.getValue()));
        ChatUtil.sendMessage(this.marcoText.getValue());
      }
    }
  }

  @EventTarget(Priority.LOW)
  public void onRender(Render2DEvent event) {
    if (this.isEnabled() && this.hud.getValue()) {
      if (mc.theWorld != null && mc.thePlayer != null && !mc.gameSettings.showDebugInfo) {
        GuiScreen currentScreen = mc.currentScreen;
        if (currentScreen == null || currentScreen instanceof GuiChat) {
          int distanceSq = 0;
          boolean hasBed = this.isBed(this.bedPos);
          if (hasBed) {
            double xDiff = mc.thePlayer.posX - (double) this.bedPos.getX();
            double zDiff = mc.thePlayer.posZ - (double) this.bedPos.getZ();
            distanceSq = (int) Math.sqrt(xDiff * xDiff + zDiff * zDiff) + 1;
          }
          String text =
              ChatColors.formatColor(
                  String.format(
                      "&fBed: %s%s",
                      !hasBed ? "&cfalse&r" : "&atrue&r",
                      !hasBed
                          ? ""
                          : String.format(
                              " &7| &fDistance: &r%d%s",
                              distanceSq, distanceSq >= 128 ? " &c&l⚠&r" : "")));
          ScaledResolution scaledResolution = new ScaledResolution(mc);
          float width = (float) mc.fontRendererObj.getStringWidth(text);
          float height = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;

          float x = (float) this.hudDrag.position.x;
          float y = (float) this.hudDrag.position.y;
          float sc = this.hudScale.getValue();

          GlStateManager.pushMatrix();
          GlStateManager.scale(sc, sc, 1.0F);
          GlStateManager.translate(x / sc, y / sc, 0.0F);
          GlStateManager.disableDepth();
          GlStateManager.enableBlend();
          GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
          mc.fontRendererObj.drawString(
              text, 0.0F, 0.0F, this.getHudColor(distanceSq).getRGB(), this.hudShadow.getValue());
          GlStateManager.disableBlend();
          GlStateManager.enableDepth();
          GlStateManager.popMatrix();

          this.hudDrag.setScale(new miau.util.vector.Vector2d(width * sc, height * sc));
        }
      }
    }
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    this.waiting = false;
    this.bedScanAt = -1L;
    this.resetTracking();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      if (event.getPacket() instanceof S02PacketChat) {
        String msg = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
        if (msg.contains("§e§lProtect your bed and destroy the enemy bed")
            || msg.contains("§e§lDestroy the enemy bed and then eliminate them")) {
          this.bedScanAt = -1L;
          this.resetTracking();
          this.waiting = true;
        }
      }
      if (event.getPacket() instanceof S08PacketPlayerPosLook && this.waiting) {
        this.waiting = false;
        this.scheduleBedScan();
      }
    }
  }

  @Override
  public void onDisabled() {
    this.waiting = false;
    this.bedScanAt = -1L;
    this.resetTracking();
  }
}
