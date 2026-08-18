package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.module.modules.combat.KillAura2;
import miau.module.modules.combat.KillAuraV2;
import miau.util.math.RandomUtil;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

public class GrimAC112AutoBlock extends AutoBlockMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public GrimAC112AutoBlock(KillAura parent) {
    super("GRIMAC-1.12", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.getTarget() != null) {
      if (mc.thePlayer.getHeldItem() != null
          && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
        PacketUtil.sendPacket(
            new C0FPacketConfirmTransaction(
                RandomUtil.nextInt(0, 2147483647), (short) RandomUtil.nextInt(-32767, 0), true));
        PacketUtil.sendPacket(new C0APacketAnimation());
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
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
    parent.setRightHold(false);
    mc.thePlayer.stopUsingItem();
  }
}
