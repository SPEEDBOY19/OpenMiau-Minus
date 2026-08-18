package miau.event.impl;

import miau.event.Event;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

public class BlockPlaceEvent implements Event {
  private final BlockPos pos;
  private final ItemStack stack;

  public BlockPlaceEvent(BlockPos pos, ItemStack stack) {
    this.pos = pos;
    this.stack = stack;
  }

  public BlockPos getPos() {
    return this.pos;
  }

  public ItemStack getStack() {
    return this.stack;
  }
}
