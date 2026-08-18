package miau.module.modules.movement.noslow;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;

public class OMHypixelNoSlow extends NoSlowMode {
  private boolean sentSprintStart = false;

  public OMHypixelNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    sentSprintStart = false;
  }

  @Override
  public void onDisable() {
    sentSprintStart = false;
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) {
      return;
    }

    if (this.getParent().isAnyActive()) {
      int currentSlot = mc.thePlayer.inventory.currentItem;
      PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
      PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot));

      if (!this.getParent().hypixelJump.getValue() && mc.thePlayer.isSprinting()) {
        PacketUtil.sendPacket(
            new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
        sentSprintStart = false;
      }

      float multiplier = this.getParent().getMotionMultiplier();
      mc.thePlayer.movementInput.moveForward *= multiplier;
      mc.thePlayer.movementInput.moveStrafe *= multiplier;
    } else if (mc.thePlayer.isSprinting()
        && !this.getParent().hypixelJump.getValue()
        && !sentSprintStart) {
      PacketUtil.sendPacket(
          new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
      sentSprintStart = true;
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      sentSprintStart = false;
    }
  }
}