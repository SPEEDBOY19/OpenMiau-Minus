package miau.module.modules.ghost;

import java.util.Objects;
import miau.event.EventTarget;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.Priority;
import miau.module.Module;
import miau.module.modules.ghost.bridgeassist.mode.NormalMode;
import miau.module.modules.ghost.bridgeassist.mode.SilentMode;
import miau.property.properties.ModeProperty;
import miau.util.player.ItemUtil;
import net.minecraft.client.Minecraft;

public class BridgeAssist extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Normal", "Silent"});

  private final NormalMode normalMode = new NormalMode(this);
  private final SilentMode silentMode = new SilentMode(this);

  public BridgeAssist() {
    super("Bridge Assist", false);
  }

  @Override
  public java.util.List<miau.property.Property<?>> getAdditionalProperties() {
    java.util.List<miau.property.Property<?>> props = new java.util.ArrayList<>();
    props.addAll(normalMode.getProperties());
    props.addAll(silentMode.getProperties());
    return props;
  }

  @Override
  public String[] getSuffix() {
    if (this.mode.getModeString().equals("Silent")) {
      return new String[] {"Silent"};
    }
    return Objects.equals(normalMode.delayMs.getValue(), normalMode.delayMs.getSecondValue())
        ? new String[] {String.valueOf(normalMode.delayMs.getValue().intValue())}
        : new String[] {
          String.format(
              "%d-%d",
              normalMode.delayMs.getValue().intValue(),
              normalMode.delayMs.getSecondValue().intValue())
        };
  }

  @Override
  public void onDisabled() {
    normalMode.onDisabled();
    silentMode.onDisabled();
  }

  public boolean isHoldingBlock() {
    return ItemUtil.isHoldingBlock();
  }

  @EventTarget(Priority.LOWEST)
  public void onTick(TickEvent event) {
    if (!this.isEnabled()) return;
    if (!this.mode.getModeString().equals("Normal")) return;
    normalMode.onTick(event);
  }

  @EventTarget(Priority.LOWEST)
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled() || mc.currentScreen != null) return;

    if (this.mode.getModeString().equals("Normal")) {
      normalMode.onMoveInput(event);
    } else if (this.mode.getModeString().equals("Silent")) {
      silentMode.onMoveInput(event);
    }
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent e) {
    if (!this.isEnabled()) return;
    if (!this.mode.getModeString().equals("Silent")) return;
    silentMode.onUpdate(e);
  }
}
