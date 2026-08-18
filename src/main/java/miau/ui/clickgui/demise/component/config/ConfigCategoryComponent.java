package miau.ui.clickgui.demise.component.config;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import miau.config.online.OnlineConfigClient;
import miau.config.online.OnlineConfigEntry;
import miau.ui.clickgui.demise.IComponent;
import miau.ui.clickgui.demise.PanelGui;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ConfigCategoryComponent implements IComponent {
  private float x, y;
  private boolean isHovered, isSelected;
  private float interpolatedX;
  private float interpolatedLineWidth;
  private float scrollOffset = 0;
  private float targetScrollOffset = 0;
  private float maxScroll = 0;
  private String name = "Configs";
  private final List<ConfigComponent> configs = new ArrayList<ConfigComponent>();

  private int selectedTab = 0;
  private boolean localHovered, onlineHovered, userHovered;

  private final List<File> localConfigs = new ArrayList<>();
  private final List<OnlineConfigEntry> onlineConfigs = new ArrayList<>();
  private final List<OnlineConfigEntry> userConfigs = new ArrayList<>();
  private String onlineStatus = "Loading...";
  private String userStatus = "Loading...";

  private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
  private final OnlineConfigClient onlineClient = new OnlineConfigClient();

  public ConfigCategoryComponent(float x, float y) {
    this.x = x;
    this.y = y;
    this.isSelected = false;
    this.isHovered = false;
    this.interpolatedX = x;

    refreshLocalConfigs();
    refreshOnlineConfigs();
    refreshUserConfigs();
    buildComponents();
  }

  private void refreshLocalConfigs() {
    localConfigs.clear();
    File configDir = new File("./config/Miau/");
    if (configDir.exists() && configDir.isDirectory()) {
      File[] files = configDir.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
        for (File file : files) localConfigs.add(file);
      }
    }
  }

  private void refreshOnlineConfigs() {
    onlineStatus = "Fetching configs...";
    onlineConfigs.clear();
    EXECUTOR.execute(
        () -> {
          try {
            List<OnlineConfigEntry> entries = onlineClient.list();
            Minecraft.getMinecraft()
                .addScheduledTask(
                    () -> {
                      if (entries.isEmpty()) onlineStatus = "No configs found.";
                      else {
                        onlineConfigs.addAll(entries);
                        onlineStatus = "";
                        if (selectedTab == 1) buildComponents();
                      }
                    });
          } catch (Exception e) {
            Minecraft.getMinecraft().addScheduledTask(() -> onlineStatus = "Fetch failed!");
          }
        });
  }

  private void refreshUserConfigs() {
    userStatus = "Fetching user configs...";
    userConfigs.clear();
    EXECUTOR.execute(
        () -> {
          try {
            List<OnlineConfigEntry> entries = onlineClient.listUserConfigs();
            Minecraft.getMinecraft()
                .addScheduledTask(
                    () -> {
                      if (entries.isEmpty()) userStatus = "No user configs found.";
                      else {
                        userConfigs.addAll(entries);
                        userStatus = "";
                        if (selectedTab == 2) buildComponents();
                      }
                    });
          } catch (Exception e) {
            Minecraft.getMinecraft().addScheduledTask(() -> userStatus = "Fetch failed!");
          }
        });
  }

  private void buildComponents() {
    configs.clear();
    if (selectedTab == 0) {
      for (File file : localConfigs) {
        String n = file.getName();
        if (n.endsWith(".json")) n = n.substring(0, n.length() - 5);
        configs.add(new ConfigComponent(n));
      }
    } else if (selectedTab == 1) {
      for (OnlineConfigEntry entry : onlineConfigs) {
        configs.add(new ConfigComponent(entry, false));
      }
    } else if (selectedTab == 2) {
      for (OnlineConfigEntry entry : userConfigs) {
        configs.add(new ConfigComponent(entry, true));
      }
    }
  }

  public void initCategory() {
    refreshLocalConfigs();
    buildComponents();
    for (ConfigComponent cc : configs) {
      cc.initCategory();
    }
  }

  public void initGui() {
    refreshLocalConfigs();
    buildComponents();
  }

  public void render(boolean shader) {
    float x = this.x;

    if (isSelected) {
      x += 3;
      float width = FontRepository.getFont("Inter Regular", 18f).getStringWidth(name);
      interpolatedLineWidth = animate(interpolatedLineWidth, width, 0.05f);
    } else {
      interpolatedLineWidth = animate(interpolatedLineWidth, 0, 0.05f);
    }

    if (isHovered) {
      x += 2.5f;
    }

    if (!PanelGui.dragging) {
      interpolatedX = animate(interpolatedX, x, 0.15f);
    } else {
      interpolatedX = x;
    }

    if (!shader) {
      FontRepository.getFont("Inter Regular", 18f)
          .draw(name, (double) interpolatedX, (double) y, Color.white.getRGB());
      RenderUtil.drawRect(
          interpolatedX,
          (float) (y + FontRepository.getFont("Inter Regular", 18f).height() - 2.6f),
          interpolatedX + interpolatedLineWidth,
          (float) (y + FontRepository.getFont("Inter Regular", 18f).height() - 2.6f) + 0.5f,
          Color.white.getRGB());
    }

    if (isSelected) {
      handleScroll();

      float componentStartY = PanelGui.posY + 65;
      float viewHeight = 250;

      if (!shader) {
        float tabX = PanelGui.posX + 105;
        float tabY = componentStartY - 25;

        float localW = FontRepository.getFont("Inter Regular", 16f).getStringWidth("Local");
        float onlineW = FontRepository.getFont("Inter Regular", 16f).getStringWidth("Online");
        float userW = FontRepository.getFont("Inter Regular", 16f).getStringWidth("MiauUser");

        Color c1 = selectedTab == 0 ? new Color(255, 255, 255) : new Color(150, 150, 150);
        Color c2 = selectedTab == 1 ? new Color(255, 255, 255) : new Color(150, 150, 150);
        Color c3 = selectedTab == 2 ? new Color(255, 255, 255) : new Color(150, 150, 150);

        if (localHovered && selectedTab != 0) c1 = new Color(200, 200, 200);
        if (onlineHovered && selectedTab != 1) c2 = new Color(200, 200, 200);
        if (userHovered && selectedTab != 2) c3 = new Color(200, 200, 200);

        FontRepository.getFont("Inter Regular", 16f)
            .draw("Local", (double) tabX, (double) tabY, c1.getRGB());
        FontRepository.getFont("Inter Regular", 16f)
            .draw("Online", (double) (tabX + localW + 15), (double) tabY, c2.getRGB());
        FontRepository.getFont("Inter Regular", 16f)
            .draw(
                "MiauUser",
                (double) (tabX + localW + 15 + onlineW + 15),
                (double) tabY,
                c3.getRGB());
      }

      float totalHeight = 0;
      for (int i = 0; i < configs.size(); i++) {
        totalHeight += 40;
      }

      maxScroll = Math.max(0, totalHeight - viewHeight);
      scrollOffset = animate(scrollOffset, targetScrollOffset, 0.1f);

      RenderUtil.scissor(
          0, componentStartY - 2, PanelGui.posX + 450, viewHeight, PanelGui.interpolatedScale);
      GL11.glEnable(GL11.GL_SCISSOR_TEST);

      if (!shader && selectedTab == 1 && !onlineStatus.isEmpty()) {
        FontRepository.getFont("Inter Regular", 16f)
            .draw(
                onlineStatus,
                (double) (PanelGui.posX + 105),
                (double) (componentStartY + 10),
                new Color(200, 200, 200).getRGB());
      } else if (!shader && selectedTab == 2 && !userStatus.isEmpty()) {
        FontRepository.getFont("Inter Regular", 16f)
            .draw(
                userStatus,
                (double) (PanelGui.posX + 105),
                (double) (componentStartY + 10),
                new Color(200, 200, 200).getRGB());
      } else {
        float componentOffsetY = componentStartY;
        for (ConfigComponent config : configs) {
          float moduleY = componentOffsetY - scrollOffset;
          config.setX(PanelGui.posX + 105);
          config.setY(moduleY);
          config.render(shader);
          config.setVisible(
              moduleY + 35 >= componentStartY && moduleY <= componentStartY + viewHeight);

          componentOffsetY += 35;
        }
      }

      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    if (isSelected) {
      float componentStartY = PanelGui.posY + 65;
      float tabX = PanelGui.posX + 105;
      float tabY = componentStartY - 25;
      float localW = FontRepository.getFont("Inter Regular", 16f).getStringWidth("Local");
      float onlineW = FontRepository.getFont("Inter Regular", 16f).getStringWidth("Online");
      float userW = FontRepository.getFont("Inter Regular", 16f).getStringWidth("MiauUser");

      localHovered = PanelGui.isHovered(tabX, tabY, localW, 16, mouseX, mouseY);
      onlineHovered = PanelGui.isHovered(tabX + localW + 15, tabY, onlineW, 16, mouseX, mouseY);
      userHovered =
          PanelGui.isHovered(tabX + localW + 15 + onlineW + 15, tabY, userW, 16, mouseX, mouseY);

      float viewHeight = 250;
      for (ConfigComponent cc : configs) {
        if (cc.getY() + 35 >= componentStartY && cc.getY() <= componentStartY + viewHeight) {
          cc.drawScreen(mouseX, mouseY);
        }
      }
    }
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (isSelected && mouseButton == 0) {
      if (localHovered) {
        selectedTab = 0;
        targetScrollOffset = 0;
        buildComponents();
        return;
      } else if (onlineHovered) {
        selectedTab = 1;
        targetScrollOffset = 0;
        buildComponents();
        return;
      } else if (userHovered) {
        selectedTab = 2;
        targetScrollOffset = 0;
        buildComponents();
        return;
      }
    }

    if (isSelected) {
      float componentStartY = PanelGui.posY + 65;
      float viewHeight = 250;
      for (ConfigComponent cc : configs) {
        if (cc.getY() + 35 >= componentStartY && cc.getY() <= componentStartY + viewHeight) {
          cc.mouseClicked(mouseX, mouseY, mouseButton);
        }
      }
    }
  }

  @Override
  public void keyTyped(char typedChar, int keyCode) {
    if (isSelected) {
      for (ConfigComponent cc : configs) {
        cc.keyTyped(typedChar, keyCode);
      }
    }
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int state) {
    if (isSelected) {
      for (ConfigComponent cc : configs) {
        cc.mouseReleased(mouseX, mouseY, state);
      }
    }
  }

  public void handleScroll() {
    int wheel = Mouse.getDWheel();
    if (wheel != 0) {
      float scrollAmount = wheel > 0 ? -25 : 25;
      targetScrollOffset = MathHelper.clamp_float(targetScrollOffset + scrollAmount, 0, maxScroll);
    }
  }

  public float getX() {
    return x;
  }

  public void setX(float x) {
    this.x = x;
  }

  public float getY() {
    return y;
  }

  public void setY(float y) {
    this.y = y;
  }

  public boolean isHovered() {
    return isHovered;
  }

  public void setHovered(boolean hovered) {
    this.isHovered = hovered;
  }

  public boolean isSelected() {
    return isSelected;
  }

  public void setSelected(boolean selected) {
    this.isSelected = selected;
  }

  private float animate(float current, float target, float speed) {
    return current + (target - current) / Math.max(1, speed * 10);
  }
}
