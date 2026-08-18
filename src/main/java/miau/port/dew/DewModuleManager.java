package miau.port.dew;

import java.util.HashMap;
import java.util.Map;

/** Faithful port of Dew's ModuleManager getModule registry. */
public class DewModuleManager {
  private final Map<Class<?>, Object> modules = new HashMap<>();
  private final Map<Class<?>, miau.module.Module> miauBindings = new HashMap<>();

  @SuppressWarnings("unchecked")
  public <T> T getModule(Class<T> clazz) {
    Object m = modules.get(clazz);
    if (m != null) return (T) m;
    return (T) miauBindings.get(clazz);
  }

  public void register(Class<?> clazz, Object module) {
    modules.put(clazz, module);
  }

  public void bindMiau(Class<?> clazz, miau.module.Module miauModule) {
    miauBindings.put(clazz, miauModule);
  }

  public miau.module.Module bound(Class<?> clazz) {
    return miauBindings.get(clazz);
  }

  public void markModuleListDirty() {}
}
