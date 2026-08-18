package miau.ui.clickgui.normal;

import java.io.IOException;
import miau.Miau;
import miau.ui.clickgui.ConfigWindow;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Normal-style ClickGUI stub. Extends GuiScreen so the ClickGUI module compiles.
 * TODO: implement full Normal GUI design.
 */
public class ClickGuiScreen extends GuiScreen {
  private static ClickGuiScreen instance;
  private ConfigWindow configWindow;

  public ClickGuiScreen() {}

  public static ClickGuiScreen getInstance() {
    if (instance == null) {
      instance = new ClickGuiScreen();
    }
    return instance;
  }

  @Override
  public void initGui() {
    super.initGui();
    ScaledResolution sr = new ScaledResolution(mc);
    if (configWindow == null) {
      configWindow = new ConfigWindow(sr.getScaledWidth() - 350, sr.getScaledHeight() - 250);
    } else {
      configWindow.refreshLocalConfigs();
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    miau.module.modules.render.ClickGUI guiModule =
        (miau.module.modules.render.ClickGUI)
            Miau.moduleManager.modules.get(miau.module.modules.render.ClickGUI.class);
    if (guiModule != null) guiModule.checkModeSwitch();

    ScaledResolution sr = new ScaledResolution(mc);
    drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), 0x90000000);
    this.fontRendererObj.drawStringWithShadow(
        "DOnt use it", sr.getScaledWidth() / 2 - 55, sr.getScaledHeight() / 2, -1);
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
    if (configWindow != null) configWindow.mouseReleased(mouseX, mouseY, state);
    super.mouseReleased(mouseX, mouseY, state);
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getDWheel();
    if (wheel != 0) {
      ScaledResolution sr = new ScaledResolution(mc);
      int mouseX = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
      int mouseY =
          sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;
      if (configWindow != null) configWindow.onScroll(wheel, mouseX, mouseY);
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
  public boolean doesGuiPauseGame() {
    return false;
  }
}