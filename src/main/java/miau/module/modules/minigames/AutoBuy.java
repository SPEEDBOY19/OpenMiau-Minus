package miau.module.modules.minigames;

import java.util.*;
import miau.event.EventTarget;
import miau.event.impl.LivingUpdateEvent;
import miau.module.Module;
import miau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public class AutoBuy extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final IntProperty purchaseDelay = new IntProperty("Purchase Delay", 100, 100, 400);

  public final IntProperty woolKeybind = new IntProperty("Wool Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty woolQuickslot =
      new ModeProperty(
          "Wool Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty woolTurbo = new BooleanProperty("Wool Turbo", false);

  public final IntProperty stoneSwordKeybind =
      new IntProperty("Stone Sword Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty stoneSwordQuickslot =
      new ModeProperty(
          "Stone Sword Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty stoneSwordTurbo = new BooleanProperty("Stone Sword Turbo", false);

  public final IntProperty ironSwordKeybind =
      new IntProperty("Iron Sword Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty ironSwordQuickslot =
      new ModeProperty(
          "Iron Sword Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty ironSwordTurbo = new BooleanProperty("Iron Sword Turbo", false);

  public final IntProperty goldenAppleKeybind =
      new IntProperty("Golden Apple Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty goldenAppleQuickslot =
      new ModeProperty(
          "Golden Apple Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty goldenAppleTurbo = new BooleanProperty("Golden Apple Turbo", false);

  public final IntProperty fireballKeybind =
      new IntProperty("Fireball Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty fireballQuickslot =
      new ModeProperty(
          "Fireball Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty fireballTurbo = new BooleanProperty("Fireball Turbo", false);

  public final IntProperty tntKeybind = new IntProperty("TNT Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty tntQuickslot =
      new ModeProperty(
          "TNT Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty tntTurbo = new BooleanProperty("TNT Turbo", false);

  public final IntProperty enderPearlKeybind =
      new IntProperty("Ender Pearl Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty enderPearlQuickslot =
      new ModeProperty(
          "Ender Pearl Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty enderPearlTurbo = new BooleanProperty("Ender Pearl Turbo", false);

  public final IntProperty pickaxeKeybind =
      new IntProperty("Pickaxe Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty pickaxeQuickslot =
      new ModeProperty(
          "Pickaxe Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty pickaxeTurbo = new BooleanProperty("Pickaxe Turbo", false);

  public final IntProperty axeKeybind = new IntProperty("Axe Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty axeQuickslot =
      new ModeProperty(
          "Axe Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty axeTurbo = new BooleanProperty("Axe Turbo", false);

  public final IntProperty shearsKeybind =
      new IntProperty("Shears Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty shearsQuickslot =
      new ModeProperty(
          "Shears Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty shearsTurbo = new BooleanProperty("Shears Turbo", false);

  public final IntProperty chainmailArmorKeybind =
      new IntProperty("Chainmail Armor Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty chainmailArmorQuickslot =
      new ModeProperty(
          "Chainmail Armor Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty chainmailArmorTurbo =
      new BooleanProperty("Chainmail Armor Turbo", false);

  public final IntProperty ironArmorKeybind =
      new IntProperty("Iron Armor Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty ironArmorQuickslot =
      new ModeProperty(
          "Iron Armor Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty ironArmorTurbo = new BooleanProperty("Iron Armor Turbo", false);

  public final IntProperty diamondSwordKeybind =
      new IntProperty("Diamond Sword Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty diamondSwordQuickslot =
      new ModeProperty(
          "Diamond Sword Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty diamondSwordTurbo =
      new BooleanProperty("Diamond Sword Turbo", false);

  public final IntProperty stickKeybind =
      new IntProperty("Knockback Stick Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty stickQuickslot =
      new ModeProperty(
          "Knockback Stick Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty stickTurbo = new BooleanProperty("Knockback Stick Turbo", false);

  public final IntProperty arrowsKeybind =
      new IntProperty("Arrows Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty arrowsQuickslot =
      new ModeProperty(
          "Arrows Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty arrowsTurbo = new BooleanProperty("Arrows Turbo", false);

  public final IntProperty diamondArmorKeybind =
      new IntProperty("Diamond Armor Keybind", 0, 0, Integer.MAX_VALUE);
  public final ModeProperty diamondArmorQuickslot =
      new ModeProperty(
          "Diamond Armor Quickslot",
          0,
          new String[] {"Disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
  public final BooleanProperty diamondArmorTurbo =
      new BooleanProperty("Diamond Armor Turbo", false);

  public final IntProperty sharpnessKeybind =
      new IntProperty("Sharpness Keybind", 0, 0, Integer.MAX_VALUE);
  public final IntProperty protectionKeybind =
      new IntProperty("Protection Keybind", 0, 0, Integer.MAX_VALUE);
  public final IntProperty miningFatigueKeybind =
      new IntProperty("Mining Fatigue Keybind", 0, 0, Integer.MAX_VALUE);
  public final IntProperty hasteKeybind = new IntProperty("Haste Keybind", 0, 0, Integer.MAX_VALUE);
  public final IntProperty featherFallingKeybind =
      new IntProperty("Feather Falling Keybind", 0, 0, Integer.MAX_VALUE);

  private final Map<String, String> itemDisplayNames = new LinkedHashMap<>();
  private final List<String> items = new ArrayList<>();
  private final Map<String, Integer> locations = new HashMap<>();
  private final Map<String, Long> purchases = new HashMap<>();
  private final Map<String, Boolean> keyStates = new HashMap<>();
  private final List<int[]> clickList = new ArrayList<>();

  private final Map<String, IntProperty> keybindMap = new HashMap<>();
  private final Map<String, ModeProperty> quickslotMap = new HashMap<>();
  private final Map<String, BooleanProperty> turboMap = new HashMap<>();

  private static final Set<String> PICKAXE_TYPES =
      new HashSet<>(
          Arrays.asList("wooden_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe"));
  private static final Set<String> AXE_TYPES =
      new HashSet<>(Arrays.asList("wooden_axe", "stone_axe", "iron_axe", "diamond_axe"));

  public AutoBuy() {
    super("AutoBuy", false);
    setupItems();
  }

  private void setupItems() {
    registerItem("wool", "Wool", woolKeybind, woolQuickslot, woolTurbo);
    registerItem(
        "stone_sword", "Stone Sword", stoneSwordKeybind, stoneSwordQuickslot, stoneSwordTurbo);
    registerItem("iron_sword", "Iron Sword", ironSwordKeybind, ironSwordQuickslot, ironSwordTurbo);
    registerItem(
        "golden_apple", "Golden Apple", goldenAppleKeybind, goldenAppleQuickslot, goldenAppleTurbo);
    registerItem("fire_charge", "Fireball", fireballKeybind, fireballQuickslot, fireballTurbo);
    registerItem("tnt", "TNT", tntKeybind, tntQuickslot, tntTurbo);
    registerItem(
        "ender_pearl", "Ender Pearl", enderPearlKeybind, enderPearlQuickslot, enderPearlTurbo);
    registerItem("pickaxe", "Pickaxe", pickaxeKeybind, pickaxeQuickslot, pickaxeTurbo);
    registerItem("axe", "Axe", axeKeybind, axeQuickslot, axeTurbo);
    registerItem("shears", "Shears", shearsKeybind, shearsQuickslot, shearsTurbo);
    registerItem(
        "chainmail_boots",
        "Chainmail Armor",
        chainmailArmorKeybind,
        chainmailArmorQuickslot,
        chainmailArmorTurbo);
    registerItem("iron_boots", "Iron Armor", ironArmorKeybind, ironArmorQuickslot, ironArmorTurbo);
    registerItem(
        "diamond_sword",
        "Diamond Sword",
        diamondSwordKeybind,
        diamondSwordQuickslot,
        diamondSwordTurbo);
    registerItem("stick", "Knockback Stick", stickKeybind, stickQuickslot, stickTurbo);
    registerItem("arrow", "Arrows", arrowsKeybind, arrowsQuickslot, arrowsTurbo);
    registerItem(
        "diamond_boots",
        "Diamond Armor",
        diamondArmorKeybind,
        diamondArmorQuickslot,
        diamondArmorTurbo);

    registerUpgradeItem("upg iron_sword", "Sharpness", sharpnessKeybind);
    registerUpgradeItem("upg iron_chestplate", "Protection", protectionKeybind);
    registerUpgradeItem("upg iron_pickaxe", "Mining Fatigue", miningFatigueKeybind);
    registerUpgradeItem("upg golden_pickaxe", "Haste", hasteKeybind);
    registerUpgradeItem("upg diamond_boots", "Feather Falling", featherFallingKeybind);
  }

  private void registerItem(
      String item,
      String displayName,
      IntProperty keybind,
      ModeProperty quickslot,
      BooleanProperty turbo) {
    itemDisplayNames.put(item, displayName);
    items.add(item);
    keybindMap.put(item, keybind);
    quickslotMap.put(item, quickslot);
    turboMap.put(item, turbo);
  }

  private void registerUpgradeItem(String item, String displayName, IntProperty keybind) {
    itemDisplayNames.put(item, displayName);
    items.add(item);
    keybindMap.put(item, keybind);
  }

  @Override
  public void onEnabled() {
    super.onEnabled();
    locations.clear();
    clickList.clear();
    purchases.clear();
    keyStates.clear();
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled()) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (!(mc.currentScreen instanceof GuiChest)) {
      locations.clear();
      clickList.clear();
      return;
    }

    GuiChest chest = (GuiChest) mc.currentScreen;
    if (!(chest.inventorySlots instanceof ContainerChest)) {
      locations.clear();
      clickList.clear();
      return;
    }

    ContainerChest container = (ContainerChest) chest.inventorySlots;
    String chestName = container.getLowerChestInventory().getName();
    int chestSize = container.getLowerChestInventory().getSizeInventory();

    boolean isQuickBuy = chestName.equals("Quick Buy");
    boolean isUpgrades = chestName.equals("Upgrades & Traps");
    if (!isQuickBuy && !isUpgrades) return;

    long now = System.currentTimeMillis();

    for (String item : items) {
      String searchItem = item;

      if (isQuickBuy && item.startsWith("upg ")) continue;
      if (isUpgrades) {
        if (!item.startsWith("upg ")) continue;
        searchItem = item.substring(4);
      }

      Set<String> itemTypes = null;
      if (isQuickBuy) {
        if (searchItem.equals("pickaxe")) itemTypes = PICKAXE_TYPES;
        else if (searchItem.equals("axe")) itemTypes = AXE_TYPES;
      }

      int start = isQuickBuy ? 18 : 9;
      int end = isQuickBuy ? chestSize - 9 : 27;
      for (int i = start; i < end; i++) {
        ItemStack stack = container.getSlot(i).getStack();
        if (stack == null || stack.getItem() == null) continue;

        String unlocalizedName = stack.getItem().getUnlocalizedName();

        String cleanName = unlocalizedName.replace("tile.", "").replace("item.", "");

        if (itemTypes != null && !itemTypes.contains(cleanName)) continue;
        if (itemTypes == null && !cleanName.equals(searchItem)) continue;

        locations.put(item, i);
        break;
      }
    }

    for (String item : items) {

      if (isQuickBuy && item.startsWith("upg ")) continue;
      if (isUpgrades && !item.startsWith("upg ")) continue;

      IntProperty keybindProp = keybindMap.get(item);
      if (keybindProp == null) continue;

      int keyCode = keybindProp.getValue();
      if (keyCode == 0) continue;

      boolean keyDown = isKeyDown(keyCode);
      boolean lastKeyState = keyStates.getOrDefault(item, false);

      if (!keyDown) {
        keyStates.put(item, false);
        continue;
      }

      int hotbarSlot = -1;
      boolean turbo = false;

      if (!item.startsWith("upg ")) {
        ModeProperty slotProp = quickslotMap.get(item);
        if (slotProp != null && slotProp.getValue() > 0) {
          hotbarSlot = slotProp.getValue() - 1;
        }
        BooleanProperty turboProp = turboMap.get(item);
        if (turboProp != null) {
          turbo = turboProp.getValue();
        }
      }

      long cooldown = item.startsWith("upg ") ? 300 : 90;
      long lastTime = purchases.getOrDefault(item, 0L);

      if (!turbo && lastKeyState) continue;
      if (now - lastTime < cooldown) continue;

      purchases.put(item, now);
      keyStates.put(item, true);

      Integer slot = locations.get(item);
      if (slot != null) {
        clickList.add(new int[] {slot, hotbarSlot});
      }
    }

    int delayTicks = Math.max(1, purchaseDelay.getValue() / 50);
    if (mc.thePlayer.ticksExisted % delayTicks == 0) {
      if (!clickList.isEmpty()) {
        int[] click = clickList.remove(0);
        int slot = click[0];
        int hotbarSlot = click[1];

        if (hotbarSlot >= 0) {

          mc.playerController.windowClick(container.windowId, slot, hotbarSlot, 2, mc.thePlayer);
        } else {

          mc.playerController.windowClick(container.windowId, slot, 0, 0, mc.thePlayer);
        }
      }
    }
  }

  private boolean isKeyDown(int keyCode) {
    if (keyCode < 0) {
      return org.lwjgl.input.Mouse.isButtonDown(keyCode + 100);
    }
    return Keyboard.isKeyDown(keyCode);
  }
}
