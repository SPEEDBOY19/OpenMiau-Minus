package miau.util.network;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.Vec3;

/**
 * Bidirectional packet blinking used by velocity/timer modules. Ported from FB's BlinkUtils.
 */
public final class BlinkUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final List<Packet<?>> packets = new CopyOnWriteArrayList<>();
  private static final List<Packet<?>> packetsReceived = new CopyOnWriteArrayList<>();
  private static final List<Vec3> positions = new CopyOnWriteArrayList<>();

  private static Predicate<Packet<?>> activeFilter = null;
  private static int filterMaxAllowCount = Integer.MAX_VALUE;
  private static int currentAllowedCount = 0;

  private static Timer blinkTimeoutTimer = null;
  private static TimerTask blinkTimeoutTask = null;
  private static boolean isTimeoutScheduled = false;

  private BlinkUtil() {}

  public static boolean isBlinking() {
    return !packets.isEmpty() || !packetsReceived.isEmpty();
  }

  public static void blink(PacketEvent event) {
    blink(event, true, true, null, Integer.MAX_VALUE, null);
  }

  public static void blink(PacketEvent event, boolean sent, boolean receive) {
    blink(event, sent, receive, null, Integer.MAX_VALUE, null);
  }

  public static void blink(
      PacketEvent event,
      boolean sent,
      boolean receive,
      Predicate<Packet<?>> receiveFilter,
      int maxAllowedPackets,
      Long blinkTimes) {
    if (mc.thePlayer == null) return;
    Packet<?> packet = event.getPacket();
    if (event.isCancelled() || mc.thePlayer.isDead || mc.getCurrentServerData() == null) return;

    if (packet instanceof C00Handshake
        || packet instanceof C00PacketServerQuery
        || packet instanceof C01PacketPing
        || packet instanceof S02PacketChat
        || packet instanceof C01PacketChatMessage
        || packet instanceof C00PacketLoginStart
        || packet instanceof C01PacketEncryptionResponse) {
      return;
    }
    if (packet instanceof S29PacketSoundEffect
        && "game.player.hurt".equals(((S29PacketSoundEffect) packet).getSoundName())) {
      return;
    }

    if ((!isBlinking() || !isTimeoutScheduled) && blinkTimes != null && blinkTimes > 0) {
      scheduleAutoUnblink(blinkTimes);
    }
    if (isTimeoutScheduled && blinkTimeoutTask == null) {
      return;
    }

    if (activeFilter != receiveFilter || filterMaxAllowCount != maxAllowedPackets) {
      activeFilter = receiveFilter;
      filterMaxAllowCount = maxAllowedPackets;
      currentAllowedCount = 0;
    }

    boolean shouldReceive = false;
    if (receiveFilter != null) {
      shouldReceive = receiveFilter.test(packet);
      if (shouldReceive && currentAllowedCount < filterMaxAllowCount) {
        currentAllowedCount++;
        return;
      } else if (shouldReceive) {
        shouldReceive = false;
      }
    }

    if (event.getType() == EventType.RECEIVE) {
      if (receive) {
        if (mc.thePlayer.ticksExisted <= 10) return;
        if (shouldReceive) return;
        event.setCancelled(true);
        synchronized (packetsReceived) {
          packetsReceived.add(packet);
        }
      } else if (sent) {
        if (shouldReceive) return;
        flushReceived();
      }
    } else if (event.getType() == EventType.SEND) {
      if (sent) {
        event.setCancelled(true);
        synchronized (packets) {
          packets.add(packet);
        }
        if (packet instanceof C03PacketPlayer && ((C03PacketPlayer) packet).isMoving()) {
          C03PacketPlayer move = (C03PacketPlayer) packet;
          synchronized (positions) {
            positions.add(new Vec3(move.getPositionX(), move.getPositionY(), move.getPositionZ()));
          }
        }
      } else if (receive) {
        synchronized (packets) {
          for (Packet<?> p : packets) {
            PacketUtil.sendPacketNoEvent(p);
          }
          packets.clear();
        }
        if (packet instanceof C03PacketPlayer && ((C03PacketPlayer) packet).isMoving()) {
          C03PacketPlayer move = (C03PacketPlayer) packet;
          synchronized (positions) {
            positions.add(new Vec3(move.getPositionX(), move.getPositionY(), move.getPositionZ()));
          }
        }
      }
    }

    if (!sent && !receive) {
      unblink();
    }
  }

  public static void unblink() {
    flushReceived();
    synchronized (packets) {
      for (Packet<?> p : packets) {
        PacketUtil.sendPacketNoEvent(p);
      }
    }
    clear();
  }

  @SuppressWarnings("unchecked")
  private static void flushReceived() {
    synchronized (packetsReceived) {
      for (Packet<?> p : packetsReceived) {
        PacketUtil.handlePacket((Packet<net.minecraft.network.play.INetHandlerPlayClient>) p);
      }
      packetsReceived.clear();
    }
  }

  public static void syncSent() {
    flushReceived();
    resetFilterState();
    cancelTimeoutTask();
  }

  public static void syncReceived() {
    synchronized (packets) {
      for (Packet<?> p : packets) {
        PacketUtil.sendPacketNoEvent(p);
      }
      packets.clear();
    }
    resetFilterState();
    cancelTimeoutTask();
  }

  public static void clear() {
    synchronized (packetsReceived) {
      packetsReceived.clear();
    }
    synchronized (packets) {
      packets.clear();
    }
    synchronized (positions) {
      positions.clear();
    }
    resetFilterState();
    cancelTimeoutTask();
  }

  private static void scheduleAutoUnblink(long timeoutMs) {
    cancelTimeoutTask();
    blinkTimeoutTimer = new Timer("BlinkUtilTimeout", false);
    blinkTimeoutTask = new TimerTask() {
      @Override
      public void run() {
        synchronized (BlinkUtil.class) {
          if (isBlinking()) {
            mc.addScheduledTask(BlinkUtil::unblink);
          }
          isTimeoutScheduled = false;
          blinkTimeoutTask = null;
        }
      }
    };
    blinkTimeoutTimer.schedule(blinkTimeoutTask, timeoutMs);
    isTimeoutScheduled = true;
  }

  private static void cancelTimeoutTask() {
    synchronized (BlinkUtil.class) {
      if (blinkTimeoutTask != null) {
        blinkTimeoutTask.cancel();
        blinkTimeoutTask = null;
      }
      if (blinkTimeoutTimer != null) {
        blinkTimeoutTimer.cancel();
        blinkTimeoutTimer = null;
      }
      isTimeoutScheduled = false;
    }
  }

  private static void resetFilterState() {
    filterMaxAllowCount = Integer.MAX_VALUE;
    currentAllowedCount = 0;
    activeFilter = null;
  }

  public static boolean isC03Moving(C03PacketPlayer packet) {
    return packet.isMoving();
  }

  public static int getPacketCount() {
    synchronized (packets) {
      return packets.size();
    }
  }
}
