package miau.module.modules.misc.cheatdetector;

import miau.Miau;
import miau.event.impl.PacketEvent;
import miau.module.modules.misc.CheatDetector;
import miau.module.modules.misc.cheatdetector.impl.AutoBlockCheck;
import miau.module.modules.misc.cheatdetector.impl.KillauraCheck;
import miau.module.modules.misc.cheatdetector.impl.LegitScaffoldCheck;
import miau.module.modules.misc.cheatdetector.impl.NoSlowCheck;
import net.minecraft.entity.player.EntityPlayer;

public class CheatDetectorData {
  public AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
  public KillauraCheck killauraCheck = new KillauraCheck();
  public NoSlowCheck noSlowCheck = new NoSlowCheck();
  public LegitScaffoldCheck legitScaffoldCheck = new LegitScaffoldCheck();

  public void onUpdate(EntityPlayer player) {
    CheatDetector cd = (CheatDetector) Miau.moduleManager.getModule(CheatDetector.class);
    if (cd == null) return;

    if (cd.isCheckEnabled(autoBlockCheck.getName())) autoBlockCheck.onUpdate(player);
    if (cd.isCheckEnabled(killauraCheck.getName())) killauraCheck.onUpdate(player);
    if (cd.isCheckEnabled(noSlowCheck.getName())) noSlowCheck.onUpdate(player);
    if (cd.isCheckEnabled(legitScaffoldCheck.getName())) legitScaffoldCheck.onUpdate(player);
  }

  public void onPacket(PacketEvent e, EntityPlayer player) {
    CheatDetector cd = (CheatDetector) Miau.moduleManager.getModule(CheatDetector.class);
    if (cd == null) return;

    if (cd.isCheckEnabled(autoBlockCheck.getName())) autoBlockCheck.onPacket(e, player);
    if (cd.isCheckEnabled(killauraCheck.getName())) killauraCheck.onPacket(e, player);
    if (cd.isCheckEnabled(noSlowCheck.getName())) noSlowCheck.onPacket(e, player);
    if (cd.isCheckEnabled(legitScaffoldCheck.getName())) legitScaffoldCheck.onPacket(e, player);
  }
}
