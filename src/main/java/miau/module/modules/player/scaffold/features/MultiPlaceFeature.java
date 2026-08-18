package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.module.modules.player.scaffold.ScaffoldPlacementUtil;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.util.player.RotationUtil;
import miau.util.world.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class MultiPlaceFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(multiplace);
  }

  public MultiPlaceFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (this.multiplace.getValue()) {
      for (int i = 0; i < 3; i++) {
        Scaffold.BlockData blockData = scaffold.getBlockData();
        if (blockData == null) {
          break;
        }
        MovingObjectPosition mop =
            ScaffoldPlacementUtil.verifyPlacement(blockData, scaffold.yaw, scaffold.pitch);
        if (mop != null) {
          scaffold.place(blockData.blockPos, blockData.facing, mop.hitVec);
        } else {
          Vec3 hitVec = BlockUtil.getClickVec(blockData.blockPos, blockData.facing);
          double dx = hitVec.xCoord - mc.thePlayer.posX;
          double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
          double dz = hitVec.zCoord - mc.thePlayer.posZ;
          float[] rotations =
              RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
          if (!(Math.abs(rotations[0] - scaffold.yaw) < 120.0F)
              || !(Math.abs(rotations[1] - scaffold.pitch) < 60.0F)) {
            break;
          }
          mop = ScaffoldPlacementUtil.verifyPlacement(blockData, rotations[0], rotations[1]);
          if (mop == null) {
            break;
          }
          scaffold.place(blockData.blockPos, blockData.facing, mop.hitVec);
        }
      }
    }
  }
}
