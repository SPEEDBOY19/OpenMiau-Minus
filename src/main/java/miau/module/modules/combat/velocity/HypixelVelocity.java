package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorS12PacketEntityVelocity;
import miau.module.modules.combat.Velocity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class HypixelVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;
  private boolean absorbedVelocity = false;

  public HypixelVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (!(event.getPacket() instanceof S12PacketEntityVelocity)) return;
    S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
    if (packet.getEntityID() != player.getEntityId()) return;
    hasReceivedVelocity = true;
    if (!player.onGround) {
      if (!absorbedVelocity) {
        event.setCancelled(true);
        absorbedVelocity = true;
        return;
      }
    }
    IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
    accessor.setMotionX((int) (player.motionX * 8000));
    accessor.setMotionZ((int) (player.motionZ * 8000));
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (hasReceivedVelocity && player.onGround) {
      absorbedVelocity = false;
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
    absorbedVelocity = false;
  }
}