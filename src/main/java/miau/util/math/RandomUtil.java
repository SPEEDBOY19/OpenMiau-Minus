package miau.util.math;

import java.util.Random;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;

public class RandomUtil {
  private static final Random theRandom = new Random();

  public static long nextLong(long min, long max) {
    return (long) nextDouble((double) min, (double) (max + 1L));
  }

  public static float nextFloat(float min, float max) {
    return theRandom.nextFloat() * (max - min) + min;
  }

  public static double nextDouble(double min, double max) {
    return theRandom.nextDouble() * (max - min) + min;
  }

  public static int nextInt(int min, int max) {
    return (int) nextDouble(min, max + 1);
  }
}
