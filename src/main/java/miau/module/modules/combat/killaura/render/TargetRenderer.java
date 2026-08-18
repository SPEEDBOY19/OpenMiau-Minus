package miau.module.modules.combat.killaura.render;

import java.awt.Color;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render3DEvent;
import miau.mixin.IAccessorRenderManager;
import miau.module.modules.combat.KillAura;
import miau.module.modules.render.HUD;
import miau.util.player.TeamUtil;
import miau.util.render.ColorUtil;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

public class TargetRenderer {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final KillAura killAura;
  private int ticks = 255;

  public TargetRenderer(KillAura killAura) {
    this.killAura = killAura;
  }

  @EventTarget
  public void onRender(Render3DEvent event) {
    if (this.killAura.isEnabled() && this.killAura.getTarget() != null) {
      if (this.killAura.showTarget.getValue() != 0
          && TeamUtil.isEntityLoaded(this.killAura.getTarget())
          && this.killAura.isAttackAllowed()) {
        final float partialTicks = event.getPartialTicks();
        EntityLivingBase player = this.killAura.getTarget();

        if (mc.getRenderManager() == null || player == null) return;

        final double x =
            player.prevPosX
                + (player.posX - player.prevPosX) * partialTicks
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        final double y =
            player.prevPosY
                + (player.posY - player.prevPosY) * partialTicks
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        final double z =
            player.prevPosZ
                + (player.posZ - player.prevPosZ) * partialTicks
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        if (this.killAura.showTarget.getValue() == 2) {
          final Color color =
              ((HUD) Miau.moduleManager.modules.get(HUD.class))
                  .getColor(System.currentTimeMillis());
          final double ringY = y + Math.sin(System.currentTimeMillis() / 2E+2) + 1;
          GL11.glPushMatrix();
          GL11.glDisable(3553);
          GL11.glEnable(2848);
          GL11.glEnable(2832);
          GL11.glEnable(3042);
          GL11.glBlendFunc(770, 771);
          GL11.glHint(3154, 4354);
          GL11.glHint(3155, 4354);
          GL11.glHint(3153, 4354);
          GL11.glDepthMask(false);
          GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
          GL11.glShadeModel(GL11.GL_SMOOTH);
          GlStateManager.disableCull();
          GL11.glBegin(GL11.GL_TRIANGLE_STRIP);

          for (float i = 0;
              i <= Math.PI * 2 + ((Math.PI * 2) / 25);
              i += (float) ((Math.PI * 2) / 25)) {
            double vecX = x + 0.67 * Math.cos(i);
            double vecZ = z + 0.67 * Math.sin(i);

            ColorUtil.glColor(ColorUtil.withAlpha(color, (int) (255 * 0.25)));
            GL11.glVertex3d(vecX, ringY, vecZ);
          }

          for (float i = 0; i <= Math.PI * 2 + (Math.PI * 2) / 25; i += (Math.PI * 2) / 25) {
            double vecX = x + 0.67 * Math.cos(i);
            double vecZ = z + 0.67 * Math.sin(i);

            ColorUtil.glColor(ColorUtil.withAlpha(color, (int) (255 * 0.25)));
            GL11.glVertex3d(vecX, ringY, vecZ);

            ColorUtil.glColor(ColorUtil.withAlpha(color, 0));
            GL11.glVertex3d(vecX, ringY - Math.cos(System.currentTimeMillis() / 2E+2) / 2.0F, vecZ);
          }

          GL11.glEnd();
          GL11.glShadeModel(GL11.GL_FLAT);
          GL11.glDepthMask(true);
          GL11.glEnable(2929);
          GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
          GlStateManager.enableCull();
          GL11.glDisable(2848);
          GL11.glDisable(2848);
          GL11.glEnable(2832);
          GL11.glEnable(3553);
          GL11.glPopMatrix();
          GlStateManager.resetColor();
        } else if (this.killAura.showTarget.getValue() == 1) {
          boolean wasHurtRecently = false;
          if (player.hurtTime > 0) {
            wasHurtRecently = true;
            this.ticks = 0;
          }
          if (this.ticks <= 23) {
            wasHurtRecently = true;
          }
          this.ticks++;

          Color color =
              wasHurtRecently
                  ? Color.red
                  : ((HUD) Miau.moduleManager.modules.get(HUD.class))
                      .getColor(System.currentTimeMillis());
          GL11.glPushMatrix();
          GL11.glEnable(3042);
          GL11.glLineWidth(1.8F);
          GL11.glBlendFunc(770, 771);
          GL11.glEnable(2848);
          GlStateManager.depthMask(true);

          GL11.glEnable(GL11.GL_BLEND);
          GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
          GL11.glDisable(GL11.GL_TEXTURE_2D);
          GL11.glEnable(GL11.GL_LINE_SMOOTH);
          GL11.glDisable(GL11.GL_DEPTH_TEST);
          GL11.glDepthMask(false);

          float width = player.width / 1.15F;
          float height = player.height + (player.isSneaking() ? -0.2F : 0.1F);
          AxisAlignedBB aabb =
              new AxisAlignedBB(
                  x - width + 0.1D,
                  y,
                  z - width + 0.1D,
                  x + width - 0.1D,
                  y + height + 0.1D,
                  z + width - 0.1D);

          RenderUtil.drawBoundingBox(
              aabb, color.getRed(), color.getGreen(), color.getBlue(), 60, 1.8F);

          GL11.glDisable(GL11.GL_LINE_SMOOTH);
          GL11.glEnable(GL11.GL_TEXTURE_2D);
          GL11.glEnable(GL11.GL_DEPTH_TEST);
          GL11.glDepthMask(true);
          GL11.glDisable(GL11.GL_BLEND);

          GL11.glDisable(3042);
          GL11.glDisable(2848);
          GL11.glPopMatrix();
          GlStateManager.resetColor();
        }
      }
    }
  }
}
