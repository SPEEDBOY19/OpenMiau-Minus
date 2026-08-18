package miau.module.modules.player;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.TextProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

public class AutoSwap extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty useBlockWhitelist =
      new BooleanProperty("use-block-whitelist", false);
  public final TextProperty blockWhitelist = new TextProperty("whitelisted-blocks", "");

  private ItemStack trackedStack;
  private int lastPlaceSlot = -1;
  private int lastSwapSlot = -1;
  private long lastSwapTime;

  public AutoSwap() {
    super("AutoSwap", false);
  }

  @Override
  public void onEnabled() {
    resetState();
  }

  @Override
  public void onDisabled() {
    resetState();
  }

  @EventTarget(Priority.LOW)
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() != EventType.SEND) return;
    if (mc.theWorld == null || mc.thePlayer == null) return;
    if (!(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;

    C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
    if (packet.getPlacedBlockDirection() == 255) return;

    ItemStack stack = packet.getStack();
    if (stack == null || !(stack.getItem() instanceof ItemBlock)) return;

    trackedStack = stack.copy();
    trackedStack.stackSize = 1;
    lastPlaceSlot = mc.thePlayer.inventory.currentItem;
  }

  @EventTarget(Priority.LOW)
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() != EventType.PRE) return;
    if (mc.theWorld == null || mc.thePlayer == null) {
      resetState();
      return;
    }
    if (!mc.inGameHasFocus
        || mc.currentScreen != null
        || !mc.gameSettings.keyBindUseItem.isKeyDown()) {
      return;
    }
    if (trackedStack == null
        || lastPlaceSlot == -1
        || mc.thePlayer.inventory.currentItem != lastPlaceSlot) {
      return;
    }
    ItemStack held = mc.thePlayer.getHeldItem();
    if (held != null && held.stackSize > 0) {
      return;
    }
    if (!isWhitelistedBlock(trackedStack)) {
      return;
    }
    long now = System.currentTimeMillis();
    for (int slot = 8; slot >= 0; --slot) {
      if (slot == lastSwapSlot && now - lastSwapTime < 300L) {
        continue;
      }
      ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(slot);
      if (!matchesTrackedStack(candidate)) {
        continue;
      }
      swapToSlot(slot);
      lastSwapSlot = slot;
      lastSwapTime = now;
      break;
    }
  }

  private boolean isWhitelistedBlock(ItemStack stack) {
    if (!useBlockWhitelist.getValue()) return true;
    if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
    String whitelist = blockWhitelist.getValue().trim();
    if (whitelist.isEmpty()) return true;

    Block block = ((ItemBlock) stack.getItem()).getBlock();
    Object registryName = Block.blockRegistry.getNameForObject(block);
    if (block == null || registryName == null) return false;

    String registryId = registryName.toString();
    int meta = stack.getMetadata();
    String storageId = meta != 0 ? registryId + ":" + meta : registryId;

    for (String entry : whitelist.split(",")) {
      entry = entry.trim();
      if (entry.isEmpty()) continue;
      if (entry.equals(registryId) || entry.equals(storageId)) return true;
    }
    return false;
  }

  private boolean matchesTrackedStack(ItemStack stack) {
    if (trackedStack == null || stack == null || stack.getItem() != trackedStack.getItem()) {
      return false;
    }
    if (stack.getHasSubtypes() && stack.getMetadata() != trackedStack.getMetadata()) {
      return false;
    }
    return ItemStack.areItemStackTagsEqual(stack, trackedStack);
  }

  private void swapToSlot(int slot) {
    if (slot == -1 || slot == mc.thePlayer.inventory.currentItem) return;
    mc.thePlayer.inventory.currentItem = slot;
    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
  }

  private void resetState() {
    trackedStack = null;
    lastPlaceSlot = -1;
    lastSwapSlot = -1;
    lastSwapTime = 0L;
  }
}
