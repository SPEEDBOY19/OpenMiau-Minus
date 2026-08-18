package miau.module.modules.combat;

import com.google.common.base.Predicates;
import miau.module.Module;
import miau.module.modules.misc.AntiBot;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;

/**
 * @author ravenbS
 */
public class Piercing extends Module {

  public final ModeProperty sortMode =
      new ModeProperty("Sort-mode", 0, new String[] {"Hurt time", "Health"});
  public final BooleanProperty ignoreBlocks = new BooleanProperty("Ignore-blocks", false);
  public final BooleanProperty ignoreTeammates = new BooleanProperty("Ignore-teammates", true);
  public final BooleanProperty ignoreNonPlayer = new BooleanProperty("Ignore-non-players", true);
  public final BooleanProperty weaponOnly = new BooleanProperty("Weapon-only", false);
  public final BooleanProperty insideHitboxOnly = new BooleanProperty("Inside-hitbox-only", false);

  private static final Minecraft mc = Minecraft.getMinecraft();

  public Piercing() {
    super("Piercing", false);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {sortModes[(int) sortMode.getValue()]};
  }

  private static final String[] sortModes = new String[] {"Hurt time", "Health"};

  public boolean shouldOverrideMouseOver() {
    if (!this.isEnabled()) return false;
    if (mc == null || mc.thePlayer == null || mc.theWorld == null) return false;
    if (this.weaponOnly.getValue()
        && (mc.thePlayer.getHeldItem() == null
            || !(mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword)
                && !(mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemAxe))) {
      return false;
    }
    return ignoreBlocks.getValue()
        || mc.objectMouseOver == null
        || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
  }

  public void modifyMouseOver(float partialTicks) {
    if (!shouldOverrideMouseOver()) return;
    keystrokesmod$modifyMouseOverVanillaLook(partialTicks);
  }

  private void keystrokesmod$modifyMouseOverVanillaLook(final float partialTicks) {
    final Entity viewEntity = mc.getRenderViewEntity();
    if (viewEntity == null || mc.theWorld == null) return;

    double reach = mc.playerController.getBlockReachDistance();
    final Vec3 eyes = viewEntity.getPositionEyes(partialTicks);
    if (mc.playerController.extendedReach()) {
      reach = 6.0;
    }
    Vec3 look;
    if (miau.util.player.RotationUtil.customRots) {
      look =
          ((miau.mixin.IAccessorEntity) viewEntity)
              .callGetVectorForRotation(
                  miau.util.player.RotationUtil.serverPitch,
                  miau.util.player.RotationUtil.serverYaw);
    } else {
      look = viewEntity.getLook(partialTicks);
    }
    final Vec3 rayEnd =
        eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);

    Entity best = null;
    Vec3 bestHit = null;
    double bestDist = Double.MAX_VALUE;
    boolean bestLiving = false;
    int bestHurt = Integer.MAX_VALUE;
    float bestHp = Float.POSITIVE_INFINITY;
    final int modeSel = (int) this.sortMode.getValue();

    for (final Entity e :
        mc.theWorld.getEntitiesInAABBexcluding(
            viewEntity,
            viewEntity
                .getEntityBoundingBox()
                .addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach)
                .expand(1.0, 1.0, 1.0),
            Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBeCollidedWith))) {
      if ((this.ignoreNonPlayer.getValue() && !(e instanceof EntityPlayer))
          || (this.ignoreTeammates.getValue()
              && e instanceof EntityPlayer
              && TeamUtil.isSameTeam((EntityPlayer) e))
          || (e instanceof EntityLivingBase && AntiBot.isBot((EntityLivingBase) e))
          || (e instanceof EntityPlayer && TeamUtil.isFriend((EntityPlayer) e))) {
        continue;
      }

      final float cb = e.getCollisionBorderSize();
      final AxisAlignedBB bb = e.getEntityBoundingBox().expand(cb, cb, cb);
      final MovingObjectPosition hit = bb.calculateIntercept(eyes, rayEnd);
      final boolean inside = bb.isVecInside(eyes);

      if (!inside && hit == null) continue;
      double dist = inside ? 0.0 : eyes.distanceTo(hit.hitVec);
      if (!mc.playerController.extendedReach() && dist > 3.0) continue;
      if (dist > reach) continue;
      if (dist >= bestDist) continue;
      if (this.insideHitboxOnly.getValue() && dist > 0.10000000149011612) continue;

      if (e == viewEntity.ridingEntity && !viewEntity.canRiderInteract() && best != null) continue;

      boolean living = e instanceof EntityLivingBase;
      int hurt = living ? ((EntityLivingBase) e).hurtTime : Integer.MAX_VALUE;
      float hp = living ? ((EntityLivingBase) e).getHealth() : Float.POSITIVE_INFINITY;

      boolean take = false;
      if (best == null) {
        take = true;
      } else if (living && !bestLiving) {
        take = true;
      } else if (living == bestLiving) {
        if (!living) {
          take = dist < bestDist;
        } else if (modeSel == 0) {
          if (hurt < bestHurt) {
            take = true;
          } else if (hurt == bestHurt && dist < bestDist) {
            take = true;
          }
        } else {
          if (hp < bestHp) {
            take = true;
          } else if (hp == bestHp && dist < bestDist) {
            take = true;
          }
        }
      }

      if (take) {
        best = e;
        bestHit = inside ? (hit == null ? eyes : hit.hitVec) : hit.hitVec;
        bestDist = dist;
        bestLiving = living;
        bestHurt = hurt;
        bestHp = hp;
      }
    }

    if (best != null && reach > 3.0 && bestDist > 3.0 && !mc.playerController.extendedReach()) {
      mc.objectMouseOver =
          new MovingObjectPosition(
              MovingObjectPosition.MovingObjectType.MISS, bestHit, null, new BlockPos(bestHit));
      return;
    }

    if (best != null) {
      mc.objectMouseOver = new MovingObjectPosition(best, bestHit);
      if (best instanceof EntityLivingBase || best instanceof EntityItemFrame) {
        mc.pointedEntity = best;
      }
    }
  }
}