package miau.module.modules.combat;

import com.google.common.base.CaseFormat;
import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.mixin.IAccessorEntity;
import miau.module.Module;
import miau.module.modules.combat.velocity.*;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class Velocity extends Module {
  public static final Minecraft mc = Minecraft.getMinecraft();

  public int chanceCounter = 0;
  public int delayChanceCounter = 0;
  public boolean pendingExplosion = false;
  public boolean allowNext = true;
  public boolean jumpFlag = false;
  public boolean reverseFlag = false;
  public boolean delayActive = false;

  public boolean shouldJump = false;
  public int jumpCooldown = 0;
  public boolean hasReceivedVelocity = false;
  public int legitSmartJumpCount = 0;
  public int intaveTick = 0;
  public int intaveDamageTick = 0;

  public final BooleanProperty onSwing = new BooleanProperty("on-swing", false);

  public final List<VelocityMode> modes = new ArrayList<>();

  public final ModeProperty mode =
      new ModeProperty(
          "mode",
          0,
          new String[] {
            register(new ThreeFPracVelocity("3FPrac", this)),
            register(new StandardVelocity("Standard", this)),
            register(new LegitVelocity("Legit", this)),
            register(new IntaveVelocity("Intave", this)),
            register(new DelayVelocity("Delay", this)),
            register(new PolarVelocity("Polar", this)),
            register(new AttackReduceVelocity("AttackReduce", this)),
            register(new GrimReduceVelocity("GrimReduce", this)),
            register(new LuckyvnVelocity("Luckyvn", this)),
            register(new IntaveAVelocity("IntaveA", this)),
            register(new SimpleVelocity("Simple", this)),
            register(new CancelVelocity("Cancel", this)),
            register(new AACVelocity("AAC", this)),
            register(new AACPushVelocity("AACPush", this)),
            register(new AACZeroVelocity("AACZero", this)),
            register(new AACv4Velocity("AACv4", this)),
            register(new AAC5Velocity("AAC5", this)),
            register(new ReverseVelocity("Reverse", this)),
            register(new SmoothReverseVelocity("SmoothReverse", this)),
            register(new JumpVelocity("Jump", this)),
            register(new GlitchVelocity("Glitch", this)),
            register(new GhostBlockVelocity("GhostBlock", this)),
            register(new VulcanVelocity("Vulcan", this)),
            register(new S32PacketVelocity("S32Packet", this)),
            register(new MatrixReduceVelocity("MatrixReduce", this)),
            register(new MatrixReduce2Velocity("MatrixReduce2", this)),
            register(new MatrixReduce3Velocity("MatrixReduce3", this)),
            register(new LiquidBounceDelayVelocity("LiquidBounceDelay", this)),
            register(new GrimC03Velocity("GrimC03", this)),
            register(new BufferAbuseVelocity("BufferAbuse", this)),
            register(new FBDelayVelocity("DelayFB", this)),
            register(new CustomVelocity("Custom", this)),
            register(new LegitClickVelocity("LegitClick", this)),
            register(new GrimVerticalVelocity("GrimVertical", this)),
            register(new OldGrimVelocity("OldGrim", this)),
            register(new PolarJumpVelocity("PolarJump", this)),
            register(new OldPolarVelocity("OldPolar", this)),
            register(new BuzzReverseVelocity("BuzzReverse", this)),
            register(new Intave14Velocity("Intave14", this)),
            register(new Intave13Velocity("Intave13.0.6", this)),
            register(new Intave1433Velocity("Intave14.3.3", this)),
            register(new Intave1412Velocity("Intave14.1.2", this)),
            register(new IntaveTimerVelocity("IntaveTimer", this)),
            register(new IntaveFlagVelocity("IntaveFlag", this)),
            register(new IntaveStrongVelocity("IntaveStrong", this)),
            register(new KarhuVelocity("Karhu", this)),
            register(new KazerVelocity("Kazer", this)),
            register(new UniversoCraftOldVelocity("UniversoCraftOld", this)),
            register(new BlocksMCVelocity("BlocksMC", this)),
            register(new HylexVelocity("Hylex", this)),
            register(new DexlandVelocity("Dexland", this)),
            register(new HypixelVelocity("Hypixel", this)),
            register(new HypixelAirVelocity("HypixelAir", this)),
            register(new HypixelMovingVelocity("HypixelMoving", this))
          });

  private String register(VelocityMode m) {
    this.modes.add(m);
    return m.getName();
  }

  public boolean isInLiquidOrWeb() {
    return mc.thePlayer.isInWater()
        || mc.thePlayer.isInLava()
        || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
  }

  public boolean canDelay() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    return mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
  }

  public Velocity() {
    super("Velocity", false);
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (VelocityMode m : modes) {
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
        } catch (Exception e) {
        }
      }
    }
    return props;
  }

  public VelocityMode getActiveMode() {
    return modes.stream()
        .filter(m -> m.getName().equals(mode.getModeString()))
        .findFirst()
        .orElse(modes.get(0));
  }

  @Override
  public void onEnabled() {
    getActiveMode().onEnable();
  }

  @EventTarget
  public void onKnockback(KnockbackEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onKnockback(event);
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onUpdate(event);
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onLivingUpdate(event);
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onStrafe(event);
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onJump(event);
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onRender3D(event);
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onMoveInput(event);
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onAttack(event);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onPacket(event);
    }
  }

  @EventTarget
  public void onHitSlowDown(HitSlowDownEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onHitSlowDown(event);
    }
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    this.onDisabled();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onTick(event);
    }
  }

  @Override
  public void onDisabled() {
    getActiveMode().onDisable();
    this.pendingExplosion = false;
    this.allowNext = true;
    this.shouldJump = false;
    this.jumpCooldown = 0;
    this.hasReceivedVelocity = false;
    this.legitSmartJumpCount = 0;
    this.intaveTick = 0;
    this.intaveDamageTick = 0;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }
}
