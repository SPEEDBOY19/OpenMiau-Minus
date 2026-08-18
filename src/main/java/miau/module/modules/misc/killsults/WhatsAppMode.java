package miau.module.modules.misc.killsults;

public class WhatsAppMode implements KillSultMode {
  @Override
  public String getMessage(String targetName) {
    return "Add me on WhatsApp " + targetName;
  }
}
