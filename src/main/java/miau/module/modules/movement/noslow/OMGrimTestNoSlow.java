package miau.module.modules.movement.noslow;

import java.util.ArrayList;
import java.util.List;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class OMGrimTestNoSlow extends NoSlowMode {
  private final List<Packet<?>> packetBuffer = new ArrayList<>();
  private boolean isHolding = false;
  private boolean fakePacket = false;
  private boolean pendingRelease = false;
  private int ticksElapsed = 0;

  public OMGrimTestNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    clearBuffer();
    isHolding = false;
    fakePacket = false;
    pendingRelease = false;
    ticksElapsed = 0;
  }

  @Override
  public void onDisable() {
    releaseHold();
    clearBuffer();
    isHolding = false;
    fakePacket = false;
    pendingRelease = false;
    ticksElapsed = 0;
  }

  private void clearBuffer() {
    packetBuffer.clear();
  }

  private void releaseHold() {
    if (isHolding) {
      isHolding = false;
      flushBuffer();
    }
  }

  private void flushBuffer() {
    for (Packet<?> p : packetBuffer) {
      PacketUtil.sendPacketNoEvent(p);
    }
    packetBuffer.clear();
  }

  private void acquireHold() {
    isHolding = true;
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      handleGrimLiving();
    }
  }

  private void handleGrimLiving() {
    if (pendingRelease) {
      pendingRelease = false;
      fakePacket = true;
      PacketUtil.sendPacketNoEvent(new C07PacketPlayerDigging(
          C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
      fakePacket = false;
    }

    if (getParent().isAnyActive() && !isHolding) {
      acquireHold();
      pendingRelease = true;
      ticksElapsed = 0;
    }

    if (isHolding) {
      ticksElapsed++;
      int maxTicks = getParent().grimTestMaxTicks.getValue();
      if (ticksElapsed >= maxTicks) {
        releaseHold();
        ticksElapsed = 0;
        if (getParent().isAnyActive()) {
          PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(
              new BlockPos(-1, -1, -1), 255, mc.thePlayer.getHeldItem(), 0.0F, 0.0F, 0.0F));
        }
      }
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      Packet<?> packet = event.getPacket();

      if (isHolding) {
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
          return;
        }

        // Check if player manually releases item while blinking
        if (!fakePacket && packet instanceof C07PacketPlayerDigging) {
          C07PacketPlayerDigging digging = (C07PacketPlayerDigging) packet;
          if (digging.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            event.setCancelled(true);
            releaseHold();
            return;
          }
        }

        // Buffer all outgoing packets
        packetBuffer.add(packet);
        event.setCancelled(true);
      }
    }
  }

  public boolean shouldCancelSlowdown() {
    return isHolding;
  }
}
