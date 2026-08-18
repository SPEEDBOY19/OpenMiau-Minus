package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.management.RotationState;
import miau.mixin.IAccessorEntityPlayer;
import miau.module.Module;
import miau.property.properties.IntProperty;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class FastBow extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final IntProperty packets = new IntProperty("Packets", 20, 3, 20);

  public FastBow() {
    super("FastBow", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
    if (!mc.thePlayer.isUsingItem()) return;

    ItemStack currentItem = mc.thePlayer.inventory.getCurrentItem();

    if (currentItem != null && currentItem.getItem() instanceof ItemBow) {
      PacketUtil.sendPacket(
          new C08PacketPlayerBlockPlacement(
              BlockPos.ORIGIN,
              255,
              mc.thePlayer.getCurrentEquippedItem(),
              0.0F,
              0.0F,
              0.0F));

      float yaw;
      float pitch;
      if (RotationState.isActived()) {
        yaw = RotationState.getSmoothedYaw();
        pitch = RotationState.getRotationPitch();
      } else {
        yaw = mc.thePlayer.rotationYaw;
        pitch = mc.thePlayer.rotationPitch;
      }

      for (int i = 0; i < this.packets.getValue(); i++) {
        PacketUtil.sendPacket(new C05PacketPlayerLook(yaw, pitch, true));
      }

      PacketUtil.sendPacket(
          new C07PacketPlayerDigging(Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
      ((IAccessorEntityPlayer) mc.thePlayer)
          .setItemInUseCount(currentItem.getMaxItemUseDuration() - 1);
    }
  }
}
