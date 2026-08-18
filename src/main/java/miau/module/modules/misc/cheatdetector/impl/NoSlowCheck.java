package miau.module.modules.misc.cheatdetector.impl;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public class NoSlowCheck extends Check {
  private int noSlowTicks = 0;
  private double lastPosX;
  private double lastPosZ;

  @Override
  public String getName() {
    return "No slow";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    double deltaX = player.posX - this.lastPosX;
    double deltaZ = player.posZ - this.lastPosZ;
    double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

    if (player.isSprinting() && player.isUsingItem() && !player.isRiding()) {
      double baseThreshold = 0.05D;

      PotionEffect speedEffect = player.getActivePotionEffect(Potion.moveSpeed);
      if (speedEffect != null) {
        int amplifier = speedEffect.getAmplifier();
        baseThreshold *= 1.0D + 0.2D * (amplifier + 1);
      }

      if (speed > baseThreshold) {
        this.noSlowTicks++;
      } else {
        this.noSlowTicks = 0;
      }
    } else {
      this.noSlowTicks = 0;
    }

    if (this.noSlowTicks > 20) {
      flag(player, "speed: " + String.format("%.2f", speed) + ", ticks: " + this.noSlowTicks);
      this.noSlowTicks = 0;
    }

    this.lastPosX = player.posX;
    this.lastPosZ = player.posZ;
  }
}
