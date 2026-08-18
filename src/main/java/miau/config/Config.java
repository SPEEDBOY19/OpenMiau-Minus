package miau.config;

import com.google.gson.*;
import java.io.*;
import java.util.ArrayList;
import miau.Miau;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.property.Property;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;

public class Config {
  public static Minecraft mc = Minecraft.getMinecraft();
  public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
  public String name;
  public File file;

  public static String lastConfig;

  public Config(String name, boolean newConfig) {
    this.name = (name.equals("!") || name.equals("default")) ? "default" : name;
    lastConfig = this.name;
    this.file = new File("./config/Miau/", String.format("%s.json", this.name));
    try {
      file.getParentFile().mkdirs();
      if (newConfig) {
        ((IAccessorMinecraft) mc).getLogger().info("Created config file: " + file.getName());
      }
    } catch (Exception e) {
      ((IAccessorMinecraft) mc).getLogger().error(e.getMessage());
    }
  }

  public void load() {
    try {

      if (!file.exists()) {
        ChatUtil.display(
            "Config file not found (&c&o%s&r). Creating default config...&r", file.getName());
        save();
        return;
      }

      JsonElement parsed;
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
        parsed = new JsonParser().parse(reader);
      }
      if (parsed == null || !parsed.isJsonObject()) {
        ChatUtil.display("Invalid config format (&c&o%s&r)", file.getName());
        return;
      }

      JsonObject jsonObject = parsed.getAsJsonObject();
      ConfigVersionUtil.warnIfOutdated(jsonObject, file.getName());

      if (jsonObject.has("theme")) {
        String themeName = jsonObject.get("theme").getAsString();
        for (miau.util.render.Themes theme : miau.util.render.Themes.values()) {
          if (theme.getThemeName().equalsIgnoreCase(themeName)) {
            miau.util.render.Themes.setCurrentTheme(theme);
            break;
          }
        }
      }

      for (Module module : Miau.moduleManager.modules.values()) {
        JsonElement moduleObj = jsonObject.get(module.getName());
        if (moduleObj != null && moduleObj.isJsonObject()) {
          JsonObject object = moduleObj.getAsJsonObject();

          ArrayList<Property<?>> list = Miau.propertyManager.properties.get(module.getClass());
          if (list != null) {
            for (Property<?> property : list) {
              if (property.getName().equals("Position")
                  ? (object.has("Position_x") && object.has("Position_y"))
                  : object.has(property.getName())) {
                try {
                  property.read(object);
                } catch (Exception e) {
                  ((IAccessorMinecraft) mc)
                      .getLogger()
                      .warn(
                          "Failed to load property %s for module %s",
                          property.getName(), module.getName());
                }
              }
            }
          }

          if (object.has("toggled")) {
            JsonElement toggled = object.get("toggled");
            if (toggled != null && toggled.isJsonPrimitive()) {
              module.setEnabled(toggled.getAsBoolean());
            }
          }

          if (object.has("key")) {
            JsonElement key = object.get("key");
            if (key != null && key.isJsonPrimitive()) {
              module.setKey(key.getAsInt());
            }
          }

          if (object.has("hidden")) {
            JsonElement hidden = object.get("hidden");
            if (hidden != null && hidden.isJsonPrimitive()) {
              module.setHidden(hidden.getAsBoolean());
            }
          }
        }
      }
      ChatUtil.display("Config has been loaded (&a&o%s&r)&r", file.getName());
    } catch (FileNotFoundException e) {
      ChatUtil.display("Config file not found (&c&o%s&r)", file.getName());
    } catch (JsonSyntaxException e) {
      ChatUtil.display("%sConfig has invalid JSON syntax (&c&o%s&r)&r", file.getName());
      ((IAccessorMinecraft) mc).getLogger().error("JSON Syntax Error: " + e.getMessage());
    } catch (Exception e) {
      ((IAccessorMinecraft) mc).getLogger().error("Error loading config: " + e.getMessage());
      ChatUtil.display("%sConfig couldn't be loaded (&c&o%s&r)&r", file.getName());
    }
  }

  public void save() {
    try {
      if (!file.getParentFile().exists()) {
        file.getParentFile().mkdirs();
      }

      JsonObject object = new JsonObject();
      ConfigVersionUtil.addVersion(object);
      object.addProperty("theme", miau.util.render.Themes.getCurrentTheme().getThemeName());

      for (Module module : Miau.moduleManager.modules.values()) {
        JsonObject moduleObject = new JsonObject();
        moduleObject.addProperty("toggled", module.isEnabled());
        moduleObject.addProperty("key", module.getKey());
        moduleObject.addProperty("hidden", module.isHidden());

        ArrayList<Property<?>> list = Miau.propertyManager.properties.get(module.getClass());
        if (list != null) {
          for (Property<?> property : list) {
            try {
              property.write(moduleObject);
            } catch (Exception e) {
              ((IAccessorMinecraft) mc)
                  .getLogger()
                  .warn(
                      "Failed to save property %s for module %s",
                      property.getName(), module.getName());
            }
          }
        }
        object.add(module.getName(), moduleObject);
      }

      try (PrintWriter printWriter = new PrintWriter(new FileWriter(file))) {
        printWriter.println(gson.toJson(object));
      }
      ChatUtil.display("Config has been saved (&a&o%s&r)", file.getName());
    } catch (IOException e) {
      ((IAccessorMinecraft) mc).getLogger().error("Error saving config: " + e.getMessage());
      ChatUtil.display("Config couldn't be saved (&c&o%s&r)", file.getName());
    }
  }
}
