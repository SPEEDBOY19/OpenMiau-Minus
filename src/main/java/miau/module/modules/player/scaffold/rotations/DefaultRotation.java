package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.util.player.RotationUtil;

public class DefaultRotation implements IRotationLogic {
  @Override
  public void handleInitialRotation(
      Scaffold scaffold,
      UpdateEvent event,
      float currentYaw,
      float yawDiffTo180,
      float diagonalYaw) {
    if (scaffold.yaw == -180.0F && scaffold.pitch == 0.0F) {
      scaffold.yaw = RotationUtil.quantizeAngle(diagonalYaw);
      scaffold.pitch = RotationUtil.quantizeAngle(85.0F);
    } else {
      scaffold.yaw = RotationUtil.quantizeAngle(diagonalYaw);
    }
  }
}
