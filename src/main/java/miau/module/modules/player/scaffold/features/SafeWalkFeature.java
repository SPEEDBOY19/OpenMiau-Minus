package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.SafeWalkEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;

public class SafeWalkFeature implements ScaffoldComponent {
  private final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);

  public SafeWalkFeature(Scaffold scaffold) {}

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(safeWalk);
  }

  @Override
  public void onSafeWalk(SafeWalkEvent event) {
    if (mc.thePlayer != null && this.safeWalk.getValue()) {
      if (mc.thePlayer.onGround
          && mc.thePlayer.motionY <= 0.0
          && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
        event.setSafeWalk(true);
      }
    }
  }
}
