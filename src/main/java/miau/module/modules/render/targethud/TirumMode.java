package miau.module.modules.render.targethud;

import java.awt.Color;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;

import miau.module.modules.render.TargetHUD;
import miau.util.animation.Animation;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import miau.util.render.StencilUtil;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Timer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.GLU;

public class TirumMode extends TargetHUDMode {
  private final Animation animation = new Animation(Easing.LINEAR, 180);
  private static Field timerField = null;

  public TirumMode(TargetHUD targetHUD) {
    super(targetHUD);
  }

  @Override
  public void render(EntityLivingBase target, float defaultX, float defaultY) {
    if (target == null || target.isDead || target.isInvisible()) return;

    double[] screenPos = projectTo2D(target);
    if (screenPos == null) return;

    double distance = mc.thePlayer.getDistanceToEntity(target);
    float distanceScale = (float) MathHelper.clamp_double(1.0 - (distance - 3.0) * 0.06, 0.45, 1.25);
    float totalScale = distanceScale * parent.scale.getValue();

    float targetWidth = 155;
    float height = 58;

    // --- ĐÃ ĐIỀU CHỈNH VỊ TRÍ TẠI ĐÂY ---
    // Dịch TargetHUD sang bên phải (+35.0f) và đẩy lên cao (-30.0f) để không che TargetMark
    float posX = (float) (screenPos[0] / totalScale) + 35.0f;
    float posY = (float) (screenPos[1] / totalScale) - 30.0f;

    Font font20 = FontRepository.getFont("inter-bold", 20);
    Font font16 = FontRepository.getFont("inter-regular", 16);

    GlStateManager.pushMatrix();
    
    GlStateManager.scale(totalScale, totalScale, 1.0F);

    Color bgColor = new Color(20, 20, 20, 143);
    RoundedUtils.drawRound(posX, posY, targetWidth, height, 6, bgColor);

    if (target instanceof AbstractClientPlayer) {
      StencilUtil.initStencilToWrite();
      RoundedUtils.drawRound(posX + 5, posY + 5, 24, 24, 4, Color.WHITE);
      StencilUtil.readStencilBuffer(1);
      RenderUtil.resetColor();
      GlStateManager.color(1, 1, 1, 1);
      renderPlayer2D(posX + 5, posY + 5, 24, 24, (AbstractClientPlayer) target);
      StencilUtil.uninitStencilBuffer();
      GlStateManager.disableBlend();
    }

    String targetName = target.getName();
    font20.draw(targetName, posX + 35, posY + 5, -1, true);

    float healthPercent =
        MathHelper.clamp_float(
            (target.getHealth() + target.getAbsorptionAmount())
                / (target.getMaxHealth() + target.getAbsorptionAmount()),
            0,
            1);
    int healthBarWidth = (int) (targetWidth - 40);
    int healthBarHeight = 3;
    animation.run(healthPercent * healthBarWidth);
    Color healthColor = getBlendColor(target.getHealth(), target.getMaxHealth());

    RoundedUtils.drawRound(posX + 35, posY + 22, healthBarWidth, healthBarHeight, 1.5f, new Color(0, 0, 0, 150));
    RoundedUtils.drawRound(posX + 35, posY + 22, animation.getValue(), healthBarHeight, 1.5f, healthColor);

    String healthText = (int) target.getHealth() + " HP";
    font16.draw(healthText, posX + 35, posY + 27, -1, true);

    if (target instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) target;
      ItemStack[] items = new ItemStack[] {
          player.getCurrentEquippedItem(), 
          player.getCurrentArmor(3),       
          player.getCurrentArmor(2),       
          player.getCurrentArmor(1),       
          player.getCurrentArmor(0)        
      };

      int itemX = (int) posX + 5;
      int itemY = (int) posY + 36;

      for (ItemStack stack : items) {
        if (stack != null) {
          GlStateManager.pushMatrix();
          RenderHelper.enableGUIStandardItemLighting();
          mc.getRenderItem().zLevel = -150.0F;
          mc.getRenderItem().renderItemAndEffectIntoGUI(stack, itemX, itemY);
          mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, itemX, itemY);
          mc.getRenderItem().zLevel = 0.0F;
          RenderHelper.disableStandardItemLighting();
          GlStateManager.popMatrix();
          itemX += 17;
        }
      }
    }

    GlStateManager.popMatrix();
  }

  @Override
  protected float getPartialTicks() {
    if (timerField != null) {
      try {
        return ((Timer) timerField.get(mc)).renderPartialTicks;
      } catch (Exception ignored) {}
    }
    try {
      for (Field f : Minecraft.class.getDeclaredFields()) {
        if (f.getType() == Timer.class) {
          f.setAccessible(true);
          timerField = f;
          return ((Timer) f.get(mc)).renderPartialTicks;
        }
      }
    } catch (Exception ignored) {}
    return 1.0F;
  }

  @Override
  protected double[] projectTo2D(EntityLivingBase entity) {
    float partialTicks = getPartialTicks();

    double renderX = mc.getRenderManager().viewerPosX;
    double renderY = mc.getRenderManager().viewerPosY;
    double renderZ = mc.getRenderManager().viewerPosZ;

    double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderX;
    double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderY + (entity.height / 2.0);
    double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderZ;

    FloatBuffer winCoords = BufferUtils.createFloatBuffer(3);

    boolean result = GLU.gluProject(
        (float) x, (float) y, (float) z,
        TargetHUD.MODELVIEW,
        TargetHUD.PROJECTION,
        TargetHUD.VIEWPORT,
        winCoords
    );

    if (result) {
      float winZ = winCoords.get(2);
      if (winZ >= 0.0F && winZ <= 1.0F) {
        ScaledResolution sr = new ScaledResolution(mc);
        double screenX = winCoords.get(0) / sr.getScaleFactor();
        double screenY = (TargetHUD.VIEWPORT.get(3) - winCoords.get(1)) / sr.getScaleFactor();
        return new double[]{screenX, screenY};
      }
    }
    return null;
  }
}