package miau.module.modules.render;

import miau.event.EventTarget;
import miau.event.impl.Render3DEvent;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Port of FireBounceClient's BAHalo module. */
public class BAHalo extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final String[] CHARACTER_CHOICES = {
    "alice", "shiroko", "reisa", "hoshino", "azusa", "izuna", "kayoko", "shengya", "TianYang",
    "A", "A1", "A2", "A3", "A4",
    "B", "B1", "B2", "B3", "B4",
    "C", "C1", "C2", "C3", "C4",
    "D", "D1", "D2", "D3", "D4",
    "E", "E1", "E2", "E3", "E4",
    "F", "F1", "F2", "F3", "F4",
    "G", "G1", "G2", "G3", "G4",
    "H", "H1", "H2", "H3", "H4",
    "I", "I1", "I2", "I3", "I4",
    "J", "J1", "J2", "J3", "J4",
    "K", "K1", "K2", "K3",
    "L", "L1", "L2", "L3",
    "M", "M1", "M2", "M3",
    "N", "N1", "N2", "N3",
    "O", "O1", "O2", "O3",
    "P", "P1", "P2", "P3",
    "Q", "Q1", "Q2", "Q3",
    "R", "R1", "R2", "R3",
    "S", "S1", "S2", "S3",
    "T", "T1", "T2", "T3",
    "U", "U1", "U2", "U3",
    "V", "V1", "V2", "V3",
    "W", "W1", "W2", "W3",
    "X", "X1", "X2", "X3",
    "Y", "Y1", "Y2", "Y3",
    "Z", "Z1", "Z2", "Z3",
    "DLA", "DLB", "DLC", "DLD", "DLE", "DLF",
  };

  public final ModeProperty character = new ModeProperty("Character", 0, CHARACTER_CHOICES);
  public final BooleanProperty onlySelf = new BooleanProperty("OnlySelf", true);
  public final BooleanProperty trackCamera = new BooleanProperty("TrackCamera", true);
  public final FloatProperty size = new FloatProperty("Scale", 0.5f, 0.1f, 2f);
  public final FloatProperty offsetX = new FloatProperty("OffsetX", 0f, -2f, 2f);
  public final FloatProperty offsetY = new FloatProperty("OffsetY", 0.4f, -2f, 2f);
  public final FloatProperty offsetZ = new FloatProperty("OffsetZ", 0f, -2f, 2f);
  public final FloatProperty floatRange = new FloatProperty("FloatRange", 0.05f, 0f, 1f);
  public final FloatProperty floatSpeed = new FloatProperty("FloatSpeed", 0.5f, 0.01f, 5f);
  public final BooleanProperty flipEnabled = new BooleanProperty("FlipEnabled", true);
  public final FloatProperty flipX = new FloatProperty("FlipX", 0f, -180f, 180f);
  public final FloatProperty flipY = new FloatProperty("FlipY", 5f, -180f, 180f);
  public final FloatProperty flipZ = new FloatProperty("FlipZ", 0f, -180f, 180f);

  public BAHalo() {
    super("BAHalo", false);
  }

  private ResourceLocation getTexture() {
    return new ResourceLocation("minecraft:miau/halo/" + character.getModeString() + ".png");
  }

  @Override
  public String[] getSuffix() {
    return new String[] {character.getModeString()};
  }

  @EventTarget(Priority.HIGHEST)
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled() || mc.theWorld == null) return;
    float partialTicks = event.getPartialTicks();

    java.util.List<EntityPlayer> players;
    if (!onlySelf.getValue()) {
      players = mc.theWorld.playerEntities;
    } else {
      players = new java.util.ArrayList<>();
      if (mc.thePlayer != null) {
        players.add(mc.thePlayer);
      }
    }

    double time = mc.theWorld.getWorldTime() + partialTicks;
    double floatY = Math.sin(time * floatSpeed.getValue()) * floatRange.getValue();

    ResourceLocation texture = getTexture();

    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GlStateManager.pushMatrix();

    try {
      GlStateManager.disableLighting();
      GlStateManager.disableCull();
      GlStateManager.enableBlend();
      GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.depthMask(false);
      GlStateManager.disableDepth();
      GlStateManager.color(1f, 1f, 1f, 1f);

      for (EntityPlayer entityPlayer : players) {
        try {
          if (entityPlayer.isSpectator() || !entityPlayer.isEntityAlive()) continue;

          double interpX =
              entityPlayer.lastTickPosX + (entityPlayer.posX - entityPlayer.lastTickPosX) * partialTicks;
          double interpY =
              entityPlayer.lastTickPosY + (entityPlayer.posY - entityPlayer.lastTickPosY) * partialTicks;
          double interpZ =
              entityPlayer.lastTickPosZ + (entityPlayer.posZ - entityPlayer.lastTickPosZ) * partialTicks;

          double baseY = entityPlayer.getEyeHeight() + offsetY.getValue() + floatY;
          double hx = interpX + offsetX.getValue();
          double hy = interpY + baseY;
          double hz = interpZ + offsetZ.getValue();

          GlStateManager.pushMatrix();
          GlStateManager.translate(
              (float) (hx - mc.getRenderManager().viewerPosX),
              (float) (hy - mc.getRenderManager().viewerPosY),
              (float) (hz - mc.getRenderManager().viewerPosZ));

          float scale = size.getValue();
          GlStateManager.scale(scale, scale, scale);

          float viewY = mc.getRenderManager().playerViewY;
          float viewX = mc.getRenderManager().playerViewX;

          GlStateManager.rotate(-viewY, 0f, 1f, 0f);
          if (trackCamera.getValue()) {
            GlStateManager.rotate(viewX, 1f, 0f, 0f);
          }

          if (flipEnabled.getValue()) {
            GlStateManager.rotate(flipX.getValue(), 1f, 0f, 0f);
            GlStateManager.rotate(flipY.getValue(), 0f, 1f, 0f);
            GlStateManager.rotate(flipZ.getValue(), 0f, 0f, 1f);
          }

          float half = 0.5f;

          try {
            mc.getTextureManager().bindTexture(texture);
          } catch (Throwable t) {
            ChatUtil.display("Halo: texture " + texture.getResourcePath() + " not found");
            GlStateManager.popMatrix();
            continue;
          }

          Tessellator tessellator = Tessellator.getInstance();
          tessellator.getWorldRenderer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
          tessellator.getWorldRenderer().pos(-half, -half, 0.0).tex(0.0, 1.0).color(1f, 1f, 1f, 1f).endVertex();
          tessellator.getWorldRenderer().pos(half, -half, 0.0).tex(1.0, 1.0).color(1f, 1f, 1f, 1f).endVertex();
          tessellator.getWorldRenderer().pos(half, half, 0.0).tex(1.0, 0.0).color(1f, 1f, 1f, 1f).endVertex();
          tessellator.getWorldRenderer().pos(-half, half, 0.0).tex(0.0, 0.0).color(1f, 1f, 1f, 1f).endVertex();
          tessellator.draw();

          GlStateManager.popMatrix();
        } catch (Throwable ignored) {
        }
      }
    } finally {
      try {
        GlStateManager.color(1f, 1f, 1f, 1f);
      } catch (Throwable ignored) {
      }
      try {
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
      } catch (Throwable ignored) {
      }
      GlStateManager.popMatrix();
      GL11.glPopAttrib();
    }
  }
}
