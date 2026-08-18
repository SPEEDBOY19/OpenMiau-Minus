package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.network.BackTrack;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.misc.BackTrackUtil;
import miau.util.misc.SomeUtil;
import miau.util.player.AimAssistRotationUtil;
import miau.util.player.RotationUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class SmoothAimAssist extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final FloatProperty range = new FloatProperty("Range", 4.4F, 1F, 8F);
  private final BooleanProperty horizontalAim = new BooleanProperty("HorizontalAim", true);
  private final BooleanProperty verticalAim = new BooleanProperty("VerticalAim", true);
  private final IntProperty horizontalSpeed = new IntProperty("HorizontalSpeed", 180, 1, 180);
  private final IntProperty verticalSpeed = new IntProperty("VerticalSpeed", 180, 1, 180);
  private final FloatProperty entropyMax = new FloatProperty("EntropyDisturbMax", 1F, 0F, 10F);
  private final FloatProperty entropyMin = new FloatProperty("EntropyDisturbMin", 0.5F, 0F, 10F);
  private final FloatProperty entropyFactor = new FloatProperty("EntropyFactor", 0.5F, 0F, 10F);
  private final FloatProperty randomize = new FloatProperty("Randomize", 0.5F, 0F, 5F);
  private final BooleanProperty heuristic = new BooleanProperty("Heuristic", true);

  private final FloatProperty fov = new FloatProperty("FOV", 180F, 1F, 180F);
  private final BooleanProperty onClick =
      new BooleanProperty(
          "OnClick", false, () -> horizontalAim.getValue() || verticalAim.getValue());
  private final BooleanProperty breakBlocks = new BooleanProperty("BreakBlocks", true);

  private final TimerUtil clickTimer = new TimerUtil();

  public SmoothAimAssist() {
    super("SmoothAimAssist", false);
  }

  @EventTarget
  public void onMotion(UpdateEvent event) {
    if (event.getType() != EventType.POST) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.gameSettings.keyBindAttack.isKeyDown()) {
      clickTimer.reset();
    }

    boolean clicking =
        mc.gameSettings.keyBindAttack.isKeyDown()
            || (System.currentTimeMillis() - clickTimer.getTime() < 150);

    if (onClick.getValue() && !clicking) {
      return;
    }

    Entity nearest = null;
    double nearestDist = Double.MAX_VALUE;
    for (Entity entity : mc.theWorld.loadedEntityList) {
      final Entity candidate = entity;
      boolean selected =
          BackTrack.runWithNearestTrackedDistance(
              candidate,
              () ->
                  SomeUtil.isSelected(candidate)
                      && mc.thePlayer.canEntityBeSeen(candidate)
                      && BackTrackUtil.getDistanceToEntityBox(candidate) <= range.getValue()
                      && rotationDifference(candidate) <= fov.getValue());
      if (selected) {
        double dist = BackTrackUtil.getDistanceToEntityBox(candidate);
        if (dist < nearestDist) {
          nearestDist = dist;
          nearest = candidate;
        }
      }
    }

    if (nearest == null) return;

    if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
        && breakBlocks.getValue()) {
      return;
    }

    float[] rotation =
        AimAssistRotationUtil.face(
            (EntityLivingBase) nearest,
            horizontalSpeed.getValue()
                + (float) Math.random(),
            verticalSpeed.getValue() + (float) Math.random(),
            mc.thePlayer.rotationYaw,
            mc.thePlayer.rotationPitch,
            heuristic.getValue(),
            true,
            entropyMax.getValue(),
            entropyMin.getValue(),
            entropyFactor.getValue(),
            randomize.getValue());
    if (rotation != null) {
      mc.thePlayer.rotationYaw = rotation[0];
      mc.thePlayer.rotationPitch = rotation[1];
    }
  }

  private static double rotationDifference(Entity entity) {
    AxisAlignedBB box = entity.getEntityBoundingBox();
    Vec3 center =
        new Vec3(
            box.minX + (box.maxX - box.minX) * 0.5,
            box.minY + (box.maxY - box.minY) * 0.5,
            box.minZ + (box.maxZ - box.minZ) * 0.5);
    float[] rotation = RotationUtil.calculate(center);
    double yawDiff = MathHelper.wrapAngleTo180_float(rotation[0] - mc.thePlayer.rotationYaw);
    double pitchDiff = rotation[1] - mc.thePlayer.rotationPitch;
    return Math.hypot(yawDiff, pitchDiff);
  }
}