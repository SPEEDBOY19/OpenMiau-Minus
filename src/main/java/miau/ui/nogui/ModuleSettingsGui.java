package miau.ui.nogui;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import miau.Miau;
import miau.module.Module;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ItemListProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.client.KeyBindUtil;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ModuleSettingsGui extends GuiScreen {

  private static final Color ACCENT_A = new Color(33, 212, 253);
  private static final Color ACCENT_B = new Color(123, 108, 255);
  private static final Color BG_COLOR = new Color(5, 7, 13);
  private static final Color PANEL_BG = new Color(11, 14, 24, 246);
  private static final Color BORDER = new Color(35, 45, 64);
  private static final Color TEXT_MAIN = new Color(241, 245, 251);
  private static final Color TEXT_DIM = new Color(163, 174, 194);
  private static final Color TEXT_FAINT = new Color(94, 106, 128);
  private static final Color ROW_HOVER = new Color(255, 255, 255, 10);
  private static final Color SWITCH_OFF = new Color(38, 48, 71);
  private static final Color TRACK_BG = new Color(34, 42, 62);

  private static final float PAD = 14F;
  private static final float ROW_H = 30F;
  private static final float HEADER_H = 56F;
  private static final float TAB_H = 30F;

  private static final int T_SWITCH = 0;
  private static final int T_KEYBIND = 99;
  private static final int T_SLIDER = 1;
  private static final int T_MODE = 2;
  private static final int T_COLOR = 3;
  private static final int T_TEXT = 4;

  private final Module module;
  private final String[] tabs = {"Settings", "Bind", "Config"};
  private String tab = "Settings";

  private Font fontTitle;
  private Font fontBody;
  private Font fontValue;
  private Font fontSmall;
  private Font fontBig;

  private float scroll = 0F;
  private float targetScroll = 0F;
  private float maxScroll = 0F;
  private float openAnim = 0F;
  private long lastMS = 0L;
  private ScaledResolution sr;

  private boolean binding = false;
  private Property<?> dragging = null;
  private final Map<Property<?>, Float> knobs = new HashMap<Property<?>, Float>();
  private String status = "";

  private static class Row {
    int type;
    Property<?> prop;

    Row(int type, Property<?> prop) {
      this.type = type;
      this.prop = prop;
    }
  }

  public ModuleSettingsGui(Module module) {
    this.module = module;
    fontTitle = FontRepository.getFont("sfuidisplay-bold", 16F);
    fontBody = FontRepository.getFont("sfuidisplay-regular", 12F);
    fontValue = FontRepository.getFont("sfuidisplay-medium", 12F);
    fontSmall = FontRepository.getFont("sfuidisplay-medium", 9F);
    fontBig = FontRepository.getFont("sfuidisplay-bold", 26F);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }

  @Override
  public void initGui() {
    lastMS = System.currentTimeMillis();
  }

  private float sw() {
    return new ScaledResolution(mc).getScaledWidth();
  }

  private float sh() {
    return new ScaledResolution(mc).getScaledHeight();
  }

  private List<Row> buildRows() {
    List<Row> rows = new ArrayList<Row>();
    rows.add(new Row(T_SWITCH, null));
    rows.add(new Row(T_KEYBIND, null));
    for (Property<?> p : module.getValues()) {
      if (p instanceof DragProperty || p instanceof ItemListProperty) {
        continue;
      }
      if (!p.isVisible()) {
        continue;
      }
      int t = T_TEXT;
      if (p instanceof BooleanProperty) {
        t = T_SWITCH;
      } else if (p instanceof FloatProperty || p instanceof IntProperty || p instanceof PercentProperty) {
        t = T_SLIDER;
      } else if (p instanceof ModeProperty) {
        t = T_MODE;
      } else if (p instanceof ColorProperty) {
        t = T_COLOR;
      }
      rows.add(new Row(t, p));
    }
    return rows;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    super.drawScreen(mouseX, mouseY, partialTicks);
    long now = System.currentTimeMillis();
    float delta = Math.min((now - lastMS) / 50F, 1.5F);
    lastMS = now;
    sr = new ScaledResolution(mc);

    openAnim = expApproach(openAnim, 1F, delta, 6F);
    maxScroll = computeMaxScroll();
    scroll = expApproach(scroll, clamp(targetScroll, 0F, maxScroll), delta, 6F);

    float w = sw();
    float h = sh();
    float alpha = easeOut(openAnim);

    RoundedUtils.drawRound(0, 0, w, h, 0F, withAlpha(BG_COLOR, (int) (225 * alpha)));
    RoundedUtils.drawRound(-140, -160, 440, 440, 220F, new Color(33, 212, 253, (int) (7 * alpha)));
    RoundedUtils.drawRound(w - 300, h - 320, 430, 430, 215F, new Color(123, 108, 255, (int) (6 * alpha)));

    float winW = Math.min(340F, w - 60F);
    float winH = Math.min(430F, h - 80F);
    float wx = (w - winW) / 2F;
    float wy = (h - winH) / 2F;

    float anim = easeOut(openAnim);
    GL11.glPushMatrix();
    GL11.glTranslatef(w / 2F, h / 2F, 0F);
    GL11.glScalef(0.97F + 0.03F * anim, 0.97F + 0.03F * anim, 1F);
    GL11.glTranslatef(-w / 2F, -h / 2F, 0F);

    RoundedUtils.drawRound(wx, wy, winW, winH, 14F, PANEL_BG);
    RoundedUtils.drawRoundOutline(wx, wy, winW, winH, 14F, 1.5F, new Color(0, 0, 0, 0), withAlpha(BORDER, 255));
    float t = pulse();
    RoundedUtils.drawGradientHorizontal(wx, wy, winW, 3F, 1.5F, accent(t), accent(t + 0.35F));

    RoundedUtils.drawRound(wx + PAD, wy + 10F, 34F, 34F, 9F, new Color(255, 255, 255, 10));
    String initial = module.getName().isEmpty() ? "?" : module.getName().substring(0, 1);
    fontBig.drawWithShadow(initial, wx + PAD + (34F - fontBig.width(initial)) / 2F, wy + 10F + (34F - fontBig.height()) / 2F - 1F, accent(t).getRGB());
    fontTitle.drawWithShadow(module.getName(), wx + PAD + 42F, wy + 13F, TEXT_MAIN.getRGB());
    fontSmall.draw(module.getCategory(), wx + PAD + 42F, wy + 13F + fontTitle.height() + 3F, TEXT_FAINT.getRGB());

    float closeX = wx + winW - 40F;
    float closeY = wy + 12F;
    boolean closeHover = mouseX >= closeX && mouseX <= closeX + 26F && mouseY >= closeY && mouseY <= closeY + 26F;
    RoundedUtils.drawRound(closeX, closeY, 26F, 26F, 8F, closeHover ? new Color(226, 70, 90, 90) : new Color(255, 255, 255, 9));
    drawCross(closeX + 13F, closeY + 13F, 4.5F, 1.6F, new Color(255, 255, 255, closeHover ? 255 : 180));

    drawTabs(mouseX, mouseY, wx, wy, winW);

    float contentTop = wy + HEADER_H + TAB_H + 10F;
    float contentBottom = wy + winH - 12F;

    if (tab.equals("Settings")) {
      drawSettingsTab(mouseX, mouseY, wx, contentTop, winW, contentBottom);
    } else if (tab.equals("Bind")) {
      drawBindTab(mouseX, mouseY, wx, contentTop, winW, contentBottom);
    } else {
      drawConfigTab(mouseX, mouseY, wx, contentTop, winW, contentBottom);
    }

    GL11.glPopMatrix();

    if (binding) {
      drawBindingOverlay(w, h, winW, winH, wx, wy);
    }
  }

  private float computeMaxScroll() {
    List<Row> rows = buildRows();
    float totalH = rows.size() * ROW_H;
    float winH = Math.min(430F, sh() - 80F);
    float avail = winH - HEADER_H - TAB_H - 10F - 12F;
    return Math.max(0F, totalH - avail);
  }

  private void drawTabs(int mx, int my, float wx, float wy, float winW) {
    float tabY = wy + HEADER_H + 4F;
    float tx = wx + PAD;
    for (String name : tabs) {
      float tw = fontBody.width(name) + 26F;
      boolean active = name.equals(tab);
      boolean hovered = mx >= tx && mx <= tx + tw && my >= tabY && my <= tabY + TAB_H;
      Color bg = active ? withAlpha(ACCENT_A, 210) : (hovered ? new Color(33, 212, 253, 50) : new Color(255, 255, 255, 8));
      Color text = active ? new Color(4, 9, 17) : (hovered ? TEXT_DIM : TEXT_FAINT);
      RoundedUtils.drawRound(tx, tabY, tw, TAB_H, 8F, bg);
      fontBody.drawCentered(name, tx + tw / 2F, tabY + (TAB_H - fontBody.height()) / 2F + 0.5F, text.getRGB());
      tx += tw + 6F;
    }
  }

  private void drawSettingsTab(int mx, int my, float wx, float contentTop, float winW, float contentBottom) {
    List<Row> rows = buildRows();
    float innerH = contentBottom - contentTop;
    beginScissor(wx + PAD, contentTop, winW - PAD * 2F, innerH);
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      float rowY = contentTop + i * ROW_H - scroll;
      if (rowY + ROW_H < contentTop || rowY > contentBottom) {
        continue;
      }
      float rx = wx + PAD;
      float rw = winW - PAD * 2F;
      boolean rowHover = mx >= rx && mx <= rx + rw && my >= rowY && my <= rowY + ROW_H;
      if (rowHover) {
        RoundedUtils.drawRound(rx, rowY + 2F, rw, ROW_H - 4F, 8F, ROW_HOVER);
      }
      drawRow(row, rx, rowY, rw, ROW_H);
    }
    endScissor();

    if (maxScroll > 0.5F) {
      float barX = wx + winW - 4F;
      float thumbH = Math.max(18F, innerH * (innerH / (rows.size() * ROW_H)));
      float thumbY = contentTop + (innerH - thumbH) * (scroll / maxScroll);
      RoundedUtils.drawRound(barX, contentTop, 2.5F, innerH, 1.5F, new Color(255, 255, 255, 14));
      RoundedUtils.drawRound(barX, thumbY, 2.5F, thumbH, 1.5F, new Color(123, 108, 255, 170));
    }
  }

  private void drawRow(Row row, float rx, float rowY, float rw, float rh) {
    float rightX = rx + rw - 4F;
    float t = pulse();
    if (row.type == T_KEYBIND) {
      String name = "Keybind";
      fontBody.drawWithShadow(name, rx + 4F, rowY + (rh - fontBody.height()) / 2F, TEXT_DIM.getRGB());
      String key = module.getKey() != 0 ? KeyBindUtil.getKeyName(module.getKey()) : "None";
      float tw = fontValue.width(key) + 20F;
      float pillX = rightX - tw;
      float pillH = 18F;
      float pillY = rowY + (rh - pillH) / 2F;
      RoundedUtils.drawRound(pillX, pillY, tw, pillH, 9F, new Color(255, 255, 255, 12));
      fontValue.drawWithShadow(key, pillX + (tw - fontValue.width(key)) / 2F, pillY + (pillH - fontValue.height()) / 2F, TEXT_MAIN.getRGB());
      return;
    }
    if (row.prop == null) {
      String name = "Enabled";
      fontBody.drawWithShadow(name, rx + 4F, rowY + (rh - fontBody.height()) / 2F, TEXT_DIM.getRGB());
      drawSwitch(rx + rw - 38F, rowY + (rh - 16F) / 2F, 34F, 16F, 0F, module.isEnabled());
      return;
    }
    if (row.type == T_SWITCH) {
      boolean value = (Boolean) row.prop.getValue();
      String name = row.prop.getName();
      fontBody.drawWithShadow(name, rx + 4F, rowY + (rh - fontBody.height()) / 2F, TEXT_DIM.getRGB());
      float k = animateKnob(row.prop, value);
      drawSwitch(rx + rw - 38F, rowY + (rh - 16F) / 2F, 34F, 16F, k, value);
      return;
    }
    if (row.type == T_SLIDER) {
      float min = 0F;
      float max = 100F;
      float cur = 0F;
      if (row.prop instanceof FloatProperty) {
        FloatProperty fp = (FloatProperty) row.prop;
        min = fp.getMinimum();
        max = fp.getMaximum();
        cur = fp.getValue();
      } else if (row.prop instanceof IntProperty) {
        IntProperty ip = (IntProperty) row.prop;
        min = ip.getMinimum();
        max = ip.getMaximum();
        cur = ip.getValue();
      } else {
        PercentProperty pp = (PercentProperty) row.prop;
        min = pp.getMinimum();
        max = pp.getMaximum();
        cur = pp.getValue();
      }
      String display = stripCodes(row.prop.formatValue());
      float trackX = rx + 4F;
      float trackW = rw - 8F;
      float trackY = rowY + rh - 9F;
      float ratio = (max - min) <= 0.001F ? 0F : clamp((cur - min) / (max - min), 0F, 1F);
      RoundedUtils.drawRound(trackX, trackY, trackW, 3F, 1.5F, TRACK_BG);
      if (ratio > 0.01F) {
        RoundedUtils.drawGradientHorizontal(trackX, trackY, trackW * ratio, 3F, 1.5F, accent(t), accent(t + 0.35F));
      }
      float knobX = trackX + trackW * ratio;
      RoundedUtils.drawRound(knobX - 3F, trackY - 1.5F, 6F, 6F, 3F, withAlpha(TEXT_MAIN, 255));
      fontBody.drawWithShadow(row.prop.getName(), rx + 4F, rowY + 3F, TEXT_DIM.getRGB());
      String clippedVal = clipText(fontSmall, display, rw - 60F);
      fontSmall.drawWithShadow(clippedVal, rightX - fontSmall.width(clippedVal), rowY + 4F, TEXT_FAINT.getRGB());
      return;
    }
    if (row.type == T_MODE) {
      ModeProperty mp = (ModeProperty) row.prop;
      String mode = mp.getModeString();
      String name = row.prop.getName();
      fontBody.drawWithShadow(name, rx + 4F, rowY + (rh - fontBody.height()) / 2F, TEXT_DIM.getRGB());
      float tw = fontValue.width(mode) + 24F;
      float pillX = rightX - tw;
      float pillH = 18F;
      float pillY = rowY + (rh - pillH) / 2F;
      RoundedUtils.drawRound(pillX, pillY, tw, pillH, 9F, new Color(255, 255, 255, 12));
      fontValue.drawWithShadow(mode, pillX + (tw - fontValue.width(mode)) / 2F - 3F, pillY + (pillH - fontValue.height()) / 2F, TEXT_MAIN.getRGB());
      fontSmall.draw(">", pillX + tw - 10F, pillY + (pillH - fontSmall.height()) / 2F + 0.5F, TEXT_FAINT.getRGB());
      return;
    }
    if (row.type == T_COLOR) {
      String name = row.prop.getName();
      fontBody.drawWithShadow(name, rx + 4F, rowY + (rh - fontBody.height()) / 2F, TEXT_DIM.getRGB());
      int v = (Integer) ((ColorProperty) row.prop).getValue();
      Color c = withAlpha(new Color(v, true), 255);
      float swW = 16F;
      float swX = rightX - swW;
      float swY = rowY + (rh - swW) / 2F;
      RoundedUtils.drawRound(swX, swY, swW, swW, 5F, c);
      RoundedUtils.drawRoundOutline(swX, swY, swW, swW, 5F, 1F, new Color(0, 0, 0, 0), new Color(255, 255, 255, 55));
      return;
    }
    String name = row.prop.getName();
    fontBody.drawWithShadow(name, rx + 4F, rowY + (rh - fontBody.height()) / 2F, TEXT_DIM.getRGB());
    String val = clipText(fontValue, stripCodes(row.prop.formatValue()), rw - fontBody.width(name) - 24F);
    fontValue.draw(val, rightX - fontValue.width(val), rowY + (rh - fontValue.height()) / 2F, TEXT_FAINT.getRGB());
  }

  private void drawSwitch(float x, float y, float w, float h, float k, boolean on) {
    float t = pulse();
    if (on) {
      RoundedUtils.drawGradientHorizontal(x, y, w, h, h / 2F, accent(t), accent(t + 0.35F));
    } else {
      RoundedUtils.drawRound(x, y, w, h, h / 2F, SWITCH_OFF);
    }
    float knobX = x + 3F + k * (w - 16F);
    RoundedUtils.drawRound(knobX, y + 2.5F, 11F, 11F, 5.5F, on ? new Color(240, 247, 255) : new Color(120, 130, 152));
  }

  private float animateKnob(Property<?> prop, boolean on) {
    Float f = knobs.get(prop);
    float k = f == null ? 0F : f.floatValue();
    k = expApproach(k, on ? 1F : 0F, 0.3F, 4F);
    knobs.put(prop, k);
    return k;
  }

  private void drawBindTab(int mx, int my, float wx, float contentTop, float winW, float contentBottom) {
    float bx = wx + PAD;
    float bw = winW - PAD * 2F;
    float by = contentTop + 6F;
    float bh = 64F;
    boolean hovered = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    RoundedUtils.drawRound(bx, by, bw, bh, 10F, hovered ? new Color(33, 212, 253, 40) : new Color(255, 255, 255, 8));
    RoundedUtils.drawRoundOutline(bx, by, bw, bh, 10F, 1F, new Color(0, 0, 0, 0), hovered ? withAlpha(ACCENT_A, 180) : withAlpha(BORDER, 255));
    String key = module.getKey() != 0 ? KeyBindUtil.getKeyName(module.getKey()) : "None";
    fontValue.drawCentered(module.getName() + "  \u2192  " + key, bx + bw / 2F, by + 18F, TEXT_MAIN.getRGB());
    fontSmall.drawCentered(hovered ? "Release mouse, then press any key..." : "Click here to bind a key", bx + bw / 2F, by + 40F, TEXT_FAINT.getRGB());

    float clearW = 140F;
    float clearX = bx + bw / 2F - clearW / 2F;
    float clearY = by + bh + 14F;
    boolean clearHover = mx >= clearX && mx <= clearX + clearW && my >= clearY && my <= clearY + 30F;
    RoundedUtils.drawRound(clearX, clearY, clearW, 30F, 9F, clearHover ? new Color(226, 70, 90, 200) : new Color(255, 255, 255, 12));
    fontBody.drawCentered("Clear Keybind", clearX + clearW / 2F, clearY + 15F, TEXT_MAIN.getRGB());

    fontSmall.drawCentered("ESC cancels binding", wx + winW / 2F, contentBottom - 12F, TEXT_FAINT.getRGB());
  }

  private void drawConfigTab(int mx, int my, float wx, float contentTop, float winW, float contentBottom) {
    float bx = wx + PAD;
    float bw = winW - PAD * 2F;
    String label = "GLOBAL CONFIG";
    fontSmall.drawWithShadow(label, bx, contentTop, TEXT_FAINT.getRGB());
    String path = (mc.mcDataDir == null ? "?" : mc.mcDataDir.getAbsolutePath()) + "\\MiauConfig.json";
    String clippedPath = clipText(fontSmall, path, bw);
    fontSmall.draw(clippedPath, bx, contentTop + 12F, new Color(60, 70, 88).getRGB());

    String[][] buttons = {
      {"Save current settings", "Writes every module + property"},
      {"Load saved settings", "Restores from file"},
      {"Delete saved settings", "Removes the file"},
      {"Reset all modules", "Disables and clears binds"}
    };
    float y = contentTop + 34F;
    for (int i = 0; i < buttons.length; i++) {
      boolean hovered = mx >= bx && mx <= bx + bw && my >= y && my <= y + 36F;
      RoundedUtils.drawRound(bx, y, bw, 36F, 9F, hovered ? new Color(33, 212, 253, 50) : new Color(255, 255, 255, 8));
      fontBody.drawWithShadow(buttons[i][0], bx + 12F, y + 10F, (hovered ? TEXT_MAIN : TEXT_DIM).getRGB());
      fontSmall.draw(clipText(fontSmall, buttons[i][1], bw - 160F), bx + 12F, y + 22F, TEXT_FAINT.getRGB());
      y += 40F;
    }

    if (!status.isEmpty()) {
      fontSmall.drawCentered(status, wx + winW / 2F, contentBottom - 12F, ACCENT_A.getRGB());
    }
  }

  private void drawBindingOverlay(float w, float h, float winW, float winH, float wx, float wy) {
    RoundedUtils.drawRound(0, 0, w, h, 0F, new Color(4, 6, 12, 150));
    float cx = w / 2F;
    float cy = h / 2F;
    RoundedUtils.drawRound(cx - 220F, cy - 46F, 440F, 92F, 14F, new Color(11, 14, 24, 240));
    RoundedUtils.drawRoundOutline(cx - 220F, cy - 46F, 440F, 92F, 14F, 1F, new Color(0, 0, 0, 0), withAlpha(ACCENT_A, 180));
    RoundedUtils.drawGradientHorizontal(cx - 220F, cy - 46F, 440F, 2.5F, 1.5F, ACCENT_A, ACCENT_B);
    fontTitle.drawCentered("Press a key for " + module.getName(), cx, cy - 22F, TEXT_MAIN.getRGB());
    fontBody.drawCentered("Any key to bind  ·  ESC cancel  ·  DELETE clear", cx, cy + 20F, TEXT_DIM.getRGB());
  }

  @Override
  protected void mouseClicked(int mx, int my, int button) throws IOException {
    super.mouseClicked(mx, my, button);
    if (binding) {
      binding = false;
      return;
    }
    float w = sw();
    float h = sh();
    float winW = Math.min(340F, w - 60F);
    float winH = Math.min(430F, h - 80F);
    float wx = (w - winW) / 2F;
    float wy = (h - winH) / 2F;

    float closeX = wx + winW - 40F;
    float closeY = wy + 12F;
    if (mx >= closeX && mx <= closeX + 26F && my >= closeY && my <= closeY + 26F) {
      mc.displayGuiScreen(new NoguiGui());
      return;
    }

    float tabY = wy + HEADER_H + 4F;
    float tx = wx + PAD;
    for (String name : tabs) {
      float tw = fontBody.width(name) + 26F;
      if (mx >= tx && mx <= tx + tw && my >= tabY && my <= tabY + TAB_H) {
        tab = name;
        targetScroll = 0F;
        return;
      }
      tx += tw + 6F;
    }

    if (tab.equals("Settings")) {
      float contentTop = wy + HEADER_H + TAB_H + 10F;
      float contentBottom = wy + winH - 12F;
      List<Row> rows = buildRows();
      for (int i = 0; i < rows.size(); i++) {
        float rowY = contentTop + i * ROW_H - scroll;
        if (rowY + ROW_H < contentTop || rowY > contentBottom) {
          continue;
        }
        float rx = wx + PAD;
        float rw = winW - PAD * 2F;
        if (mx >= rx && mx <= rx + rw && my >= rowY && my <= rowY + ROW_H) {
          handleRowClick(rows.get(i), mx, rx, rw);
          return;
        }
      }
      return;
    }

    if (tab.equals("Bind")) {
      float bx = wx + PAD;
      float bw = winW - PAD * 2F;
      float by = wy + HEADER_H + TAB_H + 16F;
      if (mx >= bx && mx <= bx + bw && my >= by && my <= by + 64F) {
        binding = true;
        return;
      }
      float clearW = 140F;
      float clearX = bx + bw / 2F - clearW / 2F;
      float clearY = by + 64F + 14F;
      if (mx >= clearX && mx <= clearX + clearW && my >= clearY && my <= clearY + 30F) {
        module.setKey(0);
        return;
      }
      return;
    }

    if (tab.equals("Config")) {
      float bx = wx + PAD;
      float bw = winW - PAD * 2F;
      float y = wy + HEADER_H + TAB_H + 10F + 34F;
      for (int i = 0; i < 4; i++) {
        if (mx >= bx && mx <= bx + bw && my >= y && my <= y + 36F) {
          if (i == 0) {
            saveConfig();
          } else if (i == 1) {
            loadConfig();
          } else if (i == 2) {
            deleteConfig();
          } else {
            resetAll();
          }
          return;
        }
        y += 40F;
      }
      return;
    }
  }

  private void handleRowClick(Row row, int mx, float rx, float rw) {
    if (row.type == T_KEYBIND) {
      binding = true;
    } else if (row.type == T_SWITCH && row.prop == null) {
      module.toggle();
    } else if (row.type == T_SWITCH) {
      row.prop.setValue(!(Boolean) row.prop.getValue());
    } else if (row.type == T_SLIDER) {
      dragging = row.prop;
      updateSlider(row.prop, mx, rx, rw);
    } else if (row.type == T_MODE) {
      ((ModeProperty) row.prop).nextMode();
    }
  }

  private void updateSlider(Property<?> prop, float mx, float rx, float rw) {
    float min = 0F;
    float max = 100F;
    float trackX = rx + 4F;
    float trackW = rw - 8F;
    float ratio = clamp((mx - trackX) / trackW, 0F, 1F);
    if (prop instanceof FloatProperty) {
      FloatProperty fp = (FloatProperty) prop;
      min = fp.getMinimum();
      max = fp.getMaximum();
      fp.setValue(min + (max - min) * ratio);
    } else if (prop instanceof IntProperty) {
      IntProperty ip = (IntProperty) prop;
      min = ip.getMinimum();
      max = ip.getMaximum();
      ip.setValue(Math.round(min + (max - min) * ratio));
    } else {
      PercentProperty pp = (PercentProperty) prop;
      min = pp.getMinimum();
      max = pp.getMaximum();
      pp.setValue(Math.round(min + (max - min) * ratio));
    }
  }

  @Override
  public void updateScreen() {
    if (dragging != null) {
      if (!Mouse.isButtonDown(0)) {
        dragging = null;
      } else {
        float w = sw();
        float h = sh();
        float winW = Math.min(340F, w - 60F);
        float winH = Math.min(430F, h - 80F);
        float wx = (w - winW) / 2F;
        float wy = (h - winH) / 2F;
        float contentTop = wy + HEADER_H + TAB_H + 10F;
        int mx = (int) (Mouse.getX() * sw() / (float) mc.displayWidth);
        List<Row> rows = buildRows();
        for (int i = 0; i < rows.size(); i++) {
          if (rows.get(i).prop == dragging) {
            float rowY = contentTop + i * ROW_H - scroll;
            updateSlider(dragging, mx, wx + PAD, winW - PAD * 2F);
            break;
          }
        }
      }
    }
  }

  @Override
  protected void mouseReleased(int mx, int my, int state) {
    super.mouseReleased(mx, my, state);
    if (state == 0) {
      dragging = null;
    }
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getDWheel();
    if (wheel == 0 || !tab.equals("Settings")) {
      return;
    }
    int mx = (int) (Mouse.getX() * sw() / (float) mc.displayWidth);
    int my = (int) (sh() - Mouse.getY() * sh() / (float) mc.displayHeight);
    float w = sw();
    float h = sh();
    float wx = (w - Math.min(340F, w - 60F)) / 2F;
    float wy = (h - Math.min(430F, h - 80F)) / 2F;
    float contentTop = wy + HEADER_H + TAB_H + 10F;
    float contentBottom = wy + Math.min(430F, h - 80F) - 12F;
    if (mx >= wx + PAD && mx <= wx + Math.min(340F, w - 60F) - PAD && my >= contentTop && my <= contentBottom) {
      targetScroll -= wheel / 120F * 34F;
      targetScroll = clamp(targetScroll, 0F, maxScroll);
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) throws IOException {
    if (binding) {
      if (keyCode == 1) {
        binding = false;
      } else if (keyCode == 211 || keyCode == 14) {
        module.setKey(0);
        binding = false;
      } else {
        module.setKey(keyCode);
        binding = false;
      }
      return;
    }
    if (keyCode == 1) {
      mc.displayGuiScreen(new NoguiGui());
    }
  }

  private void saveConfig() {
    try {
      java.io.File file = new java.io.File(mc.mcDataDir, "MiauConfig.json");
      com.google.gson.JsonObject json = new com.google.gson.JsonObject();
      for (Module m : Miau.moduleManager.modules.values()) {
        com.google.gson.JsonObject modJson = new com.google.gson.JsonObject();
        modJson.addProperty("enabled", m.isEnabled());
        modJson.addProperty("key", m.getKey());
        com.google.gson.JsonArray propsJson = new com.google.gson.JsonArray();
        for (Property<?> p : m.getValues()) {
          com.google.gson.JsonObject propJson = new com.google.gson.JsonObject();
          propJson.addProperty("name", p.getName());
          String formatted = p.formatValue();
          if (formatted != null && !formatted.isEmpty()) {
            propJson.addProperty("value", formatted);
          }
          propsJson.add(propJson);
        }
        modJson.add("properties", propsJson);
        json.add(m.getName(), modJson);
      }
      java.io.FileWriter writer = new java.io.FileWriter(file);
      try {
        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
      } finally {
        writer.close();
      }
      status = "Settings saved";
    } catch (Exception e) {
      status = "Save failed";
      e.printStackTrace();
    }
  }

  private void loadConfig() {
    try {
      java.io.File file = new java.io.File(mc.mcDataDir, "MiauConfig.json");
      if (!file.exists()) {
        status = "No saved config found";
        return;
      }
      com.google.gson.JsonObject json =
          new com.google.gson.JsonParser().parse(java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8).stream().collect(java.util.stream.Collectors.joining("\n"))).getAsJsonObject();
      for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
        String modName = entry.getKey();
        com.google.gson.JsonObject modJson = entry.getValue().getAsJsonObject();
        Module m = Miau.moduleManager.getModule(modName);
        if (m == null) {
          continue;
        }
        if (modJson.has("enabled")) {
          m.setEnabled(modJson.get("enabled").getAsBoolean());
        }
        if (modJson.has("key")) {
          m.setKey(modJson.get("key").getAsInt());
        }
        if (modJson.has("properties")) {
          for (com.google.gson.JsonElement propElement : modJson.getAsJsonArray("properties")) {
            com.google.gson.JsonObject propJson = propElement.getAsJsonObject();
            String propName = propJson.get("name").getAsString();
            if (!propJson.has("value")) {
              continue;
            }
            String value = propJson.get("value").getAsString();
            for (Property<?> p : m.getValues()) {
              if (p.getName().equals(propName)) {
                p.parseString(value);
              }
            }
          }
        }
      }
      status = "Settings loaded";
    } catch (Exception e) {
      status = "Load failed";
      e.printStackTrace();
    }
  }

  private void deleteConfig() {
    try {
      java.io.File file = new java.io.File(mc.mcDataDir, "MiauConfig.json");
      if (file.exists()) {
        file.delete();
        status = "Config deleted";
      } else {
        status = "No config to delete";
      }
    } catch (Exception e) {
      status = "Delete failed";
      e.printStackTrace();
    }
  }

  private void resetAll() {
    for (Module m : Miau.moduleManager.modules.values()) {
      m.setEnabled(false);
      m.setKey(0);
      for (Property<?> p : m.getValues()) {
        p.parseString("");
      }
    }
    status = "All modules reset";
  }

  private void beginScissor(float x, float y, float w, float h) {
    float s = sr.getScaleFactor();
    int sx = (int) (x * s);
    int sy = (int) (mc.displayHeight - (y + h) * s);
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    GL11.glScissor(sx, sy, (int) (w * s), (int) (h * s));
  }

  private void endScissor() {
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
  }

  private void drawCross(float cx, float cy, float r, float thick, Color color) {
    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glColor4f(color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F, color.getAlpha() / 255F);
    GL11.glLineWidth(thick);
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex2f(cx - r, cy - r);
    GL11.glVertex2f(cx + r, cy + r);
    GL11.glVertex2f(cx - r, cy + r);
    GL11.glVertex2f(cx + r, cy - r);
    GL11.glEnd();
    GL11.glColor4f(1F, 1F, 1F, 1F);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glPopMatrix();
  }

  private static String stripCodes(String s) {
    if (s == null) {
      return "";
    }
    return s.replaceAll("[&\\u00A7].", "");
  }

  private Color accent(float t) {
    float x = (float) (0.5F + 0.5F * Math.sin(t * Math.PI));
    return interpolate(ACCENT_A, ACCENT_B, x);
  }

  private float pulse() {
    return (System.currentTimeMillis() % 7000L) / 7000F;
  }

  private String clipText(Font font, String text, float maxWidth) {
    if (font.width(text) <= maxWidth) {
      return text;
    }
    String clipped = text;
    while (!clipped.isEmpty() && font.width(clipped + "...") > maxWidth) {
      clipped = clipped.substring(0, clipped.length() - 1);
    }
    return clipped + "...";
  }

  private static float clamp(float v, float min, float max) {
    return v < min ? min : (v > max ? max : v);
  }

  private static float expApproach(float a, float b, float delta, float tau) {
    float f = 1F - (float) Math.exp(-delta / tau);
    return a + (b - a) * f;
  }

  private static float easeOut(float x) {
    return 1F - (float) Math.pow(1D - x, 3D);
  }

  private static Color withAlpha(Color c, int a) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
  }

  private static Color interpolate(Color a, Color b, float t) {
    float x = clamp(t, 0F, 1F);
    return new Color(
        (int) (a.getRed() + (b.getRed() - a.getRed()) * x),
        (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * x),
        (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * x),
        (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * x));
  }
}