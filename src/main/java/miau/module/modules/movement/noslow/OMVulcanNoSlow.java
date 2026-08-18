package miau.module.modules.movement.noslow;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class OMVulcanNoSlow extends NoSlowMode {
  private int interval = 0;

  public OMVulcanNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    interval = 0;
  }

  @Override
  public void onDisable() {
    interval = 0;
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) {
      return;
    }

    int maxInterval = this.getParent().vulcanInterval.getValue();
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
    if (event.getType() == EventType.SEND
        && event.getPacket() instanceof C07PacketPlayerDigging) {
      C07PacketPlayerDigging digging = (C07PacketPlayerDigging) event.getPacket();
      if (digging.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
        event.setCancelled(true);
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(
            new BlockPos(-1, -1, -1), 255, mc.thePlayer.getHeldItem(), 0.0F, 0.0F, 0.0F));
      }
    }
  }
}