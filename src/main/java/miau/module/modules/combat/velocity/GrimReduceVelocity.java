package miau.module.modules.combat.velocity;

import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.util.network.PacketUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0APacketAnimation;

public class GrimReduceVelocity extends VelocityMode {

  public GrimReduceVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress
          || mc.thePlayer.ticksExisted <= 20) {
        return;
      }

      EntityLivingBase target = null;
      for (Entity entity : mc.theWorld.loadedEntityList) {
        if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
          if (mc.thePlayer.getDistanceToEntity(entity) <= 7.0f) {
            target = (EntityLivingBase) entity;
            break;
          }
        }
      }

      if (target == null) return;

      if (mc.thePlayer.hurtTime > 0) {
        PacketUtil.sendPacketNoEvent(new C0APacketAnimation());
        mc.playerController.attackEntity(mc.thePlayer, target);
      }
    }
  }
}
