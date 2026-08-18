package miau.util.render;

import static org.lwjgl.opengl.GL11.*;

import miau.mixin.IAccessorEntityRenderer;
import miau.mixin.IAccessorMinecraft;
import miau.mixin.IAccessorRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class ShapeUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static void drawFilledCircle(double x, double y, double radius, int color) {
    float a = (float) (color >> 24 & 255) / 255.0F;
    float r = (float) (color >> 16 & 255) / 255.0F;
    float g = (float) (color >> 8 & 255) / 255.0F;
    float b = (float) (color & 255) / 255.0F;
    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.blendFunc(770, 771);
    glColor4f(r, g, b, a);
    glBegin(GL_TRIANGLE_FAN);
    for (int i = 0; i <= 360; i++) {
      double angle = Math.toRadians(i);
      glVertex2d(x + Math.sin(angle) * radius, y + Math.cos(angle) * radius);
    }
    glEnd();
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.resetColor();
  }

  public static void drawRect(float x1, float y1, float x2, float y2, int color) {
    if (color == 0) {
      return;
    }
    boolean texture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
    boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
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
    ShapeUtil.drawRect(x1, y1, x2, y2, backgroundColor);
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
    boolean bl = mc.gameSettings.viewBobbing;
    mc.gameSettings.viewBobbing = false;
    ((IAccessorEntityRenderer) mc.entityRenderer)
        .callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().renderPartialTicks, 2);
    mc.gameSettings.viewBobbing = bl;
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

  public static void drawTriangle(
      double x, double y, double size, double widthDiv, double heightDiv, int color) {
    boolean blend = GL11.glIsEnabled(3042);
    glEnable(3042);
    GL11.glDisable(3553);
    GL11.glBlendFunc(770, 771);
    glEnable(2848);
    GL11.glPushMatrix();
    RenderUtil.setColor(color);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(7, DefaultVertexFormats.POSITION);
    worldrenderer.pos(x, y, 0.0D).endVertex();
    worldrenderer.pos((x - size / widthDiv), (y + size), 0.0D).endVertex();
    worldrenderer.pos(x, (y + size / heightDiv), 0.0D).endVertex();
    worldrenderer.pos((x + size / widthDiv), (y + size), 0.0D).endVertex();
    worldrenderer.pos(x, y, 0.0D).endVertex();
    tessellator.draw();
    GL11.glColor4f(0.0f, 0.0f, 0.0f, 0.8f);
    worldrenderer.begin(2, DefaultVertexFormats.POSITION);
    worldrenderer.pos(x, y, 0.0D).endVertex();
    worldrenderer.pos((x - size / widthDiv), (y + size), 0.0D).endVertex();
    worldrenderer.pos(x, (y + size / heightDiv), 0.0D).endVertex();
    worldrenderer.pos((x + size / widthDiv), (y + size), 0.0D).endVertex();
    worldrenderer.pos(x, y, 0.0D).endVertex();
    tessellator.draw();
    glPopMatrix();
    glEnable(3553);
    if (!blend) {
      GL11.glDisable(3042);
    }
    GL11.glDisable(2848);
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

    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(1, DefaultVertexFormats.POSITION);
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
      worldrenderer.pos(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius).endVertex();
    }

    tessellator.draw();
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    GL11.glDepthMask(true);
    GL11.glDisable(2848);
    glEnable(2929);
    GL11.glDisable(3042);
    glEnable(3553);
    mc.entityRenderer.enableLightmap();
  }

  public static void draw3DRect(float x1, float y1, float x2, float y2) {
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(9, DefaultVertexFormats.POSITION);
    worldrenderer.pos((double) x2, (double) y1, 0.0D).endVertex();
    worldrenderer.pos((double) x1, (double) y1, 0.0D).endVertex();
    worldrenderer.pos((double) x1, (double) y2, 0.0D).endVertex();
    worldrenderer.pos((double) x2, (double) y2, 0.0D).endVertex();
    tessellator.draw();
  }
}
