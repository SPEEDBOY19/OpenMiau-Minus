package miau.module.modules.movement.speeds;

import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.movement.Speed;
import miau.util.player.MoveUtil;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Vulcan 2.8.8 BHop speed mode.
 *
 * @author CCBlueX (original LiquidBounce)
 */
public class VulcanSpeed extends SpeedMode {

  private boolean jumped;
  private int jumpTicks;
  private int jump;

  public VulcanSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    jumped = false;
    jumpTicks = 0;
    jump = 0;
  }

  private double predictedMotion(double motion, int ticks) {
    if (ticks == 0) return motion;
    double predicted = motion;
    for (int i = 0; i < ticks; i++) {
      predicted = (predicted - 0.08) * 0.9800000190734863D;
    }
    return predicted;
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!parent.canBoost()) return;

    if (MoveUtil.getSpeedLevel() < 0.22) {
      MoveUtil.setSpeed(0.22, MoveUtil.getMoveYaw());
    }

    if (mc.thePlayer.onGround) {
      jumpTicks = 0;
      if (MoveUtil.isMoving()) {
        mc.thePlayer.jump();
        jump++;
        jumped = true;

        if (mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.moveSpeed)
            && mc.thePlayer.ticksExisted > 11) {
          MoveUtil.setSpeed(
              (.06
                      * (1
                          + (mc.thePlayer
                              .getActivePotionEffect(net.minecraft.potion.Potion.moveSpeed)
                              .getAmplifier()))
                  + 0.485),
              MoveUtil.getMoveYaw());
        } else if (mc.thePlayer.ticksExisted > 11) {
          MoveUtil.setSpeed(0.485, MoveUtil.getMoveYaw());
        } else {
          MoveUtil.setSpeed(MoveUtil.getSpeedLevel(), MoveUtil.getMoveYaw());
        }
      }
      mc.thePlayer.movementInput.jump = false;
      return;
    }

    if (!jumped) return;
    jumpTicks++;

    switch (jumpTicks) {
      case 1:
        MoveUtil.setSpeed(MoveUtil.getSpeedLevel(), MoveUtil.getMoveYaw());
        break;
      case 2:
        if (jump % 4 != 1 && !mc.thePlayer.isCollidedVertically) {
          mc.thePlayer.motionY = predictedMotion(mc.thePlayer.motionY, 2);
        }
        break;
      case 4:
        if (jump % 4 == 1 || mc.thePlayer.isCollidedVertically) {
          mc.thePlayer.motionY = predictedMotion(mc.thePlayer.motionY, 4);
        }
        break;
      case 5:
        if (jump % 4 == 1) {
          MoveUtil.setSpeed(MoveUtil.getSpeedLevel(), MoveUtil.getMoveYaw());
        }
        break;
      case 8:
        MoveUtil.setSpeed(MoveUtil.getSpeedLevel(), MoveUtil.getMoveYaw());
        break;
      case 9:
        if (!(mc.theWorld
                .getBlockState(
                    new net.minecraft.util.BlockPos(
                        mc.thePlayer.posX,
                        mc.thePlayer.posY + mc.thePlayer.motionY,
                        mc.thePlayer.posZ))
                .getBlock()
            instanceof net.minecraft.block.BlockAir)) {
          MoveUtil.setSpeed(MoveUtil.getSpeedLevel(), MoveUtil.getMoveYaw());
        }
        MoveUtil.setSpeed(MoveUtil.getSpeedLevel(), MoveUtil.getMoveYaw());
        break;
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND
        && event.getPacket() instanceof C03PacketPlayer
        && mc.thePlayer.motionY < 0) {
      ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(true);
    }
  }
}
