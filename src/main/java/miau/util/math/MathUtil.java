package miau.util.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.vector.Vector3d;
import miau.util.world.*;
import net.minecraft.util.MathHelper;

@UtilityClass
public class MathUtil {

  /**
   * Method which returns a double between two input numbers
   *
   * @param min minimal number
   * @param max maximal number
   * @return random between both numbers
   */
  public static double getRandom(double min, double max) {
    if (min == max) {
      return min;
    } else if (min > max) {
      final double d = min;
      min = max;
      max = d;
    }
    return ThreadLocalRandom.current().nextDouble(min, max);
  }

  public static double roundToPlace(final double value, final int places) {
    if (places < 0) {
      throw new IllegalArgumentException();
    }

    BigDecimal bd = new BigDecimal(value);
    bd = bd.setScale(places, RoundingMode.HALF_UP);
    return bd.doubleValue();
  }

  public static float calculateGaussianValue(float x, float sigma) {
    double output = 1.0 / Math.sqrt(2.0 * Math.PI * (sigma * sigma));
    return (float) (output * Math.exp(-(x * x) / (2.0 * (sigma * sigma))));
  }

  public static double roundToHalf(double d) {
    return Math.round(d * 2) / 2.0;
  }

  public static double round(final double value, final int places) {
    try {
      final BigDecimal bigDecimal = BigDecimal.valueOf(value);

      return bigDecimal.setScale(places, RoundingMode.HALF_UP).doubleValue();
    } catch (Exception exception) {
      return 0;
    }
  }

  public static float getClosestMultipleOfDivisor(final float valueToRound, final float divisor) {
    final float quotient = Math.round(valueToRound / divisor);

    return divisor * quotient;
  }

  public static double round(final double value, final int scale, final double inc) {
    final double halfOfInc = inc / 2.0;
    final double floored = Math.floor(value / inc) * inc;

    if (value >= floored + halfOfInc) {
      return new BigDecimal(Math.ceil(value / inc) * inc)
          .setScale(scale, RoundingMode.HALF_UP)
          .doubleValue();
    } else {
      return new BigDecimal(floored).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
  }

  public static double roundWithSteps(final double value, final double steps) {
    double a = ((Math.round(value / steps)) * steps);
    a *= 1000;
    a = (int) a;
    a /= 1000;
    return a;
  }

  public static double lerp(final double a, final double b, final double c) {
    return a + c * (b - a);
  }

  public static float lerp(final float a, final float b, final float c) {
    return a + c * (b - a);
  }

  /** Gets the distance to the position. Args: x, y, z */
  public double getDistance(
      final double x1,
      final double y1,
      final double z1,
      final double x2,
      final double y2,
      final double z2) {
    final double d0 = x2 - x1;
    final double d1 = y2 - y1;
    final double d2 = z2 - z1;
    return MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
  }

  /**
   * Clamps a number, n, to be within a specified range
   *
   * @param min The minimum permitted value of the input
   * @param max The maximum permitted value of the input
   * @param n The input number to clamp
   * @return The input, bounded by the specified minimum and maximum values
   */
  public static double clamp(double min, double max, double n) {
    return Math.max(min, Math.min(max, n));
  }

  public static double wrappedDifference(double number1, double number2) {
    return Math.min(
        Math.abs(number1 - number2),
        Math.min(
            Math.abs(number1 - 360) - Math.abs(number2 - 0),
            Math.abs(number2 - 360) - Math.abs(number1 - 0)));
  }

  public static double getAngleBetweenLocations(Vector3d location1, Vector3d location2) {
    double deltaX = location2.x - location1.x;
    double deltaZ = location2.z - location1.z;
    double yawToLocation = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;
    return MathHelper.wrapAngleTo180_float((float) yawToLocation);
  }
}
