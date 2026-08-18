package miau.util.player;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

/** Holds an EnumFacing and its corresponding Vec3 offset. Ported from Rise 6. */
public class EnumFacingOffset {
  private final EnumFacing enumFacing;
  private final Vec3 offset;

  public EnumFacingOffset(final EnumFacing enumFacing, final Vec3 offset) {
    this.enumFacing = enumFacing;
    this.offset = offset;
  }

  public EnumFacing getEnumFacing() {
    return enumFacing;
  }

  public Vec3 getOffset() {
    return offset;
  }
}
