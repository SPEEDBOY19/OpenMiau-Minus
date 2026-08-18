package miau.module.modules.combat.velocity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.IntProperty;
import miau.util.network.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

public class LiquidBounceDelayVelocity extends VelocityMode {
  public final IntProperty spoofDelay = new IntProperty("spoof-delay", 500, 0, 5000);

  private final Map<Packet<?>, Long> packets = new HashMap<>();
  private boolean delayMode = false;

  public LiquidBounceDelayVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    packets.clear();
    delayMode = false;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    if (event.getPacket() instanceof S32PacketConfirmTransaction
        || event.getPacket() instanceof S12PacketEntityVelocity) {
      event.setCancelled(true);
      synchronized (packets) {
        packets.put(event.getPacket(), System.currentTimeMillis());
      }
      delayMode = true;
    }
  }

  @Override
  public void onTick(TickEvent event) {
    if (delayMode) {
      sendPacketsByOrder(false);
    }
  }

  @Override
  public void onDisable() {
    sendPacketsByOrder(true);
    packets.clear();
    delayMode = false;
  }

  @SuppressWarnings("unchecked")
  private void sendPacketsByOrder(boolean velocity) {
    synchronized (packets) {
      Iterator<Map.Entry<Packet<?>, Long>> it = packets.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<Packet<?>, Long> entry = it.next();
        if (velocity || entry.getValue() <= System.currentTimeMillis() - spoofDelay.getValue()) {
          PacketUtil.handlePacket((Packet<INetHandlerPlayClient>) entry.getKey());
          it.remove();
        }
      }
    }
  }
}