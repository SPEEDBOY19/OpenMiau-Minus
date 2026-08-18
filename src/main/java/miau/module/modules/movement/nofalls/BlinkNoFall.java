package miau.module.modules.movement.nofalls;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.movement.NoFall;
import miau.util.client.ChatUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

public class BlinkNoFall extends NoFallMode {
  private boolean lastOnGround = false;

  public BlinkNoFall(String name, NoFall parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
      C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
      boolean allowed =
          !mc.thePlayer.isOnLadder()
              && !mc.thePlayer.capabilities.allowFlying
              && mc.thePlayer.hurtTime == 0;
      if (Miau.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
        if (this.lastOnGround
            && !packet.isOnGround()
            && allowed
            && PlayerUtil.canFly(parent.distance.getValue().intValue())
            && mc.thePlayer.motionY < 0.0) {
          Miau.blinkManager.setBlinkState(false, Miau.blinkManager.getBlinkingModule());
          Miau.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
        }
      } else if (!allowed) {
        Miau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        ChatUtil.display("%s%s: &cFailed player check!&r", parent.getName());
      } else if (PlayerUtil.checkInWater(
          mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
        Miau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        ChatUtil.display("%s%s: &cFailed void check!&r", parent.getName());
      } else if (packet.isOnGround()) {
        for (Packet<?> blinkedPacket : Miau.blinkManager.blinkedPackets) {
          if (blinkedPacket instanceof C03PacketPlayer) {
            ((IAccessorC03PacketPlayer) blinkedPacket).setOnGround(true);
          }
        }
        Miau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        parent.packetDelayTimer.reset();
      }
      this.lastOnGround = packet.isOnGround() && allowed && parent.canTrigger();
    }
  }

  @Override
  public void onDisable() {
    this.lastOnGround = false;
    Miau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
  }
}
