package miau.module.modules.combat.criticals;

import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Criticals;

public class VanillaCriticals extends CriticalsMode {
  public VanillaCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onAttack(AttackEvent event) {
    if (mc.thePlayer.isInWater()) {
      return;
    }
    mc.thePlayer.onCriticalHit(event.getTarget());
  }
}
