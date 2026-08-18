package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.FloatProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class AACv4Velocity extends VelocityMode {
  public final FloatProperty aacv4MotionReducer = new FloatProperty("motion-reducer", 0.62f, 0f, 1f);

  public AACv4Velocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime > 0 && !player.onGround) {
      VelocityUtil.reduceXZ(aacv4MotionReducer.getValue());
    }
  }
}