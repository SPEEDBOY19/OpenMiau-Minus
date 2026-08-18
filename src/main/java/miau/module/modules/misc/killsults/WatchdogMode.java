package miau.module.modules.misc.killsults;

public class WatchdogMode implements KillSultMode {
  @Override
  public String getMessage(String targetName) {
    return "[STAFF] [WATCHDOG] " + targetName + " reeled in.";
  }
}
