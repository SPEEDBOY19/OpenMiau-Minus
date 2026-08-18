package miau.ui.clickgui.demise;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.module.Module;
import miau.ui.clickgui.demise.component.Category;
import miau.ui.clickgui.demise.component.SearchCategory;
import miau.ui.clickgui.demise.component.config.ConfigCategoryComponent;
import miau.util.demise.RoundedUtils;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class PanelGui extends GuiScreen {
  private final List<Category> categories = new ArrayList<Category>();
  public Category selectedCategory;
  public ConfigCategoryComponent selectedConfigCategory;
  public SearchCategory selectedSearchCategory;
  public static boolean dragging;
  private float dragX, dragY;
  public static float posX = 255, posY = 120;
  private final ConfigCategoryComponent configCategoryComponent;
  private final SearchCategory searchCategoryComponent;
  public static float interpolatedScale;
  private boolean closing;

  public PanelGui() {
    float height = 45;

    for (Module module : Miau.moduleManager.modules.values()) {
      String cat = module.getCategory();
      if (cat == null) continue;
      Category existing = null;
      for (Category c : categories) {
        if (c.getCategoryName().equalsIgnoreCase(cat)) {
          existing = c;
          break;
        }
      }
      if (existing == null) {
        existing = new Category(cat, posX + 7, posY + height);
        categories.add(existing);
        height += FontRepository.getFont("Inter Regular", 18f).height() + 7;
      }
      existing.addModule(module);
    }

    configCategoryComponent = new ConfigCategoryComponent(posX + 7, posY + height);
    searchCategoryComponent = new SearchCategory();

    if (selectedCategory == null && !categories.isEmpty()) {
      selectedCategory = categories.get(0);
    }
  }

  @Override
  public void initGui() {
    closing = false;
    interpolatedScale = 0;

    if (selectedConfigCategory != null) {
      selectedConfigCategory.initGui();
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    interpolatedScale = interpolate(interpolatedScale, !closing ? 1 : 0, 0.25f);

    if (interpolatedScale < 0.01f && closing) {
      mc.displayGuiScreen(null);
    }

    ScaledResolution sr = new ScaledResolution(mc);
    RenderUtil.scaleStart(sr.getScaledWidth() / 2f, sr.getScaledHeight() / 2f, interpolatedScale);

    if (dragging) {
      float deltaX = mouseX - (dragX + posX);
      float deltaY = mouseY - (dragY + posY);
      posX = mouseX - dragX;
      posY = mouseY - dragY;

      for (Category category : categories) {
        category.setX(category.getX() + deltaX);
        category.setY(category.getY() + deltaY);
      }

      configCategoryComponent.setX(configCategoryComponent.getX() + deltaX);
      configCategoryComponent.setY(configCategoryComponent.getY() + deltaY);
    }

    boolean skipped = true;

    for (Category category : categories) {
      boolean hovered =
          isHovered(
              category.getX(),
              category.getY(),
              FontRepository.getFont("Inter Regular", 18f)
                  .getStringWidth(category.getCategoryName()),
              FontRepository.getFont("Inter Regular", 18f).height(),
              mouseX,
              mouseY);

      if (hovered && Mouse.isButtonDown(0)) {
        if (selectedCategory != category) {
          category.initCategory();
        }

        selectedCategory = category;
        selectedConfigCategory = null;
        selectedSearchCategory = null;

        skipped = false;
      }

      category.setHovered(hovered);
      category.setSelected(selectedCategory != null && selectedCategory == category);
    }

    boolean skipped1 = true;

    if (skipped) {
      boolean hovered =
          isHovered(
              configCategoryComponent.getX(),
              configCategoryComponent.getY(),
              FontRepository.getFont("Inter Regular", 18f).getStringWidth("Configs"),
              FontRepository.getFont("Inter Regular", 18f).height(),
              mouseX,
              mouseY);

      if (hovered && Mouse.isButtonDown(0)) {
        if (selectedConfigCategory == null) {
          configCategoryComponent.initCategory();
        }

        selectedConfigCategory = configCategoryComponent;
        selectedCategory = null;
        selectedSearchCategory = null;
        skipped1 = false;
      }

      configCategoryComponent.setHovered(hovered);
      configCategoryComponent.setSelected(selectedConfigCategory != null);
    }

    RoundedUtils.drawRound(posX, posY, 450, 300, 7, new Color(0, 0, 0, 140));

    float x = posX + 7;
    float y = posY + 7;

    FontRepository.getFont("Inter Bold", 35f)
        .draw("Miau", (double) x, (double) y, new Color(255, 255, 255, 208).getRGB());
    FontRepository.getFont("Inter Bold", 24f)
        .draw(
            "1.2.0",
            (double) (FontRepository.getFont("Inter Bold", 35f).getStringWidth("Miau") + 2 + x),
            (double)
                (FontRepository.getFont("Inter Bold", 35f).height()
                    + y
                    - FontRepository.getFont("Inter Bold", 24f).height() * 1.1f),
            new Color(245, 245, 245, 208).getRGB());

    float watermarkWidth =
        FontRepository.getFont("Inter Bold", 35f).getStringWidth("Miau")
            + 2
            + FontRepository.getFont("Inter Bold", 24f).getStringWidth("1.2.0");
    float calcWidth = 450 - watermarkWidth - 19;

    RoundedUtils.drawRound(
        posX + watermarkWidth + 13, posY + 7, calcWidth, 20, 7, new Color(0, 0, 0, 100));

    boolean searchHovered =
        isHovered(posX + watermarkWidth + 13, posY + 7, calcWidth, 20, mouseX, mouseY);
    if (searchHovered && Mouse.isButtonDown(0) && skipped1) {
      if (selectedSearchCategory == null) {
        searchCategoryComponent.initCategory();
      }

      selectedSearchCategory = searchCategoryComponent;
      selectedCategory = null;
      selectedConfigCategory = null;
    }

    searchCategoryComponent.setSelected(selectedSearchCategory != null);

    if (selectedSearchCategory == null) {
      FontRepository.getFont("Inter Regular", 18f)
          .draw(
              "Search...",
              (double) (posX + watermarkWidth + 18),
              (double) (posY + 7 + FontRepository.getFont("Inter Regular", 15f).height() - 2),
              new Color(147, 147, 147, 255).getRGB());
    }

    configCategoryComponent.render(false);
    if (selectedConfigCategory != null) {
      selectedConfigCategory.drawScreen(mouseX, mouseY);
    }

    searchCategoryComponent.render(false);
    if (selectedSearchCategory != null) {
      selectedSearchCategory.drawScreen(mouseX, mouseY);
    }

    for (Category category : categories) {
      category.render(false);
    }
    if (selectedCategory != null) {
      selectedCategory.drawScreen(mouseX, mouseY);
    }

    int total = Miau.moduleManager.modules.size();
    long enabled = Miau.moduleManager.modules.values().stream().filter(Module::isEnabled).count();
    String str = "Total modules: " + total + ", Enabled: " + enabled;

    FontRepository.getFont("Inter Regular", 14f)
        .draw(
            str,
            (double)
                (posX + 450 - FontRepository.getFont("Inter Regular", 14f).getStringWidth(str) - 4),
            (double) (posY + 300 - FontRepository.getFont("Inter Regular", 14f).height()),
            new Color(255, 255, 255, 208).getRGB());
    FontRepository.getFont("Inter Regular", 14f)
        .draw(
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            (double) (posX + 3.5),
            (double) (posY + 300 - FontRepository.getFont("Inter Regular", 14f).height()),
            new Color(255, 255, 255, 208).getRGB());

    RenderUtil.scaleEnd();
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (mouseButton == 0 && isHovered(posX, posY, 450, 35, mouseX, mouseY)) {
      dragging = true;
      dragX = mouseX - posX;
      dragY = mouseY - posY;
    }

    if (selectedSearchCategory != null) {
      selectedSearchCategory.mouseClicked(mouseX, mouseY, mouseButton);
      return;
    }
    if (selectedConfigCategory != null) {
      selectedConfigCategory.mouseClicked(mouseX, mouseY, mouseButton);
      return;
    }
    if (selectedCategory != null) {
      selectedCategory.mouseClicked(mouseX, mouseY, mouseButton);
    }
  }

  @Override
  protected void mouseReleased(int mouseX, int mouseY, int state) {
    dragging = false;

    if (selectedSearchCategory != null) {
      selectedSearchCategory.mouseReleased(mouseX, mouseY, state);
      return;
    }
    if (selectedConfigCategory != null) {
      selectedConfigCategory.mouseReleased(mouseX, mouseY, state);
      return;
    }
    if (selectedCategory != null) {
      selectedCategory.mouseReleased(mouseX, mouseY, state);
    }
  }

  @Override
  public void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      closing = true;
    }

    if (keyCode == Keyboard.KEY_TAB && selectedCategory != null && !categories.isEmpty()) {
      selectedCategory =
          categories.get((categories.indexOf(selectedCategory) + 1) % categories.size());
    }

    if (closing) {
      return;
    }

    if (selectedSearchCategory != null) {
      selectedSearchCategory.keyTyped(typedChar, keyCode);
      return;
    }
    if (selectedConfigCategory != null) {
      selectedConfigCategory.keyTyped(typedChar, keyCode);
      return;
    }
    if (selectedCategory != null) {
      selectedCategory.keyTyped(typedChar, keyCode);
    }
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }

  private float interpolate(float current, float target, float factor) {
    return current + (target - current) * factor;
  }

  public static boolean isHovered(
      float x, float y, float width, float height, float mouseX, float mouseY) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }
}
