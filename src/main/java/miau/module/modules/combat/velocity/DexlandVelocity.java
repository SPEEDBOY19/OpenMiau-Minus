package miau.module.modules.combat.velocity;

import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Velocity;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import net.minecraft.entity.player.EntityPlayer;

public class DexlandVelocity extends VelocityMode {
  public final IntProperty times = new IntProperty("times", 2, 1, 10);
  public final FloatProperty hReduce = new FloatProperty("h-reduce", 0.42f, 0f, 1f);

  private int count = 0;
  private long lastAttackTime = 0;

  public DexlandVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    count = 0;
    lastAttackTime = 0;
  }

  @Override
  public void onAttack(AttackEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime > 0
        && ++count % times.getValue() == 0
        && System.currentTimeMillis() - lastAttackTime <= 8000) {
      VelocityUtil.reduceXZ(hReduce.getValue());
    }
    lastAttackTime = System.currentTimeMillis();
  }

  @Override
  public void onDisable() {
    count = 0;
    lastAttackTime = 0;
  }
}