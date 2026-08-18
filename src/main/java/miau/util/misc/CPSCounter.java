package miau.util.misc;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Deque;
import java.util.Map;

/** Simple CPS tracker used by velocity modules. Ported from FB's CPSCounter. */
public final class CPSCounter {
  public enum MouseButton { LEFT, MIDDLE, RIGHT }

  private static final Map<MouseButton, Deque<Long>> clicks = new EnumMap<>(MouseButton.class);

  static {
    for (MouseButton button : MouseButton.values()) {
      clicks.put(button, new ArrayDeque<>());
    }
  }

  private CPSCounter() {}

  public static void registerClick(MouseButton button) {
    long now = System.currentTimeMillis();
    Deque<Long> deque = clicks.get(button);
    synchronized (deque) {
      deque.addLast(now);
      while (!deque.isEmpty() && now - deque.peekFirst() > 1000L) {
        deque.pollFirst();
      }
    }
  }

  public static int getCPS(MouseButton button) {
    long now = System.currentTimeMillis();
    Deque<Long> deque = clicks.get(button);
    synchronized (deque) {
      while (!deque.isEmpty() && now - deque.peekFirst() > 1000L) {
        deque.pollFirst();
      }
      return deque.size();
    }
  }
}
