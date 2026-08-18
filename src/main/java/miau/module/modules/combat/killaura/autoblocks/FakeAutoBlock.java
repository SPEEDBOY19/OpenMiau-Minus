package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.player.PlayerUtil;

public class FakeAutoBlock extends AutoBlockMode {
  public FakeAutoBlock(KillAura parent) {
    super("FAKE", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    parent.isBlocking = false;
    parent.fakeBlockState = parent.getTarget() != null;
    if (PlayerUtil.isUsingItem()
        && !parent.isPlayerBlocking()
        && !Miau.playerStateManager.digging
        && !Miau.playerStateManager.placing) {
      swap = true;
    }
    return swap;
  }
}
