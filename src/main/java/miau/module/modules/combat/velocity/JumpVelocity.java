package miau.module.modules.combat.velocity;

import miau.event.impl.JumpEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.module.modules.combat.velocity.VelocityUtil;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class JumpVelocity extends VelocityMode {
  public final IntProperty chance = new IntProperty("chance", 100, 0, 100);
  public final ModeProperty jumpCooldownMode =
      new ModeProperty("jump-cooldown-mode", 0, new String[] {"Ticks", "ReceivedHits"});
  public final IntProperty ticksUntilJump =
      new IntProperty("ticks-until-jump", 4, 0, 20, () -> jumpCooldownMode.getValue() == 0);
  public final IntProperty hitsUntilJump =
      new IntProperty("received-hits-until-jump", 2, 0, 5, () -> jumpCooldownMode.getValue() == 1);
  public final BooleanProperty jumpResetOnlyOnSwing = new BooleanProperty("jump-reset-only-on-swing", false);

  private int limitUntilJump = 0;
  private boolean hasReceivedVelocity = false;
  private boolean shouldJump = false;

  public JumpVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    limitUntilJump = 0;
    hasReceivedVelocity = false;
    shouldJump = false;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    double packetDirection = 0.0;
    boolean velocity = false;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() != player.getEntityId()) return;
      packetDirection = Math.atan2(packet.getMotionX(), packet.getMotionZ());
      velocity = true;
    } else if (event.getPacket() instanceof S27PacketExplosion) {
      double motionX = player.motionX + ((S27PacketExplosion) event.getPacket()).func_149149_c();
      double motionZ = player.motionZ + ((S27PacketExplosion) event.getPacket()).func_149147_e();
      packetDirection = Math.atan2(motionX, motionZ);
      velocity = true;
    }
    if (!velocity) return;

    double degreePlayer = VelocityUtil.getDirection();
    double degreePacket = Math.floorMod((int) Math.toDegrees(packetDirection), 360);
    double angle = Math.abs(degreePacket + degreePlayer);
    angle = Math.floorMod((int) angle, 360);
    double threshold = 120.0;
    boolean inRange = angle >= 180 - threshold / 2 && angle <= 180 + threshold / 2;
    if (inRange) hasReceivedVelocity = true;
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (jumpCooldownMode.getValue() == 0) limitUntilJump++;
    else if (player.hurtTime == 9) limitUntilJump++;

    if (hasReceivedVelocity) {
      boolean ready =
          jumpCooldownMode.getValue() == 0
              ? limitUntilJump >= ticksUntilJump.getValue()
              : limitUntilJump >= hitsUntilJump.getValue();
      boolean swinging = !jumpResetOnlyOnSwing.getValue() || player.isSwingInProgress;
      if (!((miau.mixin.IAccessorEntityLivingBase) player).getIsJumping()
          && VelocityUtil.randomInt(0, 100) <= chance.getValue()
          && ready
          && swinging
          && player.onGround
          && player.hurtTime == 9
          && player.isSprinting()) {
        VelocityUtil.tryJump();
        limitUntilJump = 0;
      }
      hasReceivedVelocity = false;
    }
  }

  @Override
  public void onDisable() {
    limitUntilJump = 0;
    hasReceivedVelocity = false;
    shouldJump = false;
  }
}