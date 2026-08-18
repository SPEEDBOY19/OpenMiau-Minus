package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.util.network.PacketUtil;
import miau.util.player.RayCastUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class TeleportHit extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private EntityLivingBase targetEntity = null;
  private boolean shouldHit = false;

  public TeleportHit() {
    super("TeleportHit", false);
  }

  private boolean isSelected(EntityLivingBase entity) {
    return entity != null && entity != mc.thePlayer && !entity.isDead && entity.isEntityAlive();
  }

  private void sendPath(double tx, double ty, double tz) {
    double sx = mc.thePlayer.posX;
    double sy = mc.thePlayer.posY;
    double sz = mc.thePlayer.posZ;
    double dx = tx - sx;
    double dy = ty - sy;
    double dz = tz - sz;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    double steps = Math.max(1, Math.ceil(distance / 0.9D));
    for (int i = 1; i <= steps; i++) {
      double t = i / steps;
      PacketUtil.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(
          sx + dx * t, sy + dy * t, sz + dz * t, false));
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    MovingObjectPosition mop = RayCastUtil.rayCast(
        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 100.0, 0.2f);
    EntityLivingBase facedEntity =
        mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
            && mop.entityHit instanceof EntityLivingBase
        ? (EntityLivingBase) mop.entityHit
        : null;

    if (mc.gameSettings.keyBindAttack.isKeyDown() && this.isSelected(facedEntity)) {
      if (facedEntity.getDistanceSqToEntity(mc.thePlayer) >= 1) this.targetEntity = facedEntity;
    }

    if (this.targetEntity != null) {
      if (!this.shouldHit) {
        this.shouldHit = true;
        return;
      }

      if (mc.thePlayer.fallDistance > 0.0F) {
        Vec3 rotationVector = RayCastUtil.getVectorForRotation(0f, mc.thePlayer.rotationYaw);
        double x = mc.thePlayer.posX
            + rotationVector.xCoord * (mc.thePlayer.getDistanceToEntity(this.targetEntity) - 1.0F);
        double z = mc.thePlayer.posZ
            + rotationVector.zCoord * (mc.thePlayer.getDistanceToEntity(this.targetEntity) - 1.0F);
        double y = this.targetEntity.posY + 0.25;

        this.sendPath(x, y + 1, z);

        mc.thePlayer.swingItem();
        PacketUtil.sendPacket(new C02PacketUseEntity(this.targetEntity, C02PacketUseEntity.Action.ATTACK));
        mc.thePlayer.onCriticalHit(this.targetEntity);
        this.shouldHit = false;
        this.targetEntity = null;
      } else if (mc.thePlayer.onGround) {
        mc.thePlayer.jump();
      }
    } else {
      this.shouldHit = false;
    }
  }
}
