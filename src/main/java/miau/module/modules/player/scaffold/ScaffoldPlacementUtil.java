package miau.module.modules.player.scaffold;

import miau.module.modules.player.Scaffold.BlockData;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;

public final class ScaffoldPlacementUtil {
  private static final Minecraft MC = Minecraft.getMinecraft();
  private static final double INSET = 0.05;
  private static final double FACE_STEP = 0.2;

  private ScaffoldPlacementUtil() {}

  public static double blockReach() {
    return MC.playerController.getBlockReachDistance();
  }

  public static MovingObjectPosition raycastPlacement(float yaw, float pitch) {
    return RotationUtil.rayCastBlock(blockReach(), yaw, pitch);
  }

  public static boolean matchesPlacement(MovingObjectPosition mop, BlockData data) {
    return mop != null
        && mop.typeOfHit == MovingObjectType.BLOCK
        && mop.getBlockPos().equals(data.blockPos)
        && mop.sideHit == data.facing;
  }

  public static MovingObjectPosition verifyPlacement(BlockData data, float yaw, float pitch) {
    MovingObjectPosition mop = raycastPlacement(yaw, pitch);
    return matchesPlacement(mop, data) ? mop : null;
  }

  public static PlacementAim resolveAim(
      BlockData data, float baseYaw, float basePitch, double[] faceSamples) {
    if (MC.thePlayer == null || data == null) return null;

    float bestYaw = Float.NaN;
    float bestPitch = Float.NaN;
    float bestCost = Float.MAX_VALUE;
    Vec3 bestHit = null;

    double[] x = faceSamples;
    double[] y = faceSamples;
    double[] z = faceSamples;
    switch (data.facing) {
      case NORTH:
        z = new double[] {0.0};
        break;
      case EAST:
        x = new double[] {1.0};
        break;
      case SOUTH:
        z = new double[] {1.0};
        break;
      case WEST:
        x = new double[] {0.0};
        break;
      case DOWN:
        y = new double[] {0.0};
        break;
      case UP:
        y = new double[] {1.0};
        break;
      default:
        break;
    }

    double reach = blockReach();
    for (double dx : x) {
      for (double dy : y) {
        for (double dz : z) {
          double relX = data.blockPos.getX() + dx - MC.thePlayer.posX;
          double relY = data.blockPos.getY() + dy - MC.thePlayer.posY - MC.thePlayer.getEyeHeight();
          double relZ = data.blockPos.getZ() + dz - MC.thePlayer.posZ;
          float[] rots = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, basePitch);
          MovingObjectPosition mop = RotationUtil.rayCastBlock(reach, rots[0], rots[1]);
          if (!matchesPlacement(mop, data)) continue;
          float cost =
              Math.abs(MathHelper.wrapAngleTo180_float(rots[0] - baseYaw))
                  + Math.abs(rots[1] - basePitch);
          if (cost < bestCost) {
            bestCost = cost;
            bestYaw = rots[0];
            bestPitch = rots[1];
            bestHit = mop.hitVec;
          }
        }
      }
    }

    if (Float.isNaN(bestYaw)) {
      return resolveAimInset(data, baseYaw, basePitch, reach);
    }
    float[] gcd = RotationUtil.flexRotation(bestYaw, bestPitch, baseYaw, basePitch);
    MovingObjectPosition verify = RotationUtil.rayCastBlock(reach, gcd[0], gcd[1]);
    if (matchesPlacement(verify, data)) {
      return new PlacementAim(gcd[0], gcd[1], verify.hitVec);
    }
    return new PlacementAim(bestYaw, bestPitch, bestHit);
  }

  private static PlacementAim resolveAimInset(
      BlockData data, float baseYaw, float basePitch, double reach) {
    EnumFacing hitFace = data.facing;
    int n = (int) Math.round(1.0 / FACE_STEP);
    float bestYaw = Float.NaN;
    float bestPitch = Float.NaN;
    float bestCost = Float.MAX_VALUE;
    Vec3 bestHit = null;

    for (int r = 0; r <= n; r++) {
      double v = Math.min(1.0, Math.max(0.0, r * FACE_STEP));
      for (int c = 0; c <= n; c++) {
        double u = Math.min(1.0, Math.max(0.0, c * FACE_STEP));
        Vec3 point = facePoint(data.blockPos, hitFace, u, v, INSET);
        double relX = point.xCoord - MC.thePlayer.posX;
        double relY = point.yCoord - MC.thePlayer.posY - MC.thePlayer.getEyeHeight();
        double relZ = point.zCoord - MC.thePlayer.posZ;
        float[] rots = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, basePitch);
        MovingObjectPosition mop = RotationUtil.rayCastBlock(reach, rots[0], rots[1]);
        if (!matchesPlacement(mop, data)) continue;
        float cost =
            Math.abs(MathHelper.wrapAngleTo180_float(rots[0] - baseYaw))
                + Math.abs(rots[1] - basePitch);
        if (cost < bestCost) {
          bestCost = cost;
          bestYaw = rots[0];
          bestPitch = rots[1];
          bestHit = mop.hitVec;
        }
      }
    }
    if (Float.isNaN(bestYaw)) return null;
    float[] gcd = RotationUtil.flexRotation(bestYaw, bestPitch, baseYaw, basePitch);
    MovingObjectPosition verify = RotationUtil.rayCastBlock(reach, gcd[0], gcd[1]);
    if (matchesPlacement(verify, data)) {
      return new PlacementAim(gcd[0], gcd[1], verify.hitVec);
    }
    return new PlacementAim(bestYaw, bestPitch, bestHit);
  }

  private static Vec3 facePoint(BlockPos pos, EnumFacing face, double u, double v, double inset) {
    switch (face) {
      case UP:
        return new Vec3(pos.getX() + u, pos.getY() + 1.0 - inset, pos.getZ() + v);
      case DOWN:
        return new Vec3(pos.getX() + u, pos.getY() + inset, pos.getZ() + v);
      case SOUTH:
        return new Vec3(pos.getX() + u, pos.getY() + v, pos.getZ() + 1.0 - inset);
      case NORTH:
        return new Vec3(pos.getX() + u, pos.getY() + v, pos.getZ() + inset);
      case EAST:
        return new Vec3(pos.getX() + 1.0 - inset, pos.getY() + v, pos.getZ() + u);
      case WEST:
        return new Vec3(pos.getX() + inset, pos.getY() + v, pos.getZ() + u);
      default:
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
  }

  public static final class PlacementAim {
    public final float yaw;
    public final float pitch;
    public final Vec3 hitVec;

    public PlacementAim(float yaw, float pitch, Vec3 hitVec) {
      this.yaw = yaw;
      this.pitch = pitch;
      this.hitVec = hitVec;
    }
  }
}
