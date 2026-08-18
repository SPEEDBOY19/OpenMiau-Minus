package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class PolarJumpVelocity extends VelocityMode {
  public final IntProperty forceChangeHurtTimeCount =
      new IntProperty("force-change-hurt-time-count", 3, 1, 20);
  public final BooleanProperty polarJumpDebugger = new BooleanProperty("debug", false);

  private int polarHurtTime = 0;
  private int polarHurtCount = 0;

  public PolarJumpVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    polarHurtTime = VelocityUtil.randomInt(7, 10);
    polarHurtCount = 0;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == player.getEntityId()) {
        polarHurtCount++;
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (polarHurtTime == player.hurtTime && player.onGround) {
      VelocityUtil.tryJump();
      if (polarJumpDebugger.getValue()) {
        ChatUtil.display("[PolarJump] Jumped");
      }
      polarHurtTime = VelocityUtil.randomInt(7, 10);
      if (polarJumpDebugger.getValue()) {
        ChatUtil.display("[PolarJump] NextJumpHurtTime: " + polarHurtTime);
      }
    }
    if (polarHurtCount >= forceChangeHurtTimeCount.getValue()) {
      polarHurtCount = 0;
      polarHurtTime = VelocityUtil.randomInt(7, 10);
      if (polarJumpDebugger.getValue()) {
        ChatUtil.display("[PolarJump] ForceChangeJumpHurtTime-NextJumpHurtTime: " + polarHurtTime);
      }
    }
  }

  @Override
  public void onDisable() {
    polarHurtTime = 0;
    polarHurtCount = 0;
  }
}