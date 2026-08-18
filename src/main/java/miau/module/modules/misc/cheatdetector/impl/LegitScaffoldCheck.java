package miau.module.modules.misc.cheatdetector.impl;

import java.util.ArrayList;
import java.util.List;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;

public class LegitScaffoldCheck extends Check {
  private long lastCrouchStart = 0;
  private long lastCrouchEnd = 0;
  private boolean wasSneaking = false;
  private long lastSwingTick = Long.MIN_VALUE;
  private final List<Integer> crouchDurations = new ArrayList<>();
  private long lastFlagTick = 0;

  @Override
  public String getName() {
    return "Legit scaffold";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    long tick = mc.theWorld.getTotalWorldTime();
    trackCrouch(tick, player.isSneaking());
    trackSwing(tick, player.swingProgressInt);

    if (isScaffold(player)) {
      evaluate(player, tick);
    }
  }

  private void trackCrouch(long tick, boolean currSneak) {
    if (currSneak && !wasSneaking) {
      lastCrouchStart = tick;
    } else if (!currSneak && wasSneaking) {
      int duration = (int) (tick - lastCrouchStart);
      lastCrouchEnd = tick;
      crouchDurations.add(0, duration);
      if (crouchDurations.size() > 5) crouchDurations.remove(5);
    }
    wasSneaking = currSneak;
  }

  private void trackSwing(long tick, int swingProgressInt) {
    if (swingProgressInt == 1) {
      lastSwingTick = tick;
    }
  }

  private boolean isScaffold(EntityPlayer player) {
    return (player.rotationPitch >= 60.0F
        && player.onGround
        && player.getHeldItem() != null
        && player.getHeldItem().getItem() instanceof ItemBlock);
  }

  private void evaluate(EntityPlayer player, long tick) {
    if (lastCrouchStart == 0 || lastCrouchEnd == 0) {
      return;
    }

    int crouchDuration = (int) (lastCrouchEnd - lastCrouchStart);
    boolean quickCrouch = (crouchDuration >= 1 && crouchDuration <= 2);

    boolean swingTiming =
        (lastSwingTick >= lastCrouchEnd
            && lastSwingTick <= lastCrouchEnd + 3L
            && tick - lastSwingTick <= 10L);

    boolean consistent =
        (crouchDurations.size() >= 3
            && crouchDurations.get(0) <= 3
            && crouchDurations.get(1) <= 3
            && crouchDurations.get(2) <= 3);

    if (quickCrouch && swingTiming && consistent) {
      if (tick - lastFlagTick >= 60L) {
        flag(player, "crouch: " + crouchDuration + "t");
        lastFlagTick = tick;
      }
    }
  }
}
