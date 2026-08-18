package miau.port.dew;

import com.google.gson.JsonObject;
import java.util.function.BooleanSupplier;
import miau.property.Property;

/**
 * Faithful port of Dew's Value base class, bridged onto Miau's Property system so values appear in
 * the clickgui. {@link #set(Object)} mirrors Dew (no Hud refresh needed here).
 */
public abstract class Value<T> extends Property<T> {
  protected final String name;

  public Value(String name, T defaultValue) {
    this(name, defaultValue, () -> true);
  }

  public Value(String name, T defaultValue, BooleanSupplier visibleSupplier) {
    super(name, defaultValue, visibleSupplier);
    this.name = name;
  }

  public String getName() {
    return this.name;
  }

  public T get() {
    return this.getValue();
  }

  public void set(T newValue) {
    this.setValue(newValue);
  }

  public boolean isVisible() {
    return super.isVisible();
  }

  public void setVisibleCondition(BooleanSupplier visibleSupplier) {
    this.setVisibleChecker(visibleSupplier);
  }

  @Override
  public String getValuePrompt() {
    return "";
  }

  @Override
  public String formatValue() {
    return String.valueOf(this.getValue());
  }

  @Override
  public boolean parseString(String string) {
    return this.setValue(string);
  }

  @Override
  public boolean read(JsonObject jsonObject) {
    return false;
  }

  @Override
  public void write(JsonObject jsonObject) {}
}
