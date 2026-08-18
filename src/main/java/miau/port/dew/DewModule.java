package miau.port.dew;

import miau.port.dew.DewCommon;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Faithful port of Dew's Module base, bridged onto a lightweight register in DewCommon. */
public abstract class DewModule {
  public final String name;
  public final ModuleCategory category;
  private final List<Value<?>> values = new ArrayList<>();
  public int key;
  public boolean showOnArray;
  public boolean canBeEnabled;
  public boolean guiExpanded = false;
  private boolean enabled;

  public DewModule(
      String name, ModuleCategory category, int key, boolean enabled, boolean showOnArray, boolean canBeEnabled) {
    this.name = name;
    this.category = category;
    this.key = key;
    this.showOnArray = showOnArray;

    if (!canBeEnabled) {
      this.enabled = false;
      this.canBeEnabled = false;
    } else {
      this.enabled = enabled;
      this.canBeEnabled = true;
    }

    if (this.enabled) {
      this.onEnable();
    }

    this.autoRegisterValues();
  }

  private void autoRegisterValues() {
    for (Field field : this.getClass().getDeclaredFields()) {
      if (!Value.class.isAssignableFrom(field.getType())) continue;
      field.setAccessible(true);
      try {
        Value<?> value = (Value<?>) field.get(this);
        if (value != null) {
          values.add(value);
        }
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }
  }

  public List<Value<?>> getValues() {
    return values;
  }

  public void toggleState() {
    this.setState(!this.isEnabled());
  }

  public void setState(boolean state) {
    if (this.enabled == state) return;

    if (!this.canBeEnabled) {
      this.onEnable();
      this.onDisable();
      return;
    }

    if (state) {
      this.onEnable();
    } else {
      this.onDisable();
    }

    this.enabled = state;
    DewCommon.moduleManager.markModuleListDirty();
  }

  public boolean isEnabled() {
    return this.enabled;
  }

  public boolean isGuiExpanded() {
    return guiExpanded;
  }

  public void setGuiExpanded(boolean guiExpanded) {
    this.guiExpanded = guiExpanded;
  }

  public String tag() {
    return "";
  }

  public void onEnable() {}

  public void onDisable() {}
}
