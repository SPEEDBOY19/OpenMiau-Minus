package miau.module.modules.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import miau.event.EventTarget;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.Render3DEvent;
import miau.mixin.IAccessorRenderManager;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class PearlESP extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty theme =
      new ModeProperty(
          "Theme",
          0,
          new String[] {
            "Default", "Rainbow", "Aurora", "Cherry", "Cotton Candy", "Flare", "Flower", "Forest",
            "Frost", "Gold", "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
          });
  public final FloatProperty lineWidth = new FloatProperty("Line width", 1.5F, 0.5F, 3.0F);
  public final BooleanProperty outlineBlock = new BooleanProperty("Outline block", true);
  public final BooleanProperty shadeBlock = new BooleanProperty("Shade block", true);
  public final BooleanProperty trajectoryLine = new BooleanProperty("Trajectory line", true);

  private static final double DRAG = 0.99;
  private static final double GRAVITY = 0.03;
  private static final int MAX_PREDICTION_TICKS = 240;
  private static final int MAX_COLLISION_SUBSTEPS = 12;

  private final Map<Integer, List<Vec3>> cachedTrajectory = new HashMap<>();
  private final Map<Integer, Vec3> cachedLanding = new HashMap<>();
  private final Map<Integer, Float> pearlAlpha = new HashMap<>();
  private final Map<Integer, Vec3> predictedVelocity = new HashMap<>();
  private final Map<Integer, Vec3> lastPredictedPosition = new HashMap<>();

  public PearlESP() {
    super("PearlESP", false);
  }

  @Override
  public void onEnabled() {
    this.resetPearls();
  }

  @Override
  public void onDisabled() {
    this.resetPearls();
  }

  private void resetPearls() {
    cachedTrajectory.clear();
    cachedLanding.clear();
    pearlAlpha.clear();
    predictedVelocity.clear();
    lastPredictedPosition.clear();
  }

  private boolean isSolid(int x, int y, int z) {
    if (mc.theWorld == null) return false;
    if (mc.theWorld.isAirBlock(new BlockPos(x, y, z))) return false;
    net.minecraft.block.Block block =
        mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
    if (block == Blocks.water
        || block == Blocks.flowing_water
        || block == Blocks.lava
        || block == Blocks.flowing_lava) {
      return false;
    }
    if (block == Blocks.tallgrass
        || block == Blocks.double_plant
        || block == Blocks.yellow_flower
        || block == Blocks.red_flower
        || block == Blocks.deadbush
        || block == Blocks.vine
        || block == Blocks.fire) {
      return false;
    }
    return true;
  }

  private boolean collidesAt(double x, double y, double z) {
    int blockX = MathHelper.floor_double(x);
    int blockY = MathHelper.floor_double(y);
    int blockZ = MathHelper.floor_double(z);
    if (!isSolid(blockX, blockY, blockZ)) return false;

    AxisAlignedBB box = mc.theWorld.getBlockState(new BlockPos(blockX, blockY, blockZ))
        .getBlock().getCollisionBoundingBox(
            mc.theWorld, new BlockPos(blockX, blockY, blockZ), mc.theWorld.getBlockState(new BlockPos(blockX, blockY, blockZ)));
    if (box == null) return false;

    double minX = (1.0 - (box.maxX - box.minX)) * 0.5;
    double minZ = (1.0 - (box.maxZ - box.minZ)) * 0.5;
    double localX = x - blockX;
    double localY = y - blockY;
    double localZ = z - blockZ;
    return localX >= minX
        && localX <= minX + (box.maxX - box.minX)
        && localY >= 0.0
        && localY <= box.maxY - box.minY
        && localZ >= minZ
        && localZ <= minZ + (box.maxZ - box.minZ);
  }

  private boolean isWaterAt(Vec3 position) {
    if (mc.theWorld == null) return false;
    net.minecraft.block.Block block =
        mc.theWorld.getBlockState(
                new BlockPos(
                    MathHelper.floor_double(position.xCoord),
                    MathHelper.floor_double(position.yCoord),
                    MathHelper.floor_double(position.zCoord)))
            .getBlock();
    return block == Blocks.water || block == Blocks.flowing_water;
  }

  private Vec3 advancePearlVelocity(Vec3 velocity, Vec3 position) {
    double drag = isWaterAt(position) ? 0.8 : DRAG;
    return new Vec3(
        velocity.xCoord * drag, velocity.yCoord * drag - GRAVITY, velocity.zCoord * drag);
  }

  private Vec3 predictResultLanding = null;
  private List<Vec3> predictResultPts = new ArrayList<>();

  private void predictTrajectory(Vec3 pos, Vec3 vel) {
    List<Vec3> pts = new ArrayList<>();
    double px = pos.xCoord;
    double py = pos.yCoord;
    double pz = pos.zCoord;
    double vx = vel.xCoord;
    double vy = vel.yCoord;
    double vz = vel.zCoord;
    pts.add(new Vec3(px, py, pz));

    for (int step = 0; step < MAX_PREDICTION_TICKS; step++) {
      double sx = px;
      double sy = py;
      double sz = pz;
      double nx = px + vx;
      double ny = py + vy;
      double nz = pz + vz;
      double largestAxis = Math.max(Math.abs(vx), Math.max(Math.abs(vy), Math.abs(vz)));
      int substeps =
          Math.max(2, Math.min(MAX_COLLISION_SUBSTEPS, (int) Math.ceil(largestAxis / 0.18)));

      for (int sub = 1; sub <= substeps; sub++) {
        double t = sub / (double) substeps;
        double cx = sx + (nx - sx) * t;
        double cy = sy + (ny - sy) * t;
        double cz = sz + (nz - sz) * t;
        pts.add(new Vec3(cx, cy, cz));
        if (collidesAt(cx, cy, cz)) {
          this.predictResultLanding =
              new Vec3(Math.floor(cx), Math.floor(cy), Math.floor(cz));
          this.predictResultPts = pts;
          return;
        }
        if (cy < -64.0) {
          this.predictResultLanding = null;
          this.predictResultPts = pts;
          return;
        }
      }

      px = nx;
      py = ny;
      pz = nz;
      double drag = isWaterAt(new Vec3(px, py, pz)) ? 0.8 : DRAG;
      vx *= drag;
      vy = vy * drag - GRAVITY;
      vz *= drag;
      if (ny < -64.0) {
        this.predictResultLanding = null;
        this.predictResultPts = pts;
        return;
      }
    }
    this.predictResultLanding = null;
    this.predictResultPts = pts;
  }

  private int clampInt(int v, int lo, int hi) {
    return v < lo ? lo : v > hi ? hi : v;
  }

  private int withAlpha(int color, int alpha) {
    return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
  }

  private int lerpColor(int c1, int c2, double t) {
    int r =
        clampInt(
            (int)
                (((c1 >> 16) & 0xFF)
                    + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t)),
            0,
            255);
    int g =
        clampInt(
            (int)
                (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t)),
            0,
            255);
    int b =
        clampInt(
            (int) (((c1) & 0xFF) + ((((c2) & 0xFF) - ((c1) & 0xFF)) * t)), 0, 255);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private int getThemeColor(String name) {
    String lo = name.toLowerCase().trim();
    double ms = System.currentTimeMillis();

    if (lo.equals("rainbow")) {
      double t = ms / 420.0;
      return 0xFF000000
          | (clampInt((int) (128 + 127 * Math.sin(t)), 0, 255) << 16)
          | (clampInt((int) (128 + 127 * Math.sin(t + 2.094)), 0, 255) << 8)
          | clampInt((int) (128 + 127 * Math.sin(t + 4.189)), 0, 255);
    }

    double p = (Math.sin(ms / 1200.0) + 1.0) / 2.0;
    if (lo.equals("aurora")) return lerpColor(0xFF7301C2, 0xFF17F0B1, p);
    if (lo.equals("cherry")) return lerpColor(0xFFDD3D69, 0xFFE0B3B7, p);
    if (lo.equals("cotton candy")) return lerpColor(0xFF92DAE8, 0xFFED68B8, p);
    if (lo.equals("flare")) return lerpColor(0xFFF26B16, 0xFFE4A61D, p);
    if (lo.equals("flower")) return lerpColor(0xFFC89AD8, 0xFFAC59B9, p);
    if (lo.equals("forest")) return lerpColor(0xFF1F7617, 0xFF60A623, p);
    if (lo.equals("frost")) return lerpColor(0xFFDFE3E3, 0xFFBCC5CA, p);
    if (lo.equals("gold")) return lerpColor(0xFFE5DF30, 0xFFDADAB6, p);
    if (lo.equals("grayscale")) return lerpColor(0xFF616368, 0xFFE7E8EA, p);
    if (lo.equals("inferno")) return lerpColor(0xFF350000, 0xFFC03912, p);
    if (lo.equals("royal")) return lerpColor(0xFF85BFE8, 0xFF1D3D87, p);
    if (lo.equals("sandstorm")) return lerpColor(0xFF9D9369, 0xFFF5E3B4, p);
    if (lo.equals("sky")) return lerpColor(0xFF81EAF8, 0xFF15BCD3, p);
    if (lo.equals("vine")) return lerpColor(0xFF27E439, 0xFF9AF8A1, p);
    return 0xFFFFFFFF;
  }

  private int themeColor(int alpha) {
    int base = getThemeColor(this.theme.getModeString());
    return withAlpha(base, alpha);
  }

  private int themeColorDim(int alpha) {
    int base = getThemeColor(this.theme.getModeString());
    int r = (base >> 16) & 0xFF;
    int g = (base >> 8) & 0xFF;
    int b = base & 0xFF;
    return withAlpha(
        0xFF000000 | ((int) (r * 0.6) << 16) | ((int) (g * 0.6) << 8) | (int) (b * 0.6), alpha);
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;

    HashSet<Integer> live = new HashSet<>();
    List<Integer> remove = new ArrayList<>();

    for (Object o : mc.theWorld.loadedEntityList) {
      Entity e = (Entity) o;
      if (e == null || e.isDead || !(e instanceof EntityEnderPearl)) continue;

      Vec3 pos = new Vec3(e.posX, e.posY, e.posZ);
      if (mc.thePlayer.getDistanceSqToEntity(e) > 512.0 * 512.0) continue;

      int key = e.getEntityId();
      live.add(key);

      Float alpha = pearlAlpha.get(key);
      float alphaVal = alpha == null ? 0.0F : alpha;
      alphaVal = Math.min(1.0F, alphaVal + 0.12F);
      pearlAlpha.put(key, alphaVal);

      if (e.ticksExisted < 2) continue;

      Vec3 last = new Vec3(e.lastTickPosX, e.lastTickPosY, e.lastTickPosZ);
      Vec3 observedVelocity = new Vec3(pos.xCoord - last.xCoord, pos.yCoord - last.yCoord, pos.zCoord - last.zCoord);
      double speed =
          Math.sqrt(
              observedVelocity.xCoord * observedVelocity.xCoord
                  + observedVelocity.yCoord * observedVelocity.yCoord
                  + observedVelocity.zCoord * observedVelocity.zCoord);
      if (speed < 0.01) continue;

      Vec3 previousPosition = lastPredictedPosition.get(key);
      if (previousPosition != null
          && previousPosition.xCoord == pos.xCoord
          && previousPosition.yCoord == pos.yCoord
          && previousPosition.zCoord == pos.zCoord) continue;

      Vec3 nextVelocity = advancePearlVelocity(observedVelocity, pos);
      Vec3 previousVelocity = predictedVelocity.get(key);
      if (previousVelocity != null) {
        Vec3 expectedVelocity = advancePearlVelocity(previousVelocity, pos);
        double errorX = nextVelocity.xCoord - expectedVelocity.xCoord;
        double errorY = nextVelocity.yCoord - expectedVelocity.yCoord;
        double errorZ = nextVelocity.zCoord - expectedVelocity.zCoord;
        double errorSq = errorX * errorX + errorY * errorY + errorZ * errorZ;
        if (errorSq < 0.09) {
          nextVelocity =
              new Vec3(
                  nextVelocity.xCoord * 0.82 + expectedVelocity.xCoord * 0.18,
                  nextVelocity.yCoord * 0.82 + expectedVelocity.yCoord * 0.18,
                  nextVelocity.zCoord * 0.82 + expectedVelocity.zCoord * 0.18);
        }
      }

      predictTrajectory(pos, nextVelocity);
      predictedVelocity.put(key, nextVelocity);
      lastPredictedPosition.put(key, pos);
      if (predictResultLanding != null) {
        cachedLanding.put(key, predictResultLanding);
      }
      cachedTrajectory.put(key, new ArrayList<>(predictResultPts));
    }

    for (Integer id : pearlAlpha.keySet()) {
      if (live.contains(id)) continue;
      Float alpha = pearlAlpha.get(id);
      float faded = (alpha == null ? 1.0F : alpha) - 0.14F;
      if (faded <= 0.0F) {
        remove.add(id);
      } else {
        pearlAlpha.put(id, faded);
      }
    }
    for (Integer id : remove) {
      cachedLanding.remove(id);
      cachedTrajectory.remove(id);
      pearlAlpha.remove(id);
      predictedVelocity.remove(id);
      lastPredictedPosition.remove(id);
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;

    float lineW = this.lineWidth.getValue();
    for (Map.Entry<Integer, List<Vec3>> entry : cachedTrajectory.entrySet()) {
      Vec3 landing = cachedLanding.get(entry.getKey());
      Float rawAlpha = pearlAlpha.get(entry.getKey());
      float fa = rawAlpha != null ? rawAlpha : 1.0F;

      int colBlock = themeColor((int) (fa * 180));
      int colShade = themeColorDim((int) (fa * 60));

      if (trajectoryLine.getValue()) {
        this.drawSmoothTrajectory(entry.getValue(), lineW, fa);
      }

      if (landing != null) {
        int bx = MathHelper.floor_double(landing.xCoord);
        int by = MathHelper.floor_double(landing.yCoord);
        int bz = MathHelper.floor_double(landing.zCoord);
        double rx = (double) bx - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double ry = (double) by - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double rz = (double) bz - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        if (shadeBlock.getValue()) {
          RenderUtil.enableRenderState();
          RenderUtil.setColor(colShade);
          RenderUtil.drawFilledBox(
              new AxisAlignedBB(rx, ry, rz, rx + 1.0, ry + 1.0, rz + 1.0),
              (colShade >> 16) & 0xFF,
              (colShade >> 8) & 0xFF,
              colShade & 0xFF);
          RenderUtil.disableRenderState();
        }
        if (outlineBlock.getValue()) {
          RenderUtil.enableRenderState();
          RenderUtil.setColor(colBlock);
          RenderUtil.drawBoundingBox(
              new AxisAlignedBB(rx, ry, rz, rx + 1.0, ry + 1.0, rz + 1.0),
              (colBlock >> 16) & 0xFF,
              (colBlock >> 8) & 0xFF,
              colBlock & 0xFF,
              255,
              1.5F);
          RenderUtil.disableRenderState();
        }
      }
    }
  }

  private void drawSmoothTrajectory(List<Vec3> pts, float lineWidth, float alpha) {
    if (pts == null || pts.size() < 2) return;

    int base = getThemeColor(this.theme.getModeString());
    int r = (base >> 16) & 0xFF;
    int g = (base >> 8) & 0xFF;
    int b = base & 0xFF;
    int sz = pts.size();

    RenderUtil.enableRenderState();
    RenderUtil.setColor(0xFFFFFFFF);
    GL11.glLineWidth(lineWidth);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

    double rx = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
    double ry = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
    double rz = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

    for (int i = 0; i < sz - 1; i++) {
      float frac = (float) i / Math.max(1, sz - 1);
      int a = clampInt((int) (alpha * (255 - frac * 180)), 0, 255);
      int color = (a << 24) | (r << 16) | (g << 8) | b;
      RenderUtil.setColor(color);
      Vec3 p1 = pts.get(i);
      Vec3 p2 = pts.get(i + 1);
      GL11.glBegin(GL11.GL_LINES);
      GL11.glVertex3d(p1.xCoord - rx, p1.yCoord - ry, p1.zCoord - rz);
      GL11.glVertex3d(p2.xCoord - rx, p2.yCoord - ry, p2.zCoord - rz);
      GL11.glEnd();
    }

    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0F);
    GlStateManager.resetColor();
    RenderUtil.disableRenderState();
  }
}
