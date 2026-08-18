package miau.module.modules.render;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.enums.ChatColors;
import miau.util.client.SoundUtil;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorGuiChat;
import miau.mixin.IAccessorRenderManager;
import miau.module.Module;
import miau.module.modules.render.hud.InterfaceComponent;
import miau.property.properties.*;
import miau.util.font.FontRepository;
import miau.util.render.ColorUtil;
import miau.util.render.MenuBackground;
import miau.util.render.RenderUtil;
import miau.util.render.Themes;
import miau.util.shader.BlurUtils;
import miau.util.shader.RoundedUtils;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class HUD extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final java.util.Map<Module, InterfaceComponent> components = new java.util.HashMap<>();

  private long lastMS = System.currentTimeMillis();
  private List<Module> activeModules = new ArrayList<>();

  private float watermarkFade = 0f;
  private int firstWX = 0, firstWY = 0;
  private boolean watermarkTrack = false;
  private boolean watermarkFirstClick = true;

  private double lastX = 0, lastZ = 0;
  private String bpsString = "0.00";
  private float idleTicks = 0f;
  private final List<FootParticle> particles = new ArrayList<>();

  // Magic Circle Textures Array
  private static final ResourceLocation[] MAGIC_CIRCLE_TEXTURES = new ResourceLocation[] {
      new ResourceLocation("miau/magiccircle/A.png"),
      new ResourceLocation("miau/magiccircle/B.png"),
      new ResourceLocation("miau/magiccircle/C.png"),
      new ResourceLocation("miau/magiccircle/D.png"),
      new ResourceLocation("miau/magiccircle/F.png")
  };

  private static class FootParticle {
    double x, y, z;
    double motionX, motionY, motionZ;
    int maxAge;
    int age;
    Color color;

    FootParticle(double x, double y, double z, double motionX, double motionY, double motionZ, int maxAge, Color color) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.motionX = motionX;
      this.motionY = motionY;
      this.motionZ = motionZ;
      this.maxAge = maxAge;
      this.age = 0;
      this.color = color;
    }

    boolean update() {
      x += motionX;
      y += motionY;
      z += motionZ;
      motionX *= 1.02;
      motionZ *= 1.02;
      age++;
      return age >= maxAge;
    }
  }

  private static final ResourceLocation MIAU_LOGO =
      new ResourceLocation("miau/logo.png");

  private static final ResourceLocation WATERMARK_IMAGE =
      new ResourceLocation("miau/watermark.png");
  private static final int WATERMARK_TEXTURE_WIDTH = 1645;
  private static final int WATERMARK_TEXTURE_HEIGHT = 656;

  public final BooleanProperty showWatermark = new BooleanProperty("watermark", true);
  public final BooleanProperty customHotbar = new BooleanProperty("Custom Hotbar", true);
  
  // Magic Circle Properties (Thêm tuỳ chọn chọn ảnh)
  public final BooleanProperty magicCircle = new BooleanProperty("Magic Circle", true);
  public final ModeProperty magicCircleTexture = 
      new ModeProperty("Circle Texture", 0, new String[] {"ALL", "A", "B", "C", "D", "F"}, this.magicCircle::getValue);
  public final FloatProperty magicCircleSpeed = 
      new FloatProperty("Circle Speed", 1.0f, 0.1f, 5.0f, this.magicCircle::getValue);

  public final ModeProperty hudMode =
      new ModeProperty("mode", 3, new String[] {"NORMAL", "EXHIBITION", "WATERMARK+", "MIAU"});

  public final FloatProperty watermarkPlusX =
      new FloatProperty("Watermark+ X", 10f, -2000f, 2000f);
  public final FloatProperty watermarkPlusY =
      new FloatProperty("Watermark+ Y", 10f, -2000f, 2000f);
  public final FloatProperty watermarkPlusScale =
      new FloatProperty("Watermark+ Scale", 0.12f, 0.02f, 1.0f);
  public final IntProperty watermarkPlusOpacity =
      new IntProperty("Watermark+ Opacity", 255, 0, 255);
  public final BooleanProperty watermarkPlusDrag =
      new BooleanProperty("Watermark+ Drag", true);
  public final BooleanProperty watermarkPlusFade =
      new BooleanProperty("Watermark+ Fade", true);
  public final ModeProperty watermarkPlusColorMode =
      new ModeProperty("Watermark+ Color Mode", 0, new String[] {"NONE", "THEME", "STATIC", "RAINBOW"});
  public final ColorProperty watermarkPlusColor =
      new ColorProperty("Watermark+ Color", 0xFFFFFF);
  public final ModeProperty watermarkPlusStyle =
      new ModeProperty("Watermark+ Style", 0, new String[] {"NONE", "SHADOW", "OUTLINE", "REFLECTION"});
  public final FloatProperty watermarkPlusShadowOffset =
      new FloatProperty("Watermark+ Shadow Offset", 2f, 0.5f, 10f);
  public final IntProperty watermarkPlusShadowOpacity =
      new IntProperty("Watermark+ Shadow Opacity", 100, 0, 255);
  public final FloatProperty watermarkPlusReflectionGap =
      new FloatProperty("Watermark+ Reflection Gap", 4f, 0f, 20f);
  public final IntProperty watermarkPlusReflectionOpacity =
      new IntProperty("Watermark+ Reflection Opacity", 90, 0, 255);

  public final TextProperty watermarkName =
      new TextProperty("watermark-name", "MiauMinus", this.showWatermark::getValue);
  public final BooleanProperty showCoordinates =
      new BooleanProperty("coordinates", true, () -> this.hudMode.getValue() == 1);
  public final BooleanProperty showTime =
      new BooleanProperty("show-time", true, this.showWatermark::getValue);
  public final BooleanProperty showFps =
      new BooleanProperty("show-fps", true, this.showWatermark::getValue);
  public final BooleanProperty showPing =
      new BooleanProperty("show-ping", true, this.showWatermark::getValue);
  public final BooleanProperty showBps =
      new BooleanProperty("show-bps", true, this.showWatermark::getValue);
  public final BooleanProperty showBalance =
      new BooleanProperty("show-balance", false, this.showWatermark::getValue);

  public final ModeProperty colorAnimation =
      new ModeProperty("color-animation", 1, new String[] {"STATIC", "FADE", "RAINBOW"});
  public final ModeProperty modulesToShow =
      new ModeProperty("modules-to-show", 1, new String[] {"ALL", "EXCLUDE RENDER", "ONLY BOUND"});
  public final ModeProperty fontFace = new ModeProperty("Font", 1, FontRepository.FONT_NAMES);
  public final ModeProperty posX =
      new ModeProperty("position-x", 0, new String[] {"LEFT", "RIGHT"});
  public final ModeProperty posY =
      new ModeProperty("position-y", 0, new String[] {"TOP", "BOTTOM"});
  public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
  public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
  public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
  public final BooleanProperty showBar = new BooleanProperty("bar", true);
  public final BooleanProperty shadow = new BooleanProperty("shadow", true);
  public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
  public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
  public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
  public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
  public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
  public final ModeProperty toggleSoundSelect =
      new ModeProperty(
          "toggle-sound", 0, new String[] {"Default", "Sigma5"}, this.toggleSound::getValue);
  public final BooleanProperty attackSound = new BooleanProperty("attack-sounds", true);
  public final ModeProperty attackSoundSelect =
      new ModeProperty(
          "attack-sound",
          0,
          new String[] {"Ting", "Bass", "Pop", "Swing", "Cloth3"},
          this.attackSound::getValue);
  public final FloatProperty soundVolume =
      new FloatProperty("sound-volume", 1.0F, 0.0F, 1.0F);
  public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
  public final BooleanProperty notifications = new BooleanProperty("notifications", true);
  public final BooleanProperty shaders = new BooleanProperty("Shaders", false);

  public final IntProperty backgroundAlpha = new IntProperty("Background Alpha", 110, 0, 255);
  public final FloatProperty roundingRadius =
      new FloatProperty("Rounding Radius", 1.0F, 0.0F, 10.0F);
  public final ModeProperty menuBackground =
      new ModeProperty("Menu Background", 0, MenuBackground.NAMES);

  public float getModuleListHeight() {
    float itemHeight = hudMode.getValue() == 1 ? 12.0f : 10.0f;
    int count = 0;
    for (Module module : Miau.moduleManager.modules.values()) {
      if (!module.isEnabled()) continue;
      if (modulesToShow.getValue() == 1 && module.isHidden()) continue;
      if (modulesToShow.getValue() == 2 && module.getKey() == 0) continue;
      String name = module.getName().toLowerCase();
      if (name.equals("hud") || name.equals("gui") || name.equals("clickgui")) continue;
      count++;
    }
    return count * itemHeight * scale.getValue();
  }

  private InterfaceComponent getComponent(Module module) {
    return components.computeIfAbsent(module, InterfaceComponent::new);
  }

  private String getModuleName(Module module) {
    String moduleName = module.getName();
    if (this.lowerCase.getValue()) {
      moduleName = moduleName.toLowerCase(Locale.ROOT);
    }
    return moduleName;
  }

  private String[] getModuleSuffix(Module module) {
    String[] moduleSuffix = module.getSuffix();
    if (this.lowerCase.getValue()) {
      for (int i = 0; i < moduleSuffix.length; i++) {
        moduleSuffix[i] = moduleSuffix[i].toLowerCase();
      }
    }
    return moduleSuffix;
  }

  private int getModuleWidth(Module module) {
    return this.calculateStringWidth(this.getModuleName(module), this.getModuleSuffix(module));
  }

  private int calculateStringWidth(String string, String[] arr) {
    int width = getFont().getStringWidth(string);
    if (this.suffixes.getValue()) {
      for (String str : arr) {
        width += 3 + getFont().getStringWidth(str);
      }
    }
    return width;
  }

  public miau.util.font.Font getFont() {
    return FontRepository.getHudFont(18);
  }

  public HUD() {
    super("HUD", true, true);
    FontRepository.setHudFace(this.fontFace.getValue());
    MinecraftForge.EVENT_BUS.register(this);
  }

  @SubscribeEvent
  public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
    if (this.isEnabled() && customHotbar.getValue()) {
      if (event.type == RenderGameOverlayEvent.ElementType.HOTBAR
          || event.type == RenderGameOverlayEvent.ElementType.HEALTH
          || event.type == RenderGameOverlayEvent.ElementType.FOOD
          || event.type == RenderGameOverlayEvent.ElementType.ARMOR
          || event.type == RenderGameOverlayEvent.ElementType.EXPERIENCE) {
        event.setCanceled(true);
      }
    }
  }

  @Override
  public void verifyValue(String name) {
    if (name.equalsIgnoreCase("Font")) {
      FontRepository.setHudFace(this.fontFace.getValue());
      FontRepository.clearCache();
    }
  }

  public Color getColor(long time) {
    return this.getColor(time, 0L);
  }

  public Color getColor(long time, long yPos) {
    Themes theme = Themes.getCurrentTheme();

    switch (this.colorAnimation.getValue()) {
      case 0:
        return theme.getFirstColor();
      case 1:
        return theme.getAccentColor(new Vector2d(0, yPos));
      case 2:
        return ColorUtil.rainbow((int) (time * 500 / 6));
      default:
        return Color.white;
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer != null) {
        double dist = Math.hypot(mc.thePlayer.posX - lastX, mc.thePlayer.posZ - lastZ);
        bpsString =
            String.valueOf(
                miau.util.math.MathUtil.round(
                    dist * 20.0D * ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed, 2));
        lastX = mc.thePlayer.posX;
        lastZ = mc.thePlayer.posZ;

        boolean isMoving = mc.thePlayer.moveForward != 0 || mc.thePlayer.moveStrafing != 0 || !mc.thePlayer.onGround;
        if (isMoving) {
          idleTicks = Math.max(0f, idleTicks - 2f);
        } else {
          idleTicks = Math.min(100f, idleTicks + 1f);
        }
      }
    }
    if (this.isEnabled() && event.getType() == EventType.POST) {
      this.activeModules =
          Miau.moduleManager.modules.values().stream()
              .filter(
                  module ->
                      module.isEnabled()
                          && (this.modulesToShow.getValue() == 0 || !module.isHidden())
                          && getComponent(module).shouldDisplay(this))
              .sorted(Comparator.comparingInt(this::getModuleWidth).reversed())
              .collect(Collectors.<Module>toList());
      try {
        Miau.clientName = ChatColors.getDynamicPrefix();
      } catch (Exception e) {
      }
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (this.attackSound.getValue()) {
      SoundUtil.playAttackSound();
    }
  }

 private String getExhibitionWatermark() {
    String customName = this.watermarkName.getValue();
    if (customName == null || customName.isEmpty()) customName = "MiauMinus";

    int ping = 0;
    if (mc.getNetHandler() != null && mc.thePlayer != null) {
        net.minecraft.client.network.NetworkPlayerInfo playerInfo =
            mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (playerInfo != null) ping = playerInfo.getResponseTime();
    }

    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a");
    String formattedTime = sdf.format(new java.util.Date());

    String text = customName.charAt(0) + "\u00A77" + customName.substring(1);

    if (this.showTime.getValue()) text += " [\u00A7f" + formattedTime + "\u00A77]";
    if (this.showFps.getValue()) text += " [\u00A7f" + Minecraft.getDebugFPS() + " FPS\u00A77]";
    if (this.showPing.getValue()) text += " [\u00A7f" + ping + "ms\u00A77]";
    if (this.showBps.getValue()) text += " [\u00A7f" + bpsString + " BPS\u00A77]";
    if (this.showBalance.getValue())
        text += " [\u00A7f" + miau.module.modules.misc.Balance.balance + " Balance\u00A77]";
        
    return text;
}

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.getRenderManager() == null) return;

    double pX = RenderUtil.lerpDouble(mc.thePlayer.posX, mc.thePlayer.lastTickPosX, event.getPartialTicks())
        - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
    double pY = RenderUtil.lerpDouble(mc.thePlayer.posY, mc.thePlayer.lastTickPosY, event.getPartialTicks())
        - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY() + 0.02;
    double pZ = RenderUtil.lerpDouble(mc.thePlayer.posZ, mc.thePlayer.lastTickPosZ, event.getPartialTicks())
        - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

    long time = System.currentTimeMillis();

    GlStateManager.pushMatrix();
    GlStateManager.pushAttrib();
    
    GlStateManager.enableBlend();
    GlStateManager.enableTexture2D();
    GlStateManager.disableLighting();
    GlStateManager.disableCull();
    GlStateManager.depthMask(false);
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

    // MAGIC CIRCLE Render Layer (Có hỗ trợ chọn riêng Texture)
    if (this.magicCircle.getValue()) {
      float speedMultiplier = this.magicCircleSpeed.getValue();
      float[] radii = {0.85f, 1.05f, 1.25f, 1.45f, 1.65f};
      float[] speeds = {0.06f, -0.09f, 0.04f, -0.05f, 0.08f};

      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldrenderer = tessellator.getWorldRenderer();

      int selectedMode = this.magicCircleTexture.getValue(); // 0: ALL, 1: A, 2: B, 3: C, 4: D, 5: F

      for (int i = 0; i < MAGIC_CIRCLE_TEXTURES.length; i++) {
        // Bỏ qua nếu người dùng chọn duy nhất 1 texture khác với index hiện tại
        if (selectedMode != 0 && selectedMode != (i + 1)) continue;

        float radius = (selectedMode == 0) ? radii[i] : 1.35f;
        float speed = (selectedMode == 0) ? speeds[i] : 0.05f;
        float rotation = (time % 360000L) * (speed * speedMultiplier);
        Color layerColor = this.getColor(time + (i * 150L));

        mc.getTextureManager().bindTexture(MAGIC_CIRCLE_TEXTURES[i]);

        GlStateManager.pushMatrix();
        GlStateManager.translate(pX, pY + (i * 0.001D), pZ);
        GlStateManager.rotate(rotation, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(90.0f, 1.0f, 0.0f, 0.0f);

        GlStateManager.color(
            layerColor.getRed() / 255.0f,
            layerColor.getGreen() / 255.0f,
            layerColor.getBlue() / 255.0f,
            0.75f
        );

        worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(-radius, -radius, 0.0D).tex(0.0D, 0.0D).endVertex();
        worldrenderer.pos(-radius, radius, 0.0D).tex(0.0D, 1.0D).endVertex();
        worldrenderer.pos(radius, radius, 0.0D).tex(1.0D, 1.0D).endVertex();
        worldrenderer.pos(radius, -radius, 0.0D).tex(1.0D, 0.0D).endVertex();
        tessellator.draw();

        GlStateManager.popMatrix();
      }
    }

    // Foot Particles Render
    GlStateManager.disableTexture2D();
    if (idleTicks > 20f && Math.random() < (idleTicks / 100f) * 0.4) {
      double spawnAngle = Math.random() * Math.PI * 2;
      double spawnX = pX + Math.sin(spawnAngle) * (1.65f * 0.9);
      double spawnZ = pZ + Math.cos(spawnAngle) * (1.65f * 0.9);
      
      double mX = (Math.sin(spawnAngle) * 0.008) + (Math.random() - 0.5) * 0.005;
      double mY = 0.015 + Math.random() * 0.015;
      double mZ = (Math.cos(spawnAngle) * 0.008) + (Math.random() - 0.5) * 0.005;
      
      int maxAge = 25 + (int) (Math.random() * 20);
      particles.add(new FootParticle(spawnX, pY, spawnZ, mX, mY, mZ, maxAge, this.getColor(time + (long)(Math.random() * 500))));
    }

    particles.removeIf(p -> {
      boolean dead = p.update();
      float lifeRatio = 1.0f - ((float) p.age / (float) p.maxAge);
      float alpha = lifeRatio * 0.6f;
      float pSize = 0.035f * lifeRatio;

      Color c = p.color;
      GL11.glColor4f(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, alpha);

      GL11.glBegin(GL11.GL_QUADS);
      GL11.glVertex3d(p.x - pSize, p.y - pSize, p.z - pSize);
      GL11.glVertex3d(p.x + pSize, p.y - pSize, p.z - pSize);
      GL11.glVertex3d(p.x + pSize, p.y + pSize, p.z + pSize);
      GL11.glVertex3d(p.x - pSize, p.y + pSize, p.z + pSize);
      GL11.glEnd();

      return dead;
    });

    GlStateManager.popAttrib();
    GlStateManager.popMatrix();
    GlStateManager.resetColor();
  }

  private void renderMiauHUDMode(long l, ScaledResolution sr) {
    if (mc.thePlayer == null || mc.theWorld == null) return;

    int fps = Minecraft.getDebugFPS();
    int ping = 0;
    try {
      if (mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
        ping = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
      }
    } catch (Exception ignored) {}

    java.time.LocalTime now = java.time.LocalTime.now();
    String timeStr = String.format("%02d:%02d", now.getHour(), now.getMinute());
    String bpsFormatted = bpsString + " BPS";

    Color themeColor1 = this.getColor(l, 0L);
    Color themeColor2 = this.getColor(l, 100L);

    String clientTitle = this.watermarkName.getValue();
    if (clientTitle == null || clientTitle.isEmpty()) clientTitle = "MiauMinus";

    String verStr = "v" + Miau.version;
    String fpsPingStr = fps + " FPS  §7|  " + ping + "ms";

    float titleW = FontRepository.getHudFont(18).width(clientTitle);
    float verW = FontRepository.getHudFont(12).width(verStr);
    float fpsPingW = FontRepository.getHudFont(12).width(fpsPingStr);
    
    float bpsWidth = FontRepository.getHudFont(12).width(bpsFormatted) + 8.0f;
    float timeWidth = FontRepository.getHudFont(12).width(timeStr) + 8.0f;

    float topRowWidth = titleW + 4.0f + verW + 16.0f + fpsPingW;
    float bottomRowWidth = bpsWidth + 6.0f + timeWidth;
    
    float iconBoxSize = 26.0f;
    float textContentWidth = Math.max(topRowWidth, bottomRowWidth);
    
    float width = Math.max(190.0f, 8.0f + iconBoxSize + 8.0f + textContentWidth + 10.0f);
    float height = 38.0f;
    float x = 6.0f;
    float y = 6.0f;

    GlStateManager.pushMatrix();

    int bgAlphaVal = this.backgroundAlpha.getValue() > 0 ? Math.min(230, this.backgroundAlpha.getValue() + 50) : 175;
    RoundedUtils.drawRound(x, y, width, height, 7.0f, new Color(15, 15, 22, bgAlphaVal));

    RoundedUtils.drawGradientCornerLR(x, y, 3.5f, height, 3.0f, themeColor1, themeColor2);

    float iconX = x + 8.0f;
    float iconY = y + (height - iconBoxSize) / 2.0f;

    RoundedUtils.drawRound(iconX, iconY, iconBoxSize, iconBoxSize, 5.0f, new Color(25, 25, 35, 200));

    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    mc.getTextureManager().bindTexture(MIAU_LOGO);
    float imgPadding = 3.0f;
    net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(
        (int) (iconX + imgPadding), 
        (int) (iconY + imgPadding), 
        0, 0, 
        (int) (iconBoxSize - imgPadding * 2.0f), 
        (int) (iconBoxSize - imgPadding * 2.0f), 
        iconBoxSize - imgPadding * 2.0f, 
        iconBoxSize - imgPadding * 2.0f
    );
    GlStateManager.disableBlend();

    float textX = iconX + iconBoxSize + 8.0f;
    float titleY = y + 7.0f;

    FontRepository.getHudFont(18).drawWithShadow(clientTitle, textX, titleY, -1);

    float verX = textX + titleW + 4.0f;
    FontRepository.getHudFont(12).drawWithShadow(verStr, verX, titleY + 2.0f, themeColor2.getRGB());

    float fpsX = x + width - fpsPingW - 8.0f;
    FontRepository.getHudFont(12).drawWithShadow(fpsPingStr, fpsX, titleY + 2.0f, new Color(200, 200, 200).getRGB());

    float badgeY = y + 21.0f;

    RoundedUtils.drawRound(textX, badgeY, bpsWidth, 11.0f, 3.0f, new Color(30, 30, 42, 180));
    FontRepository.getHudFont(12).drawWithShadow(
        bpsFormatted, 
        textX + 4.0f, 
        badgeY + 2.0f, 
        themeColor1.getRGB()
    );

    float timeX = textX + bpsWidth + 5.0f;
    RoundedUtils.drawRound(timeX, badgeY, timeWidth, 11.0f, 3.0f, new Color(30, 30, 42, 180));
    FontRepository.getHudFont(12).drawWithShadow(
        timeStr, 
        timeX + 4.0f, 
        badgeY + 2.0f, 
        new Color(180, 180, 190).getRGB()
    );

    GlStateManager.popMatrix();
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    long currentMS = System.currentTimeMillis();
    float delta = (currentMS - lastMS);
    lastMS = currentMS;
    if (delta > 200 || delta < 0) delta = 16;
    ScaledResolution sr = new ScaledResolution(mc);

    GlStateManager.pushMatrix();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.disableDepth();
    GlStateManager.depthMask(false);

    for (Module module : Miau.moduleManager.modules.values()) {
      InterfaceComponent component = getComponent(module);
      boolean shouldBeVisible =
          module.isEnabled()
              && (this.modulesToShow.getValue() == 0 || !module.isHidden())
              && component.shouldDisplay(this);

      if (shouldBeVisible) {
        component.animationTime = (float) Math.min(1.0, component.animationTime + (delta * 0.006));
      } else {
        component.animationTime = (float) Math.max(0.0, component.animationTime - (delta * 0.006));
      }
    }

    java.util.List<InterfaceComponent> animatingComponents =
        Miau.moduleManager.modules.values().stream()
            .map(this::getComponent)
            .filter(c -> c.animationTime > 0.001)
            .sorted(
                Comparator.comparingInt((InterfaceComponent c) -> this.getModuleWidth(c.module))
                    .reversed())
            .collect(Collectors.toList());

    boolean isMcFont = FontRepository.isMinecraftSelected();
    float heightExhibition = 9.0f + 3.0f;
    float heightNormal = 9.0f + 1.0f;
    float currentYExhibition = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();
    float currentYNormal = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();

    if (this.posX.getValue() == 0) {
      if (this.posY.getValue() == 0) {
        if (this.hudMode.getValue() == 3) {
          currentYExhibition += 46.0f;
          currentYNormal += 46.0f;
        } else if (this.showWatermark.getValue()) {
          float watermarkHeight = getFont().getFontHeight() + 6.0F;
          currentYExhibition += watermarkHeight;
          currentYNormal += watermarkHeight;
        }
      } else {
        float bottomOffset = 0.0F;
        if (this.hudMode.getValue() == 1
            && this.showCoordinates.getValue()
            && mc.thePlayer != null) {
          bottomOffset += getFont().getFontHeight() * 3 + 12.0F;
        }
        currentYExhibition += bottomOffset;
        currentYNormal += bottomOffset;
      }
    }

    if (this.posY.getValue() == 1) {
      currentYExhibition =
          (float) sr.getScaledHeight()
              - currentYExhibition
              - heightExhibition * this.scale.getValue();
      currentYNormal =
          (float) sr.getScaledHeight() - currentYNormal - heightNormal * this.scale.getValue();
    }

    for (InterfaceComponent component : animatingComponents) {
      float targetY = (this.hudMode.getValue() == 1) ? currentYExhibition : currentYNormal;

      if (component.position.y == 0) component.position.y = targetY;

      component.position.y =
          miau.util.math.MathUtil.lerp((float) component.position.y, targetY, 0.015f * delta);

      if (component.module.isEnabled()
          && (this.modulesToShow.getValue() == 0 || !component.module.isHidden())
          && component.shouldDisplay(this)) {
        float spacingEx =
            heightExhibition * this.scale.getValue() * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
        float spacingNorm =
            (heightNormal + (this.shadow.getValue() ? 1.0F : 0.0F))
                * this.scale.getValue()
                * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
        currentYExhibition += spacingEx;
        currentYNormal += spacingNorm;
      }
    }

    if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
      String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
      if (Miau.commandManager != null && Miau.commandManager.isTypingCommand(text)) {
        RenderUtil.enableRenderState();
        RenderUtil.drawOutlineRect(
            2.0F,
            (float) (mc.currentScreen.height - 14),
            (float) (mc.currentScreen.width - 2),
            (float) (mc.currentScreen.height - 2),
            1.5F,
            0,
            this.getColor(System.currentTimeMillis()).getRGB());
        RenderUtil.disableRenderState();
      }
    }

    if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
      long l = System.currentTimeMillis();

      if (this.shaders.getValue()) {
        BlurUtils.prepareBloom();
        renderElements(l, delta, animatingComponents, sr, true, true);
        BlurUtils.bloomEnd(3, 2f);

        BlurUtils.prepareBlur();
        renderElements(l, delta, animatingComponents, sr, true, true);
        BlurUtils.blurEnd(2, 3f);
      }

      renderElements(l, delta, animatingComponents, sr, true, false);
      renderPotions(sr);

      if (this.customHotbar.getValue()) {
        renderCustomHotbar(sr);
      }
    }

    GlStateManager.depthMask(true);
    GlStateManager.enableDepth();
    GlStateManager.disableBlend();
    GlStateManager.popMatrix();
  }

  private void renderCustomHotbar(ScaledResolution sr) {
    if (mc.thePlayer == null || mc.playerController == null) return;
    if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;

    float width = 230;
    float height = 60;
    float x = (sr.getScaledWidth() - width) / 2.0f;
    float y = sr.getScaledHeight() - height - 5;

    RoundedUtils.drawRound(x, y, width, height, 6f, new Color(20, 20, 20, 160));

    boolean hasAbsorption = mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.absorption);
    float absorption = mc.thePlayer.getAbsorptionAmount();

    float hp = mc.thePlayer.getHealth();
    float maxHp = mc.thePlayer.getMaxHealth();
    float hpPercent = Math.max(0, Math.min(1, hp / maxHp));
    float leftWidth = 100;
    
    float hpBarY = hasAbsorption ? y + 10 : y + 8;
    float hpBarH = hasAbsorption ? 10f : 14f;
    
    RoundedUtils.drawRound(x + 8, hpBarY, leftWidth, hpBarH, 4f, new Color(40, 40, 40, 200));
    RoundedUtils.drawRound(x + 8, hpBarY, leftWidth * hpPercent, hpBarH, 4f, new Color(45, 230, 45, 255));
    
    String hpText = String.valueOf((int)hp);
    miau.util.font.Font font = getFont();
    font.drawWithShadow(hpText, x + 8 + leftWidth / 2 - font.getStringWidth(hpText) / 2, hpBarY + (hpBarH / 2) - (font.height() / 2f), -1);

    if (hasAbsorption && absorption > 0) {
      float maxAbsorption = 20.0f;
      float absorptionPercent = Math.max(0, Math.min(1, absorption / maxAbsorption));
      
      RoundedUtils.drawRound(x + 8, y + 3, leftWidth, 5f, 2f, new Color(40, 40, 40, 200));
      RoundedUtils.drawRound(x + 8, y + 3, leftWidth * absorptionPercent, 5f, 2f, new Color(255, 215, 0, 255));
      
      String absText = String.valueOf((int)absorption);
      font.drawWithShadow(absText, x + 8 + leftWidth / 2 - font.getStringWidth(absText) / 2, y - 1, -1);
    }

    float rightX = x + width - 100 - 8;
    
    int armor = mc.thePlayer.getTotalArmorValue();
    float armorPercent = Math.max(0, Math.min(1, armor / 20f));
    RoundedUtils.drawRound(rightX, y + 8, 100, 6, 2f, new Color(40, 40, 40, 200));
    RoundedUtils.drawRound(rightX, y + 8, 100 * armorPercent, 6, 2f, new Color(0, 150, 255, 255));
    
    int food = mc.thePlayer.getFoodStats().getFoodLevel();
    float foodPercent = Math.max(0, Math.min(1, food / 20f));
    RoundedUtils.drawRound(rightX, y + 16, 100, 6, 2f, new Color(40, 40, 40, 200));
    RoundedUtils.drawRound(rightX, y + 16, 100 * foodPercent, 6, 2f, new Color(255, 170, 0, 255));

    float xp = mc.thePlayer.experience;
    RoundedUtils.drawRound(x + 8, y + 26, width - 16, 3, 1.5f, new Color(15, 15, 15, 220));
    RoundedUtils.drawRound(x + 8, y + 26, (width - 16) * xp, 3, 1.5f, new Color(0, 200, 255, 255));

    float slotY = y + 33;
    float slotSize = 20;
    float slotSpacing = (width - 16 - (9 * slotSize)) / 8;

    net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
    for (int i = 0; i < 9; i++) {
        float slotX = x + 8 + i * (slotSize + slotSpacing);
        
        RoundedUtils.drawRound(slotX, slotY, slotSize, slotSize, 3f, new Color(30, 30, 30, 180));
        
        if (mc.thePlayer.inventory.currentItem == i) {
            RoundedUtils.drawRound(slotX - 1.5f, slotY - 1.5f, slotSize + 3, slotSize + 3, 4f, new Color(0, 170, 255, 200));
        }

        net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
        if (stack != null) {
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, (int)slotX + 2, (int)slotY + 2);
            
            if (stack.stackSize > 1) {
                GlStateManager.pushMatrix();
                GlStateManager.disableDepth();
                GlStateManager.disableLighting();
                
                GlStateManager.scale(0.55f, 0.55f, 0.55f);
                String countStr = String.valueOf(stack.stackSize);
                
                float renderX = (slotX + slotSize - 2) / 0.55f - mc.fontRendererObj.getStringWidth(countStr);
                float renderY = (slotY + slotSize - 2) / 0.55f - mc.fontRendererObj.FONT_HEIGHT;
                
                mc.fontRendererObj.drawStringWithShadow(countStr, renderX, renderY, -1);
                
                GlStateManager.enableLighting();
                GlStateManager.enableDepth();
                GlStateManager.popMatrix();
            }
        }
    }
    net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
    
    GlStateManager.enableAlpha();
    GlStateManager.disableBlend();
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  private void renderPotions(ScaledResolution sr) {
    if (mc.thePlayer != null) {
      java.util.Collection<net.minecraft.potion.PotionEffect> effects =
          mc.thePlayer.getActivePotionEffects();
      if (!effects.isEmpty()) {
        miau.util.font.Font font = getFont();
        float drawY = sr.getScaledHeight() - 3;
        java.util.List<net.minecraft.potion.PotionEffect> sortedEffects = new ArrayList<>(effects);
        sortedEffects.sort(
            (a, b) -> {
              String nameA = net.minecraft.client.resources.I18n.format(a.getEffectName());
              String nameB = net.minecraft.client.resources.I18n.format(b.getEffectName());
              String timeA = net.minecraft.potion.Potion.getDurationString(a);
              String timeB = net.minecraft.potion.Potion.getDurationString(b);
              String textA =
                  (lowerCase.getValue() ? nameA.toLowerCase() : nameA)
                      + (a.getAmplifier() > 0 ? " " + (a.getAmplifier() + 1) : "")
                      + " \u00A77"
                      + timeA;
              String textB =
                  (lowerCase.getValue() ? nameB.toLowerCase() : nameB)
                      + (b.getAmplifier() > 0 ? " " + (b.getAmplifier() + 1) : "")
                      + " \u00A77"
                      + timeB;
              return Float.compare(-font.getStringWidth(textA), -font.getStringWidth(textB));
            });

        for (net.minecraft.potion.PotionEffect effect : sortedEffects) {
          net.minecraft.potion.Potion potion =
              net.minecraft.potion.Potion.potionTypes[effect.getPotionID()];
          if (potion == null) continue;

          String name = net.minecraft.client.resources.I18n.format(potion.getName());
          if (lowerCase.getValue()) name = name.toLowerCase();
          if (effect.getAmplifier() > 0) name += " " + (effect.getAmplifier() + 1);

          String time = net.minecraft.potion.Potion.getDurationString(effect);
          String text = name + " \u00A77" + time;
          int textWidth = font.getStringWidth(text);
          float drawX = sr.getScaledWidth() - 2;

          drawY -= (font.height() + 1.5f);

          float pX = drawX - textWidth - 14 - 2;
          float pY = drawY;
          float pW = textWidth + 14 + 4;
          float pH = font.height() + 1.5f;

          if (this.backgroundAlpha.getValue() > 0) {
            RoundedUtils.drawRound(
                pX, pY, pW, pH, 4f, new Color(0, 0, 0, this.backgroundAlpha.getValue()));
          }
          int effectColor = potion.getLiquidColor() | 0xFF000000;
          font.drawWithShadow(text, drawX - textWidth - 1, drawY, effectColor);

          if (potion.hasStatusIcon()) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            mc.getTextureManager()
                .bindTexture(
                    new ResourceLocation(
                        "textures/gui/container/inventory.png"));
            int iconIndex = potion.getStatusIconIndex();
            net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
                (int) (drawX - textWidth - 14),
                (int) drawY,
                (iconIndex % 8) * 18,
                198 + (iconIndex / 8) * 18,
                18,
                18,
                9,
                9,
                256,
                256);
            GlStateManager.disableBlend();
          }
        }
      }
    }
  }

  private void renderElements(
      long l,
      float delta,
      java.util.List<InterfaceComponent> animatingComponents,
      ScaledResolution sr,
      boolean updateState,
      boolean backgroundsOnly) {
    boolean isMcFont = FontRepository.isMinecraftSelected();
    float heightExhibition = 9.0f + 3.0f;
    float heightNormal = 9.0f + 1.0f;

    if (this.hudMode.getValue() == 3 && !backgroundsOnly) {
      this.renderMiauHUDMode(l, sr);
    }

    if (this.hudMode.getValue() == 2 && !backgroundsOnly) {
      if (this.watermarkPlusFade.getValue()) {
        this.watermarkFade = (float) Math.min(1.0, this.watermarkFade + delta * 0.008);
      } else {
        this.watermarkFade = 1f;
      }
      this.renderWatermarkImage(sr);
    } else {
      this.watermarkFade = (float) Math.max(0.0, this.watermarkFade - delta * 0.008);
    }

    if (this.showWatermark.getValue() && this.hudMode.getValue() != 2 && this.hudMode.getValue() != 3) {
      String watermark = getExhibitionWatermark();
      if (watermark != null) {
        try {
          float wX = 3.0f;
          float wY = 3.0f;
          float wW = mc.fontRendererObj.getStringWidth(watermark);
          float wH = mc.fontRendererObj.FONT_HEIGHT;

          if (this.backgroundAlpha.getValue() > 0) {
            RoundedUtils.drawRound(
                wX - 1.0f,
                wY - 1.0f,
                wW + 2.0f,
                wH + 2.0f,
                4f,
                new Color(0, 0, 0, this.backgroundAlpha.getValue()));
          }
          if (!backgroundsOnly) {
            mc.fontRendererObj.drawStringWithShadow(watermark, wX, wY, this.getColor(l).getRGB());
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    if (this.hudMode.getValue() == 0 && !backgroundsOnly) {
      float yCoord = sr.getScaledHeight() - getFont().getFontHeight() - 2.0F;
      int hudColor = this.getColor(l).getRGB();
      int whiteColor = -1;

      float currentX = 2.0F;
      getFont().drawWithShadow("Version: ", currentX, yCoord, whiteColor);
      currentX += getFont().getStringWidth("Version: ");

      String ver = Miau.version;
      if (ver != null && ver.length() > 0) {
        String firstChar = ver.substring(0, 1);
        String restVer = ver.substring(1);
        getFont().drawWithShadow(firstChar, currentX, yCoord, hudColor);
        currentX += getFont().getStringWidth(firstChar);
        getFont().drawWithShadow(restVer, currentX, yCoord, whiteColor);
        currentX += getFont().getStringWidth(restVer);
      }

      getFont().drawWithShadow(" Username: ", currentX, yCoord, whiteColor);
      currentX += getFont().getStringWidth(" Username: ");

      getFont().drawWithShadow(mc.getSession().getUsername(), currentX, yCoord, hudColor);
    }

    if (this.hudMode.getValue() == 1) {
      if (this.showCoordinates.getValue() && mc.thePlayer != null) {
        String posX2 = String.valueOf(Math.round(mc.thePlayer.posX));
        String posY2 = String.valueOf(Math.round(mc.thePlayer.posY));
        String posZ2 = String.valueOf(Math.round(mc.thePlayer.posZ));
        float yCoord = sr.getScaledHeight() - 10;
        float fontHeight = getFont().getFontHeight();
        int colour = this.getColor(l).getRGB();
        getFont().drawWithShadow("X: \u00A77" + posX2, 3.0F, yCoord - fontHeight * 2, colour);
        getFont().drawWithShadow("Y: \u00A77" + posY2, 3.0F, yCoord - fontHeight, colour);
        getFont().drawWithShadow("Z: \u00A77" + posZ2, 3.0F, yCoord, colour);
      }

      float height = heightExhibition;
      float x = (float) this.offsetX.getValue();
      if (this.posX.getValue() == 1) x = (float) sr.getScaledWidth() - x;

      GlStateManager.pushMatrix();
      GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

      for (InterfaceComponent component : animatingComponents) {
        Module module = component.module;
        String moduleName = this.getModuleName(module);
        String[] moduleSuffix = this.getModuleSuffix(module);
        float totalWidth =
            (float)
                (this.calculateStringWidth(moduleName, moduleSuffix)
                    - (this.shadow.getValue() ? 0 : 1));

        double animProgress = component.animationTime;
        float drawY = (float) component.position.y / this.scale.getValue();
        float baseX = x / this.scale.getValue();
        float targetX;
        boolean shouldBeVisible =
            module.isEnabled()
                && (this.modulesToShow.getValue() == 0 || !module.isHidden())
                && component.shouldDisplay(this);

        if (this.posX.getValue() == 1) {
          targetX = baseX - totalWidth;
          if (!shouldBeVisible) targetX += totalWidth + 20;
        } else {
          targetX = baseX;
          if (!shouldBeVisible) targetX -= totalWidth + 20;
        }

        if (component.position.x == 5000)
          component.position.x = this.posX.getValue() == 1 ? targetX + 50 : targetX - 50;

        if (updateState) {
          component.position.x =
              miau.util.math.MathUtil.lerp((float) component.position.x, targetX, 0.015f * delta);
        }
        float drawX = (float) component.position.x;

        int alpha = (int) (255 * animProgress);
        long finalY = (long) component.position.y;
        int color = (alpha << 24) | (this.getColor(l, finalY).getRGB() & 0x00FFFFFF);
        int bgAlphaVal = (int) (this.backgroundAlpha.getValue() * animProgress);

        if (this.backgroundAlpha.getValue() > 0) {
          RoundedUtils.drawRound(
              drawX - 2.0F,
              drawY - 2.0F,
              totalWidth + 4.0F,
              height + 2.0F,
              4f,
              new Color(0, 0, 0, bgAlphaVal));
        }
        if (!backgroundsOnly) {
          if (this.showBar.getValue()) {
            RenderUtil.enableRenderState();
            if (this.posX.getValue() == 0)
              RenderUtil.drawRect(
                  drawX - 3.0F, drawY - 2.0F, drawX - 2.0F, drawY + height - 2.0F, color);
            else
              RenderUtil.drawRect(
                  drawX + totalWidth + 2.0F,
                  drawY - 2.0F,
                  drawX + totalWidth + 3.0F,
                  drawY + height - 2.0F,
                  color);
            RenderUtil.disableRenderState();
          }

          getFont().drawWithShadow(moduleName, drawX, drawY, color);

          if (this.suffixes.getValue() && moduleSuffix.length > 0) {
            float suffixX = drawX + getFont().getStringWidth(moduleName) + 2.0F;
            int suffixColor = ((int) (170 * animProgress) << 24) | 0x00AAAAAA;
            for (String str : moduleSuffix) {
              getFont().drawWithShadow(str, suffixX, drawY, suffixColor);
              suffixX += getFont().getStringWidth(str) + 2.0F;
            }
          }
        }
      }
      GlStateManager.popMatrix();
    } else {
      float height = heightNormal;
      float x =
          (float) this.offsetX.getValue()
              + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F))
                  * this.scale.getValue();
      if (this.posX.getValue() == 1) x = (float) sr.getScaledWidth() - x;

      GlStateManager.pushMatrix();
      GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

      for (InterfaceComponent component : animatingComponents) {
        Module module = component.module;
        String moduleName = this.getModuleName(module);
        String[] moduleSuffix = this.getModuleSuffix(module);
        float totalWidth =
            (float)
                (this.calculateStringWidth(moduleName, moduleSuffix)
                    - (this.shadow.getValue() ? 0 : 1));

        double animProgress = component.animationTime;
        float drawY = (float) component.position.y / this.scale.getValue();
        float baseX = x / this.scale.getValue();
        float targetX;
        boolean shouldBeVisible =
            module.isEnabled()
                && (this.modulesToShow.getValue() == 0 || !module.isHidden())
                && component.shouldDisplay(this);

        if (this.posX.getValue() == 1) {
          targetX = baseX - totalWidth;
          if (!shouldBeVisible) targetX += totalWidth + 20;
        } else {
          targetX = baseX;
          if (!shouldBeVisible) targetX -= totalWidth + 20;
        }

        if (component.position.x == 5000)
          component.position.x = this.posX.getValue() == 1 ? targetX + 50 : targetX - 50;

        if (updateState) {
          component.position.x =
              miau.util.math.MathUtil.lerp((float) component.position.x, targetX, 0.015f * delta);
        }
        float drawX = (float) component.position.x;

        int alpha = (int) (255 * animProgress);

        long finalY = (long) component.position.y;
        int color = (alpha << 24) | (this.getColor(l, finalY).getRGB() & 0x00FFFFFF);
        int bgAlphaVal = (int) (this.backgroundAlpha.getValue() * animProgress);

        if (this.backgroundAlpha.getValue() > 0) {
          RoundedUtils.drawRound(
              drawX - 1.0F,
              drawY - 1.0F,
              totalWidth + 2.0F,
              height + 2.0F,
              4f,
              new Color(0, 0, 0, bgAlphaVal));
        }

        if (!backgroundsOnly) {
          if (this.showBar.getValue()) {
            RenderUtil.enableRenderState();
            if (this.shadow.getValue()) {
              RenderUtil.drawRect(
                  drawX + (this.posX.getValue() == 0 ? -3.0F : totalWidth + 1.0F),
                  drawY - (this.posY.getValue() == 0 ? (finalY == 0L ? 1.0F : 0.0F) : 1.0F),
                  drawX + (this.posX.getValue() == 0 ? -2.0F : totalWidth + 2.0F),
                  drawY
                      + height
                      + (this.posY.getValue() == 0 ? 1.0F : (finalY == 0L ? 1.0F : 0.0F)),
                  color);
            } else {
              RenderUtil.drawRect(
                  drawX + (this.posX.getValue() == 0 ? -2.0F : totalWidth + 1.0F),
                  drawY - (this.posY.getValue() == 0 ? (finalY == 0L ? 1.0F : 0.0F) : 0.0F),
                  drawX + (this.posX.getValue() == 0 ? -1.0F : totalWidth + 2.0F),
                  drawY
                      + height
                      + (this.posY.getValue() == 0 ? 0.0F : (finalY == 0L ? 1.0F : 0.0F)),
                  color);
            }
            RenderUtil.disableRenderState();
          }

          GlStateManager.disableDepth();

          if (this.shadow.getValue()) getFont().drawWithShadow(moduleName, drawX, drawY, color);
          else
            getFont()
                .draw(
                    moduleName,
                    drawX,
                    drawY + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                    color,
                    false);

          if (this.suffixes.getValue() && moduleSuffix.length > 0) {
            float width = (float) getFont().getStringWidth(moduleName) + 3.0F;
            int suffixColor = ((int) (160 * animProgress) << 24) | 0x00AAAAAA;
            for (String string : moduleSuffix) {
              if (this.shadow.getValue())
                getFont().drawWithShadow(string, drawX + width, drawY, suffixColor);
              else
                getFont()
                    .draw(
                        string,
                        drawX + width,
                        drawY + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                        suffixColor,
                        false);
              width +=
                  (float) getFont().getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
            }
          }
        }
      }

      if (this.blinkTimer.getValue() && !backgroundsOnly) {
        BlinkModules blinkingModule = Miau.blinkManager.getBlinkingModule();
        if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
          long movementPacketSize = Miau.blinkManager.countMovement();
          if (movementPacketSize > 0L) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            getFont()
                .draw(
                    String.valueOf(movementPacketSize),
                    (float) sr.getScaledWidth() / 2.0F / this.scale.getValue()
                        - (float) getFont().getStringWidth(String.valueOf(movementPacketSize))
                            / 2.0F,
                    (float) sr.getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                    this.getColor(l, 0L).getRGB() & 16777215 | -1090519040,
                    this.shadow.getValue());
            GlStateManager.disableBlend();
          }
        }
      }
      GlStateManager.enableDepth();
      GlStateManager.popMatrix();
    }
  }

  private void renderWatermarkImage(ScaledResolution sr) {
    float scaleFactor = this.watermarkPlusScale.getValue();
    int drawWidth = (int) (WATERMARK_TEXTURE_WIDTH * scaleFactor);
    int drawHeight = (int) (WATERMARK_TEXTURE_HEIGHT * scaleFactor);
    int x = (int) (float) this.watermarkPlusX.getValue();
    int y = (int) (float) this.watermarkPlusY.getValue();
    float alpha = this.watermarkPlusOpacity.getValue() / 255.0f * this.watermarkFade;

    if (this.watermarkPlusDrag.getValue() && mc.currentScreen instanceof GuiChat) {
      this.dragWatermark(sr, drawWidth, drawHeight);
      x = (int) (float) this.watermarkPlusX.getValue();
      y = (int) (float) this.watermarkPlusY.getValue();
    }

    float tr = 1.0f, tg = 1.0f, tb = 1.0f;
    switch (this.watermarkPlusColorMode.getValue()) {
      case 1:
        Color theme = this.getColor(System.currentTimeMillis());
        tr = theme.getRed() / 255.0f;
        tg = theme.getGreen() / 255.0f;
        tb = theme.getBlue() / 255.0f;
        break;
      case 2:
        Color custom = new Color(this.watermarkPlusColor.getValue());
        tr = custom.getRed() / 255.0f;
        tg = custom.getGreen() / 255.0f;
        tb = custom.getBlue() / 255.0f;
        break;
      case 3:
        Color rainbow = ColorUtil.rainbow((int) (System.currentTimeMillis() / 10));
        tr = rainbow.getRed() / 255.0f;
        tg = rainbow.getGreen() / 255.0f;
        tb = rainbow.getBlue() / 255.0f;
        break;
      default:
        break;
    }

    float shadowOffset = this.watermarkPlusShadowOffset.getValue();
    float shadowAlpha = this.watermarkPlusShadowOpacity.getValue() / 255.0f * alpha;
    switch (this.watermarkPlusStyle.getValue()) {
      case 1:
        this.drawWatermarkImage(x + (int) shadowOffset, y + (int) shadowOffset, drawWidth, drawHeight, 0f, 0f, 0f, shadowAlpha);
        break;
      case 2:
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) continue;
            this.drawWatermarkImage(
                x + (int) (dx * shadowOffset), y + (int) (dy * shadowOffset), drawWidth, drawHeight, 0f, 0f, 0f, shadowAlpha);
          }
        }
        break;
      case 3:
        float gap = this.watermarkPlusReflectionGap.getValue();
        float reflectionAlpha = this.watermarkPlusReflectionOpacity.getValue() / 255.0f * alpha;
        this.drawWatermarkImageFlipped(
            x, y + drawHeight + (int) gap, drawWidth, drawHeight, tr, tg, tb, reflectionAlpha);
        break;
      default:
        break;
    }

    this.drawWatermarkImage(x, y, drawWidth, drawHeight, tr, tg, tb, alpha);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.disableBlend();
  }

  private void drawWatermarkImage(
      int x, int y, int width, int height, float r, float g, float b, float a) {
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.color(r, g, b, a);
    mc.getTextureManager().bindTexture(WATERMARK_IMAGE);
    net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(
        x, y, 0, 0, width, height, width, height);
  }

  private void drawWatermarkImageFlipped(
      int x, int y, int width, int height, float r, float g, float b, float a) {
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y + height, 0);
    GlStateManager.scale(1.0f, -1.0f, 1.0f);
    this.drawWatermarkImage(0, 0, width, height, r, g, b, a);
    GlStateManager.popMatrix();
  }

  private void dragWatermark(ScaledResolution sr, int width, int height) {
    float sf = sr.getScaleFactor();
    int x = (int) (float) this.watermarkPlusX.getValue();
    int y = (int) (float) this.watermarkPlusY.getValue();
    if (Mouse.isButtonDown(0) && this.watermarkFirstClick) {
      this.firstWX = (int) (Mouse.getX() / sf);
      this.firstWY = (int) ((sr.getScaledHeight() * 2 - Mouse.getY()) / sf);
      this.watermarkFirstClick = false;
      if (this.firstWX >= x
          && this.firstWX <= x + width
          && this.firstWY >= y
          && this.firstWY <= y + height) {
        this.watermarkTrack = true;
      }
    }
    if (!Mouse.isButtonDown(0)) {
      this.watermarkFirstClick = true;
      this.watermarkTrack = false;
    }
    if (this.watermarkTrack) {
      int mx = (int) (Mouse.getX() / sf);
      int my = (int) ((sr.getScaledHeight() * 2 - Mouse.getY()) / sf);
      int deltaX = mx - this.firstWX;
      int deltaY = my - this.firstWY;
      this.watermarkPlusX.setValue(this.watermarkPlusX.getValue() + deltaX);
      this.watermarkPlusY.setValue(this.watermarkPlusY.getValue() + deltaY);
      this.firstWX = mx;
      this.firstWY = my;
    }
  }

  private String toRoman(int value) {
    switch (value) {
      case 2:
        return "II";
      case 3:
        return "III";
      case 4:
        return "IV";
      case 5:
        return "V";
      default:
        return String.valueOf(value);
    }
  }
}