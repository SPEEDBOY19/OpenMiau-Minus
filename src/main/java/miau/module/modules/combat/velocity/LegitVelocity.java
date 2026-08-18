package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.mixin.IAccessorMinecraft;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class LegitVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;
  private float velocityYaw = 0.0f;
  private int nextHurtTime = 0;
  private int nextAttacks = 0;

  public final BooleanProperty directionReduce = new BooleanProperty("direction-reduce", false);
  public final BooleanProperty singleTickAttack = new BooleanProperty("single-tick-attack", false);
  public final IntProperty mHurtTime =
      new IntProperty("min-hurt-time", 9, 1, 10, this.singleTickAttack::getValue);
  public final IntProperty mmHurtTime =
      new IntProperty("max-hurt-time", 10, 1, 10, this.singleTickAttack::getValue);
  public final IntProperty minAttacks =
      new IntProperty("min-attacks", 3, 1, 20, this.singleTickAttack::getValue);
  public final IntProperty maxAttacks =
      new IntProperty("max-attacks", 5, 1, 20, this.singleTickAttack::getValue);

  public LegitVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == miau.event.types.EventType.RECEIVE
        && event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) event.getPacket();
      if (s12.getEntityID() == mc.thePlayer.getEntityId()) {
        double velX = s12.getMotionX() / 8000.0D;
        double velZ = s12.getMotionZ() / 8000.0D;
        velocityYaw =
            (float)
                net.minecraft.util.MathHelper.wrapAngleTo180_double(
                    Math.toDegrees(Math.atan2(velZ, velX)) + 90);
        hasReceivedVelocity = true;

        if (this.singleTickAttack.getValue()) {
          nextHurtTime =
              mHurtTime.getValue()
                  + (int) (Math.random() * ((mmHurtTime.getValue() - mHurtTime.getValue()) + 1));
          nextAttacks =
              minAttacks.getValue()
                  + (int) (Math.random() * ((maxAttacks.getValue() - minAttacks.getValue()) + 1));
        }
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == miau.event.types.EventType.PRE) {
      if (this.singleTickAttack.getValue() && hasReceivedVelocity) {
        if (mc.thePlayer.hurtTime == nextHurtTime
            && mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit
                == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY
            && !mc.thePlayer.isUsingItem()) {
          for (int i = 0; i < nextAttacks; i++) {
            ((IAccessorMinecraft) mc).callClickMouse();
          }
        }
      }
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onMoveInput(miau.event.impl.MoveInputEvent event) {
    if (this.directionReduce.getValue() && mc.thePlayer.hurtTime != 0) {
      miau.module.modules.combat.KillAura killAura =
          (miau.module.modules.combat.KillAura)
              miau.Miau.moduleManager.modules.get(miau.module.modules.combat.KillAura.class);
      if (killAura.isEnabled() && killAura.getTarget() != null) {
        miau.util.player.MoveUtil.fixMovement(velocityYaw);
      }
    }
  }
}
