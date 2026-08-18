package miau.module.modules.combat.killaura.target;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;

public class AttackData {
  private final EntityLivingBase entity;
  private final AxisAlignedBB box;
  private final double x;
  private final double y;
  private final double z;

  public AttackData(EntityLivingBase entityLivingBase) {
    this.entity = entityLivingBase;
    double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
    this.box =
        entityLivingBase
            .getEntityBoundingBox()
            .expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
    this.x = entityLivingBase.posX;
    this.y = entityLivingBase.posY;
    this.z = entityLivingBase.posZ;
  }

  public EntityLivingBase getEntity() {
    return this.entity;
  }

  public AxisAlignedBB getBox() {
    return this.box;
  }

  public double getX() {
    return this.x;
  }

  public double getY() {
    return this.y;
  }

  public double getZ() {
    return this.z;
  }
}
