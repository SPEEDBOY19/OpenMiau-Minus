package miau.port.dew;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/** Glue stub mirroring Dew's Hud module, providing block-count helpers used by Scaffold. */
public class Hud {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public boolean isEnabled() {
    miau.module.Module m = DewCommon.moduleManager.bound(Hud.class);
    return m != null && m.isEnabled();
  }

  public void markModuleListDirty() {}

  public int getTotalValidBlocksInHotbar() {
    int total = 0;
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (DewCommon.scaffoldGlue.isInvalidBlock(stack)) continue;
      total += stack.stackSize;
    }
    return total;
  }

  public boolean isInvalidBlock(ItemStack stack) {
    return DewCommon.scaffoldGlue.isInvalidBlock(stack);
  }
}
