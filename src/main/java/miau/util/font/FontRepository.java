package miau.util.font;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import miau.util.font.impl.minecraft.MinecraftFontRenderer;
import miau.util.font.impl.rise.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

public final class FontRepository {

  private static final Map<String, Font> RENDERER_CACHE = new HashMap<>();
  private static final String FONT_PATH = "assets/keystrokesmod/fonts/";
  private static final String RESOURCE_PREFIX = "keystrokesmod:fonts/";

  // Built from the .ttf files in assets/keystrokesmod/fonts/

  public static final String[] FONT_NAMES = {
    "Minecraft",
    "brcobane-regular",
    "brcobane-medium",
    "brcobane-bold",
    "brcobane-semibold",
    "comfortaa-regular",
    "comfortaa-medium",
    "comfortaa-bold",
    "comfortaa-semibold",
    "geistsans-regular",
    "geistsans-medium",
    "geistsans-bold",
    "geistsans-semibold",
    "greycliffcf-regular",
    "greycliffcf-medium",
    "greycliffcf-bold",
    "greycliffcf-semibold",
    "inter-regular",
    "inter-medium",
    "inter-bold",
    "inter-semibold",
    "notosanscjkkr-regular",
    "manrope-regular",
    "manrope-medium",
    "manrope-bold",
    "manrope-semibold",
    "materialicons-regular",
    "materialicons-outlined",
    "materialsymbolsoutlined",
    "productsans-regular",
    "productsans-medium",
    "productsans-bold",
    "productsans-semibold",
    "rubik-regular",
    "rubik-bold",
    "sfuidisplay-regular",
    "sfuidisplay-medium",
    "sfuidisplay-bold",
    "sfuidisplay-semibold",
    "sourcesans3-regular",
    "sourcesans3-medium",
    "sourcesans3-bold",
    "sourcesans3-semibold",
    "tahoma-regular",
    "tahoma-bold",
    "ubuntusans-regular",
    "ubuntusans-medium",
    "ubuntusans-bold",
    "ubuntusans-semibold",
    "augustus"
  };

  private static int currentHudFace = 0;


  public static Font getFont(String name) {
    return getFont(name, 18f);
  }

  public static Font getFont(String name, float size) {
    String key = name + "@" + (int) size;
    Font cached = RENDERER_CACHE.get(key);
    if (cached != null) return cached;

    try {
      java.awt.Font awt = loadAwtFont(name, size);
      if (awt != null) {
        // Smooth (linear filtering) for icon fonts — required for proper rendering
        boolean smooth = name.toLowerCase().startsWith("material");
        Font renderer = new FontRenderer(awt, true, true, smooth);
        RENDERER_CACHE.put(key, renderer);
        return renderer;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    // Fallback to Minecraft font if loading fails — never crash
    return new MinecraftFontRenderer();
  }


  public static Font getHudFont(int size) {
    if (isMinecraftSelected()) return new MinecraftFontRenderer();
    String name = getHudFontName();
    if (name.isEmpty()) return new MinecraftFontRenderer();
    return getFont(name, (float) size);
  }

  public static Font getMinecraftFont() {
    return new MinecraftFontRenderer();
  }

  public static void setHudFace(int faceIndex) {
    if (faceIndex < 0 || faceIndex >= FONT_NAMES.length) return;
    currentHudFace = faceIndex;
    RENDERER_CACHE.clear(); // Clear cache so fonts reload with new face
  }

  public static int getHudFace() {
    return currentHudFace;
  }

  public static String getHudFontName() {
    if (currentHudFace == 0) return "";
    return FONT_NAMES[currentHudFace];
  }

  public static boolean isMinecraftSelected() {
    return currentHudFace == 0;
  }

  public static void clearCache() {
    RENDERER_CACHE.clear();
  }


  private static java.awt.Font loadAwtFont(String name, float size) throws Exception {
    String fileName = name.endsWith(".ttf") ? name : name + ".ttf";

    // Try production: Minecraft resource manager (works inside mod jar)
    InputStream is = null;
    try {
      ResourceLocation loc = new ResourceLocation(RESOURCE_PREFIX + fileName);
      is = Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream();
    } catch (Exception ignored) {
    }

    // Fallback dev: ClassLoader (works in IDE / dev env)
    if (is == null) {
      is = FontRepository.class.getClassLoader().getResourceAsStream(FONT_PATH + fileName);
    }

    if (is == null) return null;
    return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is).deriveFont(size);
  }

  private FontRepository() {}
}
