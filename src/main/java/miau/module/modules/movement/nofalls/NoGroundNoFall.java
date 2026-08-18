package miau.module.modules.movement.nofalls;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.movement.NoFall;
import net.minecraft.network.play.client.C03PacketPlayer;

public class NoGroundNoFall extends NoFallMode {
  public NoGroundNoFall(String name, NoFall parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
      ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
    }
  }
}
