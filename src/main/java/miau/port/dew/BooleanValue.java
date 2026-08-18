package miau.port.dew;

import com.google.gson.JsonObject;
import java.util.function.BooleanSupplier;

public class BooleanValue extends Value<Boolean> {
  public BooleanValue(String name, boolean defaultValue) {
    super(name, defaultValue);
  }

  public BooleanValue(String name, boolean defaultValue, BooleanSupplier visible) {
    super(name, defaultValue, visible);
  }

  public void toggle() {
    this.setValue(!this.getValue());
  }

  @Override
  public boolean parseString(String string) {
    if (string == null) {
      return this.setValue(!this.getValue());
    } else if (string.equalsIgnoreCase("true")
        || string.equalsIgnoreCase("on")
        || string.equalsIgnoreCase("1")) {
      return this.setValue(true);
    } else {
      return (string.equalsIgnoreCase("false")
              || string.equalsIgnoreCase("off")
              || string.equalsIgnoreCase("0"))
          && this.setValue(false);
    }
  }

  @Override
  public boolean read(JsonObject jsonObject) {
    if (!jsonObject.has(this.name)) return false;
    this.set(jsonObject.get(this.name).getAsBoolean());
    return true;
  }

  @Override
  public void write(JsonObject jsonObject) {
    jsonObject.addProperty(this.name, this.getValue());
  }
}
