package miau.property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import miau.module.Module;

public class PropertyManager {
  public LinkedHashMap<Class<?>, ArrayList<Property<?>>> properties = new LinkedHashMap<>();

  public Property<?> getProperty(Module module, String string) {
    ArrayList<Property<?>> props = properties.get(module.getClass());
    if (props == null) return null;
    for (Property<?> property : props) {
      if (property
          .getName()
          .replace("-", "")
          .replace(" ", "")
          .equalsIgnoreCase(string.replace("-", "").replace(" ", ""))) {
        return property;
      }
    }
    return null;
  }
}
