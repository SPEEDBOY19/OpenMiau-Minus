package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C0APacketAnimation;

public class SwingFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty swing =
      new ModeProperty("swing", 0, new String[] {"NORMAL", "NONE", "PLACE", "PACKET"});

  public SwingFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(swing);
  }

  @Override
  public void onBlockPlaced() {
    int mode = swing.getValue();
    if (mode == 3) {
      PacketUtil.sendPacket(new C0APacketAnimation());
    }
    if (mode == 1 || mode == 3) {
      mc.thePlayer.isSwingInProgress = false;
      mc.thePlayer.swingProgress = 0.0F;
      mc.thePlayer.swingProgressInt = 0;
    }
  }
}
