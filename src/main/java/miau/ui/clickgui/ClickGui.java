package miau.ui.clickgui;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import miau.module.modules.render.ClickGUI;
import miau.ui.clickgui.components.Component;
import miau.ui.clickgui.components.impl.BindComponent;
import miau.ui.clickgui.components.impl.CategoryComponent;
import miau.ui.clickgui.components.impl.ModuleComponent;
import miau.ui.clickgui.components.impl.SearchBarComponent;
import miau.ui.clickgui.components.impl.SliderComponent;
import miau.util.animation.AnimationTimer;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import miau.util.render.Themes;
import miau.util.shader.RoundedUtils;
import miau.util.vector.Vector2d;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ClickGui extends GuiScreen {
  public static float openingScale = 1.0f;
  private AnimationTimer backgroundFade;
  private AnimationTimer blurSmooth;
  private AnimationTimer scaleAnimation = new AnimationTimer(300.0F);
  private AnimationTimer tabTransitionAnim = new AnimationTimer(450.0F); // Tăng thời gian lên 450ms để animation chậm và mượt hơn[cite: 12]
  private ScaledResolution sr;
  public static ArrayList<CategoryComponent> categories;
  public static int lastMouseX;
  public static int lastMouseY;

 
  public static SliderComponent activeModeDropdown = null;

  private ConfigWindow configWindow;

  private static final ResourceLocation TAB_CLICKGUI_IMAGE =
      new ResourceLocation("miau/clickgui_tab.png");
  private static final ResourceLocation TAB_CONFIG_IMAGE =
      new ResourceLocation("miau/config_tab.png");
  private static final int TAB_CLICKGUI = 0;
  private static final int TAB_CONFIG = 1;
  public static int selectedTab = TAB_CLICKGUI;
  private static final float TAB_BAR_HEIGHT = 24.0f;
  private static final float TAB_BAR_TOP = 8.0f;
  private static final float TAB_WIDTH = 88.0f;
  private static final float TAB_ICON_SIZE = 14.0f;
  private static final float CONTENT_TOP = TAB_BAR_TOP + TAB_BAR_HEIGHT + 12.0f;

  private int actualScreenWidth;
  private int actualScreenHeight;
  private boolean pendingScaleRefresh;
  private long lastMS = System.currentTimeMillis();


  public ClickGui() {
    categories = new ArrayList<>();
    String[] catNames =
        new String[] {
          "Combat",
          "Ghost",
          "Movement",
          "Player",
          "Render",
          "Misc",
          "Search",
          "Themes",
          "Network",
          "Minigames",
          "Grind"
        };

    net.minecraft.client.gui.ScaledResolution sr =
        new net.minecraft.client.gui.ScaledResolution(
            net.minecraft.client.Minecraft.getMinecraft());
    int screenWidth = sr.getScaledWidth();

    float startX = 15;
    float startY = CONTENT_TOP;
    float marginX = 105;
    float marginY = 60;

    float currentX = startX;
    float currentY = startY;

    for (String name : catNames) {
      CategoryComponent cc = new CategoryComponent(name);
      if (currentX + cc.width + 10 > screenWidth) {
        currentX = startX;
        currentY += marginY;
      }
      cc.setX(currentX, false);
      cc.setY(currentY, false);
      categories.add(cc);
      currentX += marginX;
    }
  }

 
  public void initMain() {
     (this.blurSmooth = this.backgroundFade = new AnimationTimer(500.0F)).start();
  }

  private void updateAutoLayout(float delta) {
    float startX = 15, startY = CONTENT_TOP;
    float marginX = 105, marginY = 10;

    for (int col = 0; col < 20; col++) {
      final int currentCol = col;
      List<CategoryComponent> inCol = new ArrayList<>();
      for (CategoryComponent c : categories) {
        int cCol = Math.round((c.getX() - startX) / marginX);
        if (cCol == currentCol) inCol.add(c);
      }
      inCol.sort(Comparator.comparingDouble(CategoryComponent::getY));

      float currentY = startY;
      for (CategoryComponent c : inCol) {
        if (!c.dragging) {
          c.setY(lerp(c.getY(), currentY, 0.015f * delta), false);
        } else {
          currentY = c.getY();
        }
        currentY += (c.lastHeight - c.getY()) + marginY;
      }
    }
  }

  private float lerp(float start, float end, float delta) {
    return start + (end - start) * delta;
  }

  @Override
  public void initGui() {
    super.initGui();
    miau.ui.clickgui.faiths.FaithsCharacterRenderer.resetAnimation();
    this.scaleAnimation.start();
    ClickGui.openingScale = 0.5f;
    this.sr = new ScaledResolution(mc);
    this.actualScreenWidth = this.sr.getScaledWidth();
    this.actualScreenHeight = this.sr.getScaledHeight();

    int delay = 0;
    for (CategoryComponent categoryComponent : categories) {
      categoryComponent.setScreenSize(this.width, this.height);
      categoryComponent.limitPositions();
      categoryComponent.reloadModules();

      categoryComponent.guiOpenTimer = new AnimationTimer(250 + delay * 80);
      categoryComponent.guiOpenTimer.start();
      delay++;
    }

    selectedTab = TAB_CLICKGUI;
    tabTransitionAnim.start();

    if (configWindow == null) {
      configWindow = new ConfigWindow((actualScreenWidth - 360) / 2f, CONTENT_TOP + 15f);
    } else {
      configWindow.refreshLocalConfigs();
    }
  }

  private List<CategoryComponent> getCategoriesInRenderOrder() {
    List<CategoryComponent> renderOrder = new ArrayList<>(categories);
    renderOrder.sort(Comparator.comparingLong(c -> c.lastInteractedTime));
    return renderOrder;
  }

  private CategoryComponent getTopmostUnderCursor(
      List<CategoryComponent> renderOrder, int x, int y) {
    for (int i = renderOrder.size() - 1; i >= 0; i--) {
      if (renderOrder.get(i).overRect(x, y)) {
        return renderOrder.get(i);
      }
    }
    return null;
  }

  @Override
  public void drawScreen(int x, int y, float p) {
    long currentMS = System.currentTimeMillis();
    float delta = currentMS - lastMS;
    lastMS = currentMS;
    if (delta > 50 || delta < 0) delta = 16;

    float centerX = this.width / 2.0f;
    float centerY = this.height / 2.0f;

    float scaleFactor = 1.0f;
  
    ClickGui.openingScale = scaleFactor;

    int scaledX = x;
    int scaledY = y;
    lastMouseX = scaledX;
    lastMouseY = scaledY;

    updateAutoLayout(delta);

    miau.module.modules.render.HUD hudModule =
        (miau.module.modules.render.HUD)
            miau.Miau.moduleManager.modules.get(miau.module.modules.render.HUD.class);
    ClickGUI guiModule = (ClickGUI) miau.Miau.moduleManager.modules.get(ClickGUI.class);
    if (guiModule != null) guiModule.checkModeSwitch();

    int bgColorAlpha = (int) (130 * this.scaleAnimation.getValueFloat(0.0f, 1.0f, 1));
    drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, bgColorAlpha).getRGB());
    miau.ui.clickgui.faiths.FaithsCharacterRenderer.renderCharacter(1.0f);

    List<CategoryComponent> renderOrder = getCategoriesInRenderOrder();
    CategoryComponent topmostUnderCursor = getTopmostUnderCursor(renderOrder, scaledX, scaledY);

    float openEase =
        (float)
            miau.util.animation.Easing.EASE_OUT_EXPO.apply(
                this.scaleAnimation.getValueFloat(0.0f, 1.0f, 1));
    float slideInY = (1.0f - openEase) * this.height * 0.15f;

    GL11.glPushMatrix();
    GL11.glTranslatef(0f, slideInY, 0);
    GL11.glTranslatef(centerX, centerY, 0);
    GL11.glScaled(scaleFactor, scaleFactor, 1.0);
    GL11.glTranslatef(-centerX, -centerY, 0);

    drawTabBar(scaledX, scaledY);

    // Tính toán animation chuyển tab và hiệu ứng bảng Config từ dưới bay lên mượt mà hơn
    float tabAnimProgress = tabTransitionAnim.getValueFloat(0.0f, 1.0f, 1);
    float tabAnimEase = (float) miau.util.animation.Easing.EASE_OUT_EXPO.apply(tabAnimProgress);

    if (selectedTab == TAB_CLICKGUI) {
      GL11.glPushMatrix();
      float alphaClickGui = tabAnimEase;
      GlStateManager.color(1.0f, 1.0f, 1.0f, alphaClickGui);
      
      for (CategoryComponent c : renderOrder) {
        c.render(this.fontRendererObj);
        c.mousePosition(scaledX, scaledY, c == topmostUnderCursor);

        for (Component m : c.getModules()) {
          m.drawScreen(scaledX, scaledY);
        }
      }
      GL11.glPopMatrix();

      SliderComponent dropdown = activeModeDropdown;
      activeModeDropdown = null;
      if (dropdown != null && dropdown.isModeDropdownActive()) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        dropdown.renderModeDropdownOverlay(scaledX, scaledY);
      }
    } else {
      // Tab Config: Hiệu ứng trượt từ dưới lên mượt mà, chậm rãi
      GL11.glPushMatrix();
      float configOffsetY = (1.0f - tabAnimEase) * 140.0f; // Khoảng cách trượt từ dưới lên
      float configAlpha = tabAnimEase;
      
      GL11.glTranslatef(0, configOffsetY, 0);
      GlStateManager.color(1.0f, 1.0f, 1.0f, configAlpha);

      if (configWindow != null) {
        configWindow.drawWindow(scaledX, scaledY, delta);
      }
      GL11.glPopMatrix();
    }

    GL11.glPopMatrix();
  }

  private void drawTabBar(int mouseX, int mouseY) {
    float barW = TAB_WIDTH * 2f + 11f;
    float barX = (this.width - barW) / 2f;
    float barY = TAB_BAR_TOP;
    float barH = TAB_BAR_HEIGHT;

    Color themeColor =
        Themes.getCurrentTheme().getAccentColor(new Vector2d(barX, barY));
    Color themeColor2 =
        Themes.getCurrentTheme().getAccentColor(new Vector2d(barX + barW, barY + barH));

    RoundedUtils.drawRoundOutline(
        barX, barY, barW, barH, 8.0f, 1.2f, new Color(18, 18, 18, 235), themeColor);

    float tabY = barY + 3f;
    float tabHeight = barH - 6f;
    float dividerX = barX + 5f + TAB_WIDTH;
    drawTabButton(
        "Clickgui",
        TAB_CLICKGUI_IMAGE,
        barX + 5f,
        tabY,
        TAB_WIDTH,
        tabHeight,
        selectedTab == TAB_CLICKGUI,
        mouseX,
        mouseY,
        themeColor);
    drawTabButton(
        "Config",
        TAB_CONFIG_IMAGE,
        dividerX + 1f,
        tabY,
        TAB_WIDTH,
        tabHeight,
        selectedTab == TAB_CONFIG,
        mouseX,
        mouseY,
        themeColor2);

    RenderUtil.drawLine(
        dividerX,
        barY + 5f,
        dividerX,
        barY + barH - 5f,
        1.0f,
        new Color(60, 60, 60, 120).getRGB());
  }

  private void drawTabButton(
      String label,
      ResourceLocation image,
      float x,
      float y,
      float width,
      float height,
      boolean selected,
      int mouseX,
      int mouseY,
      Color themeColor) {
    boolean hovered = isInRect(mouseX, mouseY, x, y, width, height);

    if (selected) {
      Color fill =
          new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 40);
      RoundedUtils.drawRound(x, y, width, height, 6.0f, fill);
    } else if (hovered) {
      RoundedUtils.drawRound(x, y, width, height, 6.0f, new Color(255, 255, 255, 18));
    }

    float iconSize = TAB_ICON_SIZE;
    drawImage(image, x + 6f, y + (height - iconSize) / 2f, iconSize, iconSize);

    Font font = FontRepository.getHudFont(11);
    int textColor = selected ? -1 : new Color(170, 170, 170).getRGB();
    float textX = x + 8f + iconSize;
    float textY = y + (height - 10f) / 2f;
    font.draw(label, textX, textY, textColor);
  }

  private void drawImage(ResourceLocation loc, float x, float y, float width, float height) {
    try {
      mc.getTextureManager().bindTexture(loc);
      GlStateManager.enableBlend();
      GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
      Gui.drawModalRectWithCustomSizedTexture(
          (int) x, (int) y, 0.0f, 0.0f, (int) width, (int) height, width, height);
      GlStateManager.disableBlend();
      GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    } catch (Exception ignored) {
    }
  }

  private boolean handleTabBarClick(int mouseX, int mouseY) {
    float barW = TAB_WIDTH * 2f + 11f;
    float barX = (this.width - barW) / 2f;
    float barY = TAB_BAR_TOP;
    float barH = TAB_BAR_HEIGHT;
    if (!isInRect(mouseX, mouseY, barX, barY, barW, barH)) {
      return false;
    }
    float tabY = barY + 3f;
    float tabHeight = barH - 6f;
    float dividerX = barX + 5f + TAB_WIDTH;
    
    if (isInRect(mouseX, mouseY, barX + 5f, tabY, TAB_WIDTH, tabHeight)) {
      if (selectedTab != TAB_CLICKGUI) {
        selectedTab = TAB_CLICKGUI;
        tabTransitionAnim = new AnimationTimer(450.0F); // Cập nhật lại thời gian 450ms khi click[cite: 12]
        tabTransitionAnim.start();
      }
      return true;
    }
    if (isInRect(mouseX, mouseY, dividerX + 1f, tabY, TAB_WIDTH, tabHeight)) {
      if (selectedTab != TAB_CONFIG) {
        selectedTab = TAB_CONFIG;
        tabTransitionAnim = new AnimationTimer(450.0F); // Cập nhật lại thời gian 450ms khi click[cite: 12]
        tabTransitionAnim.start();
      }
      return true;
    }
    return false;
  }

  private boolean isInRect(int mouseX, int mouseY, float x, float y, float width, float height) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }

  private SliderComponent getHoveredModeDropdown(int mouseX, int mouseY) {
    for (CategoryComponent category : categories) {
      if (!category.isOpened()) {
        continue;
      }
      for (Component component : category.getModules()) {
        if (!(component instanceof ModuleComponent)) {
          continue;
        }
        SliderComponent dropdown = ((ModuleComponent) component).getActiveModeDropdown();
        if (dropdown != null && dropdown.isMouseOverModeDropdown(mouseX, mouseY)) {
          return dropdown;
        }
      }
    }
    return null;
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
    float centerX = this.width / 2.0f;
    float centerY = this.height / 2.0f;
    float progress = this.scaleAnimation.getValueFloat(0.0f, 1.0f, 1);
    float ease = (float) miau.util.animation.Easing.EASE_OUT_EXPO.apply(progress);
    float scaleFactor = 0.8f + (0.2f * ease);
    int scaledX = (int) (centerX + (mouseX - centerX) / scaleFactor);
    int scaledY = (int) (centerY + (mouseY - centerY) / scaleFactor);

    if (handleTabBarClick(scaledX, scaledY)) {
      return;
    }

    if (selectedTab == TAB_CONFIG) {
      if (configWindow != null) configWindow.mouseClicked(scaledX, scaledY, mouseButton);
      return;
    }

    List<CategoryComponent> inputOrder = new ArrayList<>(categories);
    inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));

  
    if (handleActiveModeDropdownClick(inputOrder, scaledX, scaledY, mouseButton)) {
      return;
    }

    CategoryComponent topmostCategory = null;
    for (CategoryComponent category : inputOrder) {
      if (category.overRect(scaledX, scaledY)) {
        topmostCategory = category;
        break;
      }
    }

    if (topmostCategory != null) topmostCategory.markInteracted();

    if (mouseButton == 0) {
      for (CategoryComponent category : categories) category.overTitle(false);
      if (topmostCategory != null && topmostCategory.draggable(scaledX, scaledY)) {
        topmostCategory.overTitle(true);
        topmostCategory.xx = scaledX - topmostCategory.getX();
        topmostCategory.yy = scaledY - topmostCategory.getY();
        topmostCategory.dragging = true;
      }
    }

    if (mouseButton == 1
        && topmostCategory != null
        && topmostCategory.overTitle(scaledX, scaledY)) {
      topmostCategory.mouseClicked(!topmostCategory.isOpened());
    }

    if (topmostCategory != null
        && topmostCategory.isOpened()
        && !topmostCategory.getModules().isEmpty()
        && !topmostCategory.overTitle(scaledX, scaledY)) {
      for (Component component : topmostCategory.getModules()) {
        if (component.onClick(scaledX, scaledY, mouseButton)) break;
      }
    }
  }

  private boolean handleActiveModeDropdownClick(
      List<CategoryComponent> inputOrder, int scaledX, int scaledY, int mouseButton) {
    for (CategoryComponent category : inputOrder) {
      if (!category.isOpened()) {
        continue;
      }
      for (Component component : category.getModules()) {
        if (!(component instanceof ModuleComponent)) {
          continue;
        }
        ModuleComponent module = (ModuleComponent) component;
        SliderComponent dropdown = module.getActiveModeDropdown();
        if (dropdown != null && dropdown.isMouseOverModeDropdown(scaledX, scaledY)) {
          category.markInteracted();
          module.onClick(scaledX, scaledY, mouseButton);
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void mouseReleased(int x, int y, int button) {
    if (this.scaleAnimation.getValueFloat(0.0f, 1.0f, 1) < 0.95f) return;

    float centerX = this.width / 2.0f;
    float centerY = this.height / 2.0f;
    float progress = this.scaleAnimation.getValueFloat(0.0f, 1.0f, 1);
    float ease = (float) miau.util.animation.Easing.EASE_OUT_EXPO.apply(progress);
    float scaleFactor = 0.8f + (0.2f * ease);
    int scaledX = (int) (centerX + (x - centerX) / scaleFactor);
    int scaledY = (int) (centerY + (y - centerY) / scaleFactor);

    if (selectedTab == TAB_CONFIG) {
      if (configWindow != null) configWindow.mouseReleased(scaledX, scaledY, button);
      return;
    }

    if (button == 0) {
      for (CategoryComponent category : categories) {
        category.overTitle(false);
        if (category.isOpened() && !category.getModules().isEmpty()) {
          for (Component module : category.getModules()) {
            module.mouseReleased(scaledX, scaledY, button);
          }
        }
      }
    }
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    if (this.scaleAnimation.getValueFloat(0.0f, 1.0f, 1) < 0.95f) return;

    int wheelInput = Mouse.getDWheel();
    if (wheelInput != 0) {
      int mouseX = Mouse.getEventX() * this.width / mc.displayWidth;
      int mouseY = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;

      float centerX = this.width / 2.0f;
      float centerY = this.height / 2.0f;
      float progress = this.scaleAnimation.getValueFloat(0.0f, 1.0f, 1);
      float ease = (float) miau.util.animation.Easing.EASE_OUT_EXPO.apply(progress);
      float scaleFactor = 0.8f + (0.2f * ease);
      int scaledX = (int) (centerX + (mouseX - centerX) / scaleFactor);
      int scaledY = (int) (centerY + (mouseY - centerY) / scaleFactor);

      SliderComponent dropdown = getHoveredModeDropdown(scaledX, scaledY);
      if (dropdown != null) {
        dropdown.scrollModeDropdown(wheelInput);
        return;
      }

      if (selectedTab == TAB_CONFIG) {
        if (configWindow != null) configWindow.onScroll(wheelInput, scaledX, scaledY);
        return;
      }

      for (CategoryComponent category : categories) {
        category.onScroll(wheelInput);
      }
    }
  }

  @Override
  public void keyTyped(char t, int k) {
    if (selectedTab == TAB_CONFIG) {
      if (configWindow != null && configWindow.keyTyped(t, k)) return;
      if (k == Keyboard.KEY_ESCAPE) {
        this.mc.displayGuiScreen(null);
      }
      return;
    }

   
    boolean isBinding = binding();

    SearchBarComponent searchBar = null;
    CategoryComponent searchCategory = null;

    
    for (CategoryComponent category : categories) {
      if (category.category.equalsIgnoreCase("Search")) {
        searchCategory = category;
        if (!category.getModules().isEmpty()
            && category.getModules().get(0) instanceof SearchBarComponent) {
          searchBar = (SearchBarComponent) category.getModules().get(0);
        }
        break;
      }
    }

 
    if (searchBar != null && searchCategory != null) {
    
      if (searchBar.focused) {
     
        if (k == Keyboard.KEY_ESCAPE) {
          searchBar.focused = false;
          return;
        }
      } else if (!isBinding
          && k != Keyboard.KEY_ESCAPE
          && k != Keyboard.KEY_RETURN
          && k != Keyboard.KEY_BACK) {
   
            if (String.valueOf(t).matches("[a-zA-Z0-9 ]")) {
          if (!searchCategory.isOpened()) {
            searchCategory.mouseClicked(true);
          }
  
          searchBar.focused = true;
  
        }
      }
    }

   
    if (k == Keyboard.KEY_ESCAPE) {
      if (!isBinding) {
        this.mc.displayGuiScreen(null);
        return;
      }
    }


    for (CategoryComponent category : categories) {
      if (category.isOpened() && !category.getModules().isEmpty()) {
        for (Component module : category.getModules()) {
          module.keyTyped(t, k);
        }
      }
    }
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }

  private boolean binding() {
    for (CategoryComponent c : categories) {
      for (Component m : c.getModules()) {
        if (m instanceof ModuleComponent) {
          for (Component component : ((ModuleComponent) m).settings) {
            if (component instanceof BindComponent && ((BindComponent) component).isBinding)
              return true;
          }
        }
      }
    }
    return false;
  }

  public void onSliderChange() {
    for (CategoryComponent c : categories) {
      for (Component m : c.getModules()) {
        if (m instanceof ModuleComponent) ((ModuleComponent) m).onSliderChange();
      }
    }
  }

  public void requestScaleRefresh() {
    this.pendingScaleRefresh = true;
  }

  public static double getActiveRenderScale() {
    return 1.0D;
  }

  public void drawForEffects(boolean bloom) {
    if (!bloom) {
      RoundedUtils.drawRound(0, 0, this.width, this.height, 0.0f, true, new Color(0, 0, 0, 150));
    } else {
      RoundedUtils.drawRound(0, 0, this.width, this.height, 0.0f, true, new Color(81, 99, 149, 80));

      float centerX = this.width / 2.0f;
      float centerY = this.height / 2.0f;
      GL11.glPushMatrix();
      GL11.glTranslatef(centerX, centerY, 0);
      GL11.glScaled(openingScale, openingScale, 1.0);
      GL11.glTranslatef(-centerX, -centerY, 0);

      List<CategoryComponent> renderOrder = getCategoriesInRenderOrder();
      if (selectedTab == TAB_CLICKGUI) {
        for (CategoryComponent c : renderOrder) {
          c.renderBloom(this.mc.fontRendererObj);
        }
      } else if (configWindow != null) {
        configWindow.drawWindow(lastMouseX, lastMouseY, 0);
      }
      GL11.glPopMatrix();
    }
  }
}
