package miau.module.modules.combat.killaura.rotation;

import miau.component.RotationComponent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PolarRotation extends RotationMode {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private final Random random = new Random();

  public PolarRotation(KillAura killAura) {
    super(killAura, "POLAR");
  }

  @Override
  public float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event) {
    RotationComponent.setActive(true, this.killAura.moveFix.getValue());

    EntityLivingBase target = killAura.getTarget();
    if (target == null) {
      RotationComponent.markSmoothed(lastRots);
      return lastRots;
    }

    double playerSpeed = Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
    double targetSpeed =
        Math.hypot(target.posX - target.lastTickPosX, target.posZ - target.lastTickPosZ);
    double xzTrim = Math.min(0.15, (playerSpeed + targetSpeed) * 0.2);
    double yTrim = Math.min(0.2, (playerSpeed + targetSpeed) * 0.25);

    AxisAlignedBB bb = target.getEntityBoundingBox().contract(xzTrim, yTrim, xzTrim);

    double motionPredFactor = random.nextDouble() * 0.7;
    double predX = target.posX + (target.posX - target.lastTickPosX) * motionPredFactor;
    double predY = target.posY + (target.posY - target.lastTickPosY) * motionPredFactor;
    double predZ = target.posZ + (target.posZ - target.lastTickPosZ) * motionPredFactor;
    bb = bb.offset(predX - target.posX, predY - target.posY, predZ - target.posZ);

    List<Vec3> allPoints = findPoints(bb, 2048);
    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
    List<Vec3> validPoints = new ArrayList<>();

    for (Vec3 point : allPoints) {
      if (canSeePoint(eyePos, point)) {
        validPoints.add(point);
      }
    }

    Vec3 lookDir = mc.thePlayer.getLook(1.0F).normalize();
    Vec3 bestPoint;

    if (!validPoints.isEmpty()) {
      validPoints.sort(
          (a, b) -> {
            double da = a.subtract(eyePos).crossProduct(lookDir).lengthVector();
            double db = b.subtract(eyePos).crossProduct(lookDir).lengthVector();
            return Double.compare(da, db);
          });
      bestPoint = validPoints.get(0);
    } else {
      allPoints.sort(
          (a, b) -> {
            double da = a.subtract(eyePos).crossProduct(lookDir).lengthVector();
            double db = b.subtract(eyePos).crossProduct(lookDir).lengthVector();
            return Double.compare(da, db);
          });
      bestPoint = allPoints.get(0);
    }

    double diffX = bestPoint.xCoord - eyePos.xCoord;
    double diffY = bestPoint.yCoord - eyePos.yCoord;
    double diffZ = bestPoint.zCoord - eyePos.zCoord;
    double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

    float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
    float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

    yaw += random.nextGaussian() * 0.15;
    pitch += random.nextGaussian() * 0.15;

    float jitterFactor = 0.7F;
    if (random.nextBoolean()) {
      yaw += (random.nextFloat() - 0.5f) * jitterFactor;
      pitch += (random.nextFloat() - 0.5f) * jitterFactor;
    }

    float yawDiff = MathHelper.wrapAngleTo180_float(yaw - lastRots[0]);
    float pitchDiff = pitch - lastRots[1];

    float maxStep = (float) rotSpeed;
    if (Math.abs(yawDiff) > maxStep) {
      yaw = lastRots[0] + Math.copySign(maxStep, yawDiff);
    }
    if (Math.abs(pitchDiff) > maxStep) {
      pitch = lastRots[1] + Math.copySign(maxStep, pitchDiff);
    }

    float[] result = new float[] {yaw, pitch};
    RotationComponent.markSmoothed(result);
    return result;
  }

  private List<Vec3> findPoints(AxisAlignedBB bb, int pointCount) {
    List<Vec3> points = new ArrayList<>();
    double cbrt = Math.cbrt(pointCount);

    double minX = bb.minX, minY = bb.minY, minZ = bb.minZ;
    double maxX = bb.maxX, maxY = bb.maxY, maxZ = bb.maxZ;

    double width = maxX - minX;
    double height = maxY - minY;
    double depth = maxZ - minZ;

    double total = width + height + depth;
    int stepsX = Math.max(2, (int) (cbrt * (width / total) * 3));
    int stepsY = Math.max(2, (int) (cbrt * (height / total) * 3));
    int stepsZ = Math.max(2, (int) (cbrt * (depth / total) * 3));

    double stepX = width / (stepsX - 1);
    double stepY = height / (stepsY - 1);
    double stepZ = depth / (stepsZ - 1);

    // Z-faces
    for (int i = 0; i < stepsX; i++) {
      for (int j = 0; j < stepsY; j++) {
        double x = minX + stepX * i;
        double y = minY + stepY * j;
        points.add(new Vec3(x, y, minZ));
        points.add(new Vec3(x, y, maxZ));
      }
    }

    // Y-faces
    for (int i = 0; i < stepsX; i++) {
      for (int k = 0; k < stepsZ; k++) {
        double x = minX + stepX * i;
        double z = minZ + stepZ * k;
        points.add(new Vec3(x, minY, z));
        points.add(new Vec3(x, maxY, z));
      }
    }

    // X-faces
    for (int j = 0; j < stepsY; j++) {
      for (int k = 0; k < stepsZ; k++) {
        double y = minY + stepY * j;
        double z = minZ + stepZ * k;
        points.add(new Vec3(minX, y, z));
        points.add(new Vec3(maxX, y, z));
      }
    }

    return points;
  }

  private boolean canSeePoint(Vec3 eyePos, Vec3 point) {
    return mc.theWorld.rayTraceBlocks(eyePos, point, false, true, false) == null;
  }
}
