package miau.module.modules.combat;

import java.util.function.Supplier;
import miau.event.EventTarget;
import miau.event.impl.Render3DEvent;
import miau.module.Module;
import miau.mixin.IAccessorRenderManager;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.misc.BackTrackUtil;
import miau.util.misc.ITruePosition;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class ForwardTrack extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty espMode = new ModeProperty("ESP-Mode", 0, new String[] {"Box", "Model", "Wireframe"});
  public final FloatProperty wireframeWidth =
      new FloatProperty("WireFrame-Width", 1f, 0.5f, 5f, () -> this.espMode.getModeString().equals("Wireframe"));
  public final ColorProperty espColor =
      new ColorProperty("ESPColor", 0x00FF00, () -> !this.espMode.getModeString().equals("Model"));

  public ForwardTrack() {
    super("ForwardTrack", false);
  }

  private boolean isSelected(Entity entity) {
    return entity instanceof EntityLivingBase && entity != mc.thePlayer && !entity.isDead && entity.isEntityAlive();
  }

  public void includeEntityTruePos(Entity entity, Supplier<Object> action) {
    if (!this.isEnabled() || !this.isSelected(entity)) return;
    BackTrackUtil.runWithSimulatedPosition(entity, this.usePosition(entity), action);
  }

  private Vec3 usePosition(Entity entity) {
    if (!mc.isIntegratedServerRunning() && entity instanceof ITruePosition) {
      ITruePosition tp = (ITruePosition) entity;
      if (tp.isTruePos()) {
        return BackTrackUtil.getTrueInterpolatedPosition(
            entity, tp, ((miau.mixin.IAccessorMinecraft) mc).getTimer().renderPartialTicks);
      }
    }
    return new Vec3(entity.posX, entity.posY, entity.posZ);
  }

  private Vec3 renderPos() {
    IAccessorRenderManager accessor = (IAccessorRenderManager) mc.getRenderManager();
    return new Vec3(accessor.getRenderPosX(), accessor.getRenderPosY(), accessor.getRenderPosZ());
  }

  private float lerpYaw(Entity entity, float partialTicks) {
    return entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (mc.theWorld == null) return;
    Vec3 renderPos = this.renderPos();
    for (Entity target : mc.theWorld.loadedEntityList) {
      if (!this.isSelected(target)) continue;
      Vec3 vec = this.usePosition(target);
      double x = vec.xCoord - renderPos.xCoord;
      double y = vec.yCoord - renderPos.yCoord;
      double z = vec.zCoord - renderPos.zCoord;
      String mode = this.espMode.getModeString();
      int color = this.espColor.getValue();
      if (mode.equals("Box")) {
        AxisAlignedBB box =
            target.getEntityBoundingBox().offset(x - target.posX, y - target.posY, z - target.posZ);
        RenderUtil.drawBoundingBox(
            box, (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 1.0F);
      } else if (mode.equals("Model")) {
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.color(0.6f, 0.6f, 0.6f, 1f);
        mc.getRenderManager().doRenderEntity(target, x, y, z, this.lerpYaw(target, event.getPartialTicks()), event.getPartialTicks(), true);
        GL11.glPopAttrib();
        GlStateManager.popMatrix();
      } else if (mode.equals("Wireframe")) {
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(this.wireframeWidth.getValue());
        RenderUtil.glColor(color);
        mc.getRenderManager().doRenderEntity(target, x, y, z, this.lerpYaw(target, event.getPartialTicks()), event.getPartialTicks(), true);
        GL11.glPopAttrib();
        GlStateManager.popMatrix();
      }
    }
  }
}
