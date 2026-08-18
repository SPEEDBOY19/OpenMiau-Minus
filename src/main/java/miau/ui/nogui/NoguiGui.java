package miau.ui.nogui;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import miau.Miau;
import miau.module.Module;
import miau.property.Property;
import miau.property.properties.DragProperty;
import miau.property.properties.ItemListProperty;
import miau.util.client.KeyBindUtil;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class NoguiGui extends GuiScreen {

  private static final Color ACCENT_A = new Color(33, 212, 253);
  private static final Color ACCENT_B = new Color(123, 108, 255);
  private static final Color BG_COLOR = new Color(5, 7, 13);
  private static final Color PANEL_BG = new Color(10, 13, 22, 242);
  private static final Color CARD_BG = new Color(14, 20, 30);
  private static final Color CARD_BG_HOVER = new Color(18, 26, 39);
  private static final Color CARD_ON_TOP = new Color(10, 32, 47, 200);
  private static final Color CARD_ON_BOTTOM = new Color(12, 16, 34, 200);
  private static final Color BORDER = new Color(35, 45, 64);
  private static final Color BORDER_HOVER = new Color(52, 67, 92);
  private static final Color TEXT_MAIN = new Color(241, 245, 251);
  private static final Color TEXT_DIM = new Color(163, 174, 194);
  private static final Color TEXT_FAINT = new Color(94, 106, 128);
  private static final Color ROW_HOVER = new Color(255, 255, 255, 10);
  private static final Color SWITCH_OFF = new Color(38, 48, 71);
  private static final Color TRACK_BG = new Color(34, 42, 62);

  private static final float MARGIN = 16F;
  private static final float BAR_Y = 10F;
  private static final float BAR_H = 40F;
  private static final float GRID_Y = 72F;
  private static final float CARD_W = 152F;
  private static final float CARD_H = 52F;
  private static final float GAP_X = 10F;
  private static final float GAP_Y = 12F;
  private static final float PAD = 14F;
  private static final float ROW_H = 30F;
  private static final float HEADER_H = 58F;
  private static final float LOGO_W = 58F;
  private static final float CONFIG_PANEL_W = 420F;
  private static final float SETTINGS_PANEL_W = 420F;
  private static final float SETTINGS_PANEL_H = 520F;

  private final Minecraft mc = Minecraft.getMinecraft();

  private Font fontLogo;
  private Font fontTab;
  private Font fontCard;
  private Font fontBody;
  private Font fontValue;
  private Font fontSmall;
  private Font fontTitle;
  private Font fontBig;

  private Map<String, List<Module>> categories;
  private List<String> catNames;
  private String activeCategory;
  private String search = "";
  private boolean searchFocused = false;
  private String configSearch = "";
  private boolean configSearchFocused = false;

  private List<Module> visibleModules = new ArrayList<Module>();
  private final Map<Module, Float> hover = new HashMap<Module, Float>();
  private final Map<Module, Float> entrance = new HashMap<Module, Float>();
  private final Map<Property<?>, Float> knobs = new HashMap<Property<?>, Float>();

  private float tabProgress = 0F;
  private int tabTarget = -1;
  private float scroll = 0F;
  private float targetScroll = 0F;
  private float openAnim = 0F;
  private long lastMS = 0L;
  private int cachedEnabled = 0;
  private boolean configVisible = false;
  private Module configModule = null;

  private Property<?> draggingSlider = null;
  private Module bindingModule = null;
  private ScaledResolution currentSR;

  private float[] configRect;
  private final List<Object[]> configRows = new ArrayList<Object[]>();

  private static class Card {
    Module module;
    float x;
    float y;
    float w;
    float h;

    Card(Module module, float x, float y, float w, float h) {
      this.module = module;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }
  }

  private List<Card> cards = new ArrayList<Card>();
  private float[] bindsRect;

  public NoguiGui() {
    categories = Miau.moduleManager.getModulesByCategory();
    catNames = new ArrayList<String>(categories.keySet());
    if (!catNames.isEmpty()) {
      activeCategory = catNames.get(0);
    }
    fontLogo = FontRepository.getFont("sfuidisplay-bold", 13F);
    fontTab = FontRepository.getFont("sfuidisplay-medium", 12F);
    fontCard = FontRepository.getFont("sfuidisplay-semibold", 13F);
    fontBody = FontRepository.getFont("sfuidisplay-regular", 12F);
    fontValue = FontRepository.getFont("sfuidisplay-medium", 12F);
    fontSmall = FontRepository.getFont("sfuidisplay-medium", 9F);
    fontTitle = FontRepository.getFont("sfuidisplay-bold", 17F);
    fontBig = FontRepository.getFont("sfuidisplay-bold", 30F);
    rebuildLayout();
  }

  private void rebuildLayout() {
    visibleModules.clear();
    if (search.isEmpty()) {
      List<Module> list = categories.get(activeCategory);
      if (list != null) {
        visibleModules.addAll(list);
      }
    } else {
      String q = search.toLowerCase();
      for (List<Module> list : categories.values()) {
        for (Module m : list) {
          if (m.getName().toLowerCase().contains(q)) {
            visibleModules.add(m);
          }
        }
      }
    }
    cards.clear();
    float w = scaledWidth();
    float gridW = w - MARGIN * 2F;
    int cols = Math.max(1, (int) Math.floor((gridW + GAP_X) / (CARD_W + GAP_X)));
    float totalW = cols * CARD_W + (cols - 1) * GAP_X;
    float startX = MARGIN + (gridW - totalW) / 2F;
    for (int i = 0; i < visibleModules.size(); i++) {
      int row = i / cols;
      int col = i % cols;
      float x = startX + col * (CARD_W + GAP_X);
      float y = GRID_Y + row * (CARD_H + GAP_Y);
      cards.add(new Card(visibleModules.get(i), x, y, CARD_W, CARD_H));
    }
    Map<Module, Float> nh = new HashMap<Module, Float>();
    for (Module m : visibleModules) {
      Float v = entrance.get(m);
      nh.put(m, v == null ? 0F : v.floatValue());
    }
    entrance.clear();
    entrance.putAll(nh);
  }

  private float scaledWidth() {
    return currentSR != null ? currentSR.getScaledWidth() : new ScaledResolution(mc).getScaledWidth();
  }

  private float scaledHeight() {
    return currentSR != null ? currentSR.getScaledHeight() : new ScaledResolution(mc).getScaledHeight();
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }

  @Override
  public void initGui() {
    lastMS = System.currentTimeMillis();
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    super.drawScreen(mouseX, mouseY, partialTicks);
    long now = System.currentTimeMillis();
    float delta = Math.min((now - lastMS) / 50F, 1.5F);
    lastMS = now;
    currentSR = new ScaledResolution(mc);

    cachedEnabled = countEnabled();

    openAnim = expApproach(openAnim, 1F, delta, 6F);
    if (tabTarget < 0) {
      for (int i = 0; i < catNames.size(); i++) {
        if (catNames.get(i).equals(activeCategory)) {
          tabTarget = i;
          break;
        }
      }
    }
    tabProgress = expApproach(tabProgress, (float) tabTarget, delta, 6F);
    scroll = expApproach(scroll, targetScroll, delta, 7F);

    float w = scaledWidth();
    float h = scaledHeight();
    float alpha = easeOut(openAnim);

    RoundedUtils.drawRound(0, 0, w, h, 0F, withAlpha(BG_COLOR, (int) (235 * alpha)));
    drawAmbientGlow(w, h, alpha);

    float scale = 1F - (1F - openAnim) * 0.03F;
    GL11.glPushMatrix();
    GL11.glTranslatef(w / 2F, h / 2F, 0F);
    GL11.glScalef(scale, scale, 1F);
    GL11.glTranslatef(-w / 2F, -h / 2F, 0F);

    drawTopBar(mouseX, mouseY, w, alpha);
    drawGrid(mouseX, mouseY, w, h, alpha);
    drawBindsPanel(mouseX, mouseY, w, h, alpha);
    drawFooter(mouseX, mouseY, w, h, alpha);

    GL11.glPopMatrix();

    if (bindingModule != null) {
      drawBindingOverlay(w, h);
    }
  }

  private void drawAmbientGlow(float w, float h, float alpha) {
    RoundedUtils.drawRound(-140, -160, 440, 440, 220F, new Color(33, 212, 253, (int) (7 * alpha)));
    RoundedUtils.drawRound(w - 300, h - 320, 430, 430, 215F, new Color(123, 108, 255, (int) (6 * alpha)));
  }

  private void drawTopBar(int mx, int my, float w, float alpha) {
    float x = MARGIN;
    float y = BAR_Y;
    float bw = w - MARGIN * 2F;

    RoundedUtils.drawRound(x, y, bw, BAR_H, 13F, PANEL_BG);
    RoundedUtils.drawRoundOutline(x, y, bw, BAR_H, 13F, 1F, new Color(0, 0, 0, 0), withAlpha(BORDER, (int) (255 * alpha)));

    float t = pulse();
    RoundedUtils.drawGradientHorizontal(x + 4F, y + 7F, LOGO_W, BAR_H - 14F, 8F, accent(t), accent(t + 0.35F));
    fontLogo.drawWithShadow("MIAU", x + 4F + (LOGO_W - fontLogo.width("MIAU")) / 2F, y + 7F + (BAR_H - 14F - fontLogo.height()) / 2F, new Color(4, 9, 17).getRGB());

    float tx = x + 4F + LOGO_W + 14F;
    float tabH = 26F;
    float tabY = y + (BAR_H - tabH) / 2F;
    float[] tabXs = new float[catNames.size()];
    float[] tabWs = new float[catNames.size()];
    for (int i = 0; i < catNames.size(); i++) {
      float tw = fontTab.width(catNames.get(i)) + 18F;
      tabXs[i] = tx;
      tabWs[i] = tw;
      tx += tw + 6F;
    }
    if (!catNames.isEmpty()) {
      float slide = clamp(tabProgress, 0F, catNames.size() - 1F);
      int left = (int) Math.floor(slide);
      int right = Math.min(left + 1, catNames.size() - 1);
      float ft = slide - left;
      float moverX = lerp(tabXs[left], tabXs[right], ft);
      float moverW = lerp(tabWs[left], tabWs[right], ft);
      if (catNames.size() > 1) {
        RoundedUtils.drawGradientHorizontal(moverX, tabY, moverW, tabH, 8F, accent(t), accent(t + 0.35F));
      }
    }
    for (int i = 0; i < catNames.size(); i++) {
      boolean active = i == tabTarget;
      boolean hovered = mx >= tabXs[i] && mx <= tabXs[i] + tabWs[i] && my >= tabY && my <= tabY + tabH;
      Color text = active ? new Color(4, 9, 17) : (hovered ? TEXT_DIM : TEXT_FAINT);
      fontTab.draw(catNames.get(i), tabXs[i] + (tabWs[i] - fontTab.width(catNames.get(i))) / 2F, tabY + (tabH - fontTab.height()) / 2F, text.getRGB());
    }

    float closeSize = 28F;
    float closeX = x + bw - closeSize - 6F;
    float closeY = y + (BAR_H - closeSize) / 2F;
    boolean closeHover = mx >= closeX && mx <= closeX + closeSize && my >= closeY && my <= closeY + closeSize;
    RoundedUtils.drawRound(closeX, closeY, closeSize, closeSize, 9F, closeHover ? new Color(226, 70, 90, 90) : new Color(255, 255, 255, 9));
    drawCross(closeX + closeSize / 2F, closeY + closeSize / 2F, 4.5F, 1.6F, new Color(255, 255, 255, closeHover ? 255 : 180));

    String stats = cachedEnabled + "/" + visibleModules.size();
    float statsW = fontValue.width(stats) + 22F;
    float statsX = closeX - statsW - 8F;
    RoundedUtils.drawRound(statsX, closeY, statsW, closeSize, 9F, new Color(255, 255, 255, 10));
    fontSmall.drawCentered(stats, statsX + statsW / 2F, closeY + (closeSize - fontSmall.height()) / 2F + 0.5F, TEXT_DIM.getRGB());

    float searchW = 132F;
    float searchX = statsX - searchW - 10F;
    boolean searchHover = mx >= searchX && mx <= searchX + searchW && my >= closeY && my <= closeY + closeSize;
    RoundedUtils.drawRound(searchX, closeY, searchW, closeSize, 9F, new Color(7, 10, 18, 210));
    RoundedUtils.drawRoundOutline(searchX, closeY, searchW, closeSize, 9F, 1F, new Color(0, 0, 0, 0), searchFocused ? accent(t) : withAlpha(BORDER, 255));
    drawMagnifier(searchX + 10F, closeY + closeSize / 2F, withAlpha(TEXT_FAINT, 255));
    String shown = searchFocused || !search.isEmpty() ? search : "Search...";
    int textColor = search.isEmpty() && !searchFocused ? TEXT_FAINT.getRGB() : TEXT_DIM.getRGB();
    String clipped = clipText(fontBody, shown, searchW - 32F);
    fontBody.draw(clipped, searchX + 21F, closeY + (closeSize - fontBody.height()) / 2F, textColor);
  }

  private void drawGrid(int mx, int my, float w, float h, float alpha) {
    float gridW = w - MARGIN * 2F;
    float bottom = h - 52F;
    float viewH = Math.max(40F, bottom - GRID_Y);
    int cols = Math.max(1, (int) Math.floor((gridW + GAP_X) / (CARD_W + GAP_X)));
    int rows = (int) Math.ceil(cards.size() / (float) cols);
    float totalH = rows * (CARD_H + GAP_Y) - GAP_Y;
    float maxScroll = Math.max(0F, totalH - viewH);
    targetScroll = clamp(targetScroll, 0F, maxScroll);

    boolean overGrid = mx >= MARGIN && mx <= MARGIN + gridW && my >= GRID_Y && my <= bottom;

    String label = search.isEmpty() ? activeCategory + "  ·  " + visibleModules.size() : "RESULTS  ·  " + visibleModules.size();
    fontSmall.drawWithShadow(label, MARGIN, GRID_Y - 14F, TEXT_FAINT.getRGB());

    beginScissor(MARGIN, GRID_Y, gridW, viewH);
    float fade = easeOut(openAnim);
    for (Card card : cards) {
      drawCard(card, mx, my, fade);
    }
    endScissor();

    if (maxScroll > 0.5F) {
      float barX = MARGIN + gridW - 3.5F;
      float thumbH = Math.max(22F, viewH * (viewH / totalH));
      float thumbY = GRID_Y + (viewH - thumbH) * (scroll / maxScroll);
      RoundedUtils.drawRound(barX, GRID_Y, 2.5F, viewH, 1.5F, new Color(255, 255, 255, 14));
      RoundedUtils.drawRound(barX, thumbY, 2.5F, thumbH, 1.5F, new Color(33, 212, 253, 170));
    }
  }

  private void drawCard(Card card, int mx, int my, float alpha) {
    float t = pulse();
    boolean hovering = mx >= card.x && mx <= card.x + card.w && my >= card.y - scroll && my <= card.y - scroll + card.h;

    Float hov = hover.get(card.module);
    float h = hov == null ? 0F : hov.floatValue();
    h = expApproach(h, hovering ? 1F : 0F, 0.3F, 5F);
    hover.put(card.module, h);

    Float ent = entrance.get(card.module);
    float e = ent == null ? 0F : ent.floatValue();
    e = expApproach(e, 1F, 0.3F, 4F);
    entrance.put(card.module, e);
    if (e < 0.01F) {
      return;
    }

    float x = card.x;
    float y = card.y - scroll + (1F - easeOut(e)) * 14F;
    float w = card.w + h * 2F;
    float hh = card.h + h * 2F;
    x -= h;
    y -= h;

    boolean enabled = card.module.isEnabled();
    Color base = enabled ? new Color(0, 0, 0, 0) : (h > 0.4F ? CARD_BG_HOVER : CARD_BG);

    if (enabled) {
      RoundedUtils.drawRound(x - 1F, y - 1F, w + 2F, hh + 2F, 11F, new Color(33, 212, 253, (int) (16 + 18 * h)));
      RoundedUtils.drawGradientVertical(x, y, w, hh, 10F, CARD_ON_TOP, CARD_ON_BOTTOM);
      RoundedUtils.drawRoundOutline(x, y, w, hh, 10F, 1.4F, new Color(0, 0, 0, 0), accent(t));
      RoundedUtils.drawGradientVertical(x, y + 6F, 2.6F, hh - 12F, 1.3F, accent(t), accent(t + 0.35F));
    } else {
      RoundedUtils.drawRound(x, y, w, hh, 10F, base);
      RoundedUtils.drawRoundOutline(x, y, w, hh, 10F, 1F, new Color(0, 0, 0, 0), h > 0.4F ? withAlpha(accent(t), 70) : BORDER);
    }

    int nameColor = enabled ? TEXT_MAIN.getRGB() : (hovering ? TEXT_MAIN.getRGB() : new Color(198, 207, 222).getRGB());
    fontCard.drawWithShadow(card.module.getName(), x + 10F, y + 9F, nameColor);

    float dotY = y + 24F;
    RoundedUtils.drawRound(x + 10F, dotY + 4F, 3F, 3F, 1.5F, enabled ? accent(t) : BORDER_HOVER);
    String cat = card.module.getCategory();
    fontSmall.draw(cat == null ? "" : clipText(fontSmall, cat, card.w - 70F), x + 17F, dotY, TEXT_FAINT.getRGB());

    String key = card.module.getKey() != 0 ? KeyBindUtil.getKeyName(card.module.getKey()) : "";
    if (!key.isEmpty()) {
      fontSmall.drawWithShadow(clipText(fontSmall, key, 40F), x + w - 10F - fontSmall.width(clipText(fontSmall, key, 40F)), dotY, TEXT_FAINT.getRGB());
    }

    if (hovering && !enabled) {
      float cy = y + 10F;
      for (int i = 0; i < 3; i++) {
        RoundedUtils.drawRound(x + w - 34F + i * 7F, cy + 9F, 3F, 3F, 1.5F, withAlpha(accent(t), 160));
      }
    }

    float pillW = 36F;
    float pillH = 14F;
    float pillY = y + 6F;
    float pillX = x + w - pillW - 8F;
    if (enabled) {
      RoundedUtils.drawGradientHorizontal(pillX, pillY, pillW, pillH, 7F, accent(t), accent(t + 0.35F));
      fontSmall.drawWithShadow("ON", pillX + (pillW - fontSmall.width("ON")) / 2F, pillY + (pillH - fontSmall.height()) / 2F + 0.5F, new Color(4, 9, 17).getRGB());
    } else if (h > 0.4F) {
      RoundedUtils.drawRound(pillX, pillY, pillW, pillH, 7F, new Color(255, 255, 255, 10));
      fontSmall.draw("OFF", pillX + (pillW - fontSmall.width("OFF")) / 2F, pillY + (pillH - fontSmall.height()) / 2F + 0.5F, TEXT_FAINT.getRGB());
    }
  }

  private void drawConfigPanel(int mx, int my, float w, float h, float alpha) {
    if (!configVisible) return;
    float fw = CONFIG_PANEL_W;
    float fh = h - 40F;
    float fx = w - fw - MARGIN;
    float fy = 20F;

    configRect = new float[] {fx, fy, fw, fh};
    RoundedUtils.drawRound(fx, fy, fw, fh, 12F, new Color(11, 14, 24, 220));
    RoundedUtils.drawRoundOutline(fx, fy, fw, fh, 12F, 1F, new Color(0, 0, 0, 0), withAlpha(BORDER, 200));

    float t = pulse();
    RoundedUtils.drawGradientHorizontal(fx, fy, fw, 3F, 1.5F, ACCENT_A, ACCENT_B);

    fontTitle.drawWithShadow("CONFIG MANAGEMENT", fx + PAD, fy + 10F, TEXT_MAIN.getRGB());

    float searchW = fw - PAD * 2F;
    float searchX = fx + PAD;
    float searchY = fy + 50F;
    boolean searchHover = mx >= searchX && mx <= searchX + searchW && my >= searchY && my <= searchY + 26F;
    RoundedUtils.drawRound(searchX, searchY, searchW, 26F, 8F, new Color(7, 10, 18, 200));
    RoundedUtils.drawRoundOutline(searchX, searchY, searchW, 26F, 8F, 1F, new Color(0, 0, 0, 0), configSearchFocused ? withAlpha(ACCENT_A, 220) : withAlpha(BORDER, 255));
    drawMagnifier(searchX + 8F, searchY + 13F, withAlpha(TEXT_FAINT, 255));
    String shown = configSearchFocused || !configSearch.isEmpty() ? configSearch : "Search configs...";
    int textColor = configSearch.isEmpty() && !configSearchFocused ? TEXT_FAINT.getRGB() : TEXT_DIM.getRGB();
    String clipped = clipText(fontBody, shown, searchW - 24F);
    fontBody.draw(clipped, searchX + 16F, searchY + 8F, textColor);

    float listY = searchY + 36F;
    float listH = fh - 100F;
    float listW = fw - 20F;
    boolean overList = mx >= searchX && mx <= searchX + listW && my >= listY && my <= listY + listH;

    beginScissor(searchX, listY, listW, listH);
    drawConfigList(searchX, listY, listW, listH, mx, my);
    endScissor();

    if (overList && Mouse.isButtonDown(0)) {
      configSearch = "";
      configModule = null;
    }

    float bottomY = fy + fh - 38F;
    float saveBtnX = fx + PAD;
    float saveBtnY = bottomY;
    float saveBtnW = 90F;
    float saveBtnH = 28F;
    boolean saveHover = mx >= saveBtnX && mx <= saveBtnX + saveBtnW && my >= saveBtnY && my <= saveBtnY + saveBtnH;
    RoundedUtils.drawRound(saveBtnX, saveBtnY, saveBtnW, saveBtnH, 8F, saveHover ? withAlpha(ACCENT_A, 200) : new Color(7, 10, 18, 200));
    fontBody.drawCentered("Save Config", saveBtnX + saveBtnW / 2F, saveBtnY + 14F, new Color(4, 9, 17).getRGB());

    float loadBtnX = saveBtnX + saveBtnW + 8F;
    boolean loadHover = mx >= loadBtnX && mx <= loadBtnX + saveBtnW && my >= saveBtnY && my <= saveBtnY + saveBtnH;
    RoundedUtils.drawRound(loadBtnX, saveBtnY, saveBtnW, saveBtnH, 8F, loadHover ? withAlpha(ACCENT_B, 200) : new Color(7, 10, 18, 200));
    fontBody.drawCentered("Load Config", loadBtnX + saveBtnW / 2F, saveBtnY + 14F, TEXT_MAIN.getRGB());

    float delBtnX = loadBtnX + saveBtnW + 8F;
    boolean delHover = mx >= delBtnX && mx <= delBtnX + saveBtnW && my >= saveBtnY && my <= saveBtnY + saveBtnH;
    RoundedUtils.drawRound(delBtnX, saveBtnY, saveBtnW, saveBtnH, 8F, delHover ? new Color(226, 70, 90, 200) : new Color(7, 10, 18, 200));
    fontBody.drawCentered("Delete", delBtnX + saveBtnW / 2F, saveBtnY + 14F, TEXT_MAIN.getRGB());

    if (Mouse.isButtonDown(0)) {
      if (saveHover) {
        saveConfig();
      } else if (loadHover) {
        loadConfig();
      } else if (delHover) {
        deleteConfig();
      }
    }
  }

  private void drawConfigList(float x, float y, float w, float h, int mx, int my) {
    configRows.clear();
    float rowH = 28F;
    for (Module m : Miau.moduleManager.modules.values()) {
      boolean rowHover = mx >= x && mx <= x + w && my >= y && my <= y + rowH;
      if (rowHover) {
        RoundedUtils.drawRound(x, y + 2F, w, rowH - 4F, 7F, ROW_HOVER);
      }
      String name = clipText(fontBody, m.getName(), w - 60F);
      int color = m.isEnabled() ? TEXT_DIM.getRGB() : TEXT_FAINT.getRGB();
      fontBody.draw(name, x + 8F, y + 8F, color);
      if (m.isEnabled()) {
        RoundedUtils.drawRound(x + w - 4F, y + 10F, 3F, 3F, 1F, ACCENT_A);
      }
      y += rowH;
    }
  }

  private void drawBindsPanel(int mx, int my, float w, float h, float alpha) {
    List<Module> bound = new ArrayList<Module>();
    for (Module m : Miau.moduleManager.modules.values()) {
      if (m.getKey() != 0) {
        bound.add(m);
      }
    }
    if (bound.isEmpty()) {
      bindsRect = null;
      return;
    }
    bound.sort(new Comparator<Module>() {
      public int compare(Module a, Module b) {
        return a.getName().compareTo(b.getName());
      }
    });
    int show = Math.min(bound.size(), 5);
    float pw = 190F;
    float ph = 34F + show * 19F;
    float px = w - pw - MARGIN;
    float py = h - ph - 10F;
    bindsRect = new float[] {px, py, pw, ph};

    RoundedUtils.drawRound(px, py, pw, ph, 11F, new Color(11, 15, 25, 215));
    RoundedUtils.drawRoundOutline(px, py, pw, ph, 11F, 1F, new Color(0, 0, 0, 0), withAlpha(BORDER, 200));
    fontSmall.drawWithShadow("KEYBINDS  (" + bound.size() + ")", px + 10F, py + 7F, TEXT_FAINT.getRGB());
    for (int i = 0; i < show; i++) {
      Module m = bound.get(i);
      float rowY = py + 24F + i * 19F;
      String name = clipText(fontBody, m.getName(), pw - 95F);
      fontBody.draw(name, px + 10F, rowY, m.isEnabled() ? TEXT_DIM.getRGB() : TEXT_FAINT.getRGB());
      if (m.isEnabled()) {
        RoundedUtils.drawRound(px + 8F, rowY + 3F, 2F, 2F, 1F, ACCENT_A);
      }
      String key = KeyBindUtil.getKeyName(m.getKey());
      fontBody.draw(key, px + pw - 10F - fontBody.width(key), rowY, TEXT_MAIN.getRGB());
    }
    if (bound.size() > show) {
      String more = "+" + (bound.size() - show) + " more";
      fontSmall.draw(more, px + pw - 10F - fontSmall.width(more), py + ph - 12F, TEXT_FAINT.getRGB());
    }
  }

  private void drawFooter(int mx, int my, float w, float h, float alpha) {
    float y = h - 25F;
    fontSmall.draw("LMB  toggle     RMB  settings     ESC close", MARGIN, y, new Color(70, 80, 100).getRGB());
    String version = "v" + Miau.version;
    fontSmall.drawRight(version, w - MARGIN, y, TEXT_FAINT.getRGB());
  }

  private void drawBindingOverlay(float w, float h) {
    RoundedUtils.drawRound(0, 0, w, h, 0F, new Color(4, 6, 12, 195));
    float cx = w / 2F;
    float cy = h / 2F;
    RoundedUtils.drawRound(cx - 210F, cy - 58F, 420F, 116F, 16F, PANEL_BG);
    RoundedUtils.drawRoundOutline(cx - 210F, cy - 58F, 420F, 116F, 16F, 1F, new Color(0, 0, 0, 0), withAlpha(ACCENT_A, 180));
    RoundedUtils.drawGradientHorizontal(cx - 210F, cy - 58F, 420F, 2.5F, 1.5F, ACCENT_A, ACCENT_B);
    fontTitle.drawCentered("Bind a key for " + bindingModule.getName(), cx, cy - 34F, TEXT_MAIN.getRGB());
    fontBody.drawCentered("Press any key to bind  ·  ESC cancel  ·  DELETE clear", cx, cy + 16F, TEXT_DIM.getRGB());
  }

  private void beginScissor(float x, float y, float w, float h) {
    float s = currentSR.getScaleFactor();
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

  private void drawMagnifier(float cx, float cy, Color color) {
    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glColor4f(color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F, color.getAlpha() / 255F);
    GL11.glLineWidth(1.4F);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    for (int i = 0; i < 24; i++) {
      double ang = Math.toRadians(i * 15D);
      GL11.glVertex2f((float) (cx + Math.cos(ang) * 4F), (float) (cy + Math.sin(ang) * 4F));
    }
    GL11.glEnd();
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex2f(cx + 3F, cy + 3F);
    GL11.glVertex2f(cx + 6.5F, cy + 6.5F);
    GL11.glEnd();
    GL11.glColor4f(1F, 1F, 1F, 1F);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glPopMatrix();
  }

  @Override
  protected void mouseClicked(int mx, int my, int button) throws IOException {
    super.mouseClicked(mx, my, button);
    if (bindingModule != null) {
      bindingModule = null;
      return;
    }
    float w = scaledWidth();
    float h = scaledHeight();

    float x = MARGIN;
    float y = BAR_Y;
    float bw = w - MARGIN * 2F;
    float closeSize = 28F;
    float closeX = x + bw - closeSize - 6F;
    float closeY = y + (BAR_H - closeSize) / 2F;
    if (mx >= closeX && mx <= closeX + closeSize && my >= closeY && my <= closeY + closeSize) {
      mc.displayGuiScreen(null);
      return;
    }

    float tx = x + 4F + LOGO_W + 14F;
    float tabH = 26F;
    float tabY = y + (BAR_H - tabH) / 2F;
    for (int i = 0; i < catNames.size(); i++) {
      float tw = fontTab.width(catNames.get(i)) + 18F;
      if (mx >= tx && mx <= tx + tw && my >= tabY && my <= tabY + tabH) {
        if (!catNames.get(i).equals(activeCategory) || !search.isEmpty()) {
          activeCategory = catNames.get(i);
          tabTarget = i;
          search = "";
          searchFocused = false;
          targetScroll = 0F;
          rebuildLayout();
        }
        return;
      }
      tx += tw + 6F;
    }

    float closeSize2 = 28F;
    float statsW = fontValue.width(cachedEnabled + "/" + visibleModules.size()) + 22F;
    float statsX = closeSize2 <= 0 ? 0 : (MARGIN + w - MARGIN * 2F - closeSize2 - 6F) - statsW - 8F;
    float searchW = 132F;
    float searchX = statsX - searchW - 10F;
    if (mx >= searchX && mx <= searchX + searchW && my >= closeY && my <= closeY + closeSize2) {
      searchFocused = true;
      configSearchFocused = false;
      return;
    }

    if (configRect != null && contains(configRect, mx, my)) {
      handleConfigClick(mx, my, button);
      return;
    }

    if (button == 0 || button == 1) {
      for (Card card : cards) {
        if (mx >= card.x && mx <= card.x + card.w && my >= card.y - scroll && my <= card.y - scroll + card.h) {
          if (button == 0) {
            card.module.toggle();
          } else {
            mc.displayGuiScreen(new ModuleSettingsGui(card.module));
          }
          return;
        }
      }
    }

    if (bindsRect != null && contains(bindsRect, mx, my)) {
      return;
    }

    searchFocused = false;
  }

  private void handleConfigClick(int mx, int my, int button) {
    if (button != 0) return;
    float w = scaledWidth();
    float fw = CONFIG_PANEL_W;
    float fx = w - fw - MARGIN;
    float searchY = 88F;
    if (mx >= fx + PAD && mx <= fx + fw - PAD && my >= searchY && my <= searchY + 160F) {
      float rowH = 28F;
      int row = (int) ((my - searchY) / rowH);
      if (row >= 0 && row < visibleModules.size()) {
        configModule = visibleModules.get(row);
        configVisible = true;
      }
    }
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getDWheel();
    if (wheel == 0) return;
    int mx = (int) (Mouse.getX() * scaledWidth() / (float) mc.displayWidth);
    int my = (int) (scaledHeight() - Mouse.getY() * scaledHeight() / (float) mc.displayHeight);
    if (configRect != null && contains(configRect, mx, my)) {
      float fw = CONFIG_PANEL_W;
      float fx = scaledWidth() - fw - MARGIN;
      float searchY = 88F;
      if (mx >= fx + PAD && mx <= fx + fw - PAD && my >= searchY && my <= searchY + 280F) {
        float rowH = 28F;
        int row = (int) ((my - searchY) / rowH);
        if (row >= 0 && row < visibleModules.size()) {
          configModule = visibleModules.get(row);
          configVisible = true;
        }
      }
    } else {
      targetScroll -= wheel / 120F * 40F;
      targetScroll = clamp(targetScroll, 0F, 99999F);
    }
  }

  @Override
  protected void mouseReleased(int mx, int my, int state) {
    super.mouseReleased(mx, my, state);
    if (state == 0) {
      draggingSlider = null;
    }
  }

  @Override
  public void updateScreen() {
    if (draggingSlider != null) {
      int mx = (int) (Mouse.getX() * scaledWidth() / (float) mc.displayWidth);
      int my = (int) (scaledHeight() - Mouse.getY() * scaledHeight() / (float) mc.displayHeight);
      if (!Mouse.isButtonDown(0)) {
        draggingSlider = null;
      }
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) throws IOException {
    if (bindingModule != null) {
      if (keyCode == 1) {
        bindingModule = null;
      } else if (keyCode == 211 || keyCode == 14) {
        bindingModule.setKey(0);
        bindingModule = null;
      } else {
        bindingModule.setKey(keyCode);
        bindingModule = null;
      }
      return;
    }
    if (searchFocused) {
      if (keyCode == 1) {
        searchFocused = false;
        return;
      }
      if (keyCode == 14 && !search.isEmpty()) {
        search = search.substring(0, search.length() - 1);
        targetScroll = 0F;
        rebuildLayout();
        return;
      }
      if (typedChar >= 32 && typedChar != 127) {
        search += typedChar;
        targetScroll = 0F;
        rebuildLayout();
        return;
      }
      return;
    }
    if (configSearchFocused) {
      if (keyCode == 1) {
        configSearchFocused = false;
        return;
      }
      if (keyCode == 14 && !configSearch.isEmpty()) {
        configSearch = configSearch.substring(0, configSearch.length() - 1);
        return;
      }
      if (typedChar >= 32 && typedChar != 127) {
        configSearch += typedChar;
        return;
      }
      return;
    }
    if (keyCode == 1) {
      mc.displayGuiScreen(null);
    }
  }

  private void saveConfig() {
    try {
      java.io.File file = new java.io.File(mc.mcDataDir, "keystrokesconfig.json");
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
      try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        gson.toJson(json, writer);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void loadConfig() {
    try {
      java.io.File file = new java.io.File(mc.mcDataDir, "keystrokesconfig.json");
      if (!file.exists()) return;
      com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(new java.io.FileReader(file)).getAsJsonObject();
      for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
        String modName = entry.getKey();
        com.google.gson.JsonObject modJson = entry.getValue().getAsJsonObject();
        boolean enabled = modJson.get("enabled").getAsBoolean();
        int key = modJson.get("key").getAsInt();
        Module m = Miau.moduleManager.getModule(modName);
        if (m != null) {
          m.setEnabled(enabled);
          m.setKey(key);
          if (modJson.has("properties")) {
            for (com.google.gson.JsonElement propElement : modJson.getAsJsonArray("properties")) {
              com.google.gson.JsonObject propJson = propElement.getAsJsonObject();
              String propName = propJson.get("name").getAsString();
              if (propJson.has("value")) {
                String value = propJson.get("value").getAsString();
                Module mod = Miau.moduleManager.getModule(modName);
                if (mod != null) {
                  for (Property<?> p : mod.getValues()) {
                    if (p.getName().equals(propName)) {
                      p.parseString(value);
                    }
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void deleteConfig() {
    try {
      java.io.File file = new java.io.File(mc.mcDataDir, "keystrokesconfig.json");
      if (file.exists()) {
        file.delete();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void saveCustomConfig() {
    try {
      java.io.File file = new java.io.File(mc.mcDataDir, "miau_custom_config.json");
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
      try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        gson.toJson(json, writer);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void loadCustomConfig() {
    try {
      java.io.File file = new java.io.File(mc.mcDataDir, "miau_custom_config.json");
      if (!file.exists()) return;
      com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(new java.io.FileReader(file)).getAsJsonObject();
      for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
        String modName = entry.getKey();
        com.google.gson.JsonObject modJson = entry.getValue().getAsJsonObject();
        boolean enabled = modJson.get("enabled").getAsBoolean();
        int key = modJson.get("key").getAsInt();
        Module m = Miau.moduleManager.getModule(modName);
        if (m != null) {
          m.setEnabled(enabled);
          m.setKey(key);
          if (modJson.has("properties")) {
            for (com.google.gson.JsonElement propElement : modJson.getAsJsonArray("properties")) {
              com.google.gson.JsonObject propJson = propElement.getAsJsonObject();
              String propName = propJson.get("name").getAsString();
              if (propJson.has("value")) {
                String value = propJson.get("value").getAsString();
                for (Property<?> p : m.getValues()) {
                  if (p.getName().equals(propName)) {
                    p.parseString(value);
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void resetCustomConfig() {
    for (Module m : Miau.moduleManager.modules.values()) {
      m.setEnabled(false);
      m.setKey(0);
      for (Property<?> p : m.getValues()) {
        p.parseString("");
      }
    }
  }

  private List<Property<?>> buildVisibleProps(Module module) {
    List<Property<?>> result = new ArrayList<Property<?>>();
    for (Property<?> p : module.getValues()) {
      if (p instanceof DragProperty || p instanceof ItemListProperty) {
        continue;
      }
      if (p.isVisible()) {
        result.add(p);
      }
    }
    return result;
  }

  private int countEnabled() {
    int n = 0;
    for (Module m : Miau.moduleManager.modules.values()) {
      if (m.isEnabled()) {
        n++;
      }
    }
    return n;
  }

  private static String stripCodes(String s) {
    if (s == null) return "";
    return s.replaceAll("[&\\u00A7].", "");
  }

  private String clipText(Font font, String text, float maxWidth) {
    if (font.width(text) <= maxWidth) return text;
    String clipped = text;
    while (!clipped.isEmpty() && font.width(clipped + "...") > maxWidth) {
      clipped = clipped.substring(0, clipped.length() - 1);
    }
    return clipped + "...";
  }

  private static boolean contains(float[] rect, float mx, float my) {
    return mx >= rect[0] && mx <= rect[0] + rect[2] && my >= rect[1] && my <= rect[1] + rect[3];
  }

  private static float clamp(float v, float min, float max) {
    return v < min ? min : (v > max ? max : v);
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
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

  private Color accent(float t) {
    float x = (float) (0.5F + 0.5F * Math.sin(t * Math.PI));
    return interpolate(ACCENT_A, ACCENT_B, x);
  }

  private float pulse() {
    return (System.currentTimeMillis() % 7000L) / 7000F;
  }
}
