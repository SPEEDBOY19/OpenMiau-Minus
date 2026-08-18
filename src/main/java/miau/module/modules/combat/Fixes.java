package miau.module.modules.combat;

import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.JumpEvent;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.UpdateEvent;
import miau.mixin.IAccessorEntityLivingBase;
import miau.mixin.IAccessorMinecraft;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.Module;
import miau.module.modules.misc.MouseRawInput;
import miau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class Fixes extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final BooleanProperty noClickDelay = new BooleanProperty("NoClickDelay", true);
  private final BooleanProperty noRightClickDelay = new BooleanProperty("NoRightClickDelay", true);
  private final BooleanProperty noBlockHitDelay = new BooleanProperty("NoBlockHitDelay", false);
  private final BooleanProperty rawMouseInput = new BooleanProperty("RawMouseInput(EnableAgainToApply)", true);

  private final BooleanProperty booster = new BooleanProperty("Booster", false);
  private final BooleanProperty fpsBoost = new BooleanProperty("FPSBoost", true);
  private final BooleanProperty renderOptimization = new BooleanProperty("RenderOptimization", true);
  private final BooleanProperty entityOptimization = new BooleanProperty("EntityOptimization", true);

  private final BooleanProperty noJumpDelay = new BooleanProperty("NoJumpDelay", true);

  public Fixes() {
    super("Fixes", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (noClickDelay.getValue()) {
      ((IAccessorMinecraft) mc).setLeftClickCounter(0);
    }
    if (noBlockHitDelay.getValue()) {
      ((IAccessorPlayerControllerMP) mc.playerController).setBlockHitDelay(0);
    }
    if (noRightClickDelay.getValue()) {
      ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
    }

    if (booster.getValue() && entityOptimization.getValue()) {
      optimizeEntities();
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (noJumpDelay.getValue()) {
      ((IAccessorEntityLivingBase) mc.thePlayer).setJumpTicks(0);
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (booster.getValue() && renderOptimization.getValue()) {
      optimizeRendering();
    }
  }

  @EventTarget
  public void onWorld(LoadWorldEvent event) {
    if (booster.getValue() && entityOptimization.getValue()) {
      clearEntityCache();
    }
  }

  @Override
  public void onEnabled() {
    if (rawMouseInput.getValue()) {
      Module raw = Miau.moduleManager.modules.get(MouseRawInput.class);
      if (raw != null && !raw.isEnabled()) {
        raw.toggle();
      }
    }
  }

  @Override
  public void onDisabled() {
    if (rawMouseInput.getValue()) {
      Module raw = Miau.moduleManager.modules.get(MouseRawInput.class);
      if (raw != null && raw.isEnabled()) {
        raw.toggle();
      }
    }
  }

  private void optimizeEntities() {
    World world = mc.theWorld;
    if (world == null) return;
    Entity player = mc.thePlayer;
    if (player == null) return;

    for (Entity entity : world.loadedEntityList) {
      if (entity != player) {
        double distance = player.getDistanceToEntity(entity);

        if (distance > 64) {
          entity.setInvisible(true);
        } else if (distance > 32) {
          entity.setInvisible(false);
        }
      }
    }
  }

  private void optimizeRendering() {
    GlStateManager.disableAlpha();
    GlStateManager.enableAlpha();

    if (mc.renderEngine != null) {
      GlStateManager.bindTexture(0);
    }

    if (fpsBoost.getValue()) {
      mc.entityRenderer.disableLightmap();
      mc.entityRenderer.enableLightmap();
    }
  }

  private void clearEntityCache() {
    try {
      World world = mc.theWorld;
      if (world == null) return;
      List<Entity> entityList = world.loadedEntityList;

      if (entityList.size() > 100) {
        world.loadedEntityList.removeIf(
            entity ->
                entity.isDead
                    || entity.riddenByEntity == null
                        && entity.ridingEntity == null
                        && entity.ticksExisted > 600);
      }
    } catch (Exception ignored) {
    }
  }
}
