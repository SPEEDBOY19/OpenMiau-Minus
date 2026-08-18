package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

public class MoreClick extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final IntProperty extraPacket = new IntProperty("ExtraClickPacket", 1, 1, 20);
  public final BooleanProperty keepSprint = new BooleanProperty("KeepSprint", false);
  public final IntProperty sendPacketDelay = new IntProperty("SendPacketDelay", 50, 0, 1000);
  public final BooleanProperty debugger = new BooleanProperty("Debugger", false);

  private final TimerUtil packetDelay = new TimerUtil();
  private boolean silentAttack = false;

  public MoreClick() {
    super("MoreClick", false);
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || this.silentAttack) return;
    if (!this.packetDelay.hasTimeElapsed(this.sendPacketDelay.getValue())) return;
    this.packetDelay.reset();
    Entity target = event.getTarget();
    if (target == null) return;
    this.silentAttack = true;
    try {
      for (int i = 0; i < this.extraPacket.getValue(); i++) {
        if (mc.thePlayer.getDistanceToEntity(target) < 3.0F) {
          mc.thePlayer.swingItem();
          mc.playerController.attackEntity(mc.thePlayer, target);
          if (this.keepSprint.getValue()) {
            mc.thePlayer.setSprinting(true);
          }
        }
      }
    } finally {
      this.silentAttack = false;
    }
    if (this.debugger.getValue()) {
      ChatUtil.display("&7Attacked x" + this.extraPacket.getValue());
    }
  }

  @Override
  public void onEnabled() {
    this.packetDelay.reset();
  }

  @Override
  public void onDisabled() {
    this.packetDelay.reset();
  }
}
