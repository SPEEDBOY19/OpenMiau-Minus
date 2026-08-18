package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.math.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

public class AdvancedJumpReset extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final ModeProperty jumpRate =
      new ModeProperty(
          "How to calculate jump rate",
          1,
          new String[] {"Tick since last velocity", "Simple RNG", "Polar Safe RNG"});
  private final IntProperty minTickSinceLastVL =
      new IntProperty(
          "Min ticks since last jump reset",
          3,
          0,
          20,
          () -> jumpRate.getModeString().equals("Tick since last velocity"));
  private final IntProperty simpleRNG =
      new IntProperty(
          "Simple RNG jump rate",
          75,
          0,
          100,
          () -> jumpRate.getModeString().equals("Simple RNG"));

  private final ModeProperty pauseWhen =
      new ModeProperty(
          "Pause jump reset when",
          0,
          new String[] {"Server flag packet received", "Not in combat", "Both"});
  private final IntProperty serverLagTick =
      new IntProperty(
          "Min Tick since server lag packet received",
          3,
          0,
          20,
          () -> pauseWhen.getModeString().contains("Server flag packet received"));
  private final IntProperty notInCombatTick =
      new IntProperty(
          "Min Tick since not in combat",
          3,
          0,
          20,
          () -> pauseWhen.getModeString().contains("Not in combat"));

  private final FloatProperty hurtTimeRange =
      new FloatProperty("Jump reset if hurt time in", 5f, 9f, 1f, 10f);

  private final ModeProperty howToJump =
      new ModeProperty("How to jump", 0, new String[] {"Functional", "Legitimize", "Motion"});
  private final FloatProperty motionHeight =
      new FloatProperty(
          "Motion height", 0.42f, 0.1f, 1f, () -> howToJump.getModeString().equals("Motion"));

  private final BooleanProperty reduce = new BooleanProperty("Reduce", false);
  private final ModeProperty reduceEvent =
      new ModeProperty(
          "Reduce when what happened",
          1,
          new String[] {"Jumped", "Hurt time updated"},
          () -> reduce.getValue());
  private final ModeProperty reduceMode =
      new ModeProperty(
          "Reduce calculation", 0, new String[] {"Linear", "Smooth"}, () -> reduce.getValue());
  private final FloatProperty reduceHurtTime =
      new FloatProperty(
          "Reduce hurt time by",
          1f,
          3f,
          1f,
          10f,
          () -> reduce.getValue() && reduceEvent.getModeString().equals("Hurt time updated"));
  private final FloatProperty reduceFactor =
      new FloatProperty("Basic reduce factor", 0.6f, 0f, 1f, () -> reduce.getValue());
  private final FloatProperty reduceFactorWhileHit =
      new FloatProperty("Reduce factor while hitting", 0.6f, 0f, 1f, () -> reduce.getValue());
  private final FloatProperty reduceFactorWhileSprint =
      new FloatProperty("Reduce factor while sprinting", 0.6f, 0f, 1f, () -> reduce.getValue());
  private final FloatProperty reduceFactorWhileHitSprint =
      new FloatProperty(
          "Reduce factor while hitting and sprinting", 0.6f, 0f, 1f, () -> reduce.getValue());

  private final FloatProperty activeMotion = new FloatProperty("Active motion", 700f, 7000f, 300f, 32000f);
  private final BooleanProperty stopWhenBackward = new BooleanProperty("Stop when S Pressed", true);
  private final BooleanProperty stopWhenBlocking = new BooleanProperty("Stop when Blocking", true);
  private final BooleanProperty stopWhenSneaking = new BooleanProperty("Stop when Sneaking", true);
  private final BooleanProperty stopWhenFire = new BooleanProperty("Stop when on fire", true);
  private final BooleanProperty stopWhenSpeed = new BooleanProperty("Stop when Speed potion", false);
  private final BooleanProperty stopWhenJumpBoost =
      new BooleanProperty("Stop when Jump Boost", false);
  private final BooleanProperty stopWhenInInventory =
      new BooleanProperty("Stop when in inventory", true);
  private final BooleanProperty stopWhenBadSurrounding =
      new BooleanProperty("Stop when in bad surrounding", true);
  private final BooleanProperty stopWhenInAir = new BooleanProperty("Stop when in air", true);

  private int tickSinceLastVelocity = 0;
  private int tickSinceLastAttack = 0;
  private int tickSinceLastFlag = 0;
  private boolean shouldJump = false;
  private int lastHurtTime = 0;
  private int lastVelocitySize = 0;

  public AdvancedJumpReset() {
    super("AdvancedJumpReset", false);
  }

  private void onHurtTimeUpdate() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (shouldReduce() && reduceEvent.getModeString().equals("Hurt time updated")) {
      doReduce();
    }
    if (player.hurtTime >= hurtTimeRange.getValue()
        && player.hurtTime <= hurtTimeRange.getSecondValue()
        && canJump(lastVelocitySize)
        && shouldJump) {
      doJump();
      if (shouldReduce() && reduceEvent.getModeString().equals("Jumped")) {
        doReduce();
      }
      tickSinceLastVelocity = 0;
      shouldJump = false;
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime != lastHurtTime) {
      lastHurtTime = player.hurtTime;
      onHurtTimeUpdate();
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
        shouldJump = true;
        lastVelocitySize =
            (int)
                MathHelper.sqrt_double(
                    packet.getMotionX() * packet.getMotionX()
                        + packet.getMotionZ() * packet.getMotionZ());
      }
      return;
    }
    if (event.getPacket() instanceof S08PacketPlayerPosLook) {
      tickSinceLastFlag = 0;
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    tickSinceLastAttack = 0;
  }

  private void doReduce() {
    if (!reduce.getValue()) return;
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime >= reduceHurtTime.getValue()
        && player.hurtTime <= reduceHurtTime.getSecondValue()) {
      float original = reduceFactor.getValue();
      if (player.isSprinting()) original = reduceFactorWhileSprint.getValue();
      if (tickSinceLastAttack < 3) original = reduceFactorWhileHit.getValue();
      if (player.isSprinting() && tickSinceLastAttack < 3) {
        original = reduceFactorWhileHitSprint.getValue();
      }
      float amount = original;
      if (reduceMode.getModeString().equals("Smooth")) {
        amount = 1 - original;
      }
      amount = MathHelper.clamp_float(amount, 0f, 1f);
      player.motionX *= amount;
      player.motionZ *= amount;
    }
  }

  private void doJump() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;
    switch (howToJump.getModeString()) {
      case "Functional":
        if (!mc.gameSettings.keyBindJump.isKeyDown()) {
          player.jump();
        }
        break;
      case "Legitimize":
        KeyBinding.onTick(mc.gameSettings.keyBindJump.getKeyCode());
        break;
      case "Motion":
        player.motionY = motionHeight.getValue();
        break;
    }
  }

  private boolean shouldPause() {
    switch (pauseWhen.getModeString()) {
      case "Server flag packet received":
        return tickSinceLastFlag < serverLagTick.getValue();
      case "Not in combat":
        return tickSinceLastAttack < notInCombatTick.getValue();
      case "Both":
        return tickSinceLastFlag < serverLagTick.getValue()
            || tickSinceLastAttack < notInCombatTick.getValue();
    }
    return false;
  }

  private boolean shouldReduce() {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return false;
    if (reduceEvent.getModeString().equals("Jumped")) {
      return shouldJump;
    }
    return player.hurtTime >= hurtTimeRange.getValue()
        && player.hurtTime <= hurtTimeRange.getSecondValue();
  }

  private void resetAll() {
    tickSinceLastVelocity = 0;
    tickSinceLastAttack = 0;
    tickSinceLastFlag = 0;
    shouldJump = false;
    lastHurtTime = 0;
    lastVelocitySize = -1;
  }

  @Override
  public void onEnabled() {
    resetAll();
  }

  @Override
  public void onDisabled() {
    resetAll();
  }

  @EventTarget
  public void onWorld(LoadWorldEvent event) {
    resetAll();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    tickSinceLastVelocity++;
    tickSinceLastAttack++;
    tickSinceLastFlag++;
  }

  private boolean canJump(int xzAverageMotion) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return false;
    if (xzAverageMotion < activeMotion.getValue()
        || xzAverageMotion > activeMotion.getSecondValue()) {
      return false;
    }
    if (stopWhenBlocking.getValue() && player.isBlocking()) return false;
    if (stopWhenBackward.getValue() && player.moveForward == -1.0F) return false;
    if (stopWhenSneaking.getValue() && player.isSneaking()) return false;
    if (stopWhenFire.getValue() && player.isBurning()) return false;
    if (stopWhenSpeed.getValue() && player.isPotionActive(Potion.moveSpeed)) return false;
    if (stopWhenJumpBoost.getValue() && player.isPotionActive(Potion.jump)) return false;
    if (stopWhenInInventory.getValue() && mc.currentScreen != null) return false;
    if (stopWhenBadSurrounding.getValue()
        && (player.isCollidedHorizontally
            || player.isInWater()
            || player.isInLava()
            || player.isOnLadder()
            || ((IAccessorEntity) player).getIsInWeb())) {
      return false;
    }
    if (stopWhenInAir.getValue() && !player.onGround) return false;
    if (shouldPause()) return false;
    switch (jumpRate.getModeString()) {
      case "Tick since last velocity":
        return tickSinceLastVelocity >= minTickSinceLastVL.getValue();
      case "Simple RNG":
        return RandomUtil.nextInt(0, 100) <= simpleRNG.getValue();
      case "Polar Safe RNG":
        return player.ticksExisted % 2 == 0;
    }
    return false;
  }
}