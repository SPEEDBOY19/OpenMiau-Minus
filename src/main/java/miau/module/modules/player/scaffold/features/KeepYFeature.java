package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class KeepYFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty keepY =
      new ModeProperty(
          "keep-y",
          0,
          new String[] {"NONE", "VANILLA", "Extra 1 Block", "TELLY", "EXTRATELLY", "BETA"});
  public final BooleanProperty keepYonPress =
      new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
  public final BooleanProperty tellyRightClick =
      new BooleanProperty(
          "telly-on-right-click",
          false,
          () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);

  // Chỉnh trễ 6-7 ticks trên không để nhân vật thực hiện cú nhảy xa qua 3-4 block trước khi đặt
  public final FloatProperty betaTickDelay =
      new FloatProperty(
          "beta-tick-delay", 6.0F, 1.0F, 12.0F, () -> this.keepY.getValue() == 5);

  public int betaAirTicks = 0;
  public boolean betaPlacedThisCycle = false;

  public KeepYFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(keepY, keepYonPress, tellyRightClick, betaTickDelay);
  }

  @Override
  public void onEnable() {
    this.betaAirTicks = 0;
    this.betaPlacedThisCycle = false;
  }

  @Override
  public void onDisable() {
    this.betaAirTicks = 0;
    this.betaPlacedThisCycle = false;
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (this.keepY.getValue() == 5) {
      if (mc.thePlayer.onGround) {
        this.betaAirTicks = 0;
        this.betaPlacedThisCycle = false;
      } else {
        this.betaAirTicks++;
      }
    } else {
      this.betaAirTicks = 0;
      this.betaPlacedThisCycle = false;
    }

    if (mc.thePlayer.onGround) {
      if (scaffold.stage > 0) scaffold.stage--;
      if (scaffold.stage < 0) scaffold.stage++;

      if (scaffold.stage == 0
          && this.keepY.getValue() != 0
          && (this.keepYonPress.getValue()
              ? Scaffold.mc.gameSettings.keyBindUseItem.isKeyDown()
              : !mc.gameSettings.keyBindJump.isKeyDown())) {
        scaffold.stage = 1;
      }
      scaffold.startY =
          scaffold.shouldKeepY ? scaffold.startY : MathHelper.floor_double(mc.thePlayer.posY);
      scaffold.shouldKeepY = false;
    }
  }
}