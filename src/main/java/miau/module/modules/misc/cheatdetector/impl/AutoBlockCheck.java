package miau.module.modules.misc.cheatdetector.impl;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class AutoBlockCheck extends Check {
  private int autoBlockTicks = 0;

  @Override
  public String getName() {
    return "AutoBlock";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.isSwingInProgress && player.isBlocking()) {
      this.autoBlockTicks++;
    } else {
      this.autoBlockTicks = 0;
    }

    if (this.autoBlockTicks > 10) {
      flag(player, "ticks: " + this.autoBlockTicks);
      this.autoBlockTicks = 0;
    }
  }
}
