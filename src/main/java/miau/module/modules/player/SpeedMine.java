package miau.module.modules.player;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.player.PlayerUtil;
import miau.util.world.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

public class SpeedMine extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final IAccessorPlayerControllerMP accessor =
      (IAccessorPlayerControllerMP) mc.playerController;
  public final ModeProperty mode =
      new ModeProperty("Mode", 0, new String[] {"Percentage", "Ticks"});
  public final PercentProperty speed = new PercentProperty("speed", 50);
  public final IntProperty ticks = new IntProperty("ticks", 1, 1, 100);
  public final BooleanProperty ignoringMiningFatigue =
      new BooleanProperty("ignore-mining-fatigue", false);
  public final BooleanProperty equalAirGroundDig =
      new BooleanProperty("equal-air-ground-dig", true);

  public SpeedMine() {
    super("SpeedMine", false);
  }

  @EventTarget
  public void onPreUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;

    if (this.ignoringMiningFatigue.getValue()) {
      mc.thePlayer.removePotionEffect(Potion.digSlowdown.getId());
    }

    this.accessor.setBlockHitDelay(0);

    double percentageFaster = 0;

    switch (this.mode.getValue()) {
      case 0: // Percentage
        percentageFaster = this.speed.getValue().doubleValue() / 100.0;

        if (!mc.thePlayer.onGround
            && mc.thePlayer.ticksExisted % 5 == 0
            && this.equalAirGroundDig.getValue()) {
          this.accessor.setCurBlockDamageMP(this.accessor.getCurBlockDamageMP() / 5.0F);
          percentageFaster = 0.8;
        }

        if (PlayerUtil.blockRelativeToPlayer(0.0F, (float) mc.thePlayer.motionY, 0.0F) != Blocks.air
            && !mc.thePlayer.onGround
            && this.equalAirGroundDig.getValue()) {
          this.accessor.setCurBlockDamageMP(this.accessor.getCurBlockDamageMP() * 5.0F);
          percentageFaster -= 0.8;
        }
        break;

      case 1: // Ticks
        if (mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
          BlockPos blockPos = mc.objectMouseOver.getBlockPos();
          float blockHardness =
              BlockUtil.getBlock(blockPos)
                  .getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, blockPos);
          percentageFaster = blockHardness * (float) this.ticks.getValue().intValue();
        }

        if (!mc.thePlayer.onGround
            && mc.thePlayer.ticksExisted % 5 == 0
            && this.equalAirGroundDig.getValue()) {
          this.accessor.setCurBlockDamageMP(this.accessor.getCurBlockDamageMP() / 5.0F);
          percentageFaster = 0.81;
        }

        if (PlayerUtil.blockRelativeToPlayer(0.0F, (float) mc.thePlayer.motionY, 0.0F) != Blocks.air
            && !mc.thePlayer.onGround
            && this.equalAirGroundDig.getValue()) {
          this.accessor.setCurBlockDamageMP(this.accessor.getCurBlockDamageMP() * 5.0F);
          percentageFaster -= 0.81;
        }
        break;
    }

    float curDamage = this.accessor.getCurBlockDamageMP();
    if (curDamage > 1.0F - percentageFaster && curDamage < 0.99F) {
      this.accessor.setCurBlockDamageMP(0.99F);
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}
