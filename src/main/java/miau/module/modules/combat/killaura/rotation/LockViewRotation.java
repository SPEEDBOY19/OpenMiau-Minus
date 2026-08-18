package miau.module.modules.combat.killaura.rotation;

import miau.component.RotationComponent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;

public class LockViewRotation extends RotationMode {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public LockViewRotation(KillAura killAura) {
    super(killAura, "LOCK_VIEW");
  }

  @Override
  public float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event) {
    if (rotSpeed != 0) {
      RotationComponent.setActive(true, this.killAura.moveFix.getValue());

      float[] result =
          RotationUtil.smooth(
              lastRots,
              targetRots,
              rotSpeed,
              this.killAura.getTarget(),
              this.killAura.attackRange.getValue());

      // Lock the player's view
      mc.thePlayer.rotationYaw = result[0];
      mc.thePlayer.rotationPitch = result[1];
      mc.thePlayer.rotationYawHead = result[0];
      mc.thePlayer.renderYawOffset = result[0];

      RotationComponent.markSmoothed(result);
      return result;
    } else {
      return lastRots;
    }
  }
}
