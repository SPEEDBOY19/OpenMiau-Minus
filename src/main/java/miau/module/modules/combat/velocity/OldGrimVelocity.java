package miau.module.modules.combat.velocity;

import miau.Miau;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorEntity;
import miau.module.modules.combat.KillAura;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.network.PacketUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class OldGrimVelocity extends VelocityMode {
  public final BooleanProperty oldGrimRayCast = new BooleanProperty("ray-cast", true);
  public final BooleanProperty oldGrimLegit = new BooleanProperty("legit", false);
  public final BooleanProperty webValue = new BooleanProperty("cancel-in-web", false);
  public final BooleanProperty liquidValue = new BooleanProperty("cancel-in-liquid", false);
  public final FloatProperty oldGrimAttackReduce = new FloatProperty("attack-reduce", 0.5f, 0f, 1f);

  private boolean oldGrimVelocity = false;
  private boolean oldGrimAttacked = false;

  public OldGrimVelocity(String name, Velocity parent) {
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
      boolean inWeb = ((IAccessorEntity) player).getIsInWeb() && webValue.getValue();
      boolean inLiquid = (player.isInWater() || player.isInLava()) && liquidValue.getValue();
      if (inWeb || inLiquid) return;
      double horizontalStrength =
          Math.sqrt(Math.pow(packet.getMotionX(), 2) + Math.pow(packet.getMotionZ(), 2));
      if (horizontalStrength <= 1000) return;
      oldGrimVelocity = true;
      oldGrimAttacked = false;
      event.setCancelled(true);
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = Velocity.mc.thePlayer;
    if (player == null) return;
    if (player.hurtTime == 0) {
      oldGrimVelocity = false;
      oldGrimAttacked = false;
    }
    if (!oldGrimVelocity || oldGrimAttacked) return;

    Entity entity = null;
    if (oldGrimRayCast.getValue()) {
      entity = VelocityUtil.getNearestEntityInRange(3.2f);
    } else {
      KillAura killAura =
          (KillAura) Miau.moduleManager.modules.get(KillAura.class);
      EntityLivingBase target = killAura != null ? killAura.getTarget() : null;
      if (target != null && player.getDistanceToEntity(target) <= 3.0) {
        entity = target;
      }
    }
    if (entity == null) return;

    boolean state = player.isSprinting();
    if (!state) {
      PacketUtil.sendPacket(
          new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SPRINTING));
    }
    int count = oldGrimLegit.getValue() ? 1 : 6;
    for (int i = 0; i < count; i++) {
      PacketUtil.sendPacket(new C0APacketAnimation());
      PacketUtil.sendPacket(new C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK));
      if (!oldGrimLegit.getValue()) {
        PacketUtil.sendPacket(new C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK));
      }
    }
    if (!state) {
      PacketUtil.sendPacket(new C03PacketPlayer(player.onGround));
      PacketUtil.sendPacket(
          new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SPRINTING));
    }
    oldGrimAttacked = true;
    player.motionX *= oldGrimAttackReduce.getValue();
    player.motionZ *= oldGrimAttackReduce.getValue();
  }

  @Override
  public void onDisable() {
    oldGrimVelocity = false;
    oldGrimAttacked = false;
  }
}