package miau.module.modules.movement.speeds;

import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.Speed;
import miau.module.modules.player.Scaffold;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;

public class BhopSpeed extends SpeedMode {
  private int inAirTicks = 0;
  private int dmgTicks = 0;
  private boolean collided = false;
  private boolean down = false;
  private boolean dmg = false;

  public final ModeProperty bhopMode =
      new ModeProperty("Bhop Mode", 0, new String[] {"Ground", "8 tick"});
  public final BooleanProperty disableWhileScaffold = new BooleanProperty("Disable while scaffold", false);
  public final BooleanProperty rotateYaw = new BooleanProperty("Rotate Yaw", false);

  public BhopSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public List<Property<?>> getProperties() {
    List<Property<?>> props = new ArrayList<>();
    props.add(bhopMode);
    props.add(disableWhileScaffold);
    props.add(rotateYaw);
    return props;
  }

  @Override
  public void onEnable() {
    inAirTicks = 0;
    dmgTicks = 0;
    collided = false;
    down = false;
    dmg = false;
  }

  @Override
  public void onDisable() {
    inAirTicks = 0;
    dmgTicks = 0;
    collided = false;
    down = false;
    dmg = false;
  }

  private boolean scaffoldEnabled() {
    Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
    return scaffold != null && scaffold.isEnabled();
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (!parent.canBoost() && mc.thePlayer.onGround) {
      return;
    }

    if (disableWhileScaffold.getValue() && scaffoldEnabled()) return;

    inAirTicks = mc.thePlayer.onGround ? 0 : inAirTicks + 1;
    if (dmg && dmgTicks > 0) dmgTicks--;

    if (mc.thePlayer.isCollidedHorizontally) {
      collided = true;
    } else if (mc.thePlayer.onGround) {
      collided = false;
    }

    if (mc.thePlayer.onGround) {
      if (dmg && dmgTicks == 0) dmg = false;
      down = false;
      mc.thePlayer.jump();
      if (MoveUtil.isMoving()) {
        if (!rotateYaw.getValue()) {
          if (MoveUtil.getForwardValue() != -1) {
            MoveUtil.setSpeed(getBhopSpeed(), MoveUtil.getMoveYaw());
          } else {
            MoveUtil.setSpeed(getBhopSpeed() - 0.3, MoveUtil.getMoveYaw());
          }
        } else {
          MoveUtil.setSpeed(getBhopSpeed(), MoveUtil.getMoveYaw());
        }
      }
      return;
    }

    if (bhopMode.getValue() != 1 || !MoveUtil.isMoving() || collided || dmg) return;

    int simpleY = (int) Math.round((mc.thePlayer.posY % 1) * 10000);

    if (simpleY == 13) {
      mc.thePlayer.motionY -= 0.02483;
      down = true;
    }
    if (simpleY == 2000) {
      mc.thePlayer.motionY -= 0.1913;
    }
    if (down) {
      mc.thePlayer.posY -= 1E-5;
    }
    if (simpleY == 3426) {
      MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
    }
  }

  private double getBhopSpeed() {
    int level = MoveUtil.getSpeedLevel();
    switch (level) {
      case 1:
        return 0.51;
      case 2:
        return 0.59;
      case 3:
        return 0.69;
      case 4:
        return 0.78;
      default:
        return 0.48;
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) event.getPacket();
      if (velocity.getEntityID() == mc.thePlayer.getEntityId()) {
        dmg = true;
        dmgTicks = 8;
      }
    }
  }
}
