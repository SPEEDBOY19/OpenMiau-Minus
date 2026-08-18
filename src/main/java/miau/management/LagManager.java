package miau.management;

import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.Vec3;

public class LagManager {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final Deque<LagPacket> packetQueue;

  private int tickDelay;

  private long msDelay;
  private boolean usingMsDelay;

  private boolean flushing;
  private Vec3 lastPosition;

  /**
   * FastTrack set: packets in this set bypass the queue on re-entry. Set by LagRange (or any lag
   * module) before flushing to prevent flushed packets from being re-queued by the LagManager. Uses
   * IdentityHashMap so packet identity (==) is used, not equals().
   */
  public Set<Packet<?>> fastTrackSet;

  public LagManager() {
    this.packetQueue = new ConcurrentLinkedDeque<>();
    this.tickDelay = 0;
    this.msDelay = 0;
    this.usingMsDelay = false;
    this.flushing = false;
    this.lastPosition = new Vec3(0.0, 0.0, 0.0);
  }

  /**
   * Flush all packets from the queue whose release conditions are met. - Tick mode: packet's
   * accumulated delay must exceed current tickDelay - Ms mode: elapsed time since enqueue must be
   * >= msDelay
   */
  private void flushQueue() {
    if (mc.getNetHandler() == null) {
      this.packetQueue.clear();
      return;
    }
    this.flushing = true;
    try {
      while (!this.packetQueue.isEmpty()) {
        LagPacket lp = this.packetQueue.peek();
        boolean canRelease;

        if (this.usingMsDelay) {

          canRelease =
              this.msDelay <= 0L || (System.currentTimeMillis() - lp.enqueueTimeMs) >= this.msDelay;
        } else {

          canRelease = this.tickDelay <= 0 || lp.delay > this.tickDelay;
        }

        if (!canRelease) break;

        this.packetQueue.poll();
        PacketUtil.sendPacketNoEvent(lp.packet);

        if (lp.packet instanceof C03PacketPlayer) {
          C03PacketPlayer c03 = (C03PacketPlayer) lp.packet;
          if (c03.isMoving()) {
            this.lastPosition =
                new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
          }
        }
      }
    } finally {
      this.flushing = false;
    }
  }

  private void incrementDelays() {
    this.packetQueue.forEach(z -> z.delay++);
  }

  /**
   * Called from MixinNetworkManager.sendPacket() for every outbound packet. Returns true if the
   * packet was queued (caller should cancel sending), false if it was passed through.
   */
  public boolean handlePacket(Packet<?> packet) {
    this.flushQueue();

    if (this.fastTrackSet != null && this.fastTrackSet.remove(packet)) {
      if (packet instanceof C03PacketPlayer) {
        C03PacketPlayer c03 = (C03PacketPlayer) packet;
        if (c03.isMoving()) {
          this.lastPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
        }
      }
      return false;
    }

    if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
      return false;
    }

    boolean shouldQueue = this.usingMsDelay ? this.msDelay > 0L : this.tickDelay > 0;

    if (shouldQueue) {
      this.packetQueue.offer(new LagPacket(packet));
      return true;
    } else {
      if (packet instanceof C03PacketPlayer) {
        C03PacketPlayer c03 = (C03PacketPlayer) packet;
        if (c03.isMoving()) {
          this.lastPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
        }
      }
      return false;
    }
  }

  /** Set delay in game ticks (50ms each). Used by BlockHit for backward compatibility. */
  public void setDelay(int ticks) {
    this.tickDelay = ticks;
    this.usingMsDelay = false;
    this.msDelay = 0L;
  }

  /** Set delay in milliseconds. Used by LagRange for precise time-based release. */
  public void setDelayMs(long ms) {
    this.msDelay = ms;
    this.usingMsDelay = true;
    this.tickDelay = 0;
  }

  /** Reset all delays immediately (both tick and ms). */
  public void resetDelay() {
    this.tickDelay = 0;
    this.msDelay = 0L;
    this.usingMsDelay = false;
  }

  public Vec3 getLastPosition() {
    return this.lastPosition;
  }

  public boolean isFlushing() {
    return this.flushing;
  }

  /** Expose whether the manager is currently operating in ms-precision mode. */
  public boolean isUsingMsDelay() {
    return this.usingMsDelay;
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() == EventType.POST) {
      if (mc.thePlayer.isDead) {
        this.resetDelay();
      }

      if (!this.usingMsDelay) {
        this.incrementDelays();
      }
      this.flushQueue();
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getPacket() instanceof C00Handshake
        || event.getPacket() instanceof C00PacketLoginStart
        || event.getPacket() instanceof C00PacketServerQuery
        || event.getPacket() instanceof C01PacketPing
        || event.getPacket() instanceof C01PacketEncryptionResponse) {
      this.resetDelay();
    }
  }

  public static class LagPacket {
    public final Packet<?> packet;

    /** Tick-based age counter (incremented each tick in tick mode). */
    public int delay;

    /** Timestamp when this packet was enqueued (for ms-based release). */
    public final long enqueueTimeMs;

    public LagPacket(Packet<?> packet) {
      this.packet = packet;
      this.delay = 0;
      this.enqueueTimeMs = System.currentTimeMillis();
    }
  }
}
