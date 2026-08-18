package miau.ui.clickgui.faiths;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import miau.module.modules.render.ClickGUI;
import miau.util.math.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class FaithsCharacterRenderer {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static ResourceLocation characterTexture = null;
  private static ResourceLocation glowMaskTexture = null;
  private static Color hairGlowColor = null;
  private static String loadedCharName = null;
  private static float charAspect = 700F / 999F;
  private static int charRawHeight = 0;
  private static float slideProgress = 0F;

  private static final Map<String, ResourceLocation> characterTextureMap = new HashMap<>();
  private static final Map<String, ResourceLocation> glowMaskTextureMap = new HashMap<>();
  private static final Map<String, Color> characterGlowMap = new HashMap<>();
  private static final Map<String, Float> characterAspectMap = new HashMap<>();
  private static final Map<String, Integer> characterRawHeightMap = new HashMap<>();

  public static void resetAnimation() {
    slideProgress = 0F;
  }

  private static void scanAssetCharacters(List<String> list) {
    try {
      java.net.URL url = miau.Miau.class.getResource("/assets/keystrokesmod/textures/gui/faiths/");
      if (url != null) {
        if (url.getProtocol().equals("file")) {
          File folder = new File(url.toURI());
          File[] files =
              folder.listFiles(
                  (d, name) ->
                      name.toLowerCase().endsWith(".png")
                          || name.toLowerCase().endsWith(".jpg")
                          || name.toLowerCase().endsWith(".jpeg"));
          if (files != null) {
            for (File f : files) {
              String name = f.getName();
              int dot = name.lastIndexOf('.');
              if (dot > 0) {
                String charName = name.substring(0, dot).toLowerCase();
                if (!list.contains(charName)) {
                  list.add(charName);
                }
              }
            }
          }
        } else if (url.getProtocol().equals("jar")) {
          String path = url.getPath();
          int bang = path.indexOf("!");
          if (bang != -1) {
            String jarPath = path.substring(5, bang);
            try (java.util.jar.JarFile jar =
                new java.util.jar.JarFile(java.net.URLDecoder.decode(jarPath, "UTF-8"))) {
              java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
              String prefix = "assets/keystrokesmod/textures/gui/faiths/";
              while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(prefix) && !entry.isDirectory()) {
                  String sub = entryName.substring(prefix.length());
                  int dot = sub.lastIndexOf('.');
                  if (dot > 0) {
                    String charName = sub.substring(0, dot).toLowerCase();
                    if (!list.contains(charName)) {
                      list.add(charName);
                    }
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception ignored) {
    }
  }

  public static List<String> getAvailableCharacters() {
    List<String> list = new ArrayList<>();

    scanAssetCharacters(list);

    File dir = new File("./config/Miau/characters/");
    if (!dir.exists()) {
      dir.mkdirs();
    } else {
      File[] files =
          dir.listFiles(
              (d, name) ->
                  name.toLowerCase().endsWith(".png")
                      || name.toLowerCase().endsWith(".jpg")
                      || name.toLowerCase().endsWith(".jpeg"));
      if (files != null) {
        for (File f : files) {
          String name = f.getName();
          int dot = name.lastIndexOf('.');
          if (dot > 0) {
            String charName = name.substring(0, dot).toLowerCase();
            if (!list.contains(charName)) {
              list.add(charName);
            }
          }
        }
      }
    }
    if (list.isEmpty()) {
      list.add("character");
    }
    return list;
  }

  public static String[] getCharacterArray() {
    List<String> list = getAvailableCharacters();
    return list.toArray(new String[0]);
  }

  private static Color extractHairGlowColor(BufferedImage img) {
    int width = img.getWidth();
    int height = img.getHeight();
    int endY = (int) (height * 0.35);

    long totalR = 0, totalG = 0, totalB = 0;
    int count = 0;
    float maxVibrancy = -1;
    Color vibrantColor = null;

    for (int y = 0; y < endY; y++) {
      for (int x = 0; x < width; x++) {
        int argb = img.getRGB(x, y);
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < 180) continue;

        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float saturation = hsb[1];
        float brightness = hsb[2];

        if (brightness > 0.2f) {
          totalR += r;
          totalG += g;
          totalB += b;
          count++;

          float vibrancy = saturation * brightness;
          if (vibrancy > maxVibrancy) {
            maxVibrancy = vibrancy;
            vibrantColor = new Color(r, g, b);
          }
        }
      }
    }

    if (vibrantColor != null && maxVibrancy > 0.2f) {
      return vibrantColor;
    } else if (count > 0) {
      return new Color((int) (totalR / count), (int) (totalG / count), (int) (totalB / count));
    }
    return new Color(180, 140, 255);
  }

  private static void loadTexture(String charName) {
    String key = charName != null ? charName.toLowerCase() : "character";
    if (key.equalsIgnoreCase(loadedCharName)
        && characterTexture != null
        && glowMaskTexture != null) {
      return;
    }
    loadedCharName = key;
    if (characterTextureMap.containsKey(key)) {
      characterTexture = characterTextureMap.get(key);
      glowMaskTexture = glowMaskTextureMap.get(key);
      hairGlowColor = characterGlowMap.get(key);
      Float aspect = characterAspectMap.get(key);
      if (aspect != null) charAspect = aspect;
      Integer rh = characterRawHeightMap.get(key);
      if (rh != null) charRawHeight = rh;
      return;
    }

    BufferedImage src = null;

    File dir = new File("./config/Miau/characters/");
    if (dir.exists() && dir.isDirectory()) {
      File[] matches =
          dir.listFiles(
              (d, name) -> {
                String n = name.toLowerCase();
                return n.startsWith(loadedCharName + ".")
                    && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"));
              });
      if (matches != null && matches.length > 0) {
        try {
          src = ImageIO.read(matches[0]);
        } catch (Exception ignored) {
        }
      }
    }

    if (src == null) {
      String[] extensions = {".png", ".jpg", ".jpeg"};
      for (String ext : extensions) {
        String assetPath = "/assets/keystrokesmod/textures/gui/faiths/" + loadedCharName + ext;
        try (InputStream stream = miau.Miau.class.getResourceAsStream(assetPath)) {
          if (stream != null) {
            src = ImageIO.read(stream);
            if (src != null) break;
          }
        } catch (Exception ignored) {
        }
      }
    }

    if (src == null && !loadedCharName.equals("character")) {
      try (InputStream stream =
          miau.Miau.class.getResourceAsStream(
              "/assets/keystrokesmod/textures/gui/faiths/character.png")) {
        if (stream != null) {
          src = ImageIO.read(stream);
        }
      } catch (Exception ignored) {
      }
    }

    if (src == null) return;

    try {
      hairGlowColor = extractHairGlowColor(src);
      long time = System.currentTimeMillis();
      characterTexture =
          mc.getTextureManager()
              .getDynamicTextureLocation("faiths_char_" + time, new DynamicTexture(src));

      int w = src.getWidth(), h = src.getHeight();
      charRawHeight = h;
      if (h > 0) charAspect = (float) w / (float) h;
      BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      for (int py = 0; py < h; py++) {
        for (int px = 0; px < w; px++) {
          int alpha = (src.getRGB(px, py) >>> 24) & 0xFF;
          if (alpha > 0) mask.setRGB(px, py, (alpha << 24) | 0x00FFFFFF);
        }
      }
      glowMaskTexture =
          mc.getTextureManager()
              .getDynamicTextureLocation("faiths_char_glow_" + time, new DynamicTexture(mask));

      characterTextureMap.put(key, characterTexture);
      glowMaskTextureMap.put(key, glowMaskTexture);
      characterGlowMap.put(key, hairGlowColor);
      characterAspectMap.put(key, charAspect);
      characterRawHeightMap.put(key, charRawHeight);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void drawColoredTexture(
      float x, float y, float width, float height, float r, float g, float b, float a) {
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
    int red = (int) (r * 255F);
    int green = (int) (g * 255F);
    int blue = (int) (b * 255F);
    int alpha = (int) (a * 255F);
    worldrenderer
        .pos(x, y + height, 0.0D)
        .tex(0.0D, 1.0D)
        .color(red, green, blue, alpha)
        .endVertex();
    worldrenderer
        .pos(x + width, y + height, 0.0D)
        .tex(1.0D, 1.0D)
        .color(red, green, blue, alpha)
        .endVertex();
    worldrenderer
        .pos(x + width, y, 0.0D)
        .tex(1.0D, 0.0D)
        .color(red, green, blue, alpha)
        .endVertex();
    worldrenderer.pos(x, y, 0.0D).tex(0.0D, 0.0D).color(red, green, blue, alpha).endVertex();
    tessellator.draw();
  }

  public static void renderCharacter(float delta) {
    try {
      ClickGUI clickGuiMod = (ClickGUI) miau.Miau.moduleManager.getModule(ClickGUI.class);
      if (clickGuiMod != null && !clickGuiMod.showCharacter.getValue()) {
        return;
      }
    } catch (Exception ignored) {
    }

    String charName = "character";
    try {
      ClickGUI clickGuiMod = (ClickGUI) miau.Miau.moduleManager.getModule(ClickGUI.class);
      if (clickGuiMod != null && clickGuiMod.character != null) {
        charName = clickGuiMod.character.getModeString();
      }
    } catch (Exception ignored) {
    }

    loadTexture(charName);
    if (characterTexture == null || glowMaskTexture == null) return;

    slideProgress = MathUtil.lerp(slideProgress, 1.0F, 0.03F * delta);
    if (slideProgress > 0.999F) slideProgress = 1.0F;

    ScaledResolution sr = new ScaledResolution(mc);
    float charHeight =
        charAspect >= 0.78f
            ? Math.min(240F, sr.getScaledHeight() * 0.7F)
            : Math.min(320F, sr.getScaledHeight() * 0.85F);
    float charWidth = charHeight * charAspect;

    float targetX = sr.getScaledWidth() - charWidth - 10F;
    float startX = sr.getScaledWidth() + 50F;
    float charX = startX + (targetX - startX) * slideProgress;
    float charY = sr.getScaledHeight() - charHeight;

    Color glowColor = hairGlowColor;
    if (glowColor == null) {
      glowColor = new Color(180, 140, 255);
    }

    float r = glowColor.getRed() / 255.0F;
    float g = glowColor.getGreen() / 255.0F;
    float b = glowColor.getBlue() / 255.0F;

    GlStateManager.enableBlend();
    GlStateManager.enableTexture2D();
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    mc.getTextureManager().bindTexture(glowMaskTexture);
    GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);

    int totalSamples = 1 + 8 + 12 + 16 + 20 + 24;
    float intensity = (0.65F * slideProgress) / totalSamples;

    drawColoredTexture(
        charX, charY, charWidth, charHeight, r * intensity, g * intensity, b * intensity, 1.0F);

    float[][] rings = {
      {1.5F, 8},
      {3.5F, 12},
      {6.0F, 16},
      {9.0F, 20},
      {12.5F, 24}
    };
    for (float[] ring : rings) {
      float radius = ring[0];
      int count = (int) ring[1];
      for (int j = 0; j < count; j++) {
        double angle = 2 * Math.PI * j / count;
        float offX = (float) (Math.cos(angle) * radius);
        float offY = (float) (Math.sin(angle) * radius);
        drawColoredTexture(
            charX + offX,
            charY + offY,
            charWidth,
            charHeight,
            r * intensity,
            g * intensity,
            b * intensity,
            1.0F);
      }
    }

    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    mc.getTextureManager().bindTexture(characterTexture);
    drawColoredTexture(charX, charY, charWidth, charHeight, 1.0F, 1.0F, 1.0F, slideProgress);
    GlStateManager.resetColor();
  }
}
