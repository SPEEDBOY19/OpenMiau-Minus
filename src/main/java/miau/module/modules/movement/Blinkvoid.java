package miau.module.modules.movement;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LivingUpdateEvent;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.module.modules.player.Scaffold;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

public class Blinkvoid extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty offOnScaffold = new BooleanProperty("Off on scaffold", true);
  public final FloatProperty scaffoldDelay = new FloatProperty("Scaffold Delay", 5.0F, 1.0F, 10.0F);
  public final FloatProperty fallDistance = new FloatProperty("Fall Distance", 3.0F, 1.0F, 8.0F);

  private int jumpticks = 0;
  private int scaffoldTimer = 0;
  private boolean falling = false;
  private boolean air = false;
  private boolean killaura = false;
  private boolean ljing = false;

  public Blinkvoid() {
    super("Blinkvoid", false);
  }

  @Override
  public void onEnabled() {
    this.jumpticks = 0;
    this.scaffoldTimer = 0;
    this.falling = false;
    this.air = false;
    this.killaura = false;
    this.ljing = false;
  }

  @Override
  public void onDisabled() {
    if (this.killaura) {
      this.enableKillAura();
    }
    this.falling = false;
    this.air = false;
    this.killaura = false;
    this.disableBlink();
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    double blinkDist = this.fallDistance.getValue();
    int dist = this.fallDistance();

    if (this.scaffoldTimer > 0) this.scaffoldTimer--;

    if (mc.thePlayer.onGround) this.ljing = true;
    if (this.jumpticks-- <= 0
        && !mc.thePlayer.capabilities.isFlying
        && !this.scaffoldDisable()
        && dist == -1
        && !this.falling
        && !mc.thePlayer.onGround
        && this.getKillAuraTarget() == null) {      this.falling = true;
      this.killaura = this.isKillAuraEnabled();
      this.disableKillAura();
      this.enableBlink();
    } else if (this.falling
        && mc.thePlayer.fallDistance > blinkDist
        && dist == -1
        && !this.air) {
      Vec3 pos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
      this.air = true;
      PacketUtil.sendPacketNoEvent(new C04PacketPlayerPosition(pos.xCoord, -420.0, pos.zCoord, false));
      this.disableBlink();
    } else if (this.falling
        && (mc.thePlayer.onGround || dist != -1 || this.getKillAuraTarget() != null)) {
      if (this.killaura) this.enableKillAura();
      this.falling = false;
      this.air = false;
      this.killaura = false;
      this.disableBlink();
    }
  }

  private boolean scaffoldDisable() {
    int delay = (int) (this.scaffoldDelay.getValue() * 20);

    if (this.offOnScaffold.getValue()) {
      Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
      if (scaffold != null && scaffold.isEnabled()) {
        this.scaffoldTimer = delay;
        return true;
      } else if (this.scaffoldTimer > 0) {
        return true;
      }
    }
    return false;
  }

  private int fallDistance() {
    int fallDist = -1;
    double px = mc.thePlayer.posX;
    double pz = mc.thePlayer.posZ;
    int y = (int) Math.floor(mc.thePlayer.posY) - 1;

    for (int i = y; i > -1; i--) {
      net.minecraft.block.Block block = mc.theWorld.getBlockState(new BlockPos((int) Math.floor(px), i, (int) Math.floor(pz))).getBlock();
      if (block == Blocks.air
          || block == Blocks.standing_sign
          || block == Blocks.wall_sign
          || block == Blocks.water
          || block == Blocks.flowing_water
          || block == Blocks.lava
          || block == Blocks.flowing_lava) {
        continue;
      }
      fallDist = y - i;
      break;
    }
    return fallDist;
  }

  private KillAura getKillAura() {
    return (KillAura) Miau.moduleManager.modules.get(KillAura.class);
  }

  private net.minecraft.entity.EntityLivingBase getKillAuraTarget() {
    KillAura ka = this.getKillAura();
    return ka != null ? ka.getTarget() : null;
  }

  private boolean isKillAuraEnabled() {
    KillAura ka = this.getKillAura();
    return ka != null && ka.isEnabled();
  }

  private void enableKillAura() {
    KillAura ka = this.getKillAura();
    if (ka != null && !ka.isEnabled()) ka.setEnabled(true);
  }

  private void disableKillAura() {
    KillAura ka = this.getKillAura();
    if (ka != null && ka.isEnabled()) ka.setEnabled(false);
  }

  private void enableBlink() {
    Blink blink = (Blink) Miau.moduleManager.modules.get(Blink.class);
    if (blink != null && !blink.isEnabled()) blink.setEnabled(true);
  }

  private void disableBlink() {
    Blink blink = (Blink) Miau.moduleManager.modules.get(Blink.class);
    if (blink != null && blink.isEnabled()) blink.setEnabled(false);
  }
}
