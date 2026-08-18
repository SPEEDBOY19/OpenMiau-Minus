package miau.component;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

public final class BadPacketsComponent {

  private static boolean slot, attack, swing, block, inventory;
  private static boolean savedSlot, savedAttack, savedSwing, savedBlock, savedInventory;

  public static boolean bad() {
    return bad(true, true, true, true, true);
  }

  public static boolean bad(
      final boolean slotCheck,
      final boolean attackCheck,
      final boolean swingCheck,
      final boolean blockCheck,
      final boolean inventoryCheck) {
    return (savedSlot && slotCheck)
        || (savedAttack && attackCheck)
        || (savedSwing && swingCheck)
        || (savedBlock && blockCheck)
        || (savedInventory && inventoryCheck);
  }

  @EventTarget(Priority.HIGHEST)
  public final void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      final Packet<?> packet = event.getPacket();

      if (packet instanceof C09PacketHeldItemChange) {
        slot = true;
      } else if (packet instanceof C0APacketAnimation) {
        swing = true;
      } else if (packet instanceof C02PacketUseEntity) {
        attack = true;
      } else if (packet instanceof C08PacketPlayerBlockPlacement
          || packet instanceof C07PacketPlayerDigging) {
        block = true;
      } else if (packet instanceof C0EPacketClickWindow
          || (packet instanceof C16PacketClientStatus
              && ((C16PacketClientStatus) packet).getStatus()
                  == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)
          || packet instanceof C0DPacketCloseWindow) {
        inventory = true;
      }
    }
  }

  @EventTarget(Priority.HIGHEST)
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      savedSlot = slot;
      savedAttack = attack;
      savedSwing = swing;
      savedBlock = block;
      savedInventory = inventory;
    } else if (event.getType() == EventType.POST) {
      reset();
    }
  }

  public static void reset() {
    slot = false;
    swing = false;
    attack = false;
    block = false;
    inventory = false;
    savedSlot = false;
    savedAttack = false;
    savedSwing = false;
    savedBlock = false;
    savedInventory = false;
  }
}
