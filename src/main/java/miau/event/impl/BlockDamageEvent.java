package miau.event.impl;

import miau.event.Event;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;

public class BlockDamageEvent implements Event {
  private final EntityPlayer player;
  private final BlockPos blockPos;

  public BlockDamageEvent(EntityPlayer player, BlockPos blockPos) {
    this.player = player;
    this.blockPos = blockPos;
  }

  public EntityPlayer getPlayer() {
    return this.player;
  }

  public BlockPos getBlockPos() {
    return this.blockPos;
  }
}
