package miau.ui.clickgui.augustus;

import miau.Miau;
import miau.module.Module;
import miau.module.modules.render.HUD;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
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

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class AugustusClickGui extends GuiScreen {
    
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    public static Color themeColor = new Color(0, 233, 255); // #00E9FF
    public static String colorMode = "Custom"; // "Custom" or "HUD"
    public static int customR = 0;
    public static int customG = 233;
    public static int customB = 255;
    
    public static final Color PALETTE_BG = new Color(0x0B0F15);
    public static final Color PALETTE_PANEL = new Color(0x121821);
    public static final Color PALETTE_CARD = new Color(0x171D27);
    public static final Color PALETTE_CARD_HOVER = new Color(0x1C2430);
    public static final Color PALETTE_ACCENT = new Color(0x00D8FF);
    public static final Color PALETTE_TEXT = new Color(0xFFFFFF);
    public static final Color PALETTE_SECONDARY = new Color(0xA9B3C3);
    public static final Color PALETTE_DANGER = new Color(0xFF5A5A);
    public static final Color PALETTE_SUCCESS = new Color(0x46E07A);
    public static final Color PALETTE_BORDER = new Color(255, 255, 255, 20);
    public static final Color PALETTE_GLOW = new Color(0, 216, 255, 51);
    
    private static final String[] CATEGORIES = {"Combat", "Movement", "Player", "Render", "Ghost", "Network", "Minigames", "Misc"};
    
    private boolean showSettingsPopup = false;
    private String draggingSlider = null; // "R", "G", "B"
    
    private float panelX = 50f;
    private float panelY = 20f;
    private float panelWidth = 620f;
    private float panelHeight = 380f;
    private float categoryScroll = 0f;
    private float moduleScroll = 0f;
    private float valueScroll = 0f;
    private float categoryScrollTarget = 0f;
    private float moduleScrollTarget = 0f;
    private float valueScrollTarget = 0f;
    private boolean isDragging = false;
    private float dragOffX = 0f;
    private float dragOffY = 0f;
    private String currentCategory = "Render";
    private String currentModule = null;
    private float modulePanelWidth = 170f;
    private boolean isResizingPanel = false;
    private float resizeStartX = 0f;
    private float resizeStartWidth = 0f;
    private AbstractValueComponent draggingComponent = null;
    private float dragBarX = 0f;
    private float dragBarX2 = 0f;
    
    private final Map<Module, List<AbstractValueComponent>> componentCache = new HashMap<>();
    private final List<TargetEntry> targetEntries = new ArrayList<>();
    
    private final Map<String, AnimFloat> categoryHover = new HashMap<>();
    private final Map<String, AnimFloat> moduleHover = new HashMap<>();
    private final AnimFloat closeHover = new AnimFloat(0f);
    private final AnimFloat settingHover = new AnimFloat(0f);
    private final AnimFloat profileHover = new AnimFloat(0f);
    private final AnimFloat underlinePos = new AnimFloat(0f);
    private final AnimFloat underlineWidth = new AnimFloat(0f);
    private final AnimFloat openAnim = new AnimFloat(0f);
    
    public AugustusClickGui() {
        targetEntries.add(new TargetEntry("Players", () -> EntityTargets.player, () -> EntityTargets.player = !EntityTargets.player));
        targetEntries.add(new TargetEntry("Mobs", () -> EntityTargets.mob, () -> EntityTargets.mob = !EntityTargets.mob));
        targetEntries.add(new TargetEntry("Animals", () -> EntityTargets.animal, () -> EntityTargets.animal = !EntityTargets.animal));
        targetEntries.add(new TargetEntry("Invisible", () -> EntityTargets.invisible, () -> EntityTargets.invisible = !EntityTargets.invisible));
        targetEntries.add(new TargetEntry("Dead", () -> EntityTargets.dead, () -> EntityTargets.dead = !EntityTargets.dead));
    }
    
    @Override
    public void initGui() {
        isDragging = false;
        isResizingPanel = false;
        openAnim.value = 0f;
        openAnim.current = 0f;
    }
    
    @Override
    public void onGuiClosed() {
        isDragging = false;
        isResizingPanel = false;
        showSettingsPopup = false;
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    
    private float getTitleHeight() {
        return 50f;
    }
    
    private float endX() {
        return panelX + panelWidth;
    }
    
    private float endY() {
        return panelY + panelHeight;
    }
    
    private float getCategoryContentWidth() {
        Font fontCategory = FontRepository.getFont("augustus", 24f);
        float w = 0f;
        for (String category : CATEGORIES) {
            w += fontCategory.getStringWidth(category) + 22f;
        }
        w += fontCategory.getStringWidth("Targets") + 22f;
        return w;
    }
    
    private void clampCategoryScroll() {
        float availW = (endX() - 40f) - (panelX + 145f);
        float contentW = getCategoryContentWidth();
        float maxScroll = Math.min(0f, availW - contentW);
        if (categoryScrollTarget > 0f) categoryScrollTarget = 0f;
        if (categoryScrollTarget < maxScroll) categoryScrollTarget = maxScroll;
        if (categoryScroll > 0f) categoryScroll = 0f;
        if (categoryScroll < maxScroll) categoryScroll = maxScroll;
    }
    
    private void setCategory(String category) {
        currentCategory = category;
        moduleScroll = 0f;
        moduleScrollTarget = 0f;
        valueScroll = 0f;
        valueScrollTarget = 0f;
        currentModule = null;
    }
    
    private void setModule(String moduleName) {
        currentModule = moduleName;
        valueScroll = 0f;
        valueScrollTarget = 0f;
    }

    private List<Module> getFilteredModules() {
        List<Module> filteredModules = new ArrayList<>();
        if (currentCategory == null) return filteredModules;
        for (Module mod : Miau.moduleManager.modules.values()) {
            if (mod != null && mod.getCategory() != null) {
                if (String.valueOf(mod.getCategory()).equalsIgnoreCase(currentCategory)) {
                    filteredModules.add(mod);
                }
            }
        }
        return filteredModules;
    }
    
    private List<AbstractValueComponent> getComponents(Module module) {
        List<AbstractValueComponent> components = componentCache.get(module);
        if (components == null) {
            components = new ArrayList<>();
            if (module.getValues() != null) {
                for (Property<?> prop : module.getValues()) {
                    if (prop instanceof BooleanProperty) {
                        components.add(new BoolValueComponent((BooleanProperty) prop));
                    } else if (prop instanceof IntProperty) {
                        components.add(new IntValueComponent((IntProperty) prop));
                    } else if (prop instanceof FloatProperty) {
                        components.add(new FloatValueComponent((FloatProperty) prop));
                    } else if (prop instanceof ModeProperty) {
                        components.add(new ListValueComponent((ModeProperty) prop));
                    } else if (prop.getClass().getName().toLowerCase().contains("color")) {
                        components.add(new ColorValueComponent(prop));
                    }
                }
            }
            componentCache.put(module, components);
        }
        return components;
    }
    
    private void startScissorBox(float x, float y, float x2, float y2) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        int screenHeight = mc.displayHeight;
        int renderX = (int) (x * scale);
        int renderY = (int) (screenHeight - (y2 * scale));
        int renderWidth = (int) ((x2 - x) * scale);
        int renderHeight = (int) ((y2 - y) * scale);
        GL11.glScissor(renderX, renderY, Math.max(0, renderWidth), Math.max(0, renderHeight));
    }
    
    private void updateThemeColor() {
        if ("HUD".equalsIgnoreCase(colorMode)) {
            try {
                HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
                if (hud != null) {
                    themeColor = hud.getColor(System.currentTimeMillis());
                }
            } catch (Exception ignored) {}
        } else {
            themeColor = new Color(customR, customG, customB);
        }
    }
    
    private static float easeTo(float current, float target, float partialTicks) {
        float factor = 1f - (float) Math.pow(0.03, partialTicks);
        float next = current + (target - current) * factor;
        if (Math.abs(target - next) < 0.01f) next = target;
        return next;
    }
    
    private static Color blend(Color a, Color b, float t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateThemeColor();

        openAnim.value = 1f;
        openAnim.current = easeTo(openAnim.current, openAnim.value, partialTicks);
        float open = openAnim.current;

        float openScale = 0.93f + 0.07f * open;
        float cx = panelX + panelWidth / 2f;
        float cy = panelY + panelHeight / 2f;
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0);
        GL11.glScalef(openScale, openScale, 1f);
        GL11.glTranslatef(-cx, -cy, 0);

        if (isDragging) {
            panelX = mouseX - dragOffX;
            panelY = mouseY - dragOffY;
        }
        
        if (isResizingPanel) {
            float delta = mouseX - resizeStartX;
            modulePanelWidth = resizeStartWidth + delta;
            if (modulePanelWidth < 140f) modulePanelWidth = 140f;
            if (modulePanelWidth > panelWidth - 180f) modulePanelWidth = panelWidth - 180f;
        }
        
        categoryScroll = easeTo(categoryScroll, categoryScrollTarget, partialTicks);
        moduleScroll = easeTo(moduleScroll, moduleScrollTarget, partialTicks);
        valueScroll = easeTo(valueScroll, valueScrollTarget, partialTicks);
        
        float ex = endX();
        float ey = endY();
        float th = getTitleHeight();
        
        RoundedUtils.drawRound(0f, 0f, width, height, 0f, new Color(0x0B0F15, true).getRGB());
        RenderUtil.drawRect(0f, 0f, width, height, new Color(0x0B0F15).getRGB() & 0x00FFFFFF | 0x66000000);
        
        RoundedUtils.drawRound(panelX + 2f, panelY + 4f, panelWidth + 4f, panelHeight + 4f, 14f, new Color(0, 0, 0, 70).getRGB());
        RoundedUtils.drawRound(panelX, panelY, panelWidth, panelHeight, 14f, true, new Color(0x121821, true).getRGB());
        RoundedUtils.drawRoundOutline(panelX, panelY, panelWidth, panelHeight, 14f, 1f, new Color(0, 0, 0, 0), PALETTE_BORDER);
        
        RoundedUtils.drawGradientHorizontal(panelX + 5f, panelY + th - 1.5f, panelWidth - 10f, 1.5f, 0.75f,
                new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 0),
                themeColor);
        
        Font fontLarge = FontRepository.getFont("augustus", 46f);
        fontLarge.draw("CLICKGUI", panelX + 10, panelY + 4, new Color(255, 255, 255).getRGB(), false);
        
        float closeX = ex - 26f;
        float closeY = panelY + 12f;
        boolean hoverClose = isHover(mouseX, mouseY, closeX - 6, closeY - 6, closeX + 20, closeY + 20);
        closeHover.value = hoverClose ? 1f : 0f;
        closeHover.current = easeTo(closeHover.current, closeHover.value, partialTicks);
        RoundedUtils.drawRound(closeX - 5f, closeY - 5f, 24f, 24f, 7f,
                blend(PALETTE_CARD, PALETTE_DANGER, closeHover.current * 0.85f));
        Font fontMedium = FontRepository.getFont("augustus", 22f);
        fontMedium.draw("X", closeX + 1, closeY - 1,
                blend(new Color(160, 160, 160), new Color(255, 255, 255), closeHover.current).getRGB(), false);
        
        Font fontCategory = FontRepository.getFont("augustus", 24f);
        float catAreaStart = panelX + 145f;
        float catAreaEnd = ex - 40f;
        float catPanelY = panelY + 12f;
        
        startScissorBox(catAreaStart, panelY, catAreaEnd, panelY + th);
        
        float catPos = categoryScroll;
        for (String category : CATEGORIES) {
            float sw = fontCategory.getStringWidth(category);
            float sx = catAreaStart + catPos;
            boolean active = currentCategory != null && currentCategory.equalsIgnoreCase(category);
            boolean hover = isHover(mouseX, mouseY, sx - 6, catPanelY - 6, sx + sw + 6, catPanelY + fontCategory.getFontHeight() + 6);
            
            AnimFloat anim = categoryHover.computeIfAbsent(category, k -> new AnimFloat(0f));
            anim.value = hover ? 1f : 0f;
            anim.current = easeTo(anim.current, anim.value, partialTicks);
            
            RoundedUtils.drawRound(sx - 6f, catPanelY - 3f, sw + 12f, fontCategory.getFontHeight() + 6f, 6f,
                    blend(new Color(255, 255, 255, 8), themeColor, Math.max(active ? 0.9f : 0f, anim.current * 0.55f)));
            
            Color color = active ? new Color(255, 255, 255) : blend(PALETTE_SECONDARY, PALETTE_TEXT, anim.current);
            fontCategory.draw(category, sx, catPanelY, color.getRGB(), false);
            
            if (active) {
                RenderUtil.fillCircle(sx + sw + 10, catPanelY + fontCategory.getFontHeight() / 2f + 1, 2.5f, 12, themeColor.getRGB());
            }
            catPos += sw + 22f;
        }
        
        String targetsLabel = "Targets";
        float tsw = fontCategory.getStringWidth(targetsLabel);
        float targetsStart = catAreaStart + catPos;
        boolean targetsActive = currentCategory == null;
        AnimFloat targetsAnim = categoryHover.computeIfAbsent("Targets", k -> new AnimFloat(0f));
        targetsAnim.value = isHover(mouseX, mouseY, targetsStart - 6, catPanelY - 6, targetsStart + tsw + 6, catPanelY + fontCategory.getFontHeight() + 6) ? 1f : 0f;
        targetsAnim.current = easeTo(targetsAnim.current, targetsAnim.value, partialTicks);
        RoundedUtils.drawRound(targetsStart - 6f, catPanelY - 3f, tsw + 12f, fontCategory.getFontHeight() + 6f, 6f,
                blend(new Color(255, 255, 255, 8), themeColor, Math.max(targetsActive ? 0.9f : 0f, targetsAnim.current * 0.55f)));
        fontCategory.draw(targetsLabel, targetsStart, catPanelY,
                targetsActive ? new Color(255, 255, 255).getRGB() : PALETTE_SECONDARY.getRGB(), false);
        if (targetsActive) {
            RenderUtil.fillCircle(targetsStart + tsw + 10, catPanelY + fontCategory.getFontHeight() / 2f + 1, 2.5f, 12, themeColor.getRGB());
        }
        
        float targetUnderlineX = 0f;
        float targetUnderlineW = 0f;
        if (currentCategory == null) {
            targetUnderlineX = targetsStart;
            targetUnderlineW = tsw;
        } else {
            float tmpPos = categoryScroll;
            for (String category : CATEGORIES) {
                float sw = fontCategory.getStringWidth(category);
                if (currentCategory.equalsIgnoreCase(category)) {
                    targetUnderlineX = catAreaStart + tmpPos;
                    targetUnderlineW = sw;
                    break;
                }
                tmpPos += sw + 22f;
            }
        }
        underlinePos.current = easeTo(underlinePos.current, targetUnderlineX, partialTicks);
        underlineWidth.current = easeTo(underlineWidth.current, targetUnderlineW, partialTicks);
        RoundedUtils.drawGradientHorizontal(underlinePos.current, panelY + th - 2.5f, underlineWidth.current, 2.5f, 1.25f,
                new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 0),
                themeColor);
        
        float catFadeW = 22f;
        float catFadeY = catPanelY - 5f;
        float catFadeH = fontCategory.getFontHeight() + 10f;
        float contentW = getCategoryContentWidth();
        float availW = catAreaEnd - catAreaStart;
        if (categoryScroll < -1f) {
            RoundedUtils.drawGradientHorizontal(catAreaStart, catFadeY, catFadeW, catFadeH, 0f,
                    new Color(18, 24, 33), new Color(18, 24, 33, 0));
        }
        if (contentW > availW) {
            RoundedUtils.drawGradientHorizontal(catAreaEnd - catFadeW, catFadeY, catFadeW, catFadeH, 0f,
                    new Color(18, 24, 33, 0), new Color(18, 24, 33));
        }
        
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        RoundedUtils.drawRound(panelX + modulePanelWidth - 1.5f, panelY + th, 3f, panelHeight - th, 1.5f,
                new Color(255, 255, 255, 14));
        
        float modulePanelStartY = panelY + th + 2f;
        
        if (currentCategory == null) {
            drawTargetsScreen(mouseX, mouseY, ex, ey, modulePanelStartY, panelY + th);
        } else {
            drawModulesScreen(mouseX, mouseY, ex, ey, modulePanelStartY, panelY + th, th);
        }
        
        float profileY = ey - 58f;
        boolean hoverProfile = isHover(mouseX, mouseY, panelX, profileY, panelX + modulePanelWidth, ey);
        profileHover.value = hoverProfile ? 1f : 0f;
        profileHover.current = easeTo(profileHover.current, profileHover.value, partialTicks);
        RoundedUtils.drawRound(panelX + 2f, profileY, modulePanelWidth - 4f, 56f, 10f,
                blend(PALETTE_CARD, new Color(0x1E2834), profileHover.current * 0.6f));
        RoundedUtils.drawRoundOutline(panelX + 2f, profileY, modulePanelWidth - 4f, 56f, 10f, 1f,
                new Color(0, 0, 0, 0), PALETTE_BORDER);
        RoundedUtils.drawGradientVertical(panelX + 2f, profileY, modulePanelWidth - 4f, 3f, 1.5f,
                new Color(0, 0, 0, 0), themeColor);
        
        if (mc.thePlayer != null) {
            RoundedUtils.drawRound(panelX + 8f, profileY + 10f, 36f, 36f, 8f, new Color(255, 255, 255, 12));
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            ScaledResolution sr = new ScaledResolution(mc);
            int scale = sr.getScaleFactor();
            int avX = (int) ((panelX + 8f) * scale);
            int avY = (int) (mc.displayHeight - (profileY + 46f) * scale);
            GL11.glScissor(avX, avY, (int) (36f * scale), (int) (36f * scale));
            mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect((int)(panelX + 8), (int)(profileY + 10), 8.0f, 8.0f, 8, 8, 36, 36, 64.0f, 64.0f);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glPopMatrix();
            RoundedUtils.drawRoundOutline(panelX + 8f, profileY + 10f, 36f, 36f, 8f, 1f, new Color(0, 0, 0, 0), PALETTE_BORDER);
            
            RenderUtil.fillCircle(panelX + 39f, profileY + 41f, 4f, 14, PALETTE_SUCCESS.getRGB());
            RoundedUtils.drawRoundOutline(panelX + 39f - 4f, profileY + 41f - 4f, 8f, 8f, 4f, 1f, new Color(0, 0, 0, 0), new Color(0, 0, 0, 80));
            
            Font fontSmall = FontRepository.getFont("augustus", 14f);
            fontSmall.draw(mc.thePlayer.getName(), panelX + 52, profileY + 8, new Color(240, 244, 250).getRGB(), false);
            
            Font rankFont = FontRepository.getFont("augustus", 9f);
            String rank = "PREMIUM";
            float rw = rankFont.getStringWidth(rank) + 7f;
            RoundedUtils.drawRound(panelX + 52f + fontSmall.getStringWidth(mc.thePlayer.getName()) + 6f, profileY + 10f, rw, 9f, 4.5f,
                    new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 40));
            rankFont.draw(rank, panelX + 52f + fontSmall.getStringWidth(mc.thePlayer.getName()) + 9.5f, profileY + 10f, themeColor.getRGB(), false);
            
            Font fontTiny = FontRepository.getFont("augustus", 11f);
            int mins = mc.thePlayer.ticksExisted / 1200;
            int secs = (mc.thePlayer.ticksExisted % 1200) / 20;
            fontTiny.draw(String.format("Session  %02d:%02d", mins, secs), panelX + 52, profileY + 22, PALETTE_SECONDARY.getRGB(), false);
            
            int fps = Minecraft.getDebugFPS();
            int ping = 0;
            try {
                if (mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
                    ping = mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
                }
            } catch (Exception ignored) {}
            long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576L;
            fontTiny.draw("FPS " + fps, panelX + 52, profileY + 34, blend(PALETTE_SUCCESS, themeColor, 0.35f).getRGB(), false);
            fontTiny.draw("Ping " + ping + "ms", panelX + 52 + 44f, profileY + 34, themeColor.getRGB(), false);
            fontTiny.draw("Mem " + usedMem + "MB", panelX + 52 + 44f + 66f, profileY + 34, PALETTE_SECONDARY.getRGB(), false);
        }

        float settingX = panelX + modulePanelWidth - 28f;
        float settingY = profileY + 14f;
        boolean hoverSetting = isHover(mouseX, mouseY, settingX - 4, settingY - 4, settingX + 18, settingY + 18);
        settingHover.value = hoverSetting ? 1f : 0f;
        settingHover.current = easeTo(settingHover.current, settingHover.value, partialTicks);
        Font fontIcon = FontRepository.getFont("augustus", 22f);
        RoundedUtils.drawRound(settingX - 3f, settingY - 3f, 22f, 22f, 7f,
                blend(PALETTE_CARD, themeColor, settingHover.current * 0.55f));
        fontIcon.draw("⚙", settingX + 2, settingY + 1,
                blend(new Color(200, 200, 200), Color.WHITE, settingHover.current).getRGB(), false);

        if (showSettingsPopup) {
            drawColorSettingsPopup(mouseX, mouseY, settingX, settingY);
        }

        if (open < 1f) {
            RenderUtil.drawRect(0f, 0f, width, height, new Color(0, 0, 0, (int) ((1f - open) * 120f)).getRGB());
        }

        GL11.glPopMatrix();
    }

    private void drawColorSettingsPopup(int mouseX, int mouseY, float settingX, float settingY) {
        float popX = panelX + modulePanelWidth + 10f;
        float popY = endY() - ( "Custom".equalsIgnoreCase(colorMode) ? 140f : 80f );
        float popW = 160f;
        float popH = "Custom".equalsIgnoreCase(colorMode) ? 130f : 70f;

        RoundedUtils.drawRound(popX, popY, popW, popH, 10f, new Color(0x11161E, true).getRGB());
        RoundedUtils.drawRoundOutline(popX, popY, popW, popH, 10f, 1f, new Color(0, 0, 0, 0), PALETTE_BORDER);

        Font fontSmall = FontRepository.getFont("augustus", 18f);

        float btn1X = popX + 10f;
        float btn1Y = popY + 10f;
        float btnW = 65f;
        float btnH = 20f;

        boolean activeCustom = "Custom".equalsIgnoreCase(colorMode);
        boolean hoverCustom = isHover(mouseX, mouseY, btn1X, btn1Y, btn1X + btnW, btn1Y + btnH);
        RoundedUtils.drawRound(btn1X, btn1Y, btnW, btnH, 6f,
                activeCustom ? themeColor : blend(PALETTE_CARD, PALETTE_CARD_HOVER, hoverCustom ? 0.8f : 0f));
        fontSmall.draw("Custom", btn1X + 8, btn1Y + 3, activeCustom ? Color.BLACK.getRGB() : Color.WHITE.getRGB(), false);

        float btn2X = popX + 85f;
        float btn2Y = popY + 10f;
        boolean activeHUD = "HUD".equalsIgnoreCase(colorMode);
        boolean hoverHUD = isHover(mouseX, mouseY, btn2X, btn2Y, btn2X + btnW, btn2Y + btnH);
        RoundedUtils.drawRound(btn2X, btn2Y, btnW, btnH, 6f,
                activeHUD ? themeColor : blend(PALETTE_CARD, PALETTE_CARD_HOVER, hoverHUD ? 0.8f : 0f));
        fontSmall.draw("HUD", btn2X + 18, btn2Y + 3, activeHUD ? Color.BLACK.getRGB() : Color.WHITE.getRGB(), false);

        if (activeCustom) {
            float sliderY = popY + 40f;
            drawColorSlider("R", customR, popX + 10f, sliderY, popW - 20f, new Color(255, 80, 80));
            drawColorSlider("G", customG, popX + 10f, sliderY + 25f, popW - 20f, new Color(80, 255, 80));
            drawColorSlider("B", customB, popX + 10f, sliderY + 50f, popW - 20f, new Color(80, 180, 255));

            if (draggingSlider != null && Mouse.isButtonDown(0)) {
                float barX = popX + 25f;
                float barW = popW - 40f;
                float pct = Math.max(0f, Math.min(1f, (mouseX - barX) / barW));
                int val = Math.round(pct * 255f);
                if ("R".equals(draggingSlider)) customR = val;
                if ("G".equals(draggingSlider)) customG = val;
                if ("B".equals(draggingSlider)) customB = val;
            }
        }
    }

    private void drawColorSlider(String label, int value, float x, float y, float width, Color col) {
        Font font = FontRepository.getFont("augustus", 16f);
        font.draw(label, x, y + 2, col.getRGB(), false);

        float barX = x + 15f;
        float barY = y + 3f;
        float barW = width - 25f;
        float barH = 6f;

        RoundedUtils.drawRound(barX, barY, barW, barH, 3f, new Color(0x0B0F15, true).getRGB());
        RoundedUtils.drawRoundOutline(barX, barY, barW, barH, 3f, 0.5f, new Color(0, 0, 0, 0), PALETTE_BORDER);

        float pct = value / 255f;
        float fillW = barW * pct;
        if (fillW > 1) {
            RoundedUtils.drawGradientHorizontal(barX, barY, fillW, barH, 3f, new Color(col.getRed(), col.getGreen(), col.getBlue(), 60), col);
        }

        float knobX = barX + fillW;
        RoundedUtils.drawRound(knobX - 3f, barY - 2.5f, 7f, 11f, 5.5f, Color.WHITE);
        RoundedUtils.drawRound(knobX - 3f, barY - 2.5f, 7f, 11f, 5.5f, new Color(255, 255, 255, 200));
        font.draw(String.valueOf(value), barX + barW + 5, y + 2, new Color(210, 214, 222).getRGB(), false);
    }

    private void drawTargetsScreen(int mouseX, int mouseY, float ex, float ey, float modulePanelStartY, float catPanelEndY) {
        Font fontMedium = FontRepository.getFont("augustus", 22f);
        float sepX = panelX + modulePanelWidth;
        
        RoundedUtils.drawRound(panelX + 2f, modulePanelStartY, sepX - panelX - 4f, ey - 58f - modulePanelStartY, 8f, PALETTE_CARD);
        fontMedium.draw("Targets", panelX + 10, modulePanelStartY + 8, themeColor.getRGB(), false);
        
        float itemY = modulePanelStartY + 35f;
        for (TargetEntry entry : targetEntries) {
            boolean active = entry.stateGetter.get();
            boolean hover = isHover(mouseX, mouseY, panelX + 6, itemY - 3, panelX + modulePanelWidth - 6, itemY + fontMedium.getFontHeight() + 3);
            AnimFloat anim = categoryHover.computeIfAbsent("entry_" + entry.label, k -> new AnimFloat(0f));
            anim.value = hover ? 1f : 0f;
            anim.current = easeTo(anim.current, anim.value, 1f / 20f);
            if (hover || active) {
                RoundedUtils.drawRound(panelX + 6f, itemY - 3f, modulePanelWidth - 12f, fontMedium.getFontHeight() + 6f, 6f,
                        active ? new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 22)
                               : blend(PALETTE_CARD, PALETTE_CARD_HOVER, anim.current));
            }
            Color color = active ? themeColor : blend(PALETTE_SECONDARY, PALETTE_TEXT, anim.current);
            fontMedium.draw(entry.label, panelX + 12, itemY, color.getRGB(), false);
            RoundedUtils.drawRound(panelX + modulePanelWidth - 22f, itemY + 5f, 12f, 12f, 4f,
                    active ? themeColor : new Color(40, 46, 56));
            if (active) {
                RenderUtil.fillCircle(panelX + modulePanelWidth - 16f, itemY + 11f, 2.5f, 10, Color.WHITE.getRGB());
            }
            itemY += fontMedium.getFontHeight() + 8f;
        }
        
        Font fontLarge = FontRepository.getFont("augustus", 32f);
        fontLarge.draw("Entity Targets", sepX + 15, catPanelEndY + 15, new Color(255, 255, 255).getRGB(), false);
        RoundedUtils.drawGradientHorizontal(sepX + 18f, catPanelEndY + fontLarge.getFontHeight() + 20f, 80f, 2f, 1f,
                new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 0), themeColor);
    }

    private void drawModulesScreen(int mouseX, int mouseY, float ex, float ey, float modulePanelStartY, float catPanelEndY, float th) {
        if (currentCategory == null) return;
        
        Font fontMedium = FontRepository.getFont("augustus", 22f);
        // FONT INTER BOLD CHO TÊN CÁC MODULE TRONG MỤC CHỌN
        Font fontModuleBold = FontRepository.getFont("inter-bold", 20f);
        
        float moduleItemHeight = fontMedium.getFontHeight() + 10f;
        float sepX = panelX + modulePanelWidth;
        
        RoundedUtils.drawRound(panelX + 2f, modulePanelStartY, sepX - panelX - 2f, ey - 58f - modulePanelStartY, 8f, PALETTE_CARD);
        
        List<Module> filteredModules = getFilteredModules();
        
        startScissorBox(panelX + 2, modulePanelStartY, sepX, ey - 58f);
        float currentY = modulePanelStartY + 8f + moduleScroll;
        for (Module module : filteredModules) {
            Color colorState = module.isEnabled() ? themeColor : blend(PALETTE_SECONDARY, PALETTE_TEXT, 0.15f);
            boolean isSelected = module.getName().equals(currentModule);
            boolean hover = isHover(mouseX, mouseY, panelX + 2, currentY - 3f, sepX, currentY + moduleItemHeight - 3f);
            
            AnimFloat anim = moduleHover.computeIfAbsent(module.getName(), k -> new AnimFloat(0f));
            anim.value = hover ? 1f : 0f;
            anim.current = easeTo(anim.current, anim.value, 1f / 20f);
            
            RoundedUtils.drawRound(panelX + 4f, currentY - 3f, sepX - panelX - 6f, moduleItemHeight - 4f, 7f,
                    isSelected ? new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 30)
                               : blend(new Color(255, 255, 255, 0), PALETTE_CARD_HOVER, anim.current * 0.9f));
            if (isSelected) {
                RoundedUtils.drawRoundOutline(panelX + 4f, currentY - 3f, sepX - panelX - 6f, moduleItemHeight - 4f, 7f, 1f,
                        new Color(0, 0, 0, 0), themeColor);
            }
            
            Color dotCol = module.isEnabled() ? themeColor : new Color(60, 68, 82);
            RenderUtil.fillCircle(panelX + 10f, currentY + moduleItemHeight / 2f - 1, 3f, 12, dotCol.getRGB());
            
            float nameX = panelX + 19f;
            // VẼ TÊN MODULE BẰNG FONT INTER BOLD
            fontModuleBold.draw(module.getName(), nameX, currentY + 1f, colorState.getRGB(), false);
            
            String keyName = KeyBindUtil.getKeyName(module.getKey());
            Font kfont = FontRepository.getFont("augustus", 12f);
            if (module.getKey() != 0) {
                float kw = kfont.getStringWidth(keyName) + 7f;
                float kx = panelX + modulePanelWidth - 34f - kw;
                RoundedUtils.drawRound(kx, currentY + 2f, kw, moduleItemHeight - 12f, 4f, new Color(255, 255, 255, 10));
                kfont.draw(keyName, kx + 3.5f, currentY + 4f, new Color(160, 168, 180).getRGB(), false);
            }
            
            currentY += moduleItemHeight;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        if (currentModule == null) return;
        
        Module module = null;
        for (Module mod : Miau.moduleManager.modules.values()) {
            if (mod.getName().equals(currentModule)) {
                module = mod;
                break;
            }
        }
        if (module == null) return;
        
        Font fontLarge = FontRepository.getFont("augustus", 32f);
        fontLarge.draw(module.getName(), sepX + 18f, catPanelEndY + 15f, new Color(255, 255, 255).getRGB(), false);
        RoundedUtils.drawGradientHorizontal(sepX + 20f, catPanelEndY + fontLarge.getFontHeight() + 20f, 60f, 2f, 1f,
                new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 0), themeColor);
        
        float valueStartY = catPanelEndY + fontLarge.getFontHeight() + 25f;
        
        startScissorBox(sepX + 4, valueStartY, ex - 4, ey - 6);
        
        List<AbstractValueComponent> components = getComponents(module);
        float totalValueY = 0f;
        for (AbstractValueComponent component : components) {
            if (component.value.isVisible()) {
                float valueY = valueStartY + totalValueY + valueScroll;
                component.drawValueName(sepX + 18f, valueY);
                totalValueY += component.drawValue(mouseX, mouseY, sepX + 18f, valueY, ex - 18f, ey - 6f);
            }
        }
        
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float ex = endX();
        float ey = endY();
        float th = getTitleHeight();
        
        if (mouseButton == 0 && isHover(mouseX, mouseY, ex - 30f, panelY + 8f, ex - 2f, panelY + 38f)) {
            mc.displayGuiScreen(null);
            return;
        }
        
        float profileY = ey - 58f;
        float settingX = panelX + modulePanelWidth - 28f;
        float settingY = profileY + 14f;

        if (mouseButton == 0 && isHover(mouseX, mouseY, settingX - 4, settingY - 4, settingX + 18, settingY + 18)) {
            showSettingsPopup = !showSettingsPopup;
            return;
        }

        if (showSettingsPopup) {
            float popX = panelX + modulePanelWidth + 10f;
            float popY = endY() - ( "Custom".equalsIgnoreCase(colorMode) ? 140f : 80f );
            float popW = 160f;

            if (mouseButton == 0) {
                if (isHover(mouseX, mouseY, popX + 10f, popY + 10f, popX + 75f, popY + 30f)) {
                    colorMode = "Custom";
                    return;
                }
                if (isHover(mouseX, mouseY, popX + 85f, popY + 10f, popX + 150f, popY + 30f)) {
                    colorMode = "HUD";
                    return;
                }

                if ("Custom".equalsIgnoreCase(colorMode)) {
                    float sliderY = popY + 40f;
                    float barX = popX + 25f;
                    float barW = popW - 40f;

                    if (isHover(mouseX, mouseY, barX, sliderY - 2, barX + barW, sliderY + 10)) {
                        draggingSlider = "R";
                        return;
                    }
                    if (isHover(mouseX, mouseY, barX, sliderY + 23, barX + barW, sliderY + 35)) {
                        draggingSlider = "G";
                        return;
                    }
                    if (isHover(mouseX, mouseY, barX, sliderY + 48, barX + barW, sliderY + 60)) {
                        draggingSlider = "B";
                        return;
                    }
                }
            }
        }
        
        Font fontCategory = FontRepository.getFont("augustus", 24f);
        float catAreaStart = panelX + 145f;
        float catPanelY = panelY + 12f;
        float catPos = categoryScroll;
        for (String category : CATEGORIES) {
            float sw = fontCategory.getStringWidth(category);
            float sx = catAreaStart + catPos;
            if (isHover(mouseX, mouseY, sx - 6, catPanelY - 6, sx + sw + 6, catPanelY + fontCategory.getFontHeight() + 6) && mouseButton == 0) {
                setCategory(category);
                return;
            }
            catPos += sw + 22f;
        }
        
        String targetsLabel = "Targets";
        float tsx = catAreaStart + catPos;
        float tex = tsx + fontCategory.getStringWidth(targetsLabel);
        if (isHover(mouseX, mouseY, tsx - 6, catPanelY - 6, tex + 6, catPanelY + fontCategory.getFontHeight() + 6) && mouseButton == 0) {
            setCategory(null);
            return;
        }
        
        if (mouseButton == 0 && isHover(mouseX, mouseY, panelX, panelY, ex, panelY + th)) {
            isDragging = true;
            dragOffX = mouseX - panelX;
            dragOffY = mouseY - panelY;
            return;
        }
        
        float sepX = panelX + modulePanelWidth;
        if (mouseButton == 0 && mouseX >= sepX - 6 && mouseX <= sepX + 6 && mouseY > panelY + th && mouseY < ey) {
            isResizingPanel = true;
            resizeStartX = mouseX;
            resizeStartWidth = modulePanelWidth;
            return;
        }
        
        if (currentCategory == null) {
            handleTargetsClick(mouseX, mouseY, mouseButton, panelY + th, ex, ey);
        } else {
            handleModuleClick(mouseX, mouseY, mouseButton, panelY + th, th, ex, ey);
        }
    }
    
    private void handleTargetsClick(int mouseX, int mouseY, int mouseButton, float catPanelEndY, float ex, float ey) {
        Font fontMedium = FontRepository.getFont("augustus", 22f);
        float modulePanelStartY = panelY + getTitleHeight() + 2;
        float itemY = modulePanelStartY + 35f;
        for (TargetEntry entry : targetEntries) {
            if (mouseButton == 0 && isHover(mouseX, mouseY, panelX + 6, itemY - 3, panelX + modulePanelWidth - 6, itemY + fontMedium.getFontHeight() + 3)) {
                entry.toggle.toggle();
                return;
            }
            itemY += fontMedium.getFontHeight() + 8f;
        }
    }
    
    private void handleModuleClick(int mouseX, int mouseY, int mouseButton, float catPanelEndY, float th, float ex, float ey) {
        if (currentCategory == null) return;
        
        Font fontMedium = FontRepository.getFont("augustus", 22f);
        float modulePanelStartY = panelY + th + 2f;
        float moduleItemHeight = fontMedium.getFontHeight() + 10f;
        float sepX = panelX + modulePanelWidth;
        
        List<Module> filteredModules = getFilteredModules();
        
        if (isHover(mouseX, mouseY, panelX + 2, modulePanelStartY, sepX, ey - 58f)) {
            float currentY = modulePanelStartY + 8f + moduleScroll;
            for (Module module : filteredModules) {
                if (isHover(mouseX, mouseY, panelX + 2, currentY - 3f, sepX, currentY + moduleItemHeight - 3f)) {
                    if (mouseButton == 0) {
                        module.toggle();
                    } else if (mouseButton == 1) {
                        setModule(module.getName());
                    }
                    return;
                }
                currentY += moduleItemHeight;
            }
        }
        
        if (currentModule == null) return;
        
        Module module = null;
        for (Module mod : Miau.moduleManager.modules.values()) {
            if (mod.getName().equals(currentModule)) {
                module = mod;
                break;
            }
        }
        if (module == null) return;
        
        Font fontLarge = FontRepository.getFont("augustus", 32f);
        float valueStartY = catPanelEndY + fontLarge.getFontHeight() + 25f;
        List<AbstractValueComponent> components = getComponents(module);
        float totalValueY = 0f;
        for (AbstractValueComponent component : components) {
            if (component.value.isVisible()) {
                float valueY = valueStartY + totalValueY + valueScroll;
                float labelX = sepX + 18f;
                float valueX2 = ex - 18f;
                float inc = component.mouseClicked(mouseX, mouseY, labelX, valueY, valueX2, ey - 6f, mouseButton);
                if (inc == -1f) {
                    draggingComponent = component;
                    dragBarX = component.getBarStartX(labelX);
                    dragBarX2 = valueX2;
                    return;
                }
                if (inc == 0f) return;
                totalValueY += inc;
            }
        }
    }
    
    @Override
    public void handleMouseInput() {
        try {
            super.handleMouseInput();
            int wheel = Mouse.getEventDWheel() != 0 ? Mouse.getEventDWheel() : Mouse.getDWheel();
            if (wheel != 0) {
                int mouseX = Mouse.getEventX() * width / mc.displayWidth;
                int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
                float sepX = panelX + modulePanelWidth;
                
                if (isHover(mouseX, mouseY, panelX, panelY, endX(), panelY + getTitleHeight())) {
                    categoryScrollTarget += (wheel > 0 ? 30f : -30f);
                    clampCategoryScroll();
                } else if (isHover(mouseX, mouseY, panelX, panelY, sepX, endY())) {
                    moduleScrollTarget += (wheel > 0 ? 25f : -25f);
                    
                    Font fontMedium = FontRepository.getFont("augustus", 22f);
                    float moduleItemHeight = fontMedium.getFontHeight() + 10f;
                    List<Module> filteredModules = getFilteredModules();
                    
                    float totalHeight = filteredModules.size() * moduleItemHeight + 16f;
                    float visibleHeight = (endY() - 58f) - (panelY + getTitleHeight() + 2f);
                    float maxScroll = Math.min(0f, visibleHeight - totalHeight);
                    
                    if (moduleScrollTarget > 0f) moduleScrollTarget = 0f;
                    if (moduleScrollTarget < maxScroll) moduleScrollTarget = maxScroll;
                } else if (isHover(mouseX, mouseY, sepX, panelY, endX(), endY())) {
                    valueScrollTarget += (wheel > 0 ? 25f : -25f);
                    if (valueScrollTarget > 0f) valueScrollTarget = 0f;
                }
            }
        } catch (Exception ignored) {}
    }
    
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (draggingComponent != null && clickedMouseButton == 0) {
            draggingComponent.updateDrag(mouseX, dragBarX, dragBarX2);
        }
    }
    
    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            isDragging = false;
            isResizingPanel = false;
            draggingComponent = null;
            draggingSlider = null;
        }
    }
    
    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
        }
    }
    
    private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
        return mx >= x && mx <= x2 && my >= y && my <= y2;
    }

    private static int getIntPropMin(IntProperty v) {
        try {
            java.lang.reflect.Method m = v.getClass().getMethod("getMin");
            return (int) m.invoke(v);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("getMinimum");
                return (int) m.invoke(v);
            } catch (Exception e2) {
                try {
                    java.lang.reflect.Field f = v.getClass().getDeclaredField("min");
                    f.setAccessible(true);
                    return f.getInt(v);
                } catch (Exception e3) {
                    return 0;
                }
            }
        }
    }

    private static int getIntPropMax(IntProperty v) {
        try {
            java.lang.reflect.Method m = v.getClass().getMethod("getMax");
            return (int) m.invoke(v);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("getMaximum");
                return (int) m.invoke(v);
            } catch (Exception e2) {
                try {
                    java.lang.reflect.Field f = v.getClass().getDeclaredField("max");
                    f.setAccessible(true);
                    return f.getInt(v);
                } catch (Exception e3) {
                    return 100;
                }
            }
        }
    }

    private static float getFloatPropMin(FloatProperty v) {
        try {
            java.lang.reflect.Method m = v.getClass().getMethod("getMin");
            return (float) m.invoke(v);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("getMinimum");
                return (float) m.invoke(v);
            } catch (Exception e2) {
                try {
                    java.lang.reflect.Field f = v.getClass().getDeclaredField("min");
                    f.setAccessible(true);
                    return f.getFloat(v);
                } catch (Exception e3) {
                    return 0f;
                }
            }
        }
    }

    private static float getFloatPropMax(FloatProperty v) {
        try {
            java.lang.reflect.Method m = v.getClass().getMethod("getMax");
            return (float) m.invoke(v);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("getMaximum");
                return (float) m.invoke(v);
            } catch (Exception e2) {
                try {
                    java.lang.reflect.Field f = v.getClass().getDeclaredField("max");
                    f.setAccessible(true);
                    return f.getFloat(v);
                } catch (Exception e3) {
                    return 100f;
                }
            }
        }
    }
    
    // ========== VALUE COMPONENTS ==========
    
    public abstract static class AbstractValueComponent {
        public Property<?> value;
        
        protected AbstractValueComponent(Property<?> value) {
            this.value = value;
        }
        
        protected Font getFont() {
            return FontRepository.getFont("augustus", 22f);
        }
        
        public void drawValueName(float x, float y) {
            getFont().draw(value.getName() + ": ", x, y, new Color(210, 210, 210).getRGB(), false);
        }
        
        public float getBarStartX(float x) {
            return x + getFont().getStringWidth(value.getName() + ": ") + 10f;
        }
        
        public abstract float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2);
        
        public abstract float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton);
        
        public void updateDrag(int mouseX, float barX, float barX2) {
        }
    }

    public static class ColorValueComponent extends AbstractValueComponent {
        private boolean expanded = false;
        private String draggingColorChannel = null;
        private final AnimFloat expandAnim = new AnimFloat(0f);

        public ColorValueComponent(Property<?> value) {
            super(value);
        }

        private Color getColorVal() {
            try {
                if (value.getValue() instanceof Color) return (Color) value.getValue();
                if (value.getValue() instanceof Integer) return new Color((Integer) value.getValue());
            } catch (Exception ignored) {}
            return Color.WHITE;
        }

        private void setColorVal(Color color) {
            try {
                if (value.getValue() instanceof Color) {
                    ((Property<Color>) value).setValue(color);
                } else if (value.getValue() instanceof Integer) {
                    ((Property<Integer>) value).setValue(color.getRGB());
                }
            } catch (Exception ignored) {}
        }

        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }

        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            Color curCol = getColorVal();
            float previewX = x2 - 30f;
            RoundedUtils.drawRound(previewX, y, 24f, 14f, 4f, curCol);
            RoundedUtils.drawRoundOutline(previewX, y, 24f, 14f, 4f, 1f, new Color(0, 0, 0, 0), PALETTE_BORDER);
            boolean hoverPreview = isHover(mouseX, mouseY, previewX, y, previewX + 24f, y + 14f);
            if (hoverPreview) {
                RoundedUtils.drawRoundOutline(previewX, y, 24f, 14f, 4f, 1.5f, new Color(0, 0, 0, 0), themeColor);
            }

            if (!expanded) {
                expandAnim.current = AugustusClickGui.easeTo(expandAnim.current, 0f, 1f / 20f);
                return getFont().getFontHeight() + 10f;
            }
            expandAnim.current = AugustusClickGui.easeTo(expandAnim.current, 1f, 1f / 20f);

            float subY = y + getFont().getFontHeight() + 10f;
            float barX = x + 20f;
            float barW = (x2 - x) - 60f;

            subY += drawSliderRow("R", curCol.getRed(), barX, subY, barW, new Color(255, 80, 80));
            subY += drawSliderRow("G", curCol.getGreen(), barX, subY, barW, new Color(80, 255, 80));
            subY += drawSliderRow("B", curCol.getBlue(), barX, subY, barW, new Color(80, 180, 255));

            if (draggingColorChannel != null && Mouse.isButtonDown(0)) {
                float pct = Math.max(0f, Math.min(1f, (mouseX - (barX + 20f)) / (barW - 20f)));
                int val = Math.round(pct * 255f);
                if ("R".equals(draggingColorChannel)) setColorVal(new Color(val, curCol.getGreen(), curCol.getBlue()));
                if ("G".equals(draggingColorChannel)) setColorVal(new Color(curCol.getRed(), val, curCol.getBlue()));
                if ("B".equals(draggingColorChannel)) setColorVal(new Color(curCol.getRed(), curCol.getGreen(), val));
            }

            return subY - y;
        }

        private float drawSliderRow(String channel, int value, float x, float y, float width, Color col) {
            Font font = getFont();
            font.draw(channel, x, y, col.getRGB(), false);
            float barX = x + 20f;
            float barW = width - 20f;
            RoundedUtils.drawRound(barX, y + 4f, barW, 6f, 3f, new Color(0x0B0F15, true).getRGB());

            float fillW = barW * (value / 255f);
            if (fillW > 1) {
                RoundedUtils.drawGradientHorizontal(barX, y + 4f, fillW, 6f, 3f,
                        new Color(col.getRed(), col.getGreen(), col.getBlue(), 60), col);
            }

            float knobX = barX + fillW;
            RoundedUtils.drawRound(knobX - 2.5f, y + 2.5f, 6f, 9f, 4.5f, Color.WHITE);

            font.draw(String.valueOf(value), barX + barW + 10f, y, new Color(210, 214, 222).getRGB(), false);
            return 20f;
        }

        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            float previewX = x2 - 30f;
            if (mouseButton == 0 && mouseX >= previewX && mouseX <= previewX + 24f && mouseY >= y && mouseY <= y + 14f) {
                expanded = !expanded;
                return 0f;
            }

            if (expanded && mouseButton == 0) {
                float subY = y + getFont().getFontHeight() + 10f;
                float barX = x + 40f;
                float barW = (x2 - x) - 80f;

                if (mouseY >= subY && mouseY <= subY + 18f && mouseX >= barX && mouseX <= barX + barW) {
                    draggingColorChannel = "R";
                    return -1f;
                }
                if (mouseY >= subY + 20f && mouseY <= subY + 38f && mouseX >= barX && mouseX <= barX + barW) {
                    draggingColorChannel = "G";
                    return -1f;
                }
                if (mouseY >= subY + 40f && mouseY <= subY + 58f && mouseX >= barX && mouseX <= barX + barW) {
                    draggingColorChannel = "B";
                    return -1f;
                }
            }
            return getFont().getFontHeight() + 10f;
        }
    }
    
    public static class BoolValueComponent extends AbstractValueComponent {
        private BooleanProperty v;
        private final AnimFloat switchAnim = new AnimFloat(0f);

        public BoolValueComponent(BooleanProperty v) {
            super(v);
            this.v = v;
        }

        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            switchAnim.value = v.getValue() ? 1f : 0f;
            switchAnim.current = AugustusClickGui.easeTo(switchAnim.current, switchAnim.value, 1f / 20f);

            float swX = x2 - 38f;
            float swY = y + 1f;
            float swW = 32f;
            float swH = 15f;
            RoundedUtils.drawRound(swX, swY, swW, swH, swH / 2f,
                    blend(v.getValue() ? themeColor : new Color(0x2A3342), new Color(0x3A4656), switchAnim.current));
            RoundedUtils.drawRoundOutline(swX, swY, swW, swH, swH / 2f, 0.5f,
                    new Color(0, 0, 0, 0),
                    v.getValue() ? new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 90) : PALETTE_BORDER);
            float knobOffset = switchAnim.current * (swW - 13f);
            RoundedUtils.drawRound(swX + 1f + knobOffset, swY + 1f, 13f, 13f, 6.5f, Color.WHITE);

            String text = v.getValue() ? "ON" : "OFF";
            Font font = getFont();
            font.draw(text, swX - font.getStringWidth(text) - 6, y, 
                    v.getValue() ? themeColor.getRGB() : PALETTE_SECONDARY.getRGB(), false);
            return getFont().getFontHeight() + 10f;
        }

        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            if (mouseButton == 0 && isHover(mouseX, mouseY, x, y - 2, x2, y + getFont().getFontHeight() + 2)) {
                v.setValue(!v.getValue());
                return 0f;
            }
            return getFont().getFontHeight() + 10f;
        }

        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }
    
    public static class IntValueComponent extends AbstractValueComponent {
        private IntProperty v;
        private boolean isRange;
        private boolean draggingMin = false;
        private final AnimFloat fillAnim = new AnimFloat(0f);
        
        public IntValueComponent(IntProperty v) {
            super(v);
            this.v = v;
            this.isRange = checkIsRange(v);
        }
        
        private static boolean checkIsRange(IntProperty prop) {
            String className = prop.getClass().getName().toLowerCase();
            if (className.contains("range") || className.contains("multi")) return true;
            try {
                prop.getClass().getMethod("getMinValue");
                return true;
            } catch (Exception e1) {
                try {
                    prop.getClass().getMethod("getMinVal");
                    return true;
                } catch (Exception e2) {
                    try {
                        prop.getClass().getMethod("isRange");
                        return (boolean) prop.getClass().getMethod("isRange").invoke(prop);
                    } catch (Exception e3) {
                        try {
                            prop.getClass().getDeclaredField("minValue");
                            return true;
                        } catch (Exception e4) {
                            return false;
                        }
                    }
                }
            }
        }
        
        private int getCurMin() {
            if (!isRange) return getIntPropMin(v);
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("getMinValue");
                return (int) m.invoke(v);
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = v.getClass().getMethod("getMinVal");
                    return (int) m.invoke(v);
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = v.getClass().getDeclaredField("minValue");
                        f.setAccessible(true);
                        return f.getInt(v);
                    } catch (Exception e3) {
                        return getIntPropMin(v);
                    }
                }
            }
        }
        
        private int getCurMax() {
            if (!isRange) return v.getValue();
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("getMaxValue");
                return (int) m.invoke(v);
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = v.getClass().getMethod("getMaxVal");
                    return (int) m.invoke(v);
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = v.getClass().getDeclaredField("maxValue");
                        f.setAccessible(true);
                        return f.getInt(v);
                    } catch (Exception e3) {
                        return v.getValue();
                    }
                }
            }
        }
        
        private void setCurMin(int val) {
            if (!isRange) {
                v.setValue(val);
                return;
            }
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("setMinValue", int.class);
                m.invoke(v, val);
                return;
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = v.getClass().getMethod("setMinVal", int.class);
                    m.invoke(v, val);
                    return;
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = v.getClass().getDeclaredField("minValue");
                        f.setAccessible(true);
                        f.setInt(v, val);
                        return;
                    } catch (Exception e3) {}
                }
            }
            v.setValue(val);
        }
        
        private void setCurMax(int val) {
            if (!isRange) {
                v.setValue(val);
                return;
            }
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("setMaxValue", int.class);
                m.invoke(v, val);
                return;
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = v.getClass().getMethod("setMaxVal", int.class);
                    m.invoke(v, val);
                    return;
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = v.getClass().getDeclaredField("maxValue");
                        f.setAccessible(true);
                        f.setInt(v, val);
                        return;
                    } catch (Exception e3) {}
                }
            }
            v.setValue(val);
        }
        
        private float barHeight() {
            return getFont().getFontHeight() + 4f;
        }
        
        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            float barX = getBarStartX(x);
            float barY = y - 2f;
            float barH = barHeight();
            
            int minBound = getIntPropMin(v);
            int maxBound = getIntPropMax(v);
            int rangeBound = maxBound - minBound;
            
            if (isRange) {
                int curMin = getCurMin();
                int curMax = getCurMax();
                float pMin = rangeBound <= 0 ? 0f : (float) (curMin - minBound) / (float) rangeBound;
                float pMax = rangeBound <= 0 ? 0f : (float) (curMax - minBound) / (float) rangeBound;
                pMin = Math.max(0f, Math.min(1f, pMin));
                pMax = Math.max(0f, Math.min(1f, pMax));
                if (pMin > pMax) pMin = pMax;
                
                RoundedUtils.drawRound(barX, barY, x2 - barX, barH, barH / 2f, new Color(0x0B0F15, true).getRGB());
                RoundedUtils.drawRoundOutline(barX, barY, x2 - barX, barH, barH / 2f, 0.5f, new Color(0, 0, 0, 0), PALETTE_BORDER);
                float fillStart = barX + (x2 - barX) * pMin;
                float fillEnd = barX + (x2 - barX) * pMax;
                if (fillEnd > fillStart) {
                    RoundedUtils.drawGradientHorizontal(fillStart, barY, fillEnd - fillStart, barH, barH / 2f,
                            new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 50), themeColor);
                }
                
                float knobRadius = (barH + 4f) / 2f;
                float centerY = barY + barH / 2f;
                RoundedUtils.drawRound(fillStart - knobRadius, centerY - knobRadius, knobRadius * 2, knobRadius * 2, knobRadius, Color.WHITE);
                RoundedUtils.drawRound(fillEnd - knobRadius, centerY - knobRadius, knobRadius * 2, knobRadius * 2, knobRadius, Color.WHITE);
                
                String display = curMin + " - " + curMax;
                float textWidth = getFont().getStringWidth(display);
                float textX = barX + ((x2 - barX) - textWidth) / 2f;
                getFont().draw(display, textX, y, new Color(235, 239, 245).getRGB(), false);
            } else {
                int curVal = v.getValue();
                float percent = rangeBound <= 0 ? 0f : (float) (curVal - minBound) / (float) rangeBound;
                percent = Math.max(0f, Math.min(1f, percent));
                fillAnim.value = percent;
                fillAnim.current = AugustusClickGui.easeTo(fillAnim.current, fillAnim.value, 1f / 20f);
                
                RoundedUtils.drawRound(barX, barY, x2 - barX, barH, barH / 2f, new Color(0x0B0F15, true).getRGB());
                RoundedUtils.drawRoundOutline(barX, barY, x2 - barX, barH, barH / 2f, 0.5f, new Color(0, 0, 0, 0), PALETTE_BORDER);
                float fillEnd = barX + (x2 - barX) * fillAnim.current;
                if (fillEnd > barX + 1f) {
                    RoundedUtils.drawGradientHorizontal(barX, barY, fillEnd - barX, barH, barH / 2f,
                            new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 50), themeColor);
                }
                
                float knobRadius = (barH + 4f) / 2f;
                float centerY = barY + barH / 2f;
                float knobX = fillEnd > barX + 1f ? fillEnd : barX;
                RoundedUtils.drawRound(knobX - knobRadius, centerY - knobRadius, knobRadius * 2, knobRadius * 2, knobRadius, Color.WHITE);
                
                String display = String.valueOf(curVal);
                float textWidth = getFont().getStringWidth(display);
                float textX = barX + ((x2 - barX) - textWidth) / 2f;
                getFont().draw(display, textX, y, new Color(235, 239, 245).getRGB(), false);
            }
            
            return barH + 8f;
        }
        
        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            float barX = getBarStartX(x);
            float barY = y - 2f;
            float barH = barHeight();
            if (mouseButton == 0 && isHover(mouseX, mouseY, barX, barY, x2, barY + barH)) {
                if (isRange) {
                    float span = x2 - barX;
                    float percent = span <= 0f ? 0f : (mouseX - barX) / span;
                    percent = Math.max(0f, Math.min(1f, percent));
                    int minBound = getIntPropMin(v);
                    int maxBound = getIntPropMax(v);
                    int clickedVal = Math.round(minBound + percent * (maxBound - minBound));
                    
                    int curMin = getCurMin();
                    int curMax = getCurMax();
                    if (Math.abs(clickedVal - curMin) <= Math.abs(clickedVal - curMax)) {
                        draggingMin = true;
                        setCurMin(Math.min(clickedVal, curMax));
                    } else {
                        draggingMin = false;
                        setCurMax(Math.max(clickedVal, curMin));
                    }
                } else {
                    updateDrag(mouseX, barX, x2);
                }
                return -1f;
            }
            return barH + 8f;
        }
        
        @Override
        public void updateDrag(int mouseX, float barX, float barX2) {
            float span = barX2 - barX;
            float percent = span <= 0f ? 0f : (mouseX - barX) / span;
            percent = Math.max(0f, Math.min(1f, percent));
            int minBound = getIntPropMin(v);
            int maxBound = getIntPropMax(v);
            int newValue = Math.round(minBound + percent * (maxBound - minBound));
            
            if (isRange) {
                if (draggingMin) {
                    int curMax = getCurMax();
                    if (newValue > curMax) newValue = curMax;
                    setCurMin(newValue);
                } else {
                    int curMin = getCurMin();
                    if (newValue < curMin) newValue = curMin;
                    setCurMax(newValue);
                }
            } else {
                v.setValue(newValue);
            }
        }
        
        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }
    
    public static class FloatValueComponent extends AbstractValueComponent {
        private FloatProperty v;
        private boolean isRange;
        private boolean draggingMin = false;
        private final AnimFloat fillAnim = new AnimFloat(0f);
        
        public FloatValueComponent(FloatProperty v) {
            super(v);
            this.v = v;
            this.isRange = checkIsRange(v);
        }
        
        private static boolean checkIsRange(FloatProperty prop) {
            String className = prop.getClass().getName().toLowerCase();
            if (className.contains("range") || className.contains("multi")) return true;
            try {
                prop.getClass().getMethod("getMinValue");
                return true;
            } catch (Exception e1) {
                try {
                    prop.getClass().getMethod("getMinVal");
                    return true;
                } catch (Exception e2) {
                    try {
                        prop.getClass().getMethod("isRange");
                        return (boolean) prop.getClass().getMethod("isRange").invoke(prop);
                    } catch (Exception e3) {
                        try {
                            prop.getClass().getDeclaredField("minValue");
                            return true;
                        } catch (Exception e4) {
                            return false;
                        }
                    }
                }
            }
        }
        
        private float getCurMin() {
            if (!isRange) return getFloatPropMin(v);
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("getMinValue");
                return (float) m.invoke(v);
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = v.getClass().getMethod("getMinVal");
                    return (float) m.invoke(v);
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = v.getClass().getDeclaredField("minValue");
                        f.setAccessible(true);
                        return f.getFloat(v);
                    } catch (Exception e3) {
                        return getFloatPropMin(v);
                    }
                }
            }
        }
        
        private float getCurMax() {
            if (!isRange) return v.getValue();
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("getMaxValue");
                return (float) m.invoke(v);
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = v.getClass().getMethod("getMaxVal");
                    return (float) m.invoke(v);
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = v.getClass().getDeclaredField("maxValue");
                        f.setAccessible(true);
                        return f.getFloat(v);
                    } catch (Exception e3) {
                        return v.getValue();
                    }
                }
            }
        }
        
        private void setCurMin(float val) {
            if (!isRange) {
                v.setValue(val);
                return;
            }
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("setMinValue", float.class);
                m.invoke(v, val);
                return;
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = v.getClass().getMethod("setMinVal", float.class);
                    m.invoke(v, val);
                    return;
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = v.getClass().getDeclaredField("minValue");
                        f.setAccessible(true);
                        f.setFloat(v, val);
                        return;
                    } catch (Exception e3) {}
                }
            }
            v.setValue(val);
        }
        
        private void setCurMax(float val) {
            if (!isRange) {
                v.setValue(val);
                return;
            }
            try {
                java.lang.reflect.Method m = v.getClass().getMethod("setMaxValue", float.class);
                m.invoke(v, val);
                return;
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = v.getClass().getMethod("setMaxVal", float.class);
                    m.invoke(v, val);
                    return;
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = v.getClass().getDeclaredField("maxValue");
                        f.setAccessible(true);
                        f.setFloat(v, val);
                        return;
                    } catch (Exception e3) {}
                }
            }
            v.setValue(val);
        }
        
        private float barHeight() {
            return getFont().getFontHeight() + 4f;
        }
        
        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            float barX = getBarStartX(x);
            float barY = y - 2f;
            float barH = barHeight();
            
            float minBound = getFloatPropMin(v);
            float maxBound = getFloatPropMax(v);
            float rangeBound = maxBound - minBound;
            
            if (isRange) {
                float curMin = getCurMin();
                float curMax = getCurMax();
                float pMin = rangeBound <= 0f ? 0f : (curMin - minBound) / rangeBound;
                float pMax = rangeBound <= 0f ? 0f : (curMax - minBound) / rangeBound;
                pMin = Math.max(0f, Math.min(1f, pMin));
                pMax = Math.max(0f, Math.min(1f, pMax));
                if (pMin > pMax) pMin = pMax;
                
                RoundedUtils.drawRound(barX, barY, x2 - barX, barH, barH / 2f, new Color(0x0B0F15, true).getRGB());
                RoundedUtils.drawRoundOutline(barX, barY, x2 - barX, barH, barH / 2f, 0.5f, new Color(0, 0, 0, 0), PALETTE_BORDER);
                float fillStart = barX + (x2 - barX) * pMin;
                float fillEnd = barX + (x2 - barX) * pMax;
                if (fillEnd > fillStart) {
                    RoundedUtils.drawGradientHorizontal(fillStart, barY, fillEnd - fillStart, barH, barH / 2f,
                            new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 50), themeColor);
                }
                
                float knobRadius = (barH + 4f) / 2f;
                float centerY = barY + barH / 2f;
                RoundedUtils.drawRound(fillStart - knobRadius, centerY - knobRadius, knobRadius * 2, knobRadius * 2, knobRadius, Color.WHITE);
                RoundedUtils.drawRound(fillEnd - knobRadius, centerY - knobRadius, knobRadius * 2, knobRadius * 2, knobRadius, Color.WHITE);
                
                String display = String.format("%.1f - %.1f", curMin, curMax);
                float textWidth = getFont().getStringWidth(display);
                float textX = barX + ((x2 - barX) - textWidth) / 2f;
                getFont().draw(display, textX, y, new Color(235, 239, 245).getRGB(), false);
            } else {
                float curVal = v.getValue();
                float percent = rangeBound <= 0f ? 0f : (curVal - minBound) / rangeBound;
                percent = Math.max(0f, Math.min(1f, percent));
                fillAnim.value = percent;
                fillAnim.current = AugustusClickGui.easeTo(fillAnim.current, fillAnim.value, 1f / 20f);
                
                RoundedUtils.drawRound(barX, barY, x2 - barX, barH, barH / 2f, new Color(0x0B0F15, true).getRGB());
                RoundedUtils.drawRoundOutline(barX, barY, x2 - barX, barH, barH / 2f, 0.5f, new Color(0, 0, 0, 0), PALETTE_BORDER);
                float fillEnd = barX + (x2 - barX) * fillAnim.current;
                if (fillEnd > barX + 1f) {
                    RoundedUtils.drawGradientHorizontal(barX, barY, fillEnd - barX, barH, barH / 2f,
                            new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 50), themeColor);
                }
                
                float knobRadius = (barH + 4f) / 2f;
                float centerY = barY + barH / 2f;
                float knobX = fillEnd > barX + 1f ? fillEnd : barX;
                RoundedUtils.drawRound(knobX - knobRadius, centerY - knobRadius, knobRadius * 2, knobRadius * 2, knobRadius, Color.WHITE);
                
                String display = String.format("%.1f", curVal);
                float textWidth = getFont().getStringWidth(display);
                float textX = barX + ((x2 - barX) - textWidth) / 2f;
                getFont().draw(display, textX, y, new Color(235, 239, 245).getRGB(), false);
            }
            
            return barH + 8f;
        }
        
        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            float barX = getBarStartX(x);
            float barY = y - 2f;
            float barH = barHeight();
            if (mouseButton == 0 && isHover(mouseX, mouseY, barX, barY, x2, barY + barH)) {
                if (isRange) {
                    float span = x2 - barX;
                    float percent = span <= 0f ? 0f : (mouseX - barX) / span;
                    percent = Math.max(0f, Math.min(1f, percent));
                    float minBound = getFloatPropMin(v);
                    float maxBound = getFloatPropMax(v);
                    float clickedVal = minBound + percent * (maxBound - minBound);
                    clickedVal = Math.round(clickedVal * 10f) / 10f;
                    
                    float curMin = getCurMin();
                    float curMax = getCurMax();
                    if (Math.abs(clickedVal - curMin) <= Math.abs(clickedVal - curMax)) {
                        draggingMin = true;
                        setCurMin(Math.min(clickedVal, curMax));
                    } else {
                        draggingMin = false;
                        setCurMax(Math.max(clickedVal, curMin));
                    }
                } else {
                    updateDrag(mouseX, barX, x2);
                }
                return -1f;
            }
            return barH + 8f;
        }
        
        @Override
        public void updateDrag(int mouseX, float barX, float barX2) {
            float span = barX2 - barX;
            float percent = span <= 0f ? 0f : (mouseX - barX) / span;
            percent = Math.max(0f, Math.min(1f, percent));
            float minBound = getFloatPropMin(v);
            float maxBound = getFloatPropMax(v);
            float newValue = minBound + percent * (maxBound - minBound);
            newValue = Math.round(newValue * 10f) / 10f;
            
            if (isRange) {
                if (draggingMin) {
                    float curMax = getCurMax();
                    if (newValue > curMax) newValue = curMax;
                    setCurMin(newValue);
                } else {
                    float curMin = getCurMin();
                    if (newValue < curMin) newValue = curMin;
                    setCurMax(newValue);
                }
            } else {
                v.setValue(newValue);
            }
        }
        
        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }
    
    // COMPONENT MODE SỔ DANH SÁCH (DROPDOWN LIST)
    public static class ListValueComponent extends AbstractValueComponent {
        private ModeProperty v;
        private boolean expanded = false;

        public ListValueComponent(ModeProperty v) {
            super(v);
            this.v = v;
        }

        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            String[] modes = v.getModes();
            if (modes == null || modes.length == 0) return getFont().getFontHeight() + 10f;

            int selectedIndex = v.getValue();
            String selectedText = (selectedIndex >= 0 && selectedIndex < modes.length) 
                                  ? modes[selectedIndex] 
                                  : String.valueOf(selectedIndex);

            float fontHeight = getFont().getFontHeight();
            float mainRowHeight = fontHeight + 10f;

            // Nút hiển thị mode hiện tại
            float pillW = getFont().getStringWidth(selectedText) + 26f;
            float pillX = x2 - pillW;
            boolean mainHover = isHover(mouseX, mouseY, pillX, y - 1f, x2, y + fontHeight + 1f);

            RoundedUtils.drawRound(pillX, y - 1f, pillW, fontHeight + 2f, 6f,
                    blend(PALETTE_CARD_HOVER, themeColor, mainHover ? 0.4f : 0.15f));
            RoundedUtils.drawRoundOutline(pillX, y - 1f, pillW, fontHeight + 2f, 6f, 1f,
                    new Color(0, 0, 0, 0),
                    blend(PALETTE_BORDER, themeColor, mainHover ? 0.6f : 0.2f));

            // Chữ hiển thị trên pill
            getFont().draw(selectedText, pillX + 8, y, new Color(255, 215, 0).getRGB(), false);

            Font fontChev = FontRepository.getFont("augustus", 18f);
            fontChev.draw(expanded ? "^" : "v", pillX + pillW - 14, y + 2, PALETTE_SECONDARY.getRGB(), false);

            if (!expanded) {
                return mainRowHeight;
            }

            // MỞ SỔ TẤT CẢ CÁC MODE BÊN DƯỚI
            float currentModeY = y + mainRowHeight;
            float listX = x + 10f;
            float listW = x2 - listX;

            for (int i = 0; i < modes.length; i++) {
                String modeName = modes[i];
                boolean isSelected = (i == selectedIndex);
                boolean modeHover = isHover(mouseX, mouseY, listX, currentModeY, x2, currentModeY + 16f);

                if (modeHover) {
                    RoundedUtils.drawRound(listX, currentModeY, listW, 16f, 4f, new Color(255, 255, 255, 15));
                }

                // MODE ĐANG CHỌN = MÀU VÀNG (#FFD700), MODE KHÁC = MÀU BÌNH THƯỜNG
                Color textColor = isSelected ? new Color(255, 215, 0) : (modeHover ? Color.WHITE : PALETTE_SECONDARY);

                if (isSelected) {
                    RenderUtil.fillCircle(listX + 8f, currentModeY + 8f, 2.5f, 10, new Color(255, 215, 0).getRGB());
                }

                getFont().draw(modeName, listX + (isSelected ? 16f : 8f), currentModeY + 2f, textColor.getRGB(), false);

                currentModeY += 18f;
            }

            return (currentModeY - y) + 4f;
        }

        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            String[] modes = v.getModes();
            if (modes == null || modes.length == 0) return getFont().getFontHeight() + 10f;

            float fontHeight = getFont().getFontHeight();
            float mainRowHeight = fontHeight + 10f;

            int selectedIndex = v.getValue();
            String selectedText = (selectedIndex >= 0 && selectedIndex < modes.length) 
                                  ? modes[selectedIndex] 
                                  : String.valueOf(selectedIndex);
            float pillW = getFont().getStringWidth(selectedText) + 26f;
            float pillX = x2 - pillW;

            // Bấm mở/đóng danh sách mode
            if (mouseButton == 0 && isHover(mouseX, mouseY, pillX, y - 1f, x2, y + fontHeight + 1f)) {
                expanded = !expanded;
                return 0f;
            }

            // Chọn mode trực tiếp trong danh sách đã mở
            if (expanded && mouseButton == 0) {
                float currentModeY = y + mainRowHeight;
                float listX = x + 10f;

                for (int i = 0; i < modes.length; i++) {
                    if (isHover(mouseX, mouseY, listX, currentModeY, x2, currentModeY + 16f)) {
                        v.setValue(i);
                        expanded = false; // Đóng lại sau khi đã chọn
                        return 0f;
                    }
                    currentModeY += 18f;
                }
            }

            return mainRowHeight;
        }
        
        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }
    
    public static class AnimFloat {
        public float value;
        public float current;
        public AnimFloat(float initial) {
            this.value = initial;
            this.current = initial;
        }
    }
    
    public static class EntityTargets {
        public static boolean player = true;
        public static boolean mob = true;
        public static boolean animal = true;
        public static boolean invisible = false;
        public static boolean dead = false;
    }
    
    public static class TargetEntry {
        public String label;
        private StateGetter stateGetter;
        private Toggle toggle;
        
        public interface StateGetter { boolean get(); }
        public interface Toggle { void toggle(); }
        
        public TargetEntry(String label, StateGetter stateGetter, Toggle toggle) {
            this.label = label;
            this.stateGetter = stateGetter;
            this.toggle = toggle;
        }
    }
}