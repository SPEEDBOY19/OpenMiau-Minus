package miau.module.modules.combat.velocity;

import miau.Miau;
import miau.event.EventManager;
import miau.event.impl.AttackEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import miau.util.player.MoveUtil;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class AttackReduceVelocity extends VelocityMode {

  private boolean slot = false;
  private boolean attack = false;
  private boolean swing = false;
  private boolean block = false;
  private boolean inventory = false;
  private boolean dig = false;

  public final BooleanProperty reduce = new BooleanProperty("Reduce", true);
  public final BooleanProperty tickExactEnable = new BooleanProperty("TickExact", true);
  public final IntProperty tick500 = new IntProperty("500", 3, 0, 20);
  public final IntProperty tick1000 = new IntProperty("1000", 4, 0, 20);
  public final IntProperty tick2000 = new IntProperty("2000", 4, 0, 20);
  public final IntProperty tick3000 = new IntProperty("3000", 5, 0, 20);
  public final IntProperty tick4000 = new IntProperty("4000", 6, 0, 20);
  public final IntProperty tick5000 = new IntProperty("5000", 6, 0, 20);
  public final IntProperty tick6000 = new IntProperty("6000", 7, 0, 20);
  public final IntProperty tick7000 = new IntProperty("7000", 7, 0, 20);
  public final IntProperty tick8000 = new IntProperty("8000", 8, 0, 20);
  public final IntProperty tick9000 = new IntProperty("9000", 8, 0, 20);
  public final IntProperty tick10000 = new IntProperty("10000", 9, 0, 20);

  private int reduceTicks = 0;
  private int anInt = 0;

  public AttackReduceVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    reduceTicks = 0;
    anInt = 0;
  }

  @Override
  public void onDisable() {
    reduceTicks = 0;
    anInt = 0;
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (mc.thePlayer == null) return;

    if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
      Packet<?> packet = event.getPacket();
      if (packet instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
        if (velocity.getEntityID() == mc.thePlayer.getEntityId()) {
          this.reduceTicks = ReduceTicks(velocity.getMotionX(), velocity.getMotionZ());
        }
      }
    }

    if (event.getType() == EventType.SEND && !event.isCancelled()) {
      Packet<?> packet = event.getPacket();

      if (packet instanceof C09PacketHeldItemChange) {
        slot = true;
      } else if (packet instanceof C0APacketAnimation) {
        swing = true;
      } else if (packet instanceof C02PacketUseEntity) {
        C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
        if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
          attack = true;
        }
      } else if (packet instanceof C08PacketPlayerBlockPlacement) {
        block = true;
      } else if (packet instanceof C07PacketPlayerDigging) {
        block = true;
        dig = true;
      } else if (packet instanceof C0DPacketCloseWindow ||
                 packet instanceof C0EPacketClickWindow ||
                 (packet instanceof C16PacketClientStatus &&
                  ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)) {
        inventory = true;
      } else if (packet instanceof C03PacketPlayer) {
        resetBadPackets();
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (reduceTicks <= 0 || !reduce.getValue()) return;

    reduceTicks--;

    Module killAura = Miau.moduleManager.modules.get(KillAura.class);
    if (!(killAura instanceof KillAura) || !killAura.isEnabled()) return;

    EntityLivingBase target = ((KillAura) killAura).getTarget();
    if (target == null) return;

    if (((IAccessorEntity) mc.thePlayer).getIsInWeb()) return;
    if (!mc.thePlayer.isSprinting()) return;
    if (!MoveUtil.isMoving()) return;
    if (target == mc.thePlayer) return;
    if (badPackets()) return;

    if (mc.getNetHandler() != null) {
      EventManager.call(new AttackEvent(target));
      mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
      mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
    }

    mc.thePlayer.motionX *= 0.6;
    mc.thePlayer.motionZ *= 0.6;
    mc.thePlayer.setSprinting(false);

    anInt++;
    ChatUtil.sendRaw("Reduce" + anInt);
  }

  private int ReduceTicks(int motionX, int motionZ) {
    double kb = Math.hypot(motionX, motionZ);

    if (!tickExactEnable.getValue()) {
      double ticks = 6.43153527E-4 * kb + 2.9419087136;
      int result = (int) Math.round(ticks);
      if (result < 1) result = 1;
      if (result > 10) result = 10;
      return result;
    }

    if (kb <= 500) return tick500.getValue();
    if (kb <= 1000) return tick1000.getValue();
    if (kb <= 2000) return tick2000.getValue();
    if (kb <= 3000) return tick3000.getValue();
    if (kb <= 4000) return tick4000.getValue();
    if (kb <= 5000) return tick5000.getValue();
    if (kb <= 6000) return tick6000.getValue();
    if (kb <= 7000) return tick7000.getValue();
    if (kb <= 8000) return tick8000.getValue();
    if (kb <= 9000) return tick9000.getValue();
    return tick10000.getValue();
  }

  private boolean badPackets() {
    return badPackets(false, false, false, false, false, false);
  }

  private boolean badPackets(boolean p1, boolean p2, boolean p3, boolean p4, boolean p5, boolean p6) {
    if (slot && !p1) return true;
    if (attack && !p2) return true;
    if (swing && !p3) return true;
    if (block && !p4) return true;
    if (inventory && !p5) return true;
    if (dig && !p6) return true;
    return false;
  }

  private void resetBadPackets() {
    slot = false;
    swing = false;
    attack = false;
    block = false;
    inventory = false;
    dig = false;
  }
}
