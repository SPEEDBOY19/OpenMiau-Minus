package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class FastFall extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty boostMode = new ModeProperty("BoostMode", 1, new String[] {"Number", "Factor", "SetMotion"});
  public final FloatProperty boostNumber = new FloatProperty("BoostNumber", 1f, 0.01f, 10f, () -> this.boostMode.getModeString().equals("Number"));
  public final FloatProperty boostFactor = new FloatProperty("BoostFactor", 2f, 1f, 10f, () -> this.boostMode.getModeString().equals("Factor"));
  public final FloatProperty setMotionY = new FloatProperty("SetMotionNumber", 0.8f, 0.01f, 10f, () -> this.boostMode.getModeString().equals("SetMotion"));
  public final BooleanProperty changeTimer = new BooleanProperty("ChangeTimer", false);
  public final IntProperty timers = new IntProperty("Times", 1, 1, 2, () -> this.changeTimer.getValue());
  public final FloatProperty timer1Factor = new FloatProperty("Timer1Factor", 0.5f, 0.01f, 2f, () -> this.timers.getValue() >= 1 && this.changeTimer.getValue());
  public final IntProperty timer1Ticks = new IntProperty("Timer1Ticks", 3, 1, 20, () -> this.timers.getValue() >= 1 && this.changeTimer.getValue());
  public final FloatProperty timer2Factor = new FloatProperty("Timer2Factor", 0.5f, 0.01f, 150f, () -> this.timers.getValue() >= 2 && this.changeTimer.getValue());
  public final IntProperty timer2Ticks = new IntProperty("Timer2Ticks", 3, 1, 20, () -> this.timers.getValue() >= 2 && this.changeTimer.getValue());
  public final BooleanProperty autoDisable = new BooleanProperty("AutoDisable", false);

  private boolean boosted = false;
  private boolean tick1Start = false;
  private int timer1Tick = 0;
  private boolean tick2Start = false;
  private int timer2Tick = 0;
  private boolean changingTimer = false;

  public FastFall() {
    super("FastFall", false);
  }

  private boolean isFalling() {
    return !mc.thePlayer.onGround && mc.thePlayer.motionY < 0.0;
  }

  private void changeTimer(float speed) {
    ((IAccessorMinecraft) mc).getTimer().timerSpeed = speed;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (this.isFalling()) {
      if (this.boostMode.getModeString().equals("Number")) {
        if (!this.boosted) mc.thePlayer.motionY -= this.boostNumber.getValue();
      } else if (this.boostMode.getModeString().equals("Factor")) {
        if (!this.boosted) mc.thePlayer.motionY *= this.boostFactor.getValue();
      } else if (this.boostMode.getModeString().equals("SetMotion")) {
        if (!this.boosted) mc.thePlayer.motionY = -this.setMotionY.getValue();
      }
      this.boosted = true;
    }
    if (!this.isFalling() || mc.thePlayer.onGround && this.boosted) {
      if (this.autoDisable.getValue()) this.setEnabled(false);
      if (this.changeTimer.getValue()) this.changeTimer(1f);
      this.tick1Start = false;
      this.tick2Start = false;
      this.timer1Tick = 0;
      this.timer2Tick = 0;
      this.changingTimer = false;
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (this.boosted && this.changeTimer.getValue() && !mc.thePlayer.onGround && !this.changingTimer) {
      this.changingTimer = true;
    }
    if (this.boosted && this.changeTimer.getValue() && this.changingTimer) {
      if (mc.thePlayer.onGround) {
        this.changeTimer(1f);
        return;
      }
      if (!this.tick1Start && !this.tick2Start) {
        if (this.timer1Tick > this.timer1Ticks.getValue() && this.changingTimer && this.tick1Start) {
          this.tick1Start = false;
          this.tick2Start = true;
          return;
        }
        this.tick1Start = true;
        this.changeTimer(this.timer1Factor.getValue());
        this.timer1Tick++;
      }
      if (!this.tick1Start && this.tick2Start) {
        if (this.timer2Tick > this.timer2Ticks.getValue() && this.changingTimer && this.tick2Start) {
          this.tick2Start = false;
          this.changingTimer = false;
          return;
        }
        this.changeTimer(this.timer2Factor.getValue());
        this.timer2Tick++;
      }
    }
  }

  @Override
  public void onEnabled() {
    this.boosted = false;
    this.tick1Start = false;
    this.tick2Start = false;
    this.timer1Tick = 0;
    this.timer2Tick = 0;
    this.changingTimer = false;
  }

  @Override
  public void onDisabled() {
    if (this.changeTimer.getValue()) this.changeTimer(1f);
  }
}