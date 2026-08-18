package miau;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.florianmichael.viamcp.ViaMCP;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import me.ksyz.accountmanager.AccountManager;
import miau.command.CommandManager;
import miau.command.commands.*;
import miau.config.Config;
import miau.event.EventManager;
import miau.management.*;
import miau.module.Module;
import miau.module.ModuleManager;
import miau.module.modules.combat.*;
import miau.module.modules.combat.killaura.autoblocks.LegitAutoBlock;
import miau.module.modules.ghost.AutoBed;
import miau.module.modules.ghost.*;
import miau.module.modules.minigames.*;
import miau.module.modules.misc.*;
import miau.module.modules.movement.*;
import miau.module.modules.network.*;
import miau.module.modules.player.*;
import miau.module.modules.render.*;
import miau.module.modules.render.Statistics;
import miau.module.modules.render.TargetMark;
import miau.notification.NotificationManager;
import miau.notification.NotificationRenderer;
import miau.property.Property;
import miau.property.PropertyManager;
import miau.util.client.SoundUtil;
import org.lwjgl.opengl.Display;

public class Miau {
  public static final boolean DEVELOPMENT_SWITCH = false;
  public static String clientName = "&7[&cM&6i&ea&au&7-]&r ";
  public static String version = ClientInfo.VERSION;
  public static RotationManager rotationManager;
  public static FloatManager floatManager;
  public static BlinkManager blinkManager;
  public static DelayManager delayManager;
  public static LagManager lagManager;
  public static PlayerStateManager playerStateManager;
  public static FriendManager friendManager;
  public static TargetManager targetManager;
  public static PropertyManager propertyManager;
  public static ModuleManager moduleManager;
  public static CommandManager commandManager;
  public static DiscordRichPresence discordRichPresence;
  public static NotificationManager notificationManager;
  public static DragManager dragManager;
  public static miau.util.player.PlayerTracker playerTracker;
  public static miau.component.BadPacketsComponent badPacketsComponent;
  public static miau.component.SlotComponent slotComponent;

  public Miau() {
    this.init();
  }

