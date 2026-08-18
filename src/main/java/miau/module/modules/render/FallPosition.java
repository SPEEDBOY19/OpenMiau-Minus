package miau.module.modules.render;

import java.awt.Color;
import miau.event.EventTarget;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.Render3DEvent;
import miau.mixin.IAccessorEntityRenderer;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class FallPosition extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty showTrajectory = new BooleanProperty("Show Trajectory Line", false);
  public final BooleanProperty showDownArrow = new BooleanProperty("Show Down Arrow", true);
  public final BooleanProperty showReticle = new BooleanProperty("Show Target Reticle", true);
  public final BooleanProperty showFallDamage = new BooleanProperty("Show Fall Damage", true);
  public final IntProperty simulationTicks = new IntProperty("Simulation Ticks", 100, 20, 200);
  public final FloatProperty arrowHeight = new FloatProperty("Arrow Height", 2.5F, 1.0F, 6.0F);
  public final ColorProperty landingColor = new ColorProperty("Landing Color", 0xFFB428);
  public final ColorProperty wallColor = new ColorProperty("Wall Color", 0xFF6E96);
  public final ColorProperty lineColor = new ColorProperty("Line Color", 0x00C8FF);
  public final ColorProperty alignedColor = new ColorProperty("Aligned Color", 0x3CFF3C);
  public final ColorProperty offTargetColor = new ColorProperty("Off Target Color", 0xFF3C3C);

  private Vec3 landingPos = null;
  private double fallDistanceAtCalc = 0;
  private boolean isAligned = false;

  private static final java.nio.FloatBuffer MODELVIEW = GLAllocation.createDirectFloatBuffer(16);
  private static final java.nio.FloatBuffer PROJECTION = GLAllocation.createDirectFloatBuffer(16);
  private static final java.nio.IntBuffer VIEWPORT = GLAllocation.createDirectIntBuffer(16);
  private static final java.nio.FloatBuffer SCREEN_COORDS = GLAllocation.createDirectFloatBuffer(3);

  public FallPosition() {
    super("FallPosition", false);
  }

  @Override
  public void onEnabled() {
    this.landingPos = null;
  }

  @Override
  public void onDisabled() {
    this.landingPos = null;
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
      this.landingPos = null;
      return;
    }

    boolean isFalling =
        mc.thePlayer.fallDistance > 0.5F
            && !mc.thePlayer.onGround
            && !mc.thePlayer.isInWater()
            && !mc.thePlayer.isInLava();
    if (!isFalling) {
      this.landingPos = null;
      return;
    }

    double px = mc.thePlayer.posX;
    double py = mc.thePlayer.posY;
    double pz = mc.thePlayer.posZ;
    double vx = mc.thePlayer.motionX;
    double vy = mc.thePlayer.motionY;
    double vz = mc.thePlayer.motionZ;

    int maxTicks = this.simulationTicks.getValue();
    Vec3 result = null;

    for (int t = 0; t < maxTicks; t++) {
      vy -= 0.08;
      px += vx;
      py += vy;
      pz += vz;
      vy *= 0.9800000190734863;
      vx *= 0.91;
      vz *= 0.91;

      if (py < 0 || this.isSolid(new BlockPos((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz)))) {
        result = new Vec3(Math.floor(px), Math.floor(py), Math.floor(pz));
        break;
      }
    }

    this.landingPos = result;
    this.fallDistanceAtCalc = mc.thePlayer.posY - (result != null ? result.yCoord : mc.thePlayer.posY);

    if (this.landingPos != null) {
      this.isAligned =
          Math.floor(mc.thePlayer.posX) == this.landingPos.xCoord
              && Math.floor(mc.thePlayer.posZ) == this.landingPos.zCoord;
    } else {
      this.isAligned = false;
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled() || this.landingPos == null || mc.thePlayer == null || mc.theWorld == null) return;

    int landingColor = this.landingColor.getValue();
    int wallColor = this.wallColor.getValue();
    BlockPos landingPos = new BlockPos((int) this.landingPos.xCoord, (int) this.landingPos.yCoord, (int) this.landingPos.zCoord);
    RenderUtil.renderBlock(landingPos, landingColor, true, true);
    RenderUtil.renderBlock(landingPos.up(), wallColor, true, false);

    Vec3 surfaceCenter = new Vec3(landingPos.getX() + 0.5, landingPos.getY() + 1.01, landingPos.getZ() + 0.5);

    if (this.showTrajectory.getValue()) {
      int lineColor = this.lineColor.getValue();
      Vec3 eyePos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
      this.drawLine3D(eyePos, surfaceCenter, 1.5f, lineColor);
    }

    if (this.showDownArrow.getValue()) {
      int arrowColor = this.isAligned ? this.alignedColor.getValue() : this.offTargetColor.getValue();
      double height = this.arrowHeight.getValue();
      this.drawDownArrow(surfaceCenter, height, 0.35, 2.5f, arrowColor);
    }

    if (this.showReticle.getValue()) {
      int reticleColor = this.isAligned ? this.alignedColor.getValue() : this.offTargetColor.getValue();
      Vec3 playerCenter = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + 0.05, mc.thePlayer.posZ);
      this.drawGroundCircle(playerCenter, 0.42, 28, 2.0f, reticleColor);
      this.drawGroundCircle(playerCenter, 0.28, 28, 4.0f, reticleColor);
      this.drawGroundCircle(playerCenter, 0.12, 20, 2.0f, reticleColor);
    }

    if (this.showFallDamage.getValue()) {
      double totalFall = this.fallDistanceAtCalc;
      String heightLabel = String.format("%.0f \u2193", totalFall);
      String distLabel = String.format("%.0f blocks", totalFall);
      this.drawText3D(heightLabel, new Vec3(surfaceCenter.xCoord, surfaceCenter.yCoord + 0.55, surfaceCenter.zCoord), 0xFF8844);
      this.drawText3D(distLabel, new Vec3(surfaceCenter.xCoord, surfaceCenter.yCoord + 0.30, surfaceCenter.zCoord), 0xFFFFFF);
    }
  }

  private void drawDownArrow(Vec3 tip, double height, double baseHalfWidth, float lineWidth, int color) {
    Vec3 base = new Vec3(tip.xCoord, tip.yCoord + height, tip.zCoord);
    Vec3 c1 = new Vec3(base.xCoord + baseHalfWidth, base.yCoord, base.zCoord + baseHalfWidth);
    Vec3 c2 = new Vec3(base.xCoord + baseHalfWidth, base.yCoord, base.zCoord - baseHalfWidth);
    Vec3 c3 = new Vec3(base.xCoord - baseHalfWidth, base.yCoord, base.zCoord - baseHalfWidth);
    Vec3 c4 = new Vec3(base.xCoord - baseHalfWidth, base.yCoord, base.zCoord + baseHalfWidth);

    this.drawLine3D(base, tip, lineWidth, color);
    this.drawLine3D(c1, tip, lineWidth, color);
    this.drawLine3D(c2, tip, lineWidth, color);
    this.drawLine3D(c3, tip, lineWidth, color);
    this.drawLine3D(c4, tip, lineWidth, color);
    this.drawLine3D(c1, c2, lineWidth, color);
    this.drawLine3D(c2, c3, lineWidth, color);
    this.drawLine3D(c3, c4, lineWidth, color);
    this.drawLine3D(c4, c1, lineWidth, color);
  }

  private void drawGroundCircle(Vec3 center, double radius, int segments, float lineWidth, int color) {
    Vec3 prev = null;
    for (int i = 0; i <= segments; i++) {
      double angle = 2 * Math.PI * i / segments;
      Vec3 point =
          new Vec3(
              center.xCoord + Math.cos(angle) * radius,
              center.yCoord,
              center.zCoord + Math.sin(angle) * radius);
      if (prev != null) {
        this.drawLine3D(prev, point, lineWidth, color);
      }
      prev = point;
    }
  }

  private void drawLine3D(Vec3 start, Vec3 end, float lineWidth, int color) {
    double x1 = start.xCoord - mc.getRenderManager().viewerPosX;
    double y1 = start.yCoord - mc.getRenderManager().viewerPosY;
    double z1 = start.zCoord - mc.getRenderManager().viewerPosZ;
    double x2 = end.xCoord - mc.getRenderManager().viewerPosX;
    double y2 = end.yCoord - mc.getRenderManager().viewerPosY;
    double z2 = end.zCoord - mc.getRenderManager().viewerPosZ;

    RenderUtil.enableRenderState();
    RenderUtil.setColor(color);
    GL11.glLineWidth(lineWidth);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex3d(x1, y1, z1);
    GL11.glVertex3d(x2, y2, z2);
    GL11.glEnd();
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0f);
    RenderUtil.disableRenderState();
  }

  private void drawText3D(String label, Vec3 pos, int color) {
    double[] screen = this.worldToScreen(pos, 1.0f);
    if (screen == null) return;
    GlStateManager.pushMatrix();
    GlStateManager.scale(0.5f, 0.5f, 1.0f);
    mc.fontRendererObj.drawStringWithShadow(
        label,
        (float) (screen[0] / 0.5) - (float) mc.fontRendererObj.getStringWidth(label) / 2.0f / 0.5f,
        (float) (screen[1] / 0.5),
        color);
    GlStateManager.popMatrix();
  }

  private boolean isSolid(BlockPos pos) {
    if (pos == null || mc.theWorld == null) return false;
    net.minecraft.block.Block block = mc.theWorld.getBlockState(pos).getBlock();
    if (block == Blocks.air
        || block == Blocks.water
        || block == Blocks.flowing_water
        || block == Blocks.lava
        || block == Blocks.flowing_lava
        || block == Blocks.fire) {
      return false;
    }
    return true;
  }

  private double[] worldToScreen(Vec3 pos, float partialTicks) {
    ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);
    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
    GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
    GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
    ((java.nio.Buffer) SCREEN_COORDS).clear();
    boolean success =
        GLU.gluProject(
            (float) (pos.xCoord - mc.getRenderManager().viewerPosX),
            (float) (pos.yCoord - mc.getRenderManager().viewerPosY),
            (float) (pos.zCoord - mc.getRenderManager().viewerPosZ),
            MODELVIEW,
            PROJECTION,
            VIEWPORT,
            SCREEN_COORDS);
    mc.entityRenderer.setupOverlayRendering();
    if (!success) return null;
    double scale = new ScaledResolution(mc).getScaleFactor();
    double screenX = SCREEN_COORDS.get(0) / scale;
    double screenY = ((float) mc.displayHeight - SCREEN_COORDS.get(1)) / scale;
    double screenZ = SCREEN_COORDS.get(2);
    if (screenZ < 0.0 || screenZ >= 1.0) return null;
    return new double[] {screenX, screenY};
  }
}
