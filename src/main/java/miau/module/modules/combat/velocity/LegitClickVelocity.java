package miau.module.modules.combat.velocity;

import miau.Miau;
import miau.event.impl.TickEvent;
import miau.module.modules.combat.KillAura;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import miau.util.network.PacketUtil;

public class LegitClickVelocity extends VelocityMode {
  public final BooleanProperty ignoreBlocking = new BooleanProperty("ignore-blocking", true);
  public final IntProperty durationHurtTime = new IntProperty("duration-hurt-time", 4, 1, 10);
  public final BooleanProperty whenFacingEnemyOnly = new BooleanProperty("facing-enemy-only", false);
  public final IntProperty clickRange = new IntProperty("click-range", 3, 1, 6);
  public final ModeProperty swingMode =
      new ModeProperty("swing-mode", 0, new String[] {"Normal", "Packet"});
  public final IntProperty clicksMin = new IntProperty("clicks-min", 1, 1, 5);
  public final IntProperty clicksMax = new IntProperty("clicks-max", 3, 1, 10);
  public final IntProperty clicksInterval = new IntProperty("clicks-interval", 1, 1, 100);

  private int attackStartHurtTime = 0;
  private int clicksTick = 0;

  public LegitClickVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    attackStartHurtTime = 0;
    clicksTick = 0;
  }

  @Override
  public void onTick(TickEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null || Velocity.mc.theWorld == null) return;
    if (player.hurtTime == 0) {
      attackStartHurtTime = 0;
      clicksTick = 0;
      return;
    }
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (ignoreBlocking.getValue() && (player.isBlocking() || (killAura != null && killAura.shouldAutoBlock()))) {
      return;
    }
    if (attackStartHurtTime == 0 && player.hurtTime > 0) {
      attackStartHurtTime = player.hurtTime;
    }
    if (attackStartHurtTime - player.hurtTime >= durationHurtTime.getValue()) {
      return;
    }

    Entity entity = Velocity.mc.objectMouseOver != null
        ? Velocity.mc.objectMouseOver.entityHit
        : null;
    if (entity == null) {
      if (whenFacingEnemyOnly.getValue()) {
        return;
      }
      entity = VelocityUtil.getNearestEntityInRange(clickRange.getValue());
    }
    if (entity == null) return;

    clicksTick++;
    if (clicksTick % clicksInterval.getValue() != 0) return;

    int totalClicks = VelocityUtil.randomInt(clicksMin.getValue(), clicksMax.getValue() + 1);
    for (int i = 0; i < totalClicks; i++) {
      if (swingMode.getValue() == 0) {
        player.swingItem();
      } else {
        PacketUtil.sendPacket(new C0APacketAnimation());
      }
      player.attackTargetEntityWithCurrentItem(entity);
    }
  }

  @Override
  public void onDisable() {
    attackStartHurtTime = 0;
    clicksTick = 0;
  }
}