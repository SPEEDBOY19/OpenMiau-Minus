package miau.module.modules.misc;

import miau.module.Module;

public class AntiObfuscate extends Module {
  public AntiObfuscate() {
    super("AntiObfuscate", false, true);
  }

  public String stripObfuscated(String input) {
    if (input == null) {
      return null;
    }
    return input.replaceAll("§k", "");
  }
}
