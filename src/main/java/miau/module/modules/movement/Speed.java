package miau.module.modules.movement;

import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.JumpEvent;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.StrafeEvent;
import miau.event.types.Priority;
import miau.mixin.IAccessorEntity;
import miau.module.Module;
import miau.module.modules.movement.speeds.DefaultSpeed;
import miau.module.modules.movement.speeds.LegitSpeed;
import miau.module.modules.movement.speeds.LowHopSpeed;
import miau.module.modules.movement.speeds.SpeedMode;
import miau.module.modules.movement.speeds.VulcanSpeed;
import miau.module.modules.movement.speeds.PolarSpeed;
import miau.module.modules.movement.speeds.BhopSpeed;
import miau.module.modules.player.Scaffold;
import miau.property.Property;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;

public class Speed extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode =
      new ModeProperty(
          "mode",
          0,
          new String[] {"DEFAULT", "LEGIT", "LowHop", "VULCAN", "POLAR", "BHOP"});

  private final SpeedMode[] modes =
      new SpeedMode[] {
        new DefaultSpeed("DEFAULT", this),
        new LegitSpeed("LEGIT", this),
        new LowHopSpeed("LowHop", this),
        new VulcanSpeed("VULCAN", this),
        new PolarSpeed("POLAR", this),
        new BhopSpeed("BHOP", this)
      };

  private int lastMode = -1;

  public Speed() {
    super("Speed", false);
  }

  public boolean canBoost() {
    Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
    return !scaffold.isEnabled()
        && MoveUtil.isForwardPressed()
        && mc.thePlayer.getFoodStats().getFoodLevel() > 6
        && !mc.thePlayer.isSneaking()
        && !mc.thePlayer.isInWater()
        && !mc.thePlayer.isInLava()
        && !((IAccessorEntity) mc.thePlayer).getIsInWeb();
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (SpeedMode m : modes) {
      props.addAll(m.getProperties());
    }
    return props;
  }

  @EventTarget(Priority.LOW)
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      modes[this.mode.getValue()].onStrafe(event);
    }
  }

  @EventTarget(Priority.LOW)
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled()) {
      return;
    }

    if (this.mode.getValue() != lastMode) {
      if (lastMode != -1) {
        modes[lastMode].onDisable();
      }
      ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
      lastMode = this.mode.getValue();
      modes[this.mode.getValue()].onEnable();
    }

    modes[this.mode.getValue()].onLivingUpdate(event);
  }

  @EventTarget(Priority.LOW)
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    modes[this.mode.getValue()].onPacket(event);
  }

  @Override
  public void onDisabled() {
    for (SpeedMode m : modes) {
      m.onDisable();
    }
    ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
  }

  @EventTarget(Priority.LOW)
  public void onJump(JumpEvent event) {
    if (!this.isEnabled()) return;
    modes[this.mode.getValue()].onJump(event);
  }

  @EventTarget(Priority.LOW)
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled()) return;
    modes[this.mode.getValue()].onMoveInput(event);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {mode.getModeString()};
  }
}
