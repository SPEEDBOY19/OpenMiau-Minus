package miau.module.modules.misc.killsults;

public class LiberationDayMode implements KillSultMode {
  @Override
  public String getMessage(String targetName) {
    return targetName + " Chuc mung thang lon!!";
  }
}
