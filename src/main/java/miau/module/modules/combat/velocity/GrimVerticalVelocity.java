package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorS12PacketEntityVelocity;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class GrimVerticalVelocity extends VelocityMode {
  public final ModeProperty mode =
      new ModeProperty(
          "reveal-mode",
          0,
          new String[] {"Vertical", "1.17", "Reduce"});
  public final BooleanProperty callEvent = new BooleanProperty("call-event", false);
  public final BooleanProperty via = new BooleanProperty("via", false);
  public final BooleanProperty smartVelo = new BooleanProperty("smart-velo", false);
  public final FloatProperty motionXZ = new FloatProperty("motion-xz", 0.05f, 0.01f, 0.2f);
  public final IntProperty c0fPacketAmount = new IntProperty("c0f-packet-amount", 2, 1, 10);
  public final BooleanProperty sendC0FValue = new BooleanProperty("send-c0f", true);

  private boolean canCancel = false;
  private boolean canSpoof = false;
  private boolean attack = false;
  private boolean velocityInput = false;
  private float savedMotionXZ = 0.05f;

  public GrimVerticalVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() != player.getEntityId()) return;
      String m = mode.getModeString().toLowerCase();
      if (m.equals("reduce")) {
        double velocityX = packet.getMotionX() / 8000.0;
        double velocityZ = packet.getMotionZ() / 8000.0;
        player.motionX = velocityX * 0.078;
        player.motionZ = velocityZ * 0.078;
      } else if (m.equals("1.17")) {
        canCancel = true;
        canSpoof = true;
        event.setCancelled(true);
      } else if (m.equals("vertical")) {
        if (packet.getMotionX() == 0 && packet.getMotionZ() == 0) return;
        velocityInput = true;
        savedMotionXZ = getMotionNoXZ(packet);
        if (player.isSprinting() && VelocityUtil.isMoving()) {
          if (sendC0FValue.getValue()) {
            for (int i = 0; i < c0fPacketAmount.getValue(); i++) {
              PacketUtil.sendPacket(
                  new C0FPacketConfirmTransaction(
                      VelocityUtil.randomInt(102, 1000024123),
                      (short) VelocityUtil.randomInt(102, 1000024123),
                      true));
            }
          }
          attack = true;
        }
        event.setCancelled(true);
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    String m = mode.getModeString().toLowerCase();
    if (m.equals("1.17")) {
      if (canSpoof) {
        PacketUtil.sendPacket(
            new C03PacketPlayer.C06PacketPlayerPosLook(
                player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch, player.onGround));
        PacketUtil.sendPacket(
            new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                new BlockPos(player).down(),
                EnumFacing.DOWN));
        canSpoof = false;
      }
    } else if (m.equals("vertical")) {
      if (attack) {
        float reduce = smartVelo.getValue() && player.onGround ? savedMotionXZ : 0.077760000f;
        VelocityUtil.reduceXZ(reduce);
        velocityInput = false;
        attack = false;
      }
    }
  }

  private float getMotionNoXZ(S12PacketEntityVelocity packet) {
    double x = packet.getMotionX();
    double y = packet.getMotionY();
    double z = packet.getMotionZ();
    double strength = Math.sqrt(x * x + y * y + z * z);
    double motionNoXZ;
    if (strength >= 20000.0) {
      motionNoXZ = Velocity.mc.thePlayer.onGround ? 0.06425 : 0.075;
    } else if (strength >= 5000.0) {
      motionNoXZ = Velocity.mc.thePlayer.onGround ? 0.02625 : 0.0552;
    } else {
      motionNoXZ = 0.0175;
    }
    return (float) motionNoXZ;
  }

  @Override
  public void onDisable() {
    canCancel = false;
    canSpoof = false;
    attack = false;
    velocityInput = false;
  }
}