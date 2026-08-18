package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.network.PacketUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class NoFluid extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty waterValue = new BooleanProperty("Water", true);
  public final BooleanProperty lavaValue = new BooleanProperty("Lava", true);
  public final BooleanProperty oldGrim = new BooleanProperty("OldGrim", false);

  public NoFluid() {
    super("NoFluid", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if ((this.waterValue.getValue() || this.lavaValue.getValue()) && this.oldGrim.getValue()) {
      int baseX = (int) mc.thePlayer.posX;
      int baseY = (int) mc.thePlayer.posY;
      int baseZ = (int) mc.thePlayer.posZ;
      for (int x = 2; x >= -1; x--) {
        for (int y = 2; y >= -1; y--) {
          for (int z = 2; z >= -1; z--) {
            BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block == Blocks.water || block == Blocks.lava) {
              PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, EnumFacing.DOWN));
            }
          }
        }
      }
    }
  }
}