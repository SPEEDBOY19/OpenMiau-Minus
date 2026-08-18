package miau.ui.clickgui.components.impl;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import miau.module.Module;
import miau.ui.clickgui.animation.ScrollOffsetAnimation;
import miau.ui.clickgui.components.Component;
import miau.util.animation.AnimationTimer;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

public class CategoryComponent {
  private static long interactionSequence;
  private static final Map<String, CategoryIconStacks> CATEGORY_ICON_STACKS =
      buildCategoryIconStacks();

  public List<Component> modules = new CopyOnWriteArrayList<>();
  public String category;
  public boolean opened;
  public float width;
  public float y;
  public float x;
  public float titleHeight;
  public boolean dragging;
  public float xx;
  public float yy;
  public boolean hovering = false;
  public boolean hoveringOverCategory = false;
  public AnimationTimer smoothTimer;
  public AnimationTimer guiOpenTimer;
  private AnimationTimer textTimer;
  public float big;

  private static final int TRANSLUCENT_BACKGROUND = new Color(0, 0, 0, 110).getRGB();
  private static final int REGULAR_OUTLINE = new Color(81, 99, 149).getRGB();
  private static final int REGULAR_OUTLINE2 = new Color(97, 67, 133).getRGB();
  private static final int CATEGORY_NAME_COLOR = new Color(220, 220, 220).getRGB();

  public float lastHeight;
  private float lastNamePos;
  private float animationStartNamePos;
  public float moduleY;
  private float screenHeight;
  private float screenWidth;
  private float animationStartHeight;

  private final ScrollOffsetAnimation scrollAnim = new ScrollOffsetAnimation(200);
  public long lastInteractedTime = 0L;

  public float renderX;
  public float renderY;
  private long lastRenderMS = System.currentTimeMillis();

  private float cachedExtra;
  private float cachedLiftY;
  private float cachedHoverAnim;
  private float cachedRenderModuleY;

  private static final class CategoryLayoutMetrics {
    private final float visibleHeight;
    private final float minScrollY;
    private final float contentBottom;

    private CategoryLayoutMetrics(float visibleHeight, float minScrollY, float contentBottom) {
      this.visibleHeight = visibleHeight;
      this.minScrollY = minScrollY;
      this.contentBottom = contentBottom;
    }
  }

  private static final class CategoryIconStacks {
    private final ItemStack normalStack;
    private final ItemStack activeStack;

    private CategoryIconStacks(ItemStack normalStack, ItemStack activeStack) {
      this.normalStack = normalStack;
      this.activeStack = activeStack;
    }
  }

  public CategoryComponent(String category) {
    this.category = category;
    this.width = this.category.equalsIgnoreCase("Config") ? 135 : 92;
    this.renderX = this.x = 5;
    this.renderY = this.moduleY = this.y = 5;
    this.titleHeight = 13;
    float moduleRenderY = this.titleHeight + 3;
    scrollAnim.reset(this.moduleY);

    this.lastHeight = this.y + this.titleHeight + 4;
    this.animationStartHeight = this.lastHeight;

    List<Module> mods = miau.Miau.moduleManager.getModulesByCategory().get(category);
    if (mods != null) {
      for (Module mod : mods) {
        ModuleComponent b = new ModuleComponent(mod, this, moduleRenderY);
        this.modules.add(b);
        moduleRenderY += 16;
      }
    }
  }

  public List<Component> getModules() {
    return this.modules;
  }

  public void reloadModules() {
    Map<String, Boolean> openStates = captureModuleOpenStates();
    this.modules.clear();
    this.titleHeight = 13;
    float moduleRenderY = this.titleHeight + 3;

    if (this.category.equalsIgnoreCase("Search")) {
      SearchBarComponent searchBar = new SearchBarComponent(this, moduleRenderY);
      this.modules.add(searchBar);
      syncAfterModuleReload();
      return;
    }

    if (this.category.equalsIgnoreCase("Themes")) {
      for (miau.util.render.Themes theme : miau.util.render.Themes.values()) {
        ThemeSelectComponent tsc = new ThemeSelectComponent(this, moduleRenderY, theme);
        this.modules.add(tsc);
        moduleRenderY += tsc.getHeightF();
      }
      syncAfterModuleReload();
      return;
    }

    List<Module> mods = miau.Miau.moduleManager.getModulesByCategory().get(this.category);
    if (mods != null) {
      for (Module mod : mods) {
        ModuleComponent component = new ModuleComponent(mod, this, moduleRenderY);
        component.restoreOpenState(Boolean.TRUE.equals(openStates.get(mod.getName())));
        this.modules.add(component);
        moduleRenderY += 16;
      }
    }
    syncAfterModuleReload();
  }

