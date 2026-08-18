package miau.module.modules.movement.nofalls;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.movement.NoFall;
import miau.util.player.PlayerUtil;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;

public class SpoofNoFall extends NoFallMode {
  public SpoofNoFall(String name, NoFall parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
      C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
      if (!packet.isOnGround()) {
        AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
        if (PlayerUtil.canFly(parent.distance.getValue())
            && !PlayerUtil.checkInWater(aabb)
            && parent.canTrigger()) {
          parent.packetDelayTimer.reset();
          ((IAccessorC03PacketPlayer) packet).setOnGround(true);
          mc.thePlayer.fallDistance = 0.0F;
        }
      }
    }
  }
}
