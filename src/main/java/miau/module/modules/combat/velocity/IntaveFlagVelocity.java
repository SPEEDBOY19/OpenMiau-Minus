package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.util.network.PacketUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class IntaveFlagVelocity extends VelocityMode {
  private boolean intaFlag = false;

  public IntaveFlagVelocity(String name, Velocity parent) {
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
        intaFlag = false;
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime >= 9 && !intaFlag) {
      PacketUtil.sendPacket(
          new C03PacketPlayer.C04PacketPlayerPosition(
              player.posX + 6.0,
              player.posY + 1.0,
              player.posZ + 6.0,
              false));
      intaFlag = true;
    }
  }

  @Override
  public void onDisable() {
    intaFlag = false;
  }
}