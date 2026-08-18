package miau.module.modules.render.targethud;

import java.awt.Color;
import miau.Miau;
import miau.enums.ChatColors;
import miau.module.modules.render.TargetHUD;
import miau.util.player.TeamUtil;
import miau.util.render.ColorUtil;
import miau.util.render.RenderUtil;
import miau.util.render.ShapeUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class MyauMode extends TargetHUDMode {
  private ResourceLocation headTexture = null;
  private final TimerUtil animTimer = new TimerUtil();
  private float oldHealth = 0.0F;
  private float newHealth = 0.0F;
  private float maxHealth = 1.0F;
  private EntityLivingBase lastTarget = null;

  public MyauMode(TargetHUD targetHUD) {
    super(targetHUD);
  }

  public void reset() {
    this.headTexture = null;
    this.oldHealth = 0.0F;
    this.newHealth = 0.0F;
    this.maxHealth = 1.0F;
    this.lastTarget = null;
  }

  private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
    if (entityLivingBase instanceof EntityPlayer) {
      NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
      if (playerInfo != null) {
        return playerInfo.getLocationSkin();
      }
    }
    return null;
  }

  private Color getTargetColor(EntityLivingBase entityLivingBase) {
    if (entityLivingBase instanceof EntityPlayer) {
      if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
        return Miau.friendManager.getColor();
      }
      if (TeamUtil.isTarget((EntityPlayer) entityLivingBase)) {
        return Miau.targetManager.getColor();
      }
    }
    switch (parent.color.getValue()) {
      case 0:
        return ColorUtil.getHealthBlend(
            entityLivingBase.getHealth() / entityLivingBase.getMaxHealth());
      case 1:
        return new Color(0, 0, 0); // fallback for HUD color
      case 2:
        return new Color(255, 255, 255); // fallback
    }
    return Color.WHITE;
  }

  @Override
  public void render(EntityLivingBase target, float x, float y) {
    if (target == null || target.isDead || target.isInvisible()) return;

    double[] screenPos = projectTo2D(target);
    if (screenPos == null) return;

    float distanceScale = calculateDistanceScale(target);
    float totalScale = distanceScale * parent.scale.getValue();

    float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
    float abs = target.getAbsorptionAmount() / 2.0F;
    float heal = target.getHealth() / 2.0F + abs;

    if (target != lastTarget) {
      this.headTexture = null;
      this.animTimer.setTime();
      this.oldHealth = heal;
      this.newHealth = heal;
      this.lastTarget = target;
    }

    if (!parent.animations.getValue() || this.animTimer.hasTimeElapsed(150L)) {
      this.oldHealth = this.newHealth;
      this.newHealth = heal;
      this.maxHealth = target.getMaxHealth() / 2.0F;
      if (this.oldHealth != this.newHealth) {
        this.animTimer.reset();
      }
    }

    ResourceLocation resourceLocation = this.getSkin(target);
    if (resourceLocation != null) {
      this.headTexture = resourceLocation;
    }

    float elapsedTime = (float) Math.min(Math.max(this.animTimer.getElapsedTime(), 0L), 150L);
    float healthRatio =
        Math.min(
            Math.max(
                RenderUtil.lerpFloat(this.newHealth, this.oldHealth, elapsedTime / 150.0F)
                    / this.maxHealth,
                0.0F),
            1.0F);
    Color targetColor = this.getTargetColor(target);
    Color healthBarColor =
        parent.color.getValue() == 0 ? ColorUtil.getHealthBlend(healthRatio) : targetColor;
    float healthDeltaRatio = Math.min(Math.max((health - heal + 1.0F) / 2.0F, 0.0F), 1.0F);
    Color healthDeltaColor = ColorUtil.getHealthBlend(healthDeltaRatio);

    String targetNameText =
        ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(target)));
    int targetNameWidth = mc.fontRendererObj.getStringWidth(targetNameText);
    String healthText =
        ChatColors.formatColor(
            String.format("&r&f%s%s\u2764&r", healthFormat.format(heal), abs > 0.0F ? "&6" : "&c"));
    int healthTextWidth = mc.fontRendererObj.getStringWidth(healthText);
    String statusText =
        ChatColors.formatColor(
            String.format("&r&l%s&r", heal == health ? "D" : (heal < health ? "W" : "L")));
    int statusTextWidth = mc.fontRendererObj.getStringWidth(statusText);
    String healthDiffText =
        ChatColors.formatColor(
            String.format("&r%s&r", heal == health ? "0.0" : diffFormat.format(health - heal)));
    int healthDiffWidth = mc.fontRendererObj.getStringWidth(healthDiffText);
    float barContentWidth =
        Math.max(
            (float) targetNameWidth
                + (parent.indicator.getValue() ? 2.0F + (float) statusTextWidth + 2.0F : 0.0F),
            (float) healthTextWidth
                + (parent.indicator.getValue() ? 2.0F + (float) healthDiffWidth + 2.0F : 0.0F));
    float headIconOffset = parent.head.getValue() && this.headTexture != null ? 25.0F : 0.0F;
    float barTotalWidth =
        Math.max(headIconOffset + 70.0F, headIconOffset + 2.0F + barContentWidth + 2.0F);

    float posX = (float) (screenPos[0] / totalScale) - (barTotalWidth / 2.0f);
    float posY = (float) (screenPos[1] / totalScale) + 10.0f;

    GlStateManager.pushMatrix();
    GlStateManager.scale(totalScale, totalScale, 1.0F);
    GlStateManager.translate(posX, posY, -450.0F);
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(770, 771);
    GlStateManager.disableAlpha();
    GlStateManager.disableDepth();
    GlStateManager.disableTexture2D();
    int backgroundColor =
        new Color(0.0F, 0.0F, 0.0F, (float) parent.background.getValue() / 100.0F).getRGB();
    int outlineColor =
        parent.outline.getValue() ? targetColor.getRGB() : new Color(0, 0, 0, 0).getRGB();
    ShapeUtil.drawOutlineRect(
        0.0F, 0.0F, barTotalWidth, 27.0F, 1.5F, backgroundColor, outlineColor);
    ShapeUtil.drawRect(
        headIconOffset + 2.0F,
        22.0F,
        barTotalWidth - 2.0F,
        25.0F,
        ColorUtil.darker(healthBarColor, 0.2F).getRGB());
    ShapeUtil.drawRect(
        headIconOffset + 2.0F,
        22.0F,
        headIconOffset + 2.0F + healthRatio * (barTotalWidth - 2.0F - headIconOffset - 2.0F),
        25.0F,
        healthBarColor.getRGB());
    GlStateManager.enableTexture2D();
    GlStateManager.enableAlpha();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    mc.fontRendererObj.drawString(
        targetNameText, headIconOffset + 2.0F, 2.0F, -1, parent.shadow.getValue());
    mc.fontRendererObj.drawString(
        healthText, headIconOffset + 2.0F, 12.0F, -1, parent.shadow.getValue());
    if (parent.indicator.getValue()) {
      mc.fontRendererObj.drawString(
          statusText,
          barTotalWidth - 2.0F - (float) statusTextWidth,
          2.0F,
          healthDeltaColor.getRGB(),
          parent.shadow.getValue());
      mc.fontRendererObj.drawString(
          healthDiffText,
          barTotalWidth - 2.0F - (float) healthDiffWidth,
          12.0F,
          ColorUtil.darker(healthDeltaColor, 0.8F).getRGB(),
          parent.shadow.getValue());
    }
    if (parent.head.getValue() && this.headTexture != null) {
      GlStateManager.color(1.0F, 1.0F, 1.0F);
      mc.getTextureManager().bindTexture(this.headTexture);
      net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
          2, 2, 8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
      net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
          2, 2, 40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
      GlStateManager.color(1.0F, 1.0F, 1.0F);
    }
    GlStateManager.disableBlend();
    GlStateManager.enableDepth();
    GlStateManager.popMatrix();
  }
}