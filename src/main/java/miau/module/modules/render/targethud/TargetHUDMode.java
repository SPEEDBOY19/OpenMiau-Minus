package miau.module.modules.render.targethud;

import java.awt.Color;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import miau.module.modules.render.TargetHUD;
import miau.util.render.ShapeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Timer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public abstract class TargetHUDMode {
  protected final TargetHUD parent;
  protected static final Minecraft mc = Minecraft.getMinecraft();
  protected static final DecimalFormat healthFormat =
      new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
  protected static final DecimalFormat diffFormat =
      new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));

  private static Field timerField = null;

  public TargetHUDMode(TargetHUD parent) {
    this.parent = parent;
  }

  public abstract void render(EntityLivingBase target, float x, float y);

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

  protected float calculateDistanceScale(EntityLivingBase target) {
    double distance = mc.thePlayer.getDistanceToEntity(target);
    return (float) MathHelper.clamp_double(1.0 - (distance - 3.0) * 0.06, 0.45, 1.25);
  }

  public void drawHead(ResourceLocation skin, int width, int height, Color color) {
    GL11.glColor4f(
        color.getRed() / 255f,
        color.getGreen() / 255f,
        color.getBlue() / 255f,
        color.getAlpha() / 255f);
    mc.getTextureManager().bindTexture(skin);
    net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
        2, 2, 8F, 8F, 8, 8, width, height, 64F, 64F);
  }

  protected void renderPlayer2D(
      float x, float y, float width, float height, AbstractClientPlayer player) {
    miau.util.render.GLUtil.startBlend();
    mc.getTextureManager().bindTexture(player.getLocationSkin());
    net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
        (int) x, (int) y, 8.0f, 8.0f, 8, 8, (int) width, (int) height, 64.0F, 64.0F);
    miau.util.render.GLUtil.endBlend();
  }

  public void drawArmorHUD(EntityPlayer player, int y, int x) {
    GL11.glPushMatrix();
    java.util.List<ItemStack> stuff = new java.util.ArrayList<>();
    for (int index = 3; index >= 0; --index) {
      ItemStack armor = player.inventory.armorInventory[index];
      if (armor != null) stuff.add(armor);
    }
    if (player.getCurrentEquippedItem() != null) stuff.add(player.getCurrentEquippedItem());
    int split = -3;
    for (ItemStack item : stuff) {
      if (mc.theWorld != null) {
        RenderHelper.enableGUIStandardItemLighting();
        split += 16;
      }
      GlStateManager.pushMatrix();
      GlStateManager.disableAlpha();
      GlStateManager.clear(256);
      GlStateManager.enableBlend();
      mc.getRenderItem().zLevel = -150.0F;
      mc.getRenderItem().renderItemAndEffectIntoGUI(item, split + x + 18, y + 17);
      mc.getRenderItem().zLevel = 0.0F;
      GlStateManager.enableBlend();
      GlStateManager.scale(0.5F, 0.5F, 0.5F);
      GlStateManager.disableDepth();
      GlStateManager.disableLighting();
      GlStateManager.enableDepth();
      GlStateManager.scale(2.0f, 2.0f, 2.0f);
      GlStateManager.enableAlpha();
      GlStateManager.popMatrix();
    }
    GL11.glPopMatrix();
  }

  public void rectangle(double x, double y, double x1, double y1, int color) {
    ShapeUtil.drawRect((float) x, (float) y, (float) x1, (float) y1, color);
  }

  public void rectangleBordered(
      double x, double y, double x1, double y1, double width, int internalColor, int borderColor) {
    rectangle(x + width, y + width, x1 - width, y1 - width, internalColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    rectangle(x + width, y, x1 - width, y + width, borderColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    rectangle(x, y, x + width, y1, borderColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    rectangle(x1 - width, y, x1, y1, borderColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    rectangle(x + width, y1 - width, x1 - width, y1, borderColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
  }

  public void skeetRect(double x, double y, double x1, double y1, double size) {
    rectangleBordered(
        x,
        y - 4.0,
        x1 + size,
        y1 + size,
        0.5,
        new Color(60, 60, 60).getRGB(),
        new Color(10, 10, 10).getRGB());
    rectangleBordered(
        x + 1.0,
        y - 3.0,
        x1 + size - 1.0,
        y1 + size - 1.0,
        1.0,
        new Color(40, 40, 40).getRGB(),
        new Color(40, 40, 40).getRGB());
    rectangleBordered(
        x + 2.5,
        y - 1.5,
        x1 + size - 2.5,
        y1 + size - 2.5,
        0.5,
        new Color(40, 40, 40).getRGB(),
        new Color(60, 60, 60).getRGB());
    rectangleBordered(
        x + 2.5,
        y - 1.5,
        x1 + size - 2.5,
        y1 + size - 2.5,
        0.5,
        new Color(22, 22, 22).getRGB(),
        new Color(255, 255, 255, 0).getRGB());
  }

  public void skeetRectSmall(double x, double y, double x1, double y1, double size) {
    rectangleBordered(
        x + 4.35,
        y + 0.5,
        x1 + size - 84.5,
        y1 + size - 4.35,
        0.5,
        new Color(48, 48, 48).getRGB(),
        new Color(10, 10, 10).getRGB());
    rectangleBordered(
        x + 5.0,
        y + 1.0,
        x1 + size - 85.0,
        y1 + size - 5.0,
        0.5,
        new Color(17, 17, 17).getRGB(),
        new Color(255, 255, 255, 0).getRGB());
  }

  public void drawModel(float yaw, float pitch, EntityLivingBase entityLivingBase) {
    GlStateManager.resetColor();
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.enableColorMaterial();
    GlStateManager.pushMatrix();
    GlStateManager.translate(0.0f, 0.0f, 50.0f);
    GlStateManager.scale(-50.0f, 50.0f, 50.0f);
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
    GlStateManager.translate(0.0f, 0.0f, 0.0f);
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

  public Color getBlendColor(double current, double max) {
    long base = Math.round(max / 5.0);
    if (current >= base * 5L) {
      return new Color(15, 255, 15);
    }
    if (current >= base * 4L) {
      return new Color(166, 255, 0);
    }
    if (current >= base * 3L) {
      return new Color(255, 191, 0);
    }
    if (current >= base * 2L) {
      return new Color(255, 89, 0);
    }
    return new Color(255, 0, 0);
  }

  public Color blendColors(float[] fractions, Color[] colors, float progress) {
    if (fractions == null) throw new IllegalArgumentException("Fractions can't be null");
    if (colors == null) throw new IllegalArgumentException("Colours can't be null");
    if (fractions.length != colors.length)
      throw new IllegalArgumentException(
          "Fractions and colours must have equal number of elements");
    int[] indicies = getFractionIndicies(fractions, progress);
    float[] range = new float[] {fractions[indicies[0]], fractions[indicies[1]]};
    Color[] colorRange = new Color[] {colors[indicies[0]], colors[indicies[1]]};
    float max = range[1] - range[0];
    float value = progress - range[0];
    float weight = value / max;
    return blend(colorRange[0], colorRange[1], 1.0f - weight);
  }

  public int[] getFractionIndicies(float[] fractions, float progress) {
    int[] range = new int[2];
    int startPoint;
    for (startPoint = 0;
        startPoint < fractions.length && fractions[startPoint] <= progress;
        ++startPoint) {}
    if (startPoint >= fractions.length) {
      startPoint = fractions.length - 1;
    }
    range[0] = startPoint - 1;
    range[1] = startPoint;
    return range;
  }

  public Color blend(Color color1, Color color2, double ratio) {
    float r = (float) ratio;
    float ir = 1.0f - r;
    float[] rgb1 = new float[3];
    float[] rgb2 = new float[3];
    color1.getColorComponents(rgb1);
    color2.getColorComponents(rgb2);
    float red = rgb1[0] * r + rgb2[0] * ir;
    float green = rgb1[1] * r + rgb2[1] * ir;
    float blue = rgb1[2] * r + rgb2[2] * ir;
    if (red < 0.0f) {
      red = 0.0f;
    } else if (red > 255.0f) {
      red = 255.0f;
    }
    if (green < 0.0f) {
      green = 0.0f;
    } else if (green > 255.0f) {
      green = 255.0f;
    }
    if (blue < 0.0f) {
      blue = 0.0f;
    } else if (blue > 255.0f) {
      blue = 255.0f;
    }
    Color color3 = null;
    try {
      color3 = new Color(red, green, blue);
    } catch (IllegalArgumentException exp) {
      java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance();
      exp.printStackTrace();
    }
    return color3;
  }

  public void renderEnchantText(ItemStack item, int x, float y) {
    int enchantmentY = (int) (y - 8.0f);
    if (item.getItem() instanceof net.minecraft.item.ItemSword) {
      float sharpness = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, item);
      float fireAspect =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, item);
      float knockback = EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, item);
      if (sharpness > 0.0f) {
        mc.fontRendererObj.drawString(
            "S" + this.getColor(sharpness) + (int) sharpness, x * 2, enchantmentY, 16777215, true);
        enchantmentY -= 8;
      }
      if (fireAspect > 0.0f) {
        mc.fontRendererObj.drawString(
            "F" + this.getColor(fireAspect) + (int) fireAspect,
            x * 2,
            enchantmentY,
            16777215,
            true);
        enchantmentY -= 8;
      }
      if (knockback > 0.0f) {
        mc.fontRendererObj.drawString(
            "K" + this.getColor(knockback) + (int) knockback, x * 2, enchantmentY, 16777215, true);
      }
    }
    if (item.getItem() instanceof net.minecraft.item.ItemArmor) {
      float protection =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, item);
      float unbreaking =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, item);
      float thorns = EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, item);
      if (protection > 0.0f) {
        mc.fontRendererObj.drawString(
            "P" + this.getColor(protection) + (int) protection,
            x * 2,
            enchantmentY,
            16777215,
            true);
        enchantmentY -= 8;
      }
      if (unbreaking > 0.0f) {
        mc.fontRendererObj.drawString(
            "U" + this.getColor(unbreaking) + (int) unbreaking,
            x * 2,
            enchantmentY,
            16777215,
            true);
        enchantmentY -= 8;
      }
      if (thorns > 0.0f) {
        mc.fontRendererObj.drawString(
            "T" + this.getColor(thorns) + (int) thorns, x * 2, enchantmentY, 16777215, true);
      }
    }
    if (item.getItem() instanceof net.minecraft.item.ItemBow) {
      float power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, item);
      float punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, item);
      float flame = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, item);
      if (power > 0.0f) {
        mc.fontRendererObj.drawString(
            "P" + this.getColor(power) + (int) power, x * 2, enchantmentY, 16777215, true);
        enchantmentY -= 8;
      }
      if (punch > 0.0f) {
        mc.fontRendererObj.drawString(
            "P" + this.getColor(punch) + (int) punch, x * 2, enchantmentY, 16777215, true);
        enchantmentY -= 8;
      }
      if (flame > 0.0f) {
        mc.fontRendererObj.drawString(
            "F" + this.getColor(flame) + (int) flame, x * 2, enchantmentY, 16777215, true);
      }
    }
  }

  public String getColor(float n) {
    if (n != 1.0f) {
      if (n == 2.0f) {
        return "\u00a7a";
      }
      if (n == 3.0f) {
        return "\u00a73";
      }
      if (n == 4.0f) {
        return "\u00a74";
      }
      if (n >= 5.0f) {
        return "\u00a7e";
      }
    }
    return "\u00a7f";
  }

  public int getNextPostion(int anim, int max, double speed) {
    if (anim == max) return anim;
    if (anim > max) {
      anim -= Math.max(1, (int) Math.round((anim - max) / speed));
    } else {
      anim += Math.max(1, (int) Math.round((max - anim) / speed));
    }
    return anim;
  }

  public int reAlpha(int color, float alpha) {
    Color c = new Color(color);
    float r = 0.003921569F * (float) c.getRed();
    float g = 0.003921569F * (float) c.getGreen();
    float b = 0.003921569F * (float) c.getBlue();
    return (new Color(r, g, b, alpha)).getRGB();
  }

  public float clampValue(float value, float floor, float cap) {
    if (value < floor) {
      return floor;
    }
    if (value > cap) {
      return cap;
    }
    return value;
  }

  public void drawRoundedGradientRect(
      float left,
      float top,
      float right,
      float bottom,
      float radius,
      int topLeft,
      int bottomLeft,
      int bottomRight,
      int topRight) {
    float minDimension = Math.min(right - left, bottom - top);
    if (radius > minDimension / 2.0F) {
      radius = minDimension / 2.0F;
    }

    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    GL11.glBegin(GL11.GL_POLYGON);

    glColor(topLeft);
    for (int i = 0; i <= 90; i += 3) {
      GL11.glVertex2f(
          (float) (left + radius + Math.sin(Math.toRadians(i)) * radius * -1.0),
          (float) (top + radius + Math.cos(Math.toRadians(i)) * radius * -1.0));
    }
    glColor(bottomLeft);
    for (int i = 90; i <= 180; i += 3) {
      GL11.glVertex2f(
          (float) (left + radius + Math.sin(Math.toRadians(i)) * radius * -1.0),
          (float) (bottom - radius + Math.cos(Math.toRadians(i)) * radius * -1.0));
    }
    glColor(bottomRight);
    for (int i = 0; i <= 90; i += 3) {
      GL11.glVertex2f(
          (float) (right - radius + Math.sin(Math.toRadians(i)) * radius),
          (float) (bottom - radius + Math.cos(Math.toRadians(i)) * radius));
    }
    glColor(topRight);
    for (int i = 90; i <= 180; i += 3) {
      GL11.glVertex2f(
          (float) (right - radius + Math.sin(Math.toRadians(i)) * radius),
          (float) (top + radius + Math.cos(Math.toRadians(i)) * radius));
    }
    GL11.glEnd();

    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    GL11.glBegin(GL11.GL_QUADS);
    glColor(topLeft);
    GL11.glVertex2f(left, top + radius);
    glColor(bottomLeft);
    GL11.glVertex2f(left, bottom - radius);
    glColor(bottomRight);
    GL11.glVertex2f(right, bottom - radius);
    glColor(topRight);
    GL11.glVertex2f(right, top + radius);
    GL11.glEnd();

    GL11.glBegin(GL11.GL_QUADS);
    glColor(topLeft);
    GL11.glVertex2f(left + radius, top);
    glColor(bottomLeft);
    GL11.glVertex2f(left + radius, bottom);
    glColor(bottomRight);
    GL11.glVertex2f(right - radius, bottom);
    glColor(topRight);
    GL11.glVertex2f(right - radius, top);
    GL11.glEnd();

    GlStateManager.shadeModel(GL11.GL_FLAT);
    GlStateManager.disableBlend();
    GlStateManager.enableTexture2D();
  }

  public void drawRoundedRectangle(
      float left, float top, float right, float bottom, float radius, int color) {
    float minDimension = Math.min(right - left, bottom - top);
    if (radius > minDimension / 2.0F) {
      radius = minDimension / 2.0F;
    }

    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    glColor(color);
    GL11.glBegin(GL11.GL_POLYGON);

    for (int i = 0; i <= 90; i += 3) {
      GL11.glVertex2f(
          (float) (left + radius + Math.sin(Math.toRadians(i)) * radius * -1.0),
          (float) (top + radius + Math.cos(Math.toRadians(i)) * radius * -1.0));
    }
    for (int i = 90; i <= 180; i += 3) {
      GL11.glVertex2f(
          (float) (left + radius + Math.sin(Math.toRadians(i)) * radius * -1.0),
          (float) (bottom - radius + Math.cos(Math.toRadians(i)) * radius * -1.0));
    }
    for (int i = 0; i <= 90; i += 3) {
      GL11.glVertex2f(
          (float) (right - radius + Math.sin(Math.toRadians(i)) * radius),
          (float) (bottom - radius + Math.cos(Math.toRadians(i)) * radius));
    }
    for (int i = 90; i <= 180; i += 3) {
      GL11.glVertex2f(
          (float) (right - radius + Math.sin(Math.toRadians(i)) * radius),
          (float) (top + radius + Math.cos(Math.toRadians(i)) * radius));
    }
    GL11.glEnd();

    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2f(left, top + radius);
    GL11.glVertex2f(left, bottom - radius);
    GL11.glVertex2f(right, bottom - radius);
    GL11.glVertex2f(right, top + radius);
    GL11.glEnd();

    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2f(left + radius, top);
    GL11.glVertex2f(left + radius, bottom);
    GL11.glVertex2f(right - radius, bottom);
    GL11.glVertex2f(right - radius, top);
    GL11.glEnd();

    GlStateManager.disableBlend();
    GlStateManager.enableTexture2D();
  }

  public void glColor(int color) {
    GL11.glColor4f(
        (color >> 16 & 255) / 255.0F,
        (color >> 8 & 255) / 255.0F,
        (color & 255) / 255.0F,
        (color >> 24 & 255) / 255.0F);
  }

  public int mergeAlpha(int rgb, int alpha) {
    return (rgb & 0xFFFFFF) | (alpha << 24);
  }
}