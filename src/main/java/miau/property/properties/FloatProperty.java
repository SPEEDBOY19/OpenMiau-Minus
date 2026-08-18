package miau.property.properties;

import com.google.gson.JsonObject;
import java.util.function.BooleanSupplier;
import miau.property.Property;

public class FloatProperty extends Property<Float> {
  private final Float minimum;
  private final Float maximum;
  private Float secondValue;
  private final boolean doubleSlider;

  public FloatProperty(String name, Float value, Float minimum, Float maximum) {
    this(name, value, minimum, maximum, (BooleanSupplier) null);
  }

  public FloatProperty(
      String string, Float value, Float minimum, Float maximum, BooleanSupplier check) {
    super(string, value, floatV -> floatV >= minimum && floatV <= maximum, check);
    this.minimum = minimum;
    this.maximum = maximum;
    this.doubleSlider = false;
    this.secondValue = value;
  }

  public FloatProperty(String name, Float value, Float secondValue, Float minimum, Float maximum) {
    this(name, value, secondValue, minimum, maximum, (BooleanSupplier) null);
  }

  public FloatProperty(
      String string,
      Float value,
      Float secondValue,
      Float minimum,
      Float maximum,
      BooleanSupplier check) {
    super(string, value, floatV -> floatV >= minimum && floatV <= maximum, check);
    this.minimum = minimum;
    this.maximum = maximum;
    this.doubleSlider = true;
    this.secondValue = secondValue;
    if (this.getValue() != null && this.secondValue != null && this.getValue() > this.secondValue) {
      super.setValue(this.secondValue);
    }
  }

  @Override
  public String getValuePrompt() {
    return String.format("%s-%s", this.minimum, this.maximum);
  }

  @Override
  public String formatValue() {
    if (doubleSlider) {
      return String.format("&6%s-%s", this.getValue(), this.secondValue);
    }
    return String.format("&6%s", this.getValue());
  }

  @Override
  public boolean parseString(String string) {
    if (doubleSlider && string.contains("-")) {
      String[] split = string.split("-");
      if (split.length == 2) {
        this.setValue(Float.parseFloat(split[0]));
        this.setSecondValue(Float.parseFloat(split[1]));
        return true;
      }
    }
    return this.setValue(Float.parseFloat(string));
  }

  @Override
  public boolean read(JsonObject jsonObject) {
    if (!jsonObject.has(this.getName())) return false;
    boolean success = this.setValue(jsonObject.get(this.getName()).getAsNumber().floatValue());
    if (doubleSlider && jsonObject.has(this.getName() + "_second")) {
      this.setSecondValue(jsonObject.get(this.getName() + "_second").getAsNumber().floatValue());
    }
    return success;
  }

  @Override
  public void write(JsonObject jsonObject) {
    jsonObject.addProperty(this.getName(), this.getValue());
    if (doubleSlider) {
      jsonObject.addProperty(this.getName() + "_second", this.secondValue);
    }
  }

  @Override
  public boolean setValue(Object object) {
    Float val = (Float) object;
    if (doubleSlider && secondValue != null && val > secondValue) {
      val = secondValue;
    }
    return super.setValue(val);
  }

  public Float getMin() {
    return minimum;
  }

  public Float getMax() {
    return maximum;
  }

  public Float getMinimum() {
    return minimum;
  }

  public Float getMaximum() {
    return maximum;
  }

  public Float getSecondValue() {
    return secondValue;
  }

  public void setSecondValue(Float val) {
    if (val < this.getValue()) {
      val = this.getValue();
    }
    if (val > maximum) {
      val = maximum;
    }
    this.secondValue = val;
  }

  public boolean isDoubleSlider() {
    return doubleSlider;
  }

  public Float getDefaultValue() {
    return getValue();
  }

  public Float getDecimalPlaces() {
    return 1.0f;
  }
}
