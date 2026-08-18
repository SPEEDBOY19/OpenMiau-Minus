package miau.util.render;

import net.minecraft.block.BlockBed;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;

public final class BedFootHighlightMatcher implements BlockHighlightMatcher {

  @Override
  public boolean matchesBlock(IBlockState state) {
    return state != null && state.getBlock() instanceof BlockBed;
  }

  @Override
  public boolean shouldIndexAt(BlockPos pos, IBlockState state) {
    if (!matchesBlock(state)) {
      return false;
    }
    return state.getValue((IProperty) BlockBed.PART) == BlockBed.EnumPartType.FOOT;
  }
}
