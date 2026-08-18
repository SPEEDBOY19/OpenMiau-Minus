package miau.module.modules.render;

import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;

import miau.ui.clickgui.ClickGui;
import miau.ui.clickgui.demise.PanelGui;
import miau.ui.clickgui.faiths.FaithsClickGui;
import miau.ui.clickgui.augustus.AugustusClickGui;
import miau.ui.clickgui.miauminus.MiauMinusClickGui;
import miau.ui.clickgui.normal.ClickGuiScreen;
import miau.ui.clickgui.rise.RiseClickGui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.*;

public class ClickGUI extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private boolean switchingGuiStyle;

  private static final int[] COLORS = {
      0xFF4FC3F7, // Sky Blue
      0xFF81C784, // Green
      0xFFFF8A65, // Orange
      0xFFBA68C8, // Purple
      0xFFFFD54F, // Yellow
      0xFFFF6F6B, // Red
      0xFF4DB6AC, // Teal
      0xFFFFFFFF, // White
  };
  private static final String[] COLOR_NAMES = {
      "Sky Blue", "Green", "Orange", "Purple", "Yellow", "Red", "Teal", "White"
  };

  public final ModeProperty accentColor = new ModeProperty("Color", 0, COLOR_NAMES);
  
  public final ModeProperty style = new ModeProperty(
      "Style", 0, new String[]{"Miau", "Rise", "Faiths", "Demise", "Normal", "Augustus", "styles miauminus"}
  );

  public final ModeProperty theme =
      new ModeProperty(
          "Theme",
          0,
          new String[] {"Default", "Dark", "Blue", "Red", "Green", "Purple", "Orange", "Cyan"});

  public final ModeProperty character =
      new ModeProperty(
          "Character", 0, miau.ui.clickgui.faiths.FaithsCharacterRenderer.getCharacterArray());

  public final BooleanProperty saveGuiState = new BooleanProperty("Save GUI State", true);
  public final BooleanProperty blur = new BooleanProperty("Blur", true);
  public final BooleanProperty shaders = new BooleanProperty("Shaders", false);
  public final BooleanProperty showCharacter = new BooleanProperty("show-character", true);

  public final IntProperty windowWidth = new IntProperty("Window Width", 600, 300, 1200);
  public final IntProperty windowHeight = new IntProperty("Window Height", 400, 200, 800);
  public final FloatProperty cornerRadius = new FloatProperty("Corner Radius", 8.0f, 0.0f, 20.0f);

  // GUI instance holders
  private ClickGui clickGui;
  private FaithsClickGui faithsClickGui;
  private AugustusClickGui augustusClickGui;
  private MiauMinusClickGui miauMinusClickGui;
  private ClickGuiScreen normalClickGuiScreen;
  private RiseClickGui riseClickGui;

  public ClickGUI() {
    super("ClickGUI", false);
    setKey(Keyboard.KEY_RSHIFT);
  }

  public Color getAccentColor() {
    int idx = accentColor.getValue();
    if (idx < 0 || idx >= COLORS.length) idx = 0;
    return new Color(COLORS[idx], true);
  }

  public GuiScreen getSelectedGui() {
    int modeVal = style.getValue();
    switch (modeVal) {
      case 0: // Miau (Default ClickGUI)
        if (clickGui == null) {
          clickGui = new ClickGui();
        }
        return clickGui;
      case 1: // Rise
        if (riseClickGui == null) {
          riseClickGui = new RiseClickGui();
        }
        return riseClickGui;
      case 2: // Faiths
        if (faithsClickGui == null) {
          faithsClickGui = new FaithsClickGui();
        }
        return faithsClickGui;
      case 3: // Demise
        return new PanelGui();
      case 4: // Normal
        if (normalClickGuiScreen == null) {
          normalClickGuiScreen = ClickGuiScreen.getInstance();
        }
        return normalClickGuiScreen != null ? normalClickGuiScreen : new ClickGuiScreen();
      case 5: // Augustus
        if (augustusClickGui == null) {
          augustusClickGui = new AugustusClickGui();
        }
        return augustusClickGui;
      case 6: // MiauMinus
        if (miauMinusClickGui == null) {
          miauMinusClickGui = new MiauMinusClickGui();
        }
        return miauMinusClickGui;
      default:
        if (clickGui == null) {
          clickGui = new ClickGui();
        }
        return clickGui;
    }
  }

  public void openSelectedGui() {
    GuiScreen screen = getSelectedGui();
    this.switchingGuiStyle = mc.currentScreen instanceof ClickGui
        || mc.currentScreen instanceof ClickGuiScreen
        || mc.currentScreen instanceof RiseClickGui
        || mc.currentScreen instanceof FaithsClickGui
        || mc.currentScreen instanceof PanelGui
        || mc.currentScreen instanceof AugustusClickGui;
    try {
      mc.displayGuiScreen(screen);
    } finally {
      this.switchingGuiStyle = false;
    }
  }

  public boolean isSwitchingGuiStyle() {
    return switchingGuiStyle;
  }

  @Override
  public void verifyValue(String name) {
    if ("Style".equalsIgnoreCase(name)) {
      if (mc.currentScreen instanceof ClickGui || mc.currentScreen instanceof ClickGuiScreen
          || mc.currentScreen instanceof RiseClickGui
          || mc.currentScreen instanceof FaithsClickGui 
          || mc.currentScreen instanceof PanelGui
          || mc.currentScreen instanceof AugustusClickGui) {
        openSelectedGui();
      }
    }
  }

  @Override
  public void onEnabled() {
    setEnabled(false);
    if (mc.theWorld == null) {
      return;
    }
    character.setModes(miau.ui.clickgui.faiths.FaithsCharacterRenderer.getCharacterArray());
    openSelectedGui();
  }

  @Override
  public void onDisabled() {
    super.onDisabled();
    mc.displayGuiScreen(null);
    if (mc.currentScreen == null) {
      mc.setIngameFocus();
    }
  }

  public void checkModeSwitch() {
    if (mc.currentScreen == null) return;
    int currentMode = style.getValue();
    
    if (currentMode == 0 && !(mc.currentScreen instanceof ClickGui)) {
      openSelectedGui();
    } else if (currentMode == 1 && !(mc.currentScreen instanceof RiseClickGui)) {
      openSelectedGui();
    } else if (currentMode == 2 && !(mc.currentScreen instanceof FaithsClickGui)) {
      openSelectedGui();
    } else if (currentMode == 3 && !(mc.currentScreen instanceof PanelGui)) {
      openSelectedGui();
    } else if (currentMode == 4 && !(mc.currentScreen instanceof ClickGuiScreen)) {
      openSelectedGui();
    } else if (currentMode == 5 && !(mc.currentScreen instanceof AugustusClickGui)) {
      openSelectedGui();
    } else if (currentMode == 6 && !(mc.currentScreen instanceof MiauMinusClickGui)) {
      openSelectedGui();
    }
  }
}