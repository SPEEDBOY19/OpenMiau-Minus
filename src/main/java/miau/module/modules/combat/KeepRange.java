package miau.module.modules.combat;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.player.PlayerUtil;
import miau.util.player.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class KeepRange extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("mode", 1, new String[] {"BackWards", "Stop"});
  public final FloatProperty range = new FloatProperty("range", 3.0F, 0.0F, 6.0F);
  public final BooleanProperty disableNearEdge = new BooleanProperty("disable-near-edge", true);
  public final IntProperty edgeRange =
      new IntProperty("edge-range", 5, 0, 6, () -> !disableNearEdge.getValue());
  public final IntProperty combo = new IntProperty("combo-to-start", 2, 0, 6);

  private boolean edge;
  private int row;

  public KeepRange() {
    super("KeepRange", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (!this.isEnabled()) return;
    if (!mc.thePlayer.onGround) return;

    edge = false;
    int range = this.edgeRange.getValue();

    for (int x = -range; x <= range; x++) {
      for (int z = -range; z <= range; z++) {
        for (int y = -5; y <= 0; y++) {
          Block block =
              mc.theWorld
                  .getBlockState(
                      new BlockPos(
                          mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z))
                  .getBlock();
          boolean air = block instanceof BlockAir;

          if (!air) {
            break;
          }

          if (y == 0) {
            edge = true;
            return;
          }
        }
      }
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled()) return;

    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    EntityLivingBase target = killAura != null ? killAura.getTarget() : null;

    if (target == null || (edge && disableNearEdge.getValue())) {
      row = 0;
      return;
    }

    if (target.hurtTime > 0) row += 1;
    if (mc.thePlayer.hurtTime > 0) row = 0;

    if (row <= combo.getValue() * 8 && combo.getValue() > 0) {
      return;
    }

    if (PlayerUtil.calculatePerfectRangeToEntity(target) < this.range.getValue() - 0.05) {
      final float forward = mc.thePlayer.movementInput.moveForward;
      final float strafe = mc.thePlayer.movementInput.moveStrafe;

      final float[] targetRotations = RotationUtil.calculate(target);
      final double angle = MathHelper.wrapAngleTo180_double(targetRotations[0] - 180);

      if (forward == 0 && strafe == 0) {
        return;
      }

      float closestForward = 0, closestStrafe = 0;
      float closestDifference = Float.MAX_VALUE;

      for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
        for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
          if (predictedStrafe == 0 && predictedForward == 0) continue;

          final double predictedAngle =
              MathHelper.wrapAngleTo180_double(
                  Math.toDegrees(
                      MoveUtil.direction(
                          mc.thePlayer.rotationYaw, predictedForward, predictedStrafe)));
          final double difference = MoveUtil.wrappedDifference(angle, predictedAngle);

          if (difference < closestDifference) {
            closestDifference = (float) difference;
            closestForward = predictedForward;
            closestStrafe = predictedStrafe;
          }
        }
      }

      switch (this.mode.getModeString()) {
        case "Stop":
          if (closestForward == forward * -1) mc.thePlayer.movementInput.moveForward = 0;
          if (closestStrafe == strafe * -1) mc.thePlayer.movementInput.moveStrafe = 0;
          break;

        case "BackWards":
          mc.thePlayer.movementInput.moveForward = closestForward;
          mc.thePlayer.movementInput.moveStrafe = closestStrafe;
          break;
      }
    }
  }
}
