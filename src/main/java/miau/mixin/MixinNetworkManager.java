package miau.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.concurrent.Future;
import miau.Miau;
import miau.event.EventManager;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(
    value = {NetworkManager.class},
    priority = 9999)
public abstract class MixinNetworkManager {
  @Inject(
      method = {"channelRead0*"},
      at = {@At("HEAD")},
      cancellable = true)
  @SuppressWarnings("unchecked")
  private void channelRead0(
      ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo callbackInfo) {
    if (net.minecraft.client.Minecraft.getMinecraft().thePlayer == null
        || net.minecraft.client.Minecraft.getMinecraft().theWorld == null) {
      return;
    }
    if (!packet.getClass().getName().startsWith("net.minecraft.network.play.client")) {
      if (Miau.delayManager != null
          && Miau.delayManager.shouldDelay((Packet<INetHandlerPlayClient>) packet)) {
        callbackInfo.cancel();
      } else {
        PacketEvent event = new PacketEvent(EventType.RECEIVE, packet);
        EventManager.call(event);
        if (event.isCancelled()) {
          callbackInfo.cancel();
        }
      }
    }
  }

  @Inject(
      method = {"sendPacket(Lnet/minecraft/network/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true)
  private void sendPacket(Packet<?> packet, CallbackInfo callbackInfo) {
    if (miau.util.network.PacketUtil.sendingNoEvent) {
      return;
    }
    if (!packet.getClass().getName().startsWith("net.minecraft.network.play.server")) {
      PacketEvent event = new PacketEvent(EventType.SEND, packet);
      EventManager.call(event);
      if (event.isCancelled()) {
        callbackInfo.cancel();
      } else if (Miau.playerStateManager != null
          && Miau.blinkManager != null
          && Miau.lagManager != null) {
        if (!Miau.lagManager.isFlushing()) {
          Miau.playerStateManager.handlePacket(packet);
          if (Miau.blinkManager.isBlinking()) {
            if (Miau.blinkManager.offerPacket(packet)) {
              callbackInfo.cancel();
              return;
            }
          }
          if (Miau.lagManager.handlePacket(packet)) {
            callbackInfo.cancel();
          }
        }
      }
    }
  }

  @Inject(
      method = {
        "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V"
      },
      at = {@At("HEAD")},
      cancellable = true)
  private void sendPacket2(
      Packet<?> packet,
      GenericFutureListener<? extends Future<? super Void>> genericFutureListener,
      GenericFutureListener<? extends Future<? super Void>>[] arr,
      CallbackInfo callbackInfo) {
    if (miau.util.network.PacketUtil.sendingNoEvent) {
      return;
    }
    if (!packet.getClass().getName().startsWith("net.minecraft.network.play.server")) {
      PacketEvent event = new PacketEvent(EventType.SEND, packet);
      EventManager.call(event);
      if (event.isCancelled()) {
        callbackInfo.cancel();
        return;
      }
      if (Miau.playerStateManager != null && Miau.blinkManager != null && Miau.lagManager != null) {
        if (!Miau.lagManager.isFlushing()) {
          Miau.playerStateManager.handlePacket(packet);
          if (Miau.blinkManager.isBlinking()) {
            if (Miau.blinkManager.offerPacket(packet)) {
              callbackInfo.cancel();
              return;
            }
          }
          if (Miau.lagManager.handlePacket(packet)) {
            callbackInfo.cancel();
          }
        }
      }
    }
  }
}
