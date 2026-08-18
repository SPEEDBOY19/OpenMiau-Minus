package miau.port.dew;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BooleanSupplier;

public class NumberValue extends Value<Double> {
  private final double min, max, step;

  public NumberValue(String name, double defaultValue, double min, double max, double step) {
    super(name, defaultValue);
    this.min = min;
    this.max = max;
    this.step = step;
  }

  public NumberValue(
      String name, double defaultValue, double min, double max, double step, BooleanSupplier visible) {
    super(name, defaultValue, visible);
    this.min = min;
    this.max = max;
    this.step = step;
  }

  public double getMin() {
    return min;
  }

  public double getMax() {
    return max;
  }

  public double getStep() {
    return step;
  }

  @Override
  public void set(Double value) {
    BigDecimal bd = BigDecimal.valueOf(value);
    BigDecimal bdStep = BigDecimal.valueOf(step);
    BigDecimal bdMin = BigDecimal.valueOf(min);
    BigDecimal bdMax = BigDecimal.valueOf(max);

    BigDecimal snapped =
        bd.divide(bdStep, 15, RoundingMode.HALF_UP).setScale(0, RoundingMode.HALF_UP).multiply(bdStep);

    snapped = snapped.max(bdMin).min(bdMax);
    super.set(snapped.doubleValue());
  }

  @Override
  public boolean setValue(Object object) {
    boolean ok = super.setValue(object);
    if (ok) {
      BigDecimal bd = BigDecimal.valueOf((Double) object);
      BigDecimal bdStep = BigDecimal.valueOf(step);
      BigDecimal bdMin = BigDecimal.valueOf(min);
      BigDecimal bdMax = BigDecimal.valueOf(max);
      BigDecimal snapped =
          bd.divide(bdStep, 15, RoundingMode.HALF_UP).setScale(0, RoundingMode.HALF_UP).multiply(bdStep);
      snapped = snapped.max(bdMin).min(bdMax);
      return super.setValue(snapped.doubleValue());
    }
    return false;
  }

  @Override
  public boolean read(JsonObject jsonObject) {
    if (!jsonObject.has(this.name)) return false;
    this.set(jsonObject.get(this.name).getAsDouble());
    return true;
  }

  @Override
  public void write(JsonObject jsonObject) {
    jsonObject.addProperty(this.name, this.getValue());
  }
}
