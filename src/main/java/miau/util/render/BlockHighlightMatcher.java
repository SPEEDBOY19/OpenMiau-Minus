package miau.util.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;

public interface BlockHighlightMatcher {
  boolean matchesBlock(IBlockState state);

  default boolean shouldIndexAt(BlockPos pos, IBlockState state) {
    return matchesBlock(state);
  }

  default boolean isActive() {
    return true;
  }

  default void beginScanPass() {}
}
