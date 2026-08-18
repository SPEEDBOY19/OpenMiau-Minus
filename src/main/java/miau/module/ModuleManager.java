package miau.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.KeyEvent;
import miau.module.modules.render.HUD;

public class ModuleManager {
  private static final Logger LOGGER = Logger.getLogger(ModuleManager.class.getName());
  private static final String MODULE_PACKAGE = "miau.module.modules.";
  private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();

  static {
    CATEGORY_NAMES.put("combat", "Combat");
    CATEGORY_NAMES.put("ghost", "Ghost");
    CATEGORY_NAMES.put("movement", "Movement");
    CATEGORY_NAMES.put("render", "Render");
    CATEGORY_NAMES.put("player", "Player");
    CATEGORY_NAMES.put("misc", "Misc");
    CATEGORY_NAMES.put("network", "Network");
    CATEGORY_NAMES.put("minigames", "Minigames");
    CATEGORY_NAMES.put("grind", "Grind");
  }

  public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();

  public Module getModule(String string) {
    return this.modules.values().stream()
        .filter(mD -> mD.getName().replace(" ", "").equalsIgnoreCase(string.replace(" ", "")))
        .findFirst()
        .orElse(null);
  }

  public Module getModule(Class<?> clazz) {
    return this.modules.get(clazz);
  }

  public LinkedHashMap<String, List<Module>> getModulesByCategory() {
    LinkedHashMap<String, List<Module>> categories = new LinkedHashMap<>();
    for (String categoryName : CATEGORY_NAMES.values()) {
      categories.put(categoryName, new ArrayList<>());
    }

    for (Module module : this.modules.values()) {
      String categoryName = getCategoryName(module);
      if (categoryName != null) {
        categories.get(categoryName).add(module);
      }
    }

    Comparator<Module> byName = Comparator.comparing(module -> module.getName().toLowerCase());
    categories.values().forEach(modules -> modules.sort(byName));
    categories.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    return categories;
  }

  private static String getCategoryName(Module module) {
    String packageName = module.getClass().getPackage().getName();
    if (!packageName.startsWith(MODULE_PACKAGE)) {
      return null;
    }

    String categoryKey = packageName.substring(MODULE_PACKAGE.length());
    int nestedPackage = categoryKey.indexOf('.');
    if (nestedPackage >= 0) {
      categoryKey = categoryKey.substring(0, nestedPackage);
    }
    return CATEGORY_NAMES.get(categoryKey);
  }

  @EventTarget
  public void onKey(KeyEvent event) {
    for (Module module : this.modules.values()) {
      if (module.getKey() != event.getKey()) {
        continue;
      }
      boolean shouldNotify = module.toggle();
      HUD hud = (HUD) this.modules.get(HUD.class);
      if (hud != null && shouldNotify) {
        shouldNotify = hud.toggleAlerts.getValue();
      }
      if (shouldNotify) {
        String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
        String message = String.format("%s%s: %s&r", Miau.clientName, module.getName(), status);
        miau.util.client.ChatUtil.display(message);
      }
    }
  }
}
