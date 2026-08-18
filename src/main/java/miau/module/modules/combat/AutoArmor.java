package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

public class AutoArmor extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final IntProperty delay = new IntProperty("Delay", 50, 0, 1000);
  public final IntProperty minItemAge = new IntProperty("MinItemAge", 0, 0, 2000);
  public final BooleanProperty smartSwap = new BooleanProperty("SmartSwap", true);

  private final TimerUtil timer = new TimerUtil();
  private int armorType = 0;

  public AutoArmor() {
    super("AutoArmor", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
    if (!(mc.currentScreen instanceof GuiInventory)) return;
    if (mc.thePlayer.openContainer.windowId != 0) return;
    if (!this.timer.hasTimeElapsed(this.delay.getValue())) return;

    for (int attempt = 0; attempt < 4; attempt++) {
      int type = this.armorType;
      this.armorType = (this.armorType + 1) % 4;
      if (this.tryEquip(type)) {
        this.timer.reset();
        break;
      }
    }
  }

  private boolean tryEquip(int type) {
    int armorSlot = 5 + type;
    ItemStack current = mc.thePlayer.inventory.armorInventory[type];
    double bestScore = current == null ? -1.0D : this.getScore(current);

    int bestSlot = -1;
    for (int i = 9; i < 45; i++) {
      ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
      if (stack == null || !(stack.getItem() instanceof ItemArmor)) continue;
      if (((ItemArmor) stack.getItem()).armorType != 3 - type) continue;
      if (this.minItemAge.getValue() > 0 && stack.animationsToGo > 0) continue;
      double score = this.getScore(stack);
      if (score > bestScore) {
        bestScore = score;
        bestSlot = i;
      }
    }
    if (bestSlot == -1) return false;

    mc.playerController.windowClick(0, bestSlot, 0, 1, mc.thePlayer);
    return true;
  }

  private double getScore(ItemStack stack) {
    ItemArmor armor = (ItemArmor) stack.getItem();
    return armor.damageReduceAmount * 100.0D
        + EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack) * 5.0D
        + armor.getMaxDamage() / 100.0D;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.delay.getValue() + "ms"};
  }
}
