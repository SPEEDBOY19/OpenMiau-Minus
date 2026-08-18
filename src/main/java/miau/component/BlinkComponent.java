package miau.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

public final class BlinkComponent {

  private static final Minecraft mc = Minecraft.getMinecraft();
  public static final ConcurrentLinkedQueue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
  public static boolean blinking, dispatch;
  public static ArrayList<Class<?>> exemptedPackets = new ArrayList<>();
  public static TimerUtil exemptionWatch = new TimerUtil();

  public static void setExempt(Class<?>... packets) {
    exemptedPackets = new ArrayList<>(Arrays.asList(packets));
    exemptionWatch.reset();
  }

  @EventTarget(Priority.LOWEST)
  public final void onPacket(PacketEvent event) {
    if (event.getType() != EventType.SEND) return;

    if (mc.thePlayer == null) {
      packets.clear();
      exemptedPackets.clear();
      return;
    }

    if (mc.thePlayer.isDead
        || mc.isSingleplayer()
        || !((miau.mixin.IAccessorNetHandlerPlayClient) mc.getNetHandler())
            .isDoneLoadingTerrain()) {
      packets.forEach(p -> {
        miau.module.modules.misc.Balance.onRelease(p);
        PacketUtil.sendPacketNoEvent(p);
      });
      packets.clear();
      blinking = false;
      exemptedPackets.clear();
      return;
    }

    final Packet<?> packet = event.getPacket();

    if (packet instanceof C00Handshake
        || packet instanceof C00PacketLoginStart
        || packet instanceof C00PacketServerQuery
        || packet instanceof C01PacketPing
        || packet instanceof C01PacketEncryptionResponse) {
      return;
    }

    if (blinking && !dispatch) {
      if (exemptionWatch.hasTimeElapsed(100)) {
        exemptionWatch.reset();
        exemptedPackets.clear();
      }

      if (!event.isCancelled()
          && exemptedPackets.stream().noneMatch(packetClass -> packetClass == packet.getClass())) {
        packets.add(packet);
        event.setCancelled(true);
      }
    } else if (packet instanceof C03PacketPlayer) {
      packets.forEach(p -> {
        miau.module.modules.misc.Balance.onRelease(p);
        PacketUtil.sendPacketNoEvent(p);
      });
      packets.clear();
      dispatch = false;
    }
  }

  @EventTarget(Priority.LOWEST)
  public final void onWorldLoad(LoadWorldEvent event) {
    packets.clear();
    blinking = false;
    dispatch = false;
    exemptedPackets.clear();
  }

  public static void dispatch() {
    dispatch = true;
  }
}
