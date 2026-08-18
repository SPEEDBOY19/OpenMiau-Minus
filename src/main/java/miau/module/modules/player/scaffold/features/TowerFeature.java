package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.StrafeEvent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.ModeProperty;
import miau.util.player.ItemUtil;
import miau.util.player.MoveUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

public class TowerFeature implements ScaffoldComponent {
  private final Scaffold scaffold;

  public final ModeProperty tower =
      new ModeProperty("tower", 0, new String[] {"NONE", "VANILLA", "TELLY"});

  public TowerFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(tower);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (Scaffold.mc.thePlayer.onGround) {
      scaffold.towering = false;
    }
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    if (!Scaffold.mc.thePlayer.isCollidedHorizontally
        && Scaffold.mc.thePlayer.hurtTime <= 5
        && !Scaffold.mc.thePlayer.isPotionActive(Potion.jump)
        && Scaffold.mc.gameSettings.keyBindJump.isKeyDown()
        && ItemUtil.isHoldingBlock()) {
      int yState = (int) (Scaffold.mc.thePlayer.posY % 1.0 * 100.0);
      switch (this.tower.getValue()) {
        case 1:
          handleVanillaTower(event, yState);
          break;
        default:
          scaffold.towerTick = 0;
          scaffold.towerDelay = 0;
      }
    } else {
      scaffold.towerTick = 0;
      scaffold.towerDelay = 0;
    }
  }

  private void handleVanillaTower(StrafeEvent event, int yState) {
    switch (scaffold.towerTick) {
      case 0:
        if (Scaffold.mc.thePlayer.onGround) {
          scaffold.towerTick = 1;
          Scaffold.mc.thePlayer.motionY = -0.0784000015258789;
        }
        return;
      case 1:
        if (yState == 0 && PlayerUtil.isAirBelow()) {
          scaffold.startY = MathHelper.floor_double(Scaffold.mc.thePlayer.posY);
          scaffold.towerTick = 2;
          Scaffold.mc.thePlayer.motionY = 0.42F;
          if (MoveUtil.isForwardPressed())
            MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
          else {
            MoveUtil.setSpeed(0.0);
            event.setForward(0.0F);
            event.setStrafe(0.0F);
          }
          return;
        } else {
          scaffold.towerTick = 0;
          return;
        }
      case 2:
        scaffold.towerTick = 3;
        Scaffold.mc.thePlayer.motionY = 0.75 - Scaffold.mc.thePlayer.posY % 1.0;
        return;
      case 3:
        scaffold.towerTick = 1;
        Scaffold.mc.thePlayer.motionY = 1.0 - Scaffold.mc.thePlayer.posY % 1.0;
        return;
      default:
        scaffold.towerTick = 0;
        return;
    }
  }
}
