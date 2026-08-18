package miau.module.modules.render;

import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.event.impl.UpdateEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.FloatProperty;
import miau.util.render.RenderUtil;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class Radar extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DragProperty dragging = new DragProperty("Radar", new Vector2d(60, 60));

    public final BooleanProperty tracerLines = new BooleanProperty("Show tracer lines", false);
    public final BooleanProperty showNames = new BooleanProperty("Show Player Names", true);
    public final BooleanProperty showHeads = new BooleanProperty("Show Player Heads", true);
    public final FloatProperty radarZoom = new FloatProperty("Zoom", 1.0f, 0.5f, 5.0f, 0.1f);

    private int scale = 2;
    private static final int RECT_COLOR = new Color(0, 0, 0, 145).getRGB();

    public Radar() {
        super("Radar", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc == null || mc.gameSettings == null) return;
        this.scale = new ScaledResolution(mc).getScaleFactor();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.gameSettings.showDebugInfo) return;

        int radius = 50;
        int segments = 64;
        double maxRenderDist = radius - 9.0;
        float pxPerBlock = radarZoom.getValue().floatValue();

        float topLeftX = (float) this.dragging.position.x;
        float topLeftY = (float) this.dragging.position.y;
        this.dragging.scale.x = radius * 2;
        this.dragging.scale.y = radius * 2;

        float centerX = topLeftX + radius;
        float centerY = topLeftY + radius;

        RenderUtil.fillCircle(centerX, centerY, radius, segments, RECT_COLOR);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glLineWidth(1.5f);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.7f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= segments; i++) {
            double angle = i * (Math.PI * 2.0 / segments);
            GL11.glVertex2d(centerX + Math.cos(angle) * radius, centerY + Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        Gui.drawRect((int) centerX - 1, (int) centerY - 1, (int) centerX + 2, (int) centerY + 2, -1);

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (centerX - radius) * this.scale, mc.displayHeight - this.scale * (int) (centerY + radius), (2 * radius) * this.scale, (2 * radius) * this.scale);

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != mc.thePlayer && player.deathTime == 0 && !player.isInvisible()) {
                double distanceSquared = player.getDistanceSqToEntity(mc.thePlayer);

                double playerAngle = (mc.thePlayer.rotationYaw + Math.atan2(player.posX - mc.thePlayer.posX, player.posZ - mc.thePlayer.posZ) * 57.295780181884766) % 360.0;

                double scaledDistance = Math.sqrt(distanceSquared) * pxPerBlock;
                double xOffset = scaledDistance * Math.sin(Math.toRadians(playerAngle));
                double zOffset = scaledDistance * Math.cos(Math.toRadians(playerAngle));

                double distanceOnRadar = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
                if (distanceOnRadar > maxRenderDist) {
                    double factor = maxRenderDist / distanceOnRadar;
                    xOffset *= factor;
                    zOffset *= factor;
                }

                double renderPosX = centerX - xOffset;
                double renderPosY = centerY - zOffset;

                int dotColor = Color.red.getRGB();
                String displayName = player.getName();
                if (mc.thePlayer.isOnSameTeam(player)) {
                    dotColor = Color.green.getRGB();
                }

                if (tracerLines.getValue()) {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glLineWidth(0.5f);
                    GL11.glColor3d(1.0, 0.0, 0.0);
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex2d(centerX, centerY);
                    GL11.glVertex2d(renderPosX, renderPosY);
                    GL11.glEnd();
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                }

                if (showHeads.getValue()) {
                    drawPlayerHead(player, (int) (renderPosX - 8), (int) (renderPosY - 8));
                } else {
                    Gui.drawRect((int) (renderPosX - 1.5), (int) (renderPosY - 1.5), (int) (renderPosX + 2), (int) (renderPosY + 2), dotColor);
                }

                if (showNames.getValue()) {
                    float nameY = showHeads.getValue() ? (float) ((renderPosY + 10) * 2) : (float) ((renderPosY - 6) * 2);
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(0.5, 0.5, 0.5);
                    mc.fontRendererObj.drawStringWithShadow(displayName, (float) (renderPosX * 2 - mc.fontRendererObj.getStringWidth(displayName) / 2), nameY, -1);
                    GlStateManager.popMatrix();
                }
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glPopMatrix();
    }

    private void drawPlayerHead(EntityPlayer player, int x, int y) {
        try {
            ResourceLocation skin = ((AbstractClientPlayer) player).getLocationSkin();
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            Gui.drawScaledCustomSizeModalRect(x, y, 8F, 8F, 8, 8, 16, 16, 64F, 64F);
            Gui.drawScaledCustomSizeModalRect(x, y, 40F, 8F, 8, 8, 16, 16, 64F, 64F);

            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        } catch (Exception ignored) {
        }
    }
}