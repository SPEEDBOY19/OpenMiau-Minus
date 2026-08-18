package miau.component;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.util.player.IInventoryPlayerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public final class SlotComponent {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private static boolean render = true;
  public static boolean finished = true;

  public void setSlot(final int slot) {
    setSlot(slot, true);
  }

  public void setSlot(final int slot, final boolean renderEffect) {
    if (slot < 0 || slot >= 9) return;

    IInventoryPlayerAccessor inv = (IInventoryPlayerAccessor) mc.thePlayer.inventory;
    inv.miau$setAlternativeCurrentItem(slot);
    inv.miau$setAlternativeSlot(true);
    render = renderEffect;
    finished = false;

    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
  }

  public void setSlotDelayed(final int slot, boolean force) {
    setSlotDelayed(slot, force, true);
  }

  public void setSlotDelayed(final int slot, boolean force, boolean renderEffect) {
    if (Math.random() * Math.random() > 0.25 || force) {
      setSlot(
          ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem(), renderEffect);
    } else {
      setSlot(slot, renderEffect);
    }
  }

  public int getItemIndex() {
    if (mc.thePlayer == null) return 0;
    IInventoryPlayerAccessor inv = (IInventoryPlayerAccessor) mc.thePlayer.inventory;
    return inv.miau$getAlternativeSlot()
        ? inv.miau$getAlternativeCurrentItem()
        : mc.thePlayer.inventory.currentItem;
  }

  public ItemStack getItemStack() {
    if (mc.thePlayer == null || mc.thePlayer.inventoryContainer == null) return null;
    int index = getItemIndex();
    return mc.thePlayer.inventoryContainer.getSlot(index + 36).getStack();
  }

  public Item getItem() {
    ItemStack stack = getItemStack();
    return stack == null ? null : stack.getItem();
  }

  public boolean isHoldingBlock() {
    return getItem() instanceof ItemBlock;
  }

  @EventTarget(Priority.HIGHEST)
  public void onPreUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null) return;

    IInventoryPlayerAccessor inv = (IInventoryPlayerAccessor) mc.thePlayer.inventory;
    inv.miau$setAlternativeSlot(false);
    inv.miau$setAlternativeCurrentItem(mc.thePlayer.inventory.currentItem);
  }
}
