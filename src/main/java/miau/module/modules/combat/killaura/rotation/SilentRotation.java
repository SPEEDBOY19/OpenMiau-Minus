package miau.module.modules.combat.killaura.rotation;

import miau.component.RotationComponent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;

public class SilentRotation extends RotationMode {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public SilentRotation(KillAura killAura) {
    super(killAura, "SILENT");
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

      RotationComponent.markSmoothed(result);
      return result;
    } else {
      return lastRots;
    }
  }
}
