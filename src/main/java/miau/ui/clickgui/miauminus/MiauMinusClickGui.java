package miau.ui.clickgui.miauminus;

import miau.Miau;
import miau.module.Module;
import miau.module.modules.render.HUD;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
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
import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class MiauMinusClickGui extends GuiScreen {
    
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    public static Color themeColor = new Color(0, 190, 245, 200); 
    public static final Color GLASS_BG = new Color(10, 14, 20, 180);       // Nền GUI mờ ảo
    public static final Color BORDER_COLOR = new Color(255, 255, 255, 25);  // Khai báo lại viền phụ
    public static final Color TEXT_SELECTED = new Color(255, 255, 255);     
    public static final Color TEXT_UNSELECTED = new Color(140, 155, 170);   
    
    public static String colorMode = "Custom";
    public static int customR = 0;
    public static int customG = 190;
    public static int customB = 245;
    
    private boolean showSettingsPopup = false;
    private String draggingSlider = null;
    
    private float panelX = 0f;
    private float panelY = 0f;
    private float panelWidth = 580f;
    private float panelHeight = 360f;
    
    private float categoryScroll = 0f;
    private float moduleScroll = 0f;
    private float valueScroll = 0f;
    
    private boolean isDragging = false;
    private float dragOffX = 0f;
    private float dragOffY = 0f;
    
    private String currentCategory = "Render";
    private String currentModule = "Ambience";
    private float modulePanelWidth = 150f;
    
    private AbstractValueComponent draggingComponent = null;
    private float dragBarX = 0f;
    private float dragBarX2 = 0f;
    
    private final Map<Module, List<AbstractValueComponent>> componentCache = new HashMap<>();
    private final List<TargetEntry> targetEntries = new ArrayList<>();
    private final List<AmbiencePresetButton> ambiencePresetButtons = new ArrayList<>();
    
    public MiauMinusClickGui() {
        targetEntries.add(new TargetEntry("Players", () -> EntityTargets.player, () -> EntityTargets.player = !EntityTargets.player));
        targetEntries.add(new TargetEntry("Mobs", () -> EntityTargets.mob, () -> EntityTargets.mob = !EntityTargets.mob));
        targetEntries.add(new TargetEntry("Animals", () -> EntityTargets.animal, () -> EntityTargets.animal = !EntityTargets.animal));
        targetEntries.add(new TargetEntry("Invisible", () -> EntityTargets.invisible, () -> EntityTargets.invisible = !EntityTargets.invisible));
        targetEntries.add(new TargetEntry("Dead", () -> EntityTargets.dead, () -> EntityTargets.dead = !EntityTargets.dead));
    }
    
    @Override
    public void initGui() {
        isDragging = false;
        ScaledResolution sr = new ScaledResolution(mc);
        panelWidth = Math.min(610f, sr.getScaledWidth() * 0.85f);
        panelHeight = Math.min(380f, sr.getScaledHeight() * 0.85f);
        panelX = (sr.getScaledWidth() - panelWidth) / 2f;
        panelY = (sr.getScaledHeight() - panelHeight) / 2f;
    }
    
    @Override
    public void onGuiClosed() {
        isDragging = false;
        showSettingsPopup = false;
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    
    private float getHeaderHeight() {
        return 42f;
    }
    
    private float endX() {
        return panelX + panelWidth;
    }
    
    private float endY() {
        return panelY + panelHeight;
    }
    
    private void setCategory(String category) {
        currentCategory = category;
        moduleScroll = 0f;
        valueScroll = 0f;
        currentModule = null;
    }
    
    private void setModule(String moduleName) {
        currentModule = moduleName;
        valueScroll = 0f;
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
    
    // Lấy màu từ HUD theo logic chuẩn
    private void updateThemeColor() {
        try {
            HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
            if (hud != null) {
                Color hudCol = hud.getColor(System.currentTimeMillis());
                themeColor = new Color(hudCol.getRed(), hudCol.getGreen(), hudCol.getBlue(), 200);
                return;
            }
        } catch (Exception ignored) {}
        
        if ("Custom".equalsIgnoreCase(colorMode)) {
            themeColor = new Color(customR, customG, customB, 200);
        }
    }
    
    private void drawCustomImage(String path, float x, float y, float width, float height) {
        try {
            net.minecraft.util.ResourceLocation loc = new net.minecraft.util.ResourceLocation(path);
            mc.getTextureManager().bindTexture(loc);
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0, (int) width, (int) height, width, height);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        } catch (Exception ignored) {}
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateThemeColor();

        if (isDragging) {
            panelX = mouseX - dragOffX;
            panelY = mouseY - dragOffY;
        }
        
        float ex = endX();
        float ey = endY();
        float headerH = getHeaderHeight();
        
        RenderUtil.drawRect(0, 0, width, height, new Color(0, 0, 0, 80).getRGB());
        
        // --- HIỆU ỨNG VIỀN GLOW TRẮNG CHẠY VÒNG VÒNG QUANH GUI ---
        float glowAlpha = 0.3f + 0.2f * (float)Math.sin(System.currentTimeMillis() / 400.0);
        Color glowWhite = new Color(255, 255, 255, (int)(glowAlpha * 255));
        
        // Vẽ viền ngoài glow trắng chạy
        RoundedUtils.drawRoundOutline(panelX - 1.5f, panelY - 1.5f, panelWidth + 3f, panelHeight + 3f, 9.5f, 1.5f, glowWhite, themeColor);
        // Vẽ viền chính đổi màu theo HUD
        RoundedUtils.drawRoundOutline(panelX, panelY, panelWidth, panelHeight, 8f, 1.2f, themeColor, themeColor);

        // Header
        RoundedUtils.drawRound(panelX, panelY, panelWidth, headerH, 8f, GLASS_BG);
        drawHeaderContent(ex, mouseX, mouseY);
        
        float contentTopY = panelY + headerH + 8f;
        float contentHeight = ey - contentTopY;
        float profileHeight = 54f; 
        float moduleListHeight = contentHeight - profileHeight - 8f;
        
        // List Module
        RoundedUtils.drawRound(panelX, contentTopY, modulePanelWidth, moduleListHeight, 8f, GLASS_BG);
        RoundedUtils.drawRoundOutline(panelX, contentTopY, modulePanelWidth, moduleListHeight, 8f, 0.8f, BORDER_COLOR, BORDER_COLOR);
        
        // Profile & Discord
        float profileY = contentTopY + moduleListHeight + 8f;
        RoundedUtils.drawRound(panelX, profileY, modulePanelWidth, profileHeight, 8f, GLASS_BG);
        RoundedUtils.drawRoundOutline(panelX, profileY, modulePanelWidth, profileHeight, 8f, 0.8f, BORDER_COLOR, BORDER_COLOR);
        drawProfileContent(profileY, mouseX, mouseY);
        
        // Setting Panel
        float settingX = panelX + modulePanelWidth + 8f;
        float settingW = ex - settingX;
        
        RoundedUtils.drawRound(settingX, contentTopY, settingW, contentHeight, 8f, GLASS_BG);
        RoundedUtils.drawRoundOutline(settingX, contentTopY, settingW, contentHeight, 8f, 0.8f, BORDER_COLOR, BORDER_COLOR);
        
        if (currentCategory == null) {
            drawTargetsScreen(mouseX, mouseY, settingX, contentTopY, settingW, contentHeight);
        } else {
            drawModulesAndSettings(mouseX, mouseY, contentTopY, moduleListHeight, settingX, settingW, contentHeight);
        }

        if (showSettingsPopup) {
            drawColorSettingsPopup(mouseX, mouseY);
        }
    }
    
    private void drawHeaderContent(float ex, int mouseX, int mouseY) {
        drawCustomImage("miau/moduleimage/clickgui.png", panelX + 12f, panelY + 11f, 75f, 20f);
        
        Font fontCat = FontRepository.getFont("Inter Bold", 16f);
        float catAreaStart = panelX + 105f;
        float catAreaEnd = ex - 30f;
        float catY = panelY + 13f;
        
        startScissorBox(catAreaStart, panelY, catAreaEnd, panelY + getHeaderHeight());
        
        float catPos = categoryScroll;
        String[] categories = {"Combat", "Movement", "Player", "Render", "Ghost", "Network", "Minigames", "Misc"};
        for (String category : categories) {
            float sw = fontCat.getStringWidth(category);
            float sx = catAreaStart + catPos;
            boolean isSelected = currentCategory != null && currentCategory.equalsIgnoreCase(category);
            Color color = isSelected ? TEXT_SELECTED : TEXT_UNSELECTED;
            
            drawCustomImage("miau/moduleimage/" + category.toLowerCase() + ".png", sx - 14f, catY + 1f, 12f, 12f);
            
            fontCat.draw(category, sx, catY, color.getRGB(), false);
            if (isSelected) {
                RoundedUtils.drawRound(sx - 14f, panelY + getHeaderHeight() - 4f, sw + 18f, 2f, 1f, themeColor);
            }
            catPos += sw + 26f;
        }
        
        String targetsLabel = "Targets";
        float tsw = fontCat.getStringWidth(targetsLabel);
        float targetsStart = catAreaStart + catPos;
        boolean isTargetsSelected = (currentCategory == null);
        Color tColor = isTargetsSelected ? TEXT_SELECTED : TEXT_UNSELECTED;
        fontCat.draw(targetsLabel, targetsStart, catY, tColor.getRGB(), false);
        if (isTargetsSelected) {
            RoundedUtils.drawRound(targetsStart - 1, panelY + getHeaderHeight() - 4f, tsw + 2, 2f, 1f, themeColor);
        }
        
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        float closeX = ex - 20f;
        float closeY = panelY + 13f;
        Font fontClose = FontRepository.getFont("Inter Bold", 16f);
        boolean isCloseHover = isHover(mouseX, mouseY, closeX - 4, closeY - 4, closeX + 12, closeY + 12);
        fontClose.draw("✕", closeX, closeY, isCloseHover ? new Color(255, 90, 90).getRGB() : TEXT_UNSELECTED.getRGB(), false);
    }
    
    private void drawProfileContent(float profileY, int mouseX, int mouseY) {
        if (mc.thePlayer != null) {
            GL11.glPushMatrix();
            mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect((int)(panelX + 8), (int)(profileY + 6), 8.0f, 8.0f, 8, 8, 24, 24, 64.0f, 64.0f);
            GL11.glPopMatrix();
            
            Font fontBold = FontRepository.getFont("Inter Bold", 13f);
            Font fontSmall = FontRepository.getFont("Inter Regular", 11f);
            fontBold.draw(mc.thePlayer.getName(), panelX + 36, profileY + 6, TEXT_SELECTED.getRGB(), false);
            
            int mins = mc.thePlayer.ticksExisted / 1200;
            fontSmall.draw(String.format("Time: %02dh %02dm", mins / 60, mins % 60), panelX + 36, profileY + 18, TEXT_UNSELECTED.getRGB(), false);
        }
        
        float discX = panelX + 8f;
        float discY = profileY + 32f;
        float discW = modulePanelWidth - 16f;
        float discH = 16f;
        boolean isDiscHover = isHover(mouseX, mouseY, discX, discY, discX + discW, discY + discH);
        
        RoundedUtils.drawRound(discX, discY, discW, discH, 4f, isDiscHover ? new Color(88, 101, 242, 180) : new Color(88, 101, 242, 120));
        drawCustomImage("miau/moduleimage/discord.png", discX + 4f, discY + 2f, 12f, 12f);
        
        Font fontDisc = FontRepository.getFont("Inter Bold", 11f);
        fontDisc.draw("Discord Community", discX + 20f, discY + 4f, Color.WHITE.getRGB(), false);
        
        float settingX = panelX + modulePanelWidth - 20f;
        float settingY = profileY + 8f;
        Font fontIcon = FontRepository.getFont("Inter Bold", 15f);
        fontIcon.draw("⚙", settingX, settingY, TEXT_UNSELECTED.getRGB(), false);
    }

    private void drawModulesAndSettings(int mouseX, int mouseY, float contentTopY, float moduleListHeight, float settingX, float settingW, float contentHeight) {
        Font fontModule = FontRepository.getFont("Inter Bold", 14f);
        float moduleItemHeight = 30f;
        List<Module> filteredModules = getFilteredModules();
        
        startScissorBox(panelX + 2, contentTopY + 4, panelX + modulePanelWidth - 2, contentTopY + moduleListHeight - 4);
        float currentY = contentTopY + 6f + moduleScroll;
        
        for (Module module : filteredModules) {
            float msx = panelX + 6f;
            float mWidth = modulePanelWidth - 12f;
            boolean isSelected = module.getName().equalsIgnoreCase(currentModule);
            boolean isHovered = isHover(mouseX, mouseY, msx, currentY, msx + mWidth, currentY + moduleItemHeight - 4f);
            
            if (isSelected) {
                RoundedUtils.drawRound(msx, currentY, mWidth, moduleItemHeight - 4f, 6f, new Color(255, 255, 255, 15));
                RoundedUtils.drawRound(msx, currentY + 3f, 3f, moduleItemHeight - 10f, 1.5f, themeColor);
            } else if (isHovered) {
                RoundedUtils.drawRound(msx, currentY, mWidth, moduleItemHeight - 4f, 6f, new Color(255, 255, 255, 8));
            }

            Color colorText = module.isEnabled() ? themeColor : (isSelected ? TEXT_SELECTED : TEXT_UNSELECTED);
            fontModule.draw(module.getName(), msx + (isSelected ? 12f : 8f), currentY + 7f, colorText.getRGB(), false);
            
            currentY += moduleItemHeight;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        if (currentModule == null && !filteredModules.isEmpty()) {
            currentModule = filteredModules.get(0).getName();
        }
        if (currentModule == null) return;
        
        Module module = null;
        for (Module mod : Miau.moduleManager.modules.values()) {
            if (mod.getName().equalsIgnoreCase(currentModule)) {
                module = mod;
                break;
            }
        }
        if (module == null) return;
        
        Font fontLarge = FontRepository.getFont("Inter Bold", 20f);
        float titleX = settingX + 15f;
        float titleY = contentTopY + 12f;
        
        fontLarge.draw(module.getName(), titleX, titleY, TEXT_SELECTED.getRGB(), false);
        
        Font fontSub = FontRepository.getFont("Inter Regular", 12f);
        fontSub.draw("Adjust settings for " + module.getName() + ".", titleX, titleY + 16f, TEXT_UNSELECTED.getRGB(), false);
        
        float valueStartY = titleY + 36f;
        boolean isAmbience = "Ambience".equalsIgnoreCase(module.getName());
        float settingsBoxW = isAmbience ? settingW * 0.55f : (settingW - 30f);
        
        startScissorBox(titleX, valueStartY, titleX + settingsBoxW, contentTopY + contentHeight - 10f);
        
        List<AbstractValueComponent> components = getComponents(module);
        float totalValueY = 0f;
        
        for (AbstractValueComponent component : components) {
            if (component.value.isVisible()) {
                float valueY = valueStartY + totalValueY + valueScroll;
                component.drawValueName(titleX, valueY);
                totalValueY += component.drawValue(mouseX, mouseY, titleX, valueY, titleX + settingsBoxW, contentTopY + contentHeight);
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        if (isAmbience) {
            float previewX = titleX + settingsBoxW + 15f;
            float previewW = settingX + settingW - previewX - 15f;
            
            if (previewW > 80f) {
                int currentTime = 6000;
                for (AbstractValueComponent comp : components) {
                    if (comp.value.getName().equalsIgnoreCase("Time") && comp.value instanceof IntProperty) {
                        currentTime = ((IntProperty) comp.value).getValue();
                        break;
                    }
                }
                
                RoundedUtils.drawRound(previewX, valueStartY, previewW, 120f, 6f, new Color(15, 22, 36));
                RoundedUtils.drawRoundOutline(previewX, valueStartY, previewW, 120f, 6f, 1f, BORDER_COLOR, BORDER_COLOR);
                
                Color skyColor = getSkyColorForTime(currentTime);
                RoundedUtils.drawRound(previewX + 2f, valueStartY + 2f, previewW - 4f, 85f, 5f, skyColor);
                
                float sunProgress = (float) currentTime / 24000f;
                float sunX = previewX + 15f + (previewW - 30f) * sunProgress;
                float sunY = valueStartY + 45f - (float) Math.sin(sunProgress * Math.PI * 2) * 25f;
                
                if (currentTime >= 12000 && currentTime < 23500) {
                    RoundedUtils.drawRound(sunX - 6f, sunY - 6f, 12f, 12f, 6f, new Color(220, 220, 240, 200));
                } else {
                    RoundedUtils.drawRound(sunX - 7f, sunY - 7f, 14f, 14f, 7f, new Color(255, 230, 100, 220));
                }
                
                String timeLabel = getTimeName(currentTime);
                Font fontTime = FontRepository.getFont("Inter Bold", 12f);
                fontTime.draw(timeLabel, previewX + 8f, valueStartY + 95f, Color.WHITE.getRGB(), false);
                
                float presetY = valueStartY + 128f;
                float presetH = contentTopY + contentHeight - presetY - 10f;
                if (presetH > 25f) {
                    RoundedUtils.drawRound(previewX, presetY, previewW, presetH, 6f, GLASS_BG);
                    RoundedUtils.drawRoundOutline(previewX, presetY, previewW, presetH, 6f, 1f, BORDER_COLOR, BORDER_COLOR);
                    fontSub.draw("Toi Bi Gay", previewX + 8f, presetY + 5f, TEXT_SELECTED.getRGB(), false);
                    
                    ambiencePresetButtons.clear();
                    String[] presets = {"Sunrise", "Day", "Afternoon", "Night", "Midnight"};
                    int[] presetTimes = {23000, 6000, 13000, 18000, 0};
                    
                    float btnY = presetY + 18f;
                    float btnW = previewW - 12f;
                    float btnH = 14f;
                    
                    Font fontBtn = FontRepository.getFont("Inter Regular", 11f);
                    for (int i = 0; i < presets.length; i++) {
                        float btnX = previewX + 6f;
                        boolean isHoverBtn = isHover(mouseX, mouseY, btnX, btnY, btnX + btnW, btnY + btnH);
                        
                        RoundedUtils.drawRound(btnX, btnY, btnW, btnH, 3f, isHoverBtn ? new Color(255, 255, 255, 30) : new Color(255, 255, 255, 12));
                        fontBtn.draw(presets[i], btnX + 6f, btnY + 2f, TEXT_SELECTED.getRGB(), false);
                        
                        ambiencePresetButtons.add(new AmbiencePresetButton(btnX, btnY, btnW, btnH, presetTimes[i]));
                        btnY += btnH + 3f;
                    }
                }
            }
        }
    }
    
    private Color getSkyColorForTime(int time) {
        if (time >= 22500 || time < 1000) return new Color(15, 15, 35); 
        if (time >= 1000 && time < 6000) return new Color(230, 130, 70); 
        if (time >= 6000 && time < 12000) return new Color(85, 160, 245); 
        if (time >= 12000 && time < 18000) return new Color(210, 90, 50); 
        return new Color(25, 30, 60); 
    }

    private String getTimeName(int time) {
        if (time >= 22500 || time < 1000) return "Midnight (0)";
        if (time >= 1000 && time < 6000) return "Sunrise (23000)";
        if (time >= 6000 && time < 12000) return "Day (6000)";
        if (time >= 12000 && time < 18000) return "Afternoon (13000)";
        return "Night (18000)";
    }
    
    private void drawTargetsScreen(int mouseX, int mouseY, float settingX, float contentTopY, float settingW, float contentHeight) {
        Font fontTitle = FontRepository.getFont("Inter Bold", 20f);
        fontTitle.draw("Entity Targets", settingX + 15f, contentTopY + 12f, TEXT_SELECTED.getRGB(), false);
        
        Font fontMedium = FontRepository.getFont("Inter Regular", 16f);
        float itemY = contentTopY + 45f;
        for (TargetEntry entry : targetEntries) {
            boolean active = entry.stateGetter.get();
            Color color = active ? themeColor : TEXT_UNSELECTED;
            fontMedium.draw(entry.label, settingX + 15f, itemY, color.getRGB(), false);
            itemY += fontMedium.getFontHeight() + 10f;
        }
    }

    private void drawColorSettingsPopup(int mouseX, int mouseY) {
        float popX = panelX + modulePanelWidth + 8f;
        float popY = endY() - 120f;
        float popW = 140f;
        float popH = 110f;

        RoundedUtils.drawRound(popX, popY, popW, popH, 6f, new Color(15, 20, 28, 240));
        RoundedUtils.drawRoundOutline(popX, popY, popW, popH, 6f, 1f, BORDER_COLOR, BORDER_COLOR);

        Font fontSmall = FontRepository.getFont("Inter Regular", 12f);
        fontSmall.draw("RGB Theme Color", popX + 8f, popY + 8f, TEXT_SELECTED.getRGB(), false);

        float sliderY = popY + 28f;
        drawColorSlider("R", customR, popX + 8f, sliderY, popW - 16f, new Color(255, 80, 80));
        drawColorSlider("G", customG, popX + 8f, sliderY + 22f, popW - 16f, new Color(80, 255, 80));
        drawColorSlider("B", customB, popX + 8f, sliderY + 44f, popW - 16f, new Color(80, 180, 255));

        if (draggingSlider != null && Mouse.isButtonDown(0)) {
            float barX = popX + 20f;
            float barW = popW - 35f;
            float pct = Math.max(0f, Math.min(1f, (mouseX - barX) / barW));
            int val = Math.round(pct * 255f);
            if ("R".equals(draggingSlider)) customR = val;
            if ("G".equals(draggingSlider)) customG = val;
            if ("B".equals(draggingSlider)) customB = val;
        }
    }

    private void drawColorSlider(String label, int value, float x, float y, float width, Color col) {
        Font font = FontRepository.getFont("Inter Regular", 12f);
        font.draw(label, x, y + 1, col.getRGB(), false);

        float barX = x + 12f;
        float barY = y + 3f;
        float barW = width - 20f;
        float barH = 3f;

        RoundedUtils.drawRound(barX, barY, barW, barH, 1.5f, new Color(255, 255, 255, 20));

        float pct = value / 255f;
        float fillW = barW * pct;
        if (fillW > 0) {
            RoundedUtils.drawRound(barX, barY, fillW, barH, 1.5f, col);
        }

        RoundedUtils.drawRound(barX + fillW - 2, barY - 2, 4, 7, 1.5f, Color.WHITE);
        font.draw(String.valueOf(value), barX + barW + 4, y + 1, TEXT_UNSELECTED.getRGB(), false);
    }
    
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float ex = endX();
        float ey = endY();
        float headerH = getHeaderHeight();
        
        if (mouseButton == 0 && isHover(mouseX, mouseY, ex - 24f, panelY + 8f, ex - 4f, panelY + 28f)) {
            mc.displayGuiScreen(null);
            return;
        }
        
        if (mouseButton == 0 && currentModule != null && currentModule.equalsIgnoreCase("Ambience")) {
            for (AmbiencePresetButton btn : ambiencePresetButtons) {
                if (isHover(mouseX, mouseY, btn.x, btn.y, btn.x + btn.width, btn.y + btn.height)) {
                    Module mod = null;
                    for (Module m : Miau.moduleManager.modules.values()) {
                        if (m.getName().equalsIgnoreCase("Ambience")) { mod = m; break; }
                    }
                    if (mod != null && mod.getValues() != null) {
                        for (Property<?> p : mod.getValues()) {
                            if (p.getName().equalsIgnoreCase("Time") && p instanceof IntProperty) {
                                ((IntProperty) p).setValue(btn.targetTime);
                                break;
                            }
                        }
                    }
                    return;
                }
            }
        }
        
        float profileY = panelY + headerH + 8f + ((ey - (panelY + headerH + 8f)) - 54f - 8f);
        float discX = panelX + 8f;
        float discY = profileY + 32f;
        float discW = modulePanelWidth - 16f;
        float discH = 16f;
        if (mouseButton == 0 && isHover(mouseX, mouseY, discX, discY, discX + discW, discY + discH)) {
            try {
                Desktop.getDesktop().browse(new URI("https://discord.gg/4r9M52zRge"));
            } catch (Exception ignored) {}
            return;
        }
        
        float settingX = panelX + modulePanelWidth - 24f;
        float settingY = profileY + 8f;

        if (mouseButton == 0 && isHover(mouseX, mouseY, settingX - 4, settingY - 4, settingX + 16, settingY + 16)) {
            showSettingsPopup = !showSettingsPopup;
            return;
        }
        
        Font fontCat = FontRepository.getFont("Inter Bold", 16f);
        float catAreaStart = panelX + 105f;
        float catY = panelY + 13f;
        float catPos = categoryScroll;
        String[] categories = {"Combat", "Movement", "Player", "Render", "Ghost", "Network", "Minigames", "Misc"};
        
        for (String category : categories) {
            float sw = fontCat.getStringWidth(category);
            float sx = catAreaStart + catPos;
            if (isHover(mouseX, mouseY, sx - 14f, catY - 3, sx + sw + 8, catY + fontCat.getFontHeight() + 3) && mouseButton == 0) {
                setCategory(category);
                return;
            }
            catPos += sw + 26f;
        }
        
        String targetsLabel = "Targets";
        float tsx = catAreaStart + catPos;
        float tex = tsx + fontCat.getStringWidth(targetsLabel);
        if (isHover(mouseX, mouseY, tsx - 3, catY - 3, tex + 8, catY + fontCat.getFontHeight() + 3) && mouseButton == 0) {
            setCategory(null);
            return;
        }
        
        if (mouseButton == 0 && isHover(mouseX, mouseY, panelX, panelY, ex, panelY + headerH)) {
            isDragging = true;
            dragOffX = mouseX - panelX;
            dragOffY = mouseY - panelY;
            return;
        }
        
        float contentTopY = panelY + headerH + 8f;
        float moduleListHeight = (ey - contentTopY) - 54f - 8f;
        
        if (isHover(mouseX, mouseY, panelX + 2, contentTopY, panelX + modulePanelWidth, contentTopY + moduleListHeight)) {
            float moduleItemHeight = 30f;
            List<Module> filteredModules = getFilteredModules();
            float currentY = contentTopY + 6f + moduleScroll;
            
            for (Module module : filteredModules) {
                if (isHover(mouseX, mouseY, panelX + 4, currentY, panelX + modulePanelWidth - 4, currentY + moduleItemHeight - 4f)) {
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
        
        if (currentModule != null) {
            Module module = null;
            for (Module mod : Miau.moduleManager.modules.values()) {
                if (mod.getName().equalsIgnoreCase(currentModule)) {
                    module = mod;
                    break;
                }
            }
            if (module != null) {
                float titleX = panelX + modulePanelWidth + 23f;
                float valueStartY = contentTopY + 48f;
                float settingW = ex - (panelX + modulePanelWidth + 8f);
                boolean isAmbience = "Ambience".equalsIgnoreCase(module.getName());
                float settingsBoxW = isAmbience ? settingW * 0.55f : (settingW - 30f);
                
                List<AbstractValueComponent> components = getComponents(module);
                float totalValueY = 0f;
                for (AbstractValueComponent component : components) {
                    if (component.value.isVisible()) {
                        float valueY = valueStartY + totalValueY + valueScroll;
                        float inc = component.mouseClicked(mouseX, mouseY, titleX, valueY, titleX + settingsBoxW, ey, mouseButton);
                        if (inc == -1f) {
                            draggingComponent = component;
                            dragBarX = component.getBarStartX(titleX);
                            dragBarX2 = titleX + settingsBoxW;
                            return;
                        }
                        if (inc == 0f) return;
                        totalValueY += inc;
                    }
                }
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
                
                float headerH = getHeaderHeight();
                float contentTopY = panelY + headerH + 8f;
                float moduleListHeight = (endY() - contentTopY) - 54f - 8f;
                
                if (isHover(mouseX, mouseY, panelX, contentTopY, panelX + modulePanelWidth, contentTopY + moduleListHeight)) {
                    moduleScroll += (wheel > 0 ? 20f : -20f);
                    
                    float moduleItemHeight = 30f;
                    List<Module> filteredModules = getFilteredModules();
                    float totalHeight = filteredModules.size() * moduleItemHeight + 8f;
                    float maxScroll = Math.min(0f, moduleListHeight - totalHeight);
                    
                    if (moduleScroll > 0f) moduleScroll = 0f;
                    if (moduleScroll < maxScroll) moduleScroll = maxScroll;
                } else if (isHover(mouseX, mouseY, panelX + modulePanelWidth, contentTopY, endX(), endY())) {
                    valueScroll += (wheel > 0 ? 20f : -20f);
                    if (valueScroll > 0f) valueScroll = 0f;
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
    
    public static class AmbiencePresetButton {
        public float x, y, width, height;
        public int targetTime;
        public AmbiencePresetButton(float x, float y, float width, float height, int targetTime) {
            this.x = x; this.y = y; this.width = width; this.height = height; this.targetTime = targetTime;
        }
    }
    
    // ========== VALUE COMPONENTS ==========
    
    public abstract static class AbstractValueComponent {
        public Property<?> value;
        
        protected AbstractValueComponent(Property<?> value) {
            this.value = value;
        }
        
        protected Font getFont() {
            return FontRepository.getFont("Inter Regular", 14f);
        }
        
        public void drawValueName(float x, float y) {
            getFont().draw(value.getName(), x, y + 2f, TEXT_SELECTED.getRGB(), false);
        }
        
        public float getBarStartX(float x) {
            return x;
        }
        
        public abstract float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2);
        
        public abstract float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton);
        
        public void updateDrag(int mouseX, float barX, float barX2) {
        }
    }

    public static class ColorValueComponent extends AbstractValueComponent {
        private boolean expanded = false;
        private String draggingChannel = null;

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

        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            Color curCol = getColorVal();
            float previewX = x2 - 24f;
            
            RoundedUtils.drawRound(previewX, y, 20f, 12f, 3f, curCol);
            RoundedUtils.drawRoundOutline(previewX, y, 20f, 12f, 3f, 1f, Color.WHITE, Color.WHITE);

            if (!expanded) return 24f;

            float subY = y + 18f;
            float barX = x + 8f;
            float barW = (x2 - x) - 16f;

            subY += drawSliderRow("R", curCol.getRed(), barX, subY, barW, new Color(255, 80, 80));
            subY += drawSliderRow("G", curCol.getGreen(), barX, subY, barW, new Color(80, 255, 80));
            subY += drawSliderRow("B", curCol.getBlue(), barX, subY, barW, new Color(80, 180, 255));

            if (draggingChannel != null && Mouse.isButtonDown(0)) {
                float pct = Math.max(0f, Math.min(1f, (mouseX - (barX + 15f)) / (barW - 15f)));
                int val = Math.round(pct * 255f);
                if ("R".equals(draggingChannel)) setColorVal(new Color(val, curCol.getGreen(), curCol.getBlue()));
                if ("G".equals(draggingChannel)) setColorVal(new Color(curCol.getRed(), val, curCol.getBlue()));
                if ("B".equals(draggingChannel)) setColorVal(new Color(curCol.getRed(), curCol.getGreen(), val));
            }

            return subY - y;
        }

        private float drawSliderRow(String channel, int value, float x, float y, float width, Color col) {
            Font font = getFont();
            font.draw(channel, x, y, col.getRGB(), false);
            float barX = x + 15f;
            float barW = width - 15f;
            RoundedUtils.drawRound(barX, y + 3f, barW, 3f, 1.5f, new Color(255, 255, 255, 20));

            float fillW = barW * (value / 255f);
            if (fillW > 0) RoundedUtils.drawRound(barX, y + 3f, fillW, 3f, 1.5f, col);

            font.draw(String.valueOf(value), barX + barW + 6f, y, TEXT_UNSELECTED.getRGB(), false);
            return 15f;
        }

        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            float previewX = x2 - 24f;
            if (mouseButton == 0 && mouseX >= previewX && mouseX <= previewX + 20f && mouseY >= y && mouseY <= y + 12f) {
                expanded = !expanded;
                return 0f;
            }

            if (expanded && mouseButton == 0) {
                float subY = y + 18f;
                float barX = x + 23f;
                float barW = (x2 - x) - 30f;

                if (mouseY >= subY && mouseY <= subY + 15f && mouseX >= barX && mouseX <= barX + barW) {
                    draggingChannel = "R";
                    return -1f;
                }
                if (mouseY >= subY + 15f && mouseY <= subY + 30f && mouseX >= barX && mouseX <= barX + barW) {
                    draggingChannel = "G";
                    return -1f;
                }
                if (mouseY >= subY + 30f && mouseY <= subY + 45f && mouseX >= barX && mouseX <= barX + barW) {
                    draggingChannel = "B";
                    return -1f;
                }
            }
            return 24f;
        }
    }
    
    public static class BoolValueComponent extends AbstractValueComponent {
        private BooleanProperty v;
        
        public BoolValueComponent(BooleanProperty v) {
            super(v);
            this.v = v;
        }
        
        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            float switchW = 28f;
            float switchH = 14f;
            float switchX = x2 - switchW;
            float switchY = y + 1f;
            
            if (v.getValue()) {
                RoundedUtils.drawRound(switchX, switchY, switchW, switchH, switchH / 2f, themeColor);
                RoundedUtils.drawRound(switchX + switchW - switchH + 2f, switchY + 2f, switchH - 4f, switchH - 4f, (switchH - 4f) / 2f, Color.WHITE);
            } else {
                RoundedUtils.drawRound(switchX, switchY, switchW, switchH, switchH / 2f, new Color(255, 255, 255, 25));
                RoundedUtils.drawRound(switchX + 2f, switchY + 2f, switchH - 4f, switchH - 4f, (switchH - 4f) / 2f, TEXT_UNSELECTED);
            }
            return 22f;
        }
        
        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            if (mouseButton == 0 && isHover(mouseX, mouseY, x, y - 1, x2, y + 16f)) {
                v.setValue(!v.getValue());
                return 0f;
            }
            return 22f;
        }
        
        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }
    
    public static class IntValueComponent extends AbstractValueComponent {
        private IntProperty v;
        
        public IntValueComponent(IntProperty v) {
            super(v);
            this.v = v;
        }
        
        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            int minBound = getIntPropMin(v);
            int maxBound = getIntPropMax(v);
            int rangeBound = maxBound - minBound;
            
            int curVal = v.getValue();
            float percent = rangeBound <= 0 ? 0f : (float) (curVal - minBound) / (float) rangeBound;
            percent = Math.max(0f, Math.min(1f, percent));
            
            String display = String.valueOf(curVal);
            Font font = getFont();
            float strW = font.getStringWidth(display);
            float valBoxW = Math.max(32f, strW + 8f);
            
            RoundedUtils.drawRound(x2 - valBoxW, y - 1f, valBoxW, 13f, 3f, new Color(0, 0, 0, 50));
            font.draw(display, x2 - valBoxW + (valBoxW - strW) / 2f, y + 1f, TEXT_SELECTED.getRGB(), false);
            
            float barY = y + 18f;
            float barW = x2 - x;
            float barH = 3f;
            
            RoundedUtils.drawRound(x, barY, barW, barH, 1.5f, new Color(255, 255, 255, 20));
            
            float fillEnd = barW * percent;
            if (fillEnd > 0) {
                RoundedUtils.drawRound(x, barY - 1f, fillEnd, barH + 2f, 2f, new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 70));
                RoundedUtils.drawRound(x, barY, fillEnd, barH, 1.5f, themeColor);
            }
            
            float knobX = x + fillEnd;
            RoundedUtils.drawRound(knobX - 4f, barY + (barH / 2f) - 4f, 8f, 8f, 4f, new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 100));
            RoundedUtils.drawRound(knobX - 3f, barY + (barH / 2f) - 3f, 6f, 6f, 3f, Color.WHITE);
            
            return 26f;
        }
        
        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            float barY = y + 18f;
            if (mouseButton == 0 && isHover(mouseX, mouseY, x, barY - 3f, x2, barY + 6f)) {
                updateDrag(mouseX, x, x2);
                return -1f;
            }
            return 26f;
        }
        
        @Override
        public void updateDrag(int mouseX, float barX, float barX2) {
            float span = barX2 - barX;
            float percent = span <= 0f ? 0f : (mouseX - barX) / span;
            percent = Math.max(0f, Math.min(1f, percent));
            int minBound = getIntPropMin(v);
            int maxBound = getIntPropMax(v);
            int newValue = Math.round(minBound + percent * (maxBound - minBound));
            v.setValue(newValue);
        }
        
        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }
    
    public static class FloatValueComponent extends AbstractValueComponent {
        private FloatProperty v;
        
        public FloatValueComponent(FloatProperty v) {
            super(v);
            this.v = v;
        }
        
        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            float minBound = getFloatPropMin(v);
            float maxBound = getFloatPropMax(v);
            float rangeBound = maxBound - minBound;
            
            float curVal = v.getValue();
            float percent = rangeBound <= 0f ? 0f : (curVal - minBound) / rangeBound;
            percent = Math.max(0f, Math.min(1f, percent));
            
            String display = String.format("%.2f", curVal);
            Font font = getFont();
            float strW = font.getStringWidth(display);
            float valBoxW = Math.max(36f, strW + 8f);
            
            RoundedUtils.drawRound(x2 - valBoxW, y - 1f, valBoxW, 13f, 3f, new Color(0, 0, 0, 50));
            font.draw(display, x2 - valBoxW + (valBoxW - strW) / 2f, y + 1f, TEXT_SELECTED.getRGB(), false);
            
            float barY = y + 18f;
            float barW = x2 - x;
            float barH = 3f;
            
            RoundedUtils.drawRound(x, barY, barW, barH, 1.5f, new Color(255, 255, 255, 20));
            
            float fillEnd = barW * percent;
            if (fillEnd > 0) {
                RoundedUtils.drawRound(x, barY - 1f, fillEnd, barH + 2f, 2f, new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 70));
                RoundedUtils.drawRound(x, barY, fillEnd, barH, 1.5f, themeColor);
            }
            
            float knobX = x + fillEnd;
            RoundedUtils.drawRound(knobX - 4f, barY + (barH / 2f) - 4f, 8f, 8f, 4f, new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 100));
            RoundedUtils.drawRound(knobX - 3f, barY + (barH / 2f) - 3f, 6f, 6f, 3f, Color.WHITE);
            
            return 26f;
        }
        
        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            float barY = y + 18f;
            if (mouseButton == 0 && isHover(mouseX, mouseY, x, barY - 3f, x2, barY + 6f)) {
                updateDrag(mouseX, x, x2);
                return -1f;
            }
            return 26f;
        }
        
        @Override
        public void updateDrag(int mouseX, float barX, float barX2) {
            float span = barX2 - barX;
            float percent = span <= 0f ? 0f : (mouseX - barX) / span;
            percent = Math.max(0f, Math.min(1f, percent));
            float minBound = getFloatPropMin(v);
            float maxBound = getFloatPropMax(v);
            float newValue = minBound + percent * (maxBound - minBound);
            newValue = Math.round(newValue * 100f) / 100f;
            v.setValue(newValue);
        }
        
        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
        }
    }
    
    public static class ListValueComponent extends AbstractValueComponent {
        private ModeProperty v;
        
        public ListValueComponent(ModeProperty v) {
            super(v);
            this.v = v;
        }
        
        @Override
        public float drawValue(int mouseX, int mouseY, float x, float y, float x2, float y2) {
            String[] modes = v.getModes();
            String selected = (modes != null && v.getValue() >= 0 && v.getValue() < modes.length) 
                              ? modes[v.getValue()] 
                              : String.valueOf(v.getValue());
                               
            Font font = getFont();
            float strW = font.getStringWidth(selected);
            float boxW = Math.max(55f, strW + 16f);
            float boxX = x2 - boxW;
            
            RoundedUtils.drawRound(boxX, y - 1f, boxW, 14f, 3f, new Color(0, 0, 0, 50));
            font.draw(selected, boxX + 6f, y + 1f, TEXT_SELECTED.getRGB(), false);
            font.draw("v", boxX + boxW - 10f, y, TEXT_UNSELECTED.getRGB(), false);
            
            return 22f;
        }
        
        @Override
        public float mouseClicked(int mouseX, int mouseY, float x, float y, float x2, float y2, int mouseButton) {
            if (mouseButton == 0 && isHover(mouseX, mouseY, x, y - 1f, x2, y + 14f)) {
                String[] modes = v.getModes();
                int maxModes = (modes != null && modes.length > 0) ? modes.length : 10;
                
                int nextIndex = v.getValue() + 1;
                if (nextIndex >= maxModes) {
                    nextIndex = 0;
                }
                v.setValue(nextIndex);
                return 0f;
            }
            return 22f;
        }
        
        private boolean isHover(int mx, int my, float x, float y, float x2, float y2) {
            return mx >= x && mx <= x2 && my >= y && my <= y2;
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