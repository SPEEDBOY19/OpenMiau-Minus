package miau.module.modules.render;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import javax.vecmath.Vector4d;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.TickEvent;
import miau.mixin.IAccessorEntityRenderer;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.player.TeamUtil;
import miau.util.render.RenderUtil;
import miau.util.render.Themes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public class NameTags extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final Map<String, Integer> nameWidths = new HashMap<>();

  public final BooleanProperty showTargets = new BooleanProperty("targets", false);
  public final BooleanProperty player =
      new BooleanProperty("player", true, () -> !showTargets.getValue());
  public final BooleanProperty invisibles =
      new BooleanProperty("invisibles", false, () -> !showTargets.getValue());
  public final BooleanProperty animals =
      new BooleanProperty("animals", false, () -> !showTargets.getValue());
  public final BooleanProperty mobs =
      new BooleanProperty("mobs", false, () -> !showTargets.getValue());
  public final BooleanProperty teams =
      new BooleanProperty("player-teammates", true, () -> !showTargets.getValue());

  public final BooleanProperty showTeam = new BooleanProperty("show-team-tag", false);
  public final BooleanProperty showTarget = new BooleanProperty("show-target-tag", false);
  public final BooleanProperty showFriendTag = new BooleanProperty("show-friend-tag", false);
  public final BooleanProperty shortenedTags = new BooleanProperty("shortened-tags", false);
  public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);

  public NameTags() {
    super("NameTags", false);
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    nameWidths.clear();
  }

  public float getWidth(String name, net.minecraft.client.gui.FontRenderer font) {
    String id = name + font.hashCode();
    if (!nameWidths.containsKey(id)) {
      nameWidths.put(id, font.getStringWidth(name));
    }
    return nameWidths.get(id);
  }

  public boolean shouldRenderTags(EntityLivingBase entity) {
    if (entity == null || mc.theWorld == null || mc.thePlayer == null) {
      return false;
    }
    if (entity.deathTime > 0 || entity.isDead) {
      return false;
    }
    if (entity == mc.thePlayer) {
      return player.getValue() && mc.gameSettings.thirdPersonView != 0 && !showTargets.getValue();
    }

    if (mc.getRenderViewEntity() == null
        || mc.getRenderViewEntity().getDistanceToEntity(entity) > 512.0F) {
      return false;
    }

    if (entity.isInvisible() && !showTargets.getValue() && !invisibles.getValue()) {
      return false;
    }

    if (showTargets.getValue()) {
      if (entity instanceof EntityPlayer) {
        return Miau.targetManager != null && Miau.targetManager.isFriend(entity.getName());
      }
      return false;
    }

    if (entity instanceof EntityPlayer) {
      if (!player.getValue()) {
        return false;
      }
      if (!teams.getValue() && TeamUtil.isSameTeam((EntityPlayer) entity)) {
        return false;
      }
      return true;
    }

    if (entity instanceof net.minecraft.entity.passive.EntityAnimal
        || entity instanceof net.minecraft.entity.passive.EntityBat
        || entity instanceof net.minecraft.entity.passive.EntitySquid
        || entity instanceof net.minecraft.entity.passive.EntityVillager) {
      return animals.getValue();
    }

    return mobs.getValue();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled() || mc.theWorld == null) {
      return;
    }
    for (Entity entity : mc.theWorld.loadedEntityList) {
      if (entity instanceof EntityLivingBase && shouldRenderTags((EntityLivingBase) entity)) {
        entity.ignoreFrustumCheck = true;
      }
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled() || mc.theWorld == null) {
      return;
    }

    double scaleFactor = new ScaledResolution(mc).getScaleFactor();
    double scale = scaleFactor / Math.pow(scaleFactor, 2.0);

    GlStateManager.pushMatrix();
    GlStateManager.scale(scale, scale, scale);

    for (Entity entity : mc.theWorld.loadedEntityList) {
      if (!(entity instanceof EntityLivingBase)) {
        continue;
      }

      EntityLivingBase living = (EntityLivingBase) entity;
      if (!shouldRenderTags(living)) {
        continue;
      }

      ((IAccessorEntityRenderer) mc.entityRenderer)
          .callSetupCameraTransform(event.getPartialTicks(), 0);
      Vector4d position = RenderUtil.projectToScreen(living, scaleFactor);
      mc.entityRenderer.setupOverlayRendering();

      if (position == null) {
        continue;
      }

      String nametag =
          living.getDisplayName().getFormattedText()
              + " §7[§4❤"
              + Math.round(living.getHealth())
              + "§7]";

      if (showTeam.getValue()
          && living instanceof EntityPlayer
          && TeamUtil.isSameTeam((EntityPlayer) living)) {
        nametag = "§a§l" + (shortenedTags.getValue() ? "[TM]" : "[TEAM]") + "§r " + nametag;
      }

      if (showTarget.getValue()
          && living instanceof EntityPlayer
          && Miau.targetManager != null
          && Miau.targetManager.isFriend(living.getName())) {
        nametag = "§4§l" + (shortenedTags.getValue() ? "[T]" : "[TARGET]") + "§r " + nametag;
      }

      if (showFriendTag.getValue()
          && living instanceof EntityPlayer
          && Miau.friendManager != null
          && Miau.friendManager.isFriend(living.getName())) {
        nametag = "§b§l" + (shortenedTags.getValue() ? "[F]" : "[FRIEND]") + "§r " + nametag;
      }

      float padding = 2.0f;
      float height = 8.0f;
      float width = getWidth(nametag, mc.fontRendererObj);

      float posX = (float) (position.x + (position.z - position.x) / 2.0);
      float posY = (float) position.y - height;

      GlStateManager.pushMatrix();
      GlStateManager.translate(posX, posY, 0.0f);
      float scaleVal = this.scale.getValue();
      GlStateManager.scale(scaleVal, scaleVal, 1.0f);

      float x1 = -width / 2.0f - padding;
      float y1 = -padding - 3.0f;
      float x2 = x1 + width + padding * 2.0f;
      float y2 = y1 + height + padding * 2.0f;

      RenderUtil.enableRenderState();
      RenderUtil.drawRect(x1, y1, x2, y2, Themes.getBackgroundShade().getRGB());
      RenderUtil.disableRenderState();

      float centeredPosX = -(width / 2.0f);
      mc.fontRendererObj.drawString(
          nametag, centeredPosX + 0.5f, -2.0f, Color.WHITE.getRGB(), true);
      GlStateManager.popMatrix();
    }

    GlStateManager.popMatrix();
  }
}
