package miau.module.modules.movement.noslow;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class OMNewGrimNoSlow extends NoSlowMode {
  private int ticks = 0;
  private int interval = 0;

  public OMNewGrimNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    ticks = 0;
    interval = 0;
  }

  @Override
  public void onDisable() {
    ticks = 0;
    interval = 0;
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) {
      return;
    }

    ticks++;

    int maxInterval = this.getParent().grimInterval.getValue();
    if (++interval < maxInterval) {
      return;
    }
    interval = 0;

    if (this.getParent().isAnyActive()) {
      PacketUtil.sendPacket(
          new C07PacketPlayerDigging(
              C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
      PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(
          new BlockPos(-1, -1, -1), 255, mc.thePlayer.getHeldItem(), 0.0F, 0.0F, 0.0F));

      float multiplier = this.getParent().getMotionMultiplier();
      mc.thePlayer.movementInput.moveForward *= multiplier;
      mc.thePlayer.movementInput.moveStrafe *= multiplier;
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
      }
    }
  }
}