  public void updateSearchResults(String query) {
    if (!this.category.equalsIgnoreCase("Search")) return;

    Map<String, Boolean> openStates = captureModuleOpenStates();

    Component searchBar = null;
    if (!this.modules.isEmpty() && this.modules.get(0) instanceof SearchBarComponent) {
      searchBar = this.modules.get(0);
    }

    this.modules.clear();

    float moduleRenderY = this.titleHeight + 3;

    if (searchBar != null) {
      this.modules.add(searchBar);
      moduleRenderY += searchBar.getHeightF();
    }

    if (query != null && !query.trim().isEmpty()) {
      String lowerQuery = query.toLowerCase().replace(" ", "");

      for (Module mod : miau.Miau.moduleManager.modules.values()) {
        if (mod.getName().equalsIgnoreCase("ClickGUI") || mod.getName().equalsIgnoreCase("GUI"))
          continue;

        if (mod.getName().toLowerCase().replace(" ", "").contains(lowerQuery)) {
          ModuleComponent component = new ModuleComponent(mod, this, moduleRenderY);
          component.restoreOpenState(Boolean.TRUE.equals(openStates.get(mod.getName())));
          this.modules.add(component);
          moduleRenderY += component.getHeightF();
        }
      }
    }

    syncAfterModuleReload();
  }

  private Map<String, Boolean> captureModuleOpenStates() {
    Map<String, Boolean> openStates = new HashMap<>();
    for (Component moduleComponent : this.modules) {
      if (moduleComponent instanceof ModuleComponent
          && ((ModuleComponent) moduleComponent).mod != null) {
        openStates.put(
            ((ModuleComponent) moduleComponent).mod.getName(),
            ((ModuleComponent) moduleComponent).isOpened);
      }
    }
    return openStates;
  }

  private void syncAfterModuleReload() {
    CategoryLayoutMetrics layoutMetrics =
        computeLayoutMetrics(this.opened || this.smoothTimer != null);
    float minScrollY = layoutMetrics.minScrollY;
    float maxScrollY = this.y;
    float clampedScroll = Math.max(minScrollY, Math.min(maxScrollY, scrollAnim.getTarget()));
    this.moduleY = clampedScroll;
    scrollAnim.reset(clampedScroll);

    if (this.opened && !this.modules.isEmpty()) {
      this.big = layoutMetrics.visibleHeight;
      this.lastHeight = layoutMetrics.contentBottom;
      return;
    }

    if (!this.opened && this.smoothTimer == null) {
      this.big = 0f;
    }
    this.lastHeight = this.y + this.titleHeight + 4;
  }

  public void setX(float newX, boolean limit) {
    if (limit) {
      newX = Math.max(newX, 2);
      newX = Math.min(newX, screenWidth - this.width - 4);
    }
    this.x = newX;
  }

  public void setY(float y, boolean limit) {
    if (limit) {
      y = Math.max(y, 1);
      float maxY = screenHeight - this.titleHeight - 5;
      y = Math.min(y, maxY);
    }

    float scrollOffset = scrollAnim.getTarget() - this.y;
    this.y = y;
    float newTarget = y + scrollOffset;
    this.moduleY = newTarget;
    scrollAnim.reset(newTarget);
  }

  public void overTitle(boolean d) {
    this.dragging = d;
  }

  public boolean isOpened() {
    return this.opened;
  }

  public void markInteracted() {
    this.lastInteractedTime = ++interactionSequence;
  }

