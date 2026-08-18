package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.module.Module;
import miau.property.properties.IntProperty;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;

public class ComboOneHit extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final IntProperty attackPackets = new IntProperty("AttackPackets", 50, 1, 1000);

  public ComboOneHit() {
    super("ComboOneHit", false);
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;
    Entity target = event.getTarget();
    if (target == null) return;
    for (int i = 0; i < this.attackPackets.getValue(); i++) {
      PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
      mc.thePlayer.swingItem();
    }
  }
}