  public void init() {
    rotationManager = new RotationManager();
    floatManager = new FloatManager();
    blinkManager = new BlinkManager();
    delayManager = new DelayManager();
    lagManager = new LagManager();
    playerStateManager = new PlayerStateManager();
    friendManager = new FriendManager();
    targetManager = new TargetManager();
    propertyManager = new PropertyManager();
    moduleManager = new ModuleManager();
    commandManager = new CommandManager();
    discordRichPresence = new DiscordRichPresence();
    notificationManager = new NotificationManager();
    dragManager = new DragManager();

    EventManager.register(rotationManager);
    EventManager.register(floatManager);
    EventManager.register(blinkManager);
    EventManager.register(delayManager);
    EventManager.register(lagManager);
    EventManager.register(moduleManager);
    EventManager.register(commandManager);
    EventManager.register(discordRichPresence);
    EventManager.register(NotificationRenderer.getInstance());
    EventManager.register(dragManager);

    badPacketsComponent = new miau.component.BadPacketsComponent();
    EventManager.register(badPacketsComponent);
    slotComponent = new miau.component.SlotComponent();
    EventManager.register(slotComponent);
    EventManager.register(new miau.component.PingSpoofComponent());
    EventManager.register(new miau.component.BlinkComponent());
    EventManager.register(new miau.component.RotationComponent());
    playerTracker = new miau.util.player.PlayerTracker();
    EventManager.register(playerTracker);
    moduleManager.modules.put(AimAssist.class, new AimAssist());
    moduleManager.modules.put(Ambience.class, new Ambience());
    moduleManager.modules.put(Animations.class, new Animations());
    moduleManager.modules.put(AntiAFK.class, new AntiAFK());
    moduleManager.modules.put(AntiBot.class, new AntiBot());
    moduleManager.modules.put(AntiCheatDetector.class, new AntiCheatDetector());
    moduleManager.modules.put(AntiDebuff.class, new AntiDebuff());
    moduleManager.modules.put(AntiFireball.class, new AntiFireball());
    moduleManager.modules.put(AntiObbyTrap.class, new AntiObbyTrap());
    moduleManager.modules.put(AntiObfuscate.class, new AntiObfuscate());
    moduleManager.modules.put(AntiVoid.class, new AntiVoid());
    moduleManager.modules.put(AutoAnduril.class, new AutoAnduril());
    moduleManager.modules.put(AutoAuth.class, new AutoAuth());
    moduleManager.modules.put(AutoGG.class, new AutoGG());
    moduleManager.modules.put(AutoPlay.class, new AutoPlay());
    moduleManager.modules.put(AutoBlockIn.class, new AutoBlockIn());
    moduleManager.modules.put(AutoBlock.class, new AutoBlock());
    moduleManager.modules.put(AutoBed.class, new AutoBed());
    moduleManager.modules.put(AutoBuy.class, new AutoBuy());
    moduleManager.modules.put(AutoChest.class, new AutoChest());
    moduleManager.modules.put(AutoClicker.class, new AutoClicker());
    moduleManager.modules.put(AutoHead.class, new AutoHead());
    moduleManager.modules.put(AutoLadderClutch.class, new AutoLadderClutch());
    moduleManager.modules.put(BlockLadder.class, new BlockLadder());
    moduleManager.modules.put(BslLegitTellyFix.class, new BslLegitTellyFix());
    moduleManager.modules.put(SpotifyMod.class, new SpotifyMod());
    moduleManager.modules.put(AutoReconnect.class, new AutoReconnect());
    moduleManager.modules.put(AutoSoup.class, new AutoSoup());
    moduleManager.modules.put(AutoSwap.class, new AutoSwap());
    moduleManager.modules.put(AutoTool.class, new AutoTool());
    moduleManager.modules.put(BackTrack.class, new BackTrack());
    moduleManager.modules.put(BedESP.class, new BedESP());
    moduleManager.modules.put(BedNuker.class, new BedNuker());
    moduleManager.modules.put(BedTracker.class, new BedTracker());
    moduleManager.modules.put(BedwarsUtils.class, new BedwarsUtils());
    moduleManager.modules.put(PartyDetector.class, new PartyDetector());
    moduleManager.modules.put(Blink.class, new Blink());
    moduleManager.modules.put(Blinkvoid.class, new Blinkvoid());
    moduleManager.modules.put(BlockHit.class, new BlockHit());
    moduleManager.modules.put(BlockOverlay.class, new BlockOverlay());
    moduleManager.modules.put(BreakProgress.class, new BreakProgress());
    moduleManager.modules.put(BridgeAssist.class, new BridgeAssist());
    moduleManager.modules.put(Capes.class, new Capes());
    moduleManager.modules.put(Chams.class, new Chams());
    moduleManager.modules.put(CheatDetector.class, new CheatDetector());
    moduleManager.modules.put(ChestESP.class, new ChestESP());
    moduleManager.modules.put(ChestStealer.class, new ChestStealer());
    moduleManager.modules.put(ClickGUI.class, new ClickGUI());
    moduleManager.modules.put(NoGui.class, new NoGui());
    moduleManager.modules.put(ClientSpoofer.class, new ClientSpoofer());
    moduleManager.modules.put(Clutch.class, new Clutch());
    moduleManager.modules.put(Disabler.class, new Disabler());
    moduleManager.modules.put(Displace.class, new Displace());
    moduleManager.modules.put(ESP.class, new ESP());
    moduleManager.modules.put(EntityCulling.class, new EntityCulling());    moduleManager.modules.put(FakeLag.class, new FakeLag());
    moduleManager.modules.put(FastPlace.class, new FastPlace());
    moduleManager.modules.put(FlagDetector.class, new FlagDetector());
    moduleManager.modules.put(Fly.class, new Fly());
    moduleManager.modules.put(FreeLook.class, new FreeLook());
    moduleManager.modules.put(Freeze.class, new Freeze());
    moduleManager.modules.put(FullBright.class, new FullBright());
    moduleManager.modules.put(Fireball.class, new Fireball());
    moduleManager.modules.put(GhostHand.class, new GhostHand());
    moduleManager.modules.put(JumpReset.class, new JumpReset());
    moduleManager.modules.put(HUD.class, new HUD());
    moduleManager.modules.put(HypixelDisabler.class, new HypixelDisabler());
    moduleManager.modules.put(HitBox.class, new HitBox());
    moduleManager.modules.put(Indicators.class, new Indicators());
    moduleManager.modules.put(InvManager.class, new InvManager());
    moduleManager.modules.put(InvWalk.class, new InvWalk());
    moduleManager.modules.put(ItemESP.class, new ItemESP());
    moduleManager.modules.put(ItemPhysics.class, new ItemPhysics());
    moduleManager.modules.put(Jesus.class, new Jesus());
    moduleManager.modules.put(KeepSprint.class, new KeepSprint());
    moduleManager.modules.put(Keystrokes.class, new Keystrokes());
    moduleManager.modules.put(KillAura.class, new KillAura());    moduleManager.modules.put(KillAura2.class, new KillAura2());
    moduleManager.modules.put(KillEffect.class, new KillEffect());
    moduleManager.modules.put(KillSults.class, new KillSults());
    moduleManager.modules.put(KnockbackDelay.class, new KnockbackDelay());
    moduleManager.modules.put(KnockbackBoost.class, new KnockbackBoost());
    moduleManager.modules.put(LagRange.class, new LagRange());
    moduleManager.modules.put(LagRange2.class, new LagRange2());
    moduleManager.modules.put(LightningTracker.class, new LightningTracker());
    moduleManager.modules.put(LongJump.class, new LongJump());
    moduleManager.modules.put(MoveFix.class, new MoveFix());
    moduleManager.modules.put(MouseRawInput.class, new MouseRawInput());
    moduleManager.modules.put(MurderDetector.class, new MurderDetector());
    moduleManager.modules.put(NameTags.class, new NameTags());
    moduleManager.modules.put(NickHider.class, new NickHider());
    moduleManager.modules.put(NoClickDelay.class, new NoClickDelay());
    moduleManager.modules.put(NoFall.class, new NoFall());
    moduleManager.modules.put(NoHurtCam.class, new NoHurtCam());
    moduleManager.modules.put(NoJumpDelay.class, new NoJumpDelay());
    moduleManager.modules.put(NoRotate.class, new NoRotate());
    moduleManager.modules.put(NoSlow.class, new NoSlow());
    moduleManager.modules.put(NoSlowUtils.class, new NoSlowUtils());
    moduleManager.modules.put(NowPlayingHud.class, new NowPlayingHud());
    moduleManager.modules.put(ViewPackets.class, new ViewPackets());
    moduleManager.modules.put(Panic.class, new Panic());
    moduleManager.modules.put(Piercing.class, new Piercing());
    moduleManager.modules.put(PingSpoof.class, new PingSpoof());
    moduleManager.modules.put(PlayerList.class, new PlayerList());
    moduleManager.modules.put(SkywarsAlerts.class, new SkywarsAlerts());
    moduleManager.modules.put(ProjectileAimBot.class, new ProjectileAimBot());
    moduleManager.modules.put(RPC.class, new RPC());
    moduleManager.modules.put(Reach.class, new Reach());
    moduleManager.modules.put(Refill.class, new Refill());
    moduleManager.modules.put(SafeWalk.class, new SafeWalk());
    moduleManager.modules.put(Scaffold.class, new Scaffold());
    moduleManager.modules.put(Scoreboard.class, new Scoreboard());
    moduleManager.modules.put(Spammer.class, new Spammer());
    moduleManager.modules.put(Speed.class, new Speed());
    moduleManager.modules.put(TimerHop.class, new TimerHop());
    moduleManager.modules.put(TimerBalance.class, new TimerBalance());
    moduleManager.modules.put(SpeedMine.class, new SpeedMine());
    moduleManager.modules.put(Sprint.class, new Sprint());
    moduleManager.modules.put(StaffDetector.class, new StaffDetector());
    moduleManager.modules.put(BalanceFix.class, new BalanceFix());
    moduleManager.modules.put(NoWeb.class, new NoWeb());
    moduleManager.modules.put(Balance.class, new Balance());
    moduleManager.modules.put(SprintReset.class, new SprintReset());
    moduleManager.modules.put(SmartClicking.class, new SmartClicking());
    moduleManager.modules.put(SlinkNotifs.class, new SlinkNotifs());
    moduleManager.modules.put(BetterHud2.class, new BetterHud2());
    moduleManager.modules.put(Statistics.class, new Statistics());
    moduleManager.modules.put(TargetHUD.class, new TargetHUD());
    moduleManager.modules.put(TargetHud2.class, new TargetHud2());
    moduleManager.modules.put(TargetStrafe.class, new TargetStrafe());
    moduleManager.modules.put(ThePitUtils.class, new ThePitUtils());
    moduleManager.modules.put(TickBase.class, new TickBase());
    moduleManager.modules.put(Tracers.class, new Tracers());
    moduleManager.modules.put(Trajectories.class, new Trajectories());
    moduleManager.modules.put(PearlESP.class, new PearlESP());
    moduleManager.modules.put(FallPosition.class, new FallPosition());
    moduleManager.modules.put(TimerBeacon.class, new TimerBeacon());
    moduleManager.modules.put(UtilsOverlay.class, new UtilsOverlay());
    moduleManager.modules.put(Utility.class, new Utility());
    moduleManager.modules.put(Velocity.class, new Velocity());
    moduleManager.modules.put(ViewClip.class, new ViewClip());
    moduleManager.modules.put(MoreKB.class, new MoreKB());
    moduleManager.modules.put(Statistics.class, new Statistics());
    moduleManager.modules.put(WaterMark.class, new WaterMark());
    moduleManager.modules.put(TargetMark.class, new TargetMark());
    moduleManager.modules.put(CheatDetector.class, new CheatDetector());
    moduleManager.modules.put(Radar.class, new Radar());
    moduleManager.modules.put(AdvancedJumpReset.class, new AdvancedJumpReset());
    moduleManager.modules.put(AirJump.class, new AirJump());
    moduleManager.modules.put(AntiBounce.class, new AntiBounce());
    moduleManager.modules.put(ArmorBreaker.class, new ArmorBreaker());
    moduleManager.modules.put(AutoArmor.class, new AutoArmor());
    moduleManager.modules.put(AutoBow.class, new AutoBow());
    moduleManager.modules.put(AutoGapple.class, new AutoGapple());
    moduleManager.modules.put(AutoLeave.class, new AutoLeave());
    moduleManager.modules.put(AutoPot.class, new AutoPot());
    moduleManager.modules.put(AutoProjectile.class, new AutoProjectile());
    moduleManager.modules.put(AutoRod.class, new AutoRod());
    moduleManager.modules.put(AutoRod2.class, new AutoRod2());
    moduleManager.modules.put(AutoWalk.class, new AutoWalk());
    moduleManager.modules.put(AutoWeapon.class, new AutoWeapon());
    moduleManager.modules.put(BAHalo.class, new BAHalo());
    moduleManager.modules.put(BlockAttack.class, new BlockAttack());
    moduleManager.modules.put(BoatFly.class, new BoatFly());
    moduleManager.modules.put(BufferSpeed.class, new BufferSpeed());
    moduleManager.modules.put(ComboOneHit.class, new ComboOneHit());
    moduleManager.modules.put(FastBow.class, new FastBow());
    moduleManager.modules.put(FastClimb.class, new FastClimb());
    moduleManager.modules.put(FastFall.class, new FastFall());
    moduleManager.modules.put(FastStairs.class, new FastStairs());
    moduleManager.modules.put(Fixes.class, new Fixes());
    moduleManager.modules.put(ForwardTrack.class, new ForwardTrack());
    moduleManager.modules.put(GUIMove.class, new GUIMove());
    moduleManager.modules.put(HighJump.class, new HighJump());
    moduleManager.modules.put(IceSpeed.class, new IceSpeed());
    moduleManager.modules.put(Ignite.class, new Ignite());
    moduleManager.modules.put(LegitReach.class, new LegitReach());
    moduleManager.modules.put(LiquidWalk.class, new LiquidWalk());
    moduleManager.modules.put(MoreClick.class, new MoreClick());
    moduleManager.modules.put(NewVelocity.class, new NewVelocity());
    moduleManager.modules.put(NoClip.class, new NoClip());
    moduleManager.modules.put(NoFluid.class, new NoFluid());
    moduleManager.modules.put(NoFriends.class, new NoFriends());
    moduleManager.modules.put(OldGrimGapple.class, new OldGrimGapple());
    moduleManager.modules.put(Parkour.class, new Parkour());
    moduleManager.modules.put(PerfectHorseJump.class, new PerfectHorseJump());
    moduleManager.modules.put(ReverseStep.class, new ReverseStep());
    moduleManager.modules.put(Scaffold2.class, new Scaffold2());
    moduleManager.modules.put(ScaffoldTest.class, new ScaffoldTest());
    moduleManager.modules.put(SlimeJump.class, new SlimeJump());
    moduleManager.modules.put(SmartBlinker.class, new SmartBlinker());
    moduleManager.modules.put(SmoothAimAssist.class, new SmoothAimAssist());
    moduleManager.modules.put(Sneak.class, new Sneak());
    moduleManager.modules.put(SprintStatesSync.class, new SprintStatesSync());
    moduleManager.modules.put(Step.class, new Step());
    moduleManager.modules.put(Strafe.class, new Strafe());
    moduleManager.modules.put(TeleportHit.class, new TeleportHit());
    moduleManager.modules.put(TimerRange.class, new TimerRange());
    moduleManager.modules.put(TimerRangeV2.class, new TimerRangeV2());
    moduleManager.modules.put(TNTBlock.class, new TNTBlock());
    moduleManager.modules.put(VoidBouncer.class, new VoidBouncer());
    moduleManager.modules.put(WallClimb.class, new WallClimb());
    moduleManager.modules.put(WTap.class, new WTap());
    commandManager.commands.add(new BindCommand());
    commandManager.commands.add(new ConfigCommand());
    commandManager.commands.add(new OnlineConfigCommand());
    commandManager.commands.add(new UserConfigCommand());
    commandManager.commands.add(new DenickCommand());
    commandManager.commands.add(new FriendCommand());
    commandManager.commands.add(new HelpCommand());
    commandManager.commands.add(new HideCommand());
    commandManager.commands.add(new IgnCommand());
    commandManager.commands.add(new ItemCommand());
    commandManager.commands.add(new ListCommand());
    commandManager.commands.add(new ModuleCommand());
    commandManager.commands.add(new PlayerCommand());
    commandManager.commands.add(new ShowCommand());
    commandManager.commands.add(new TargetCommand());
    commandManager.commands.add(new ToggleCommand());
    commandManager.commands.add(new VclipCommand());
    commandManager.commands.add(new ReportCommand());
    for (Module module : moduleManager.modules.values()) {
      ArrayList<Property<?>> properties = new ArrayList<>();
      for (final Field field : module.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        final Object obj;
        try {
          obj = field.get(module);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
        if (obj instanceof Property<?>) {
          ((Property<?>) obj).setOwner(module);
          properties.add((Property<?>) obj);
        }
      }
      for (Property<?> p : module.getAdditionalProperties()) {
        p.setOwner(module);
        properties.add(p);
      }
      propertyManager.properties.put(module.getClass(), properties);
      EventManager.register(module);
    }

    Config config = new Config("default", true);
    if (config.file.exists()) {
      config.load();
    }
    if (friendManager.file.exists()) {
      friendManager.load();
    }
    if (targetManager.file.exists()) {
      targetManager.load();
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (moduleManager != null && propertyManager != null) {
                    config.save();
                  }
                }));

    try (InputStreamReader reader =
        new InputStreamReader(
            Objects.requireNonNull(Miau.class.getResourceAsStream("/version.json")),
            StandardCharsets.UTF_8)) {
      JsonObject modInfo = new JsonParser().parse(reader).getAsJsonObject();
      version = modInfo.get("version").getAsString();
    } catch (Exception e) {
      version = ClientInfo.VERSION;
    }
    Display.setTitle(ClientInfo.getDisplayVersion());

    SoundUtil.playWelcomeSound();

    AccountManager.init();
    ViaMCP.create();
  }

  public static Locale getLocale() {
    return Locale.getDefault();
  }

  public static void setLocale(Locale locale) {
    Locale.setDefault(locale);
  }

  public static void terminate() {}
}
