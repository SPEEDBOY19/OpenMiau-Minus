package miau.module.modules.movement.nofalls;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.movement.NoFall;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Vulcan 2.7.7 NoFall mode.
 *
 * @author CCBlueX (original LiquidBounce)
 */
public class VulcanNoFall extends NoFallMode {

  public VulcanNoFall(String name, NoFall parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND
        && event.getPacket() instanceof C03PacketPlayer
        && mc.thePlayer.fallDistance > 7.0f) {
      ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(true);
      mc.thePlayer.fallDistance = 0.0F;
      mc.thePlayer.motionY = 0.0;
    }
  }
}
