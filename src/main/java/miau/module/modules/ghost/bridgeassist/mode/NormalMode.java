package miau.module.modules.ghost.bridgeassist.mode;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.ghost.BridgeAssist;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.player.MoveUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

public class NormalMode {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final BridgeAssist parent;
  private int sneakDelay = 0;

  public final FloatProperty delayMs;
  public final BooleanProperty directionCheck;
  public final BooleanProperty jumpCheck;
  public final BooleanProperty pitchCheck;
  public final BooleanProperty blocksOnly;
  public final BooleanProperty normalSneakOnly;

  public NormalMode(BridgeAssist parent) {
    this.parent = parent;
    this.delayMs =
        new FloatProperty(
            "delay", 2.0F, 3.0F, 0.0F, 10.0F, () -> parent.mode.getModeString().equals("Normal"));
    this.directionCheck =
        new BooleanProperty(
            "direction-check", true, () -> parent.mode.getModeString().equals("Normal"));
    this.jumpCheck =
        new BooleanProperty("jump-check", true, () -> parent.mode.getModeString().equals("Normal"));
    this.pitchCheck =
        new BooleanProperty(
            "pitch-check", true, () -> parent.mode.getModeString().equals("Normal"));
    this.blocksOnly =
        new BooleanProperty(
            "blocks-only", true, () -> parent.mode.getModeString().equals("Normal"));
    this.normalSneakOnly =
        new BooleanProperty(
            "sneaking-only", false, () -> parent.mode.getModeString().equals("Normal"));
  }

  public List<Property<?>> getProperties() {
    return Arrays.asList(
        delayMs, directionCheck, jumpCheck, pitchCheck, blocksOnly, normalSneakOnly);
  }

  public void onDisabled() {
    this.sneakDelay = 0;
  }

  private boolean canMoveSafely() {
    double[] offset = MoveUtil.predictMovement();
    return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
  }

  private boolean shouldSneak() {
    if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
      return false;
    } else if (this.jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
      return false;
    } else if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
      return false;
    } else if (this.normalSneakOnly.getValue()
        && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
      return false;
    } else {
      return (!this.blocksOnly.getValue() || parent.isHoldingBlock()) && mc.thePlayer.onGround;
    }
  }

  public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
      if (this.sneakDelay > 0) {
        this.sneakDelay--;
      }
      if (this.sneakDelay == 0 && this.canMoveSafely()) {
        this.sneakDelay =
            RandomUtils.nextInt(
                this.delayMs.getValue().intValue(), this.delayMs.getSecondValue().intValue() + 1);
      }
    }
  }

  public void onMoveInput(MoveInputEvent event) {
    if (this.normalSneakOnly.getValue()
        && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
        && shouldSneak()) {
      mc.thePlayer.movementInput.sneak = false;
      mc.thePlayer.movementInput.moveForward /= 0.3F;
      mc.thePlayer.movementInput.moveStrafe /= 0.3F;
    }

    if (!mc.thePlayer.movementInput.sneak) {
      if (this.shouldSneak() && (this.sneakDelay > 0 || this.canMoveSafely())) {
        mc.thePlayer.movementInput.sneak = true;
        mc.thePlayer.movementInput.moveStrafe *= 0.3F;
        mc.thePlayer.movementInput.moveForward *= 0.3F;
      }
    }
  }

  public int getSneakDelay() {
    return sneakDelay;
  }
}
