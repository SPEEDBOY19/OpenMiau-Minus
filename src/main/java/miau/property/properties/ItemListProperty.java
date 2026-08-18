package miau.property.properties;

import java.util.function.BooleanSupplier;
import net.minecraft.item.ItemStack;

public class ItemListProperty extends TextProperty {

  public ItemListProperty(String name, String value) {
    super(name, value);
  }

  public ItemListProperty(String name, String value, BooleanSupplier booleanSupplier) {
    super(name, value, booleanSupplier);
  }

  public boolean matches(ItemStack stack) {
    if (stack == null) return false;
    String val = this.getValue();
    if (val == null || val.isEmpty()) return false;

    String[] items = val.split(",");
    String itemName = stack.getUnlocalizedName().toLowerCase();
    String displayName = stack.getDisplayName().toLowerCase();

    for (String item : items) {
      item = item.trim().toLowerCase();
      if (item.isEmpty()) continue;
      if (itemName.contains(item) || displayName.contains(item)) {
        return true;
      }
    }
    return false;
  }
}
