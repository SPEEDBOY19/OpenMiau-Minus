package miau.port.dew;

import net.minecraft.block.*;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Shared block-slot validity helper mirroring Dew's Scaffold.isInvalidBlock. */
public class ScaffoldGlue {

  public boolean isInvalidBlock(ItemStack stack) {
    if (stack == null || !(stack.getItem() instanceof ItemBlock) || stack.stackSize == 0) {
      return true;
    }

    Block block = ((ItemBlock) stack.getItem()).getBlock();

    if (block instanceof BlockGlass
        || block instanceof BlockStainedGlass
        || block instanceof BlockIce
        || block instanceof BlockPackedIce) {
      return false;
    }

    return !block.isFullBlock()
        || block instanceof BlockChest
        || block instanceof BlockEnderChest
        || block instanceof BlockWorkbench
        || block instanceof BlockFurnace
        || block instanceof BlockAnvil
        || block instanceof BlockFenceGate
        || block instanceof BlockTrapDoor
        || block instanceof BlockDoor
        || block.getMaterial().isReplaceable()
        || block instanceof BlockFalling
        || block instanceof BlockSlab
        || block instanceof BlockStairs
        || block instanceof BlockPane
        || block instanceof BlockWall
        || block instanceof BlockSign
        || block instanceof BlockButton
        || block instanceof BlockPressurePlate;
  }
}
