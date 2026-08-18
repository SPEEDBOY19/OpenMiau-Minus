package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.FloatProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class GhostBlockVelocity extends VelocityMode {
  public final FloatProperty minHurtTime = new FloatProperty("min-hurt-time", 1f, 1f, 10f);
  public final FloatProperty maxHurtTime = new FloatProperty("max-hurt-time", 9f, 1f, 10f);

  private boolean hasReceivedVelocity = false;

  public GhostBlockVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
      EntityPlayer player = Velocity.mc.thePlayer;
      if (player == null) return;
      if (event.getPacket() instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() == player.getEntityId()) {
          hasReceivedVelocity = true;
        }
      }
    }
  }

  @Override
  public void onUpdate(miau.event.impl.UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (hasReceivedVelocity
        && player.hurtTime >= minHurtTime.getValue().intValue()
        && player.hurtTime <= maxHurtTime.getValue().intValue()) {
      player.noClip = true;
    } else if (player.hurtTime == 0) {
      hasReceivedVelocity = false;
      player.noClip = false;
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}