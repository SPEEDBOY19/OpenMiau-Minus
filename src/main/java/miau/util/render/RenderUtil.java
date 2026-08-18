package miau.util.render;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;
import miau.enums.ChatColors;
import miau.mixin.IAccessorEntityRenderer;
import miau.mixin.IAccessorMinecraft;
import miau.mixin.IAccessorRenderManager;
import miau.ui.clickgui.ClickGui;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class RenderUtil {

  private static Minecraft mc;
  private static Frustum cameraFrustum;
  private static IntBuffer viewportBuffer;
  private static FloatBuffer modelViewBuffer;
  private static FloatBuffer projectionBuffer;
  private static FloatBuffer vectorBuffer;
  private static Map<Integer, EnchantmentData> enchantmentMap;

  static {
    RenderUtil.mc = Minecraft.getMinecraft();
    RenderUtil.cameraFrustum = new Frustum();
    RenderUtil.viewportBuffer = GLAllocation.createDirectIntBuffer(16);
    RenderUtil.modelViewBuffer = GLAllocation.createDirectFloatBuffer(16);
    RenderUtil.projectionBuffer = GLAllocation.createDirectFloatBuffer(16);
    RenderUtil.vectorBuffer = GLAllocation.createDirectFloatBuffer(4);
    RenderUtil.enchantmentMap = new EnchantmentMap();
  }

  private static ChatColors getColorForLevel(int currentLevel, int maxLevel) {
    if (currentLevel > maxLevel) {
      return ChatColors.LIGHT_PURPLE;
    }
    if (currentLevel == maxLevel) {
      return ChatColors.RED;
    }
    switch (currentLevel) {
      case 1:
        {
          return ChatColors.AQUA;
        }
      case 2:
        {
          return ChatColors.GREEN;
        }
      case 3:
        {
          return ChatColors.YELLOW;
        }
      case 4:
        {
          return ChatColors.GOLD;
        }
    }
    return ChatColors.GRAY;
  }

  public static void drawOutlinedString(String text, float x, float y) {
    String string2 = text.replaceAll("(?i)§[\\da-f]", "");
    RenderUtil.mc.fontRendererObj.drawString(string2, x + 1.0f, y, 0, false);
    RenderUtil.mc.fontRendererObj.drawString(string2, x - 1.0f, y, 0, false);
    RenderUtil.mc.fontRendererObj.drawString(string2, x, y + 1.0f, 0, false);
    RenderUtil.mc.fontRendererObj.drawString(string2, x, y - 1.0f, 0, false);
    RenderUtil.mc.fontRendererObj.drawString(text, x, y, -1, false);
  }

  public static void renderEnchantmentText(ItemStack itemStack, float x, float y, float scale) {
    NBTTagList nBTTagList;
    nBTTagList =
        itemStack.getItem() == Items.enchanted_book
            ? Items.enchanted_book.getEnchantments(itemStack)
            : itemStack.getEnchantmentTagList();
    if (nBTTagList != null) {
      for (int i = 0; i < nBTTagList.tagCount(); ++i) {
        EnchantmentData enchantmentData =
            enchantmentMap.get(nBTTagList.getCompoundTagAt(i).getInteger("id"));
        if (enchantmentData == null) {
          continue;
        }
        short s = nBTTagList.getCompoundTagAt(i).getShort("lvl");
        ChatColors chatColors = RenderUtil.getColorForLevel(s, enchantmentData.maxLevel);
        RenderUtil.drawOutlinedString(
            ChatColors.formatColor(
                String.format("&r%s%s%d&r", enchantmentData.shortName, chatColors, (int) s)),
            x * (1.0f / scale),
            (y + (float) i * 4.0f) * (1.0f / scale));
      }
    }
  }

  public static void renderItemInGUI(ItemStack itemStack, int x, int y) {
    GlStateManager.pushMatrix();
    GlStateManager.depthMask(true);
    GlStateManager.clear(256);
    RenderHelper.enableGUIStandardItemLighting();
    GL11.glDisable(GL11.GL_LIGHTING);
    GlStateManager.pushMatrix();
    GlStateManager.scale(1.0f, 1.0f, -0.01f);
    RenderUtil.mc.getRenderItem().zLevel = -150.0f;
    mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, x, y);
    mc.getRenderItem().renderItemOverlays(RenderUtil.mc.fontRendererObj, itemStack, x, y);
    RenderUtil.mc.getRenderItem().zLevel = 0.0f;
    GlStateManager.popMatrix();
    RenderHelper.disableStandardItemLighting();
    GlStateManager.enableAlpha();
    GlStateManager.disableBlend();
    GlStateManager.enableTexture2D();
    GlStateManager.popMatrix();
    GlStateManager.pushMatrix();
    GlStateManager.scale(0.5f, 0.5f, 0.5f);
    GlStateManager.disableDepth();
    RenderUtil.renderEnchantmentText(itemStack, x, y, 0.5f);
    GlStateManager.enableDepth();
    GlStateManager.scale(2.0f, 2.0f, 2.0f);
    GlStateManager.popMatrix();
  }

  public static void renderPotionEffect(PotionEffect potionEffect, int x, int y) {
    int n3 = Potion.potionTypes[potionEffect.getPotionID()].getStatusIconIndex();
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.pushMatrix();
    GlStateManager.depthMask(true);
    GlStateManager.clear(256);
    GlStateManager.pushMatrix();
    GlStateManager.scale(1.0f, 1.0f, -0.01f);
    mc.getTextureManager()
        .bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
    Gui.drawModalRectWithCustomSizedTexture(
        x, y, n3 % 8 * 18, 198 + n3 / 8 * 18, 18, 18, 256.0f, 256.0f);
    GlStateManager.popMatrix();
    GlStateManager.enableAlpha();
    GlStateManager.disableBlend();
    GlStateManager.enableTexture2D();
    GlStateManager.popMatrix();
  }

  public static void drawRect(float x1, float y1, float x2, float y2, int color) {
    if (color == 0) {
      return;
    }
    boolean texture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
    boolean blend = GL11.glIsEnabled(GL_BLEND);
    if (texture2D) GL11.glDisable(GL11.GL_TEXTURE_2D);
    if (!blend) GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    RenderUtil.setColor(color);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(7, DefaultVertexFormats.POSITION);
    worldrenderer.pos((double) x1, (double) y1, 0.0D).endVertex();
    worldrenderer.pos((double) x1, (double) y2, 0.0D).endVertex();
    worldrenderer.pos((double) x2, (double) y2, 0.0D).endVertex();
    worldrenderer.pos((double) x2, (double) y1, 0.0D).endVertex();
    tessellator.draw();
    if (!blend) GL11.glDisable(GL11.GL_BLEND);
    if (texture2D) GL11.glEnable(GL11.GL_TEXTURE_2D);
    GlStateManager.resetColor();
  }

  public static void drawOutLineRect(
      float x,
      float y,
      float width,
      float height,
      float size,
      Color internalColor,
      Color borderColor) {
    drawRect(x, y, x + width, y + height, internalColor.getRGB());
    drawRect(x, y, x + size, y + height, borderColor.getRGB());
    drawRect(x, y - size, x + width + size, y, borderColor.getRGB());
    drawRect(x + width, y, x + width + size, y + height, borderColor.getRGB());
    drawRect(x, y + height - size, x + width, y + height, borderColor.getRGB());
  }

  public static int getContrastTextColor(Color bgColor) {
    double luminance =
        (0.299 * bgColor.getRed() + 0.587 * bgColor.getGreen() + 0.114 * bgColor.getBlue()) / 255.0;
    return luminance > 0.6 ? 0xff000000 : 0xffffffff;
  }

  public static void drawRect3D(float x1, float y1, float x2, float y2, int color) {
    if (color == 0) {
      return;
    }
    RenderUtil.setColor(color);
    GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
    GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(9, DefaultVertexFormats.POSITION);
    for (int i = 0; i < 2; ++i) {
      worldrenderer.pos((double) x1, (double) y1, 0.0D).endVertex();
      worldrenderer.pos((double) x1, (double) y2, 0.0D).endVertex();
      worldrenderer.pos((double) x2, (double) y2, 0.0D).endVertex();
      worldrenderer.pos((double) x2, (double) y1, 0.0D).endVertex();
    }
    tessellator.draw();
    GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
    GlStateManager.resetColor();
  }

  public static void drawOutlineRect(
      float x1, float y1, float x2, float y2, float lineWidth, int backgroundColor, int lineColor) {
    RenderUtil.drawRect(0.0f, 0.0f, x2, 27.0f, backgroundColor);
    if (lineColor == 0) {
      return;
    }
    RenderUtil.setColor(lineColor);
    GL11.glLineWidth(lineWidth);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(1, DefaultVertexFormats.POSITION);
    worldrenderer.pos((double) x1, (double) y1, 0.0D).endVertex();
    worldrenderer.pos((double) x1, (double) y2, 0.0D).endVertex();
    worldrenderer.pos((double) x2, (double) y2, 0.0D).endVertex();
    worldrenderer.pos((double) x2, (double) y1, 0.0D).endVertex();
    worldrenderer.pos((double) x1, (double) y1, 0.0D).endVertex();
    worldrenderer.pos((double) x2, (double) y1, 0.0D).endVertex();
    worldrenderer.pos((double) x1, (double) y2, 0.0D).endVertex();
    worldrenderer.pos((double) x2, (double) y2, 0.0D).endVertex();
    tessellator.draw();
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0f);
    GlStateManager.resetColor();
  }

  public static void drawLine(float x1, float y1, float x2, float y2, float lineWidth, int color) {
    RenderUtil.setColor(color);
    GL11.glLineWidth(lineWidth);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(1, DefaultVertexFormats.POSITION);
    worldrenderer.pos((double) x1, (double) y1, 0.0D).endVertex();
    worldrenderer.pos((double) x2, (double) y2, 0.0D).endVertex();
    tessellator.draw();
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0f);
    GlStateManager.resetColor();
  }

  public static void drawLine3D(
      Vec3 start,
      double endX,
      double endY,
      double endZ,
      float red,
      float green,
      float blue,
      float alpha,
      float lineWidth) {
    GlStateManager.pushMatrix();
    GlStateManager.color(red, green, blue, alpha);
    boolean bl = RenderUtil.mc.gameSettings.viewBobbing;
    RenderUtil.mc.gameSettings.viewBobbing = false;
    ((IAccessorEntityRenderer) RenderUtil.mc.entityRenderer)
        .callSetupCameraTransform(
            ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks, 2);
    RenderUtil.mc.gameSettings.viewBobbing = bl;
    GL11.glLineWidth(lineWidth);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(1, DefaultVertexFormats.POSITION);
    worldrenderer.pos(start.xCoord, start.yCoord, start.zCoord).endVertex();
    worldrenderer
        .pos(
            endX - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
            endY - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
            endZ - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ())
        .endVertex();
    tessellator.draw();
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0f);
    GlStateManager.resetColor();
    GlStateManager.popMatrix();
  }

  public static void drawArrow(
      float centerX, float centerY, float angle, float length, float lineWidth, int color) {
    float f6 = angle + (float) Math.toRadians(45.0);
    float f7 = angle - (float) Math.toRadians(45.0);
    RenderUtil.setColor(color);
    GL11.glLineWidth(lineWidth);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(1, DefaultVertexFormats.POSITION);
    worldrenderer.pos((double) centerX, (double) centerY, 0.0D).endVertex();
    worldrenderer
        .pos(
            (double) (centerX + length * (float) Math.cos(f6)),
            (double) (centerY + length * (float) Math.sin(f6)),
            0.0D)
        .endVertex();
    worldrenderer.pos((double) centerX, (double) centerY, 0.0D).endVertex();
    worldrenderer
        .pos(
            (double) (centerX + length * (float) Math.cos(f7)),
            (double) (centerY + length * (float) Math.sin(f7)),
            0.0D)
        .endVertex();
    tessellator.draw();
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0f);
    GlStateManager.resetColor();
  }

  public static void drawTriangle(
      float centerX, float centerY, float angle, float length, int color) {
    float f5 = angle + (float) Math.toRadians(26.25);
    float f6 = angle - (float) Math.toRadians(26.25);
    RenderUtil.setColor(color);
    GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
    GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(9, DefaultVertexFormats.POSITION);
    worldrenderer.pos((double) centerX, (double) centerY, 0.0D).endVertex();
    worldrenderer
        .pos(
            (double) (centerX + length * (float) Math.cos(f5)),
            (double) (centerY + length * (float) Math.sin(f5)),
            0.0D)
        .endVertex();
    worldrenderer
        .pos(
            (double) (centerX + length * (float) Math.cos(f6)),
            (double) (centerY + length * (float) Math.sin(f6)),
            0.0D)
        .endVertex();
    tessellator.draw();
    GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
    GlStateManager.resetColor();
  }

  public static void drawFramebuffer(Framebuffer framebuffer) {
    ScaledResolution scaledResolution = new ScaledResolution(mc);
    GlStateManager.bindTexture(framebuffer.framebufferTexture);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
    worldrenderer.pos(0.0, 0.0, 0.0D).tex(0.0, 1.0).endVertex();
    worldrenderer.pos(0.0, scaledResolution.getScaledHeight(), 0.0D).tex(0.0, 0.0).endVertex();
    worldrenderer
        .pos(scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight(), 0.0D)
        .tex(1.0, 0.0)
        .endVertex();
    worldrenderer.pos(scaledResolution.getScaledWidth(), 0.0, 0.0D).tex(1.0, 1.0).endVertex();
    tessellator.draw();
  }

  public static void fillCircle(double x, double y, double radius, int segments, int color) {
    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

    RenderUtil.setColor(color);

    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);
    worldrenderer.pos(x, y, 0.0D).endVertex();
    for (int i = 0; i <= segments; i++) {
      double angle = i * (Math.PI * 2.0 / segments);
      double px = x + Math.cos(angle) * radius;
      double py = y + Math.sin(angle) * radius;
      worldrenderer.pos(px, py, 0.0D).endVertex();
    }
    tessellator.draw();

    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.resetColor();
  }

  public static void drawCircle(
      double centerX, double centerY, double centerZ, double radius, int segments, int color) {
    RenderUtil.setColor(color);
    GL11.glLineWidth(3.0f);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION);
    for (int i = 0; i <= segments; ++i) {
      double d5 = (double) i * (Math.PI * 2 / (double) segments);
      worldrenderer
          .pos(centerX + Math.cos(d5) * radius, centerY, centerZ + Math.sin(d5) * radius)
          .endVertex();
    }
    tessellator.draw();
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0f);
    GlStateManager.resetColor();
  }

  public static void drawEntityCircle(Entity entity, double radius, int segments, int color) {
    double d2 =
        RenderUtil.lerpDouble(
                entity.posX,
                entity.lastTickPosX,
                ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks)
            - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
    double d3 =
        RenderUtil.lerpDouble(
                entity.posY,
                entity.lastTickPosY,
                ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks)
            - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
    double d4 =
        RenderUtil.lerpDouble(
                entity.posZ,
                entity.lastTickPosZ,
                ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks)
            - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
    RenderUtil.drawCircle(d2, d3, d4, radius, segments, color);
  }

  public static void drawFilledBox(AxisAlignedBB axisAlignedBB, int red, int green, int blue) {
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldRenderer = tessellator.getWorldRenderer();
    worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(red, green, blue, 63)
        .endVertex();
    tessellator.draw();
  }

  public static void drawFilledBox(AxisAlignedBB axisAlignedBB, float red, float green, float blue, float alpha) {
    int a = (int) (alpha * 255);
    int r = (int) (red * 255);
    int g = (int) (green * 255);
    int b = (int) (blue * 255);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldRenderer = tessellator.getWorldRenderer();
    worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
        .color(r, g, b, a)
        .endVertex();
    worldRenderer
        .pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ)
        .color(r, g, b, a)
        .endVertex();
    tessellator.draw();
  }

  public static void drawBoundingBox(
      AxisAlignedBB axisAlignedBB, int red, int green, int blue, int alpha, float lineWidth) {
    GL11.glLineWidth(lineWidth);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    RenderGlobal.drawOutlinedBoundingBox(axisAlignedBB, red, green, blue, alpha);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0f);
  }

  public static void drawEntityBox(Entity entity, int red, int green, int blue) {
    double d2 =
        RenderUtil.lerpDouble(
            entity.posX,
            entity.lastTickPosX,
            ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
    double d3 =
        RenderUtil.lerpDouble(
            entity.posY,
            entity.lastTickPosY,
            ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
    double d4 =
        RenderUtil.lerpDouble(
            entity.posZ,
            entity.lastTickPosZ,
            ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
    RenderUtil.drawFilledBox(
        entity
            .getEntityBoundingBox()
            .expand(0.1f, 0.1f, 0.1f)
            .offset(d2 - entity.posX, d3 - entity.posY, d4 - entity.posZ)
            .offset(
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()),
        red,
        green,
        blue);
  }

  public static void drawEntityBoundingBox(
      Entity entity, int red, int green, int blue, int alpha, float lineWidth, double expand) {
    double d2 =
        RenderUtil.lerpDouble(
            entity.posX,
            entity.lastTickPosX,
            ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
    double d3 =
        RenderUtil.lerpDouble(
            entity.posY,
            entity.lastTickPosY,
            ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
    double d4 =
        RenderUtil.lerpDouble(
            entity.posZ,
            entity.lastTickPosZ,
            ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
    RenderUtil.drawBoundingBox(
        entity
            .getEntityBoundingBox()
            .expand(expand, expand, expand)
            .offset(d2 - entity.posX, d3 - entity.posY, d4 - entity.posZ)
            .offset(
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()),
        red,
        green,
        blue,
        alpha,
        lineWidth);
  }

  public static void drawBlockBox(BlockPos blockPos, double height, int red, int green, int blue) {
    RenderUtil.drawFilledBox(
        new AxisAlignedBB(
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ(),
                (double) blockPos.getX() + 1.0,
                (double) blockPos.getY() + height,
                (double) blockPos.getZ() + 1.0)
            .offset(
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()),
        red,
        green,
        blue);
  }

  public static void drawBlockBoundingBox(
      BlockPos blockPos, double height, int red, int green, int blue, int alpha, float lineWidth) {
    RenderUtil.drawBoundingBox(
        new AxisAlignedBB(
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ(),
                (double) blockPos.getX() + 1.0,
                (double) blockPos.getY() + height,
                (double) blockPos.getZ() + 1.0)
            .offset(
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()),
        red,
        green,
        blue,
        alpha,
        lineWidth);
  }

  public static void drawCornerESP(EntityPlayer entity, float red, float green, float blue) {
    float x =
        (float)
            (RenderUtil.lerpDouble(
                    entity.posX,
                    entity.lastTickPosX,
                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX());
    float y =
        (float)
            (RenderUtil.lerpDouble(
                    entity.posY,
                    entity.lastTickPosY,
                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY());
    float z =
        (float)
            (RenderUtil.lerpDouble(
                    entity.posZ,
                    entity.lastTickPosZ,
                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y + entity.height / 2.0F, z);
    GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
    GlStateManager.scale(-0.098F, -0.098F, 0.098F);
    float width = (float) (26.6 * entity.width / 2.0);
    float height = 12.0F;
    GlStateManager.color(red, green, blue);
    draw3DRect(width, height - 1.0F, width - 4.0F, height);
    draw3DRect(-width, height - 1.0F, -width + 4.0F, height);
    draw3DRect(-width, height, -width + 1.0F, height - 4.0F);
    draw3DRect(width, height, width - 1.0F, height - 4.0F);
    draw3DRect(width, -height, width - 4.0F, -height + 1.0F);
    draw3DRect(-width, -height, -width + 4.0F, -height + 1.0F);
    draw3DRect(-width, -height + 1.0F, -width + 1.0F, -height + 4.0F);
    draw3DRect(width, -height + 1.0F, width - 1.0F, -height + 4.0F);
    GlStateManager.color(0.0F, 0.0F, 0.0F);
    draw3DRect(width, height, width - 4.0F, height + 0.2F);
    draw3DRect(-width, height, -width + 4.0F, height + 0.2F);
    draw3DRect(-width - 0.2F, height + 0.2F, -width, height - 4.0F);
    draw3DRect(width + 0.2F, height + 0.2F, width, height - 4.0F);
    draw3DRect(width + 0.2F, -height, width - 4.0F, -height - 0.2F);
    draw3DRect(-width - 0.2F, -height, -width + 4.0F, -height - 0.2F);
    draw3DRect(-width - 0.2F, -height, -width, -height + 4.0F);
    draw3DRect(width + 0.2F, -height, width, -height + 4.0F);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    GlStateManager.popMatrix();
  }

  public static void drawFake2DESP(EntityPlayer entity, float red, float green, float blue) {
    float x =
        (float)
            (RenderUtil.lerpDouble(
                    entity.posX,
                    entity.lastTickPosX,
                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX());
    float y =
        (float)
            (RenderUtil.lerpDouble(
                    entity.posY,
                    entity.lastTickPosY,
                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY());
    float z =
        (float)
            (RenderUtil.lerpDouble(
                    entity.posZ,
                    entity.lastTickPosZ,
                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y + entity.height / 2.0F, z);
    GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
    GlStateManager.scale(-0.1F, -0.1F, 0.1F);
    GlStateManager.color(red, green, blue);
    float width = (float) (23.3 * entity.width / 2.0);
    float height = 12.0F;
    draw3DRect(width, height, -width, height + 0.4F);
    draw3DRect(width, -height, -width, -height + 0.4F);
    draw3DRect(width, -height + 0.4F, width - 0.4F, height + 0.4F);
    draw3DRect(-width, -height + 0.4F, -width + 0.4F, height + 0.4F);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    GlStateManager.popMatrix();
  }

  public static void draw3DRect(float x1, float y1, float x2, float y2) {
    GL11.glBegin(GL11.GL_POLYGON);
    GL11.glVertex2f(x2, y1);
    GL11.glVertex2f(x1, y1);
    GL11.glVertex2f(x1, y2);
    GL11.glVertex2f(x2, y2);
    GL11.glEnd();
  }

  public static Vector4d projectToScreen(Entity entity, double screenScale) {
    Vector4d vector4d;
    {
      double d3 =
          RenderUtil.lerpDouble(
              entity.posX,
              entity.lastTickPosX,
              ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
      double d4 =
          RenderUtil.lerpDouble(
              entity.posY,
              entity.lastTickPosY,
              ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
      double d5 =
          RenderUtil.lerpDouble(
              entity.posZ,
              entity.lastTickPosZ,
              ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
      AxisAlignedBB axisAlignedBB =
          entity
              .getEntityBoundingBox()
              .expand(0.1f, 0.1f, 0.1f)
              .offset(d3 - entity.posX, d4 - entity.posY, d5 - entity.posZ);
      vector4d = null;
      for (Vector3d vector3d :
          new Vector3d[] {
            new Vector3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ),
            new Vector3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ),
            new Vector3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ),
            new Vector3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ),
            new Vector3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ),
            new Vector3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ),
            new Vector3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ),
            new Vector3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)
          }) {
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        if (!GLU.gluProject(
            (float) (vector3d.x - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX()),
            (float) (vector3d.y - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()),
            (float) (vector3d.z - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()),
            modelViewBuffer,
            projectionBuffer,
            viewportBuffer,
            vectorBuffer)) continue;
        vector3d =
            new Vector3d(
                (double) vectorBuffer.get(0) / screenScale,
                (double) ((float) Display.getHeight() - vectorBuffer.get(1)) / screenScale,
                vectorBuffer.get(2));
        if (!(vector3d.z >= 0.0) || !(vector3d.z < 1.0)) continue;
        if (vector4d == null) {
          vector4d = new Vector4d(vector3d.x, vector3d.y, vector3d.z, 0.0);
        }
        vector4d.x = Math.min(vector3d.x, vector4d.x);
        vector4d.y = Math.min(vector3d.y, vector4d.y);
        vector4d.z = Math.max(vector3d.x, vector4d.z);
        vector4d.w = Math.max(vector3d.y, vector4d.w);
      }
    }
    return vector4d;
  }

  public static boolean isInViewFrustum(AxisAlignedBB axisAlignedBB, double expand) {
    cameraFrustum.setPosition(
        RenderUtil.mc.getRenderViewEntity().posX,
        RenderUtil.mc.getRenderViewEntity().posY,
        RenderUtil.mc.getRenderViewEntity().posZ);
    return cameraFrustum.isBoundingBoxInFrustum(axisAlignedBB.expand(expand, expand, expand));
  }

  public static void enableRenderState() {
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableTexture2D();
    GlStateManager.disableCull();
    GlStateManager.disableAlpha();
    GlStateManager.disableDepth();
  }

  public static void disableRenderState() {
    GlStateManager.enableDepth();
    GlStateManager.enableAlpha();
    GlStateManager.enableCull();
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
  }

  public static void setColor(int argb) {
    float f = (float) (argb >> 24 & 0xFF) / 255.0f;
    float f2 = (float) (argb >> 16 & 0xFF) / 255.0f;
    float f3 = (float) (argb >> 8 & 0xFF) / 255.0f;
    float f4 = (float) (argb & 0xFF) / 255.0f;
    GlStateManager.color(f2, f3, f4, f);
  }

  public static float lerpFloat(float current, float previous, float t) {
    return previous + (current - previous) * t;
  }

  public static double lerpDouble(double current, double previous, double t) {
    return previous + (current - previous) * t;
  }

  public static final class EnchantmentData {
    public final String shortName;
    public final int maxLevel;

    public EnchantmentData(String shortName, int maxLevel) {
      this.shortName = shortName;
      this.maxLevel = maxLevel;
    }
  }

  static final class EnchantmentMap extends HashMap<Integer, EnchantmentData> {
    EnchantmentMap() {
      this.put(0, new EnchantmentData("Pr", 4));
      this.put(1, new EnchantmentData("Fp", 4));
      this.put(2, new EnchantmentData("Ff", 4));
      this.put(3, new EnchantmentData("Bp", 4));
      this.put(4, new EnchantmentData("Pp", 4));
      this.put(5, new EnchantmentData("Re", 3));
      this.put(6, new EnchantmentData("Aq", 1));
      this.put(7, new EnchantmentData("Th", 3));
      this.put(8, new EnchantmentData("Ds", 3));
      this.put(16, new EnchantmentData("Sh", 5));
      this.put(17, new EnchantmentData("Sm", 5));
      this.put(18, new EnchantmentData("BoA", 5));
      this.put(19, new EnchantmentData("Kb", 2));
      this.put(20, new EnchantmentData("Fa", 2));
      this.put(21, new EnchantmentData("Lo", 3));
      this.put(32, new EnchantmentData("Ef", 5));
      this.put(33, new EnchantmentData("St", 1));
      this.put(34, new EnchantmentData("Ub", 3));
      this.put(35, new EnchantmentData("Fo", 3));
      this.put(48, new EnchantmentData("Po", 5));
      this.put(49, new EnchantmentData("Pu", 2));
      this.put(50, new EnchantmentData("Fl", 1));
      this.put(51, new EnchantmentData("Inf", 1));
      this.put(61, new EnchantmentData("LoS", 3));
      this.put(62, new EnchantmentData("Lu", 3));
    }
  }

  private static Frustum frustum = new Frustum();

  private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
  private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
  private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
  private static final FloatBuffer SCREEN_COORDS = BufferUtils.createFloatBuffer(3);

  public static final class ProjectionContext {
    private int scaleFactor;
    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);
  }

  public static void renderBlock(BlockPos blockPos, int color, boolean outline, boolean shade) {
    renderBox(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1, 1, 1, color, outline, shade);
  }

  public static void renderChest(BlockPos blockPos, int color, boolean outline, boolean shade) {
    renderBox(
        blockPos.getX() + 0.0625F,
        blockPos.getY(),
        blockPos.getZ() + 0.0625F,
        0.875f,
        0.875f,
        0.875f,
        color,
        outline,
        shade);
  }

  public static void renderChestBatch(
      List<BlockPos> positions, int color, boolean outline, boolean shade) {
    renderChestBatch(positions, color, color, outline, shade);
  }

  public static void renderChestBatch(
      List<BlockPos> positions, int outlineColor, int shadeColor, boolean outline, boolean shade) {
    if (positions == null || positions.isEmpty()) {
      return;
    }
    double vx = mc.getRenderManager().viewerPosX;
    double vy = mc.getRenderManager().viewerPosY;
    double vz = mc.getRenderManager().viewerPosZ;
    GL11.glPushMatrix();
    GL11.glBlendFunc(770, 771);
    glEnable(3042);
    GL11.glLineWidth(2.0f);
    GL11.glDisable(3553);
    GL11.glDisable(2929);
    GL11.glDepthMask(false);
    float outlineA = (outlineColor >> 24 & 0xFF) / 255.0f;
    float outlineR = (outlineColor >> 16 & 0xFF) / 255.0f;
    float outlineG = (outlineColor >> 8 & 0xFF) / 255.0f;
    float outlineB = (outlineColor & 0xFF) / 255.0f;
    float shadeA = (shadeColor >> 24 & 0xFF) / 255.0f;
    float shadeR = (shadeColor >> 16 & 0xFF) / 255.0f;
    float shadeG = (shadeColor >> 8 & 0xFF) / 255.0f;
    float shadeB = (shadeColor & 0xFF) / 255.0f;
    for (BlockPos blockPos : positions) {
      double xPos = blockPos.getX() + 0.0625 - vx;
      double yPos = blockPos.getY() - vy;
      double zPos = blockPos.getZ() + 0.0625 - vz;
      AxisAlignedBB axisAlignedBB =
          new AxisAlignedBB(xPos, yPos, zPos, xPos + 0.875, yPos + 0.875, zPos + 0.875);
      if (outline) {
        GL11.glColor4f(outlineR, outlineG, outlineB, outlineA);
        RenderGlobal.drawSelectionBoundingBox(axisAlignedBB);
      }
      if (shade) {
        drawBoundingBox(axisAlignedBB, shadeR, shadeG, shadeB, shadeA);
      }
    }
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    glEnable(3553);
    glEnable(2929);
    GL11.glDepthMask(true);
    GL11.glDisable(3042);
    GL11.glPopMatrix();
  }

  public static void renderBlock(
      BlockPos blockPos, int color, double y2, boolean outline, boolean shade) {
    renderBox(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1, y2, 1, color, outline, shade);
  }

  /**
   * Sets scissor in GUI coords (origin top-left). Uses edge-aware rounding so the clip rectangle
   * does not lose/gain a pixel at animation boundaries.
   */
  public static void scissor(double x, double y, double width, double height) {
    ScaledResolution sr = new ScaledResolution(mc);
    if (mc.currentScreen instanceof ClickGui && ClickGui.openingScale != 1.0f) {
      double scaleFactor = ClickGui.openingScale;
      double centerX = sr.getScaledWidth() / 2.0;
      double centerY = sr.getScaledHeight() / 2.0;
      x = centerX + (x - centerX) * scaleFactor;
      y = centerY + (y - centerY) * scaleFactor;
      width *= scaleFactor;
      height *= scaleFactor;
    }

    double guiScale = ClickGui.getActiveRenderScale();
    x *= guiScale;
    y *= guiScale;
    width *= guiScale;
    height *= guiScale;

    int scale = sr.getScaleFactor();
    double screenH = sr.getScaledHeight();

    int left = (int) Math.floor(x * scale);
    int right = (int) Math.ceil((x + width) * scale);
    int scaledWidth = Math.max(0, right - left);

    double bottomGui = y + height;
    int glBottom = (int) Math.floor((screenH - bottomGui) * scale);
    int glTop = (int) Math.ceil((screenH - y) * scale);
    int scaledHeight = Math.max(0, glTop - glBottom);

    if (scaledWidth < 0 || scaledHeight < 0) {
      return;
    }

    GL11.glScissor(left, glBottom, scaledWidth, scaledHeight);
  }

  private static final int SCISSOR_PUSH_STACK_DEPTH = 4;
  private static final IntBuffer SCISSOR_PUSH_BUF = BufferUtils.createIntBuffer(16);
  private static final int[][] scissorPushStack = new int[SCISSOR_PUSH_STACK_DEPTH][5];
  private static int scissorPushDepth = 0;

  public static void scissorPushGui(double x, double y, double width, double height) {
    ScaledResolution sr = new ScaledResolution(mc);
    if (mc.currentScreen instanceof ClickGui && ClickGui.openingScale != 1.0f) {
      double scaleFactor = ClickGui.openingScale;
      double centerX = sr.getScaledWidth() / 2.0;
      double centerY = sr.getScaledHeight() / 2.0;
      x = centerX + (x - centerX) * scaleFactor;
      y = centerY + (y - centerY) * scaleFactor;
      width *= scaleFactor;
      height *= scaleFactor;
    }

    double guiScale = ClickGui.getActiveRenderScale();
    x *= guiScale;
    y *= guiScale;
    width *= guiScale;
    height *= guiScale;

    int scale = sr.getScaleFactor();
    double screenH = sr.getScaledHeight();
    int left = (int) Math.floor(x * scale);
    int right = (int) Math.ceil((x + width) * scale);
    int scaledWidth = Math.max(0, right - left);
    double bottomGui = y + height;
    int glBottom = (int) Math.floor((screenH - bottomGui) * scale);
    int glTop = (int) Math.ceil((screenH - y) * scale);
    int scaledHeight = Math.max(0, glTop - glBottom);
    boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    int[] saved = scissorPushStack[scissorPushDepth++];
    if (scissorPushDepth > SCISSOR_PUSH_STACK_DEPTH)
      throw new IllegalStateException("Scissor stack overflow");
    if (wasEnabled) {
      ((java.nio.Buffer) SCISSOR_PUSH_BUF).clear();
      GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_PUSH_BUF);
      saved[0] = 1;
      saved[1] = SCISSOR_PUSH_BUF.get(0);
      saved[2] = SCISSOR_PUSH_BUF.get(1);
      saved[3] = SCISSOR_PUSH_BUF.get(2);
      saved[4] = SCISSOR_PUSH_BUF.get(3);
      int ix = Math.max(saved[1], left);
      int iy = Math.max(saved[2], glBottom);
      int iw = Math.max(0, Math.min(saved[1] + saved[3], left + scaledWidth) - ix);
      int ih = Math.max(0, Math.min(saved[2] + saved[4], glBottom + scaledHeight) - iy);
      GL11.glScissor(ix, iy, iw, ih);
    } else {
      saved[0] = 0;
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(left, glBottom, scaledWidth, scaledHeight);
    }
  }

  public static void scissorPop() {
    int[] saved = scissorPushStack[--scissorPushDepth];
    if (saved[0] == 1) {
      GL11.glScissor(saved[1], saved[2], saved[3], saved[4]);
    } else {
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
  }

  public static boolean isInViewFrustum(final Entity entity) {
    if (entity == null) return false;
    return isInViewFrustum(entity.getEntityBoundingBox()) || entity.ignoreFrustumCheck;
  }

  public static boolean isInViewFrustum(final AxisAlignedBB bb) {
    if (bb == null) return false;
    Entity view = mc.getRenderViewEntity();
    if (view == null) return true;
    frustum.setPosition(view.posX, view.posY, view.posZ);
    return frustum.isBoundingBoxInFrustum(bb);
  }

  public static boolean isWithinDistanceSqToRenderView(
      final Entity entity, final double maxDistSq) {
    if (entity == null) return false;
    Entity view = mc.getRenderViewEntity();
    if (view == null) return false;
    return entity.getDistanceSqToEntity(view) <= maxDistSq;
  }

  public static boolean isBlockPosWithinDistanceSqToView(
      final BlockPos pos, final double maxDistSq) {
    if (pos == null) return false;
    Entity view = mc.getRenderViewEntity();
    if (view == null) return false;
    double dx = pos.getX() + 0.5 - view.posX;
    double dy = pos.getY() + 0.5 - view.posY;
    double dz = pos.getZ() + 0.5 - view.posZ;
    return dx * dx + dy * dy + dz * dz <= maxDistSq;
  }

  public static void drawRect(double left, double top, double right, double bottom, int color) {
    float f3 = (color >> 24 & 255) / 255.0F;
    float f = (color >> 16 & 255) / 255.0F;
    float f1 = (color >> 8 & 255) / 255.0F;
    float f2 = (color & 255) / 255.0F;
    GlStateManager.pushMatrix();
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.color(f, f1, f2, f3);
    worldrenderer.begin(7, DefaultVertexFormats.POSITION);
    worldrenderer.pos(left, bottom, 0.0D).endVertex();
    worldrenderer.pos(right, bottom, 0.0D).endVertex();
    worldrenderer.pos(right, top, 0.0D).endVertex();
    worldrenderer.pos(left, top, 0.0D).endVertex();
    tessellator.draw();
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    GlStateManager.popMatrix();
  }

  public static void drawPlayerBoundingBox(Vec3 pos, int color) {
    GlStateManager.pushMatrix();
    double x = pos.xCoord - mc.getRenderManager().viewerPosX;
    double y = pos.yCoord - mc.getRenderManager().viewerPosY;
    double z = pos.zCoord - mc.getRenderManager().viewerPosZ;
    AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox().expand(0.1D, 0.1, 0.1);
    AxisAlignedBB axis =
        new AxisAlignedBB(
            bbox.minX - mc.thePlayer.posX + x,
            bbox.minY - mc.thePlayer.posY + y,
            bbox.minZ - mc.thePlayer.posZ + z,
            bbox.maxX - mc.thePlayer.posX + x,
            bbox.maxY - mc.thePlayer.posY + y,
            bbox.maxZ - mc.thePlayer.posZ + z);
    float a = (float) (color >> 24 & 255) / 255.0F;
    float r = (float) (color >> 16 & 255) / 255.0F;
    float g = (float) (color >> 8 & 255) / 255.0F;
    float b = (float) (color & 255) / 255.0F;
    GL11.glBlendFunc(770, 771);
    GL11.glEnable(3042);
    GL11.glDisable(3553);
    GL11.glDisable(2929);
    GL11.glDepthMask(false);
    GL11.glLineWidth(2.0F);
    GL11.glColor4f(r, g, b, a);
    drawBoundingBox(axis, r, g, b, a);
    GL11.glEnable(3553);
    GL11.glEnable(2929);
    GL11.glDepthMask(true);
    GL11.glDisable(3042);
    GlStateManager.popMatrix();
  }

  public static void drawOutline(float x, float y, float x2, float y2, float lineWidth, int color) {
    float f5 = (float) ((color >> 24) & 255) / 255.0F;
    float f6 = (float) ((color >> 16) & 255) / 255.0F;
    float f7 = (float) ((color >> 8) & 255) / 255.0F;
    float f8 = (float) (color & 255) / 255.0F;

    glEnable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glPushMatrix();
    GL11.glColor4f(f6, f7, f8, f5);
    GL11.glLineWidth(lineWidth);
    GL11.glBegin(1);
    GL11.glVertex2d(x, y);
    GL11.glVertex2d(x, y2);
    GL11.glVertex2d(x2, y2);
    GL11.glVertex2d(x2, y);
    GL11.glVertex2d(x, y);
    GL11.glVertex2d(x2, y);
    GL11.glVertex2d(x, y2);
    GL11.glVertex2d(x2, y2);
    GL11.glEnd();
    GL11.glColor4f(1f, 1f, 1f, 1f);
    glPopMatrix();
    glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
  }

  public static void renderBox(
      double x,
      double y,
      double z,
      double x2,
      double y2,
      double z2,
      int color,
      boolean outline,
      boolean shade) {
    double xPos = x - mc.getRenderManager().viewerPosX;
    double yPos = y - mc.getRenderManager().viewerPosY;
    double zPos = z - mc.getRenderManager().viewerPosZ;
    GL11.glPushMatrix();
    GL11.glBlendFunc(770, 771);
    glEnable(3042);
    GL11.glLineWidth(2.0f);
    GL11.glDisable(3553);
    GL11.glDisable(2929);
    GL11.glDepthMask(false);
    float n8 = (color >> 24 & 0xFF) / 255.0f;
    float n9 = (color >> 16 & 0xFF) / 255.0f;
    float n10 = (color >> 8 & 0xFF) / 255.0f;
    float n11 = (color & 0xFF) / 255.0f;
    GL11.glColor4f(n9, n10, n11, n8);
    AxisAlignedBB axisAlignedBB =
        new AxisAlignedBB(xPos, yPos, zPos, xPos + x2, yPos + y2, zPos + z2);
    if (outline) {
      RenderGlobal.drawSelectionBoundingBox(axisAlignedBB);
    }
    if (shade) {
      drawBoundingBox(axisAlignedBB, n9, n10, n11);
    }
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    glEnable(3553);
    glEnable(2929);
    GL11.glDepthMask(true);
    GL11.glDisable(3042);
    glPopMatrix();
  }

  /**
   * Renders only the given faces of a full block at pos (no double render when adjacent blocks are
   * also highlighted). Caller should pass only faces whose neighbor is not in the same highlight
   * set. Shaded fill uses 0.25f alpha to match drawBoundingBox; outline uses full color.
   */
  public static void renderBlockFaces(
      BlockPos blockPos,
      int color,
      boolean outline,
      boolean shade,
      java.util.Set<EnumFacing> faces) {
    if (faces == null || faces.isEmpty()) return;
    double xPos = blockPos.getX() - mc.getRenderManager().viewerPosX;
    double yPos = blockPos.getY() - mc.getRenderManager().viewerPosY;
    double zPos = blockPos.getZ() - mc.getRenderManager().viewerPosZ;
    double maxX = xPos + 1;
    double maxY = yPos + 1;
    double maxZ = zPos + 1;
    float r = (color >> 16 & 0xFF) / 255.0f;
    float g = (color >> 8 & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;
    float outlineA = (color >> 24 & 0xFF) / 255.0f;
    float shadeA = 0.25f;
    GL11.glPushMatrix();
    GL11.glBlendFunc(770, 771);
    glEnable(3042);
    GL11.glLineWidth(2.0f);
    GL11.glDisable(3553);
    GL11.glDisable(2929);
    GL11.glDepthMask(false);
    Tessellator ts = Tessellator.getInstance();
    WorldRenderer vb = ts.getWorldRenderer();
    if (shade) {
      vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
      if (faces.contains(EnumFacing.DOWN)) {
        vb.pos(xPos, yPos, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, yPos, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, yPos, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(xPos, yPos, maxZ).color(r, g, b, shadeA).endVertex();
      }
      if (faces.contains(EnumFacing.UP)) {
        vb.pos(xPos, maxY, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(xPos, maxY, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, maxY, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, maxY, zPos).color(r, g, b, shadeA).endVertex();
      }
      if (faces.contains(EnumFacing.NORTH)) {
        vb.pos(xPos, yPos, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(xPos, maxY, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, maxY, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, yPos, zPos).color(r, g, b, shadeA).endVertex();
      }
      if (faces.contains(EnumFacing.SOUTH)) {
        vb.pos(maxX, yPos, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, maxY, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(xPos, maxY, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(xPos, yPos, maxZ).color(r, g, b, shadeA).endVertex();
      }
      if (faces.contains(EnumFacing.WEST)) {
        vb.pos(xPos, yPos, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(xPos, maxY, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(xPos, maxY, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(xPos, yPos, maxZ).color(r, g, b, shadeA).endVertex();
      }
      if (faces.contains(EnumFacing.EAST)) {
        vb.pos(maxX, yPos, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, maxY, maxZ).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, maxY, zPos).color(r, g, b, shadeA).endVertex();
        vb.pos(maxX, yPos, zPos).color(r, g, b, shadeA).endVertex();
      }
      ts.draw();
    }
    if (outline) {
      GL11.glColor4f(r, g, b, outlineA);
      vb.begin(1, DefaultVertexFormats.POSITION);
      if (faces.contains(EnumFacing.DOWN)) {
        vb.pos(xPos, yPos, zPos).endVertex();
        vb.pos(maxX, yPos, zPos).endVertex();
        vb.pos(maxX, yPos, zPos).endVertex();
        vb.pos(maxX, yPos, maxZ).endVertex();
        vb.pos(maxX, yPos, maxZ).endVertex();
        vb.pos(xPos, yPos, maxZ).endVertex();
        vb.pos(xPos, yPos, maxZ).endVertex();
        vb.pos(xPos, yPos, zPos).endVertex();
      }
      if (faces.contains(EnumFacing.UP)) {
        vb.pos(xPos, maxY, zPos).endVertex();
        vb.pos(maxX, maxY, zPos).endVertex();
        vb.pos(maxX, maxY, zPos).endVertex();
        vb.pos(maxX, maxY, maxZ).endVertex();
        vb.pos(maxX, maxY, maxZ).endVertex();
        vb.pos(xPos, maxY, maxZ).endVertex();
        vb.pos(xPos, maxY, maxZ).endVertex();
        vb.pos(xPos, maxY, zPos).endVertex();
      }
      if (faces.contains(EnumFacing.NORTH)) {
        vb.pos(xPos, yPos, zPos).endVertex();
        vb.pos(xPos, maxY, zPos).endVertex();
        vb.pos(xPos, maxY, zPos).endVertex();
        vb.pos(maxX, maxY, zPos).endVertex();
        vb.pos(maxX, maxY, zPos).endVertex();
        vb.pos(maxX, yPos, zPos).endVertex();
        vb.pos(maxX, yPos, zPos).endVertex();
        vb.pos(xPos, yPos, zPos).endVertex();
      }
      if (faces.contains(EnumFacing.SOUTH)) {
        vb.pos(xPos, yPos, maxZ).endVertex();
        vb.pos(xPos, maxY, maxZ).endVertex();
        vb.pos(xPos, maxY, maxZ).endVertex();
        vb.pos(maxX, maxY, maxZ).endVertex();
        vb.pos(maxX, maxY, maxZ).endVertex();
        vb.pos(maxX, yPos, maxZ).endVertex();
        vb.pos(maxX, yPos, maxZ).endVertex();
        vb.pos(xPos, yPos, maxZ).endVertex();
      }
      if (faces.contains(EnumFacing.WEST)) {
        vb.pos(xPos, yPos, zPos).endVertex();
        vb.pos(xPos, maxY, zPos).endVertex();
        vb.pos(xPos, maxY, zPos).endVertex();
        vb.pos(xPos, maxY, maxZ).endVertex();
        vb.pos(xPos, maxY, maxZ).endVertex();
        vb.pos(xPos, yPos, maxZ).endVertex();
        vb.pos(xPos, yPos, maxZ).endVertex();
        vb.pos(xPos, yPos, zPos).endVertex();
      }
      if (faces.contains(EnumFacing.EAST)) {
        vb.pos(maxX, yPos, zPos).endVertex();
        vb.pos(maxX, maxY, zPos).endVertex();
        vb.pos(maxX, maxY, zPos).endVertex();
        vb.pos(maxX, maxY, maxZ).endVertex();
        vb.pos(maxX, maxY, maxZ).endVertex();
        vb.pos(maxX, yPos, maxZ).endVertex();
        vb.pos(maxX, yPos, maxZ).endVertex();
        vb.pos(maxX, yPos, zPos).endVertex();
      }
      ts.draw();
    }
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    glEnable(3553);
    glEnable(2929);
    GL11.glDepthMask(true);
    GL11.glDisable(3042);
    glPopMatrix();
  }

  private static void drawBoxFaceVertex(WorldRenderer wr, double x, double y, double z, int color) {
    wr.pos(x, y, z)
        .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF)
        .endVertex();
  }

  private static void drawBoxFaceVertices(
      WorldRenderer wr, EnumFacing face, AxisAlignedBB box, int start, int end) {
    switch (face) {
      case UP:
        drawBoxFaceVertex(wr, box.minX, box.maxY, box.maxZ, start);
        drawBoxFaceVertex(wr, box.maxX, box.maxY, box.maxZ, end);
        drawBoxFaceVertex(wr, box.maxX, box.maxY, box.minZ, start);
        drawBoxFaceVertex(wr, box.minX, box.maxY, box.minZ, end);
        break;
      case DOWN:
        drawBoxFaceVertex(wr, box.maxX, box.minY, box.maxZ, start);
        drawBoxFaceVertex(wr, box.minX, box.minY, box.maxZ, end);
        drawBoxFaceVertex(wr, box.minX, box.minY, box.minZ, start);
        drawBoxFaceVertex(wr, box.maxX, box.minY, box.minZ, end);
        break;
      case NORTH:
        drawBoxFaceVertex(wr, box.maxX, box.maxY, box.minZ, start);
        drawBoxFaceVertex(wr, box.maxX, box.minY, box.minZ, end);
        drawBoxFaceVertex(wr, box.minX, box.minY, box.minZ, start);
        drawBoxFaceVertex(wr, box.minX, box.maxY, box.minZ, end);
        break;
      case SOUTH:
        drawBoxFaceVertex(wr, box.minX, box.maxY, box.maxZ, start);
        drawBoxFaceVertex(wr, box.minX, box.minY, box.maxZ, end);
        drawBoxFaceVertex(wr, box.maxX, box.minY, box.maxZ, start);
        drawBoxFaceVertex(wr, box.maxX, box.maxY, box.maxZ, end);
        break;
      case EAST:
        drawBoxFaceVertex(wr, box.maxX, box.maxY, box.minZ, start);
        drawBoxFaceVertex(wr, box.maxX, box.maxY, box.maxZ, end);
        drawBoxFaceVertex(wr, box.maxX, box.minY, box.maxZ, start);
        drawBoxFaceVertex(wr, box.maxX, box.minY, box.minZ, end);
        break;
      case WEST:
        drawBoxFaceVertex(wr, box.minX, box.maxY, box.maxZ, start);
        drawBoxFaceVertex(wr, box.minX, box.maxY, box.minZ, end);
        drawBoxFaceVertex(wr, box.minX, box.minY, box.minZ, start);
        drawBoxFaceVertex(wr, box.minX, box.minY, box.maxZ, end);
        break;
    }
  }

  /**
   * Draws one face of an AABB (box in current GL space). Caller must have set blend, disabled
   * texture, etc.
   */
  public static void drawBoxFace(
      AxisAlignedBB box,
      EnumFacing face,
      int overlayColor,
      int outlineColor,
      boolean overlay,
      boolean outline) {
    Tessellator ts = Tessellator.getInstance();
    WorldRenderer wr = ts.getWorldRenderer();
    if (overlay) {
      wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
      drawBoxFaceVertices(wr, face, box, overlayColor, overlayColor);
      ts.draw();
    }
    if (outline) {
      wr.begin(2, DefaultVertexFormats.POSITION_COLOR);
      drawBoxFaceVertices(wr, face, box, outlineColor, outlineColor);
      ts.draw();
    }
  }

  public static void renderBlockShape(
      BlockPos pos,
      IBlockState state,
      int color,
      boolean outline,
      boolean shade,
      java.util.Set<EnumFacing> visibleFaces) {
    AxisAlignedBB box = state.getBlock().getSelectedBoundingBox(mc.theWorld, pos);
    if (box == null) return;
    double vx = mc.getRenderManager().viewerPosX,
        vy = mc.getRenderManager().viewerPosY,
        vz = mc.getRenderManager().viewerPosZ;
    int overlayColor = (color & 0x00FFFFFF) | (63 << 24);
    int outlineColor = color | 0xFF000000;

    GL11.glPushMatrix();
    GL11.glBlendFunc(770, 771);
    glEnable(3042);
    GL11.glLineWidth(2.0f);
    GL11.glDisable(3553);
    GL11.glDisable(2929);
    GL11.glDepthMask(false);

    AxisAlignedBB renderBox = box.offset(-vx, -vy, -vz);
    for (EnumFacing face : visibleFaces) {
      drawBoxFace(renderBox, face, overlayColor, outlineColor, shade, outline);
    }

    glEnable(3553);
    glEnable(2929);
    GL11.glDepthMask(true);
    GL11.glDisable(3042);
    glPopMatrix();
  }

  public static void renderBPS(final boolean b, final boolean b2) {}

  public static void renderEntity(
      Entity e, int type, double expand, double shift, int color, boolean damage) {
    if (e instanceof EntityLivingBase) {
      float partialTicks = ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;
      double x =
          e.lastTickPosX
              + (e.posX - e.lastTickPosX) * (double) partialTicks
              - mc.getRenderManager().viewerPosX;
      double y =
          e.lastTickPosY
              + (e.posY - e.lastTickPosY) * (double) partialTicks
              - mc.getRenderManager().viewerPosY;
      double z =
          e.lastTickPosZ
              + (e.posZ - e.lastTickPosZ) * (double) partialTicks
              - mc.getRenderManager().viewerPosZ;
      float d = (float) expand / 40.0F;
      if (e instanceof EntityPlayer && damage && ((EntityPlayer) e).hurtTime != 0) {
        color = Color.RED.getRGB();
      }

      GlStateManager.pushMatrix();
      if (type == 3) {
        GL11.glTranslated(x, y - 0.2D, z);
        GL11.glRotated((double) (-mc.getRenderManager().playerViewY), 0.0D, 1.0D, 0.0D);
        GlStateManager.disableDepth();
        GL11.glScalef(0.03F + d, 0.03F + d, 0.03F + d);
        int outline = Color.black.getRGB();
        net.minecraft.client.gui.Gui.drawRect(-20, -1, -26, 75, outline);
        net.minecraft.client.gui.Gui.drawRect(20, -1, 26, 75, outline);
        net.minecraft.client.gui.Gui.drawRect(-20, -1, 21, 5, outline);
        net.minecraft.client.gui.Gui.drawRect(-20, 70, 21, 75, outline);
        if (color != 0) {
          net.minecraft.client.gui.Gui.drawRect(-21, 0, -25, 74, color);
          net.minecraft.client.gui.Gui.drawRect(21, 0, 25, 74, color);
          net.minecraft.client.gui.Gui.drawRect(-21, 0, 24, 4, color);
          net.minecraft.client.gui.Gui.drawRect(-21, 71, 25, 74, color);
        } else {
          int st = ColorUtil.getChroma(2L, 0L);
          int en = ColorUtil.getChroma(2L, 1000L);
          dGR(-21, 0, -25, 74, st, en);
          dGR(21, 0, 25, 74, st, en);
          net.minecraft.client.gui.Gui.drawRect(-21, 0, 21, 4, en);
          net.minecraft.client.gui.Gui.drawRect(-21, 71, 21, 74, st);
        }

        GlStateManager.enableDepth();
      } else {
        int i;
        if (type == 4) {
          EntityLivingBase en = (EntityLivingBase) e;
          double health = en.getHealth() / en.getMaxHealth();
          int barHeight = (int) (74.0D * health);
          int healthColor =
              health < 0.3D
                  ? Color.red.getRGB()
                  : (health < 0.5D
                      ? Color.orange.getRGB()
                      : (health < 0.7D ? Color.yellow.getRGB() : Color.green.getRGB()));
          GL11.glTranslated(x, y - 0.2D, z);
          GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
          GlStateManager.disableDepth();
          GL11.glScalef(0.03F + d, 0.03F + d, 0.03F + d);
          i = (int) (21 + shift * 2);
          net.minecraft.client.gui.Gui.drawRect(i, -1, i + 4, 75, Color.black.getRGB());
          net.minecraft.client.gui.Gui.drawRect(
              i + 1, barHeight, i + 3, 74, Color.darkGray.getRGB());
          net.minecraft.client.gui.Gui.drawRect(i + 1, 0, i + 3, barHeight, healthColor);
          GlStateManager.enableDepth();
        } else if (type == 6) {
          drawCircle(x, y, z, 0.699999988079071D, 45, 1.5F, color, color == 0);
        } else {
          if (color == 0) {
            color = ColorUtil.getChroma(2L, 0L);
          }

          float a = (float) (color >> 24 & 255) / 255.0F;
          float r = (float) (color >> 16 & 255) / 255.0F;
          float g = (float) (color >> 8 & 255) / 255.0F;
          float b = (float) (color & 255) / 255.0F;
          AxisAlignedBB bbox =
              e.getEntityBoundingBox().expand(0.1D + expand, 0.1D + expand, 0.1D + expand);
          AxisAlignedBB axis =
              new AxisAlignedBB(
                  bbox.minX - e.posX + x,
                  bbox.minY - e.posY + y,
                  bbox.minZ - e.posZ + z,
                  bbox.maxX - e.posX + x,
                  bbox.maxY - e.posY + y,
                  bbox.maxZ - e.posZ + z);
          GL11.glBlendFunc(770, 771);
          glEnable(3042);
          GL11.glDisable(3553);
          GL11.glDisable(2929);
          GL11.glDepthMask(false);
          GL11.glLineWidth(2.0F);
          GL11.glColor4f(r, g, b, a);
          if (type == 1) {
            RenderGlobal.drawSelectionBoundingBox(axis);
          } else if (type == 2) {
            drawBoundingBox(axis, r, g, b);
          }
          glEnable(3553);
          glEnable(2929);
          GL11.glDepthMask(true);
          GL11.glDisable(3042);
        }
      }
      GlStateManager.popMatrix();
    }
  }

  public static void drawPolygon(
      final double n, final double n2, final double n3, final int n4, final int n5) {
    if (n4 < 3) {
      return;
    }
    final float n6 = (n5 >> 24 & 0xFF) / 255.0f;
    final float n7 = (n5 >> 16 & 0xFF) / 255.0f;
    final float n8 = (n5 >> 8 & 0xFF) / 255.0f;
    final float n9 = (n5 & 0xFF) / 255.0f;
    final Tessellator getInstance = Tessellator.getInstance();
    final WorldRenderer getWorldRenderer = getInstance.getWorldRenderer();
    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GL11.glColor4f(n7, n8, n9, n6);
    getWorldRenderer.begin(6, DefaultVertexFormats.POSITION);
    for (int i = 0; i < n4; ++i) {
      final double n10 = 6.283185307179586 * i / n4 + Math.toRadians(180.0);
      getWorldRenderer.pos(n + Math.sin(n10) * n3, n2 + Math.cos(n10) * n3, 0.0).endVertex();
    }
    getInstance.draw();
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
  }

  /**
   * Draws a 12-edge wireframe outline of an AABB in world space. Caller must set GL11.glLineWidth
   * and GL11.glColor before calling.
   */
  public static void drawOutlinedBox(
      AxisAlignedBB worldBox, double viewerX, double viewerY, double viewerZ) {
    AxisAlignedBB renderBox = worldBox.offset(-viewerX, -viewerY, -viewerZ);
    RenderGlobal.drawSelectionBoundingBox(renderBox);
  }

  public static void drawBoundingBox(AxisAlignedBB abb, float r, float g, float b) {
    drawBoundingBox(abb, r, g, b, 0.25f);
  }

  public static void drawBoundingBox(AxisAlignedBB abb, float r, float g, float b, float a) {
    Tessellator ts = Tessellator.getInstance();
    WorldRenderer vb = ts.getWorldRenderer();
    vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
    vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    ts.draw();
    vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
    vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    ts.draw();
    vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
    vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    ts.draw();
    vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
    vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    ts.draw();
    vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
    vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    ts.draw();
    vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
    vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
    vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
    ts.draw();
  }

  public static void renderBlockModel(IBlockState blockState, BlockPos blockPos, int color) {
    renderBlockModel(blockState, blockPos.getX(), blockPos.getY(), blockPos.getZ(), color);
  }

  public static void renderBlockModel(
      IBlockState blockState, double x, double y, double z, int color) {
    Minecraft mc = Minecraft.getMinecraft();
    BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
    IBakedModel model =
        dispatcher.getModelFromBlockState(blockState, mc.theWorld, new BlockPos(x, y, z));

    double xPos = x - mc.getRenderManager().viewerPosX;
    double yPos = y - mc.getRenderManager().viewerPosY;
    double zPos = z - mc.getRenderManager().viewerPosZ;

    float a = ((color >> 24) & 0xFF) / 255.0f;
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;

    GlStateManager.pushMatrix();
    GlStateManager.translate(xPos, yPos, zPos);

    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableTexture2D();
    GlStateManager.disableCull();
    GlStateManager.disableDepth();
    GlStateManager.depthMask(false);
    GlStateManager.color(r, g, b, a);

    renderModelColoredQuads(model, r, g, b, a);

    GlStateManager.depthMask(true);
    GlStateManager.enableDepth();
    GlStateManager.enableTexture2D();
    GlStateManager.enableCull();
    GlStateManager.disableBlend();
    GlStateManager.popMatrix();
  }

  private static void renderModelColoredQuads(
      IBakedModel model, float r, float g, float b, float a) {
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer wr = tessellator.getWorldRenderer();
    for (EnumFacing face : EnumFacing.values()) {
      for (BakedQuad quad : model.getFaceQuads(face)) {
        drawColoredQuad(wr, quad, r, g, b, a, tessellator);
      }
    }
    for (BakedQuad quad : model.getGeneralQuads()) {
      drawColoredQuad(wr, quad, r, g, b, a, tessellator);
    }
  }

  private static void drawColoredQuad(
      WorldRenderer wr,
      BakedQuad quad,
      float r,
      float g,
      float b,
      float a,
      Tessellator tessellator) {
    int[] vertexData = quad.getVertexData();
    final int vertexCount = 4;
    final int intsPerVertex = vertexData.length / vertexCount;

    wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
    for (int i = 0; i < vertexCount; i++) {
      int baseIndex = i * intsPerVertex;
      float vx = Float.intBitsToFloat(vertexData[baseIndex]);
      float vy = Float.intBitsToFloat(vertexData[baseIndex + 1]);
      float vz = Float.intBitsToFloat(vertexData[baseIndex + 2]);

      wr.pos(vx, vy, vz).color(r, g, b, a).endVertex();
    }
    tessellator.draw();
  }

  public static void drawTracerLine(Entity e, int color, float lineWidth, float partialTicks) {
    if (e == null || mc.getRenderManager() == null) {
      return;
    }

    Entity viewEntity = mc.getRenderViewEntity();
    if (viewEntity == null) {
      viewEntity = mc.thePlayer;
    }
    if (viewEntity == null) {
      return;
    }

    double targetX =
        e.lastTickPosX
            + (e.posX - e.lastTickPosX) * (double) partialTicks
            - mc.getRenderManager().viewerPosX;
    double targetY =
        e.lastTickPosY
            + (e.posY - e.lastTickPosY) * (double) partialTicks
            - mc.getRenderManager().viewerPosY
            + (double) e.getEyeHeight()
            + (e.isSneaking() ? -0.125D : 0.0D);
    double targetZ =
        e.lastTickPosZ
            + (e.posZ - e.lastTickPosZ) * (double) partialTicks
            - mc.getRenderManager().viewerPosZ;

    double startX = 0.0D;
    double startY = viewEntity.getEyeHeight();
    double startZ = 0.0D;
    if (viewEntity == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) {
      float yaw = viewEntity.rotationYaw;
      float pitch = viewEntity.rotationPitch;
      double dirX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
      double dirY = -Math.sin(Math.toRadians(pitch));
      double dirZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
      startX = dirX;
      startY += dirY;
      startZ = dirZ;
    }

    float a = (float) (color >> 24 & 255) / 255.0F;
    float r = (float) (color >> 16 & 255) / 255.0F;
    float g = (float) (color >> 8 & 255) / 255.0F;
    float b = (float) (color & 255) / 255.0F;

    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glDepthMask(false);
    GL11.glLineWidth(lineWidth);
    GL11.glColor4f(r, g, b, a);
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex3d(startX, startY, startZ);
    GL11.glVertex3d(targetX, targetY, targetZ);
    GL11.glEnd();
    GL11.glLineWidth(1.0F);
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    GL11.glDepthMask(true);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glPopMatrix();
  }

  public static void dGR(int left, int top, int right, int bottom, int startColor, int endColor) {
    int j;
    if (left < right) {
      j = left;
      left = right;
      right = j;
    }

    if (top < bottom) {
      j = top;
      top = bottom;
      bottom = j;
    }

    float f = (float) (startColor >> 24 & 255) / 255.0F;
    float f1 = (float) (startColor >> 16 & 255) / 255.0F;
    float f2 = (float) (startColor >> 8 & 255) / 255.0F;
    float f3 = (float) (startColor & 255) / 255.0F;
    float f4 = (float) (endColor >> 24 & 255) / 255.0F;
    float f5 = (float) (endColor >> 16 & 255) / 255.0F;
    float f6 = (float) (endColor >> 8 & 255) / 255.0F;
    float f7 = (float) (endColor & 255) / 255.0F;
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.disableAlpha();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.shadeModel(7425);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
    worldrenderer.pos((double) right, (double) top, 0.0D).color(f1, f2, f3, f).endVertex();
    worldrenderer.pos((double) left, (double) top, 0.0D).color(f1, f2, f3, f).endVertex();
    worldrenderer.pos((double) left, (double) bottom, 0.0D).color(f5, f6, f7, f4).endVertex();
    worldrenderer.pos((double) right, (double) bottom, 0.0D).color(f5, f6, f7, f4).endVertex();
    tessellator.draw();
    GlStateManager.shadeModel(7424);
    GlStateManager.disableBlend();
    GlStateManager.enableAlpha();
    GlStateManager.enableTexture2D();
  }

  public static void db(int w, int h, int r) {
    int c = r == -1 ? -1089466352 : r;
    net.minecraft.client.gui.Gui.drawRect(0, 0, w, h, c);
  }

  public static void drawColoredString(
      String text,
      char lineSplit,
      int x,
      int y,
      long s,
      long shift,
      boolean rect,
      FontRenderer fontRenderer) {
    int bX = x;
    int l = 0;
    long r = 0L;

    for (int i = 0; i < text.length(); ++i) {
      char c = text.charAt(i);
      if (c == lineSplit) {
        ++l;
        x = bX;
        y += fontRenderer.FONT_HEIGHT + 5;
        r = shift * (long) l;
      } else {
        fontRenderer.drawString(
            String.valueOf(c), (float) x, (float) y, ColorUtil.getChroma(s, r), rect);
        x += fontRenderer.getCharWidth(c);
        if (c != ' ') {
          r -= 90L;
        }
      }
    }
  }

  public static void drawCircle(
      double x,
      double y,
      double z,
      double radius,
      int sides,
      float lineWidth,
      int color,
      boolean chroma) {
    float a = (float) (color >> 24 & 255) / 255.0F;
    float r = (float) (color >> 16 & 255) / 255.0F;
    float g = (float) (color >> 8 & 255) / 255.0F;
    float b = (float) (color & 255) / 255.0F;
    mc.entityRenderer.disableLightmap();
    GL11.glDisable(3553);
    glEnable(3042);
    GL11.glBlendFunc(770, 771);
    GL11.glDisable(2929);
    glEnable(2848);
    GL11.glDepthMask(false);
    GL11.glLineWidth(lineWidth);
    if (!chroma) {
      GL11.glColor4f(r, g, b, a);
    }

    GL11.glBegin(1);
    long d = 0L;
    long ed = 15000L / (long) sides;
    long hed = ed / 2L;

    for (int i = 0; i < sides * 2; ++i) {
      if (chroma) {
        if (i % 2 != 0) {
          if (i == 47) {
            d = hed;
          }

          d += ed;
        }

        int c = ColorUtil.getChroma(2L, d);
        float r2 = (float) (c >> 16 & 255) / 255.0F;
        float g2 = (float) (c >> 8 & 255) / 255.0F;
        float b2 = (float) (c & 255) / 255.0F;
        GL11.glColor3f(r2, g2, b2);
      }

      double angle = 6.283185307179586D * (double) i / (double) sides + Math.toRadians(180.0D);
      GL11.glVertex3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
    }

    GL11.glEnd();
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    GL11.glDepthMask(true);
    GL11.glDisable(2848);
    glEnable(2929);
    GL11.glDisable(3042);
    glEnable(3553);
    mc.entityRenderer.enableLightmap();
  }

  public static void drawCaret(float x, float y, int color, double width, double length) {
    GL11.glPushMatrix();
    glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    RenderUtil.glColor(color);
    GL11.glLineWidth((float) width);
    float halfWidth = (float) (width / 2.0);
    float xOffset = halfWidth / 2.0f;
    float yOffset = halfWidth / 2.0f;
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex2d(x - xOffset, y + yOffset);
    GL11.glVertex2d(x + length - xOffset, y - length + yOffset);
    GL11.glVertex2d(x + length - xOffset, y - length + yOffset);
    GL11.glVertex2d(x + 2 * length - xOffset, y + yOffset);
    GL11.glEnd();
    glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    glPopMatrix();
  }

  public static void drawTriangle(
      double x, double y, double size, double widthDiv, double heightDiv, int color) {
    boolean blend = GL11.glIsEnabled(3042);
    glEnable(3042);
    GL11.glDisable(3553);
    GL11.glBlendFunc(770, 771);
    glEnable(2848);
    GL11.glPushMatrix();
    glColor(color);
    GL11.glBegin(7);
    GL11.glVertex2d(x, y);
    GL11.glVertex2d((x - size / widthDiv), (y + size));
    GL11.glVertex2d(x, (y + size / heightDiv));
    GL11.glVertex2d((x + size / widthDiv), (y + size));
    GL11.glVertex2d(x, y);
    GL11.glEnd();
    GL11.glColor4f(0.0f, 0.0f, 0.0f, 0.8f);
    GL11.glBegin(2);
    GL11.glVertex2d(x, y);
    GL11.glVertex2d((x - size / widthDiv), (y + size));
    GL11.glVertex2d(x, (y + size / heightDiv));
    GL11.glVertex2d((x + size / widthDiv), (y + size));
    GL11.glVertex2d(x, y);
    GL11.glEnd();
    glPopMatrix();
    glEnable(3553);
    if (!blend) {
      GL11.glDisable(3042);
    }
    GL11.glDisable(2848);
  }

  public static void glColor(final int n) {
    GL11.glColor4f(
        (float) (n >> 16 & 0xFF) / 255.0f,
        (float) (n >> 8 & 0xFF) / 255.0f,
        (float) (n & 0xFF) / 255.0f,
        (float) (n >> 24 & 0xFF) / 255.0f);
  }

  public static void glColor(final int n, final float alpha) {
    GL11.glColor4f(
        (float) (n >> 16 & 0xFF) / 255.0f,
        (float) (n >> 8 & 0xFF) / 255.0f,
        (float) (n & 0xFF) / 255.0f,
        alpha);
  }

  public static void drawRoundedGradientOutlinedRectangle(
      float x,
      float y,
      float x2,
      float y2,
      final float radius,
      final int n6,
      final int n7,
      final int n8) {
    x *= 2.0f;
    y *= 2.0f;
    x2 *= 2.0f;
    y2 *= 2.0f;
    GL11.glPushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glScaled(0.5, 0.5, 0.5);
    glEnable(3042);
    GL11.glDisable(3553);
    glEnable(2848);
    GL11.glBegin(9);
    glColor(n6);
    for (int i = 0; i <= 90; i += 3) {
      final double n9 = (double) (i * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n9) * radius * -1.0,
          (double) (y + radius) + Math.cos(n9) * radius * -1.0);
    }
    for (int j = 90; j <= 180; j += 3) {
      final double n10 = (double) (j * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n10) * radius * -1.0,
          (double) (y2 - radius) + Math.cos(n10) * radius * -1.0);
    }
    for (int k = 0; k <= 90; k += 3) {
      final double n11 = (double) (k * 0.017453292f);
      GL11.glVertex2d(
          (double) (x2 - radius) + Math.sin(n11) * radius,
          (double) (y2 - radius) + Math.cos(n11) * radius);
    }
    for (int l = 90; l <= 180; l += 3) {
      final double n12 = (double) (l * 0.017453292f);
      GL11.glVertex2d(
          (double) (x2 - radius) + Math.sin(n12) * radius,
          (double) (y + radius) + Math.cos(n12) * radius);
    }
    GL11.glEnd();
    GL11.glPushMatrix();
    GL11.glShadeModel(7425);
    GL11.glLineWidth(2.0f);
    GL11.glBegin(2);
    if (n7 != 0L) {
      glColor(n7);
    }
    for (int n13 = 0; n13 <= 90; n13 += 3) {
      final double n14 = (double) (n13 * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n14) * radius * -1.0,
          (double) (y + radius) + Math.cos(n14) * radius * -1.0);
    }
    for (int n15 = 90; n15 <= 180; n15 += 3) {
      final double n16 = (double) (n15 * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n16) * radius * -1.0,
          (double) (y2 - radius) + Math.cos(n16) * radius * -1.0);
    }
    if (n8 != 0) {
      glColor(n8);
    }
    for (int n17 = 0; n17 <= 90; n17 += 3) {
      final double n18 = (double) (n17 * 0.017453292f);
      GL11.glVertex2d(
          (double) (x2 - radius) + Math.sin(n18) * radius,
          (double) (y2 - radius) + Math.cos(n18) * radius);
    }
    for (int n19 = 90; n19 <= 180; n19 += 3) {
      final double n20 = (double) (n19 * 0.017453292f);
      GL11.glVertex2d(
          (double) (x2 - radius) + Math.sin(n20) * radius,
          (double) (y + radius) + Math.cos(n20) * radius);
    }
    GL11.glEnd();
    glPopMatrix();
    glEnable(3553);
    GL11.glDisable(3042);
    GL11.glDisable(2848);
    glEnable(3553);
    GL11.glPopAttrib();
    GL11.glPopMatrix();
    GL11.glLineWidth(1.0f);
    GL11.glShadeModel(7424);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  public static void draw2DPolygon(
      final double x, final double y, final double radius, final int sides, final int color) {
    if (sides < 3) {
      return;
    }
    final float a = (color >> 24 & 0xFF) / 255.0f;
    final float r = (color >> 16 & 0xFF) / 255.0f;
    final float g = (color >> 8 & 0xFF) / 255.0f;
    final float b = (color & 0xFF) / 255.0f;
    final Tessellator tessellator = Tessellator.getInstance();
    final WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glColor4f(r, g, b, a);
    final double rad180 = Math.toRadians(180.0);
    worldrenderer.begin(6, DefaultVertexFormats.POSITION);
    for (int i = 0; i < sides; ++i) {
      final double angle = 6.283185307179586 * i / sides + rad180;
      worldrenderer
          .pos(x + Math.sin(angle) * radius, y + Math.cos(angle) * radius, 0.0)
          .endVertex();
    }
    tessellator.draw();
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
  }

  public static Framebuffer createFrameBuffer(Framebuffer framebuffer) {
    return createFrameBuffer(framebuffer, false);
  }

  public static Framebuffer createFrameBuffer(Framebuffer framebuffer, boolean depth) {
    if (needsNewFramebuffer(framebuffer)) {
      if (framebuffer != null) {
        framebuffer.deleteFramebuffer();
      }
      return new Framebuffer(mc.displayWidth, mc.displayHeight, depth);
    }
    return framebuffer;
  }

  public static boolean needsNewFramebuffer(Framebuffer framebuffer) {
    return framebuffer == null
        || framebuffer.framebufferWidth != mc.displayWidth
        || framebuffer.framebufferHeight != mc.displayHeight;
  }

  public static void drawFramebufferFullscreen(Framebuffer framebuffer) {
    if (framebuffer == null) return;
    ScaledResolution sr = new ScaledResolution(mc);
    GlStateManager.bindTexture(framebuffer.framebufferTexture);
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glTexCoord2d(0.0, 1.0);
    GL11.glVertex2d(0.0, 0.0);
    GL11.glTexCoord2d(0.0, 0.0);
    GL11.glVertex2d(0.0, sr.getScaledHeight());
    GL11.glTexCoord2d(1.0, 0.0);
    GL11.glVertex2d(sr.getScaledWidth(), sr.getScaledHeight());
    GL11.glTexCoord2d(1.0, 1.0);
    GL11.glVertex2d(sr.getScaledWidth(), 0.0);
    GL11.glEnd();
  }

  public static void bindTexture(int texture) {
    glBindTexture(GL_TEXTURE_2D, texture);
  }

  public static void setAlphaLimit(float limit) {
    GlStateManager.enableAlpha();
    GlStateManager.alphaFunc(GL_GREATER, (float) (limit * .01));
  }

  public static Color interpolateColorC(Color color1, Color color2, float amount) {
    amount = Math.min(1, Math.max(0, amount));
    return new Color(
        interpolateInt(color1.getRed(), color2.getRed(), amount),
        interpolateInt(color1.getGreen(), color2.getGreen(), amount),
        interpolateInt(color1.getBlue(), color2.getBlue(), amount),
        interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
  }

  public static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
    return interpolate(oldValue, newValue, (float) interpolationValue).intValue();
  }

  public static Double interpolate(double oldValue, double newValue, double interpolationValue) {
    return (oldValue + (newValue - oldValue) * interpolationValue);
  }

  public static void resetColor() {
    GlStateManager.color(1, 1, 1, 1);
  }

  public static void startBlend() {
    GLUtil.startBlend();
  }

  public static void endBlend() {
    GLUtil.endBlend();
  }

  public static Vec3 convertTo2D(int scaleFactor, double x, double y, double z) {
    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
    GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
    GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

    boolean result =
        GLU.gluProject(
            (float) x, (float) y, (float) z, MODELVIEW, PROJECTION, VIEWPORT, SCREEN_COORDS);

    if (result) {
      return new Vec3(
          SCREEN_COORDS.get(0) / scaleFactor,
          (Display.getHeight() - SCREEN_COORDS.get(1)) / scaleFactor,
          SCREEN_COORDS.get(2));
    }

    return null;
  }

  public static ProjectionContext captureProjectionContext(
      ProjectionContext context, int scaleFactor) {
    if (context == null) {
      context = new ProjectionContext();
    }

    context.scaleFactor = scaleFactor;
    ((java.nio.Buffer) context.modelView).clear();
    ((java.nio.Buffer) context.projection).clear();
    ((java.nio.Buffer) context.viewport).clear();
    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, context.modelView);
    GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, context.projection);
    GL11.glGetInteger(GL11.GL_VIEWPORT, context.viewport);
    ((java.nio.Buffer) context.modelView).rewind();
    ((java.nio.Buffer) context.projection).rewind();
    ((java.nio.Buffer) context.viewport).rewind();
    return context;
  }

  public static boolean projectTo2D(
      ProjectionContext context, double x, double y, double z, double[] output) {
    if (context == null || output == null || output.length < 3) {
      return false;
    }

    ((java.nio.Buffer) context.screenCoords).clear();
    boolean result =
        GLU.gluProject(
            (float) x,
            (float) y,
            (float) z,
            context.modelView,
            context.projection,
            context.viewport,
            context.screenCoords);

    if (!result) {
      return false;
    }

    output[0] = context.screenCoords.get(0) / context.scaleFactor;
    output[1] = (Display.getHeight() - context.screenCoords.get(1)) / context.scaleFactor;
    output[2] = context.screenCoords.get(2);
    return true;
  }

  public static void drawRoundedRectangle(
      float x, float y, float x2, float y2, float radius, final int color) {
    if (x2 <= x) {
      return;
    }

    float width = x2 - x;

    if (width < 3) {
      radius = Math.min(radius, width / 2.0f);
    }

    x *= 2.0;
    y *= 2.0;
    x2 *= 2.0;
    y2 *= 2.0;
    GL11.glPushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glScaled(0.5, 0.5, 0.5);
    glEnable(3042);
    GL11.glDisable(3553);
    glEnable(2848);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glBegin(9);
    glColor(color);
    for (int i = 0; i <= 90; i += 3) {
      final double n7 = (double) (i * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n7) * radius * -1.0,
          (double) (y + radius) + Math.cos(n7) * radius * -1.0);
    }
    for (int j = 90; j <= 180; j += 3) {
      final double n8 = (double) (j * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n8) * radius * -1.0,
          (double) (y2 - radius) + Math.cos(n8) * radius * -1.0);
    }
    if (x2 - x >= 4.5) {
      for (int k = 0; k <= 90; k += 1) {
        final double n9 = (double) (k * 0.017453292f);
        GL11.glVertex2d(
            (double) (x2 - radius) + Math.sin(n9) * radius,
            (double) (y2 - radius) + Math.cos(n9) * radius);
      }
      for (int l = 90; l <= 180; l += 1) {
        final double n10 = (double) (l * 0.017453292f);
        GL11.glVertex2d(
            (double) (x2 - radius) + Math.sin(n10) * radius,
            (double) (y + radius) + Math.cos(n10) * radius);
      }
    }
    GL11.glEnd();
    glEnable(3553);
    GL11.glDisable(3042);
    glEnable(3553);
    GL11.glDisable(2848);
    GL11.glPopAttrib();
    GL11.glPopMatrix();
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
  }

  public static void drawRoundedRectangleOutline(
      float x, float y, float x2, float y2, float radius, float lineWidth, final int color) {
    if (x2 <= x) {
      return;
    }

    float width = x2 - x;

    if (width < 3) {
      radius = Math.min(radius, width / 2.0f);
    }

    x *= 2.0;
    y *= 2.0;
    x2 *= 2.0;
    y2 *= 2.0;
    GL11.glPushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glScaled(0.5, 0.5, 0.5);
    glEnable(3042);
    GL11.glDisable(3553);
    glEnable(2848);
    GL11.glLineWidth(lineWidth * 2.0f);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    glColor(color);
    for (int i = 0; i <= 90; i += 3) {
      final double n7 = (double) (i * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n7) * radius * -1.0,
          (double) (y + radius) + Math.cos(n7) * radius * -1.0);
    }
    for (int j = 90; j <= 180; j += 3) {
      final double n8 = (double) (j * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n8) * radius * -1.0,
          (double) (y2 - radius) + Math.cos(n8) * radius * -1.0);
    }
    if (x2 - x >= 4.5) {
      for (int k = 0; k <= 90; k += 1) {
        final double n9 = (double) (k * 0.017453292f);
        GL11.glVertex2d(
            (double) (x2 - radius) + Math.sin(n9) * radius,
            (double) (y2 - radius) + Math.cos(n9) * radius);
      }
      for (int l = 90; l <= 180; l += 1) {
        final double n10 = (double) (l * 0.017453292f);
        GL11.glVertex2d(
            (double) (x2 - radius) + Math.sin(n10) * radius,
            (double) (y + radius) + Math.cos(n10) * radius);
      }
    }
    GL11.glEnd();
    glEnable(3553);
    GL11.glDisable(3042);
    GL11.glDisable(2848);
    glEnable(3553);
    GL11.glPopAttrib();
    GL11.glPopMatrix();
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  public static void drawRectangleGL(float x, float y, float x2, float y2, final int color) {
    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_TEXTURE_2D);

    glColor(color);

    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2f(x, y);
    GL11.glVertex2f(x, y2);
    GL11.glVertex2f(x2, y2);
    GL11.glVertex2f(x2, y);
    GL11.glEnd();
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);

    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    GL11.glPopMatrix();
  }

  public static void drawRoundedGradientRect(
      float x,
      float y,
      float x2,
      float y2,
      float radius,
      final int n6,
      final int n7,
      final int n8,
      final int n9) {
    if (x2 <= x) {
      return;
    }

    float width = x2 - x;

    if (width < 3) {
      radius = Math.min(radius, width / 2.0f);
    }

    glEnable(3042);
    GL11.glDisable(3553);
    GL11.glBlendFunc(770, 771);
    glEnable(2848);
    GL11.glShadeModel(7425);
    GL11.glPushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glScaled(0.5, 0.5, 0.5);
    x *= 2.0;
    y *= 2.0;
    x2 *= 2.0;
    y2 *= 2.0;
    glEnable(3042);
    GL11.glDisable(3553);
    glColor(n6);
    glEnable(2848);
    GL11.glShadeModel(7425);
    GL11.glBegin(9);
    for (int i = 0; i <= 90; i += 3) {
      final double n10 = i * 0.017453292f;
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n10) * radius * -1.0,
          (double) (y + radius) + Math.cos(n10) * radius * -1.0);
    }
    glColor(n7);
    for (int j = 90; j <= 180; j += 3) {
      final double n11 = j * 0.017453292f;
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n11) * radius * -1.0,
          (double) (y2 - radius) + Math.cos(n11) * radius * -1.0);
    }
    if (x2 - x >= 4.5) {
      glColor(n8);
      for (int k = 0; k <= 90; k += 3) {
        final double n12 = k * 0.017453292f;
        GL11.glVertex2d(
            (double) (x2 - radius) + Math.sin(n12) * radius,
            (double) (y2 - radius) + Math.cos(n12) * radius);
      }
      glColor(n9);
      for (int l = 90; l <= 180; l += 3) {
        final double n13 = l * 0.017453292f;
        GL11.glVertex2d(
            (double) (x2 - radius) + Math.sin(n13) * radius,
            (double) (y + radius) + Math.cos(n13) * radius);
      }
    }
    GL11.glEnd();
    glEnable(3553);
    GL11.glDisable(3042);
    GL11.glDisable(2848);
    GL11.glDisable(3042);
    glEnable(3553);
    GL11.glPopAttrib();
    GL11.glPopMatrix();
    glEnable(3553);
    GL11.glDisable(3042);
    GL11.glDisable(2848);
    GL11.glShadeModel(7424);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  public static int setAlpha(int rgb, double alpha) {
    if (alpha < 0 || alpha > 1) {
      alpha = 0.5;
    }

    int red = (rgb >> 16) & 0xFF;
    int green = (rgb >> 8) & 0xFF;
    int blue = rgb & 0xFF;

    int alphaInt = (int) (alpha * 255);

    int rgba = (alphaInt << 24) | (red << 16) | (green << 8) | blue;

    return rgba;
  }

  public static void draw2DCircle(
      float centerX,
      float centerY,
      float radius,
      int segments,
      float lineWidth,
      float r,
      float g,
      float b,
      float a) {
    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_CULL_FACE);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glColor4f(r, g, b, a);
    GL11.glLineWidth(lineWidth);

    GL11.glBegin(GL11.GL_LINE_LOOP);
    for (int i = 0; i <= segments; i++) {
      double theta = 2 * Math.PI * i / segments;
      float x = (float) (radius * Math.cos(theta)) + centerX;
      float y = (float) (radius * Math.sin(theta)) + centerY;
      GL11.glVertex2f(x, y);
    }
    GL11.glEnd();

    GL11.glDisable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_CULL_FACE);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glColor4f(1, 1, 1, 1);
    GL11.glLineWidth(1);
    GL11.glPopMatrix();
  }

  public static void draw2DCircleArc(
      float centerX,
      float centerY,
      float radius,
      float startAngle,
      float endAngle,
      float lineWidth,
      int color) {
    float r = ((color >> 16) & 0xFF) / 255f;
    float g = ((color >> 8) & 0xFF) / 255f;
    float b = (color & 0xFF) / 255f;
    float a = ((color >> 24) & 0xFF) / 255f;

    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_CULL_FACE);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glColor4f(r, g, b, a);
    GL11.glLineWidth(lineWidth);

    GL11.glBegin(GL11.GL_LINE_STRIP);
    for (float angle = startAngle; angle <= endAngle; angle += 1) {
      double theta = Math.toRadians(angle + 180);
      float x = (float) (radius * Math.cos(theta)) + centerX;
      float y = (float) (radius * Math.sin(theta)) + centerY;
      GL11.glVertex2f(x, y);
    }
    GL11.glEnd();

    GL11.glDisable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_CULL_FACE);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glColor4f(1, 1, 1, 1);
    GL11.glLineWidth(1);
    GL11.glPopMatrix();
  }

  public static void drawHorizontalGradientRect(
      float left, float top, float right, float bottom, int leftColor, int rightColor) {
    float la = (leftColor >> 24 & 0xFF) / 255.0F;
    float lr = (leftColor >> 16 & 0xFF) / 255.0F;
    float lg = (leftColor >> 8 & 0xFF) / 255.0F;
    float lb = (leftColor & 0xFF) / 255.0F;
    float ra = (rightColor >> 24 & 0xFF) / 255.0F;
    float rr = (rightColor >> 16 & 0xFF) / 255.0F;
    float rg = (rightColor >> 8 & 0xFF) / 255.0F;
    float rb = (rightColor & 0xFF) / 255.0F;
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.disableAlpha();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.shadeModel(7425);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer wr = tessellator.getWorldRenderer();
    wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
    wr.pos(left, bottom, 0).color(lr, lg, lb, la).endVertex();
    wr.pos(right, bottom, 0).color(rr, rg, rb, ra).endVertex();
    wr.pos(right, top, 0).color(rr, rg, rb, ra).endVertex();
    wr.pos(left, top, 0).color(lr, lg, lb, la).endVertex();
    tessellator.draw();
    GlStateManager.shadeModel(7424);
    GlStateManager.disableBlend();
    GlStateManager.enableAlpha();
    GlStateManager.enableTexture2D();
  }

  public static void drawVerticalGradientRect(
      float left, float top, float right, float bottom, int topColor, int bottomColor) {
    float ta = (topColor >> 24 & 0xFF) / 255.0F;
    float tr = (topColor >> 16 & 0xFF) / 255.0F;
    float tg = (topColor >> 8 & 0xFF) / 255.0F;
    float tb = (topColor & 0xFF) / 255.0F;
    float ba = (bottomColor >> 24 & 0xFF) / 255.0F;
    float br = (bottomColor >> 16 & 0xFF) / 255.0F;
    float bg = (bottomColor >> 8 & 0xFF) / 255.0F;
    float bb = (bottomColor & 0xFF) / 255.0F;
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.disableAlpha();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.shadeModel(7425);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer wr = tessellator.getWorldRenderer();
    wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
    wr.pos(right, top, 0).color(tr, tg, tb, ta).endVertex();
    wr.pos(left, top, 0).color(tr, tg, tb, ta).endVertex();
    wr.pos(left, bottom, 0).color(br, bg, bb, ba).endVertex();
    wr.pos(right, bottom, 0).color(br, bg, bb, ba).endVertex();
    tessellator.draw();
    GlStateManager.shadeModel(7424);
    GlStateManager.disableBlend();
    GlStateManager.enableAlpha();
    GlStateManager.enableTexture2D();
  }

  public static void renderItemAndEffectIntoGui3D(ItemStack stack, int xPos, int yPos) {
    if (stack == null) return;

    GlStateManager.pushMatrix();
    prepareGuiItemRenderState();
    GlStateManager.depthMask(true);
    GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
    RenderHelper.enableStandardItemLighting();
    GlStateManager.pushMatrix();
    GlStateManager.scale(1.0f, 1.0f, -0.01f);
    mc.getRenderItem().zLevel = -150.0f;
    mc.getRenderItem().renderItemAndEffectIntoGUI(stack, xPos, yPos);
    mc.getRenderItem().zLevel = 0.0f;
    GlStateManager.popMatrix();
    RenderHelper.disableStandardItemLighting();
    prepareGuiTextureRenderState();
    GlStateManager.disableBlend();
    GlStateManager.popMatrix();
  }

  public static void renderItemAndEffectIntoGui2D(ItemStack stack, int xPos, int yPos) {
    if (stack == null) return;

    prepareGuiItemRenderState();
    mc.getRenderItem().zLevel = -150.0F;
    GlStateManager.enableDepth();
    RenderHelper.enableGUIStandardItemLighting();
    mc.getRenderItem().renderItemAndEffectIntoGUI(stack, xPos, yPos - 8);
    mc.getRenderItem().zLevel = 0.0F;
    GlStateManager.disableDepth();
    prepareGuiTextureRenderState();
    GlStateManager.disableBlend();
  }

  public static int getDurabilityColor(float ratio) {
    if (ratio > 0.6F) return 0x00FF00;
    if (ratio > 0.3F) return 0xFFFF00;
    return 0xFF0000;
  }

  public static void drawDurabilityBar(int xPos, int yPos, float durabilityRatio) {
    int barWidth = (int) (durabilityRatio * 13);
    int barColor = getDurabilityColor(durabilityRatio);

    GlStateManager.disableTexture2D();
    Tessellator tess = Tessellator.getInstance();
    WorldRenderer wr = tess.getWorldRenderer();

    wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
    wr.pos(xPos + 2, yPos + 15, 0).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
    wr.pos(xPos + 2, yPos + 16, 0).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
    wr.pos(xPos + 15, yPos + 16, 0).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
    wr.pos(xPos + 15, yPos + 15, 0).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
    tess.draw();

    float r = ((barColor >> 16) & 255) / 255.0F;
    float g = ((barColor >> 8) & 255) / 255.0F;
    float b = (barColor & 255) / 255.0F;
    wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
    wr.pos(xPos + 2, yPos + 15, 0).color(r, g, b, 1.0F).endVertex();
    wr.pos(xPos + 2, yPos + 16, 0).color(r, g, b, 1.0F).endVertex();
    wr.pos(xPos + 2 + barWidth, yPos + 16, 0).color(r, g, b, 1.0F).endVertex();
    wr.pos(xPos + 2 + barWidth, yPos + 15, 0).color(r, g, b, 1.0F).endVertex();
    tess.draw();

    GlStateManager.enableTexture2D();
  }

  public static int getEnchantColor(int level) {
    switch (level) {
      case 1:
        return 0xFFFFFF;
      case 2:
        return 0x55FFFF;
      case 3:
        return 0x00AAAA;
      case 4:
        return 0xAA00AA;
      case 5:
        return 0xFFAA00;
      case 10:
        return 0xFF55FF;
      default:
        return level > 5 ? 0xFF55FF : 0xFFFFFF;
    }
  }

  public static int drawEnchantWithColor(FontRenderer fr, String letter, int level, int x, int y) {
    int letterWidth = fr.drawStringWithShadow(letter, x, y, 0xFFFFFF);
    fr.drawStringWithShadow(String.valueOf(level), letterWidth, y, getEnchantColor(level));
    return letterWidth;
  }

  public static void prepareGuiTextureRenderState() {
    GlStateManager.disableLighting();
    GlStateManager.disableDepth();
    GlStateManager.depthMask(false);
    GlStateManager.enableTexture2D();
    GlStateManager.enableAlpha();
    GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(
        GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  public static void prepareGuiItemRenderState() {
    GlStateManager.disableLighting();
    GlStateManager.enableTexture2D();
    GlStateManager.enableAlpha();
    GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(
        GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    GlStateManager.enableDepth();
    GlStateManager.depthMask(true);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  public static String getEnchantmentAbbreviated(int id) {
    switch (id) {
      case 0:
        return "pt";
      case 1:
        return "frp";
      case 2:
        return "ff";
      case 3:
        return "blp";
      case 4:
        return "prp";
      case 5:
        return "thr";
      case 6:
        return "res";
      case 7:
        return "aa";
      case 16:
        return "sh";
      case 17:
        return "smt";
      case 18:
        return "ban";
      case 19:
        return "kb";
      case 20:
        return "fa";
      case 21:
        return "lot";
      case 32:
        return "eff";
      case 33:
        return "sil";
      case 34:
        return "ub";
      case 35:
        return "for";
      case 48:
        return "pow";
      case 49:
        return "pun";
      case 50:
        return "flm";
      case 51:
        return "inf";
      default:
        return null;
    }
  }

  public static ResourceLocation buildWhiteMaskedTexture(
      String resourcePath, String registryName, ResourceLocation fallback) {
    InputStream inputStream = null;
    try {
      if (resourcePath.startsWith("/assets/")) {
        String pathWithoutAssets = resourcePath.substring("/assets/".length());
        int slashIndex = pathWithoutAssets.indexOf('/');
        if (slashIndex != -1) {
          String domain = pathWithoutAssets.substring(0, slashIndex);
          String path = pathWithoutAssets.substring(slashIndex + 1);
          inputStream =
              mc.getResourceManager()
                  .getResource(new ResourceLocation(domain, path))
                  .getInputStream();
        }
      }
    } catch (Exception ignored) {
    }
    if (inputStream == null) {
      inputStream = miau.Miau.class.getResourceAsStream(resourcePath);
    }
    if (inputStream == null) return fallback;
    try (InputStream stream = inputStream) {
      BufferedImage src = ImageIO.read(stream);
      int w = src.getWidth(), h = src.getHeight();
      BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      for (int py = 0; py < h; py++) {
        for (int px = 0; px < w; px++) {
          int alpha = (src.getRGB(px, py) >>> 24) & 0xFF;
          if (alpha > 0) dst.setRGB(px, py, (alpha << 24) | 0x00FFFFFF);
        }
      }
      return mc.getTextureManager()
          .getDynamicTextureLocation(registryName, new DynamicTexture(dst));
    } catch (Exception e) {
      e.printStackTrace();
      return fallback;
    }
  }

  private static final java.util.Map<String, ResourceLocation> iconCache =
      new java.util.HashMap<>();

  /**
   * Returns a cached white-masked icon texture, loading it on first access. The resource path
   * should start with "/" (e.g. "/assets/keystrokesmod/textures/gui/close.png").
   */
  public static ResourceLocation getIcon(String resourcePath) {
    ResourceLocation cached = iconCache.get(resourcePath);
    if (cached != null) {
      return cached;
    }
    String registryName = "raven_icon_" + resourcePath.hashCode();
    ResourceLocation icon = buildWhiteMaskedTexture(resourcePath, registryName, null);
    if (icon != null) {
      iconCache.put(resourcePath, icon);
    }
    return icon;
  }

  /**
   * Draws a tinted icon texture at the given position with full GL state management. Saves and
   * restores depth/blend state automatically.
   */
  public static void scaleStart(float x, float y, float scale) {
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0);
    GlStateManager.scale(scale, scale, 1);
    GlStateManager.translate(-x, -y, 0);
  }

  public static void scaleEnd() {
    GlStateManager.popMatrix();
  }

  /** Scissor with scale factor (for animated GUIs). Matches Demise signature. */
  public static void scissor(float x, float y, float width, float height, float scale) {
    ScaledResolution sr = new ScaledResolution(mc);
    int scaleFactor = sr.getScaleFactor();

    float scaledX = (x - sr.getScaledWidth() / 2f) * scale + sr.getScaledWidth() / 2f;
    float scaledY = (y - sr.getScaledHeight() / 2f) * scale + sr.getScaledHeight() / 2f;
    float scaledW = width * scale;
    float scaledH = height * scale;

    GL11.glScissor(
        (int) (scaledX * scaleFactor),
        (int) ((sr.getScaledHeight() - (scaledY + scaledH)) * scaleFactor),
        (int) (scaledW * scaleFactor),
        (int) (scaledH * scaleFactor));
  }

  public static void drawIcon(ResourceLocation texture, float x, float y, int size, int argbColor) {
    if (texture == null) {
      return;
    }
    boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
    boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

    prepareGuiTextureRenderState();
    mc.getTextureManager().bindTexture(texture);
    float a = ((argbColor >>> 24) & 0xFF) / 255f;
    float r = ((argbColor >> 16) & 0xFF) / 255f;
    float g = ((argbColor >> 8) & 0xFF) / 255f;
    float b = (argbColor & 0xFF) / 255f;
    GlStateManager.color(r, g, b, a);

    GL11.glPushMatrix();
    GL11.glTranslatef(x, y, 0f);
    net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(
        0, 0, 0, 0, size, size, size, size);
    GL11.glPopMatrix();

    restoreGuiRenderState(depthEnabled, blendEnabled, depthMask);
  }

  public static void restoreGuiRenderState(
      boolean depthEnabled, boolean blendEnabled, boolean depthMask) {
    GlStateManager.color(1f, 1f, 1f, 1f);
    if (blendEnabled) {
      GlStateManager.enableBlend();
    } else {
      GlStateManager.disableBlend();
    }
    if (depthEnabled) {
      GlStateManager.enableDepth();
    } else {
      GlStateManager.disableDepth();
    }
    GlStateManager.depthMask(depthMask);
  }
}
