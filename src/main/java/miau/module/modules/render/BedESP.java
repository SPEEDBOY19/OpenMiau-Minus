package miau.module.modules.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.*;
import miau.util.render.RenderUtil;
import miau.util.render.SharedBlockHighlightCache;
import miau.util.render.Themes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class BedESP extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final float DEFENSE_AUTO_SCALE_THRESHOLD = 8.0F;

  public final FloatProperty range = new FloatProperty("Range", 10.0f, 2.0f, 200.0f);
  public final FloatProperty scanSpeed = new FloatProperty("Scan speed", 8.0f, 1.0f, 32.0f);
  public final BooleanProperty firstBed = new BooleanProperty("Only render first bed", false);
  public final BooleanProperty renderFullBlock = new BooleanProperty("Render full block", false);
  public final BooleanProperty showExposedOutline = new BooleanProperty("Exposed outline", false);
  public final BooleanProperty showDefenseLayers =
      new BooleanProperty("Show defense layers", false);
  public final BooleanProperty showDefenseTools = new BooleanProperty("Show break tools", false);
  public final BooleanProperty showDefenseCounts = new BooleanProperty("Show defense counts", true);
  public final FloatProperty defenseHeight = new FloatProperty("Defense height", 0.6f, 0.1f, 3.0f);
  public final FloatProperty defenseScale = new FloatProperty("Defense scale", 1.0f, 0.1f, 2.0f);
  public final BooleanProperty defenseAutoScale = new BooleanProperty("Auto Scale", false);

  private boolean lastDefenseToolMode;
  private final List<BlockPos[]> lastRenderedBedPairs = new ArrayList<>();

  private static final int DEFENSE_ICON_SIZE = 16;
  private static final int DEFENSE_ICON_SPACING = 18;
  private static final int DEFENSE_PADDING = 3;
  private static final float DEFENSE_BACKGROUND_RADIUS = 8.0F;
  private static final int DEFENSE_BACKGROUND_COLOR = 0x73000000;
  private static final int DEFENSE_BACKGROUND_OUTLINE_COLOR = 0x96000000;
  private static final int DEFENSE_MAX_LAYERS = 5;
  private static final float DEFENSE_AIR_RATIO_THRESHOLD = 0.2F;
  private static final float DEFENSE_BLOCK_RATIO_THRESHOLD = 0.2F;
  private static final EnumMap<EnumFacing, LayerOffsets[]> DEFENSE_OFFSETS =
      buildLayerOffsetsByFacing();

  private final List<BlockPos[]> activeBedPairs = new ArrayList<>();
  private final Map<Long, DefenseOverlaySnapshot> defenseSnapshots = new HashMap<>();
  private final Map<Long, DefenseWatchRegion> defenseWatchRegions = new HashMap<>();
  private final Map<Long, Set<Long>> watchedBedsByDefensePos = new HashMap<>();
  private final Map<Long, Set<Long>> watchedBedsByChunk = new HashMap<>();
  private final Set<Long> dirtyDefenseBeds = new HashSet<>();
  private final SharedBlockHighlightCache.UpdateListener defenseUpdateListener =
      new SharedBlockHighlightCache.UpdateListener() {
        @Override
        public void onBlockChanged(BlockPos pos, IBlockState newState) {
          markBedsDirtyForDefensePos(pos);
        }

        @Override
        public void onChunkQueued(int chunkX, int chunkZ) {
          markBedsDirtyForChunk(chunkX, chunkZ);
        }

        @Override
        public void onChunkRemoved(int chunkX, int chunkZ) {
          markBedsDirtyForChunk(chunkX, chunkZ);
        }

        @Override
        public void onCacheCleared() {
          clearDefenseState();
        }
      };

  public BedESP() {
    super("BedESP", false);
  }

  @Override
  public void onEnabled() {
    SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
    cache.addUpdateListener(defenseUpdateListener);
    cache.attachBed();
    cache.enqueueLoadedChunks();
    if (mc.renderGlobal != null) {
      mc.renderGlobal.loadRenderers();
    }
  }

  @Override
  public void onDisabled() {
    SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
    cache.removeUpdateListener(defenseUpdateListener);
    cache.detachBed();
    activeBedPairs.clear();
    lastRenderedBedPairs.clear();
    lastDefenseToolMode = false;
    clearDefenseState();
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent e) {
    activeBedPairs.clear();
    lastRenderedBedPairs.clear();
    lastDefenseToolMode = false;
    clearDefenseState();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE) return;
    SharedBlockHighlightCache.get().handlePacket(event);
  }

  public int getScanSpeedBudget() {
    return isEnabled() ? scanSpeed.getValue().intValue() : 0;
  }

  @EventTarget(Priority.HIGH)
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (!isEnabled()) return;
    if (mc.theWorld == null || mc.thePlayer == null) {
      activeBedPairs.clear();
      clearDefenseState();
      return;
    }

    SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
    cache.tickScan(getScanSpeedBudget());
    double rangeSq = range.getValue() * range.getValue();
    double px = mc.thePlayer.posX;
    double py = mc.thePlayer.posY;
    double pz = mc.thePlayer.posZ;
    List<BlockPos[]> candidatePairs = collectActiveBedPairs(cache, px, py, pz, rangeSq);

    activeBedPairs.clear();
    for (BlockPos[] pair : candidatePairs) {
      activeBedPairs.add(copyBedPair(pair));
    }

    boolean defenseToolMode = showDefenseTools.getValue();
    if (lastDefenseToolMode != defenseToolMode) {
      clearDefenseState();
      lastDefenseToolMode = defenseToolMode;
    }

    if (!showDefenseLayers.getValue()) {
      clearDefenseState();
      return;
    }

    Set<Long> activeFeet = new HashSet<>();
    for (BlockPos[] pair : candidatePairs) {
      BlockPos foot = pair[0];
      BlockPos head = pair[1];
      long footKey = foot.toLong();
      activeFeet.add(footKey);

      DefenseWatchRegion region = defenseWatchRegions.get(footKey);
      if (region == null || !region.matches(head)) {
        unregisterDefenseWatch(footKey);
        registerDefenseWatch(foot, head);
        dirtyDefenseBeds.add(footKey);
      }

      DefenseOverlaySnapshot snapshot = defenseSnapshots.get(footKey);
      if (snapshot == null || !snapshot.matches(head) || dirtyDefenseBeds.remove(footKey)) {
        defenseSnapshots.put(footKey, computeDefenseSnapshot(foot, head));
      }
    }

    for (Long footKey : new ArrayList<>(defenseWatchRegions.keySet())) {
      if (!activeFeet.contains(footKey)) {
        unregisterDefenseWatch(footKey);
      }
    }

    dirtyDefenseBeds.retainAll(activeFeet);
  }

  @Override
  public String[] getSuffix() {
    if (!isEnabled()) return new String[0];
    int n = SharedBlockHighlightCache.get().totalBedFeet();
    return n > 0 ? new String[] {String.valueOf(n)} : new String[0];
  }

  @EventTarget(Priority.LOWEST)
  public void onRender3D(Render3DEvent event) {
    if (!isEnabled()) return;
    if (mc.theWorld == null || mc.thePlayer == null) return;

    float blockHeight = getBlockHeight();
    double rangeSq = range.getValue() * range.getValue();
    double px = mc.thePlayer.posX;
    double py = mc.thePlayer.posY;
    double pz = mc.thePlayer.posZ;

    List<BlockPos[]> pairsToRender = new ArrayList<>();
    Set<BlockPos> addedFeet = new HashSet<>();

    for (BlockPos[] pair : activeBedPairs) {
      BlockPos foot = pair[0];
      AxisAlignedBB bb = bedWorldBounds(pair[0], pair[1], blockHeight);
      if (!RenderUtil.isInViewFrustum(bb)) continue;
      if (addedFeet.add(foot)) {
        pairsToRender.add(copyBedPair(pair));
      }
    }

    for (BlockPos[] prev : new ArrayList<>(lastRenderedBedPairs)) {
      if (prev == null || prev.length < 2) continue;
      BlockPos foot = prev[0];
      BlockPos head = prev[1];
      if (addedFeet.contains(foot)) continue;
      if (!stillHasRenderableBed(prev)) continue;
      double dx = foot.getX() + 0.5 - px;
      double dy = foot.getY() + 0.5 - py;
      double dz = foot.getZ() + 0.5 - pz;
      if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
      AxisAlignedBB bb = bedWorldBounds(foot, head, blockHeight);
      if (!RenderUtil.isInViewFrustum(bb)) continue;
      pairsToRender.add(copyBedPair(prev));
      addedFeet.add(foot);
    }

    for (BlockPos[] pair : pairsToRender) {
      renderBed(pair, blockHeight);
      if (showDefenseLayers.getValue() && isLiveBedPair(pair)) {
        renderDefenseOverlay(pair, blockHeight);
      }
    }

    lastRenderedBedPairs.clear();
    for (BlockPos[] pair : pairsToRender) {
      lastRenderedBedPairs.add(copyBedPair(pair));
    }
  }

  private List<BlockPos[]> collectActiveBedPairs(
      SharedBlockHighlightCache cache, double px, double py, double pz, double rangeSq) {
    List<BlockPos[]> candidatePairs = new ArrayList<>();

    for (Map.Entry<Long, Set<BlockPos>> chunk : cache.entriesBedFeet()) {
      for (BlockPos foot : chunk.getValue()) {
        double dx = foot.getX() + 0.5 - px;
        double dy = foot.getY() + 0.5 - py;
        double dz = foot.getZ() + 0.5 - pz;
        if (dx * dx + dy * dy + dz * dz > rangeSq) {
          continue;
        }

        BlockPos[] pair = footAndHead(foot);
        if (pair == null || !stillHasRenderableBed(pair)) {
          continue;
        }

        candidatePairs.add(copyBedPair(pair));
      }
    }

    if (!firstBed.getValue() || candidatePairs.size() <= 1) {
      return candidatePairs;
    }

    BlockPos[] nearest = null;
    double nearestDistanceSq = Double.MAX_VALUE;
    for (BlockPos[] pair : candidatePairs) {
      double dx = pair[0].getX() + 0.5 - px;
      double dy = pair[0].getY() + 0.5 - py;
      double dz = pair[0].getZ() + 0.5 - pz;
      double distanceSq = dx * dx + dy * dy + dz * dz;
      if (distanceSq < nearestDistanceSq) {
        nearestDistanceSq = distanceSq;
        nearest = pair;
      }
    }

    candidatePairs.clear();
    if (nearest != null) {
      candidatePairs.add(nearest);
    }
    return candidatePairs;
  }

  private static BlockPos[] copyBedPair(BlockPos[] pair) {
    return new BlockPos[] {new BlockPos(pair[0]), new BlockPos(pair[1])};
  }

  private static boolean isBedFoot(IBlockState st) {
    return st != null
        && st.getBlock() instanceof BlockBed
        && st.getValue((IProperty) BlockBed.PART) == BlockBed.EnumPartType.FOOT;
  }

  private boolean stillHasRenderableBed(BlockPos[] pair) {
    if (pair == null || pair.length < 2 || mc.theWorld == null) {
      return false;
    }
    IBlockState a = mc.theWorld.getBlockState(pair[0]);
    IBlockState b = mc.theWorld.getBlockState(pair[1]);
    return a != null && a.getBlock() instanceof BlockBed
        || b != null && b.getBlock() instanceof BlockBed;
  }

  private boolean isLiveBedPair(BlockPos[] pair) {
    if (pair == null || pair.length < 2 || mc.theWorld == null) {
      return false;
    }
    IBlockState footState = mc.theWorld.getBlockState(pair[0]);
    IBlockState headState = mc.theWorld.getBlockState(pair[1]);
    return isBedFoot(footState) && headState != null && headState.getBlock() instanceof BlockBed;
  }

  private static AxisAlignedBB bedWorldBounds(BlockPos foot, BlockPos head, float height) {
    int fx = foot.getX(), fy = foot.getY(), fz = foot.getZ();
    double h = fy + height;
    if (foot.getX() != head.getX()) {
      if (foot.getX() > head.getX()) {
        return new AxisAlignedBB(fx - 1.0, fy, fz, fx + 1.0, h, fz + 1.0);
      }
      return new AxisAlignedBB(fx, fy, fz, fx + 2.0, h, fz + 1.0);
    }
    if (foot.getZ() > head.getZ()) {
      return new AxisAlignedBB(fx, fy, fz - 1.0, fx + 1.0, h, fz + 1.0);
    }
    return new AxisAlignedBB(fx, fy, fz, fx + 1.0, h, fz + 2.0);
  }

  private BlockPos[] footAndHead(BlockPos foot) {
    IBlockState st = mc.theWorld.getBlockState(foot);
    if (!(st.getBlock() instanceof BlockBed)) {
      return null;
    }
    EnumFacing facing = (EnumFacing) st.getValue((IProperty) BlockBed.FACING);
    return new BlockPos[] {foot, foot.offset(facing)};
  }

  private void renderBed(BlockPos[] blocks, float height) {
    boolean exposed = showExposedOutline.getValue() && isBedExposed(blocks);
    double x = blocks[0].getX() - mc.getRenderManager().viewerPosX;
    double y = blocks[0].getY() - mc.getRenderManager().viewerPosY;
    double z = blocks[0].getZ() - mc.getRenderManager().viewerPosZ;
    GL11.glBlendFunc(770, 771);
    GL11.glEnable(3042);
    GL11.glLineWidth(2.0f);
    GL11.glDisable(3553);
    GL11.glDisable(2929);
    GL11.glDepthMask(false);
    java.awt.Color themeColor = Themes.getCurrentTheme().getAccentColor();
    float drawA = 64f / 255f;
    float r = themeColor.getRed() / 255.0f;
    float g = themeColor.getGreen() / 255.0f;
    float b = themeColor.getBlue() / 255.0f;
    GL11.glColor4d(r, g, b, drawA);
    AxisAlignedBB axisAlignedBB;
    if (blocks[0].getX() != blocks[1].getX()) {
      if (blocks[0].getX() > blocks[1].getX()) {
        axisAlignedBB = new AxisAlignedBB(x - 1.0, y, z, x + 1.0, y + height, z + 1.0);
      } else {
        axisAlignedBB = new AxisAlignedBB(x, y, z, x + 2.0, y + height, z + 1.0);
      }
    } else if (blocks[0].getZ() > blocks[1].getZ()) {
      axisAlignedBB = new AxisAlignedBB(x, y, z - 1.0, x + 1.0, y + height, z + 1.0);
    } else {
      axisAlignedBB = new AxisAlignedBB(x, y, z, x + 1.0, y + height, z + 2.0);
    }

    RenderUtil.drawBoundingBox(axisAlignedBB, r, g, b, drawA);
    if (exposed) {
      java.awt.Color outlineColor = Themes.getCurrentTheme().getAccentColor();
      GL11.glLineWidth(3.0f);
      GL11.glColor4f(
          outlineColor.getRed() / 255.0f,
          outlineColor.getGreen() / 255.0f,
          outlineColor.getBlue() / 255.0f,
          1.0f);
      RenderGlobal.drawSelectionBoundingBox(axisAlignedBB);
      GL11.glLineWidth(2.0f);
    }
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    GL11.glEnable(3553);
    GL11.glEnable(2929);
    GL11.glDepthMask(true);
    GL11.glDisable(3042);
  }

  private boolean isBedExposed(BlockPos[] pair) {
    if (pair == null || pair.length < 2 || mc.theWorld == null) {
      return false;
    }
    for (BlockPos bedPart : pair) {
      for (EnumFacing side : EnumFacing.values()) {
        BlockPos neighbor = bedPart.offset(side);
        Block neighborBlock = mc.theWorld.getBlockState(neighbor).getBlock();
        if (neighborBlock == Blocks.air) {
          return true;
        }
      }
    }
    return false;
  }

  private void renderDefenseOverlay(BlockPos[] blocks, float blockHeight) {
    DefenseOverlaySnapshot snapshot = defenseSnapshots.get(blocks[0].toLong());
    if (snapshot == null || !snapshot.matches(blocks[1]) || snapshot.entries.isEmpty()) {
      return;
    }

    RenderManager renderManager = mc.getRenderManager();
    FontRenderer fontRenderer = mc.fontRendererObj;
    if (renderManager == null || fontRenderer == null) {
      return;
    }

    AxisAlignedBB bedBounds = bedWorldBounds(blocks[0], blocks[1], blockHeight);
    double x = (bedBounds.minX + bedBounds.maxX) * 0.5 - renderManager.viewerPosX;
    double y = bedBounds.maxY + defenseHeight.getValue() - renderManager.viewerPosY;
    double z = (bedBounds.minZ + bedBounds.maxZ) * 0.5 - renderManager.viewerPosZ;
    float renderScale = computeDefenseBaseScaleValue();
    if (defenseAutoScale.getValue()) {
      float distance = (float) Math.sqrt(x * x + y * y + z * z);
      renderScale = computeDefenseScaleValue(distance);
    }
    List<DefenseOverlayEntry> stacks = snapshot.entries;
    int contentWidth =
        stacks.size() * DEFENSE_ICON_SPACING - (DEFENSE_ICON_SPACING - DEFENSE_ICON_SIZE);
    int left = -contentWidth / 2;
    int iconY = -8;
    int backgroundLeft = left - DEFENSE_PADDING;
    int backgroundTop = iconY - DEFENSE_PADDING;
    int backgroundRight = left + contentWidth + DEFENSE_PADDING;
    int backgroundBottom = iconY + DEFENSE_ICON_SIZE + DEFENSE_PADDING;

    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate((float) x, (float) y, (float) z);
      GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
      GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
      GlStateManager.scale(-renderScale, -renderScale, renderScale);

      GlStateManager.disableLighting();
      GlStateManager.depthMask(false);
      GlStateManager.disableDepth();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

      renderDefenseBackground(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom);
      applyDefenseOverlayTextState();

      for (int i = 0; i < stacks.size(); i++) {
        DefenseOverlayEntry stackData = stacks.get(i);
        int iconX = left + i * DEFENSE_ICON_SPACING;
        renderDefenseEntry(stackData, iconX, iconY);
        applyDefenseOverlayTextState();

        if (!showDefenseTools.getValue() && showDefenseCounts.getValue() && stackData.count > 1) {
          String countText = String.valueOf(stackData.getCount());
          fontRenderer.drawStringWithShadow(
              countText, iconX + 17 - fontRenderer.getStringWidth(countText), iconY + 9, 0xFFFFFF);
          applyDefenseOverlayTextState();
        }
      }
    } finally {
      GlStateManager.enableDepth();
      GlStateManager.depthMask(true);
      GlStateManager.disableLighting();
      GlStateManager.disableRescaleNormal();
      GlStateManager.disableBlend();
      GlStateManager.enableAlpha();
      GlStateManager.enableTexture2D();
      GlStateManager.tryBlendFuncSeparate(
          GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
      GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GlStateManager.popMatrix();
    }
  }

  private void renderDefenseEntry(DefenseOverlayEntry entry, int iconX, int iconY) {
    if (entry == null) {
      return;
    }

    if (entry.hasItemStack()) {
      RenderUtil.renderItemAndEffectIntoGui3D(entry.renderStack, iconX, iconY);
      return;
    }

    if (entry.hasBlockSprite()) {
      renderDefenseBlockSprite(entry.blockSprite, iconX, iconY);
    }
  }

  private void renderDefenseBlockSprite(TextureAtlasSprite sprite, int iconX, int iconY) {
    if (sprite == null) {
      return;
    }

    mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    GlStateManager.enableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.enableAlpha();
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldRenderer = tessellator.getWorldRenderer();
    worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
    worldRenderer
        .pos(iconX, iconY + DEFENSE_ICON_SIZE, 0.0D)
        .tex(sprite.getMinU(), sprite.getMaxV())
        .endVertex();
    worldRenderer
        .pos(iconX + DEFENSE_ICON_SIZE, iconY + DEFENSE_ICON_SIZE, 0.0D)
        .tex(sprite.getMaxU(), sprite.getMaxV())
        .endVertex();
    worldRenderer
        .pos(iconX + DEFENSE_ICON_SIZE, iconY, 0.0D)
        .tex(sprite.getMaxU(), sprite.getMinV())
        .endVertex();
    worldRenderer.pos(iconX, iconY, 0.0D).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
    tessellator.draw();
  }

  private void renderDefenseBackground(int left, int top, int right, int bottom) {
    RenderUtil.drawRoundedGradientOutlinedRectangle(
        left,
        top,
        right,
        bottom,
        DEFENSE_BACKGROUND_RADIUS,
        DEFENSE_BACKGROUND_COLOR,
        DEFENSE_BACKGROUND_OUTLINE_COLOR,
        DEFENSE_BACKGROUND_OUTLINE_COLOR);
  }

  private void applyDefenseOverlayTextState() {
    GlStateManager.disableLighting();
    GlStateManager.disableDepth();
    GlStateManager.depthMask(false);
    GlStateManager.enableTexture2D();
    GlStateManager.enableAlpha();
    GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(
        GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
  }

  private DefenseOverlaySnapshot computeDefenseSnapshot(BlockPos foot, BlockPos head) {
    LayerOffsets[] layers = getLayerOffsets(foot, head);
    if (layers == null) {
      return new DefenseOverlaySnapshot(
          new BlockPos(head), Collections.<DefenseOverlayEntry>emptyList());
    }

    Map<DefenseBlockKey, Integer> finalCounts = new HashMap<>();
    Map<Integer, DefenseBlockKey> resolvedBlockKeys = new HashMap<>();
    int sparseLayerCount = 0;
    BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    for (LayerOffsets layerOffsets : layers) {
      Map<DefenseBlockKey, Integer> layerCounts = new HashMap<>();
      int layerTotalBlocks = 0;
      int layerAirBlocks = 0;

      for (RelativeOffset offset : layerOffsets.positions) {
        mutablePos.set(foot.getX() + offset.x, foot.getY() + offset.y, foot.getZ() + offset.z);
        if (accumulateDefenseBlock(mutablePos, layerCounts, resolvedBlockKeys)) {
          layerAirBlocks++;
        }
        layerTotalBlocks++;
      }

      if (layerTotalBlocks == 0
          || (float) layerAirBlocks / (float) layerTotalBlocks > DEFENSE_AIR_RATIO_THRESHOLD) {
        if (++sparseLayerCount >= 2) {
          break;
        }
        continue;
      }

      sparseLayerCount = 0;
      for (Map.Entry<DefenseBlockKey, Integer> countedBlock : layerCounts.entrySet()) {
        int count = countedBlock.getValue();
        if ((float) count / (float) layerTotalBlocks >= DEFENSE_BLOCK_RATIO_THRESHOLD) {
          finalCounts.put(
              countedBlock.getKey(), finalCounts.getOrDefault(countedBlock.getKey(), 0) + count);
        }
      }
    }

    List<DefenseOverlayEntry> entries;
    if (showDefenseTools.getValue()) {
      Set<ToolOverlayType> toolTypes = new HashSet<>();
      for (DefenseBlockKey countedBlock : finalCounts.keySet()) {
        ToolOverlayType toolType = ToolOverlayType.fromState(countedBlock.state);
        if (toolType != null) {
          toolTypes.add(toolType);
        }
      }
      if (toolTypes.contains(ToolOverlayType.DIAMOND_PICKAXE)) {
        toolTypes.remove(ToolOverlayType.IRON_PICKAXE);
      }

      entries = new ArrayList<>(toolTypes.size());
      for (ToolOverlayType toolType : toolTypes) {
        entries.add(
            new DefenseOverlayEntry(toolType.createRenderStack(), null, 1, toolType.sortName));
      }
      entries.sort((left, right) -> left.sortName.compareToIgnoreCase(right.sortName));
    } else {
      entries = new ArrayList<>();
      for (Map.Entry<DefenseBlockKey, Integer> countedBlock : finalCounts.entrySet()) {
        entries.add(
            new DefenseOverlayEntry(
                countedBlock.getKey().createRenderStack(),
                countedBlock.getKey().blockSprite,
                countedBlock.getValue(),
                countedBlock.getKey().sortName));
      }
      entries.sort(
          (left, right) -> {
            int countCompare = Integer.compare(right.count, left.count);
            if (countCompare != 0) {
              return countCompare;
            }
            return left.sortName.compareToIgnoreCase(right.sortName);
          });
    }

    return new DefenseOverlaySnapshot(new BlockPos(head), Collections.unmodifiableList(entries));
  }

  private boolean accumulateDefenseBlock(
      BlockPos pos,
      Map<DefenseBlockKey, Integer> layerCounts,
      Map<Integer, DefenseBlockKey> resolvedBlockKeys) {
    IBlockState state = mc.theWorld.getBlockState(pos);
    if (state == null || state.getBlock() == Blocks.air) {
      return true;
    }

    IBlockState normalizedState = normalizeDefenseState(state);
    int stateId = Block.getStateId(normalizedState);
    DefenseBlockKey key = resolvedBlockKeys.get(stateId);
    if (key == null) {
      key = DefenseBlockKey.from(normalizedState, pos, mc.theWorld);
      resolvedBlockKeys.put(stateId, key);
    }
    layerCounts.put(key, layerCounts.getOrDefault(key, 0) + 1);
    return false;
  }

  private LayerOffsets[] getLayerOffsets(BlockPos foot, BlockPos head) {
    EnumFacing facing = getBedFacing(foot, head);
    return facing == null ? null : DEFENSE_OFFSETS.get(facing);
  }

  private static EnumMap<EnumFacing, LayerOffsets[]> buildLayerOffsetsByFacing() {
    EnumMap<EnumFacing, LayerOffsets[]> offsets = new EnumMap<>(EnumFacing.class);
    for (EnumFacing f : EnumFacing.HORIZONTALS) {
      offsets.put(f, buildLayerOffsets(f));
    }
    return offsets;
  }

  private static LayerOffsets[] buildLayerOffsets(EnumFacing canonicalFacing) {
    BlockPos foot = BlockPos.ORIGIN;
    BlockPos head = foot.offset(canonicalFacing);
    boolean facingZ = canonicalFacing.getAxis() == EnumFacing.Axis.Z;
    BlockPos firstBedPart =
        facingZ
            ? (head.getZ() > foot.getZ() ? head : foot)
            : (head.getX() > foot.getX() ? head : foot);
    BlockPos secondBedPart = firstBedPart.equals(foot) ? head : foot;
    BlockPos[] bedParts = {firstBedPart, secondBedPart};
    LayerOffsets[] layers = new LayerOffsets[DEFENSE_MAX_LAYERS];
    Set<Long> seenAcrossLayers = new HashSet<>();

    for (int layer = 1; layer <= DEFENSE_MAX_LAYERS; layer++) {
      List<RelativeOffset> offsets = new ArrayList<>();

      for (int bedIndex = 0; bedIndex < bedParts.length; bedIndex++) {
        BlockPos bed = bedParts[bedIndex];
        int outwardOffset = bedIndex == 0 ? layer : -layer;
        int startX = facingZ ? bed.getX() : bed.getX() + outwardOffset;
        int startZ = facingZ ? bed.getZ() + outwardOffset : bed.getZ();

        for (int advance = 0; advance <= layer; advance++) {
          int yOffset = 0;
          for (int breadth = advance; breadth >= 0; breadth--) {
            int firstX;
            int firstY = bed.getY() + yOffset;
            int firstZ;
            int secondX;
            int secondY = firstY;
            int secondZ;

            if (facingZ) {
              int z = startZ - (bedIndex == 0 ? advance : -advance);
              firstX = startX - breadth;
              firstZ = z;
              secondX = startX + breadth;
              secondZ = z;
            } else {
              int x = startX - (bedIndex == 0 ? advance : -advance);
              firstX = x;
              firstZ = startZ - breadth;
              secondX = x;
              secondZ = startZ + breadth;
            }

            addOffset(offsets, seenAcrossLayers, firstX, firstY, firstZ);
            addOffset(offsets, seenAcrossLayers, secondX, secondY, secondZ);

            if (breadth > 0) {
              yOffset++;
            }
          }
        }
      }

      layers[layer - 1] = new LayerOffsets(Collections.unmodifiableList(offsets));
    }

    return layers;
  }

  private static void addOffset(List<RelativeOffset> offsets, Set<Long> seen, int x, int y, int z) {
    long key = new BlockPos(x, y, z).toLong();
    if (seen.add(key)) {
      offsets.add(new RelativeOffset(x, y, z));
    }
  }

  private static EnumFacing getBedFacing(BlockPos foot, BlockPos head) {
    int dx = head.getX() - foot.getX();
    int dz = head.getZ() - foot.getZ();
    for (EnumFacing facing : EnumFacing.HORIZONTALS) {
      if (facing.getFrontOffsetX() == dx && facing.getFrontOffsetZ() == dz) {
        return facing;
      }
    }
    return null;
  }

  private void clearDefenseState() {
    defenseSnapshots.clear();
    defenseWatchRegions.clear();
    watchedBedsByDefensePos.clear();
    watchedBedsByChunk.clear();
    dirtyDefenseBeds.clear();
  }

  private void markBedsDirtyForDefensePos(BlockPos pos) {
    if (pos == null) {
      return;
    }

    Set<Long> feet = watchedBedsByDefensePos.get(pos.toLong());
    if (feet == null) {
      return;
    }

    dirtyDefenseBeds.addAll(feet);
  }

  private void markBedsDirtyForChunk(int chunkX, int chunkZ) {
    Set<Long> feet = watchedBedsByChunk.get(chunkKey(chunkX, chunkZ));
    if (feet == null) {
      return;
    }

    dirtyDefenseBeds.addAll(feet);
  }

  private void registerDefenseWatch(BlockPos foot, BlockPos head) {
    LayerOffsets[] layers = getLayerOffsets(foot, head);
    long footKey = foot.toLong();
    if (layers == null) {
      defenseWatchRegions.put(
          footKey,
          new DefenseWatchRegion(
              head.toLong(), Collections.<Long>emptySet(), Collections.<Long>emptySet()));
      return;
    }

    Set<Long> watchedPositions = new HashSet<>();
    Set<Long> watchedChunks = new HashSet<>();

    for (LayerOffsets layer : layers) {
      for (RelativeOffset offset : layer.positions) {
        BlockPos watchedPos =
            new BlockPos(foot.getX() + offset.x, foot.getY() + offset.y, foot.getZ() + offset.z);
        long posKey = watchedPos.toLong();
        if (!watchedPositions.add(posKey)) {
          continue;
        }

        watchedBedsByDefensePos.computeIfAbsent(posKey, ignored -> new HashSet<>()).add(footKey);
        watchedChunks.add(chunkKey(watchedPos.getX() >> 4, watchedPos.getZ() >> 4));
      }
    }

    for (Long chunkKey : watchedChunks) {
      watchedBedsByChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(footKey);
    }

    defenseWatchRegions.put(
        footKey,
        new DefenseWatchRegion(
            head.toLong(),
            Collections.unmodifiableSet(watchedPositions),
            Collections.unmodifiableSet(watchedChunks)));
  }

  private void unregisterDefenseWatch(long footKey) {
    DefenseWatchRegion region = defenseWatchRegions.remove(footKey);
    defenseSnapshots.remove(footKey);
    dirtyDefenseBeds.remove(footKey);

    if (region == null) {
      return;
    }

    for (Long posKey : region.watchedPositions) {
      Set<Long> feet = watchedBedsByDefensePos.get(posKey);
      if (feet == null) {
        continue;
      }
      feet.remove(footKey);
      if (feet.isEmpty()) {
        watchedBedsByDefensePos.remove(posKey);
      }
    }

    for (Long chunkKey : region.watchedChunks) {
      Set<Long> feet = watchedBedsByChunk.get(chunkKey);
      if (feet == null) {
        continue;
      }
      feet.remove(footKey);
      if (feet.isEmpty()) {
        watchedBedsByChunk.remove(chunkKey);
      }
    }
  }

  private static long chunkKey(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
  }

  public float getBlockHeight() {
    return renderFullBlock.getValue() ? 1 : 0.5625F;
  }

  private float computeDefenseBaseScaleValue() {
    return (float) defenseScale.getValue() * 0.02F;
  }

  private float computeDefenseScaleValue(float distance) {
    float baseScale = computeDefenseBaseScaleValue();
    float effectiveDistance = Math.max(1.0F, distance);
    float scaledValue = baseScale * (effectiveDistance / DEFENSE_AUTO_SCALE_THRESHOLD);
    return Math.max(baseScale, scaledValue);
  }

  private static final class DefenseOverlaySnapshot {
    private final long headKey;
    private final List<DefenseOverlayEntry> entries;

    private DefenseOverlaySnapshot(BlockPos head, List<DefenseOverlayEntry> entries) {
      this.headKey = head.toLong();
      this.entries = entries;
    }

    private boolean matches(BlockPos otherHead) {
      return otherHead != null && headKey == otherHead.toLong();
    }
  }

  private static final class DefenseOverlayEntry {
    private final ItemStack renderStack;
    private final TextureAtlasSprite blockSprite;
    private final int count;
    private final String sortName;

    private DefenseOverlayEntry(
        ItemStack renderStack, TextureAtlasSprite blockSprite, int count, String sortName) {
      this.renderStack = renderStack;
      this.blockSprite = blockSprite;
      this.count = count;
      this.sortName = sortName;
    }

    private boolean hasItemStack() {
      return renderStack != null && renderStack.getItem() != null;
    }

    private boolean hasBlockSprite() {
      return blockSprite != null;
    }

    private int getCount() {
      return count;
    }
  }

  private static final class DefenseWatchRegion {
    private final long headKey;
    private final Set<Long> watchedPositions;
    private final Set<Long> watchedChunks;

    private DefenseWatchRegion(long headKey, Set<Long> watchedPositions, Set<Long> watchedChunks) {
      this.headKey = headKey;
      this.watchedPositions = watchedPositions;
      this.watchedChunks = watchedChunks;
    }

    private boolean matches(BlockPos otherHead) {
      return otherHead != null && headKey == otherHead.toLong();
    }
  }

  private static final class DefenseBlockKey {
    private final IBlockState state;
    private final String identityKey;
    private final String sortName;
    private final int hashCode;
    private final ItemStack renderStack;
    private final TextureAtlasSprite blockSprite;

    private DefenseBlockKey(
        IBlockState state,
        String identityKey,
        String sortName,
        ItemStack renderStack,
        TextureAtlasSprite blockSprite) {
      this.state = state;
      this.identityKey = identityKey;
      this.sortName = sortName;
      this.renderStack = renderStack;
      this.blockSprite = blockSprite;
      this.hashCode = identityKey.hashCode();
    }

    private static DefenseBlockKey from(IBlockState state, BlockPos pos, World world) {
      String fallbackName = getFallbackStateName(state);
      ItemStack stack = resolveBlockItemStack(state, pos, world);
      String identityKey = resolveIdentityKey(state, stack);
      TextureAtlasSprite sprite = null;

      if (stack == null || stack.getItem() == null) {
        sprite = resolveBlockSprite(state);
        if (sprite == null) {
          stack = fallbackRenderStack(state.getBlock());
        } else {
          stack = null;
        }
      }

      String sortName =
          stack != null && stack.getItem() != null
              ? getSafeDisplayName(stack, fallbackName)
              : fallbackName;
      return new DefenseBlockKey(state, identityKey, sortName, stack, sprite);
    }

    private ItemStack createRenderStack() {
      return renderStack == null ? null : renderStack.copy();
    }

    private static String resolveIdentityKey(IBlockState state, ItemStack stack) {
      if (stack != null && stack.getItem() != null) {
        return Item.getIdFromItem(stack.getItem()) + ":" + stack.getMetadata();
      }
      Object registryName = Block.blockRegistry.getNameForObject(state.getBlock());
      return registryName != null
          ? registryName.toString()
          : Integer.toString(Block.getIdFromBlock(state.getBlock()));
    }

    private static String getSafeDisplayName(ItemStack stack, String fallback) {
      if (stack == null || stack.getItem() == null) {
        return fallback;
      }
      try {
        String displayName = stack.getDisplayName();
        return displayName != null && !displayName.isEmpty() ? displayName : fallback;
      } catch (Exception ignored) {
        return fallback;
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof DefenseBlockKey)) {
        return false;
      }
      DefenseBlockKey other = (DefenseBlockKey) obj;
      return identityKey.equals(other.identityKey);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }

  private enum ToolOverlayType {
    DIAMOND_PICKAXE(Items.diamond_pickaxe, "Diamond Pickaxe"),
    IRON_AXE(Items.iron_axe, "Iron Axe"),
    IRON_HOE(Items.iron_hoe, "Iron Hoe"),
    IRON_PICKAXE(Items.iron_pickaxe, "Iron Pickaxe"),
    SHEARS(Items.shears, "Shears"),
    IRON_SHOVEL(Items.iron_shovel, "Iron Shovel"),
    IRON_SWORD(Items.iron_sword, "Iron Sword");

    private final Item item;
    private final String sortName;
    private final ItemStack renderStack;

    ToolOverlayType(Item item, String sortName) {
      this.item = item;
      this.sortName = sortName;
      this.renderStack = new ItemStack(item);
    }

    private ItemStack createRenderStack() {
      return renderStack.copy();
    }

    private static ToolOverlayType fromState(IBlockState state) {
      if (state == null) {
        return null;
      }

      Block block = state.getBlock();
      if (block == Blocks.obsidian) {
        return DIAMOND_PICKAXE;
      }

      ToolOverlayType bestTool = IRON_PICKAXE;
      float bestEfficiency = 1.0F;
      for (ToolOverlayType toolType : values()) {
        if (toolType == DIAMOND_PICKAXE) {
          continue;
        }
        float efficiency = getEfficiency(toolType.renderStack, block);
        if (efficiency > bestEfficiency) {
          bestEfficiency = efficiency;
          bestTool = toolType;
        }
      }
      return bestTool;
    }

    private static float getEfficiency(final ItemStack itemStack, final Block block) {
      float getStrVsBlock = itemStack.getStrVsBlock(block);
      if (getStrVsBlock > 1.0f) {
        final int getEnchantmentLevel =
            net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
                net.minecraft.enchantment.Enchantment.efficiency.effectId, itemStack);
        if (getEnchantmentLevel > 0) {
          getStrVsBlock += getEnchantmentLevel * getEnchantmentLevel + 1;
        }
      }
      return getStrVsBlock;
    }
  }

  private static ItemStack resolveBlockItemStack(IBlockState state, BlockPos pos, World world) {
    Block block = state.getBlock();
    try {
      Item item = block.getItem(world, pos);
      if (item == null) {
        return null;
      }
      int meta = item.getHasSubtypes() ? block.getDamageValue(world, pos) : 0;
      return new ItemStack(item, 1, meta);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static TextureAtlasSprite resolveBlockSprite(IBlockState state) {
    if (mc == null
        || mc.getBlockRendererDispatcher() == null
        || mc.getBlockRendererDispatcher().getBlockModelShapes() == null) {
      return null;
    }
    TextureAtlasSprite sprite =
        mc.getBlockRendererDispatcher().getBlockModelShapes().getTexture(state);
    return sprite;
  }

  private static IBlockState normalizeDefenseState(IBlockState state) {
    if (state == null) {
      return null;
    }
    Block block = state.getBlock();
    if (block == Blocks.water || block == Blocks.flowing_water) {
      return Blocks.water.getDefaultState();
    }
    if (block == Blocks.lava || block == Blocks.flowing_lava) {
      return Blocks.lava.getDefaultState();
    }
    if (block == Blocks.fire) {
      return Blocks.fire.getDefaultState();
    }
    return state;
  }

  private static String getFallbackStateName(IBlockState state) {
    String localizedName = state.getBlock().getLocalizedName();
    if (localizedName != null && !localizedName.isEmpty()) {
      return localizedName;
    }
    Object registryName = Block.blockRegistry.getNameForObject(state.getBlock());
    if (registryName != null) {
      int meta = state.getBlock().getMetaFromState(state);
      return meta != 0 ? registryName + ":" + meta : registryName.toString();
    }
    return "unknown";
  }

  private static ItemStack fallbackRenderStack(Block block) {
    if (block == Blocks.bed) {
      return new ItemStack(Items.bed);
    }
    try {
      Item item = Item.getItemFromBlock(block);
      if (item != null) {
        int meta = block.getMetaFromState(block.getDefaultState());
        return new ItemStack(item, 1, meta);
      }
    } catch (Exception ignored) {
    }
    return new ItemStack(Blocks.barrier);
  }

  private static final class LayerOffsets {
    private final List<RelativeOffset> positions;

    private LayerOffsets(List<RelativeOffset> positions) {
      this.positions = positions;
    }
  }

  private static final class RelativeOffset {
    private final int x;
    private final int y;
    private final int z;

    private RelativeOffset(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }
}
