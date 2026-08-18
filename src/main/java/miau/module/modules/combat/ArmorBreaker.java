package miau.module.modules.combat;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemAxe;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class ArmorBreaker extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final IntProperty switchBackDelay = new IntProperty("SwitchBackDelay", 100, 0, 1000);
  public final BooleanProperty spoof = new BooleanProperty("SpoofItem", true);
  public final IntProperty spoofTicks =
      new IntProperty("SpoofTicks", 10, 1, 20, this.spoof::getValue);
  public final BooleanProperty onlyOnKillAura = new BooleanProperty("OnlyOnKillAura", false);

  private boolean attackEnemy = false;
  private int axeSlot = -1;
  private int originalSlot = -1;
  private boolean shouldSwitchBack = false;
  private final TimerUtil switchTimer = new TimerUtil();

  public ArmorBreaker() {
    super("ArmorBreaker", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
    if (this.onlyOnKillAura.getValue() && !isKillAuraActive()) return;
    if (this.shouldSwitchBack
        && this.axeSlot != -1
        && this.originalSlot != -1
        && this.switchTimer.hasTimeElapsed(this.switchBackDelay.getValue())) {
      if (this.spoof.getValue()) {
        Miau.slotComponent.setSlot(this.originalSlot, false);
      } else {
        mc.thePlayer.inventory.currentItem = this.originalSlot;
      }
      this.axeSlot = -1;
      this.originalSlot = -1;
      this.shouldSwitchBack = false;
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;
    if (this.onlyOnKillAura.getValue() && !isKillAuraActive()) return;
    this.attackEnemy = true;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.SEND) return;
    if (this.onlyOnKillAura.getValue() && !isKillAuraActive()) return;
    if (!(event.getPacket() instanceof C02PacketUseEntity)) return;
    C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
    if (packet.getAction() != C02PacketUseEntity.Action.ATTACK || !this.attackEnemy) return;
    this.attackEnemy = false;

    int foundAxeSlot = -1;
    for (int i = 0; i < 9; i++) {
      if (mc.thePlayer.inventory.getStackInSlot(i) != null
          && mc.thePlayer.inventory.getStackInSlot(i).getItem() instanceof ItemAxe) {
        foundAxeSlot = i;
        break;
      }
    }
    if (foundAxeSlot == -1) return;
    if (foundAxeSlot == mc.thePlayer.inventory.currentItem) return;

    this.originalSlot = mc.thePlayer.inventory.currentItem;
    this.axeSlot = foundAxeSlot;

    PacketUtil.sendPacket(new C09PacketHeldItemChange(this.axeSlot));
    PacketUtil.sendPacket(event.getPacket());
    PacketUtil.sendPacket(new C09PacketHeldItemChange(this.originalSlot));
    event.setCancelled(true);

    this.shouldSwitchBack = true;
    this.switchTimer.reset();

    if (this.spoof.getValue()) {
      Miau.slotComponent.setSlot(this.axeSlot, false);
    }
  }

  @Override
  public void onDisabled() {
    if (this.originalSlot != -1 && mc.thePlayer != null) {
      mc.thePlayer.inventory.currentItem = this.originalSlot;
    }
    this.axeSlot = -1;
    this.originalSlot = -1;
    this.shouldSwitchBack = false;
    this.attackEnemy = false;
  }

  private boolean isKillAuraActive() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    return killAura != null && killAura.isEnabled();
  }
}
