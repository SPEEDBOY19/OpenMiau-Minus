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
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class OMGrimSemiNoSlow extends NoSlowMode {
  private final List<Packet<?>> packetBuffer = new ArrayList<>();
  private boolean isHolding = false;
  private boolean pendingClear = false;
  private boolean waitSwapBack = false;
  private int currentSlot = 0;
  private int ticksElapsed = 0;

  public OMGrimSemiNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    reset();
  }

  @Override
  public void onDisable() {
    flushBuffer();
    reset();
  }

  private void reset() {
    packetBuffer.clear();
    isHolding = false;
    pendingClear = false;
    waitSwapBack = false;
    currentSlot = 0;
    ticksElapsed = 0;
  }

  private void flushBuffer() {
    List<Packet<?>> flush = new ArrayList<>(packetBuffer);
    packetBuffer.clear();
    for (Packet<?> p : flush) {
      PacketUtil.sendPacketNoEvent(p);
    }
  }

  private void handleGrimSemi() {
    if (waitSwapBack) {
      waitSwapBack = false;
      PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(currentSlot));
    }

    if (pendingClear) {
      pendingClear = false;
      currentSlot = mc.thePlayer.inventory.currentItem;
      int other = (currentSlot + 1) % 9;
      if (other == currentSlot) {
        other = 0;
      }
      PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(other));
      waitSwapBack = true;
    }

    boolean active = getParent().isAnyActive();

    if (!active) {
      if (isHolding) {
        isHolding = false;
        flushBuffer();
      }
      ticksElapsed = 0;
      return;
    }

    if (!isHolding) {
      isHolding = true;
      pendingClear = true;
      ticksElapsed = 0;
      return;
    }

    ticksElapsed++;
    int maxTicks = getParent().grimSemiInterval.getValue();
    if (ticksElapsed >= maxTicks) {
      ticksElapsed = 0;
      flushBuffer();
      PacketUtil.sendPacketNoEvent(
          new C08PacketPlayerBlockPlacement(
              new BlockPos(-1, -1, -1), 255, mc.thePlayer.getHeldItem(), 0.0F, 0.0F, 0.0F));
      pendingClear = true;
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      handleGrimSemi();
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.SEND) {
      return;
    }

    Packet<?> packet = event.getPacket();

    if (packet instanceof C07PacketPlayerDigging) {
      C07PacketPlayerDigging digging = (C07PacketPlayerDigging) packet;
      if (digging.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
        event.setCancelled(true);
        releaseHold();
        PacketUtil.sendPacketNoEvent(packet);
        if (waitSwapBack) {
          waitSwapBack = false;
          PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(currentSlot));
        }
        return;
      }
    }

    if (!isHolding) {
      return;
    }

    if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
      return;
    }

    packetBuffer.add(packet);
    event.setCancelled(true);
  }

  private void releaseHold() {
    if (isHolding) {
      isHolding = false;
      flushBuffer();
    }
  }

  public boolean shouldCancelSlowdown() {
    return isHolding;
  }
}