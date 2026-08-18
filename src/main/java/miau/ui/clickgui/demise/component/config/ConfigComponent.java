package miau.ui.clickgui.demise.component.config;

import java.awt.Color;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import miau.Miau;
import miau.config.Config;
import miau.config.online.OnlineConfigApplier;
import miau.config.online.OnlineConfigClient;
import miau.config.online.OnlineConfigEntry;
import miau.ui.clickgui.demise.IComponent;
import miau.util.client.ChatUtil;
import miau.util.demise.RoundedUtils;
import miau.util.font.FontRepository;
import net.minecraft.client.Minecraft;

public class ConfigComponent implements IComponent {
  private float x, y;
  private boolean isHovered, saveHovered, deleteHovered;
  private Color interpolatedColor = new Color(20, 20, 20, 150);
  private Color interpolatedColor1 = new Color(0, 0, 0, 0);
  public boolean visible;
  private float slideProgress = 0f;
  private String name;

  private boolean isLocal = true;
  private boolean isUser = false;
  private OnlineConfigEntry onlineEntry = null;

  private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
  private static final OnlineConfigClient onlineClient = new OnlineConfigClient();

  public ConfigComponent(String name) {
    this.name = name;
  }

  public ConfigComponent(OnlineConfigEntry entry, boolean isUser) {
    this.name = entry.getName();
    this.isLocal = false;
    this.isUser = isUser;
    this.onlineEntry = entry;
  }

  public void initCategory() {
    slideProgress = 0;
  }

  public void render(boolean shader) {
    float width = 330;
    slideProgress = animate(slideProgress, visible ? 1 : 0, 0.1f);
    float slideOffset = (width / 4) * (1.0f - slideProgress);

    if (!shader) {
      if (isHovered) {
        interpolatedColor = interpolateColorC(interpolatedColor, new Color(35, 35, 35, 190), 0.1f);
      } else {
        interpolatedColor = interpolateColorC(interpolatedColor, new Color(20, 20, 20, 150), 0.1f);
      }

      String currentConfig = "default";
      if (Objects.equals(currentConfig, name)) {
        interpolatedColor1 =
            interpolateColorC(interpolatedColor1, new Color(50, 50, 50, 150), 0.1f);
      } else {
        interpolatedColor1 = interpolateColorC(interpolatedColor1, new Color(0, 0, 0, 0), 0.1f);
      }

      RoundedUtils.drawRound(x + slideOffset, y, width, 30, 8, interpolatedColor);
      RoundedUtils.drawRound(x + slideOffset, y, width, 30, 8, interpolatedColor1);

      FontRepository.getFont("Inter Regular", 20f)
          .draw(name, (double) (x + 7 + slideOffset), (double) (y + 11), Color.white.getRGB());

      if (isLocal) {
        float saveWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth("save");
        float deleteWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth("delete");

        Color saveColor = saveHovered ? new Color(255, 255, 255) : new Color(179, 179, 179);
        Color deleteColor = deleteHovered ? new Color(255, 255, 255) : new Color(179, 179, 179);

        FontRepository.getFont("Inter Regular", 14f)
            .draw(
                "save",
                (double) (x + width - 10 - deleteWidth - 5 - saveWidth + slideOffset),
                (double) (y + 13),
                saveColor.getRGB());
        FontRepository.getFont("Inter Regular", 14f)
            .draw(
                "delete",
                (double) (x + width - 10 - deleteWidth + slideOffset),
                (double) (y + 13),
                deleteColor.getRGB());
      } else {
        String meta = "by " + onlineEntry.getAuthor();
        float metaWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth(meta);
        FontRepository.getFont("Inter Regular", 14f)
            .draw(
                meta,
                (double) (x + width - 10 - metaWidth + slideOffset),
                (double) (y + 13),
                new Color(179, 179, 179).getRGB());
      }
    } else {
      RoundedUtils.drawRound(x + slideOffset, y, width, 30, 8, Color.black);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    float width = 330;
    float slideOffset = (width / 4) * (1.0f - slideProgress);
    float saveWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth("save");
    float deleteWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth("delete");

    if (isLocal) {
      this.isHovered =
          isHovered(
              x + slideOffset, y, width - 15 - deleteWidth - 15 - saveWidth, 30, mouseX, mouseY);
      this.saveHovered =
          isHovered(
              x + width - 10 - deleteWidth - 5 - saveWidth + slideOffset,
              y,
              saveWidth,
              30,
              mouseX,
              mouseY);
      this.deleteHovered =
          isHovered(x + width - 10 - deleteWidth + slideOffset, y, deleteWidth, 30, mouseX, mouseY);
    } else {
      this.isHovered = isHovered(x + slideOffset, y, width, 30, mouseX, mouseY);
      this.saveHovered = false;
      this.deleteHovered = false;
    }
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (visible && mouseButton == 0) {
      if (isLocal) {
        if (isHovered) {
          Config config = new Config(name, true);
          if (config.file.exists()) {
            config.load();
            ChatUtil.display("Loaded config " + name);
          } else {
            ChatUtil.display("Failed to load config " + name + "!");
          }
        } else if (saveHovered) {
          Config config = new Config(name, true);
          config.save();
          ChatUtil.display("Saved config " + name);
        } else if (deleteHovered) {
          File configFile = new File("config/Miau", name + ".json");
          if (!configFile.exists()) {
            ChatUtil.display("Config does not exist: " + name);
            return;
          }
          String message =
              configFile.delete() ? "Removed config: " + name : "Failed to remove config: " + name;
          ChatUtil.display(message);
        }
      } else {
        if (isHovered) {
          loadOnlineConfig(onlineEntry, isUser);
        }
      }
    }
  }

  private void loadOnlineConfig(OnlineConfigEntry entry, boolean userConfig) {
    EXECUTOR.execute(
        () -> {
          try {
            String json =
                userConfig
                    ? onlineClient.loadUserConfig(entry.getId())
                    : onlineClient.load(entry.getId());
            Minecraft.getMinecraft()
                .addScheduledTask(
                    () -> {
                      try {
                        int applied = new OnlineConfigApplier().apply(json);
                        ChatUtil.display(
                            "%s config loaded (&a&o%s&r) &7- applied %d setting(s)&r",
                            (userConfig ? "User" : "Online"), entry.getName(), applied);
                      } catch (Exception e) {
                        ChatUtil.display(
                            Miau.clientName
                                + "Failed to load "
                                + (userConfig ? "user" : "online")
                                + " config: &c"
                                + e.getMessage()
                                + "&r");
                      }
                    });
          } catch (Exception e) {
            Minecraft.getMinecraft()
                .addScheduledTask(
                    () ->
                        ChatUtil.display(
                            Miau.clientName
                                + "Failed to load "
                                + (userConfig ? "user" : "online")
                                + " config: &c"
                                + e.getMessage()
                                + "&r"));
          }
        });
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

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  private Color interpolateColorC(Color color1, Color color2, float amount) {
    amount = Math.min(1, Math.max(0, amount));
    return new Color(
        (int) (color1.getRed() + (color2.getRed() - color1.getRed()) * amount),
        (int) (color1.getGreen() + (color2.getGreen() - color1.getGreen()) * amount),
        (int) (color1.getBlue() + (color2.getBlue() - color1.getBlue()) * amount),
        (int) (color1.getAlpha() + (color2.getAlpha() - color1.getAlpha()) * amount));
  }

  private float animate(float current, float target, float speed) {
    return current + (target - current) / Math.max(1, speed * 10);
  }

  private boolean isHovered(
      float x, float y, float width, float height, float mouseX, float mouseY) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }
}
