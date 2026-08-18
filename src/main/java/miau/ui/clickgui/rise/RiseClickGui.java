package miau.ui.clickgui.rise;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import miau.Miau;
import miau.module.Module;
import miau.module.ModuleManager;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ItemListProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.property.properties.TextProperty;
import miau.util.client.KeyBindUtil;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Rise-styled ClickGUI.
 *
 * Original Click GUI Rise by TheSmartDog.
 * Recode &amp; upgraded by BeoPhiMan. Styles ported from Avocado Reborn.
 */
@SideOnly(Side.CLIENT)
public class RiseClickGui extends GuiScreen {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final Font FONT_REGULAR = FontRepository.getFont("Inter Regular", 18f);
    private static final Font FONT_SEMIBOLD = FontRepository.getFont("Inter SemiBold", 18f);
    private static final Font FONT_TITLE = FontRepository.getFont("augustus", 35f);
    private static final Font FONT_MOD_NAME = FontRepository.getFont("Inter SemiBold", 18f);

    private static final Color WIN_BG = new Color(8, 9, 15, 255);
    private static final Color SIDEBAR_BG = new Color(6, 7, 12, 255);
    private static final Color CONTENT_BG = new Color(10, 11, 18, 255);
    private static final Color DIVIDER = new Color(20, 22, 34, 180);
    private static final Color ACCENT = new Color(30, 120, 255, 255);
    private static final Color ACCENT_DIM = new Color(30, 120, 255, 150);
    private static final Color SRCH_BG = new Color(13, 14, 22, 255);
    private static final Color SRCH_BOR = new Color(32, 36, 52, 255);
    private static final Color SRCH_BOR_F = new Color(30, 120, 255, 190);
    private static final Color SRCH_HINT = new Color(66, 72, 96, 255);
    private static final Color MOD_BG = new Color(13, 14, 22, 255);
    private static final Color MOD_BG_H = new Color(19, 21, 33, 255);
    private static final Color MOD_NAME = new Color(200, 208, 230, 255);
    private static final Color MOD_NAME_ON = new Color(30, 120, 255, 255);
    private static final Color MOD_CAT_C = new Color(30, 120, 255, 175);
    private static final Color MOD_DESC = new Color(86, 93, 118, 255);
    private static final Color MOD_SEP = new Color(21, 23, 35, 210);
    private static final Color SET_BG = new Color(8, 9, 15, 255);
    private static final Color SET_LBL = new Color(178, 187, 210, 255);
    private static final Color SL_TRACK = new Color(22, 24, 38, 255);
    private static final Color SL_FILL = new Color(30, 120, 255, 255);
    private static final Color TOG_ON = new Color(30, 120, 255, 255);
    private static final Color TOG_OFF = new Color(36, 40, 55, 255);
    private static final Color SEP_C = new Color(22, 24, 38, 255);
    private static final Color KEY_C = new Color(128, 138, 160, 255);
    private static final Color KEY_H = new Color(30, 120, 255, 255);
    private static final Color OVR_DIM = new Color(0, 0, 0, 155);
    private static final Color OVR_BOX = new Color(13, 15, 24, 250);
    private static final Color CAT_HOV = new Color(255, 255, 255, 8);
    private static final Color CAT_TXT = new Color(128, 136, 160, 255);
    private static final Color CAT_TXT_A = new Color(255, 255, 255, 255);
    private static final Color CAT_PILL = new Color(18, 24, 46, 210);

    private static final float CR = 12f;
    private static final int SW = 105;
    private static final int HDR_H = 33;
    private static final int CAT_H = 22;
    private static final int CHDR_H = 30;
    private static final int MOD_H = 37;
    private static final int M_PAD = 5;
    private static final int SBW = 3;
    private static final int DEF_W = 500;
    private static final int DEF_H = 322;
    private static final int MIN_W = 336;
    private static final int MIN_H = 222;
    private static final float ANIM_SPD = 0.18f;
    private static final float SCROLL_FRIC = 0.82f;

    private static final String[] TARGET_NAMES = {"Players", "Mobs", "Animals", "Invisible", "Dead"};

    private static class SavedState {
        String openMod = null;
        String selCat = "Combat";
        String srchTxt = "";
        boolean srchOn = false;
        float scroll = 0f;
        Map<String, Float> expandAnim = new HashMap<>();
        boolean showTargets = false;
    }

    private final SavedState saved = new SavedState();

    private int gX = 0, gY = 0;
    private int gW = DEF_W, gH = DEF_H;
    private boolean dragging = false;
    private int dDX = 0, dDY = 0;

    private String selCat = "Combat";
    private boolean srchOn = false;
    private String srchTxt = "";
    private float mScroll = 0f;
    private int mMax = 0;
    private float scrollVel = 0f;
    private String openMod = null;
    private boolean showTargets = false;

    private boolean curOn = true;
    private long blinkT = 0L;

    private boolean clickL = false, prevML = false;
    private boolean clickR = false, prevMR = false;

    private final Map<String, Float> hovAnim = new HashMap<>();
    private final Map<String, Float> expandAnim = new HashMap<>();
    private final Map<String, Float> toggleAnim = new HashMap<>();
    private final Map<String, Float> modFadeAnim = new HashMap<>();
    private final Map<String, Float> catSelAnim = new HashMap<>();
    private float contentFade = 1f;
    private int contentFadeDir = 0;
    private String pendingCat = null;

