package miau.module.modules.combat.velocity;

import miau.event.impl.JumpEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import net.minecraft.entity.player.EntityPlayer;

public class AACPushVelocity extends VelocityMode {
  private boolean jump = false;

  public final FloatProperty aacPushXZReducer = new FloatProperty("xz-reducer", 2f, 1f, 3f);
  public final BooleanProperty aacPushYReducer = new BooleanProperty("y-reducer", true);

  public AACPushVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (jump) {
      if (player.onGround) jump = false;
    } else {
      if (player.hurtTime > 0 && player.motionX != 0.0 && player.motionZ != 0.0) {
        player.onGround = true;
      }
      if (player.hurtResistantTime > 0 && aacPushYReducer.getValue()) {
        player.motionY -= 0.014999993;
      }
    }
    if (player.hurtResistantTime >= 19) {
      float reduce = aacPushXZReducer.getValue();
      player.motionX /= reduce;
      player.motionZ /= reduce;
    }
  }

  @Override
  public void onJump(JumpEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    jump = true;
  }
}