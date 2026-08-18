package miau.ui.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import me.ksyz.accountmanager.gui.GuiAccountManager;
import miau.ClientInfo;
import miau.management.MiauAPI;
import miau.ui.GuiUpdateClient;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.MenuBackground;
import miau.util.render.Themes;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class MiauMainMenu extends GuiScreen {

  private static boolean hasCheckedVersion = false;

  private static final int PARTICLE_COUNT = 55;
  private final float[] px = new float[PARTICLE_COUNT];
  private final float[] py = new float[PARTICLE_COUNT];
  private final float[] pSpeed = new float[PARTICLE_COUNT];
  private final float[] pSize = new float[PARTICLE_COUNT];
  private final float[] pAlpha = new float[PARTICLE_COUNT];
  private boolean particlesInit = false;
  private final Random rng = new Random();

  private float animProgress = 0.0f;

  private final float[] hoverAnim = new float[6];

  private long lastFrame = System.currentTimeMillis();

  private boolean bgMenuOpen = false;
  private int bgMenuX = 0;
  private int bgMenuY = 0;

  private Font fontLogo;
  private Font fontSubtitle;
  private Font fontBtn;
  private Font fontMeta;

  static Font loadRiseFont(String filename, float size) {
    try {
      InputStream is =
          MiauMainMenu.class
              .getClassLoader()
              .getResourceAsStream("assets/keystrokesmod/fonts/" + filename);
      if (is == null) return null;
      java.awt.Font awt =
          java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is).deriveFont(size);
      return new miau.util.font.impl.rise.FontRenderer(awt, true, true, false);
    } catch (Exception e) {
      return null;
    }
  }

  private void loadFonts() {
    fontLogo = FontRepository.getMinecraftFont();
    fontSubtitle = FontRepository.getMinecraftFont();
    fontBtn = FontRepository.getMinecraftFont();
    fontMeta = FontRepository.getMinecraftFont();
  }

  @Override
  public void initGui() {
    super.initGui();
    this.buttonList.clear();
    loadFonts();
    this.animProgress = 0.0f;

    if (!particlesInit) {
      for (int i = 0; i < PARTICLE_COUNT; i++) respawnParticle(i, true);
      particlesInit = true;
    }

    if (!hasCheckedVersion) {
      hasCheckedVersion = true;
      new Thread(
              () -> {
                try {
                  String resp = MiauAPI.getClientVersion();
                  JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                  if (json.has("status") && json.get("status").getAsString().equals("success")) {
                    String latest = json.get("version").getAsString();
                    String url = json.get("updateUrl").getAsString();
                    if (MiauAPI.isOutdated(ClientInfo.VERSION, latest)) {
                      Minecraft.getMinecraft()
                          .addScheduledTask(
                              () ->
                                  Minecraft.getMinecraft()
                                      .displayGuiScreen(
                                          new GuiUpdateClient(
                                              this, ClientInfo.VERSION, latest, url)));
                      return; // Don't show both update and noti at same time
                    }
                  }
                  
                  String notiResp = MiauAPI.getClientNotification();
                  JsonObject notiJson = new JsonParser().parse(notiResp).getAsJsonObject();
                  if (notiJson.has("status") && notiJson.get("status").getAsString().equals("success") && notiJson.has("hasNotification") && notiJson.get("hasNotification").getAsBoolean()) {
                      JsonObject noti = notiJson.get("notification").getAsJsonObject();
                      String title = noti.has("title") ? noti.get("title").getAsString() : "Notification";
                      String desc = noti.has("desc") ? noti.get("desc").getAsString() : "";
                      String btn1Text = noti.has("btn1Text") ? noti.get("btn1Text").getAsString() : "OK";
                      String btn1Link = noti.has("btn1Link") ? noti.get("btn1Link").getAsString() : "";
                      String btn2Text = noti.has("btn2Text") ? noti.get("btn2Text").getAsString() : "";
                      String btn2Link = noti.has("btn2Link") ? noti.get("btn2Link").getAsString() : "";
                      int durationDays = noti.has("durationDays") ? noti.get("durationDays").getAsInt() : 0;
                      long updatedAt = noti.has("updatedAt") ? noti.get("updatedAt").getAsLong() : 0;
                      
                      long now = System.currentTimeMillis();
                      if (durationDays <= 0 || updatedAt == 0 || (now - updatedAt) <= (durationDays * 24L * 60 * 60 * 1000L)) {
                          Minecraft.getMinecraft().addScheduledTask(() -> {
                              Minecraft.getMinecraft().displayGuiScreen(
                                  new miau.ui.GuiNotificationClient(this, title, desc, btn1Text, btn1Link, btn2Text, btn2Link));
                          });
                      }
                  }
                } catch (Exception ignored) {
                }
              })
          .start();
    }
  }

  private void respawnParticle(int i, boolean anywhere) {
    px[i] = rng.nextFloat() * Math.max(width, 854);
    py[i] = anywhere ? rng.nextFloat() * Math.max(height, 480) : Math.max(height, 480) + 5;
    pSpeed[i] = 0.15f + rng.nextFloat() * 0.45f;
    pSize[i] = 1.0f + rng.nextFloat() * 2.2f;
    pAlpha[i] = 0.2f + rng.nextFloat() * 0.45f;
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException {}

  @Override
  public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
    super.mouseClicked(mouseX, mouseY, button);

    if (button == 1) {
      bgMenuOpen = !bgMenuOpen;
      if (bgMenuOpen) {
        bgMenuX = mouseX;
        bgMenuY = mouseY;
      }
      return;
    }

    if (button == 0 && bgMenuOpen) {
      if (handleBackgroundMenuClick(mouseX, mouseY)) return;
      bgMenuOpen = false;
    }

    if (button != 0) return;

    if (isDiscordHover(mouseX, mouseY)) {
      try {
        java.awt.Desktop.getDesktop()
            .browse(new java.net.URI("https://discord.gg/4r9M52zRge"));
      } catch (Exception ignored) {
      }
      return;
    }

    int centerX = width / 2;
    int centerY = height / 2;

    int btnWidth = 160;
    int btnHeight = 22;
    int spacing = 5;
    int startY = centerY - 25;

    for (int i = 0; i < 3; i++) {
      int by = startY + i * (btnHeight + spacing);
      int bx = centerX - btnWidth / 2;
      if (mouseX >= bx && mouseX <= bx + btnWidth && mouseY >= by && mouseY <= by + btnHeight) {
        onButtonClick(i);
        return;
      }
    }

    int bottomY = startY + (btnHeight + spacing) * 3;
    int smallW = (btnWidth - (spacing * 2)) / 3;
    int startSmallX = centerX - btnWidth / 2;

    for (int i = 0; i < 3; i++) {
      int bx = startSmallX + i * (smallW + spacing);
      if (mouseX >= bx
          && mouseX <= bx + smallW
          && mouseY >= bottomY
          && mouseY <= bottomY + btnHeight) {
        onButtonClick(3 + i);
        return;
      }
    }
  }

  private void onButtonClick(int id) {
    miau.module.modules.render.HUD hud = null;
    try {
      if (miau.Miau.moduleManager != null) {
        hud =
            (miau.module.modules.render.HUD)
                miau.Miau.moduleManager.getModule(miau.module.modules.render.HUD.class);
      }
    } catch (Exception ignored) {
    }

    switch (id) {
      case 0:
        mc.displayGuiScreen(new GuiSelectWorld(this));
        break;
      case 1:
        mc.displayGuiScreen(new GuiMultiplayer(this));
        break;
      case 2:
        mc.displayGuiScreen(new GuiAccountManager(this));
        break;
      case 3:
        mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
        break;
      case 4:
        if (hud != null) {
          int current = hud.menuBackground.getValue();
          int next = (current + 1) % miau.util.render.MenuBackground.NAMES.length;
          hud.menuBackground.setValue(next);
        }
        break;
      case 5:
        mc.shutdown();
        break;
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    loadFonts();

    long now = System.currentTimeMillis();
    float dt = Math.min((now - lastFrame) / 1000f, 0.05f);
    lastFrame = now;

    animProgress = miau.util.math.MathUtil.lerp(animProgress, 1.0f, 0.08f * (dt * 60f));

    drawBackground(now);
    drawParticles(dt, now);
    drawHeaderBlock(now);
    drawButtonBlock(mouseX, mouseY, dt, now);
    drawFooter();
    drawDiscordButton(mouseX, mouseY);

    if (bgMenuOpen) drawBackgroundMenu(mouseX, mouseY);

    GlStateManager.color(1f, 1f, 1f, 1f);
  }

  private void drawBackground(long now) {
    int shaderIndex = 0;
    try {
      if (miau.Miau.moduleManager != null) {
        miau.module.modules.render.HUD hud =
            (miau.module.modules.render.HUD)
                miau.Miau.moduleManager.getModule(miau.module.modules.render.HUD.class);
        if (hud != null) {
          shaderIndex = hud.menuBackground.getValue();
        }
      }
    } catch (Exception ignored) {
    }

    miau.util.render.MenuBackground.draw(width, height, shaderIndex);
  }

  private miau.module.modules.render.HUD getHud() {
    try {
      if (miau.Miau.moduleManager != null) {
        return (miau.module.modules.render.HUD)
            miau.Miau.moduleManager.getModule(miau.module.modules.render.HUD.class);
      }
    } catch (Exception ignored) {}
    return null;
  }

  private int getMenuBackgroundValue() {
    miau.module.modules.render.HUD hud = getHud();
    if (hud != null) {
      try {
        return hud.menuBackground.getValue();
      } catch (Exception ignored) {}
    }
    return 0;
  }

  private int[] backgroundMenuRect() {
    String[] names = MenuBackground.NAMES;
    int rowH = 14, padX = 8, padY = 6;
    int maxW = 0;
    for (String n : names) maxW = Math.max(maxW, mc.fontRendererObj.getStringWidth(n));
    int boxW = maxW + padX * 2;
    int boxH = names.length * rowH + padY * 2;
    int bx = Math.min(bgMenuX, width - boxW - 2);
    int by = Math.min(bgMenuY, height - boxH - 2);
    if (bx < 0) bx = 0;
    if (by < 0) by = 0;
    return new int[] {bx, by, boxW, boxH, rowH, padY};
  }

  private void drawBackgroundMenu(int mouseX, int mouseY) {
    int[] r = backgroundMenuRect();
    int bx = r[0], by = r[1], boxW = r[2], boxH = r[3], rowH = r[4], padY = r[5];

    RoundedUtils.drawRound(bx, by, boxW, boxH, 4F, new Color(15, 15, 15, 240));
    RoundedUtils.drawRound(bx, by, boxW, boxH, 4F, new Color(255, 255, 255, 50));

    String[] names = MenuBackground.NAMES;
    int current = getMenuBackgroundValue();
    for (int i = 0; i < names.length; i++) {
      int ry = by + padY + i * rowH;
      boolean hovered =
          mouseX >= bx && mouseX <= bx + boxW && mouseY >= ry && mouseY < ry + rowH;
      if (i == current) {
        RoundedUtils.drawRound(bx + 2F, ry, boxW - 4F, rowH - 2F, 3F, new Color(59, 155, 240, 200));
        mc.fontRendererObj.drawStringWithShadow(names[i], bx + 8, ry + (rowH - 8) / 2f, 0xFFFFFFFF);
      } else {
        mc.fontRendererObj.drawStringWithShadow(
            names[i], bx + 8, ry + (rowH - 8) / 2f, hovered ? 0xFFCCCCCC : 0xFF999999);
      }
    }
  }

  private boolean handleBackgroundMenuClick(int mouseX, int mouseY) {
    int[] r = backgroundMenuRect();
    int bx = r[0], by = r[1], boxW = r[2], boxH = r[3], rowH = r[4], padY = r[5];
    if (mouseX < bx || mouseX > bx + boxW || mouseY < by || mouseY > by + boxH) return false;

    int row = (mouseY - by - padY) / rowH;
    String[] names = MenuBackground.NAMES;
    if (row >= 0 && row < names.length) {
      miau.module.modules.render.HUD hud = getHud();
      if (hud != null) hud.menuBackground.setValue(row);
    }
    bgMenuOpen = false;
    return true;
  }

  private void drawParticles(float dt, long now) {
    Themes theme = Themes.getCurrentTheme();
    Color c1 = theme.getFirstColor();
    Color c2 = theme.getSecondColor();

    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableTexture2D();
    GL11.glEnable(GL11.GL_POINT_SMOOTH);
    GL11.glBegin(GL11.GL_POINTS);

    for (int i = 0; i < PARTICLE_COUNT; i++) {
      py[i] -= pSpeed[i] * 18f * dt;
      px[i] += (float) Math.sin(now / 1800.0 + i * 1.7f) * 0.1f;
      if (py[i] < -4) respawnParticle(i, false);

      Color c = blendC(c1, c2, (float) i / PARTICLE_COUNT);
      GL11.glPointSize(pSize[i]);
      GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, pAlpha[i]);
      GL11.glVertex2f(px[i], py[i]);
    }

    GL11.glEnd();
    GL11.glDisable(GL11.GL_POINT_SMOOTH);
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.resetColor();
  }

  private void drawHeaderBlock(long now) {
    if (fontLogo == null || fontSubtitle == null) return;

    Themes theme = Themes.getCurrentTheme();

    float centerX = width / 2.0f;
    float centerY = height / 2.0f;

    float titleY = centerY - 85;
    float s = 1.0f + (1.0f - animProgress) * 0.2f;
    int alphaVal = (int) (255 * animProgress);

    int brightness = MenuBackground.getBrightness(getMenuBackgroundValue());
    boolean darkBackdrop = brightness >= 0 && brightness > 128;

    Color c1 = theme.getFirstColor();
    Color c2 = theme.getSecondColor();
    float fade = (float) ((Math.sin(now / 900.0) + 1.0) / 2.0);
    Color fadeColor = blendC(c1, c2, fade);
    if (darkBackdrop) {
      fadeColor =
          new Color(
              Math.max(0, fadeColor.getRed() - 80),
              Math.max(0, fadeColor.getGreen() - 80),
              Math.max(0, fadeColor.getBlue() - 80));
    }

    int color =
        new Color(fadeColor.getRed(), fadeColor.getGreen(), fadeColor.getBlue(), alphaVal).getRGB();
    Color subBase = darkBackdrop ? new Color(55, 55, 65) : new Color(220, 220, 220);
    int subColor =
        new Color(
                subBase.getRed(),
                subBase.getGreen(),
                subBase.getBlue(),
                Math.max(0, alphaVal - 60))
            .getRGB();
    int shadowColor = new Color(0, 0, 0, Math.min(alphaVal, 170)).getRGB();

    if (fontLogo instanceof miau.util.font.impl.minecraft.MinecraftFontRenderer) {
      GlStateManager.pushMatrix();
      float titleScale = s * 3.0f;
      GlStateManager.translate(centerX, titleY, 0);
      GlStateManager.scale(titleScale, titleScale, 1.0f);
      GlStateManager.translate(-centerX, -titleY, 0);
      fontLogo.drawCentered("Miau Minus", centerX + 1, titleY + 1, shadowColor);
      fontLogo.drawCentered("Miau Minus Client", centerX, titleY, color);
      GlStateManager.popMatrix();

      GlStateManager.pushMatrix();
      float subScale = s * 1.2f;
      float subY = titleY + 9.0f * 3.0f + 4.0f;
      GlStateManager.translate(centerX, subY, 0);
      GlStateManager.scale(subScale, subScale, 1.0f);
      GlStateManager.translate(-centerX, -subY, 0);
      String ver = "v" + ClientInfo.VERSION;
      fontSubtitle.drawCentered(ver, centerX + 1, subY + 1, shadowColor);
      fontSubtitle.drawCentered(ver, centerX, subY, subColor);
      GlStateManager.popMatrix();
    } else {
      GlStateManager.pushMatrix();
      GlStateManager.translate(centerX, titleY, 0);
      GlStateManager.scale(s, s, 1.0f);
      GlStateManager.translate(-centerX, -titleY, 0);
      fontLogo.drawCentered("Miau Minus Client", centerX + 1, titleY + 1, shadowColor);
      fontLogo.drawCentered("Miau Minus Client", centerX, titleY, color);
      String ver = "v" + ClientInfo.VERSION;
      fontSubtitle.drawCentered(ver, centerX + 1, titleY + fontLogo.height() + 5, shadowColor);
      fontSubtitle.drawCentered(ver, centerX, titleY + fontLogo.height() + 4, subColor);
      GlStateManager.popMatrix();
    }
  }

  private void drawButtonBlock(int mouseX, int mouseY, float dt, long now) {
    if (fontBtn == null) return;

    int centerX = width / 2;
    int centerY = height / 2;

    int btnWidth = 160;
    int btnHeight = 22;
    int spacing = 5;
    int startY = centerY - 25;

    drawButton(
        0,
        centerX - btnWidth / 2,
        startY,
        btnWidth,
        btnHeight,
        "Singleplayer",
        mouseX,
        mouseY,
        dt,
        0);

    drawButton(
        1,
        centerX - btnWidth / 2,
        startY + btnHeight + spacing,
        btnWidth,
        btnHeight,
        "Multiplayer",
        mouseX,
        mouseY,
        dt,
        1);

    drawButton(
        2,
        centerX - btnWidth / 2,
        startY + (btnHeight + spacing) * 2,
        btnWidth,
        btnHeight,
        "Alt Manager",
        mouseX,
        mouseY,
        dt,
        2);

    int bottomY = startY + (btnHeight + spacing) * 3;
    int smallW = (btnWidth - (spacing * 2)) / 3;
    int startSmallX = centerX - btnWidth / 2;

    drawButton(3, startSmallX, bottomY, smallW, btnHeight, "Settings", mouseX, mouseY, dt, 3);
    drawButton(
        4,
        startSmallX + smallW + spacing,
        bottomY,
        smallW,
        btnHeight,
        "Theme",
        mouseX,
        mouseY,
        dt,
        4);
    drawButton(
        5,
        startSmallX + (smallW + spacing) * 2,
        bottomY,
        smallW,
        btnHeight,
        "Exit",
        mouseX,
        mouseY,
        dt,
        5);
  }

  private void drawButton(
      int id,
      int bx,
      int by,
      int bw,
      int bh,
      String label,
      int mouseX,
      int mouseY,
      float dt,
      int count) {
    float btnAnim = Math.max(0.0f, Math.min(1.0f, (animProgress - (count * 0.03f)) * 2.5f));
    if (btnAnim <= 0.01f) return;

    boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
    float target = hov ? 1.0f : 0.0f;
    hoverAnim[id] = miau.util.math.MathUtil.lerp(hoverAnim[id], target, 0.25f * (dt * 60f));
    float h = hoverAnim[id];

    GlStateManager.pushMatrix();

    float cx = bx + bw / 2.0f;
    float cy = by + bh / 2.0f;

    GlStateManager.translate(cx, cy, 0);
    GlStateManager.scale(btnAnim, btnAnim, 1.0f);
    GlStateManager.translate(-cx, -cy, 0);
    GlStateManager.translate(0, (1.0f - btnAnim) * 5.0f, 0);

    Color colorBgNormal = new Color(20, 20, 20, 120);
    Color colorBgHover = new Color(40, 40, 45, 200);
    Color colorOutlineNormal = new Color(255, 255, 255, 60);
    Color colorOutlineHover = new Color(255, 255, 255, 180);

    Color bg = blendC(colorBgNormal, colorBgHover, h);
    Color outline = blendC(colorOutlineNormal, colorOutlineHover, h);
    Color textCol = blendC(new Color(200, 200, 200), Color.WHITE, h);

    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

    miau.util.render.RenderUtil.drawRoundedRectangle(bx, by, bx + bw, by + bh, 5.0f, bg.getRGB());
    miau.util.render.RenderUtil.drawRoundedRectangleOutline(
        bx, by, bx + bw, by + bh, 5.0f, 1.0f, outline.getRGB());

    float fontHeight = fontBtn.height();
    if (fontBtn instanceof miau.util.font.impl.minecraft.MinecraftFontRenderer) {
      fontHeight = 8.0f;
    }
    float fontY = by + (bh - fontHeight) / 2.0f;
    fontBtn.drawCentered(label, cx, fontY, textCol.getRGB());

    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.popMatrix();
  }

  private void drawFooter() {
    if (fontMeta == null) return;
    Gui.drawRect(0, height - 1, width, height, new Color(255, 255, 255, 10).getRGB());

    float footerY = height - fontMeta.height() - 5;

    String right = "Credit: [Tirumdz-d, ihatenocheat-bot, SPEEDBOY19 based on Project-Miau]";
    int rw = fontMeta.width(right);
    fontMeta.draw(right, width - rw - 5, footerY, 0xD0FFFFFF, false);
  }

  private boolean isDiscordHover(int mouseX, int mouseY) {
    float x = 10f, y = height - 32f, w = 132f, h = 20f;
    return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
  }

  private void drawDiscordButton(int mouseX, int mouseY) {
    float x = 10f;
    float y = height - 32f;
    float w = 132f;
    float h = 20f;
    boolean hover = isDiscordHover(mouseX, mouseY);
    RoundedUtils.drawRound(
        x, y, w, h, 4f, hover ? new Color(88, 101, 242, 230) : new Color(88, 101, 242, 150));
    drawCustomImage("miau/moduleimage/discord.png", x + 5f, y + 4f, 12f, 12f);
    Font fontDisc = FontRepository.getFont("Inter Bold", 11f);
    fontDisc.draw("Discord Community", x + 21f, y + 5f, Color.WHITE.getRGB(), false);
  }

  private void drawCustomImage(String path, float x, float y, float width, float height) {
    try {
      net.minecraft.util.ResourceLocation loc = new net.minecraft.util.ResourceLocation(path);
      mc.getTextureManager().bindTexture(loc);
      GL11.glPushMatrix();
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
      Gui.drawModalRectWithCustomSizedTexture(
          (int) x, (int) y, 0, 0, (int) width, (int) height, width, height);
      GL11.glDisable(GL11.GL_BLEND);
      GL11.glPopMatrix();
    } catch (Exception ignored) {
    }
  }

  private void drawGlow(Font font, String text, int x, int y, Color color, long now) {
    double pulse = Math.sin(now / 1400.0) * 0.5 + 0.5;
    int baseAlpha = (int) (18 + pulse * 18);
    for (int off = 4; off >= 1; off--) {
      int a = Math.max(1, baseAlpha / off);
      int gc = alpha(color, a).getRGB();
      font.draw(text, x - off, y, gc, false);
      font.draw(text, x + off, y, gc, false);
      font.draw(text, x, y - off, gc, false);
      font.draw(text, x, y + off, gc, false);
    }
  }

  private void drawBlob(float cx, float cy, float radius, Color color, int alpha) {
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableTexture2D();
    int steps = 20;
    for (int s = steps; s >= 0; s--) {
      float r = radius * ((float) s / steps);
      float a = (alpha / 255f) * (1f - (float) s / steps);
      GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, a);
      GL11.glBegin(GL11.GL_TRIANGLE_FAN);
      GL11.glVertex2f(cx, cy);
      for (int seg = 0; seg <= 52; seg++) {
        double ang = seg * (Math.PI * 2.0 / 52);
        GL11.glVertex2f((float) (cx + Math.cos(ang) * r), (float) (cy + Math.sin(ang) * r));
      }
      GL11.glEnd();
    }
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.resetColor();
  }

  private static Color blendC(Color a, Color b, float t) {
    t = Math.max(0f, Math.min(1f, t));
    return new Color(
        (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
        (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
        (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
        (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
  }

  private static Color alpha(Color c, int a) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a));
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
