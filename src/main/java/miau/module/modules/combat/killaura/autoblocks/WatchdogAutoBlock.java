package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;

public class WatchdogAutoBlock extends AutoBlockMode {
  public WatchdogAutoBlock(KillAura parent) {
    super("WATCHDOG", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.getTarget() != null) {
      if (!parent.isPlayerBlocking()
          && !Miau.playerStateManager.digging
          && !Miau.playerStateManager.placing) {
        swap = true;
      }
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;
    } else {
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }

  @Override
  public void onDisable() {
    if (parent.isPlayerBlocking()) {
      parent.stopBlock();
    }
  }
}