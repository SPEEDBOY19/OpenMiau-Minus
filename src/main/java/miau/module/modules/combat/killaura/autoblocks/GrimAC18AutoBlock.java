package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.module.modules.combat.KillAura2;
import miau.module.modules.combat.KillAuraV2;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemSword;

public class GrimAC18AutoBlock extends AutoBlockMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public GrimAC18AutoBlock(KillAura parent) {
    super("GRIMAC-1.8", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.getTarget() != null) {
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
  public void onPostUpdate() {
    if (parent.isBlocking
        && parent.getTarget() != null
        && mc.thePlayer.getHeldItem() != null
        && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
      mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
    }
  }

  @Override
  public void onDisable() {
    parent.setRightHold(false);
    mc.thePlayer.stopUsingItem();
  }
}
