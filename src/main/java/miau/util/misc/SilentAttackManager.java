package miau.util.misc;

/**
 * Suppresses the AttackEvent fired by {@code MixinPlayerControllerMP} while an action is running,
 * allowing "silent" attacks that do not trigger KillAura. Ported from FB's SilentAttackManager.
 */
public final class SilentAttackManager {
  private static boolean silent = false;

  private SilentAttackManager() {}

  public static boolean isSilent() {
    return silent;
  }

  public static void setSilent(boolean value) {
    silent = value;
  }

  public static void withSilentAttack(Runnable action) {
    if (silent) {
      action.run();
      return;
    }
    silent = true;
    try {
      action.run();
    } finally {
      silent = false;
    }
  }
}
