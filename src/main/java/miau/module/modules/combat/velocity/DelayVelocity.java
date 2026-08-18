package miau.module.modules.combat.velocity;

import java.util.concurrent.ConcurrentLinkedDeque;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.Velocity;
import miau.property.properties.FloatProperty;
import miau.util.network.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class DelayVelocity extends VelocityMode {
  public final FloatProperty minimumDelay = new FloatProperty("Minimum-delay", 100.0f, 0.0f, 1000.0f);
  public final FloatProperty maximumDelay = new FloatProperty("Maximum-delay", 200.0f, 50.0f, 1000.0f);

  private final ConcurrentLinkedDeque<Packet<?>> delayedPackets = new ConcurrentLinkedDeque<>();
  private long lagStartTime = -1;
  private long targetDelay = 0;

  public DelayVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    delayedPackets.clear();
    lagStartTime = -1;
  }

  @Override
  public void onDisable() {
    flushDelayedPackets();
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == miau.event.types.EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
        flushDelayedPackets();
        return;
      }

      if (lagStartTime == -1) return;

      long nowMs = System.currentTimeMillis();
      if (nowMs - lagStartTime >= targetDelay) {
        flushDelayedPackets();
      }
    }
  }

  @Override
  public void onPacket(PacketEvent e) {
    if (e.getType() != miau.event.types.EventType.RECEIVE) return;

    if (e.getPacket() instanceof S08PacketPlayerPosLook) {
      flushDelayedPackets();
      return;
    }

    if (lagStartTime != -1) {
      e.setCancelled(true);
      delayedPackets.addLast(e.getPacket());
      return;
    }

    if (!(e.getPacket() instanceof S12PacketEntityVelocity)) {
      return;
    }

    if (mc.thePlayer == null || mc.theWorld == null) {
      return;
    }

    S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.getPacket();
    if (packet.getEntityID() != mc.thePlayer.getEntityId()) {
      return;
    }

    e.setCancelled(true);
    delayedPackets.addLast(e.getPacket());
    lagStartTime = System.currentTimeMillis();
    long minD = minimumDelay.getValue().longValue();
    long maxD = maximumDelay.getValue().longValue();
    if (minD > maxD) minD = maxD;
    targetDelay = minD + (long) (Math.random() * (maxD - minD + 1));
  }

  @SuppressWarnings("unchecked")
  private void flushDelayedPackets() {
    lagStartTime = -1;
    if (mc.thePlayer != null && mc.getNetHandler() != null) {
      while (!delayedPackets.isEmpty()) {
        Packet<?> packet = delayedPackets.pollFirst();
        if (packet != null) {
          PacketUtil.handlePacket((Packet<INetHandlerPlayClient>) packet);
        }
      }
    }
    delayedPackets.clear();
  }
}
