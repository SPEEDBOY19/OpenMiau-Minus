package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.util.player.RotationUtil;

public class BackwardsRotation implements IRotationLogic {
  @Override
  public void handleInitialRotation(
      Scaffold scaffold,
      UpdateEvent event,
      float currentYaw,
      float yawDiffTo180,
      float diagonalYaw) {
    float seedPitch = scaffold.pitch == 0.0F ? 85.0F : scaffold.pitch;
    float[] gcd =
        RotationUtil.flexRotation(yawDiffTo180, seedPitch, event.getYaw(), event.getPitch());
    scaffold.yaw = gcd[0];
    scaffold.pitch = gcd[1];
    scaffold.bridgeYaw = gcd[0];
  }
}
