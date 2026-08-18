package miau.module.modules.movement.speeds;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.LivingUpdateEvent;
import miau.module.modules.movement.Speed;
import miau.property.Property;
import miau.property.properties.BooleanProperty;

public class LegitSpeed extends SpeedMode {
  public final BooleanProperty legitCancelSneak =
      new BooleanProperty("cancel-when-sneaking", true, () -> parent.mode.getValue() == 1);
  private boolean legitJumping;

  public LegitSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(legitCancelSneak);
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (mc.thePlayer.onGround
        && (!this.legitCancelSneak.getValue() || !mc.thePlayer.isSneaking())) {
      if (mc.theWorld
          .getCollidingBoundingBoxes(
              mc.thePlayer,
              mc.thePlayer
                  .getEntityBoundingBox()
                  .offset(mc.thePlayer.motionX / 3.0D, -1.0D, mc.thePlayer.motionZ / 3.0D))
          .isEmpty()) {
        this.legitJumping = true;
        net.minecraft.client.settings.KeyBinding.setKeyBindState(
            mc.gameSettings.keyBindJump.getKeyCode(), true);
      } else if (this.legitJumping) {
        this.legitJumping = false;
        net.minecraft.client.settings.KeyBinding.setKeyBindState(
            mc.gameSettings.keyBindJump.getKeyCode(), false);
      }
    } else if (this.legitJumping) {
      this.legitJumping = false;
      net.minecraft.client.settings.KeyBinding.setKeyBindState(
          mc.gameSettings.keyBindJump.getKeyCode(), false);
    }
  }

  @Override
  public void onDisable() {
    if (this.legitJumping) {
      this.legitJumping = false;
      net.minecraft.client.settings.KeyBinding.setKeyBindState(
          mc.gameSettings.keyBindJump.getKeyCode(), false);
    }
  }
}
