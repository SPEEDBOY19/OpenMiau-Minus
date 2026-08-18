package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.LivingUpdateEvent;
import miau.module.Module;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;

public class KnockbackBoost extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private boolean start = false;
  private int ticks = 0;

  public KnockbackBoost() {
    super("KnockbackBoost", false);
  }

  @Override
  public void onEnabled() {
    this.start = false;
    this.ticks = 0;
  }

  @Override
  public void onDisabled() {
    this.start = false;
    this.ticks = 0;
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.thePlayer.hurtTime >= 3) {
      this.start = true;
    }
    if (this.start) {
      this.ticks++;
    }

    if (!mc.thePlayer.onGround) {
      if (this.ticks == 1) {
        mc.thePlayer.motionY += 0.061;
      } else if (this.ticks > 1) {
        mc.thePlayer.motionY += 0.0283;
      }
    } else if (this.ticks > 1) {
      this.setEnabled(false);
      return;
    }

    if (this.ticks > 0 && this.ticks < 30) {
      mc.thePlayer.motionY = 0.29;
    }

    if (this.start && mc.thePlayer.hurtTime == 9 && MoveUtil.isForwardPressed()) {
      MoveUtil.setSpeed(1.94);
    }
  }
}
