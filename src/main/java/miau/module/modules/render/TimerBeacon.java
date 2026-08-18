package miau.module.modules.render;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import miau.event.EventTarget;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.Render3DEvent;
import miau.mixin.IAccessorEntityRenderer;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.client.SoundUtil;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class TimerBeacon extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty allowSelf = new BooleanProperty("Allow self", true);
  public final BooleanProperty ping = new BooleanProperty("Ping", true);

  private static final long FROZEN_TIME_MS = 4500L;
  private static final double XZ_THRESHOLD = 3.0;
  private static final double XZ_HARD_RESET_THRESHOLD = 7.0;
  private static final double SLOW_FALL_BLOCKS_PER_SECOND = 2.25;

  private final Map<Integer, Vec3> anchorPositions = new HashMap<>();
  private final Map<Integer, Long> stuckSince = new HashMap<>();
  private final Map<Integer, Boolean> alerted = new HashMap<>();
  private final Map<String, Integer> teamColours = new HashMap<>();
  private final Map<Integer, String> beaconNames = new LinkedHashMap<>();
  private final Map<Integer, Integer> beaconColours = new LinkedHashMap<>();
  private final Map<Integer, Boolean> beaconMessagesSent = new LinkedHashMap<>();

  private static final FloatBuffer MODELVIEW = GLAllocation.createDirectFloatBuffer(16);
  private static final FloatBuffer PROJECTION = GLAllocation.createDirectFloatBuffer(16);
  private static final IntBuffer VIEWPORT = GLAllocation.createDirectIntBuffer(16);
  private static final FloatBuffer SCREEN_COORDS = GLAllocation.createDirectFloatBuffer(3);

  public TimerBeacon() {
    super("TimerBeacon", false);
    this.loadTeamColours();
  }

  @Override
  public void onEnabled() {
    this.resetAll();
  }

  @Override
  public void onDisabled() {
    this.resetAll();
  }

  private void resetAll() {
    anchorPositions.clear();
    stuckSince.clear();
    alerted.clear();
    clearAllBeacons();
  }

  private void loadTeamColours() {
    teamColours.put("0", new Color(0, 0, 0, 255).getRGB());
    teamColours.put("1", new Color(0, 0, 170, 255).getRGB());
    teamColours.put("2", new Color(0, 170, 0, 255).getRGB());
    teamColours.put("3", new Color(0, 170, 170, 255).getRGB());
    teamColours.put("4", new Color(170, 0, 0, 255).getRGB());
    teamColours.put("5", new Color(170, 0, 170, 255).getRGB());
    teamColours.put("6", new Color(255, 170, 0, 255).getRGB());
    teamColours.put("7", new Color(170, 170, 170, 255).getRGB());
    teamColours.put("8", new Color(85, 85, 85, 255).getRGB());
    teamColours.put("9", new Color(85, 85, 255, 255).getRGB());
    teamColours.put("a", new Color(85, 255, 85, 255).getRGB());
    teamColours.put("b", new Color(85, 255, 255, 255).getRGB());
    teamColours.put("c", new Color(255, 85, 85, 255).getRGB());
    teamColours.put("d", new Color(255, 85, 255, 255).getRGB());
    teamColours.put("e", new Color(255, 255, 85, 255).getRGB());
    teamColours.put("f", new Color(255, 255, 255, 255).getRGB());
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
      this.resetAll();
      return;
    }

    long now = System.currentTimeMillis();
    HashSet<Integer> activeIds = new HashSet<>();

    if (this.allowSelf.getValue()) {
      this.scanEntity(mc.thePlayer, now, activeIds);
    } else {
      this.clearSelfTimerState(mc.thePlayer);
    }

    for (Object o : mc.theWorld.playerEntities) {
      Entity entity = (Entity) o;
      if (entity == null || entity == mc.thePlayer) continue;
      this.scanEntity(entity, now, activeIds);
    }

    this.pruneInactive(activeIds);
  }

  private void scanEntity(Entity entity, long now, HashSet<Integer> activeIds) {
    if (this.isInvalidTarget(entity)) return;

    int id = entity.getEntityId();
    activeIds.add(id);

    Vec3 pos = new Vec3(entity.posX, entity.posY, entity.posZ);
    if (pos == null) return;

    if (!this.isInVoid(pos) || this.isInWater(pos)) {
      this.resetTracking(id, pos);
      return;
    }

    if (!this.anchorPositions.containsKey(id)) {
      this.anchorPositions.put(id, pos);
      this.stuckSince.put(id, now);
      this.alerted.put(id, false);
      return;
    }

    Vec3 anchor = this.anchorPositions.get(id);
    long since = this.stuckSince.containsKey(id) ? this.stuckSince.get(id) : now;
    long trackedFor = now - since;
    double xzDistSq =
        anchor == null
            ? Double.MAX_VALUE
            : this.xzDistanceSq(anchor.xCoord, anchor.zCoord, pos.xCoord, pos.zCoord);
    if (anchor == null || xzDistSq > XZ_HARD_RESET_THRESHOLD * XZ_HARD_RESET_THRESHOLD) {
      this.resetTracking(id, pos);
      return;
    }

    if (xzDistSq > XZ_THRESHOLD * XZ_THRESHOLD) {
      if (!this.isSlowVoidFall(anchor, pos, trackedFor)) {
        this.resetTracking(id, pos);
        return;
      }
      this.anchorPositions.put(id, new Vec3(pos.xCoord, anchor.yCoord, pos.zCoord));
    }

    if (trackedFor >= FROZEN_TIME_MS && !this.alerted.getOrDefault(id, false)) {
      if (!this.isSlowVoidFall(this.anchorPositions.get(id), pos, trackedFor)) {
        this.resetTracking(id, pos);
        return;
      }
      if (this.notifyPotentialTimer(entity)) {
        this.alerted.put(id, true);
      }
    }
  }

  private boolean isInvalidTarget(Entity entity) {
    try {
      if (entity.isDead) return true;
    } catch (Exception ignored) {
    }
    if (entity instanceof EntityPlayer) {
      EntityPlayer p = (EntityPlayer) entity;
      try {
        if (p.getHealth() <= 0.0f) return true;
      } catch (Exception ignored) {
      }
    }
    return false;
  }

  private void resetTracking(int id, Vec3 pos) {
    this.anchorPositions.put(id, pos);
    this.stuckSince.put(id, System.currentTimeMillis());
    this.alerted.put(id, false);
  }

  private void pruneInactive(HashSet<Integer> activeIds) {
    for (Iterator<Map.Entry<Integer, Vec3>> it =
            this.anchorPositions.entrySet().iterator();
        it.hasNext(); ) {
      Map.Entry<Integer, Vec3> entry = it.next();
      int id = entry.getKey();
      if (!activeIds.contains(id)) {
        it.remove();
        this.stuckSince.remove(id);
        this.alerted.remove(id);
      }
    }
  }

  private double xzDistanceSq(double ax, double az, double bx, double bz) {
    double dx = ax - bx;
    double dz = az - bz;
    return dx * dx + dz * dz;
  }

  private boolean isSlowVoidFall(Vec3 anchor, Vec3 pos, long elapsedMs) {
    if (anchor == null || pos == null || elapsedMs < 750L) return true;
    double elapsedSeconds = Math.max(0.75, elapsedMs / 1000.0);
    double yDrop = Math.max(0.0, anchor.yCoord - pos.yCoord);
    return yDrop / elapsedSeconds <= SLOW_FALL_BLOCKS_PER_SECOND;
  }

  private boolean notifyPotentialTimer(Entity entity) {
    if (this.isClientSpectator()) return false;
    if (entity == mc.thePlayer && !this.allowSelf.getValue()) return false;
    if (this.beaconNames.containsKey(entity.getEntityId())) return true;
    this.activateBeaconForEntity(entity);
    return true;
  }

  private boolean isClientSpectator() {
    if (mc.thePlayer == null) return true;
    try {
      if (mc.thePlayer.isDead) return true;
    } catch (Exception ignored) {
    }
    try {
      if (mc.thePlayer.getHealth() <= 0.0f) return true;
    } catch (Exception ignored) {
    }
    try {
      if (mc.thePlayer.capabilities.allowFlying && !mc.thePlayer.onGround) return true;
    } catch (Exception ignored) {
    }
    return false;
  }

  private boolean isFriendlyEntity(Entity entity) {
    if (entity == mc.thePlayer) return true;
    String myPrefix = this.getOwnTeamPrefix();
    if (myPrefix == null || myPrefix.isEmpty()) return false;
    String display = this.getEntityDisplayName(entity);
    return display != null && display.startsWith(myPrefix);
  }

  private String getOwnTeamPrefix() {
    if (mc.thePlayer == null) return "";
    String prefix = this.getTeamPrefixFromDisplay(this.getEntityDisplayName(mc.thePlayer));
    return prefix.isEmpty() ? "" : prefix;
  }

  private String getEntityDisplayName(Entity entity) {
    try {
      if (entity.getDisplayName() != null) return entity.getDisplayName().getFormattedText();
    } catch (Exception ignored) {
    }
    return "";
  }

  private String getTeamPrefixFromDisplay(String display) {
    if (display == null || display.isEmpty()) return "";
    for (String color : new String[] {"c", "9", "a", "e", "b", "f", "d", "8"}) {
      String prefix = "§" + color;
      if (display.startsWith(prefix)) {
        return prefix;
      }
    }
    return "";
  }

  private void clearSelfTimerState(Entity self) {
    if (self == null) return;
    int id = self.getEntityId();
    this.anchorPositions.remove(id);
    this.stuckSince.remove(id);
    this.alerted.remove(id);
    this.clearBeacon(id);
  }

  private void activateBeaconForEntity(Entity entity) {
    int id = entity.getEntityId();
    this.beaconNames.put(id, this.getEntityName(entity));
    this.beaconColours.put(id, this.withAlpha(this.getPlayerColour(entity), 190));
    this.beaconMessagesSent.put(id, false);
  }

  private void clearBeacon(int id) {
    this.beaconNames.remove(id);
    this.beaconColours.remove(id);
    this.beaconMessagesSent.remove(id);
  }

  private void clearAllBeacons() {
    this.beaconNames.clear();
    this.beaconColours.clear();
    this.beaconMessagesSent.clear();
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled() || this.beaconNames.isEmpty()) return;
    if (this.isClientSpectator()) {
      this.clearAllBeacons();
      return;
    }

    List<Integer> ids = new ArrayList<>(this.beaconNames.keySet());
    for (int id : ids) {
      Entity entity = this.findEntityById(id);
      if (entity == null) {
        this.clearBeacon(id);
        continue;
      }
      Vec3 currentPos = new Vec3(entity.posX, entity.posY, entity.posZ);
      if (!this.isInVoid(currentPos) || this.isInWater(currentPos)) {
        this.clearBeacon(id);
        continue;
      }

      Vec3 pos = this.getLiveBeaconPosition(entity, event.getPartialTicks());
      if (pos == null) {
        this.clearBeacon(id);
        continue;
      }

      int colour = this.withAlpha(this.getPlayerColour(entity), 190);
      this.beaconNames.put(id, this.getEntityName(entity));
      this.beaconColours.put(id, colour);

      this.drawBeaconBeam(pos, colour);

      boolean messageSent =
          this.beaconMessagesSent.containsKey(id) && this.beaconMessagesSent.get(id);
      if (!messageSent) {
        this.beaconMessagesSent.put(id, true);
        this.playAlertSound();
      }
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled() || this.beaconNames.isEmpty()) return;
    if (this.isClientSpectator()) return;

    float partialTicks = event.getPartialTicks();
    List<Integer> ids = new ArrayList<>(this.beaconNames.keySet());
    for (int id : ids) {
      Entity entity = this.findEntityById(id);
      if (entity == null) continue;

      Vec3 currentPos = new Vec3(entity.posX, entity.posY, entity.posZ);
      if (!this.isInVoid(currentPos) || this.isInWater(currentPos)) continue;

      Vec3 pos = this.getLiveBeaconPosition(entity, partialTicks);
      if (pos == null) continue;

      double[] screen =
          this.worldToScreen(pos.xCoord, pos.yCoord + this.getEntityHeight(entity) + 8.0, pos.zCoord, partialTicks);
      if (screen == null) continue;

      int colour =
          this.beaconColours.containsKey(id) ? this.beaconColours.get(id) : 0xAAFFFFFF;
      int coreColour = this.withAlpha(colour, 210);
      String name = this.beaconNames.containsKey(id) ? this.beaconNames.get(id) : "player";

      String prefix = "Timer: ";
      float labelScale = this.getBeaconLabelScale(pos);
      float prefixWidth = (float) mc.fontRendererObj.getStringWidth(prefix) * labelScale;
      float nameWidth = (float) mc.fontRendererObj.getStringWidth(name) * labelScale;
      float x = (float) screen[0] - (prefixWidth + nameWidth) / 2.0f;
      int outlineColor = this.isFriendlyEntity(entity) ? 0xFF22D66B : 0xFFFF4444;
      this.drawBeaconLabelBox(
          prefix, name, x, (float) screen[1] - 18.0f, labelScale, 0xFFFFFFFF, coreColour, outlineColor);
    }
  }

  private float getBeaconLabelScale(Vec3 pos) {
    if (mc.thePlayer == null || pos == null) return 0.72f;
    double dx = mc.thePlayer.posX - pos.xCoord;
    double dy = mc.thePlayer.posY - pos.yCoord;
    double dz = mc.thePlayer.posZ - pos.zCoord;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    return this.clamp((float) (18.0 / Math.max(20.0, distance)), 0.45f, 0.72f);
  }

  private void drawBeaconLabelBox(
      String prefix, String name, float x, float y, float scale, int prefixColor, int nameColor, int outlineColor) {
    float prefixWidth = (float) mc.fontRendererObj.getStringWidth(prefix) * scale;
    float width = prefixWidth + (float) mc.fontRendererObj.getStringWidth(name) * scale;
    float height = (float) mc.fontRendererObj.FONT_HEIGHT * scale;
    float padX = 3.5f * scale;
    float padY = 2.0f * scale;

    float x1 = x - padX;
    float y1 = y - padY;
    float x2 = x + width + padX;
    float y2 = y + height + padY;
    this.drawRectOutline(x1, y1, x2, y2, 1.0f, this.withAlpha(outlineColor, 230));

    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0.0f);
    GlStateManager.scale(scale, scale, 1.0f);
    mc.fontRendererObj.drawString(prefix, 0.0f, 0.0f, prefixColor, true);
    mc.fontRendererObj.drawString(name, prefixWidth / scale, 0.0f, nameColor, true);
    GlStateManager.popMatrix();
  }

  private void drawRectOutline(float x1, float y1, float x2, float y2, float thickness, int color) {
    RenderUtil.drawRect(x1, y1, x2, y1 + thickness, color);
    RenderUtil.drawRect(x1, y2 - thickness, x2, y2, color);
    RenderUtil.drawRect(x1, y1, x1 + thickness, y2, color);
    RenderUtil.drawRect(x2 - thickness, y1, x2, y2, color);
  }

  private void drawBeaconBeam(Vec3 pos, int colour) {
    double x = pos.xCoord - mc.getRenderManager().viewerPosX;
    double y = pos.yCoord - mc.getRenderManager().viewerPosY;
    double z = pos.zCoord - mc.getRenderManager().viewerPosZ;

    RenderUtil.enableRenderState();
    RenderUtil.setColor(this.withAlpha(colour, 85));
    GL11.glLineWidth(2.0f);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex3d(x, y, z);
    GL11.glVertex3d(x, y + 128.0, z);
    GL11.glEnd();
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glLineWidth(2.0f);
    RenderUtil.disableRenderState();
  }

  private Vec3 getLiveBeaconPosition(Entity entity, float partialTicks) {
    double x = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, partialTicks);
    double y = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, partialTicks);
    double z = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, partialTicks);
    return new Vec3(Math.floor(x) + 0.5, Math.floor(y), Math.floor(z) + 0.5);
  }

  private double getEntityHeight(Entity entity) {
    try {
      return entity.height;
    } catch (Exception ignored) {
    }
    return 2.0;
  }

  private Entity findEntityById(int id) {
    if (mc.thePlayer != null && mc.thePlayer.getEntityId() == id) return mc.thePlayer;
    if (mc.theWorld == null) return null;
    for (Object o : mc.theWorld.playerEntities) {
      Entity entity = (Entity) o;
      if (entity != null && entity.getEntityId() == id) return entity;
    }
    return null;
  }

  private boolean isInVoid(Vec3 pos) {
    int y = (int) Math.floor(pos.yCoord);
    double radius = 0.42;
    double[] xs = new double[] {pos.xCoord, pos.xCoord - radius, pos.xCoord + radius};
    double[] zs = new double[] {pos.zCoord, pos.zCoord - radius, pos.zCoord + radius};

    for (double sampleX : xs) {
      for (double sampleZ : zs) {
        int x = (int) Math.floor(sampleX);
        int z = (int) Math.floor(sampleZ);
        for (int checkY = y - 1; checkY >= 0; checkY--) {
          if (!mc.theWorld.isAirBlock(new BlockPos(x, checkY, z))) return false;
        }
      }
    }
    return true;
  }

  private boolean isInWater(Vec3 pos) {
    BlockPos blockPos = new BlockPos((int) Math.floor(pos.xCoord), (int) Math.floor(pos.yCoord), (int) Math.floor(pos.zCoord));
    net.minecraft.block.Block block = mc.theWorld.getBlockState(blockPos).getBlock();
    return block == Blocks.water
        || block == Blocks.flowing_water
        || block == Blocks.lava
        || block == Blocks.flowing_lava;
  }

  private int getPlayerColour(Entity entity) {
    String name = this.getEntityDisplayName(entity);
    if (name != null && !name.isEmpty()) {
      for (int i = 0; i < name.length() - 1; i++) {
        if (name.charAt(i) == '§') {
          String code = String.valueOf(name.charAt(i + 1)).toLowerCase();
          if (this.teamColours.containsKey(code)) return this.teamColours.get(code);
        }
      }
    }
    return new Color(255, 255, 255, 255).getRGB();
  }

  private int withAlpha(int color, int alpha) {
    return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
  }

  private float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private String getEntityName(Entity entity) {
    try {
      String name = this.getEntityDisplayName(entity);
      if (name != null && !name.isEmpty()) return name.replaceAll("§[\\da-fk-or]", "");
    } catch (Exception ignored) {
    }
    try {
      if (entity instanceof EntityPlayer) {
        String name = ((EntityPlayer) entity).getName();
        if (name != null && !name.isEmpty()) return name;
      }
    } catch (Exception ignored) {
    }
    return "player";
  }

  private void playAlertSound() {
    if (!this.ping.getValue()) return;
    try {
      SoundUtil.playSound("random.orb");
    } catch (Exception ignored) {
    }
  }

  private double[] worldToScreen(double x, double y, double z, float partialTicks) {
    ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);
    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
    GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
    GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
    ((java.nio.Buffer) SCREEN_COORDS).clear();
    boolean success =
        GLU.gluProject(
            (float) (x - mc.getRenderManager().viewerPosX),
            (float) (y - mc.getRenderManager().viewerPosY),
            (float) (z - mc.getRenderManager().viewerPosZ),
            MODELVIEW,
            PROJECTION,
            VIEWPORT,
            SCREEN_COORDS);
    mc.entityRenderer.setupOverlayRendering();
    if (!success) return null;
    double scale = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
    double screenX = SCREEN_COORDS.get(0) / scale;
    double screenY = ((float) mc.displayHeight - SCREEN_COORDS.get(1)) / scale;
    double screenZ = SCREEN_COORDS.get(2);
    if (screenZ < 0.0 || screenZ >= 1.0) return null;
    return new double[] {screenX, screenY, screenZ};
  }
}
