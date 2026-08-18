package miau.util.demise;

import static org.lwjgl.opengl.GL11.*;

import java.awt.*;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public class RoundedUtils {

  public static DemiseShaderUtil roundedShader = new DemiseShaderUtil("roundedRect");
  public static DemiseShaderUtil roundedOutlineShader = new DemiseShaderUtil("roundRectOutline");
  private static final DemiseShaderUtil roundedTexturedShader =
      new DemiseShaderUtil("roundRectTexture");
  private static final DemiseShaderUtil roundedGradientShader =
      new DemiseShaderUtil("roundedRectGradient");

  public static void drawRound(
      float x, float y, float width, float height, float radius, Color color) {
    drawRound(x, y, width, height, radius, false, color);
  }

  public static void drawShaderRound(
      float x, float y, float width, float height, float radius, Color color) {
    drawShaderRound(x, y, width, height, radius, false, color);
  }

  public static void drawGradientHorizontal(
      float x, float y, float width, float height, float radius, Color left, Color right) {
    drawGradientRound(x, y, width, height, radius, left, left, right, right);
  }

  public static void drawGradientVertical(
      float x, float y, float width, float height, float radius, Color top, Color bottom) {
    drawGradientRound(x, y, width, height, radius, bottom, top, bottom, top);
  }

  public static void drawGradientCornerLR(
      float x, float y, float width, float height, float radius, Color topLeft, Color bottomRight) {
    Color mixedColor = RenderUtil.interpolateColorC(topLeft, bottomRight, .5f);
    drawGradientRound(x, y, width, height, radius, mixedColor, topLeft, bottomRight, mixedColor);
  }

  public static void drawGradientCornerRL(
      float x, float y, float width, float height, float radius, Color bottomLeft, Color topRight) {
    Color mixedColor = RenderUtil.interpolateColorC(topRight, bottomLeft, .5f);
    drawGradientRound(x, y, width, height, radius, bottomLeft, mixedColor, mixedColor, topRight);
  }

  public static void drawGradientRound(
      float x,
      float y,
      float width,
      float height,
      float radius,
      Color bottomLeft,
      Color topLeft,
      Color bottomRight,
      Color topRight) {
    RenderUtil.setAlphaLimit(0);
    RenderUtil.resetColor();
    RenderUtil.startBlend();
    roundedGradientShader.init();
    setupRoundedRectUniforms(x, y, width, height, radius, roundedGradientShader);
    // Top left
    roundedGradientShader.setUniformf(
        "color1",
        topLeft.getRed() / 255f,
        topLeft.getGreen() / 255f,
        topLeft.getBlue() / 255f,
        topLeft.getAlpha() / 255f);
    // Bottom Left
    roundedGradientShader.setUniformf(
        "color2",
        bottomLeft.getRed() / 255f,
        bottomLeft.getGreen() / 255f,
        bottomLeft.getBlue() / 255f,
        bottomLeft.getAlpha() / 255f);
    // Top Right
    roundedGradientShader.setUniformf(
        "color3",
        topRight.getRed() / 255f,
        topRight.getGreen() / 255f,
        topRight.getBlue() / 255f,
        topRight.getAlpha() / 255f);
    // Bottom Right
    roundedGradientShader.setUniformf(
        "color4",
        bottomRight.getRed() / 255f,
        bottomRight.getGreen() / 255f,
        bottomRight.getBlue() / 255f,
        bottomRight.getAlpha() / 255f);
    DemiseShaderUtil.drawQuads(x - 1, y - 1, width + 2, height + 2);
    roundedGradientShader.unload();
    RenderUtil.endBlend();
  }

  public static void drawRound(
      float x, float y, float width, float height, float radius, boolean blur, Color color) {
    RenderUtil.resetColor();
    RenderUtil.startBlend();
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    RenderUtil.setAlphaLimit(0);
    roundedShader.init();

    setupRoundedRectUniforms(x, y, width, height, radius, roundedShader);
    roundedShader.setUniformi("blur", blur ? 1 : 0);
    roundedShader.setUniformf(
        "color",
        color.getRed() / 255f,
        color.getGreen() / 255f,
        color.getBlue() / 255f,
        color.getAlpha() / 255f);

    DemiseShaderUtil.drawQuads(x - 1, y - 1, width + 2, height + 2);
    roundedShader.unload();
    RenderUtil.endBlend();
  }

  public static void drawShaderRound(
      float x, float y, float width, float height, float radius, boolean blur, Color color) {
    x += 0.2f;
    y += 0.2f;
    width -= 0.4f;
    height -= 0.4f;

    RenderUtil.resetColor();
    RenderUtil.startBlend();
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    RenderUtil.setAlphaLimit(0);
    roundedShader.init();

    setupRoundedRectUniforms(x, y, width, height, radius, roundedShader);
    roundedShader.setUniformi("blur", blur ? 1 : 0);
    roundedShader.setUniformf(
        "color",
        color.getRed() / 255f,
        color.getGreen() / 255f,
        color.getBlue() / 255f,
        color.getAlpha() / 255f);

    DemiseShaderUtil.drawQuads(x - 1, y - 1, width + 2, height + 2);
    roundedShader.unload();
    RenderUtil.endBlend();
  }

  public static void drawRoundOutline(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float outlineThickness,
      Color color,
      Color outlineColor) {
    RenderUtil.resetColor();
    RenderUtil.startBlend();
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    RenderUtil.setAlphaLimit(0);
    roundedOutlineShader.init();

    ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
    setupRoundedRectUniforms(x, y, width, height, radius, roundedOutlineShader);
    roundedOutlineShader.setUniformf("outlineThickness", outlineThickness * sr.getScaleFactor());
    roundedOutlineShader.setUniformf(
        "color",
        color.getRed() / 255f,
        color.getGreen() / 255f,
        color.getBlue() / 255f,
        color.getAlpha() / 255f);
    roundedOutlineShader.setUniformf(
        "outlineColor",
        outlineColor.getRed() / 255f,
        outlineColor.getGreen() / 255f,
        outlineColor.getBlue() / 255f,
        outlineColor.getAlpha() / 255f);

    DemiseShaderUtil.drawQuads(
        x - (2 + outlineThickness),
        y - (2 + outlineThickness),
        width + (4 + outlineThickness * 2),
        height + (4 + outlineThickness * 2));
    roundedOutlineShader.unload();
    RenderUtil.endBlend();
  }

  public static void drawRoundTextured(
      float x, float y, float width, float height, float radius, float alpha) {
    RenderUtil.resetColor();
    RenderUtil.setAlphaLimit(0);
    RenderUtil.startBlend();
    roundedTexturedShader.init();
    roundedTexturedShader.setUniformi("textureIn", 0);
    setupRoundedRectUniforms(x, y, width, height, radius, roundedTexturedShader);
    roundedTexturedShader.setUniformf("alpha", alpha);
    DemiseShaderUtil.drawQuads(x - 1, y - 1, width + 2, height + 2);
    roundedTexturedShader.unload();
    RenderUtil.endBlend();
  }

  private static void setupRoundedRectUniforms(
      float x,
      float y,
      float width,
      float height,
      float radius,
      DemiseShaderUtil roundedTexturedShader) {
    ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
    roundedTexturedShader.setUniformf(
        "location",
        x * sr.getScaleFactor(),
        (Minecraft.getMinecraft().displayHeight - (height * sr.getScaleFactor()))
            - (y * sr.getScaleFactor()));
    roundedTexturedShader.setUniformf(
        "rectSize", width * sr.getScaleFactor(), height * sr.getScaleFactor());
    roundedTexturedShader.setUniformf("radius", radius * sr.getScaleFactor());
  }
}
