package miau.module.modules.render;

import miau.Miau;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public final class Animations extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final String[] BLOCK_MODES =
      new String[] {
        "None",
        "1.7",
        "Sunny",
        "Lucid",
        "Astro",
        "Smooth",
        "Spin",
        "Leaked",
        "Old",
        "Exhibition",
        "Exhibition Old",
        "Exhibition New",
        "Swong",
        "Stella",
        "Flup",
        "Noov",
        "Komorebi",
        "Rhys",
        "Swing",
        "?",
        "Stab",
        "Beta",
        "Dortware",
        "Avatar",
        "Tap",
        "Slide"
      };

  private static final double a = Math.PI;
  private static final float b = 180.0F;

  public final ModeProperty blockAnimation = new ModeProperty("Block Animation", 0, BLOCK_MODES);
  public final BooleanProperty onlyWhenBlocking =
      new BooleanProperty("Update Position Only When Blocking", true);
  public final IntProperty swingSpeed = new IntProperty("Swing Speed", 1, -200, 50);

  public final FloatProperty x = new FloatProperty("X", 0.0F, -2.0F, 2.0F);
  public final FloatProperty y = new FloatProperty("Y", 0.0F, -2.0F, 2.0F);
  public final FloatProperty z = new FloatProperty("Z", 0.0F, -2.0F, 2.0F);
  public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.1F, 2.0F);
  public final BooleanProperty alwaysShow = new BooleanProperty("Always Show", false);

  public Animations() {
    super("Animations", false);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.blockAnimation.getModeString()};
  }

  public static boolean apply(
      float swingProgress, float equipProgress, AbstractClientPlayer player) {
    Animations animations = (Animations) Miau.moduleManager.modules.get(Animations.class);
    if (animations == null || !animations.isEnabled() || player == null) {
      return false;
    }
    animations.renderBlock(swingProgress, equipProgress, player);
    return true;
  }

  public static int getSwingAnimationEnd(EntityLivingBase entity, int original) {
    Animations animations = (Animations) Miau.moduleManager.modules.get(Animations.class);
    if (animations != null && animations.isEnabled() && entity == mc.thePlayer) {
      float speedVal = animations.swingSpeed.getValue();
      float multiplier = (-speedVal / 100.0F) + 1.0F;
      return (int) (original * multiplier);
    }
    return original;
  }

  private void transformFirstPersonItem(float equipProgress, float swingProgress) {
    GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
    GlStateManager.translate(0.0F, equipProgress * -0.6F, 0.0F);
    GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
    float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
    float f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
    GlStateManager.rotate(f * -20.0F, 0.0F, 1.0F, 0.0F);
    GlStateManager.rotate(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
    GlStateManager.rotate(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
    GlStateManager.scale(0.4F, 0.4F, 0.4F);
  }

  private void blockTransformation() {
    GlStateManager.translate(-0.5F, 0.2F, 0.0F);
    GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
    GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
    GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
  }

  private void doItemUsedTransformations(float swingProgress) {
    float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
    float f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
    GlStateManager.rotate(f * -15.0F, 0.0F, 1.0F, 0.0F);
    GlStateManager.rotate(f1 * -50.0F, 1.0F, 0.0F, 0.0F);
  }

  private void renderBlock(float swingProgress, float equipProgress, AbstractClientPlayer player) {
    if (!onlyWhenBlocking.getValue()) {
      GlStateManager.translate(x.getValue(), y.getValue(), z.getValue());
    }

    double var7 = scale.getValue().doubleValue();
    float animationProgression = alwaysShow.getValue() ? 0.0F : equipProgress;
    float convertedProgress = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) a);

    if (onlyWhenBlocking.getValue()) {
      GlStateManager.translate(x.getValue(), y.getValue(), z.getValue());
    }

    switch (blockAnimation.getModeString()) {
      case "None":
        transformFirstPersonItem(animationProgression, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        break;

      case "1.7":
        transformFirstPersonItem(animationProgression, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        break;

      case "Sunny":
        var7 = 0.99D;
        GlStateManager.translate(0.05F, -0.05F, -0.12F);
        transformFirstPersonItem(animationProgression + 0.15F, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
        break;

      case "Lucid":
        transformFirstPersonItem(animationProgression - 0.1F, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        break;

      case "Astro":
        GlStateManager.translate(0.0F, 0.03F, -0.05F);
        transformFirstPersonItem(animationProgression / 2.0F, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.rotate(convertedProgress * 30.0F / 2.0F, -convertedProgress, -0.0F, 9.0F);
        GlStateManager.rotate(convertedProgress * 40.0F, 1.0F, -convertedProgress / 2.0F, -0.0F);
        blockTransformation();
        break;

      case "Tap":
        GL11.glTranslatef(0.0F, 0.3F, 0.0F);
        float smooth = (swingProgress * 0.8F - (swingProgress * swingProgress) * 0.8F);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(smooth * -90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(0.37F, 0.37F, 0.37F);
        blockTransformation();
        break;

      case "Beta":
        GL11.glTranslatef(0.0F, 0.3F, 0.0F);
        float var15 = MathHelper.sin(swingProgress * swingProgress * 3.1415927F);
        transformFirstPersonItem(equipProgress * 0.5F, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.rotate(-var15 * 55.0F / 2.0F, -8.0F, -0.0F, 9.0F);
        GlStateManager.rotate(-var15 * 45.0F, 1.0F, var15 / 2.0F, -0.0F);
        blockTransformation();
        GL11.glTranslated(1.2D, 0.3D, 0.5D);
        GL11.glTranslatef(-1.0F, mc.thePlayer.isSneaking() ? -0.1F : -0.2F, 0.2F);
        break;

      case "Slide":
        GL11.glTranslatef(0.0F, 0.3F, 0.0F);
        float smooth2 = (swingProgress * 0.8F - (swingProgress * swingProgress) * 0.8F);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.translate(0.0F, equipProgress * 0.3F * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 2.0F + smooth2 * 0.5F, smooth2 * 3.0F);
        GlStateManager.rotate(0.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(0.37F, 0.37F, 0.37F);
        blockTransformation();
        break;

      case "Avatar":
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        GlStateManager.rotate(f * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f1 * -40.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
        blockTransformation();
        break;

      case "Smooth":
        transformFirstPersonItem(animationProgression, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        float ySmooth = -convertedProgress * 2.0F;
        GlStateManager.translate(0.0F, ySmooth / 10.0F + 0.1F, 0.0F);
        GlStateManager.rotate(ySmooth * 10.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(250.0F, 0.2F, 1.0F, -0.6F);
        GlStateManager.rotate(-10.0F, 1.0F, 0.5F, 1.0F);
        GlStateManager.rotate(-ySmooth * 20.0F, 1.0F, 0.5F, 1.0F);
        break;

      case "Stab":
        float spin = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) a);
        GlStateManager.translate(0.6F, 0.3F, -0.6F + -spin * 0.7F);
        GlStateManager.rotate(6090.0F, 0.0F, 0.0F, 0.1F);
        GlStateManager.rotate(6085.0F, 0.0F, 0.1F, 0.0F);
        GlStateManager.rotate(6110.0F, 0.1F, 0.0F, 0.0F);
        transformFirstPersonItem(0.0F, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        break;

      case "Spin":
        transformFirstPersonItem(animationProgression, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.translate(0.0F, 0.2F, -1.0F);
        GlStateManager.rotate(-59.0F, -1.0F, 0.0F, 3.0F);
        GlStateManager.rotate(-(System.currentTimeMillis() / 2 % 360), 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
        break;

      case "Leaked":
        GlStateManager.translate(0.0F, -0.03F, -0.13F);
        transformFirstPersonItem(animationProgression / 3.0F, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.translate(0.0F, 0.1F, 0.0F);
        blockTransformation();
        GlStateManager.rotate(convertedProgress * 20.0F / 2.0F, 0.0F, 1.0F, 1.5F);
        GlStateManager.rotate(-convertedProgress * 200.0F / 4.0F, 1.0F, 0.9F, 0.0F);
        break;

      case "Old":
        GlStateManager.translate(0.0F, 0.1F, 0.0F);
        transformFirstPersonItem(animationProgression / 2.0F - 0.2F, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        break;

      case "Exhibition":
        GlStateManager.translate(0.0F, -0.05F, 0.0F);
        transformFirstPersonItem(animationProgression / 2.0F, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.translate(0.0F, 0.3F, -0.0F);
        GlStateManager.rotate(-convertedProgress * 31.0F, 1.0F, 0.0F, 2.0F);
        GlStateManager.rotate(-convertedProgress * 33.0F, 1.5F, (convertedProgress / 1.1F), 0.0F);
        blockTransformation();
        break;

      case "Exhibition Old":
        GlStateManager.translate(0.0F, -0.05F, 0.0F);
        GlStateManager.translate(-0.04F, 0.13F, 0.0F);
        transformFirstPersonItem(animationProgression / 2.5F, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.rotate(
            -convertedProgress * 40.0F / 2.0F, convertedProgress / 2.0F, 1.0F, 4.0F);
        GlStateManager.rotate(-convertedProgress * 30.0F, 1.0F, convertedProgress / 3.0F, -0.0F);
        blockTransformation();
        break;

      case "Exhibition New":
        GlStateManager.translate(0.0F, -0.04F, -0.01F);
        transformFirstPersonItem(animationProgression / 2.0F, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.translate(0.0F, 0.3F, -0.0F);
        GlStateManager.rotate(-convertedProgress * 30.0F, 1.0F, 0.0F, 2.0F);
        GlStateManager.rotate(-convertedProgress * 44.0F, 1.5F, (convertedProgress / 1.2F), 0.0F);
        blockTransformation();
        break;

      case "Swong":
        GlStateManager.translate(0.0F, 0.1F, -0.05F);
        transformFirstPersonItem(animationProgression / 2.0F, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.rotate(convertedProgress * 30.0F, -convertedProgress, -0.0F, 9.0F);
        GlStateManager.rotate(convertedProgress * 40.0F, 1.0F, -convertedProgress, -0.0F);
        blockTransformation();
        break;

      case "Stella":
        transformFirstPersonItem(-0.1F, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        GlStateManager.translate(-0.5F, 0.4F, -0.2F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-70.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(40.0F, 0.0F, 1.0F, 0.0F);
        break;

      case "Flup":
        GlStateManager.translate(0.0F, 0.1F, -0.05F);
        transformFirstPersonItem(animationProgression, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        GlStateManager.translate(-0.05F, 0.2F, 0.0F);
        GlStateManager.rotate(-convertedProgress * 70.0F / 2.0F, -8.0F, -0.0F, 9.0F);
        GlStateManager.rotate(-convertedProgress * 70.0F, 1.0F, -0.4F, -0.0F);
        break;

      case "Noov":
        transformFirstPersonItem(animationProgression / 1.5F, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        GlStateManager.translate(-0.05F, 0.3F, 0.3F);
        GlStateManager.rotate(-convertedProgress * 140.0F, 8.0F, 0.0F, 8.0F);
        GlStateManager.rotate(convertedProgress * b, 8.0F, 0.0F, 8.0F);
        break;

      case "Komorebi":
        transformFirstPersonItem(-0.25F, 1.0F + convertedProgress / 10.0F);
        GlStateManager.scale(var7, var7, var7);
        GL11.glRotated(-convertedProgress * 25.0F, 1.0D, 0.0D, 0.0D);
        blockTransformation();
        break;

      case "Rhys":
        GlStateManager.translate(0.41F, -0.25F, -0.5555557F);
        GlStateManager.translate(0.0F, 0.0F, 0.0F);
        GlStateManager.rotate(35.0F, 0.0F, 1.5F, 0.0F);
        float racism = MathHelper.sin(swingProgress * swingProgress / 64.0F * (float) a);
        GlStateManager.rotate(racism * -5.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.rotate(convertedProgress * -12.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(convertedProgress * -65.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        break;

      case "Swing":
        transformFirstPersonItem(animationProgression, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        blockTransformation();
        GlStateManager.translate(-0.3F, -0.1F, -0.0F);
        break;

      case "?":
        transformFirstPersonItem(animationProgression, swingProgress);
        GlStateManager.scale(var7, var7, var7);
        GL11.glTranslatef(-0.35F, 0.1F, 0.0F);
        GL11.glTranslatef(-0.05F, -0.1F, 0.1F);
        blockTransformation();
        break;

      case "Dortware":
        float var1_dort = MathHelper.sin((float) (swingProgress * swingProgress * Math.PI - 3.0D));
        float var_dort = MathHelper.sin((float) (MathHelper.sqrt_float(swingProgress) * Math.PI));
        transformFirstPersonItem(animationProgression, 1.0F);
        GlStateManager.rotate(-var_dort * 10.0F, 0.0F, 15.0F, 200.0F);
        GlStateManager.rotate(-var_dort * 10.0F, 300.0F, var_dort / 2.0F, 1.0F);
        blockTransformation();
        GL11.glTranslated(2.4D, 0.3D, 0.5D);
        GL11.glTranslatef(-2.10F, -0.2F, 0.1F);
        GlStateManager.rotate(var1_dort * 13.0F, -10.0F, -1.4F, -10.0F);
        break;
    }
  }
}
