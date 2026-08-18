package miau.mixin;

import miau.util.player.IInventoryPlayerAccessor;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(InventoryPlayer.class)
public class MixinInventoryPlayer implements IInventoryPlayerAccessor {
  @Shadow public ItemStack[] mainInventory;

  @Shadow public int currentItem;

  @Shadow public EntityPlayer player;

  @Unique private boolean miau$alternativeSlot = false;

  @Unique private int miau$alternativeCurrentItem = 0;

  @Inject(method = "getCurrentItem", at = @At("HEAD"), cancellable = true)
  private void miau$getCurrentItem(CallbackInfoReturnable<ItemStack> cir) {
    if (this.player == net.minecraft.client.Minecraft.getMinecraft().thePlayer) {
      int slot =
          this.miau$getAlternativeSlot() ? this.miau$getAlternativeCurrentItem() : this.currentItem;
      cir.setReturnValue(slot < 9 && slot >= 0 ? this.mainInventory[slot] : null);
    }
  }

  @Inject(method = "getStrVsBlock", at = @At("HEAD"), cancellable = true)
  private void miau$getStrVsBlock(Block blockIn, CallbackInfoReturnable<Float> cir) {
    if (this.player == net.minecraft.client.Minecraft.getMinecraft().thePlayer) {
      int slot =
          this.miau$getAlternativeSlot() ? this.miau$getAlternativeCurrentItem() : this.currentItem;
      float f = 1.0F;
      if (slot < 9 && slot >= 0 && this.mainInventory[slot] != null) {
        f *= this.mainInventory[slot].getStrVsBlock(blockIn);
      }
      cir.setReturnValue(f);
    }
  }

  @Override
  public boolean miau$getAlternativeSlot() {
    return this.miau$alternativeSlot;
  }

  @Override
  public void miau$setAlternativeSlot(boolean value) {
    this.miau$alternativeSlot = value;
  }

  @Override
  public int miau$getAlternativeCurrentItem() {
    return this.miau$alternativeCurrentItem;
  }

  @Override
  public void miau$setAlternativeCurrentItem(int value) {
    this.miau$alternativeCurrentItem = value;
  }
}
