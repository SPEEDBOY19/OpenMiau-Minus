package miau.module.modules.player;

import java.util.HashSet;
import java.util.Set;
import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryEnderChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class AutoChest extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public final IntProperty delay = new IntProperty("Delay", 100, 0, 500);
  public final IntProperty waitDelay = new IntProperty("Wait Delay", 100, 0, 500);
  public final IntProperty dumpKey = new IntProperty("Dump Key", 0, 0, 255);
  public final IntProperty takeKey = new IntProperty("Take Key", 0, 0, 255);
  public final BooleanProperty renderClicked = new BooleanProperty("Render Clicked", true);
  public final BooleanProperty enderChests = new BooleanProperty("Enderchest", true);
  public final BooleanProperty normalChests = new BooleanProperty("Normal Chest", false);
  public final BooleanProperty iron = new BooleanProperty("Iron", true);
  public final BooleanProperty gold = new BooleanProperty("Gold", true);
  public final BooleanProperty diamonds = new BooleanProperty("Diamonds", true);
  public final BooleanProperty emeralds = new BooleanProperty("Emeralds", true);

  private static final String LOCAL_CHEST = I18n.format("container.chest");
  private static final String LOCAL_ENDER_CHEST = I18n.format("container.enderchest");
  public static final Set<Integer> CLICKED_SLOTS = new HashSet<>();

  private static long nextClickTime = 0L;
  private static boolean shouldDeposit = false;
  private static boolean shouldTake = false;
  private static boolean shouldDump = false;
  private static int lastCheckedSlot = 0;

  private boolean chestWasOpen = false;
  private int openDelayTicks = 0;

  public AutoChest() {
    super("AutoChest", false);
  }

  @Override
  public void onEnabled() {
    chestWasOpen = false;
    openDelayTicks = 0;
    resetState();
    CLICKED_SLOTS.clear();
  }

  @Override
  public void onDisabled() {
    CLICKED_SLOTS.clear();
    resetState();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (!(mc.currentScreen instanceof GuiChest)) {
      if (chestWasOpen) {
        chestWasOpen = false;
        resetState();
      }
      return;
    }

    GuiChest chest = (GuiChest) mc.currentScreen;
    if (!(chest.inventorySlots instanceof ContainerChest)) {
      if (chestWasOpen) {
        chestWasOpen = false;
        resetState();
      }
      return;
    }

    ContainerChest container = (ContainerChest) chest.inventorySlots;
    IInventory inventory = container.getLowerChestInventory();

    if (!chestWasOpen) {
      chestWasOpen = true;
      if (allowedChest(inventory)) {
        shouldDeposit = true;
        lastCheckedSlot = 0;
        openDelayTicks = Math.max(1, waitDelay.getValue() / 50);
      }
      return;
    }

    if (openDelayTicks > 0) {
      openDelayTicks--;
      return;
    }

    if ((Mouse.isButtonDown(0) || Mouse.isButtonDown(1) || Mouse.isButtonDown(2))
        && (shouldDump || shouldTake || shouldDeposit)) {
      ChatUtil.display("&cYou can't click while AutoChest is currently active, disabling.");
      resetState();
      return;
    }

    if (dumpKey.getValue() != 0 && Keyboard.isKeyDown(dumpKey.getValue())) {
      shouldDump = true;
      lastCheckedSlot = 0;
    }

    if (takeKey.getValue() != 0 && Keyboard.isKeyDown(takeKey.getValue())) {
      shouldTake = true;
      lastCheckedSlot = 0;
    }

    if (renderClicked.getValue()) {
      CLICKED_SLOTS.clear();
    }

    if (shouldDeposit || shouldTake || shouldDump) {
      long now = System.currentTimeMillis();
      if (now >= nextClickTime) {
        nextClickTime = now + delay.getValue();
        if (!activate(container)) {
          resetState();
        }
      }
    }
  }

  private boolean activate(ContainerChest container) {
    int size = container.inventorySlots.size();
    for (int i = lastCheckedSlot; i < size; i++) {
      Slot slot = container.getSlot(i);
      if (slot == null || !slot.getHasStack()) continue;

      if (!shouldDeposit && !shouldDump) {
        if (slot.inventory == mc.thePlayer.inventory) continue;
      } else {
        if (slot.inventory != mc.thePlayer.inventory) continue;
      }

      ItemStack stack = slot.getStack();
      if (stack == null) continue;
      if (shouldDump || allowedItem(stack.getItem())) {
        click(container, slot);
        lastCheckedSlot = i + 1;
        return true;
      }
    }
    return false;
  }

  private void click(ContainerChest container, Slot slot) {
    mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, 1, mc.thePlayer);

    if (renderClicked.getValue()) {
      CLICKED_SLOTS.add(slot.slotNumber);
    }
  }

  private boolean allowedItem(Item item) {
    return (iron.getValue() && item == Items.iron_ingot)
        || (gold.getValue() && item == Items.gold_ingot)
        || (diamonds.getValue() && item == Items.diamond)
        || (emeralds.getValue() && item == Items.emerald);
  }

  private boolean allowedChest(IInventory inventory) {
    String containerName = inventory.getDisplayName().getUnformattedText();

    if (enderChests.getValue() && inventory instanceof InventoryEnderChest) {
      if (containerName.isEmpty()) return true;
      if (LOCAL_ENDER_CHEST.equals(containerName)) return true;
    }

    if (normalChests.getValue() && !(inventory instanceof InventoryEnderChest)) {
      if (containerName.isEmpty()) return true;
      if (LOCAL_CHEST.equals(containerName)) return true;
    }

    return false;
  }

  private static void resetState() {
    shouldDeposit = false;
    shouldTake = false;
    shouldDump = false;
  }
}
