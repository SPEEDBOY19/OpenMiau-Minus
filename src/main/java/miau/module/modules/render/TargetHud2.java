package miau.module.modules.render;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PlayerUpdateEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.Render3DEvent;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.render.RenderUtil;
import miau.util.shader.BlurUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class TargetHud2 extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
  public static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
  public static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);

  private final String[] targetHuds = {"Astolfo", "Old Astolfo", "Very Old Astolfo"};

  private final Color cherry1 = new Color(243, 58, 106);
  private final Color cherry2 = new Color(253, 178, 185);
  private final Color cottonCandy1 = new Color(135, 215, 243);
  private final Color cottonCandy2 = new Color(254, 104, 204);
  private final Color flare1 = new Color(241, 39, 17);
  private final Color flare2 = new Color(244, 169, 24);
  private final Color flower1 = new Color(211, 91, 231);
  private final Color flower2 = new Color(214, 158, 231);
  private final Color gold1 = new Color(254, 252, 193);
  private final Color gold2 = new Color(255, 250, 53);
  private final Color greyScale1 = new Color(116, 116, 116);
  private final Color greyScale2 = new Color(186, 186, 186);
  private final Color royal1 = new Color(109, 182, 229);
  private final Color royal2 = new Color(33, 73, 166);
  private final Color sky1 = new Color(44, 220, 247);
  private final Color sky2 = new Color(139, 253, 249);
  private final Color vine1 = new Color(28, 255, 49);
  private final Color vine2 = new Color(171, 255, 172);

  private final Color[][] accents = {
    {null, null},
    {this.cherry1, this.cherry2},
    {this.cottonCandy1, this.cottonCandy2},
    {this.flare1, this.flare2},
    {this.flower1, this.flower2},
    {this.gold1, this.gold2},
    {this.greyScale1, this.greyScale2},
    {this.royal1, this.royal2},
    {this.sky1, this.sky2},
    {this.vine1, this.vine2}
  };

  private final float astolfoEndX = 150;
  private final float astolfoEndY = 50;
  private final float veryOldAstolfoEndX = 305 / 2f;
  private final float veryOldAstolfoEndY = 107 / 2f;
  private final float oldAstolfoEndX = 253 / 2f;
  private final float oldAstolfoEndY = 90 / 2f;

  private Color accent = new Color(0, 0, 0, 255);

  private float adjustedX, adjustedY;
  private int x, y;
  private int firstX, firstY;
  private int dragX, dragY;
  private boolean track = false;
  private boolean firstClick = true;
  private float timeMultiplier, offset, rangeSwing, followPlayerOffsetY, blurBackgroundRadius,
      borderRadius, outerBorderRadius;
  private boolean traditionHealthColor, followPlayer, showHudOnlyOnSwing, blurBackground, inChatOF;
  private int theme, borderRadiusPercent, screenFollowPlayerOffsetX, screenFollowPlayerOffsetY,
      backgroundBrightness, backgroundOpacity, blurBackgroundPasses, background, targetHud;

  public final ModeProperty targetHudMode =
      new ModeProperty("Target hud", 0, this.targetHuds);
  public final IntProperty borderRadiusPct = new IntProperty("Border radius", 25, 0, 50);
  public final IntProperty backgroundBrightnessPct =
      new IntProperty("Background brightness", 0, 0, 100);
  public final IntProperty backgroundOpacityValue = new IntProperty("Background opacity", 150, 0, 255);
  public final BooleanProperty blurBackgroundValue = new BooleanProperty("Blur background", true);
  public final IntProperty passesValue = new IntProperty("Passes", 5, 0, 10);
  public final IntProperty radiusValue = new IntProperty("Radius", 5, 0, 10);
  public final BooleanProperty showHudOnlyOnSwingValue =
      new BooleanProperty("Show hud only on swing", true);
  public final BooleanProperty followPlayerValue = new BooleanProperty("Follow player", false);
  public final IntProperty screenFollowOffsetX = new IntProperty("Screen x offset", 0, -200, 200);
  public final IntProperty screenFollowOffsetY = new IntProperty("Screen y offset", 0, -200, 200);
  public final FloatProperty followPlayerYOffset = new FloatProperty("Y offset", -0.5F, -5.0F, 5.0F);
  public final FloatProperty offsetValue = new FloatProperty("Offset", 1.0F, 0.0F, 10.0F);
  public final FloatProperty timeMultiplierValue =
      new FloatProperty("Time multiplier", 1.0F, 0.1F, 5.0F);
  public final ModeProperty themeProperty =
      new ModeProperty(
          "Theme",
          0,
          new String[] {
            "Default", "Cherry", "Cotton Candy", "Flare", "Flower",
            "Gold", "Grayscale", "Royal", "Sky", "Vine"
          });
  public final IntProperty dragXProperty = new IntProperty("Drag X", 963, 0, 10000);
  public final IntProperty dragYProperty = new IntProperty("Drag Y", 565, 0, 10000);

  public TargetHud2() {
    super("TargetHud2", false, true);
    this.dragX = this.dragXProperty.getValue();
    this.dragY = this.dragYProperty.getValue();
    this.x = this.dragX;
    this.y = this.dragY;
    this.adjustedX = this.x / 2.0f;
    this.adjustedY = this.y / 2.0f;
  }

  @Override
  public void onEnabled() {
    this.updateComponents();
    this.updatePaint();
  }

  @EventTarget
  public void onPreUpdate(PlayerUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    this.inChatOF = mc.currentScreen instanceof GuiChat;
    int ticks = mc.thePlayer.ticksExisted;
    if (ticks % 5 == 0) {
      this.updateComponents();
      this.updatePaint();
    }
  }

  private void updateComponents() {
    this.blurBackground = this.blurBackgroundValue.getValue();
    this.dragX = this.dragXProperty.getValue();
    this.dragY = this.dragYProperty.getValue();
    TargetHUD th = (TargetHUD) Miau.moduleManager.modules.get(TargetHUD.class);
    this.traditionHealthColor = th != null && th.healthColor.getValue();
    this.followPlayer = this.followPlayerValue.getValue();
    this.showHudOnlyOnSwing = this.showHudOnlyOnSwingValue.getValue();
    this.offset = this.offsetValue.getValue();
    this.timeMultiplier = this.timeMultiplierValue.getValue();
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    this.rangeSwing = killAura != null ? killAura.attackRange.getValue() : 3.5f;
    this.blurBackgroundRadius = this.radiusValue.getValue();
    this.blurBackgroundPasses = this.passesValue.getValue();
    this.theme = this.themeProperty.getValue();
    this.borderRadiusPercent = this.borderRadiusPct.getValue();
    this.backgroundBrightness = this.backgroundBrightnessPct.getValue();
    this.backgroundOpacity = this.backgroundOpacityValue.getValue();
    this.targetHud = this.targetHudMode.getValue();
    if (this.followPlayer) {
      this.screenFollowPlayerOffsetX = this.screenFollowOffsetX.getValue();
      this.screenFollowPlayerOffsetY = this.screenFollowOffsetY.getValue();
      this.followPlayerOffsetY = this.followPlayerYOffset.getValue();
    }
  }

  private void updatePaint() {
    switch (this.targetHud) {
      case 0:
        this.borderRadius = 7f * this.borderRadiusPercent / 100;
        this.outerBorderRadius = this.borderRadius + 3f;
        break;
      case 1:
        this.borderRadius = 10f * this.borderRadiusPercent / 100;
        this.outerBorderRadius = this.borderRadius + 5f;
        break;
      case 2:
        this.borderRadius = 12f * this.borderRadiusPercent / 100;
        this.outerBorderRadius = this.borderRadius + 3f;
        break;
      default:
        break;
    }
    Color backgroundHSB = Color.getHSBColor(0, 0, this.backgroundBrightness / 100f);
    this.background =
        new Color(
                backgroundHSB.getRed(), backgroundHSB.getGreen(), backgroundHSB.getBlue(), this.backgroundOpacity)
            .getRGB();
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled() || !this.followPlayer) return;
    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
    GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
    GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
  }

  @EventTarget
  public void onRenderTick(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    this.inChatOF = mc.currentScreen instanceof GuiChat;
    if (mc.currentScreen != null && !this.inChatOF) return;

    float renderedHudEndX = 0;
    float renderedHudEndY = 0;

    EntityLivingBase entity = this.getKillAuraTarget();
    EntityLivingBase self = mc.thePlayer;

    switch (this.targetHud) {
      case 0:
        renderedHudEndX = this.astolfoEndX;
        renderedHudEndY = this.astolfoEndY;
        break;
      case 1:
        renderedHudEndX = this.oldAstolfoEndX;
        renderedHudEndY = this.oldAstolfoEndY;
        break;
      case 2:
        renderedHudEndX = this.veryOldAstolfoEndX;
        renderedHudEndY = this.veryOldAstolfoEndY;
        break;
      default:
        break;
    }

    if (!this.followPlayer && this.inChatOF) {
      this.dragLogic((int) renderedHudEndX * 2, (int) renderedHudEndY * 2);
    } else if (!this.inChatOF) {
      this.track = false;
    }

    if (this.inChatOF || entity != null) {
      if (this.inChatOF) {
        entity = self;
      } else if (this.showHudOnlyOnSwing
          && entity != null
          && self.getDistanceToEntity(entity) - 0.5 >= this.rangeSwing) {
        return;
      }

      this.accent =
          this.traditionHealthColor
              ? this.getHealthColor(entity)
              : (this.theme == 0)
                  ? this.getRainbow(1)
                  : (this.theme > 0 && this.theme < this.accents.length)
                      ? this.blendColors(this.accents[this.theme][0], this.accents[this.theme][1], 1)
                      : new Color(0, 0, 0, 255);

      if (this.followPlayer) {
        if (!RenderUtil.isInViewFrustum(entity)) return;
        this.followPlayer(renderedHudEndX, renderedHudEndY, entity, event.getPartialTicks());
      } else if (this.dragX != this.x || this.dragY != this.y) {
        this.x = this.dragX;
        this.y = this.dragY;
        this.adjustedX = this.x / 2.0f;
        this.adjustedY = this.y / 2.0f;
      }

      switch (this.targetHud) {
        case 0:
          this.drawAstolfo(entity);
          break;
        case 1:
          this.drawOldAstolfo(entity);
          break;
        case 2:
          this.drawVeryOldAstolfo(entity);
          break;
        default:
          break;
      }
    }
  }

  private void drawAstolfo(EntityLivingBase entity) {
    if (this.borderRadius != 0) {
      if (this.blurBackground) {
        BlurUtils.prepareBlur();
        RenderUtil.drawRoundedRectangle(
            this.adjustedX,
            this.adjustedY,
            this.astolfoEndX + this.adjustedX,
            this.astolfoEndY + this.adjustedY,
            this.outerBorderRadius,
            -1);
        BlurUtils.blurEnd(this.blurBackgroundPasses, this.blurBackgroundRadius);
      }
      RenderUtil.drawRoundedRectangle(
          this.adjustedX,
          this.adjustedY,
          this.astolfoEndX + this.adjustedX,
          this.astolfoEndY + this.adjustedY,
          this.outerBorderRadius,
          this.background);
      RenderUtil.drawRoundedRectangle(
          30 + this.adjustedX,
          40 + this.adjustedY,
          117.5f + 29.5f + this.adjustedX,
          7.5f + 39.5f + this.adjustedY,
          this.borderRadius,
          new Color(
                  this.clamp(this.accent.getRed() - 195, 0, 255),
                  this.clamp(this.accent.getGreen() - 195, 0, 255),
                  this.clamp(this.accent.getBlue() - 195, 0, 255),
                  255)
              .getRGB());
      RenderUtil.drawRoundedRectangle(
          30 + this.adjustedX,
          40 + this.adjustedY,
          29.5f + this.adjustedX + (entity.getHealth() / entity.getMaxHealth()) * 117.5f,
          7.5f + 39.5f + this.adjustedY,
          this.borderRadius,
          new Color(
                  this.clamp(this.accent.getRed() - 77, 0, 255),
                  this.clamp(this.accent.getGreen() - 77, 0, 255),
                  this.clamp(this.accent.getBlue() - 77, 0, 255),
                  255)
              .getRGB());
      RenderUtil.drawRoundedRectangle(
          30 + this.adjustedX,
          40 + this.adjustedY,
          this.clampFloat(
              (29.5f + this.adjustedX + (entity.getHealth() / entity.getMaxHealth()) * 117.5f) - 5,
              30 + this.adjustedX,
              this.adjustedX + this.astolfoEndX),
          7.5f + 39.5f + this.adjustedY,
          this.borderRadius,
          new Color(this.accent.getRed(), this.accent.getGreen(), this.accent.getBlue(), 255)
              .getRGB());
    } else {
      if (this.blurBackground) {
        BlurUtils.prepareBlur();
        RenderUtil.drawRect(
            this.adjustedX,
            this.adjustedY,
            this.astolfoEndX + this.adjustedX,
            this.astolfoEndY + this.adjustedY,
            -1);
        BlurUtils.blurEnd(this.blurBackgroundPasses, this.blurBackgroundRadius);
      }
      RenderUtil.drawRect(
          this.adjustedX,
          this.adjustedY,
          this.astolfoEndX + this.adjustedX,
          this.astolfoEndY + this.adjustedY,
          this.background);
      RenderUtil.drawRect(
          30 + this.adjustedX,
          40 + this.adjustedY,
          117.5f + 29.5f + this.adjustedX,
          7.5f + 39.5f + this.adjustedY,
          new Color(
                  this.clamp(this.accent.getRed() - 195, 0, 255),
                  this.clamp(this.accent.getGreen() - 195, 0, 255),
                  this.clamp(this.accent.getBlue() - 195, 0, 255),
                  255)
              .getRGB());
      RenderUtil.drawRect(
          30 + this.adjustedX,
          40 + this.adjustedY,
          29.5f + this.adjustedX + (entity.getHealth() / entity.getMaxHealth()) * 117.5f,
          7.5f + 39.5f + this.adjustedY,
          new Color(
                  this.clamp(this.accent.getRed() - 77, 0, 255),
                  this.clamp(this.accent.getGreen() - 77, 0, 255),
                  this.clamp(this.accent.getBlue() - 77, 0, 255),
                  255)
              .getRGB());
      RenderUtil.drawRect(
          30 + this.adjustedX,
          40 + this.adjustedY,
          this.clampFloat(
              (29.5f + this.adjustedX + (entity.getHealth() / entity.getMaxHealth()) * 117.5f) - 5,
              30 + this.adjustedX,
              this.adjustedX + this.astolfoEndX),
          7.5f + 39.5f + this.adjustedY,
          new Color(this.accent.getRed(), this.accent.getGreen(), this.accent.getBlue(), 255)
              .getRGB());
    }
    this.drawScaledText(
        this.formatDoubleStr(Math.round(10 * entity.getHealth() / 2) / 10.0),
        30 + this.adjustedX,
        17.5f + this.adjustedY,
        2,
        new Color(this.accent.getRed(), this.accent.getGreen(), this.accent.getBlue(), 255).getRGB(),
        true);
    this.drawScaledText(entity.getName(), 30 + this.adjustedX, 5 + this.adjustedY, 1, 0xFFFFFFFF, true);
    this.renderEntity(
        entity, 10 + 5 + (int) this.adjustedX, 40 + 5 + (int) this.adjustedY, -200, 0, 20);
    if (this.track && this.borderRadius != 0) {
      this.drawRoundedRectOutline(
          this.adjustedX,
          this.adjustedY,
          this.oldAstolfoEndX + this.adjustedX,
          this.oldAstolfoEndY + this.adjustedY,
          this.outerBorderRadius,
          1,
          0x96FFFFFF);
    } else if (this.track) {
      this.drawRectOutline(
          this.adjustedX, this.adjustedY, this.oldAstolfoEndX + this.adjustedX, this.oldAstolfoEndY + this.adjustedY, 1, 0x96FFFFFF);
    }
  }

  private void drawOldAstolfo(EntityLivingBase entity) {
    if (this.borderRadius != 0) {
      if (this.blurBackground) {
        BlurUtils.prepareBlur();
        RenderUtil.drawRoundedRectangle(
            this.adjustedX,
            this.adjustedY,
            this.oldAstolfoEndX + this.adjustedX,
            this.oldAstolfoEndY + this.adjustedY,
            this.outerBorderRadius,
            -1);
        BlurUtils.blurEnd(this.blurBackgroundPasses, this.blurBackgroundRadius);
      }
      RenderUtil.drawRoundedRectangle(
          this.adjustedX,
          this.adjustedY,
          this.oldAstolfoEndX + this.adjustedX,
          this.oldAstolfoEndY + this.adjustedY,
          this.outerBorderRadius,
          this.background);
      RenderUtil.drawRoundedRectangle(
          25 + this.adjustedX,
          15 + this.adjustedY,
          25 + this.adjustedX + (entity.getHealth() / entity.getMaxHealth()) * 192 / 2,
          15 + 10 + this.adjustedY,
          this.borderRadius,
          this.accent.getRGB());
    } else {
      if (this.blurBackground) {
        BlurUtils.prepareBlur();
        RenderUtil.drawRect(
            this.adjustedX,
            this.adjustedY,
            this.oldAstolfoEndX + this.adjustedX,
            this.oldAstolfoEndY + this.adjustedY,
            -1);
        BlurUtils.blurEnd(this.blurBackgroundPasses, this.blurBackgroundRadius);
      }
      RenderUtil.drawRect(
          this.adjustedX,
          this.adjustedY,
          this.oldAstolfoEndX + this.adjustedX,
          this.oldAstolfoEndY + this.adjustedY,
          this.background);
      RenderUtil.drawRect(
          25 + this.adjustedX,
          15 + this.adjustedY,
          25 + this.adjustedX + (entity.getHealth() / entity.getMaxHealth()) * 192 / 2,
          15 + 10 + this.adjustedY,
          this.accent.getRGB());
    }
    this.drawScaledText(
        this.formatDoubleStr(Math.round(10 * entity.getHealth() / 2) / 10.0),
        25 + 40 + this.adjustedX,
        16 + this.adjustedY,
        1,
        this.accent.getRGB(),
        true);
    this.drawScaledText(entity.getName(), 25 + this.adjustedX, 3 + this.adjustedY, 1, 0xFFFFFFFF, true);
    this.renderEntity(
        entity, 10 + 3 + (int) this.adjustedX, 40 + 3 + (int) this.adjustedY, 200, -entity.rotationPitch, 20);
    if (this.track && this.borderRadius != 0) {
      this.drawRoundedRectOutline(
          this.adjustedX,
          this.adjustedY,
          this.oldAstolfoEndX + this.adjustedX,
          this.oldAstolfoEndY + this.adjustedY,
          this.outerBorderRadius,
          1,
          0x96FFFFFF);
    } else if (this.track) {
      this.drawRectOutline(
          this.adjustedX, this.adjustedY, this.oldAstolfoEndX + this.adjustedX, this.oldAstolfoEndY + this.adjustedY, 1, 0x96FFFFFF);
    }
  }

  private void drawVeryOldAstolfo(EntityLivingBase entity) {
    if (this.borderRadius != 0) {
      if (this.blurBackground) {
        BlurUtils.prepareBlur();
        RenderUtil.drawRoundedRectangle(
            this.adjustedX,
            this.adjustedY,
            this.veryOldAstolfoEndX + this.adjustedX,
            this.veryOldAstolfoEndY + this.adjustedY,
            this.outerBorderRadius,
            -1);
        BlurUtils.blurEnd(this.blurBackgroundPasses, this.blurBackgroundRadius);
      }
      RenderUtil.drawRoundedRectangle(
          this.adjustedX,
          this.adjustedY,
          this.veryOldAstolfoEndX + this.adjustedX,
          this.veryOldAstolfoEndY + this.adjustedY,
          this.outerBorderRadius,
          this.background);
      RenderUtil.drawRoundedRectangle(
          33 + this.adjustedX,
          17.5f + this.adjustedY,
          114 + 33 + this.adjustedX,
          17.5f + 12 + this.adjustedY,
          this.borderRadius,
          0xFF000000);
      RenderUtil.drawRoundedRectangle(
          33 + this.adjustedX,
          17.5f + this.adjustedY,
          33 + this.adjustedX + (entity.getHealth() / entity.getMaxHealth()) * 114,
          17.5f + 12 + this.adjustedY,
          this.borderRadius,
          this.accent.getRGB());
    } else {
      if (this.blurBackground) {
        BlurUtils.prepareBlur();
        RenderUtil.drawRect(
            this.adjustedX,
            this.adjustedY,
            this.veryOldAstolfoEndX + this.adjustedX,
            this.veryOldAstolfoEndY + this.adjustedY,
            -1);
        BlurUtils.blurEnd(this.blurBackgroundPasses, this.blurBackgroundRadius);
      }
      RenderUtil.drawRect(
          this.adjustedX,
          this.adjustedY,
          this.veryOldAstolfoEndX + this.adjustedX,
          this.veryOldAstolfoEndY + this.adjustedY,
          this.background);
      RenderUtil.drawRect(
          33 + this.adjustedX,
          17.5f + this.adjustedY,
          114 + 33 + this.adjustedX,
          17.5f + 12 + this.adjustedY,
          0xFF000000);
      RenderUtil.drawRect(
          33 + this.adjustedX,
          17.5f + this.adjustedY,
          33 + this.adjustedX + (entity.getHealth() / entity.getMaxHealth()) * 114,
          17.5f + 12 + this.adjustedY,
          this.accent.getRGB());
    }
    this.drawScaledText(
        this.formatDoubleStr(Math.round(10 * entity.getHealth() / 2) / 10.0),
        33 + 53 + this.adjustedX,
        17.5f + 1.5f + this.adjustedY,
        1,
        this.accent.getRGB(),
        true);
    this.drawScaledText(entity.getName(), 33 + this.adjustedX, 6.5f + this.adjustedY, 1, 0xFFFFFFFF, true);
    this.drawScaledText(
        "Ping: " + "\u00a78" + this.getPing(entity),
        33 + this.adjustedX,
        6.5f + 26 + this.adjustedY,
        1,
        0xFFFFFFFF,
        true);
    this.renderEntity(
        entity, 10 + 5 + (int) this.adjustedX, 40 + 8 + (int) this.adjustedY, -200, 0, 20);
    if (this.track && this.borderRadius != 0) {
      this.drawRoundedRectOutline(
          this.adjustedX,
          this.adjustedY,
          this.veryOldAstolfoEndX + this.adjustedX,
          this.veryOldAstolfoEndY + this.adjustedY,
          this.outerBorderRadius,
          1,
          0x96FFFFFF);
    } else if (this.track) {
      this.drawRectOutline(
          this.adjustedX, this.adjustedY, this.veryOldAstolfoEndX + this.adjustedX, this.veryOldAstolfoEndY + this.adjustedY, 1, 0x96FFFFFF);
    }
  }

  private int getPing(Entity entity) {
    if (entity instanceof EntityPlayer) {
      try {
        net.minecraft.client.network.NetworkPlayerInfo info =
            mc.getNetHandler().getPlayerInfo(entity.getUniqueID());
        if (info != null) return info.getResponseTime();
      } catch (Exception ignored) {
      }
    }
    return 0;
  }

  private void dragLogic(int offsetX, int offsetY) {
    ScaledResolution displaySize = new ScaledResolution(mc);
    if (Mouse.isButtonDown(0) && this.firstClick) {
      int positionX = Mouse.getX();
      int positionY = displaySize.getScaledHeight() * 2 - Mouse.getY();
      this.firstX = positionX;
      this.firstY = positionY;
      this.firstClick = false;
      if (this.x <= this.firstX
          && this.firstX <= this.x + offsetX
          && this.y <= this.firstY
          && this.firstY <= this.y + offsetY) {
        this.track = true;
      }
    }
    if (!Mouse.isButtonDown(0)) {
      this.firstClick = true;
      this.track = false;
    }
    if (this.track) {
      int positionX = Mouse.getX();
      int positionY = displaySize.getScaledHeight() * 2 - Mouse.getY();
      int deltaX = positionX - this.firstX;
      int deltaY = positionY - this.firstY;
      this.dragX = this.dragX + deltaX;
      this.dragY = this.dragY + deltaY;
      this.x = this.dragX;
      this.y = this.dragY;
      this.adjustedX = this.x / 2.0f;
      this.adjustedY = this.y / 2.0f;
      this.firstX = this.firstX + deltaX;
      this.firstY = this.firstY + deltaY;
      this.dragXProperty.setValue(this.dragX);
      this.dragYProperty.setValue(this.dragY);
    }
  }

  private Color getHealthColor(EntityLivingBase entity) {
    float ratio = entity.getHealth() / entity.getMaxHealth();
    if (ratio >= 0.75) return new Color(3, 213, 2);
    if (ratio >= 0.5) return new Color(212, 212, 1);
    if (ratio <= 0.25) return new Color(229, 2, 1);
    return new Color(212, 167, 1);
  }

  private void followPlayer(float renderedHudEndX, float renderedHudEndY, Entity entity, float partialTicks) {
    double posX = this.interpolate(entity.posX, entity.lastTickPosX, partialTicks);
    double posY = this.interpolate(entity.posY, entity.lastTickPosY, partialTicks);
    double posZ = this.interpolate(entity.posZ, entity.lastTickPosZ, partialTicks);
    double heightOffset =
        posY + (!entity.isSneaking() ? entity.height : entity.height - 0.25) + this.followPlayerOffsetY;
    Vec3 screen =
        this.worldToScreen(posX, heightOffset, posZ);
    if (screen == null) return;
    this.adjustedX = (float) screen.xCoord - renderedHudEndX / 2 + this.screenFollowPlayerOffsetX;
    this.adjustedY = (float) screen.yCoord - renderedHudEndY / 4 + this.screenFollowPlayerOffsetY;
    this.x = (int) (this.adjustedX * 2);
    this.y = (int) (this.adjustedY * 2);
  }

  private Vec3 worldToScreen(double x, double y, double z) {
    ScaledResolution sr = new ScaledResolution(mc);
    java.nio.FloatBuffer winCoords = BufferUtils.createFloatBuffer(3);
    boolean result = GLU.gluProject((float) x, (float) y, (float) z, MODELVIEW, PROJECTION, VIEWPORT, winCoords);
    if (result) {
      float winZ = winCoords.get(2);
      if (winZ >= 0.0F && winZ <= 1.0F) {
        double screenX = winCoords.get(0) / sr.getScaleFactor();
        double screenY = (VIEWPORT.get(3) - winCoords.get(1)) / sr.getScaleFactor();
        return new Vec3(screenX, screenY, 0);
      }
    }
    return null;
  }

  private double interpolate(double current, double old, float scale) {
    return old + (current - old) * scale;
  }

  private void drawRectOutline(float x1, float y1, float x2, float y2, float width, int color) {
    RenderUtil.drawLine(x1, y1, x2, y1, width, color);
    RenderUtil.drawLine(x1, y2, x2, y2, width, color);
    RenderUtil.drawLine(x1, y1, x1, y2, width, color);
    RenderUtil.drawLine(x2, y1, x2, y2, width, color);
  }

  private void drawRoundedRectOutline(
      float x1, float y1, float x2, float y2, float radius, float width, int color) {
    if (x1 > x2) {
      float temp = x1;
      x1 = x2;
      x2 = temp;
    }
    if (y1 > y2) {
      float temp = y1;
      y1 = y2;
      y2 = temp;
    }

    float rectX1 = x1 + radius;
    float rectY1 = y1 + radius;
    float rectX2 = x2 - radius;
    float rectY2 = y2 - radius;

    RenderUtil.drawLine(rectX1, y1, rectX2, y1, width, color);
    RenderUtil.drawLine(rectX1, y2, rectX2, y2, width, color);
    RenderUtil.drawLine(x1, rectY1, x1, rectY2, width, color);
    RenderUtil.drawLine(x2, rectY1, x2, rectY2, width, color);

    double degree = Math.PI / 180;
    for (int corner = 0; corner < 4; corner++) {
      double centerX = (corner < 2) ? rectX2 : rectX1;
      double centerY = (corner % 3 == 0) ? rectY2 : rectY1;
      double startAngle = 90 * corner;
      double endAngle = startAngle + 90;
      int segments = (int) (endAngle - startAngle);
      for (int i = 0; i < segments; i++) {
        double angle1 = (startAngle + i) * degree;
        double angle2 = (startAngle + i + 1) * degree;
        double xStart = centerX + Math.sin(angle1) * radius;
        double yStart = centerY + Math.cos(angle1) * radius;
        double xEnd = centerX + Math.sin(angle2) * radius;
        double yEnd = centerY + Math.cos(angle2) * radius;
        RenderUtil.drawLine((float) xStart, (float) yStart, (float) xEnd, (float) yEnd, width, color);
      }
    }
  }

  private Color getRainbow(int i) {
    float hue =
        ((System.currentTimeMillis() + i * (int) (10 * this.offset))
                % (int) (15000 / this.timeMultiplier))
            / (float) (15000 / this.timeMultiplier);
    return Color.getHSBColor(hue, 1f, 1f);
  }

  private double getWaveRatio(int i) {
    float time =
        ((System.currentTimeMillis() + i * (int) (10 * this.offset))
                % (int) (3000 / this.timeMultiplier))
            / (float) (3000 / this.timeMultiplier);
    return (time <= 0.5) ? (time * 2) : (2 - time * 2);
  }

  private Color blendColors(Color color1, Color color2, int i) {
    double ratio = this.getWaveRatio(i);
    int r = this.clamp((int) (color1.getRed() * ratio + color2.getRed() * (1 - ratio)), 0, 255);
    int g = this.clamp((int) (color1.getGreen() * ratio + color2.getGreen() * (1 - ratio)), 0, 255);
    int b = this.clamp((int) (color1.getBlue() * ratio + color2.getBlue() * (1 - ratio)), 0, 255);
    return new Color(r, g, b);
  }

  private int clamp(int val, int min, int max) {
    return (val < min) ? min : (val > max) ? max : val;
  }

  private float clampFloat(float val, float min, float max) {
    return (val < min) ? min : (val > max) ? max : val;
  }

  private String formatDoubleStr(double val) {
    return val == (long) val ? Long.toString((long) val) : Double.toString(val);
  }

  private void drawScaledText(String text, float x, float y, float scale, int color, boolean shadow) {
    RenderUtil.scaleStart(x, y, scale);
    if (shadow) {
      mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
    } else {
      mc.fontRendererObj.drawString(text, x, y, color, false);
    }
    RenderUtil.scaleEnd();
  }

  private EntityLivingBase getKillAuraTarget() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (killAura != null) {
      return killAura.getTarget();
    }
    return null;
  }

  private void renderEntity(
      Entity entity, float x, float y, float yaw, float pitch, int zoom) {
    if (!(entity instanceof EntityLivingBase)) return;
    EntityLivingBase entityLivingBase = (EntityLivingBase) entity;

    GlStateManager.resetColor();
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.enableColorMaterial();
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 50.0f);
    GlStateManager.scale(-zoom, zoom, zoom);
    GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f);
    final float renderYawOffset = entityLivingBase.renderYawOffset;
    final float rotationYaw = entityLivingBase.rotationYaw;
    final float rotationPitch = entityLivingBase.rotationPitch;
    final float prevRotationYawHead = entityLivingBase.prevRotationYawHead;
    final float rotationYawHead = entityLivingBase.rotationYawHead;
    GlStateManager.rotate(135.0f, 0.0f, 1.0f, 0.0f);
    RenderHelper.enableStandardItemLighting();
    GlStateManager.rotate(-135.0f, 0.0f, 1.0f, 0.0f);
    GlStateManager.rotate((float) (-Math.atan(pitch / 40.0f) * 20.0), 1.0f, 0.0f, 0.0f);
    entityLivingBase.renderYawOffset = yaw - yaw / yaw * 0.4f;
    entityLivingBase.rotationYaw = yaw - yaw / yaw * 0.4f;
    entityLivingBase.rotationPitch = pitch;
    entityLivingBase.rotationYawHead = entityLivingBase.rotationYaw;
    entityLivingBase.prevRotationYawHead = entityLivingBase.rotationYaw;
    final RenderManager renderManager = mc.getRenderManager();
    renderManager.setPlayerViewY(180.0f);
    renderManager.setRenderShadow(false);
    renderManager.renderEntityWithPosYaw(entityLivingBase, 0.0, 0.0, 0.0, 0.0f, 1.0f);
    renderManager.setRenderShadow(true);
    entityLivingBase.renderYawOffset = renderYawOffset;
    entityLivingBase.rotationYaw = rotationYaw;
    entityLivingBase.rotationPitch = rotationPitch;
    entityLivingBase.prevRotationYawHead = prevRotationYawHead;
    entityLivingBase.rotationYawHead = rotationYawHead;
    GlStateManager.popMatrix();
    RenderHelper.disableStandardItemLighting();
    GlStateManager.disableRescaleNormal();
    GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
    GlStateManager.disableTexture2D();
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    GlStateManager.resetColor();
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.targetHudMode.getModeString()};
  }
}
