package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.FloatProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class AACVelocity extends VelocityMode {
  private long velocityTimer = 0;
  private boolean hasReceivedVelocity = false;

  public final FloatProperty horizontal = new FloatProperty("horizontal", 0f, -1f, 1f);

  public AACVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == player.getEntityId()) {
        velocityTimer = System.currentTimeMillis();
        hasReceivedVelocity = true;
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (hasReceivedVelocity && System.currentTimeMillis() - velocityTimer >= 80) {
      VelocityUtil.reduceXZ(horizontal.getValue());
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}