    private Module bindMod = null;
    private long bindOverlayOpenTime = 0L;
    private final Map<String, Boolean> listExpanded = new HashMap<>();
    private Property<?> sliderHeld = null;

    private final Map<String, Boolean> toggleTargets = new HashMap<>();

    private long lastFrameMs = System.currentTimeMillis();

    @Override
    public void initGui() {
        ScaledResolution sr = new ScaledResolution(mc);
        gX = (sr.getScaledWidth() - gW) / 2;
        gY = (sr.getScaledHeight() - gH) / 2;
        selCat = saved.selCat;
        srchTxt = saved.srchTxt;
        srchOn = saved.srchOn;
        mScroll = saved.scroll;
        expandAnim.putAll(saved.expandAnim);
        openMod = saved.openMod;
        showTargets = saved.showTargets;
        contentFade = 1f;
        contentFadeDir = 0;
        pendingCat = null;
        super.initGui();
    }

    @Override
    public void onGuiClosed() {
        saved.selCat = selCat;
        saved.srchTxt = srchTxt;
        saved.srchOn = srchOn;
        saved.scroll = mScroll;
        saved.expandAnim = new HashMap<>(expandAnim);
        saved.openMod = openMod;
        saved.showTargets = showTargets;
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = ((now - lastFrameMs) / 16.67f);
        if (dt < 0.5f) dt = 0.5f;
        if (dt > 3f) dt = 3f;
        lastFrameMs = now;

        if (dragging) {
            gX = mouseX - dDX;
            gY = mouseY - dDY;
        }

        boolean ml = Mouse.isButtonDown(0);
        boolean mr = Mouse.isButtonDown(1);
        clickL = !prevML && ml;
        clickR = !prevMR && mr;
        prevML = ml;
        prevMR = mr;
        if (!ml) {
            dragging = false;
            sliderHeld = null;
        }

        if (now - blinkT > 530L) {
            curOn = !curOn;
            blinkT = now;
        }

        mScroll += scrollVel * dt;
        mScroll = Math.max(0, Math.min(mMax, mScroll));
        scrollVel *= Math.pow(SCROLL_FRIC, dt);
        if (Math.abs(scrollVel) < 0.3f) scrollVel = 0f;

        if (contentFadeDir == -1) {
            contentFade -= ANIM_SPD * 2f * dt;
            if (contentFade <= 0f) {
                if (pendingCat != null) {
                    selCat = pendingCat;
                    pendingCat = null;
                    showTargets = false;
                }
                contentFadeDir = 1;
                mScroll = 0f;
                scrollVel = 0f;
                openMod = null;
                modFadeAnim.clear();
            }
        } else if (contentFadeDir == 1) {
            contentFade += ANIM_SPD * 2f * dt;
            if (contentFade >= 1f) contentFadeDir = 0;
        }

        RoundedUtils.drawRoundedRectRise(gX, gY, gW, gH, CR, WIN_BG.getRGB(), true, true, true, true);
        RenderUtil.drawRect(gX + CR, gY, gX + gW - CR, gY + gH, WIN_BG.getRGB());
        RenderUtil.drawRect(gX, gY + CR, gX + gW, gY + gH - CR, WIN_BG.getRGB());

        drawSidebar(mouseX, mouseY);
        drawContent(mouseX, mouseY);

        if (bindMod != null) {
            drawKeybindOverlay();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSidebar(int mx, int my) {
        float x1 = gX;
        float y1 = gY;
        RoundedUtils.drawRoundedRectRise(x1, y1, SW, gH, CR, SIDEBAR_BG.getRGB(), true, false, false, true);

        String titleStr = "Miau Minus";
        String verStr = "b1";
        float titleW = FONT_TITLE.getStringWidth(titleStr);
        float blockW = titleW + 3 + FONT_TITLE.getStringWidth(verStr);
        float tX = x1 + (SW - blockW) / 2f;
        float tY = y1 + (HDR_H - FONT_TITLE.getFontHeight()) / 2f - 1.5f;
        FONT_TITLE.draw(titleStr, tX, tY, Color.WHITE.getRGB());
        FONT_TITLE.draw(verStr, tX + titleW + 3f, tY - 2f, new Color(30, 120, 255, 255).getRGB());

        RenderUtil.drawRect(x1 + 12f, y1 + HDR_H, x1 + SW - 12f, y1 + HDR_H + 1, DIVIDER.getRGB());

        drawCatList(mx, my, x1, y1 + HDR_H + 1, SW);
    }

    private List<String> getCategories() {
        List<String> result = new ArrayList<>();
        ModuleManager mm = Miau.moduleManager;
        if (mm != null && mm.modules != null) {
            for (Module mod : mm.modules.values()) {
                String cat = getCategoryName(mod);
                if (cat == null) continue;
                if (!result.contains(cat)) result.add(cat);
            }
        }
        return result;
    }

    private void drawCatList(int mx, int my, float sx, float startY, int sw) {
        float y = startY + 8f;
        float px1 = sx + 10f;
        float px2 = sx + sw - 10f;
        float rW = px2 - px1;
        float txX = px1 + 24f;

        boolean sAct = srchOn || (selCat == null && !srchTxt.isEmpty());
        boolean sHov = inRect(mx, my, px1, y, rW, CAT_H);

        if (sAct) {
            RoundedUtils.drawRoundedRectRise(px1, y + 1f, rW, CAT_H - 2, 6f, CAT_PILL.getRGB(), false, false, false, false);
            RenderUtil.drawRect(px1, y + 4, px1 + 2.5f, y + CAT_H - 4, new Color(80, 150, 255, 255).getRGB());
        } else if (sHov) {
            RoundedUtils.drawRoundedRectRise(px1, y + 1f, rW, CAT_H - 2, 6f, CAT_HOV.getRGB(), false, false, false, false);
        }

        int sCol = sAct ? CAT_TXT_A.getRGB() : CAT_TXT.getRGB();
        FONT_REGULAR.draw("Search", txX, y + (CAT_H - FONT_REGULAR.getFontHeight()) / 2f - 1.5f, sCol);

        if (clickL && sHov) {
            showTargets = false;
            selCat = null;
            srchOn = true;
            openMod = null;
            mScroll = 0f;
            scrollVel = 0f;
        }
        y += CAT_H + 4f;

        List<String> cats = getCategories();
        for (String cat : cats) {
            boolean isAct = selCat != null && selCat.equals(cat) && !srchOn && srchTxt.isEmpty();
            boolean isHov = inRect(mx, my, px1, y, rW, CAT_H);

            float prev = catSelAnim.getOrDefault(cat, 0f);
            float newVal = prev + ((isAct ? 1f : 0f) - prev) * ANIM_SPD * 2f * dt();
            catSelAnim.put(cat, newVal);
            float t = easeOutQ(newVal);

            if (t > 0.005f) {
                RoundedUtils.drawRoundedRectRise(px1, y + 1f, rW, CAT_H - 2, 6f, new Color(18, 24, 46, (int) (210 * t)).getRGB(), false, false, false, false);
                RenderUtil.drawRect(px1, y + 4, px1 + 2.5f, y + CAT_H - 4, new Color(80, 150, 255, 255).getRGB());
            } else if (isHov) {
                RoundedUtils.drawRoundedRectRise(px1, y + 1f, rW, CAT_H - 2, 6f, CAT_HOV.getRGB(), false, false, false, false);
            }

            int col = lerpColor(CAT_TXT, CAT_TXT_A, t).getRGB();
            FONT_SEMIBOLD.draw(cat, txX, y + (CAT_H - FONT_SEMIBOLD.getFontHeight()) / 2f - 1.5f, col);

            if (clickL && isHov && bindMod == null) {
                if (showTargets || !cat.equals(selCat)) {
                    showTargets = false;
                    pendingCat = cat;
                    contentFadeDir = -1;
                    contentFade = 1f;
                    srchOn = false;
                    srchTxt = "";
                }
            }
            y += CAT_H + 4f;
        }

        y += 4f;
        RenderUtil.drawRect(px1 + 4f, y, px2 - 4f, y + 1, DIVIDER.getRGB());
        y += 6f;

        boolean tHov = inRect(mx, my, px1, y, rW, CAT_H);
        if (showTargets) {
            RoundedUtils.drawRoundedRectRise(px1, y + 1f, rW, CAT_H - 2, 6f, CAT_PILL.getRGB(), false, false, false, false);
            RenderUtil.drawRect(px1, y + 4, px1 + 2.5f, y + CAT_H - 4, new Color(80, 150, 255, 255).getRGB());
        } else if (tHov) {
            RoundedUtils.drawRoundedRectRise(px1, y + 1f, rW, CAT_H - 2, 6f, CAT_HOV.getRGB(), false, false, false, false);
        }

        int tCol = showTargets ? CAT_TXT_A.getRGB() : CAT_TXT.getRGB();
        FONT_SEMIBOLD.draw("Targets", txX, y + (CAT_H - FONT_SEMIBOLD.getFontHeight()) / 2f - 1f, tCol);

        if (clickL && tHov && bindMod == null && !showTargets) {
            showTargets = true;
            selCat = null;
            srchOn = false;
            srchTxt = "";
            contentFadeDir = -1;
            contentFade = 1f;
            openMod = null;
            mScroll = 0f;
            scrollVel = 0f;
        }
    }

    private void drawContent(int mx, int my) {
        float cx = gX + SW;
        float cy = gY;
        float cw = gW - SW;
        float ch = gH;

        RoundedUtils.drawRoundedRectRise(cx, cy, cw, ch, CR, CONTENT_BG.getRGB(), false, true, true, false);

        drawSearchBar(mx, my, cx, cy, cw);
        RenderUtil.drawRect(cx + 8f, cy + CHDR_H - 1f, cx + cw - 8f, cy + CHDR_H, DIVIDER.getRGB());

        float listY = cy + CHDR_H;
        float listH = ch - CHDR_H;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        GL11.glScissor(
            (int) (cx * sf),
            (int) ((height - (cy + ch)) * sf),
            (int) (cw * sf),
            (int) ((ch - CHDR_H) * sf)
        );

        GL11.glColor4f(1f, 1f, 1f, easeOutQ(contentFade));
        if (showTargets) {
            drawTargetsPanel(mx, my, cx, listY, cw, listH);
        } else {
            drawModList(mx, my, cx, listY, cw, listH);
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        drawScrollbar(cx + cw - SBW - 3f, listY + 4f, listH - 8f, (int) mScroll, mMax);
    }

    private void drawSearchBar(int mx, int my, float cx, float cy, float cw) {
        float srH = 26f;
        float srW = cw * 0.55f;
        float srX = cx + cw - srW - 14f;
        float srY = cy + (CHDR_H - srH) / 2f;
        boolean hov = inRect(mx, my, srX, srY, srW, srH);

        Color bg = srchOn ? new Color(19, 21, 33, 255) : SRCH_BG;
        Color bor = srchOn ? SRCH_BOR_F : (hov ? new Color(42, 47, 66, 255) : SRCH_BOR);
        RoundedUtils.drawRound(srX, srY, srW, srH, 8f, bg);
        RoundedUtils.drawRoundOutline(srX, srY, srW, srH, 8f, 1f, bg, bor);

        float ty2 = srY + (srH - FONT_REGULAR.getFontHeight()) / 2f + 1.5f;
        float tx = srX + 19f;

        if (srchTxt.isEmpty() && !srchOn) {
            FONT_REGULAR.draw("Start typing to search...", tx, ty2, SRCH_HINT.getRGB());
        } else {
            FONT_REGULAR.draw(srchTxt, tx, ty2, Color.WHITE.getRGB());
        }

        if (srchOn && curOn) {
            float bx = tx + FONT_REGULAR.getStringWidth(srchTxt);
            RenderUtil.drawRect(bx, srY + 4f, bx + 1f, srY + srH - 4f, new Color(210, 218, 240, 190).getRGB());
        }

        if (clickL && bindMod == null) {
            boolean wasOn = srchOn;
            srchOn = hov;
            if (srchOn && !wasOn) selCat = null;
        }
    }

    private void drawModList(int mx, int my, float cx, float cy, float cw, float ch) {
        List<Module> mods = getMods();
        float iw = cw - M_PAD * 2f - SBW - 6f;
        float gap = 4f;

        for (Module mod : mods) {
            boolean isOpen = mod.getName().equals(openMod);
            String name = mod.getName();
            float prev = expandAnim.getOrDefault(name, 0f);
            float target = isOpen ? 1f : 0f;
            expandAnim.put(name, prev + (target - prev) * ANIM_SPD * dt());
        }

        float totalH = 0f;
        for (Module mod : mods) {
            totalH += MOD_H + gap;
            float eT = easeOut(expandAnim.getOrDefault(mod.getName(), 0f));
            if (eT > 0f) totalH += calcInlineH(mod) * eT;
        }
        mMax = Math.max(0, (int) (totalH - (ch - 8f)));
        mScroll = Math.max(0, Math.min(mMax, mScroll));

        float y = cy + 8f - mScroll;

        for (int idx = 0; idx < mods.size(); idx++) {
            Module mod = mods.get(idx);
            float ix = cx + M_PAD;
            float iy = y;
            float ix2 = ix + iw;
            float eT = easeOut(expandAnim.getOrDefault(mod.getName(), 0f));
            float animH = calcInlineH(mod) * eT;

            float bottom = iy + MOD_H + animH;
            if (bottom < cy || iy > cy + ch) {
                y += MOD_H + gap + animH;
                continue;
            }

            boolean inMod = inRect(mx, my, ix, iy, iw, MOD_H);
            String nameK = mod.getName();
            float prev = hovAnim.getOrDefault(nameK, 0f);
            hovAnim.put(nameK, prev + ((inMod ? 1f : 0f) - prev) * ANIM_SPD * 1.5f * dt());
            Color bgC = lerpColor(MOD_BG, MOD_BG_H, easeOutQ(hovAnim.getOrDefault(nameK, 0f)));

            boolean isOpen = mod.getName().equals(openMod);
            float modR = 8f;
            float cardY = iy;

            RoundedUtils.drawRoundedRectRise(ix, cardY, iw, MOD_H, modR, bgC.getRGB(), eT <= 0.01f, true, true, eT <= 0.01f);

            if (mod.isEnabled()) {
                RoundedUtils.drawRoundedRectRise(ix, cardY + 9f, 3f, MOD_H - 18f, 1.5f, ACCENT.getRGB(), false, false, false, false);
            }

            float nX = ix + 14f;
            float nY = cardY + 9f;

            int nameColor = mod.isEnabled() ? MOD_NAME_ON.getRGB() : MOD_NAME.getRGB();
            FONT_MOD_NAME.draw(mod.getName(), nX, nY, nameColor);
            float nW = FONT_MOD_NAME.getStringWidth(mod.getName());
            float tgY = nY + (FONT_MOD_NAME.getFontHeight() - FONT_REGULAR.getFontHeight()) / 2f;
            FONT_REGULAR.draw("(" + getCategoryNameFor(mod) + ")", nX + nW + 5f, tgY, MOD_CAT_C.getRGB());

            if (clickL && inMod && bindMod == null) {
                mod.toggle();
            }
            if (clickR && inMod && bindMod == null) {
                openMod = isOpen ? null : mod.getName();
            }
            y += MOD_H;

            if (eT > 0.01f) {
                float sh = calcInlineH(mod);
                float panelTop = y;
                float panelBottom = y + sh;
                float clipTop = Math.max(panelTop, cy);
                float clipBottom = Math.min(panelBottom, cy + ch);
                float clipH = clipBottom - clipTop;

                if (clipH > 0f) {
                    ScaledResolution sr = new ScaledResolution(mc);
                    int sf = sr.getScaleFactor();
                    GL11.glScissor(
                        (int) (ix * sf),
                        (int) ((height - clipBottom) * sf),
                        (int) (iw * sf),
                        (int) (clipH * sf)
                    );
                    RoundedUtils.drawRoundedRectRise(ix, y, iw, sh, modR, SET_BG.getRGB(), false, false, true, true);
                    drawInlineSettings(mx, my, ix + 12f, y + 6f, iw - 24f, mod);
                    GL11.glScissor(
                        (int) (cx * sf),
                        (int) ((height - (cy + ch)) * sf),
                        (int) (cw * sf),
                        (int) ((ch - CHDR_H) * sf)
                    );
                }

                y += animH;
            }
            y += gap;
        }
    }

    private void drawInlineSettings(int mx, int my, float x, float startY, float w, Module mod) {
        float yy = startY;
        String kn = mod.getKey() == 0 ? "None" : KeyBindUtil.getKeyName(mod.getKey());
        String kTxt = "Keybind: " + kn;
        float kW = FONT_REGULAR.getStringWidth(kTxt);
        boolean kHov = inRect(mx, my, x, yy, kW, 12);
        FONT_REGULAR.draw(kTxt, x, yy, (kHov || bindMod == mod) ? KEY_H.getRGB() : KEY_C.getRGB());
        if (clickL && kHov && bindMod == null) {
            bindMod = mod;
            bindOverlayOpenTime = System.currentTimeMillis();
        }
        yy += 16f;
        RenderUtil.drawRect(x, yy, x + w, yy + 1, SEP_C.getRGB());
        yy += 7f;

        for (Property<?> value : mod.getValues()) {
            if (!value.isVisible()) continue;
            if (value instanceof DragProperty) continue;
            yy = drawValue(mx, my, value, x, yy, w) + 5f;
        }
    }

    private float drawValue(int mx, int my, Property<?> v, float x, float y, float w) {
        if (v instanceof BooleanProperty) return drawBoolean((BooleanProperty) v, mx, my, x, y, w);
        if (v instanceof FloatProperty) {
            FloatProperty fp = (FloatProperty) v;
            if (fp.isDoubleSlider()) return drawFloatRange(fp, mx, my, x, y, w);
            return drawFloatSlider(fp, mx, my, x, y, w);
        }
        if (v instanceof IntProperty) return drawIntSlider((IntProperty) v, mx, my, x, y, w);
        if (v instanceof PercentProperty) return drawPercentSlider((PercentProperty) v, mx, my, x, y, w);
        if (v instanceof ModeProperty) return drawModeList((ModeProperty) v, mx, my, x, y, w);
        if (v instanceof ColorProperty) return drawColorValue((ColorProperty) v, x, y, w);
        if (v instanceof TextProperty) return drawTextValue((TextProperty) v, x, y, w);
        FONT_REGULAR.draw(v.getName() + ": " + v.formatValue(), x, y, SET_LBL.getRGB());
        return y + 13f;
    }

    private float drawBoolean(BooleanProperty v, int mx, int my, float x, float y, float w) {
        FONT_SEMIBOLD.draw(v.getName(), x, y + 1f, SET_LBL.getRGB());
        float tw = 28f, th = 13f;
        float tx = x + w - tw, ty = y;
        String tKey = v.getName() + "_tog";
        float prev = toggleAnim.getOrDefault(tKey, v.getValue() ? 1f : 0f);
        float target = v.getValue() ? 1f : 0f;
        float newT = prev + (target - prev) * ANIM_SPD * 2f * dt();
        toggleAnim.put(tKey, newT);
        float eT = easeOutQ(newT);

        RoundedUtils.drawRoundedRectRise(tx, ty, tw, th, th / 2f, lerpColor(TOG_OFF, TOG_ON, eT).getRGB(), false, false, false, false);

        float tr = th / 2f - 1.5f;
        float thmXOff = tw - tr * 2f - 3f;
        float dmX = tx + 1.5f + thmXOff * eT;
        RoundedUtils.drawRoundedRectRise(dmX, ty + 1.5f, tr * 2f, th - 3f, tr, Color.WHITE.getRGB(), false, false, false, false);

        if (clickL && inRect(mx, my, tx - 3f, ty - 3f, tw + 6f, th + 6f)) {
            v.setValue(!v.getValue());
        }
        return y + 15f;
    }

    private float drawFloatSlider(FloatProperty v, int mx, int my, float x, float y, float w) {
        float min = v.getMin(), max = v.getMax();
        float cur = v.getValue();
        FONT_REGULAR.draw(v.getName() + ": " + trim(cur), x, y, SET_LBL.getRGB());
        float sy = y + 11f;
        float fillX = x + w * clamp01((cur - min) / (max - min));
        drawSliderTrack(x, sy, w, fillX);
        if ((Mouse.isButtonDown(0) && inSlider(mx, my, x, sy, w)) || sliderHeld == v) {
            float nv = min + (max - min) * clamp01((mx - x) / w);
            v.setValue(nv);
            sliderHeld = v;
        }
        return sy + 10f;
    }

    private float drawIntSlider(IntProperty v, int mx, int my, float x, float y, float w) {
        int min = v.getMinimum(), max = v.getMaximum();
        if (max <= min) return y + 13f;
        FONT_REGULAR.draw(v.getName() + ": " + v.getValue(), x, y, SET_LBL.getRGB());
        float sy = y + 11f;
        float fill = x + w * clamp01((float) (v.getValue() - min) / (max - min));
        drawSliderTrack(x, sy, w, fill);
        if ((Mouse.isButtonDown(0) && inSlider(mx, my, x, sy, w)) || sliderHeld == v) {
            int nv = Math.round(min + (max - min) * clamp01((mx - x) / w));
            v.setValue(nv);
            sliderHeld = v;
        }
        return sy + 10f;
    }

    private float drawPercentSlider(PercentProperty v, int mx, int my, float x, float y, float w) {
        int min = v.getMinimum(), max = v.getMaximum();
        if (max <= min) return y + 13f;
        FONT_REGULAR.draw(v.getName() + ": " + v.getValue() + "%", x, y, SET_LBL.getRGB());
        float sy = y + 11f;
        float fill = x + w * clamp01((float) (v.getValue() - min) / (max - min));
        drawSliderTrack(x, sy, w, fill);
        if ((Mouse.isButtonDown(0) && inSlider(mx, my, x, sy, w)) || sliderHeld == v) {
            int nv = Math.round(min + (max - min) * clamp01((mx - x) / w));
            v.setValue(nv);
            sliderHeld = v;
        }
        return sy + 10f;
    }

    private float drawFloatRange(FloatProperty v, int mx, int my, float x, float y, float w) {
        float lo = v.getValue(), hi = v.getSecondValue();
        float min = v.getMin(), max = v.getMax();
        FONT_REGULAR.draw(v.getName() + ": " + trim(lo) + " - " + trim(hi), x, y, SET_LBL.getRGB());
        float sy = y + 11f;
        lo = Math.max(min, Math.min(max, lo));
        hi = Math.max(min, Math.min(max, hi));
        float x1 = x + w * clamp01((lo - min) / (max - min));
        float x2 = x + w * clamp01((hi - min) / (max - min));
        drawRangeTrack(x, sy, w, x1, x2);
        if (Mouse.isButtonDown(0) && inSlider(mx, my, x, sy, w)) {
            float nv = min + (max - min) * clamp01((mx - x) / w);
            float d1 = Math.abs(nv - lo), d2 = Math.abs(nv - hi);
            boolean nearLo = (hi == lo) ? nv >= lo : d1 <= d2;
            if (sliderHeld != v) {
                sliderHeld = v;
            }
            if (nearLo) {
                if (nv <= hi) v.setValue(nv);
            } else {
                if (nv >= lo) v.setSecondValue(nv);
            }
        }
        return sy + 10f;
    }

    private float drawModeList(ModeProperty v, int mx, int my, float x, float y, float w) {
        FONT_SEMIBOLD.draw(v.getName(), x, y, SET_LBL.getRGB());
        float rowH = 18f;
        float rowY = y + 13f;
        boolean isOpen = listExpanded.getOrDefault(v.getName(), false);
        boolean rowHov = inRect(mx, my, x, rowY, w, rowH);
        RoundedUtils.drawRoundedRectRise(x, rowY, w, rowH, 4f, (rowHov ? new Color(28, 31, 46, 220) : new Color(20, 22, 34, 200)).getRGB(), false, false, false, false);
        FONT_REGULAR.draw(String.valueOf(v.getValue()) + ": " + v.getModeString(), x + 6f, rowY + 3f, CAT_TXT_A.getRGB());
        String arrow = isOpen ? "\u25B4" : "\u25BE";
        FONT_REGULAR.draw(arrow, x + w - FONT_REGULAR.getStringWidth(arrow) - 6f, rowY + 3f, CAT_TXT.getRGB());

        if (clickL && rowHov) listExpanded.put(v.getName(), !isOpen);
        float cy = rowY + rowH;
        if (isOpen) {
            return drawModeOptions(v, mx, my, x, cy, w);
        }
        return cy + 2f;
    }

    private float drawModeOptions(ModeProperty v, int mx, int my, float x, float cy, float w) {
        String[] modes = v.getModes();
        for (int i = 0; i < modes.length; i++) {
            boolean sel = v.getValue() == i;
            boolean hov = inRect(mx, my, x, cy, w, 17f);
            Color bg = sel ? ACCENT_DIM : (hov ? CAT_HOV : new Color(16, 18, 27, 215));
            RoundedUtils.drawRoundedRectRise(x, cy, w, 17f, 4f, bg.getRGB(), false, false, false, false);
            FONT_REGULAR.draw(modes[i], x + 10f, cy + 2.5f, (sel || hov) ? CAT_TXT_A.getRGB() : CAT_TXT.getRGB());
            if (clickL && hov) {
                v.setValue(i);
                listExpanded.put(v.getName(), false);
                clickL = false;
            }
            cy += 18f;
        }
        return cy + 2f;
    }

    private float drawColorValue(ColorProperty v, float x, float y, float w) {
        FONT_SEMIBOLD.draw(v.getName(), x, y, SET_LBL.getRGB());
        float swX = x + w - 14f;
        int rgb = v.getValue();
        Color col = new Color(rgb);
        RoundedUtils.drawRound(swX, y - 1f, 12f, 11f, 3f, col);
        return y + 14f;
    }

    private float drawTextValue(TextProperty v, float x, float y, float w) {
        FONT_SEMIBOLD.draw(v.getName() + ":", x, y, SET_LBL.getRGB());
        float by = y + 11f;
        RoundedUtils.drawRoundedRectRise(x, by, w, 13f, 3f, SRCH_BG.getRGB(), false, false, false, false);
        RoundedUtils.drawRoundOutline(x, by, w, 13f, 3f, 1f, SRCH_BG, SRCH_BOR);
        String s = v.getValue();
        FONT_REGULAR.draw(s.isEmpty() ? "..." : s, x + 4f, by + 2f, s.isEmpty() ? SRCH_HINT.getRGB() : SET_LBL.getRGB());
        return by + 15f;
    }

    private void drawSliderTrack(float x, float y, float w, float fillX) {
        RoundedUtils.drawRoundedRectRise(x, y, w, 4f, 2f, SL_TRACK.getRGB(), false, false, false, false);
        if (fillX > x) {
            RoundedUtils.drawRoundedRectRise(x, y, fillX - x, 4f, 2f, SL_FILL.getRGB(), false, false, false, false);
        }
        int cx = (int) fillX;
        float cy = y + 2f;
        RoundedUtils.drawRoundedRectRise(cx - 3f, cy - 3.5f, 7f, 7f, 3.5f, Color.WHITE.getRGB(), false, false, false, false);
        RoundedUtils.drawRoundedRectRise(cx - 2f, cy - 2.5f, 5f, 5f, 2.5f, SL_TRACK.getRGB(), false, false, false, false);
    }

    private void drawRangeTrack(float x, float y, float w, float x1, float x2) {
        RoundedUtils.drawRoundedRectRise(x, y, w, 4f, 2f, SL_TRACK.getRGB(), false, false, false, false);
        if (x2 > x1) {
            RoundedUtils.drawRoundedRectRise(x1, y, x2 - x1, 4f, 2f, SL_FILL.getRGB(), false, false, false, false);
        }
        float cy = y + 2f;
        drawKnob(x1, cy);
        drawKnob(x2, cy);
    }

    private void drawKnob(float kx, float cy) {
        int cx = (int) kx;
        RoundedUtils.drawRoundedRectRise(cx - 3f, cy - 3.5f, 7f, 7f, 3.5f, Color.WHITE.getRGB(), false, false, false, false);
        RoundedUtils.drawRoundedRectRise(cx - 2f, cy - 2.5f, 5f, 5f, 2.5f, SL_TRACK.getRGB(), false, false, false, false);
    }

    private void drawTargetsPanel(int mx, int my, float cx, float cy, float cw, float ch) {
        float x = cx + 14f;
        float yy = cy + 14f;
        float w = cw - 28f;

        FONT_SEMIBOLD.draw("Entity Targets", x, yy, Color.WHITE.getRGB());
        yy += FONT_SEMIBOLD.getFontHeight() + 8f;
        RenderUtil.drawRect(cx + 8f, yy, cx + cw - 8f, yy + 1, DIVIDER.getRGB());
        yy += 10f;

        for (String target : TARGET_NAMES) {
            boolean isOn = isTargetOn(target);
            float tw = 22f;
            float th = 13f;
            float tx = x + w - tw;
            float ty = yy;

            RoundedUtils.drawRoundedRectRise(tx, ty, tw, th, th / 2f, isOn ? TOG_ON.getRGB() : TOG_OFF.getRGB(), false, false, false, false);
            float tr = th / 2f - 1.5f;
            float dmX = isOn ? tx + tw - tr * 2f - 1.5f : tx + 1.5f;
            RoundedUtils.drawRoundedRectRise(dmX, ty + 1.5f, tr * 2f, th - 3f, tr, Color.WHITE.getRGB(), false, false, false, false);

            FONT_REGULAR.draw(target, x, yy + 1f, SET_LBL.getRGB());

            if (clickL && inRect(mx, my, tx - 3f, ty - 3f, tw + 6f, th + 6f)) {
                toggleTargets.put(target, !isOn);
            }
            yy += 22f;
        }
    }

    private boolean isTargetOn(String name) {
        if (!toggleTargets.containsKey(name)) {
            toggleTargets.put(name, true);
            return true;
        }
        return toggleTargets.get(name);
    }

    private void drawScrollbar(float x, float y, float h, int scroll, int max) {
        if (max <= 0) return;
        float sbX = x - 1f;
        RoundedUtils.drawRoundedRectRise(sbX, y, SBW, h, SBW / 2f, new Color(18, 20, 31, 120).getRGB(), false, false, false, false);

        float th = Math.max(20f, h * h / (h + max));
        float ty = y + (scroll / (float) max) * (h - th);
        RoundedUtils.drawRoundedRectRise(sbX, ty, SBW, th, SBW / 2f, new Color(60, 130, 255, 200).getRGB(), false, false, false, false);
    }

    private void drawKeybindOverlay() {
        ScaledResolution sr = new ScaledResolution(mc);
        float sw = sr.getScaledWidth();
        float sh = sr.getScaledHeight();
        RenderUtil.drawRect(0f, 0f, sw, sh, OVR_DIM.getRGB());

        String modName = bindMod == null ? "" : bindMod.getName();
        int curKey = bindMod == null ? 0 : bindMod.getKey();
        boolean hasBind = curKey != 0;

        String line1 = "Binding: " + modName;
        String line2 = "Press a key  -  Click to clear  -  ESC to cancel";
        float line1W = FONT_MOD_NAME.getStringWidth(line1);
        float line2W = FONT_REGULAR.getStringWidth(line2);
        float boxW = Math.max(line1W, line2W) + 44f;
        float boxH = 58f;
        float bx = (sw - boxW) / 2f;
        float by = (sh - boxH) / 2f;

        RoundedUtils.drawRoundedRectRise(bx, by, boxW, boxH, 10f, OVR_BOX.getRGB(), false, false, false, false);
        RoundedUtils.drawRoundOutline(bx, by, boxW, boxH, 10f, 1.5f, OVR_BOX, ACCENT);

        if (hasBind) {
            String badgeTxt = "Currently: " + KeyBindUtil.getKeyName(curKey);
            float badgeW = FONT_REGULAR.getStringWidth(badgeTxt) + 10f;
            float badgeX = bx + boxW - badgeW - 10f;
            float badgeY = by + 7f;
            RoundedUtils.drawRoundedRectRise(badgeX, badgeY, badgeW, 12f, 4f, new Color(30, 60, 140, 160).getRGB(), false, false, false, false);
            FONT_REGULAR.draw(badgeTxt, badgeX + 5f, badgeY + 1.5f, new Color(140, 185, 255, 220).getRGB());
        }

        FONT_MOD_NAME.draw(line1, bx + (boxW - line1W) / 2f, by + 12f, CAT_TXT_A.getRGB());

        RenderUtil.drawRect(bx + 14f, by + 26f, bx + boxW - 14f, by + 27f, new Color(30, 38, 65, 200).getRGB());

        String part1 = "Press a key  -  ";
        String part2 = "Click to clear";
        String part3 = "  -  ESC to cancel";
        float p1W = FONT_REGULAR.getStringWidth(part1);
        float p2W = FONT_REGULAR.getStringWidth(part2);
        float totalW = FONT_REGULAR.getStringWidth(line2);
        float textX = bx + (boxW - totalW) / 2f;
        float textY = by + 32f;
        FONT_REGULAR.draw(part1, textX, textY, CAT_TXT.getRGB());
        FONT_REGULAR.draw(part2, textX + p1W, textY, ACCENT.getRGB());
        FONT_REGULAR.draw(part3, textX + p1W + p2W, textY, CAT_TXT.getRGB());
    }

    private String getCategoryNameFor(Module mod) {
        String cat = getCategoryName(mod);
        return cat == null ? "Misc" : cat;
    }

    private List<Module> getMods() {
        List<Module> result = new ArrayList<>();
        ModuleManager mm = Miau.moduleManager;
        if (mm != null && mm.modules != null) {
            for (Module mod : mm.modules.values()) {
                if (selCat != null && !getCategoryName(mod).equals(selCat)) continue;
                if (!srchTxt.isEmpty() && !mod.getName().toLowerCase().contains(srchTxt.toLowerCase())) continue;
                result.add(mod);
            }
        }
        return result;
    }

    private String getCategoryName(Module mod) {
        String pkg = mod.getClass().getPackage().getName();
        if (pkg.startsWith("miau.module.modules.")) {
            String cat = pkg.substring("miau.module.modules.".length());
            int dot = cat.indexOf('.');
            if (dot >= 0) cat = cat.substring(0, dot);
            return capitalize(cat);
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private float calcInlineH(Module mod) {
        float h = 6f + 16f + 7f + 1f + 7f;
        for (Property<?> v : mod.getValues()) {
            if (!v.isVisible()) continue;
            if (v instanceof DragProperty) continue;
            if (v instanceof BooleanProperty) h += 15f + 5f;
            else if (v instanceof FloatProperty) h += 22f + 5f;
            else if (v instanceof IntProperty) h += 22f + 5f;
            else if (v instanceof PercentProperty) h += 22f + 5f;
            else if (v instanceof ModeProperty) {
                float base = 13f + 18f + 2f + 5f;
                if (listExpanded.getOrDefault(v.getName(), false)) {
                    base += ((ModeProperty) v).getModes().length * 18f;
                }
                h += base;
            } else if (v instanceof ColorProperty) h += 14f + 5f;
            else if (v instanceof TextProperty) h += 26f + 5f;
            else h += 13f + 5f;
        }
        return h;
    }

    private float dt() {
        long now = System.currentTimeMillis();
        float dt = ((now - lastFrameMs) / 16.67f);
        if (dt < 0.5f) dt = 0.5f;
        if (dt > 3f) dt = 3f;
        return dt;
    }

    private float easeOut(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private float easeOutQ(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private String trim(float v) {
        float r = Math.round(v * 100.0f) / 100.0f;
        if (r == Math.floor(r)) return String.valueOf((long) r);
        return String.valueOf(r);
    }

    private Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl, 255);
    }

    private boolean inRect(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean inSlider(int mx, int my, float x, float y, float w) {
        return mx >= x - 6 && mx <= x + w + 6 && my >= y - 5 && my <= y + 9;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindMod != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                bindMod = null;
            } else if (keyCode == Keyboard.KEY_BACK) {
                bindMod.setKey(0);
                bindMod = null;
            } else {
                bindMod.setKey(keyCode);
                bindMod = null;
            }
            bindOverlayOpenTime = 0L;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (srchOn) {
            if (keyCode == Keyboard.KEY_BACK) {
                if (!srchTxt.isEmpty()) srchTxt = srchTxt.substring(0, srchTxt.length() - 1);
            } else if (keyCode == Keyboard.KEY_RETURN) {
                srchOn = false;
            } else if (typedChar >= 32) {
                srchTxt += typedChar;
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (bindMod != null && (mouseButton == 0 || mouseButton == 1)) {
            if (System.currentTimeMillis() - bindOverlayOpenTime >= 1000L && bindMod != null) {
                bindMod.setKey(0);
                bindMod = null;
                return;
            }
            return;
        }

        if (mouseButton == 0 && mouseX >= gX && mouseX <= gX + gW && mouseY >= gY && mouseY <= gY + HDR_H) {
            dragging = true;
            dDX = mouseX - gX;
            dDY = mouseY - gY;
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        int ex = Mouse.getEventX() * width / mc.displayWidth;
        int ey = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        if (ex >= gX + SW && ex <= gX + gW && ey >= gY && ey <= gY + gH) {
            scrollVel += -(wheel / 120) * 22f;
        }
    }
}