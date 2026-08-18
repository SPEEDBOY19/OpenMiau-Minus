package miau.mixin;

import miau.Miau;
import miau.event.EventManager;
import miau.event.impl.HitSlowDownEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(
    value = {EntityPlayer.class},
    priority = 9999)
public abstract class MixinEntityPlayer extends MixinEntityLivingBase {
  @Unique
  private HitSlowDownEvent hitSlowDown;

  @ModifyConstant(
      method = {"attackTargetEntityWithCurrentItem"},
      constant = {@Constant(doubleValue = 0.6)})
  private double attackTargetEntityWithCurrentItem(double speed) {
    if (this.hitSlowDown == null) {
      this.hitSlowDown = new HitSlowDownEvent(0.6D, false);
      EventManager.call(this.hitSlowDown);
    }
    return this.hitSlowDown.isCancelled() ? 1.0D : this.hitSlowDown.getSlowDown();
  }

  @Redirect(
      method = {"attackTargetEntityWithCurrentItem"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"))
  private void setSprinnt(EntityPlayer entityPlayer, boolean boolean2) {
    if (this.hitSlowDown != null) {
      if (!this.hitSlowDown.isCancelled()) {
        entityPlayer.setSprinting(this.hitSlowDown.isSprint());
      }
      this.hitSlowDown = null;
    } else {
      entityPlayer.setSprinting(boolean2);
    }
  }
}
