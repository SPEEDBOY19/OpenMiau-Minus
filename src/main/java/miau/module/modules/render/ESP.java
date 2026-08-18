package miau.module.modules.render;

import java.awt.*;
import javax.vecmath.Vector4d;
import miau.Miau;
import miau.enums.ChatColors;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.ResizeEvent;
import miau.event.impl.TickEvent;
import miau.event.types.Priority;
import miau.mixin.IAccessorEntityRenderer;
import miau.mixin.IAccessorRenderManager;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.TeamUtil;
import miau.util.render.ColorUtil;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class ESP extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private boolean shadersAvailable = true;
  private Framebuffer framebuffer = null;
  private boolean outline = true;
  private boolean glow = true;
  public final ModeProperty mode =
      new ModeProperty(
          "mode", 2, new String[] {"NONE", "2D", "3D", "OUTLINE", "FAKECORNER", "FAKE2D"});
  public final ModeProperty color =
      new ModeProperty("color", 0, new String[] {"DEFAULT", "TEAMS", "HUD"});
  public final ModeProperty healthBar =
      new ModeProperty("health-bar", 0, new String[] {"NONE", "2D", "RAVEN"});
  public final BooleanProperty players = new BooleanProperty("players", true);
  public final BooleanProperty friends = new BooleanProperty("friends", true);
  public final BooleanProperty enemies = new BooleanProperty("enemies", true);
  public final BooleanProperty self = new BooleanProperty("self", false);
  public final BooleanProperty bots = new BooleanProperty("bots", false);

  private final java.util.List<EntityPlayer> cachedEntities = new java.util.ArrayList<>();

  private boolean shouldRenderPlayer(EntityPlayer entityPlayer) {
    if (entityPlayer.deathTime > 0) {
      return false;
    } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
      return false;
    } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
      if (TeamUtil.isBot(entityPlayer)) {
        return this.bots.getValue();
      } else if (TeamUtil.isFriend(entityPlayer)) {
        return this.friends.getValue();
      } else {
        return TeamUtil.isTarget(entityPlayer) ? this.enemies.getValue() : this.players.getValue();
      }
    } else {
      return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
    }
  }

  private int getEntityColorInt(EntityPlayer entityPlayer) {
    if (TeamUtil.isFriend(entityPlayer)) {
      return Miau.friendManager.getColor().getRGB();
    } else if (TeamUtil.isTarget(entityPlayer)) {
      return Miau.targetManager.getColor().getRGB();
    } else {
      switch (this.color.getValue()) {
        case 0:
          return TeamUtil.getTeamColor(entityPlayer, 1.0F).getRGB();
        case 1:
          return TeamUtil.isSameTeam(entityPlayer)
              ? ChatColors.BLUE.toAwtColor()
              : ChatColors.RED.toAwtColor();
        case 2:
          return ((HUD) Miau.moduleManager.modules.get(HUD.class))
              .getColor(System.currentTimeMillis())
              .getRGB();
        default:
          return -1;
      }
    }
  }

  public ESP() {
    super("ESP", false);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled() || mc.theWorld == null) {
      return;
    }
    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (shouldRenderPlayer(player)) {
        player.ignoreFrustumCheck = true;
      }
    }
  }

  public boolean isOutlineEnabled() {
    return this.outline;
  }

  public boolean isGlowEnabled() {
    return this.glow;
  }

  @EventTarget
  public void onResize(ResizeEvent event) {
    if (this.framebuffer != null) {
      this.framebuffer.deleteFramebuffer();
    }
    this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
  }

  @EventTarget(Priority.HIGH)
  public void onRender(Render2DEvent event) {
    if (this.isEnabled()
        && (this.mode.getValue() == 1
            || this.mode.getValue() == 3
            || this.healthBar.getValue() == 1)) {
      this.cachedEntities.clear();
      for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
        if (entity instanceof EntityPlayer && this.shouldRenderPlayer((EntityPlayer) entity)) {
          this.cachedEntities.add((EntityPlayer) entity);
        }
      }
      if (!this.cachedEntities.isEmpty()) {
        if (this.mode.getValue() == 3 && false) {
          try {
            GlStateManager.pushMatrix();
            GlStateManager.pushAttrib();
            if (this.framebuffer == null) {
              this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            }
            this.framebuffer.bindFramebuffer(false);
            ((IAccessorEntityRenderer) mc.entityRenderer)
                .callSetupCameraTransform(event.getPartialTicks(), 0);
            boolean shadow = mc.gameSettings.entityShadows;
            mc.gameSettings.entityShadows = false;
            this.outline = false;
            this.glow = false;

            for (EntityPlayer player : this.cachedEntities) {

              boolean invisible = player.isInvisible();
              player.setInvisible(false);
              mc.getRenderManager().renderEntityStatic(player, event.getPartialTicks(), true);
              player.setInvisible(invisible);
            }

            this.glow = true;
            this.outline = true;
            mc.gameSettings.entityShadows = shadow;
            mc.entityRenderer.disableLightmap();
            mc.entityRenderer.setupOverlayRendering();
            mc.getFramebuffer().bindFramebuffer(false);

            RenderUtil.drawFramebuffer(this.framebuffer);

            this.framebuffer.framebufferClear();
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
          } catch (Throwable throwable) {
            this.shadersAvailable = false;
            this.outline = true;
            this.glow = true;
            mc.getFramebuffer().bindFramebuffer(false);
          }
        }
        if (this.mode.getValue() == 1 || this.healthBar.getValue() == 1) {
          RenderUtil.enableRenderState();
          double scaleFactor = new ScaledResolution(mc).getScaleFactor();
          double scale = 1.0 / scaleFactor;
          GlStateManager.pushMatrix();
          GlStateManager.scale(scale, scale, scale);
          for (EntityPlayer player : this.cachedEntities) {
            ((IAccessorEntityRenderer) mc.entityRenderer)
                .callSetupCameraTransform(event.getPartialTicks(), 0);
            Vector4d screenPosition = RenderUtil.projectToScreen(player, scaleFactor);
            mc.entityRenderer.setupOverlayRendering();
            if (screenPosition != null) {
              float x = (float) screenPosition.x;
              float y = (float) screenPosition.y;
              float z = (float) screenPosition.z;
              float w = (float) screenPosition.w;
              if (this.mode.getValue() == 1) {
                int color = this.getEntityColorInt(player);
                RenderUtil.drawOutlineRect(
                    x, y, z, w, 3.0F, 0, (color & 16579836) >> 2 | color & 0xFF000000);
                RenderUtil.drawOutlineRect(x, y, z, w, 1.5F, 0, color);
              }
              if (this.healthBar.getValue() == 1) {
                float heal = player.getHealth() + player.getAbsorptionAmount();
                float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                float box = (z - x) * 0.08F;
                Color healthColor = ColorUtil.getHealthBlend(percent);
                RenderUtil.drawLine(
                    x - box, y, x - box, w, 3.0F, ColorUtil.darker(healthColor, 0.2F).getRGB());
                RenderUtil.drawLine(
                    x - box, w, x - box, w + (y - w) * percent, 1.5F, healthColor.getRGB());
              }
            }
          }
          GlStateManager.popMatrix();
          RenderUtil.disableRenderState();
        }
      }
    }
  }

  @EventTarget
  public void onRender(Render3DEvent event) {
    if (this.isEnabled()
        && (this.mode.getValue() == 2
            || this.mode.getValue() == 4
            || this.mode.getValue() == 5
            || this.healthBar.getValue() == 2)) {
      RenderUtil.enableRenderState();
      this.cachedEntities.clear();
      for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
        if (entity instanceof EntityPlayer && this.shouldRenderPlayer((EntityPlayer) entity)) {
          this.cachedEntities.add((EntityPlayer) entity);
        }
      }
      for (EntityPlayer player : this.cachedEntities) {
        if (this.mode.getValue() == 2) {
          int color = this.getEntityColorInt(player);
          RenderUtil.drawEntityBoundingBox(
              player,
              (color >> 16) & 0xFF,
              (color >> 8) & 0xFF,
              color & 0xFF,
              (color >> 24) & 0xFF,
              1.5F,
              0.1F);
          GlStateManager.resetColor();
        }
        if (this.mode.getValue() == 4) {
          int color = this.getEntityColorInt(player);
          RenderUtil.drawCornerESP(
              player,
              ((color >> 16) & 0xFF) / 255.0F,
              ((color >> 8) & 0xFF) / 255.0F,
              (color & 0xFF) / 255.0F);
        }
        if (this.mode.getValue() == 5) {
          int color = this.getEntityColorInt(player);
          RenderUtil.drawFake2DESP(
              player,
              ((color >> 16) & 0xFF) / 255.0F,
              ((color >> 8) & 0xFF) / 255.0F,
              (color & 0xFF) / 255.0F);
        }
        if (this.healthBar.getValue() == 2) {
          double x =
              RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks())
                  - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
          double y =
              RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks())
                  - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()
                  - 0.1F;
          double z =
              RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks())
                  - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
          GlStateManager.pushMatrix();
          GlStateManager.translate(x, y, z);
          GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
          float heal = player.getHealth() + player.getAbsorptionAmount();
          float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
          Color healthColor = ColorUtil.getHealthBlend(percent);
          float height = player.height + 0.2F;
          RenderUtil.drawRect3D(
              0.57250005F, -0.027500002F, 0.7275F, height + 0.027500002F, Color.black.getRGB());
          RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height, Color.darkGray.getRGB());
          RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height * percent, healthColor.getRGB());
          GlStateManager.popMatrix();
        }
      }
      RenderUtil.disableRenderState();
    }
  }
}
