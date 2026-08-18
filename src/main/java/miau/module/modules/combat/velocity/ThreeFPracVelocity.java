package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

public class ThreeFPracVelocity extends VelocityMode {

  private int grimTCancel = 0;
  private int updates = 0;

  public ThreeFPracVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    grimTCancel = 0;
    updates = 0;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE) {
      if (event.getPacket() instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
          event.setCancelled(true);
          grimTCancel = 6;
        }
      }

      if (event.getPacket() instanceof S32PacketConfirmTransaction && grimTCancel > 0) {
        event.setCancelled(true);
        grimTCancel--;
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      updates++;
      if (updates >= 10) {
        updates = 0;
        if (grimTCancel > 0) {
          grimTCancel--;
        }
      }
    }
  }
}
