package miau.util.animation;

import java.util.function.Function;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;

/**
 * Easing functions for smooth animations. Each function takes a progress value (0.0 to 1.0) and
 * returns an eased value. More easing functions can be found at: https:
 */
public enum Easing {
  LINEAR(x -> x),
  EASE_IN_QUAD(x -> x * x),
  EASE_OUT_QUAD(x -> x * (2 - x)),
  EASE_IN_OUT_QUAD(x -> x < 0.5 ? 2 * x * x : -1 + (4 - 2 * x) * x),
  EASE_IN_CUBIC(x -> x * x * x),
  EASE_OUT_CUBIC(x -> (--x) * x * x + 1),
  EASE_IN_OUT_CUBIC(x -> x < 0.5 ? 4 * x * x * x : (x - 1) * (2 * x - 2) * (2 * x - 2) + 1),
  EASE_IN_QUART(x -> x * x * x * x),
  EASE_OUT_QUART(x -> 1 - (--x) * x * x * x),
  EASE_IN_OUT_QUART(x -> x < 0.5 ? 8 * x * x * x * x : 1 - 8 * (--x) * x * x * x),
  EASE_IN_QUINT(x -> x * x * x * x * x),
  EASE_OUT_QUINT(x -> 1 + (--x) * x * x * x * x),
  EASE_IN_OUT_QUINT(x -> x < 0.5 ? 16 * x * x * x * x * x : 1 + 16 * (--x) * x * x * x * x),
  EASE_IN_SINE(x -> 1 - Math.cos(x * Math.PI / 2)),
  EASE_OUT_SINE(x -> Math.sin(x * Math.PI / 2)),
  EASE_IN_OUT_SINE(x -> 1 - Math.cos(Math.PI * x / 2)),
  EASE_IN_EXPO(x -> x == 0 ? 0 : Math.pow(2, 10 * x - 10)),
  EASE_OUT_EXPO(x -> x == 1 ? 1 : 1 - Math.pow(2, -10 * x)),
  EASE_IN_OUT_EXPO(
      x ->
          x == 0
              ? 0
              : x == 1
                  ? 1
                  : x < 0.5 ? Math.pow(2, 20 * x - 10) / 2 : (2 - Math.pow(2, -20 * x + 10)) / 2),
  EASE_IN_CIRC(x -> 1 - Math.sqrt(1 - x * x)),
  EASE_OUT_CIRC(x -> Math.sqrt(1 - (--x) * x)),
  EASE_IN_OUT_CIRC(
      x -> x < 0.5 ? (1 - Math.sqrt(1 - 4 * x * x)) / 2 : (Math.sqrt(1 - 4 * (x - 1) * x) + 1) / 2),
  SIGMOID(x -> 1 / (1 + Math.exp(-x))),
  EASE_OUT_ELASTIC(
      x ->
          x == 0
              ? 0
              : x == 1
                  ? 1
                  : Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * ((2 * Math.PI) / 3)) * 0.5
                      + 1),
  EASE_IN_BACK(x -> (1.70158 + 1) * x * x * x - 1.70158 * x * x),

  /** Opal's DECELERATE: 1 - ((x-1) * (x-1)). */
  DECELERATE(x -> 1 - ((x - 1) * (x - 1))),
  /** Opal's SMOOTH_STEP: -2 * x^3 + 3 * x^2. */
  SMOOTH_STEP(x -> -2 * Math.pow(x, 3) + 3 * Math.pow(x, 2)),
  /** Opal's DYNAMIC_ISLAND: 1 - cos(x * PI * (0.2 + 2.5 * x^3)) * exp(-x * 5). */
  DYNAMIC_ISLAND(x -> 1 - Math.cos(x * Math.PI * (0.2 + 2.5 * Math.pow(x, 3))) * Math.exp(-x * 5));

  private final Function<Double, Double> function;

  Easing(Function<Double, Double> function) {
    this.function = function;
  }

  public double apply(double x) {
    return function.apply(x);
  }

  public String getName() {
    return name().toLowerCase().replace("_", " ");
  }
}
