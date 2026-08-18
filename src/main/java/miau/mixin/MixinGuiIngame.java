package miau.mixin;

import miau.Miau;
import miau.module.modules.player.AutoBlockIn;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(
    value = {GuiIngame.class},
    priority = 9999)
public abstract class MixinGuiIngame {
  @Redirect(
      method = {"updateTick"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"))
  private ItemStack updateTick(InventoryPlayer inventoryPlayer) {
    AutoBlockIn autoBlockIn = (AutoBlockIn) Miau.moduleManager.modules.get(AutoBlockIn.class);
    if (autoBlockIn != null && autoBlockIn.itemSpoof.getValue() && autoBlockIn.isEnabled()) {
      int slot = autoBlockIn.getSlot();
      if (slot >= 0) {
        return inventoryPlayer.getStackInSlot(slot);
      }
    }
    return inventoryPlayer.getCurrentItem();
  }
}
