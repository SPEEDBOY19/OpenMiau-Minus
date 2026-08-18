package miau.module.modules.combat.criticals;

import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Criticals;
import miau.module.modules.movement.Speed;
import miau.util.player.MoveUtil;

public class JumpCriticals extends CriticalsMode {
  public JumpCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onAttack(AttackEvent event) {
    if (mc.thePlayer.isInWater()) {
      return;
    }

    if (MoveUtil.isMoving()
        && mc.thePlayer.onGround
        && !miau.Miau.moduleManager.modules.get(Speed.class).isEnabled()
        && !mc.gameSettings.keyBindJump.isKeyDown()) {
      mc.thePlayer.jump();
    }
  }
}
