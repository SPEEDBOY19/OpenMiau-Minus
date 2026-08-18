package miau.config.online;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.config.ConfigVersionUtil;
import miau.module.Module;
import miau.property.Property;

public class OnlineConfigApplier {
  public int apply(String json) throws Exception {
    JsonElement parsed = new JsonParser().parse(json);
    if (parsed == null || !parsed.isJsonObject()) {
      throw new Exception("Invalid Miau config JSON");
    }

    int applied = 0;
    List<String> failedProperties = new ArrayList<>();
    JsonObject root = parsed.getAsJsonObject();
    ConfigVersionUtil.warnIfOutdated(root, "online config");
    for (Module module : Miau.moduleManager.modules.values()) {
      JsonObject object = findModuleObject(root, module);
      if (object == null) {
        continue;
      }
      applied += applyModule(module, object, failedProperties);
    }
    if (!failedProperties.isEmpty()) {
      throw new Exception(
          "Applied "
              + applied
              + " setting(s), but failed "
              + failedProperties.size()
              + " propert"
              + (failedProperties.size() == 1 ? "y" : "ies")
              + ": "
              + String.join(
                  ", ", failedProperties.subList(0, Math.min(5, failedProperties.size()))));
    }
    return applied;
  }

  private JsonObject findModuleObject(JsonObject root, Module module) {
    JsonElement element = root.get(module.getName());
    if (element == null || !element.isJsonObject()) {
      element = root.get(module.getClass().getSimpleName());
    }
    return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
  }

  private int applyModule(Module module, JsonObject object, List<String> failedProperties) {
    int applied = 0;
    if (readBoolean(object, "toggled", module::setEnabled)) applied++;
    if (readInt(object, "key", module::setKey)) applied++;
    if (readBoolean(object, "hidden", module::setHidden)) applied++;

    ArrayList<Property<?>> properties = Miau.propertyManager.properties.get(module.getClass());
    if (properties == null) {
      return applied;
    }
    for (Property<?> property : properties) {
      if (object.has(property.getName())) {
        try {
          if (property.read(object)) {
            applied++;
          }
        } catch (Exception e) {
          failedProperties.add(module.getName() + "." + property.getName());
        }
      }
    }
    return applied;
  }

  private boolean readBoolean(JsonObject object, String name, BooleanConsumer consumer) {
    if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
      return false;
    }
    consumer.accept(object.get(name).getAsBoolean());
    return true;
  }

  private boolean readInt(JsonObject object, String name, IntConsumer consumer) {
    if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
      return false;
    }
    consumer.accept(object.get(name).getAsInt());
    return true;
  }

  private interface BooleanConsumer {
    void accept(boolean value);
  }

  private interface IntConsumer {
    void accept(int value);
  }
}
