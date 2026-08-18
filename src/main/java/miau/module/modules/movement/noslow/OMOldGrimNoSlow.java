package miau.module.modules.movement.noslow;

import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class OMOldGrimNoSlow extends NoSlowMode {
  public OMOldGrimNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (getParent().isAnyActive()) {
      if (event.getType() == EventType.PRE) {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot % 7 + 2));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot));
      }

      float multiplier = getParent().getMotionMultiplier();
      mc.thePlayer.movementInput.moveForward *= multiplier;
      mc.thePlayer.movementInput.moveStrafe *= multiplier;
      if (!getParent().canSprint()) {
        mc.thePlayer.setSprinting(false);
      }
    }
  }
}
