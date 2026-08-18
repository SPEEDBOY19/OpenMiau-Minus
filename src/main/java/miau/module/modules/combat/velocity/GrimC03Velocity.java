package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorMinecraft;
import miau.module.modules.combat.Velocity;
import miau.util.network.PacketUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class GrimC03Velocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;
  private int timerTicks = 0;

  public GrimC03Velocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == player.getEntityId()) {
        if (VelocityUtil.isMoving()) {
          hasReceivedVelocity = true;
          event.setCancelled(true);
        }
      }
    }
  }

  @Override
  public void onTick(TickEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    float speed = 0.8f + (0.2f * (20 - timerTicks) / 20);
    VelocityUtil.changeTimer(Math.min(speed, 1f));
    if (timerTicks > 0) {
      timerTicks--;
    } else if (((IAccessorMinecraft) mc).getTimer().timerSpeed <= 1) {
      VelocityUtil.changeTimer(1f);
    }
    if (hasReceivedVelocity) {
      BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
      if (checkAir(pos)) {
        hasReceivedVelocity = false;
      }
    }
  }

  private boolean checkAir(BlockPos blockPos) {
    if (mc.theWorld == null) return false;
    if (!mc.theWorld.isAirBlock(blockPos)) return false;
    timerTicks = 20;
    PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
    PacketUtil.sendPacketNoEvent(
        new C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockPos, EnumFacing.DOWN));
    mc.theWorld.setBlockToAir(blockPos);
    return true;
  }

  @Override
  public void onDisable() {
    hasReceivedVelocity = false;
    timerTicks = 0;
    VelocityUtil.changeTimer(1f);
  }
}