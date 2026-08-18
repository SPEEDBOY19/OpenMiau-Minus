package miau.module.modules.misc;

import java.util.ArrayList;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.module.Module;
import miau.module.modules.misc.disabler.*;
import miau.property.Property;
import miau.property.properties.ModeProperty;

public class Disabler extends Module {

  public final List<DisablerMode> modes = new ArrayList<>();

  // -- Mode instances --
  public final TransactionDisabler transaction = new TransactionDisabler("Transaction", this);
  public final GrimAutoclickDisabler grimAutoclick = new GrimAutoclickDisabler("Grim AutoClick", this);

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Transaction", "Grim AutoClick"});

  public Disabler() {
    super("Disabler", false);
    modes.add(transaction);
    modes.add(grimAutoclick);
  }

  public DisablerMode getActiveMode() {
    String modeName = mode.getModeString();
    for (DisablerMode m : modes) {
      if (m.getName().equalsIgnoreCase(modeName)) {
        return m;
      }
    }
    return modes.isEmpty() ? null : modes.get(0);
  }

  @Override
  public void onEnabled() {
    DisablerMode active = getActiveMode();
    if (active != null) active.onEnable();
  }

  @Override
  public void onDisabled() {
    DisablerMode active = getActiveMode();
    if (active != null) active.onDisable();
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (DisablerMode m : modes) {
      for (java.lang.reflect.Field field : m.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        try {
          Object obj = field.get(m);
          if (obj instanceof Property<?>) {
            Property<?> prop = (Property<?>) obj;
            java.util.function.BooleanSupplier original = prop.getVisibleChecker();
            prop.setVisibleChecker(
                () -> this.getActiveMode() == m && (original == null || original.getAsBoolean()));
            props.add(prop);
          }
        } catch (Exception ignored) {
        }
      }
    }
    return props;
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onTick(event);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onPacket(event);
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onStrafe(event);
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onLivingUpdate(event);
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onMoveInput(event);
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onJump(event);
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onRender2D(event);
    }
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onLoadWorld(event);
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {mode.getModeString()};
  }
}
