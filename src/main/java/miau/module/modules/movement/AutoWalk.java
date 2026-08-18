package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

public class AutoWalk extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty forward = new BooleanProperty("Forward", true);
  public final BooleanProperty backward = new BooleanProperty("Backward", false);
  public final BooleanProperty left = new BooleanProperty("Left", false);
  public final BooleanProperty right = new BooleanProperty("Right", false);
  public final BooleanProperty autoDisable = new BooleanProperty("AutoDisable", false);
  public final IntProperty disableTime = new IntProperty("DisableTime", 1000, 0, 100000, () -> this.autoDisable.getValue());

  private final TimerUtil disableTimer = new TimerUtil();

  public AutoWalk() {
    super("AutoWalk", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (this.forward.getValue()) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
      if (!this.backward.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
      }
      if (!this.left.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
      }
      if (!this.right.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
      }
    }
    if (this.backward.getValue()) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), true);
      if (this.forward.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
      }
      if (!this.left.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
      }
      if (!this.right.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
      }
    }
    if (this.left.getValue()) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), true);
      if (this.forward.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
      }
      if (!this.backward.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
      }
      if (!this.right.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
      }
    }
    if (this.right.getValue()) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), true);
      if (this.forward.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
      }
      if (!this.backward.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
      }
      if (!this.left.getValue()) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
      }
    }
    if (this.autoDisable.getValue()) {
      if (this.disableTimer.hasTimeElapsed((long) this.disableTime.getValue())) {
        this.disableTimer.reset();
        this.setEnabled(false);
      }
    }
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (!GameSettings.isKeyDown(mc.gameSettings.keyBindForward)) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
    }
    if (!GameSettings.isKeyDown(mc.gameSettings.keyBindBack)) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
    }
    if (!GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
    }
    if (!GameSettings.isKeyDown(mc.gameSettings.keyBindRight)) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
    }
    this.disableTimer.reset();
  }

  @Override
  public void onEnabled() {
    this.disableTimer.reset();
  }
}