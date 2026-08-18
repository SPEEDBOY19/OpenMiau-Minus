package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class CancelVelocity extends VelocityMode {
  public final BooleanProperty cancelHorizontal = new BooleanProperty("cancel-horizontal", true);
  public final BooleanProperty cancelVertical = new BooleanProperty("cancel-vertical", true);
  public final BooleanProperty cancelVerticalOnlyInAir =
      new BooleanProperty("cancel-vertical-only-in-air", false);

  public CancelVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() != player.getEntityId()) return;
      event.setCancelled(true);

      boolean hCancel = cancelHorizontal.getValue();
      boolean vCancel = cancelVertical.getValue();
      boolean vOnlyInAir = cancelVerticalOnlyInAir.getValue();

      if (hCancel && vCancel && !vOnlyInAir) return;

      if (!hCancel) {
        player.motionX = packet.getMotionX() / 8000.0;
        player.motionZ = packet.getMotionZ() / 8000.0;
      }

      boolean shouldCancelVertical = vCancel || (vOnlyInAir && !player.onGround);
      if (!shouldCancelVertical) {
        player.motionY = packet.getMotionY() / 8000.0;
      }
    }
  }
}