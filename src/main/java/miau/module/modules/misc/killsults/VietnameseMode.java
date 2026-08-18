package miau.module.modules.misc.killsults;

public class VietnameseMode implements KillSultMode {
  @Override
  public String getMessage(String targetName) {
    return targetName + "  Khoc di em, khoc to len";
  }
}
