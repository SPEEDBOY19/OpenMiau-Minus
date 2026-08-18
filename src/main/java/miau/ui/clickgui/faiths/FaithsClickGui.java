package miau.ui.clickgui.faiths;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import miau.module.Module;
import miau.ui.clickgui.ConfigWindow;
import miau.util.render.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public class FaithsClickGui extends GuiScreen {
  private List<FaithsWindow> windows = new ArrayList<>();
  private FaithsThemeWindow themeWindow;
  private ConfigWindow configWindow;

  public FaithsClickGui() {}

  private void refreshWindows() {
    windows.clear();
    ScaledResolution sr = new ScaledResolution(mc);
    float screenWidth = sr.getScaledWidth();
    float xPos = 15;
    float yPos = 20;

    java.util.LinkedHashMap<String, List<Module>> cats =
        miau.Miau.moduleManager.getModulesByCategory();
    for (String catName : cats.keySet()) {
      if (xPos + 115 > screenWidth) {
        xPos = 15;
        yPos += 210;
      }
      FaithsWindow window = new FaithsWindow(catName, xPos, yPos);
      windows.add(window);
      xPos += 125;
    }
    if (xPos + 115 > screenWidth) {
      xPos = 15;
      yPos += 210;
    }
    themeWindow = new FaithsThemeWindow(xPos, yPos);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    miau.module.modules.render.ClickGUI guiModule =
        (miau.module.modules.render.ClickGUI)
            miau.Miau.moduleManager.modules.get(miau.module.modules.render.ClickGUI.class);
    if (guiModule != null) guiModule.checkModeSwitch();
    ScaledResolution sr = new ScaledResolution(mc);
    RenderUtil.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), 0x96000000);
    FaithsCharacterRenderer.renderCharacter(1.0f);
    for (FaithsWindow window : windows) {
      window.renderWindow(mouseX, mouseY);
    }
    if (themeWindow != null) {
      themeWindow.renderWindow(mouseX, mouseY);
    }
    if (configWindow != null) {
      configWindow.drawWindow(mouseX, mouseY, 16.0f);
    }
    super.drawScreen(mouseX, mouseY, partialTicks);
  }

  @Override
  protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
    if (configWindow != null && configWindow.mouseClicked(mouseX, mouseY, mouseButton)) {
      return;
    }
    super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  protected void mouseReleased(int mouseX, int mouseY, int state) {
    if (configWindow != null) {
      configWindow.mouseReleased(mouseX, mouseY, state);
    }
    if (themeWindow != null) {
      themeWindow.mouseReleased(mouseX, mouseY, state);
    }
    for (FaithsWindow window : windows) {
      window.mouseReleased(mouseX, mouseY, state);
    }
    super.mouseReleased(mouseX, mouseY, state);
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheelInput = Mouse.getDWheel();
    if (wheelInput != 0) {
      ScaledResolution sr = new ScaledResolution(mc);
      int mouseX = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
      int mouseY =
          sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;
      if (configWindow != null && configWindow.onScroll(wheelInput, mouseX, mouseY)) {
        return;
      }
      for (FaithsWindow window : windows) {
        if (window.onScroll(wheelInput, mouseX, mouseY)) {
          return;
        }
      }
      if (themeWindow != null) {
        themeWindow.onScroll(wheelInput, mouseX, mouseY);
      }
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) throws IOException {
    if (configWindow != null && configWindow.keyTyped(typedChar, keyCode)) {
      return;
    }
    super.keyTyped(typedChar, keyCode);
  }

  @Override
  public void initGui() {
    super.initGui();
    FaithsCharacterRenderer.resetAnimation();
    if (windows.isEmpty()) {
      refreshWindows();
    }
    ScaledResolution sr = new ScaledResolution(mc);
    if (configWindow == null) {
      configWindow = new ConfigWindow(sr.getScaledWidth() - 350, sr.getScaledHeight() - 250);
    } else {
      configWindow.refreshLocalConfigs();
    }
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
