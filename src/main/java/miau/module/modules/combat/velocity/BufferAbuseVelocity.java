package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorS12PacketEntityVelocity;
import miau.mixin.IAccessorS27PacketExplosion;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class BufferAbuseVelocity extends VelocityMode {
  public final IntProperty bufferPacket = new IntProperty("buffer-packet", 3, 1, 5);
  public final FloatProperty bufferHorizontal = new FloatProperty("buffer-horizontal", 1.0f, 0.0f, 1.0f);
  public final FloatProperty bufferVertical = new FloatProperty("buffer-vertical", 1.0f, 0.0f, 1.0f);
  public final BooleanProperty bufferDebugger = new BooleanProperty("buffer-debugger", false);

  private int bufferAmount = 0;

  public BufferAbuseVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    bufferAmount = 0;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() != player.getEntityId()) return;
      if (bufferAmount < bufferPacket.getValue()) {
        event.setCancelled(true);
        bufferAmount++;
        if (bufferDebugger.getValue()) {
          ChatUtil.display("&7[BufferAbuse] Cancelled packet " + bufferAmount + "/" + bufferPacket.getValue());
        }
        return;
      }
      IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
      accessor.setMotionX((int) (packet.getMotionX() * bufferHorizontal.getValue()));
      accessor.setMotionY((int) (packet.getMotionY() * bufferVertical.getValue()));
      accessor.setMotionZ((int) (packet.getMotionZ() * bufferHorizontal.getValue()));
      bufferAmount = 0;
      if (bufferDebugger.getValue()) {
        ChatUtil.display("&7[BufferAbuse] Applied reduction: H=" + bufferHorizontal.getValue() + ", V=" + bufferVertical.getValue());
      }
    } else if (event.getPacket() instanceof S27PacketExplosion) {
      if (bufferAmount < bufferPacket.getValue()) {
        event.setCancelled(true);
        bufferAmount++;
        if (bufferDebugger.getValue()) {
          ChatUtil.display("&7[BufferAbuse] Cancelled explosion " + bufferAmount + "/" + bufferPacket.getValue());
        }
        return;
      }
      IAccessorS27PacketExplosion accessor = (IAccessorS27PacketExplosion) event.getPacket();
      accessor.setMotionX(accessor.getMotionX() * bufferHorizontal.getValue());
      accessor.setMotionY(accessor.getMotionY() * bufferVertical.getValue());
      accessor.setMotionZ(accessor.getMotionZ() * bufferHorizontal.getValue());
      bufferAmount = 0;
      if (bufferDebugger.getValue()) {
        ChatUtil.display("&7[BufferAbuse] Applied explosion reduction");
      }
    }
  }

  @Override
  public void onDisable() {
    bufferAmount = 0;
  }
}