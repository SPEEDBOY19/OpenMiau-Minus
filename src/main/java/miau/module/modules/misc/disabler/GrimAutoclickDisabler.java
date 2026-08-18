package miau.module.modules.misc.disabler;

import miau.Miau;
import miau.event.impl.LivingUpdateEvent;
import miau.module.modules.misc.Disabler;
import miau.module.modules.combat.KillAura;
import miau.module.modules.player.Scaffold;
import miau.module.modules.ghost.AutoClicker;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class GrimAutoclickDisabler extends DisablerMode {

  public GrimAutoclickDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (mc.thePlayer == null || mc.getNetHandler() == null) {
      return;
    }

    boolean isScaffActive = false;
    Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
    if (scaffold != null && scaffold.isEnabled()) {
      isScaffActive = true;
    }


    boolean isKuraActive = false;
    KillAura aura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (aura != null && aura.isEnabled()) {
      isKuraActive = true;
    }

    boolean isAClickActive = false;
    AutoClicker autoclicker = (AutoClicker) Miau.moduleManager.modules.get(AutoClicker.class);
    if (autoclicker != null && autoclicker.isEnabled()) {
      isAClickActive = true;
    }

    if ((isScaffActive || isKuraActive || isAClickActive) && mc.thePlayer.ticksExisted % 20 == 0) {
      mc.getNetHandler().addToSendQueue(
          new C07PacketPlayerDigging(
              C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
              new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ),
              EnumFacing.DOWN
          )
      );
    }
  }
}
