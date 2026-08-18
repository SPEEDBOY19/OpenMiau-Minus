package miau.module.modules.player.scaffold.features;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import miau.Miau;
import miau.event.impl.Render3DEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.module.modules.render.HUD;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

public class BlockRenderFeature implements ScaffoldComponent {

  private final Scaffold scaffold;
  public final ModeProperty espMode =
      new ModeProperty("ESP", 0, new String[] {"Default", "HUD", "None"});
  public final BooleanProperty raytrace =
      new BooleanProperty("Raytrace", false, () -> espMode.getValue() != 2);
  public final IntProperty alpha =
      new IntProperty("Alpha", 200, 0, 255, () -> espMode.getValue() != 2 && raytrace.getValue());
  public final BooleanProperty outline =
      new BooleanProperty("Outline", true, () -> espMode.getValue() != 2);
  public final BooleanProperty shade =
      new BooleanProperty("Shade", false, () -> espMode.getValue() != 2);

  private final Map<BlockPos, Long> highlight = new HashMap<>();
  private MovingObjectPosition lastESPRaytrace = null;

  public BlockRenderFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(espMode, raytrace, alpha, outline, shade);
  }

  @Override
  public void onEnable() {
    highlight.clear();
    lastESPRaytrace = null;
  }

  @Override
  public void onDisable() {
    highlight.clear();
  }

  public void markPlaced(BlockPos pos) {
    if (espMode.getValue() != 2 && !raytrace.getValue()) {
      highlight.put(pos, System.currentTimeMillis());
    }
  }

  @Override
  public void onRender3D(Render3DEvent event) {
    if (espMode.getValue() == 2) {
      return;
    }

    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
    int themeColor = espMode.getValue() == 1 ? hud.getColor(0).getRGB() : Color.CYAN.getRGB();

    if (!highlight.isEmpty()) {
      Iterator<Map.Entry<BlockPos, Long>> iterator = highlight.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<BlockPos, Long> entry = iterator.next();
        long time = System.currentTimeMillis() - entry.getValue();
        if (time > 750) {
          iterator.remove();
          continue;
        }
        int currentAlpha = (int) (210 - (time / 750.0 * 210));
        if (currentAlpha <= 0) {
          iterator.remove();
          continue;
        }

        if (!raytrace.getValue()) {
          RenderUtil.renderBlock(
              entry.getKey(),
              (themeColor & 0xFFFFFF) | (currentAlpha << 24),
              outline.getValue(),
              shade.getValue());
        }
      }
    }

    if (raytrace.getValue()) {
      Minecraft mc = Minecraft.getMinecraft();
      MovingObjectPosition hitResult = mc.objectMouseOver;
      if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
        hitResult = lastESPRaytrace;
      } else {
        lastESPRaytrace = hitResult;
      }

      if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
        RenderUtil.renderBlock(
            hitResult.getBlockPos(),
            (themeColor & 0xFFFFFF) | (alpha.getValue() << 24),
            outline.getValue(),
            shade.getValue());
      }
    }
  }
}
