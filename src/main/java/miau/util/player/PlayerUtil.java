package miau.util.player;

import miau.util.client.KeyBindUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.ForgeHooks;

public class PlayerUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static boolean isJumping() {
    return mc.currentScreen == null
        && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode());
  }

  public static boolean isSneaking() {
    return mc.currentScreen == null
        && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
  }

  public static boolean isMovingLeft() {
    return mc.currentScreen == null
        && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode());
  }

  public static boolean isMovingRight() {
    return mc.currentScreen == null
        && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode());
  }

  public static boolean isAttacking() {
    return mc.currentScreen == null
        && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
  }

  public static boolean isUsingItem() {
    return mc.currentScreen == null
        && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode());
  }

  public static boolean canFly(float fallThreshold) {
    if (!mc.thePlayer.capabilities.allowFlying && !mc.thePlayer.capabilities.disableDamage) {
      PotionEffect jumpEffect = mc.thePlayer.getActivePotionEffect(Potion.jump);
      float jumpBoost = jumpEffect != null ? (float) (jumpEffect.getAmplifier() + 1) : 0.0F;
      float fallDistance = mc.thePlayer.fallDistance;
      if (mc.thePlayer.motionY < -0.67 || !isAirBelow()) {
        fallDistance -= (float) mc.thePlayer.motionY;
      }
      return MathHelper.ceiling_float_int(fallDistance - fallThreshold - jumpBoost) > 0;
    } else {
      return false;
    }
  }

  public static boolean canFly(int checkHeight) {
    if (!mc.thePlayer.capabilities.allowFlying && !mc.thePlayer.capabilities.disableDamage) {
      int playerY = MathHelper.floor_double(mc.thePlayer.posY);
      for (int offset = 0; offset <= checkHeight; ++offset) {
        int currentY = playerY - offset;
        if (currentY < 0) {
          break;
        }
        Block block =
            mc.theWorld
                .getBlockState(new BlockPos(mc.thePlayer.posX, currentY, mc.thePlayer.posZ))
                .getBlock();
        if (!(block instanceof BlockAir)) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  public static boolean isInWater() {
    return checkInWater(mc.thePlayer.getEntityBoundingBox().expand(-1.0E-6, 0.0, -1.0E-6));
  }

  public static boolean checkInWater(AxisAlignedBB boundingBox) {
    if (!mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()) {
      int minY = MathHelper.floor_double(boundingBox.minY);
      if (minY < 0) {
        return true;
      } else {
        int minX = MathHelper.floor_double(boundingBox.minX);
        int maxX = MathHelper.floor_double(boundingBox.maxX + 1.0);
        int minZ = MathHelper.floor_double(boundingBox.minZ);
        int maxZ = MathHelper.floor_double(boundingBox.maxZ + 1.0);
        for (int x = minX; x < maxX; ++x) {
          for (int z = minZ; z < maxZ; ++z) {
            for (int y = minY; y >= 0; --y) {
              if (!BlockUtil.isReplaceable(new BlockPos(x, y, z))) {
                return false;
              }
            }
          }
        }
        return true;
      }
    } else {
      return false;
    }
  }

  public static boolean canMove(double x, double z) {
    return canMove(x, z, -1.0);
  }

  public static boolean canMove(double x, double z, double y) {
    AxisAlignedBB boundingBox = mc.thePlayer.getEntityBoundingBox().offset(x, y, z);
    return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, boundingBox).isEmpty();
  }

  public static boolean isAirBelow() {
    AxisAlignedBB axisAlignedBB =
        PlayerUtil.mc.thePlayer.getEntityBoundingBox().offset(0.0, -1.0, 0.0);
    return !PlayerUtil.mc
        .theWorld
        .getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, axisAlignedBB)
        .isEmpty();
  }

  public static boolean isAirAbove() {
    AxisAlignedBB axisAlignedBB =
        PlayerUtil.mc.thePlayer.getEntityBoundingBox().offset(0.0, 1.0, 0.0);
    return !PlayerUtil.mc
        .theWorld
        .getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, axisAlignedBB)
        .isEmpty();
  }

  public static boolean canReach(BlockPos blockPos, double reach) {
    return PlayerUtil.isBlockWithinReach(
        blockPos,
        PlayerUtil.mc.thePlayer.posX,
        PlayerUtil.mc.thePlayer.posY + (double) PlayerUtil.mc.thePlayer.getEyeHeight(),
        PlayerUtil.mc.thePlayer.posZ,
        reach);
  }

  public static boolean isBlockWithinReach(
      BlockPos blockPos, double x, double y, double z, double reach) {
    return blockPos.distanceSqToCenter(x, y, z) < Math.pow(reach, 2.0);
  }

  /**
   * Calculates the exact distance from the player's eyes to the entity's bounding box using AABB
   * calculateIntercept. More accurate than distanceToEntity for hit validation. Ported from Rise 6.
   */
  public static double calculatePerfectRangeToEntity(Entity entity) {
    double range = 1000;
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    float[] rotations = RotationUtil.calculate(entity);
    Vec3 rotationVector = RayCastUtil.getVectorForRotation(rotations[1], rotations[0]);
    AxisAlignedBB bb = entity.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
    MovingObjectPosition mop =
        bb.calculateIntercept(
            eyes,
            eyes.addVector(
                rotationVector.xCoord * range,
                rotationVector.yCoord * range,
                rotationVector.zCoord * range));
    if (mop != null) {
      return mop.hitVec.distanceTo(eyes);
    }
    return Double.MAX_VALUE;
  }

  /** Returns the Block at the given BlockPos. */
  public static Block getBlock(BlockPos pos) {
    return mc.theWorld.getBlockState(pos).getBlock();
  }

  /** Returns the Block at the given coordinates. */
  public static Block block(final double x, final double y, final double z) {
    return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
  }

  /**
   * Returns the Block at the given offset relative to the player's position. Ported from Rise 6.
   */
  public static Block blockRelativeToPlayer(float offsetX, float offsetY, float offsetZ) {
    return mc.theWorld
        .getBlockState(
            new BlockPos(
                mc.thePlayer.posX + (double) offsetX,
                mc.thePlayer.posY + (double) offsetY,
                mc.thePlayer.posZ + (double) offsetZ))
        .getBlock();
  }

  /** Returns the Block at the given offset relative to the player's position (double overload). */
  public static Block blockRelativeToPlayer(
      final double offsetX, final double offsetY, final double offsetZ) {
    return block(
        mc.thePlayer.posX + offsetX, mc.thePlayer.posY + offsetY, mc.thePlayer.posZ + offsetZ);
  }

  public static boolean isBlockUnder(final double height) {
    return isBlockUnder(height, true);
  }

  public static boolean isBlockUnder(final double height, final boolean boundingBox) {
    if (boundingBox) {
      final AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox().offset(0, -height, 0);
      return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, bb).isEmpty();
    } else {
      for (int offset = 0; offset < height; offset++) {
        if (blockRelativeToPlayer(0, -offset, 0).isFullBlock()) {
          return true;
        }
      }
      return false;
    }
  }

  /** Attacks the given entity, applying enchantment effects and Forge hooks. Ported from Rise 6. */
  public static void attackEntity(Entity target) {
    if (ForgeHooks.onPlayerAttackTarget(mc.thePlayer, target)) {
      if (target.canAttackWithItem() && !target.hitByEntity(mc.thePlayer)) {
        float baseDamage =
            (float)
                mc.thePlayer
                    .getEntityAttribute(SharedMonsterAttributes.attackDamage)
                    .getAttributeValue();
        float enchantmentBonus =
            EnchantmentHelper.getModifierForCreature(
                mc.thePlayer.getHeldItem(),
                target instanceof EntityLivingBase
                    ? ((EntityLivingBase) target).getCreatureAttribute()
                    : EnumCreatureAttribute.UNDEFINED);
        int knockbackLevel = EnchantmentHelper.getKnockbackModifier(mc.thePlayer);
        if (mc.thePlayer.isSprinting()) {
          ++knockbackLevel;
        }
        mc.thePlayer.attackTargetEntityWithCurrentItem(target);
      }
    }
  }
}
