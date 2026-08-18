package miau.module.modules.render.targethud;

import java.awt.Color;
import miau.Miau;
import miau.module.modules.render.HUD;
import miau.module.modules.render.TargetHUD;
import miau.util.render.ColorUtil;
import miau.util.render.Themes;
import miau.util.shader.BlurUtils;
import miau.util.shader.RoundedUtils;
import miau.util.time.TimerUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class RavenMode extends TargetHUDMode {

  private TimerUtil fadeTimer;
  private TimerUtil healthBarTimer;
  private long lastAliveMS;
  private double lastHealth;
  private float lastHealthBar;

  public RavenMode(TargetHUD parent) {
    super(parent);
  }

  public void setFadeTimer(TimerUtil fadeTimer) {
    this.fadeTimer = fadeTimer;
  }

  public TimerUtil getFadeTimer() {
    return this.fadeTimer;
  }

  public void setLastAliveMS(long ms) {
    this.lastAliveMS = ms;
  }

  public long getLastAliveMS() {
    return this.lastAliveMS;
  }

  public void setHealthBarTimer(TimerUtil healthBarTimer) {
    this.healthBarTimer = healthBarTimer;
  }

  public double getLastHealth() {
    return this.lastHealth;
  }

  public void setLastHealth(double lastHealth) {
    this.lastHealth = lastHealth;
  }

  public void reset() {
    this.fadeTimer = null;
    this.healthBarTimer = null;
    this.lastAliveMS = 0;
    this.lastHealth = 0;
    this.lastHealthBar = 0;
  }

  @Override
  public void render(EntityLivingBase target, float x, float y) {
    if (target == null || target.isDead || target.isInvisible()) return;

    double[] screenPos = projectTo2D(target);
    if (screenPos == null) return;

    float distanceScale = calculateDistanceScale(target);
    float totalScale = distanceScale * parent.scale.getValue();

    String playerInfo = target.getDisplayName().getFormattedText();
    double health = target.getHealth() / target.getMaxHealth();
    if (target.isDead) {
      health = 0;
    }

    String healthStr =
        " "
            + (target.getHealth() == (int) target.getHealth()
                ? String.valueOf((int) target.getHealth())
                : healthFormat.format(target.getHealth()));

    double healthPct = target.getHealth() / target.getMaxHealth();
    if (healthPct >= 0.5) {
      playerInfo += " \u00A7a" + healthStr.trim();
    } else if (target.getHealth() >= 0.2) {
      playerInfo += " \u00A7e" + healthStr.trim();
    } else {
      playerInfo += " \u00A7c" + healthStr.trim();
    }

    drawTargetHUD(target, playerInfo, health, screenPos, totalScale);
  }

  private void drawTargetHUD(EntityLivingBase target, String string, double health, double[] screenPos, float totalScale) {
    if (parent.showStatus.getValue()) {
      float playerTotalHealth = mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount();
      float playerMaxHealth = mc.thePlayer.getMaxHealth();
      boolean shouldWin = health <= (double) (playerTotalHealth / playerMaxHealth);
      string += shouldWin ? " \u00A7aW" : " \u00A7cL";
    }

    int padding = 8;
    int targetStrWithPadding = mc.fontRendererObj.getStringWidth(string) + padding;

    float boxWidth = targetStrWithPadding + padding * 2;
    float boxHeight = mc.fontRendererObj.FONT_HEIGHT + 12 + padding * 2;

    float absX = (float) (screenPos[0] / totalScale) - (boxWidth / 2.0f);
    float absY = (float) (screenPos[1] / totalScale) + 10.0f;

    int alpha =
        (fadeTimer == null)
            ? 255
            : Math.max(0, 255 - (int) (fadeTimer.getElapsedTime() * 255 / 400));

    if (alpha > 0) {
      int maxAlphaOutline = Math.min(alpha, 110);
      int maxAlphaBackground = Math.min(alpha, 210);

      float invSc = 1.0f / totalScale;

      GlStateManager.pushMatrix();
      if (totalScale != 1.0F) {
        GL11.glScalef(totalScale, totalScale, 1.0F);
      }

      float n6 = absX * invSc;
      float n7 = absY * invSc;
      float n8 = (absX + boxWidth) * invSc;
      float n9 = (absY + boxHeight - 13f) * invSc;

      HUD hud = (HUD) Miau.moduleManager.getModule(HUD.class);
      boolean blurOn = hud != null && hud.isEnabled() && hud.shaders.getValue();

      if (blurOn) {
        switch (parent.ravenMode.getValue()) {
          case 0:
            float bloomRadius = (fadeTimer == null) ? 2f : (2f * alpha / 255f);
            float blurRadius = (fadeTimer == null) ? 3f : (3f * alpha / 255f);
            BlurUtils.prepareBloom();
            RoundedUtils.drawRound(
                n6,
                n7,
                Math.abs(n6 - n8),
                Math.abs(n7 - (n9 + 13f * invSc)),
                8.0f,
                true,
                new Color(81, 99, 149, 80));
            BlurUtils.bloomEnd(3, bloomRadius);
            BlurUtils.prepareBlur();
            RoundedUtils.drawRound(
                n6,
                n7,
                Math.abs(n6 - n8),
                Math.abs(n7 - (n9 + 13f * invSc)),
                8.0f,
                true,
                new Color(mergeAlpha(Color.black.getRGB(), maxAlphaOutline)));
            BlurUtils.blurEnd(2, blurRadius);
            break;
          case 1:
            int[] gradientColors =
                new int[] {
                  Themes.getCurrentTheme().getFirstColor().getRGB(),
                  Themes.getCurrentTheme().getSecondColor().getRGB()
                };
            drawRoundedGradientOutlinedRectangle(
                n6,
                n7,
                n8,
                n9 + 13f * invSc,
                10.0f,
                mergeAlpha(Color.black.getRGB(), maxAlphaOutline),
                mergeAlpha(gradientColors[0], alpha),
                mergeAlpha(gradientColors[1], alpha));
            break;
        }
      } else if (parent.ravenMode.getValue() == 1) {
        int[] gradientColors =
            new int[] {
              Themes.getCurrentTheme().getFirstColor().getRGB(),
              Themes.getCurrentTheme().getSecondColor().getRGB()
            };
        drawRoundedGradientOutlinedRectangle(
            n6,
            n7,
            n8,
            n9 + 13f * invSc,
            10.0f,
            mergeAlpha(Color.black.getRGB(), maxAlphaOutline),
            mergeAlpha(gradientColors[0], alpha),
            mergeAlpha(gradientColors[1], alpha));
      }

      float n13 = n6 + 6f * invSc;
      float n14 = n8 - 6f * invSc;
      float n15 = n9;

      drawRoundedRectangle(
          n13, n15, n14, n15 + 5f * invSc, 4.0f, mergeAlpha(Color.black.getRGB(), maxAlphaOutline));

      int mergedGradientLeft =
          mergeAlpha(Themes.getCurrentTheme().getFirstColor().getRGB(), maxAlphaBackground);
      int mergedGradientRight =
          mergeAlpha(Themes.getCurrentTheme().getSecondColor().getRGB(), maxAlphaBackground);

      float healthBar = n14 + (n13 - n14) * (float) (1 - health);

      boolean smoothBack = false;
      if (healthBar != lastHealthBar
          && Math.abs(lastHealthBar - n13) >= 3f * invSc
          && healthBarTimer != null) {
        float diff = lastHealthBar - healthBar;
        long elapsed = healthBarTimer.getElapsedTime();
        long duration = parent.ravenMode.getValue() == 0 ? 500 : 350;
        float t = Math.min(1.0f, (float) elapsed / (float) duration);

        if (parent.ravenMode.getValue() == 0) {
          t = quadInOut(t);
        } else {
          t = easeInOutCubic(t);
        }

        if (diff > 0) {
          lastHealthBar = lastHealthBar - diff * t;
        } else {
          smoothBack = true;
          lastHealthBar = lastHealthBar + (healthBar - lastHealthBar) * t;
        }
      } else {
        lastHealthBar = healthBar;
      }

      if (parent.healthColor.getValue()) {
        Color healthBlend = ColorUtil.getHealthBlend((float) health);
        mergedGradientLeft =
            mergedGradientRight = mergeAlpha(healthBlend.getRGB(), maxAlphaBackground);
      }

      if (lastHealthBar > n14) {
        lastHealthBar = n14;
      }

      switch (parent.ravenMode.getValue()) {
        case 0:
          drawRoundedRectangle(
              n13,
              n15,
              lastHealthBar,
              n15 + 5f * invSc,
              4.0f,
              mergeAlpha(
                  ColorUtil.darker(new Color(mergedGradientRight), 0.75f).getRGB(),
                  maxAlphaBackground));
          drawRoundedGradientRect(
              n13,
              n15,
              smoothBack ? lastHealthBar : healthBar,
              n15 + 5f * invSc,
              4.0f,
              mergedGradientLeft,
              mergedGradientLeft,
              mergedGradientRight,
              mergedGradientRight);
          break;
        case 1:
          drawRoundedGradientRect(
              n13,
              n15,
              lastHealthBar,
              n15 + 5f * invSc,
              4.0f,
              mergedGradientLeft,
              mergedGradientLeft,
              mergedGradientRight,
              mergedGradientRight);
          break;
      }

      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GlStateManager.bindTexture(0);
      GlStateManager.enableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01F);
      int textColor =
          (new Color(220, 220, 220, 255).getRGB() & 0xFFFFFF)
              | (MathHelper.clamp_int(alpha + 15, 0, 255) << 24);
      mc.fontRendererObj.drawString(
          string, n6 + padding * invSc, n7 + padding * invSc, textColor, parent.shadow.getValue());
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

      GlStateManager.popMatrix();
    } else {
      healthBarTimer = null;
    }
  }

  private float quadInOut(float t) {
    if (t < 0.5f) return 2 * t * t;
    return -1 + (4 - 2 * t) * t;
  }

  private float easeInOutCubic(float t) {
    return t < 0.5F ? 4.0F * t * t * t : (t - 1.0F) * (2.0F * t - 2.0F) * (2.0F * t - 2.0F) + 1.0F;
  }

  private void drawRoundedGradientOutlinedRectangle(
      float x,
      float y,
      float x2,
      float y2,
      float radius,
      int fillColor,
      int startColor,
      int endColor) {
    x *= 2.0f;
    y *= 2.0f;
    x2 *= 2.0f;
    y2 *= 2.0f;
    GL11.glPushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glScaled(0.5, 0.5, 0.5);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);

    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    glColor(fillColor);
    for (int i = 0; i <= 90; i += 3) {
      double angle = i * 0.017453292f;
      GL11.glVertex2d(
          x + radius + Math.sin(angle) * radius * -1.0,
          y + radius + Math.cos(angle) * radius * -1.0);
    }
    for (int i = 90; i <= 180; i += 3) {
      double angle = i * 0.017453292f;
      GL11.glVertex2d(
          x + radius + Math.sin(angle) * radius * -1.0,
          y2 - radius + Math.cos(angle) * radius * -1.0);
    }
    for (int i = 0; i <= 90; i += 3) {
      double angle = i * 0.017453292f;
      GL11.glVertex2d(
          x2 - radius + Math.sin(angle) * radius, y2 - radius + Math.cos(angle) * radius);
    }
    for (int i = 90; i <= 180; i += 3) {
      double angle = i * 0.017453292f;
      GL11.glVertex2d(
          x2 - radius + Math.sin(angle) * radius, y + radius + Math.cos(angle) * radius);
    }
    GL11.glEnd();

    GL11.glPushMatrix();
    GL11.glShadeModel(GL11.GL_SMOOTH);
    GL11.glLineWidth(2.0f);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    if (startColor != 0) {
      glColor(startColor);
    }
    for (int i = 0; i <= 90; i += 3) {
      double angle = i * 0.017453292f;
      GL11.glVertex2d(
          x + radius + Math.sin(angle) * radius * -1.0,
          y + radius + Math.cos(angle) * radius * -1.0);
    }
    for (int i = 90; i <= 180; i += 3) {
      double angle = i * 0.017453292f;
      GL11.glVertex2d(
          x + radius + Math.sin(angle) * radius * -1.0,
          y2 - radius + Math.cos(angle) * radius * -1.0);
    }
    if (endColor != 0) {
      glColor(endColor);
    }
    for (int i = 0; i <= 90; i += 3) {
      double angle = i * 0.017453292f;
      GL11.glVertex2d(
          x2 - radius + Math.sin(angle) * radius, y2 - radius + Math.cos(angle) * radius);
    }
    for (int i = 90; i <= 180; i += 3) {
      double angle = i * 0.017453292f;
      GL11.glVertex2d(
          x2 - radius + Math.sin(angle) * radius, y + radius + Math.cos(angle) * radius);
    }
    GL11.glEnd();
    GL11.glPopMatrix();

    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glPopAttrib();
    GL11.glPopMatrix();
    GL11.glLineWidth(1.0f);
    GL11.glShadeModel(GL11.GL_FLAT);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }
}