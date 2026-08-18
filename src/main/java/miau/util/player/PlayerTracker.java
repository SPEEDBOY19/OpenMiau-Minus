package miau.util.player;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class PlayerTracker {
  public static float fallDistance = 0;
  public static int onGroundTicks = 0;
  public static int ticksSinceVelocity = 0;
  private static final Minecraft mc = Minecraft.getMinecraft();

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      ticksSinceVelocity++;

      if (mc.thePlayer != null) {
        double fallDist = mc.thePlayer.lastTickPosY - mc.thePlayer.posY;
        if (fallDist > 0) {
          fallDistance += fallDist;
        }

        if (mc.thePlayer.onGround) {
          fallDistance = 0;
          onGroundTicks++;
        } else {
          onGroundTicks = 0;
        }
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE) {
      if (event.getPacket() instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (mc.thePlayer != null && packet.getEntityID() == mc.thePlayer.getEntityId()) {
          ticksSinceVelocity = 0;
        }
      } else if (event.getPacket() instanceof S27PacketExplosion) {
        ticksSinceVelocity = 0;
      }
    }
  }
}
