package miau.module.modules.combat;

import com.google.common.base.CaseFormat;
import java.util.ArrayList;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.module.Module;
import miau.module.modules.combat.criticals.CriticalsMode;
import miau.module.modules.combat.criticals.JumpCriticals;
import miau.module.modules.combat.criticals.NoGroundCriticals;
import miau.module.modules.combat.criticals.VanillaCriticals;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.time.TimerUtil;

public class Criticals extends Module {
  private final TimerUtil timer = new TimerUtil();
  public final List<CriticalsMode> modes = new ArrayList<>();
  public final ModeProperty mode;
  public final IntProperty tickDelay;

  public Criticals() {
    super("Criticals", false);

    modes.add(new JumpCriticals("Jump", this));
    modes.add(new VanillaCriticals("Vanilla", this));
    modes.add(new NoGroundCriticals("NoGround", this));

    String[] modeNames = modes.stream().map(CriticalsMode::getName).toArray(String[]::new);
    mode = new ModeProperty("mode", 0, modeNames);
    tickDelay = new IntProperty("tick-delay", 9, 0, 20);
  }

  @Override
  public void onEnabled() {
    CriticalsMode currentMode = modes.get(mode.getValue());
    if (currentMode != null) {
      currentMode.onEnable();
    }
    timer.reset();
  }

  @Override
  public void onDisabled() {
    CriticalsMode currentMode = modes.get(mode.getValue());
    if (currentMode != null) {
      currentMode.onDisable();
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    CriticalsMode currentMode = modes.get(mode.getValue());
    if (currentMode != null) {
      currentMode.onUpdate(event);
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (timer.hasTimeElapsed(tickDelay.getValue() * 50L)) {
      CriticalsMode currentMode = modes.get(mode.getValue());
      if (currentMode != null) {
        currentMode.onAttack(event);
      }
      timer.reset();
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    CriticalsMode currentMode = modes.get(mode.getValue());
    if (currentMode != null) {
      currentMode.onPacket(event);
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    CriticalsMode currentMode = modes.get(mode.getValue());
    if (currentMode != null) {
      currentMode.onMoveInput(event);
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())
    };
  }

  @Override
  public void verifyValue(String value) {}
}
