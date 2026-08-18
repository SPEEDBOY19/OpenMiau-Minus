package miau.module.modules.render;

import java.awt.Color;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.client.gui.ScaledResolution;

public class Utility extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty hurtIndicator = new BooleanProperty("Hurt Indicator", false);
  public final BooleanProperty fpsIndicator = new BooleanProperty("FPS Indicator", false);
  public final BooleanProperty targetHud = new BooleanProperty("TargetHud", false);

  public Utility() {
    super("Utility", false);
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;

    ScaledResolution sr = new ScaledResolution(mc);
    float cx = sr.getScaledWidth() / 2.0f;
    float cy = sr.getScaledHeight() / 2.0f + 20.0f;

    if (this.hurtIndicator.getValue()) {
      if (mc.thePlayer.hurtTime != 0) {
        int color = new Color(255 / mc.thePlayer.hurtTime, 17, 49).getRGB();
        String text = "Hurted: " + mc.thePlayer.hurtTime;
        mc.fontRendererObj.drawString(text, cx - 24, cy, color, true);
      }
    }

    if (this.fpsIndicator.getValue()) {
      int fps = Minecraft.getDebugFPS();
      if (fps != 0) {
        mc.fontRendererObj.drawString("FPS: " + fps, 2, 2, -1, true);
      }
    }

    if (this.targetHud.getValue()) {
      KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
      EntityLivingBase target = killAura != null ? killAura.getTarget() : null;
      if (target != null) {
        float health = target.getHealth();
        String displayName = mc.thePlayer.getDisplayName() != null
            ? mc.thePlayer.getDisplayName().getFormattedText()
            : "Player";
        mc.fontRendererObj.drawString(
            "Name: " + displayName + ".", cx - 24.0f, cy - 24.0f, 1, true);
        mc.fontRendererObj.drawString(
            "Distance: "
                + String.format("%.1f", mc.thePlayer.getDistanceToEntity(target))
                + " Blocks.",
            cx - 24,
            cy - 30,
            1,
            true);
      }
    }
  }
}
