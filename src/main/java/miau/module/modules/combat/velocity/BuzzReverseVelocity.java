package miau.module.modules.combat.velocity;

import miau.event.impl.AttackEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class BuzzReverseVelocity extends VelocityMode {
  public final BooleanProperty needAttack = new BooleanProperty("need-attack", false);

  private boolean hasReceivedVelocity = false;

  public BuzzReverseVelocity(String name, Velocity parent) {
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
        hasReceivedVelocity = true;
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime == 7 && hasReceivedVelocity) {
      if (needAttack.getValue()) return;
      VelocityUtil.reduceXZ(-1.0);
      hasReceivedVelocity = false;
    }
    if (player.hurtTime == 0 && hasReceivedVelocity) {
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onAttack(AttackEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (needAttack.getValue() && player.hurtTime == 7 && hasReceivedVelocity) {
      VelocityUtil.reduceXZ(-1.0);
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
  }
}