  public void mouseClicked(boolean on) {
    this.animationStartHeight = getCurrentAnimatedCategoryHeight();
    this.animationStartNamePos = getCurrentAnimatedNamePos();

    float animationDuration = 250.0f;

    this.opened = on;
    (this.smoothTimer = new AnimationTimer(animationDuration)).start();
    (this.textTimer = new AnimationTimer(animationDuration)).start();
  }

  public void onScroll(int mouseScrollInput) {
    onScroll(mouseScrollInput, Float.NaN, Float.NaN);
  }

  public void onScroll(int mouseScrollInput, float mouseX, float mouseY) {
    for (Component mod : this.modules) {
      mod.onScroll(mouseScrollInput);
    }
    if (!hoveringOverCategory || !this.opened) {
      return;
    }
    this.markInteracted();
    float scrollSpeed = 10f;
    float minScrollY = computeMinScrollY();
    float maxScrollY = this.y;
    float delta = scrollSpeed * (mouseScrollInput / 120f);
    if (delta != 0f) {
      scrollAnim.extend(delta);
    }
    scrollAnim.clampTarget(minScrollY, maxScrollY);
  }

  private float computeMinScrollY() {
    return computeLayoutMetrics(false).minScrollY;
  }

  public void render(FontRenderer renderer) {
    this.width = this.category.equalsIgnoreCase("Config") ? 135 : 92;
    Font titleRenderer = FontRepository.getMinecraftFont();

    long currentMS = System.currentTimeMillis();
    float delta = currentMS - lastRenderMS;
    lastRenderMS = currentMS;
    if (delta > 50 || delta < 0) delta = 16;

    float speed = 0.02f * delta;
    if (Math.abs(renderX - x) > 0.1f) {
      renderX += (x - renderX) * speed;
    } else {
      renderX = x;
    }

    if (Math.abs(renderY - y) > 0.1f) {
      renderY += (y - renderY) * speed;
    } else {
      renderY = y;
    }

    if (smoothTimer != null && System.currentTimeMillis() - smoothTimer.last >= 280) {
      smoothTimer = null;
    }
    if (textTimer != null && System.currentTimeMillis() - textTimer.last >= 280) {
      textTimer = null;
    }

    for (Component c : this.modules) {
      if (c instanceof ModuleComponent) {
        ((ModuleComponent) c).updateAnimationState();
      }
    }

    CategoryLayoutMetrics layoutMetrics = computeLayoutMetrics(this.opened || smoothTimer != null);
    big = (!this.opened && smoothTimer == null) ? 0f : layoutMetrics.visibleHeight;
    float maxScrollY = this.renderY;
    float minScrollY = layoutMetrics.minScrollY - (this.y - this.renderY);

    scrollAnim.clampTarget(layoutMetrics.minScrollY, this.y);

    moduleY = scrollAnim.getValue();
    moduleY = Math.max(layoutMetrics.minScrollY, Math.min(this.y, moduleY));
    float renderModuleY = moduleY - (this.y - this.renderY);

    float middlePos =
        this.renderX + this.width / 2 - titleRenderer.width(this.category.toLowerCase()) / 2.0f;

    float contentBottom = layoutMetrics.contentBottom - (this.y - this.renderY);

    float extra;
    if (smoothTimer != null) {
      float targetHeight = this.opened ? contentBottom : (this.renderY + this.titleHeight + 4);
      extra =
          smoothTimer.getValueFloat(
              animationStartHeight - (this.y - this.renderY), targetHeight, 1);
      if ((this.opened && extra > targetHeight) || (!this.opened && extra < targetHeight)) {
        extra = targetHeight;
      }
    } else {
      extra = contentBottom;
    }

    float targetNamePos = this.opened ? middlePos : (this.renderX + 12);
    float namePos;
    if (textTimer == null) {
      namePos = targetNamePos;
    } else {
      namePos =
          textTimer.getValueFloat(
              animationStartNamePos - (this.x - this.renderX), targetNamePos, 1);
    }
    this.lastNamePos = namePos + (this.x - this.renderX);
    this.lastHeight = extra + (this.y - this.renderY);

    float liftY = 0f;

    cachedExtra = extra;
    cachedLiftY = 0f;
    cachedHoverAnim = 0f;
    cachedRenderModuleY = renderModuleY;

    float openAnimationValue = 1.0f;
    if (guiOpenTimer != null) {
      openAnimationValue = guiOpenTimer.getValueFloat(0f, 1f, 1);
      if (openAnimationValue >= 1.0f) {
        guiOpenTimer = null;
      }
    }

    if (openAnimationValue < 1.0f) {
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      float scissorHeight = (extra - this.renderY) * openAnimationValue;
      RenderUtil.scissor(this.renderX - 3, this.renderY - 3, this.width + 6, scissorHeight + 6);
    }

    Color themeColor1 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(new miau.util.vector.Vector2d(this.renderX, this.renderY));
    Color themeColor2 =
        miau.util.render.Themes.getCurrentTheme()
            .getAccentColor(new miau.util.vector.Vector2d(this.renderX + this.width, extra));
    drawRoundedGradientOutlinedRectangle(
        this.renderX - 2,
        this.renderY,
        this.renderX + this.width + 2,
        extra,
        10,
        TRANSLUCENT_BACKGROUND,
        themeColor1.getRGB(),
        themeColor2.getRGB());

    // Lift everything via glTranslatef
    GL11.glPushMatrix();
    GL11.glTranslatef(0f, -liftY, 0f);

    renderItemForCategory(this.category, (int) (this.renderX + 1), (int) (this.renderY + 4), false);
    titleRenderer.draw(
        this.category.toLowerCase(), namePos, this.renderY + 4, CATEGORY_NAME_COLOR, false);

    float moduleAreaTop = this.renderY + this.titleHeight + 3 - liftY;
    float scissorBottom = extra - 2f - liftY;
    float moduleAreaHeight = Math.max(0f, scissorBottom - moduleAreaTop);

    if (this.opened || smoothTimer != null) {
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      RenderUtil.scissor(0, moduleAreaTop, this.renderX + this.width + 4, moduleAreaHeight);

      float scrollOffset = renderModuleY - this.renderY;
      GL11.glPushMatrix();
      GL11.glTranslatef(this.renderX - this.x, this.renderY - this.y + scrollOffset, 0f);
      for (Component c2 : this.modules) {
        c2.render();
      }
      GL11.glPopMatrix();

      GL11.glDisable(GL11.GL_SCISSOR_TEST);

      // Render overlays outside category scissor so dropdowns always draw on top (Fix D)
      for (Component c2 : this.modules) {
        if (c2 instanceof ModuleComponent) {
          ((ModuleComponent) c2).renderOverlays();
        }
      }
    }

    if (openAnimationValue < 1.0f) {
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    GL11.glPopMatrix();
  }

  public void renderBloom(FontRenderer renderer) {
    if (this.isOpened()) {
      for (Component c2 : this.getModules()) {
        c2.renderBloom();
      }
    }
  }

  public void updateHeight() {
    float y = this.titleHeight + 3;
    for (Component component : this.modules) {
      component.updateHeight(y);
      y += component.getHeightF();
    }
  }

  public float getX() {
    return this.x;
  }

  public float getY() {
    return this.y;
  }

  public float getModuleY() {
    return this.moduleY - cachedLiftY;
  }

  public float getWidth() {
    return this.width;
  }

  public void mousePosition(int mouseX, int mouseY, boolean isTopmostUnderCursor) {
    if (this.dragging) {
      float newX = mouseX - this.xx;
      float newY = mouseY - this.yy;

      newX = Math.max(newX, 2);
      newX = Math.min(newX, screenWidth - this.width - 4);

      newY = Math.max(newY, 1);
      int maxY = (int) (screenHeight - this.titleHeight - 5);
      newY = Math.min(newY, maxY);

      this.setX(newX, false);
      this.setY(newY, false);
    }

    hoveringOverCategory = isTopmostUnderCursor && overCategory(mouseX, mouseY);
    hovering = hoveringOverCategory;
  }

  public boolean overTitle(int x, int y) {
    float effectiveY = this.y - cachedLiftY;
    return x >= this.x
        && x <= this.x + this.width
        && (float) y >= effectiveY + 2.0F
        && y <= effectiveY + this.titleHeight + 1;
  }

  public boolean overCategory(int x, int y) {
    float effectiveY = this.y - cachedLiftY;
    return x >= this.x - 2
        && x <= this.x + this.width + 2
        && (float) y >= effectiveY + 2.0F
        && y <= effectiveY + this.titleHeight + big + 1;
  }

  public boolean draggable(int x, int y) {
    float effectiveY = this.y - cachedLiftY;
    return x >= this.x
        && x <= this.x + this.width
        && y >= effectiveY
        && y <= effectiveY + this.titleHeight;
  }

  public boolean overRect(int x, int y) {
    float effectiveY = this.y - cachedLiftY;
    float effectiveLastHeight = lastHeight - cachedLiftY;
    return x >= this.x - 2
        && x <= this.x + this.width + 2
        && y >= effectiveY
        && y <= effectiveLastHeight;
  }

  private void renderItemForCategory(String category, int x, int y, boolean enchant) {
    RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
    double scale = 0.55;
    GlStateManager.pushMatrix();
    GlStateManager.scale(scale, scale, scale);
    CategoryIconStacks iconStacks = CATEGORY_ICON_STACKS.get(category);
    ItemStack itemStack =
        iconStacks == null ? null : (enchant ? iconStacks.activeStack : iconStacks.normalStack);
    if (itemStack != null) {
      RenderHelper.enableGUIStandardItemLighting();
      GlStateManager.disableBlend();
      GlStateManager.translate((float) (x / scale), (float) (y / scale), 0);
      renderItem.renderItemAndEffectIntoGUI(itemStack, 0, 0);
      GlStateManager.enableBlend();
      RenderHelper.disableStandardItemLighting();
    }
    GlStateManager.scale(1, 1, 1);
    GlStateManager.popMatrix();
  }

  private float getCurrentAnimatedNamePos() {
    if (textTimer != null) {
      return lastNamePos;
    }
    float middlePos =
        this.x
            + this.width / 2
            - FontRepository.getMinecraftFont().width(this.category.toLowerCase()) / 2.0f;
    return this.opened ? middlePos : (this.x + 12);
  }

  private float getCurrentAnimatedCategoryHeight() {
    if (this.lastHeight > 0) {
      return this.lastHeight;
    }
    if (!this.modules.isEmpty() && (this.opened || this.smoothTimer != null)) {
      float modulesHeight = 0f;
      for (Component c : this.modules) {
        modulesHeight += c.getHeightF();
      }
      return this.y + this.titleHeight + modulesHeight + 4;
    }
    return this.y + this.titleHeight + 4;
  }

  public void setScreenSize(float screenWidth, float screenHeight) {
    this.screenWidth = screenWidth;
    this.screenHeight = screenHeight;
  }

  public void limitPositions() {
    setX(this.x, true);
    setY(this.y, true);
  }

  public void applySavedState(float x, float y, boolean opened, boolean clampToScreen) {
    if (clampToScreen) {
      setX(x, true);
      setY(y, true);
    } else {
      float scrollOffset = scrollAnim.getTarget() - this.y;
      this.x = x;
      this.y = y;
      float newTarget = y + scrollOffset;
      this.moduleY = newTarget;
      scrollAnim.reset(newTarget);
    }
    this.opened = opened;
    smoothTimer = null;
    textTimer = null;
    if (opened) {
      boolean hasContent = !this.modules.isEmpty();
      if (hasContent) {
        CategoryLayoutMetrics layoutMetrics = computeLayoutMetrics(true);
        this.big = layoutMetrics.visibleHeight;
        this.lastHeight = layoutMetrics.contentBottom;
      } else {
        this.big = 0f;
        this.lastHeight = this.y + this.titleHeight + 4;
      }
    } else {
      this.big = 0f;
      this.lastHeight = this.y + this.titleHeight + 4;
    }
    this.moduleY = this.y;
    scrollAnim.reset(this.y);
    this.renderX = this.x;
    this.renderY = this.y;
  }

  public void onGuiClosed() {
    if (smoothTimer != null || textTimer != null) {
      float finalHeight = this.y + this.titleHeight;
      if (this.opened) {
        if (!this.modules.isEmpty()) {
          float modulesHeight = 0f;
          for (Component c : this.modules) {
            modulesHeight += c.getHeightF();
          }
          finalHeight += modulesHeight + 4;
        } else {
          finalHeight += 4;
        }
      } else {
        finalHeight += 4;
      }
      this.lastHeight = finalHeight;
    }

    smoothTimer = null;
    textTimer = null;
    moduleY = scrollAnim.getTarget();
    scrollAnim.reset(moduleY);
  }

  private CategoryLayoutMetrics computeLayoutMetrics(boolean updateModuleOffsets) {
    if (this.modules.isEmpty() || (!this.opened && this.smoothTimer == null)) {
      return new CategoryLayoutMetrics(0f, this.y, this.y + this.titleHeight + 4);
    }

    float maxModulesHeight = (this.screenHeight * 0.9f) - this.titleHeight - 4;
    float visibleHeight = 0f;
    float totalScrollExtent = 0f;
    float moduleOffset = this.titleHeight + 3;

    for (Component component : this.modules) {
      if (updateModuleOffsets) {
        component.updateHeight(moduleOffset);
      }

      float componentHeight = component.getHeightF();
      moduleOffset += componentHeight;
      totalScrollExtent += component.getScrollExtentHeightF();

      if (visibleHeight < maxModulesHeight) {
        visibleHeight += Math.min(componentHeight, maxModulesHeight - visibleHeight);
      }
    }

    float viewport = Math.min(maxModulesHeight, totalScrollExtent);
    float overflow = Math.max(0f, totalScrollExtent - viewport);
    float minScrollY = overflow > 0f ? this.y - overflow : this.y;
    float maxBottom = this.y + (this.screenHeight * 0.9f);
    float contentBottom = Math.min(this.y + this.titleHeight + visibleHeight + 4, maxBottom);
    return new CategoryLayoutMetrics(Math.max(0f, visibleHeight), minScrollY, contentBottom);
  }

  private static Map<String, CategoryIconStacks> buildCategoryIconStacks() {
    Map<String, CategoryIconStacks> iconStacks = new HashMap<>();
    String[] categories =
        new String[] {
          "Combat",
          "Ghost",
          "Movement",
          "Render",
          "Player",
          "Misc",
          "Network",
          "Minigames",
          "Grind",
          "Search",
          "Themes"
        };
    for (String cat : categories) {
      ItemStack normalStack = createCategoryIconStack(cat, false);
      ItemStack activeStack = createCategoryIconStack(cat, true);
      if (normalStack != null && activeStack != null) {
        iconStacks.put(cat, new CategoryIconStacks(normalStack, activeStack));
      }
    }
    return iconStacks;
  }

  private static ItemStack createCategoryIconStack(String category, boolean active) {
    ItemStack itemStack;
    if (category.equalsIgnoreCase("Combat")) {
      itemStack = new ItemStack(Items.diamond_sword);
    } else if (category.equalsIgnoreCase("Ghost")) {
      itemStack = new ItemStack(Items.iron_sword);
    } else if (category.equalsIgnoreCase("Movement")) {
      itemStack = new ItemStack(Items.diamond_boots);
    } else if (category.equalsIgnoreCase("Render")) {
      itemStack = new ItemStack(Items.ender_eye);
    } else if (category.equalsIgnoreCase("Player")) {
      itemStack = new ItemStack(Items.golden_apple);
    } else if (category.equalsIgnoreCase("Misc")) {
      itemStack = new ItemStack(Items.clock);
    } else if (category.equalsIgnoreCase("Network")) {
      itemStack = new ItemStack(Items.redstone);
    } else if (category.equalsIgnoreCase("Minigames")) {
      itemStack = new ItemStack(Items.gold_ingot);
    } else if (category.equalsIgnoreCase("Grind")) {
      itemStack = new ItemStack(Items.iron_pickaxe);
    } else if (category.equalsIgnoreCase("Target")) {
      itemStack = new ItemStack(Items.arrow);
    } else if (category.equalsIgnoreCase("Search")) {
      itemStack = new ItemStack(Items.name_tag);
    } else if (category.equalsIgnoreCase("Themes")) {
      itemStack = new ItemStack(Items.dye, 1, 9);
    } else {
      return null;
    }

    if (!active) {
      return itemStack;
    }

    if (!category.equalsIgnoreCase("Player")) {
      itemStack.addEnchantment(Enchantment.unbreaking, 2);
    } else {
      itemStack.setItemDamage(1);
    }
    return itemStack;
  }

  /**
   * Renders a translucent rounded rect with gradient outlined border (raven-bS style). Ported from
   * keystrokesmod.utility.RenderUtils.drawRoundedGradientOutlinedRectangle.
   */
  private void drawRoundedGradientOutlinedRectangle(
      float x,
      float y,
      float x2,
      float y2,
      final float radius,
      final int n6,
      final int n7,
      final int n8) {
    x *= 2.0f;
    y *= 2.0f;
    x2 *= 2.0f;
    y2 *= 2.0f;
    GL11.glPushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glScaled(0.5, 0.5, 0.5);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    // Filled background
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    glColor(n6);
    for (int i = 0; i <= 90; i += 3) {
      final double n9 = (double) (i * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n9) * radius * -1.0,
          (double) (y + radius) + Math.cos(n9) * radius * -1.0);
    }
    for (int j = 90; j <= 180; j += 3) {
      final double n10 = (double) (j * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n10) * radius * -1.0,
          (double) (y2 - radius) + Math.cos(n10) * radius * -1.0);
    }
    for (int k = 0; k <= 90; k += 3) {
      final double n11 = (double) (k * 0.017453292f);
      GL11.glVertex2d(
          (double) (x2 - radius) + Math.sin(n11) * radius,
          (double) (y2 - radius) + Math.cos(n11) * radius);
    }
    for (int l = 90; l <= 180; l += 3) {
      final double n12 = (double) (l * 0.017453292f);
      GL11.glVertex2d(
          (double) (x2 - radius) + Math.sin(n12) * radius,
          (double) (y + radius) + Math.cos(n12) * radius);
    }
    GL11.glEnd();
    // Gradient outline
    GL11.glPushMatrix();
    GL11.glShadeModel(GL11.GL_SMOOTH);
    GL11.glLineWidth(2.0f);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    if (n7 != 0L) {
      glColor(n7);
    }
    for (int n13 = 0; n13 <= 90; n13 += 3) {
      final double n14 = (double) (n13 * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n14) * radius * -1.0,
          (double) (y + radius) + Math.cos(n14) * radius * -1.0);
    }
    for (int n15 = 90; n15 <= 180; n15 += 3) {
      final double n16 = (double) (n15 * 0.017453292f);
      GL11.glVertex2d(
          (double) (x + radius) + Math.sin(n16) * radius * -1.0,
          (double) (y2 - radius) + Math.cos(n16) * radius * -1.0);
    }
    if (n8 != 0) {
      glColor(n8);
    }
    for (int n17 = 0; n17 <= 90; n17 += 3) {
      final double n18 = (double) (n17 * 0.017453292f);
      GL11.glVertex2d(
          (double) (x2 - radius) + Math.sin(n18) * radius,
          (double) (y2 - radius) + Math.cos(n18) * radius);
    }
    for (int n19 = 90; n19 <= 180; n19 += 3) {
      final double n20 = (double) (n19 * 0.017453292f);
      GL11.glVertex2d(
          (double) (x2 - radius) + Math.sin(n20) * radius,
          (double) (y + radius) + Math.cos(n20) * radius);
    }
    GL11.glEnd();
    GL11.glPopMatrix();
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glPopAttrib();
    GL11.glPopMatrix();
    GL11.glLineWidth(1.0f);
    GL11.glShadeModel(GL11.GL_FLAT);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  private void glColor(int color) {
    GL11.glColor4f(
        (float) (color >> 16 & 0xFF) / 255.0f,
        (float) (color >> 8 & 0xFF) / 255.0f,
        (float) (color & 0xFF) / 255.0f,
        (float) (color >> 24 & 0xFF) / 255.0f);
  }
}
