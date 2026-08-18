package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.util.player.RotationUtil;

public class BetaRotation implements IRotationLogic {
  @Override
  public void handleInitialRotation(
      Scaffold scaffold,
      UpdateEvent event,
      float currentYaw,
      float yawDiffTo180,
      float diagonalYaw) {
    if (scaffold.yaw == -180.0F && scaffold.pitch == 0.0F) {
      scaffold.yaw = RotationUtil.quantizeAngle(event.getYaw());
      scaffold.pitch = RotationUtil.quantizeAngle(event.getPitch());
      scaffold.betaFeature.lastBetaSentYaw = event.getYaw();
      scaffold.betaFeature.lastBetaSentPitch = event.getPitch();
    }
  }

  /**
   * Full beta rotation pipeline: target computation, delta conditioning, GCD fix, noise injection.
   * Returns [placeYaw, placePitch] — result is also stored into scaffold.* fields.
   */
  public float[] handleBetaUpdate(
      Scaffold scaffold,
      UpdateEvent event,
      float yawDiffTo180,
      float diagonalYaw,
      boolean towerRotating,
      boolean willPlaceThisTick) {
    // Default implementation: just use current yaw/pitch as-is
    float targetYaw = scaffold.yaw;
    float targetPitch = scaffold.pitch;

    float[] gcd =
        RotationUtil.flexRotation(targetYaw, targetPitch, event.getYaw(), event.getPitch());
    float placeYaw = gcd[0];
    float placePitch = gcd[1];

    scaffold.placeYaw = placeYaw;
    scaffold.placePitch = placePitch;
    scaffold.betaFeature.lastBetaSentYaw = placeYaw;
    scaffold.betaFeature.lastBetaSentPitch = placePitch;

    return new float[] {placeYaw, placePitch};
  }
}
