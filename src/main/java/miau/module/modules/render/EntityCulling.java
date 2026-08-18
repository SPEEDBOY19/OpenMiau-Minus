package miau.module.modules.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import miau.event.EventTarget;
import miau.event.impl.Render3DEvent;
import miau.event.impl.RenderLivingEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

public class EntityCulling extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final IntProperty cullingDelay = new IntProperty("Culling Delay", 2, 1, 3);
  public final ModeProperty cullingMode =
      new ModeProperty("Culling Mode", 0, new String[] {"Grouped", "Custom"});
  public final IntProperty entityCullingDis =
      new IntProperty("Culling Distance", 45, 10, 150, () -> cullingMode.getValue() == 0);
  public final IntProperty playerCullingDis =
      new IntProperty("Player Cull Distance", 45, 10, 150, () -> cullingMode.getValue() == 1);
  public final IntProperty mobCullingDis =
      new IntProperty("Mob Cull Distance", 40, 10, 150, () -> cullingMode.getValue() == 1);
  public final IntProperty passiveCullingDis =
      new IntProperty("Passive Cull Distance", 30, 10, 150, () -> cullingMode.getValue() == 1);

  private static final ConcurrentHashMap<UUID, OcclusionQuery> queries = new ConcurrentHashMap<>();
  private static final boolean SUPPORT_NEW_GL = GLContext.getCapabilities().OpenGL33;
  private int destroyTimer;

  public EntityCulling() {
    super("EntityCulling", false);
  }

  @EventTarget
  public void onRenderLiving(RenderLivingEvent e) {
    if (e.getType() == EventType.POST) return;

    EntityLivingBase entity = e.getEntity();
    if (entity == mc.thePlayer
        || entity.worldObj != mc.thePlayer.worldObj
        || entity.isInvisibleToPlayer(mc.thePlayer)) {
      return;
    }

    if (checkEntity(entity)) {
      e.setCancelled(true);
      if (!canRenderName(entity)) {
        return;
      }

      RenderManager rm = mc.getRenderManager();
      double x =
          entity.lastTickPosX
              + (entity.posX - entity.lastTickPosX)
                  * ((miau.mixin.IAccessorMinecraft) mc).getTimer().renderPartialTicks
              - ((miau.mixin.IAccessorRenderManager) rm).getRenderPosX();
      double y =
          entity.lastTickPosY
              + (entity.posY - entity.lastTickPosY)
                  * ((miau.mixin.IAccessorMinecraft) mc).getTimer().renderPartialTicks
              - ((miau.mixin.IAccessorRenderManager) rm).getRenderPosY();
      double z =
          entity.lastTickPosZ
              + (entity.posZ - entity.lastTickPosZ)
                  * ((miau.mixin.IAccessorMinecraft) mc).getTimer().renderPartialTicks
              - ((miau.mixin.IAccessorRenderManager) rm).getRenderPosZ();

      try {
        net.minecraft.client.renderer.entity.Render<Entity> renderer =
            rm.getEntityRenderObject(entity);
        if (renderer instanceof net.minecraft.client.renderer.entity.RendererLivingEntity) {
          ((net.minecraft.client.renderer.entity.RendererLivingEntity) renderer)
              .renderName((EntityLivingBase) entity, x, y, z);
        }
      } catch (Exception ex) {
      }
    }

    if (entity.isInvisible() && entity instanceof EntityPlayer) {
      e.setCancelled(true);
    }

    final float entityDistance = entity.getDistanceToEntity(mc.thePlayer);

    switch (cullingMode.getValue()) {
      case 0:
        if (entityDistance > entityCullingDis.getValue()) {
          e.setCancelled(true);
        }
        break;
      case 1:
        if (entity instanceof IMob && entityDistance > mobCullingDis.getValue()) {
          e.setCancelled(true);
        } else if ((entity instanceof EntityAnimal
                || entity instanceof EntityAmbientCreature
                || entity instanceof EntityWaterMob)
            && entityDistance > passiveCullingDis.getValue()) {
          e.setCancelled(true);
        } else if (entity instanceof EntityPlayer && entityDistance > playerCullingDis.getValue()) {
          e.setCancelled(true);
        }
        break;
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent e) {
    check();
  }

  public static boolean canRenderName(EntityLivingBase entity) {
    final EntityPlayerSP player = mc.thePlayer;
    if (entity instanceof EntityPlayer && entity != player) {
      final Team otherEntityTeam = entity.getTeam();
      final Team playerTeam = player.getTeam();

      if (otherEntityTeam != null) {
        final Team.EnumVisible teamVisibilityRule = otherEntityTeam.getNameTagVisibility();

        switch (teamVisibilityRule) {
          case NEVER:
            return false;
          case HIDE_FOR_OTHER_TEAMS:
            return playerTeam == null || otherEntityTeam.isSameTeam(playerTeam);
          case HIDE_FOR_OWN_TEAM:
            return playerTeam == null || !otherEntityTeam.isSameTeam(playerTeam);
          case ALWAYS:
          default:
            return true;
        }
      }
    }

    return Minecraft.isGuiEnabled()
        && entity != mc.getRenderManager().livingPlayer
        && ((entity instanceof EntityArmorStand) || !entity.isInvisibleToPlayer(player))
        && entity.riddenByEntity == null;
  }

  @EventTarget
  public void onTickEvent(TickEvent e) {
    if (this.destroyTimer++ < 120) {
      return;
    }

    this.destroyTimer = 0;
    WorldClient theWorld = mc.theWorld;
    if (theWorld == null) {
      return;
    }

    List<UUID> remove = new ArrayList<>();
    Set<UUID> loaded = new HashSet<>();
    for (Entity entity : theWorld.loadedEntityList) {
      loaded.add(entity.getUniqueID());
    }

    for (OcclusionQuery value : queries.values()) {
      if (loaded.contains(value.uuid)) {
        continue;
      }

      remove.add(value.uuid);
      if (value.nextQuery != 0) {
        GL15.glDeleteQueries(value.nextQuery);
      }
    }

    for (UUID uuid : remove) {
      queries.remove(uuid);
    }
  }

  private void check() {
    long delay = 0;

    switch (cullingDelay.getValue() - 1) {
      case 0:
        delay = 10;
        break;
      case 1:
        delay = 25;
        break;
      case 2:
        delay = 50;
        break;
      default:
        break;
    }
    long nanoTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    for (OcclusionQuery query : queries.values()) {
      if (query.nextQuery != 0) {
        final long queryObject =
            GL15.glGetQueryObjecti(query.nextQuery, GL15.GL_QUERY_RESULT_AVAILABLE);
        if (queryObject != 0) {
          query.occluded = GL15.glGetQueryObjecti(query.nextQuery, GL15.GL_QUERY_RESULT) == 0;
          GL15.glDeleteQueries(query.nextQuery);
          query.nextQuery = 0;
        }
      }
      if (query.nextQuery == 0 && nanoTime - query.executionTime > delay) {
        query.executionTime = nanoTime;
        query.refresh = true;
      }
    }
  }

  private static boolean checkEntity(Entity entity) {
    OcclusionQuery query = queries.computeIfAbsent(entity.getUniqueID(), OcclusionQuery::new);
    if (query.refresh) {
      query.nextQuery = getQuery();
      query.refresh = false;
      int mode = SUPPORT_NEW_GL ? GL33.GL_ANY_SAMPLES_PASSED : GL15.GL_SAMPLES_PASSED;
      GL15.glBeginQuery(mode, query.nextQuery);
      drawSelectionBoundingBox(
          entity
              .getEntityBoundingBox()
              .expand(.2, .2, .2)
              .offset(
                  -((miau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                  -((miau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                  -((miau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()));
      GL15.glEndQuery(mode);
    }

    return query.occluded;
  }

  public static void drawSelectionBoundingBox(AxisAlignedBB b) {
    GlStateManager.disableAlpha();
    GlStateManager.disableCull();
    GlStateManager.depthMask(false);
    GlStateManager.colorMask(false, false, false, false);
    final Tessellator tessellator = Tessellator.getInstance();
    final WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    worldrenderer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION);
    worldrenderer.pos(b.maxX, b.maxY, b.maxZ).endVertex();
    worldrenderer.pos(b.maxX, b.maxY, b.minZ).endVertex();
    worldrenderer.pos(b.minX, b.maxY, b.maxZ).endVertex();
    worldrenderer.pos(b.minX, b.maxY, b.minZ).endVertex();
    worldrenderer.pos(b.minX, b.minY, b.maxZ).endVertex();
    worldrenderer.pos(b.minX, b.minY, b.minZ).endVertex();
    worldrenderer.pos(b.minX, b.maxY, b.minZ).endVertex();
    worldrenderer.pos(b.minX, b.minY, b.minZ).endVertex();
    worldrenderer.pos(b.maxX, b.maxY, b.minZ).endVertex();
    worldrenderer.pos(b.maxX, b.minY, b.minZ).endVertex();
    worldrenderer.pos(b.maxX, b.maxY, b.maxZ).endVertex();
    worldrenderer.pos(b.maxX, b.minY, b.maxZ).endVertex();
    worldrenderer.pos(b.minX, b.maxY, b.maxZ).endVertex();
    worldrenderer.pos(b.minX, b.minY, b.maxZ).endVertex();
    worldrenderer.pos(b.minX, b.minY, b.maxZ).endVertex();
    worldrenderer.pos(b.maxX, b.minY, b.maxZ).endVertex();
    worldrenderer.pos(b.minX, b.minY, b.minZ).endVertex();
    worldrenderer.pos(b.maxX, b.minY, b.minZ).endVertex();
    tessellator.draw();
    GlStateManager.depthMask(true);
    GlStateManager.colorMask(true, true, true, true);
    GlStateManager.enableAlpha();
    GlStateManager.enableCull();
  }

  private static int getQuery() {
    try {
      return GL15.glGenQueries();
    } catch (Throwable throwable) {
      return 0;
    }
  }

  static class OcclusionQuery {
    private final UUID uuid;
    private int nextQuery;
    private boolean refresh = true;
    private boolean occluded;
    private long executionTime = 0;

    public OcclusionQuery(UUID uuid) {
      this.uuid = uuid;
    }
  }
}
