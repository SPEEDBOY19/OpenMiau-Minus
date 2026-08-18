package miau.mixin;

import miau.Miau;
import miau.module.modules.movement.Sprint;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(
    value = {AbstractClientPlayer.class},
    priority = 9999)
public abstract class MixinAbstractClientPlayer extends MixinEntityPlayer {
  @Redirect(
      method = {"getFovModifier"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/entity/ai/attributes/IAttributeInstance;getAttributeValue()D"))
  private double getFovModifier(IAttributeInstance iAttributeInstance) {
    double attributeValue = iAttributeInstance.getAttributeValue();
    if ((((Entity) (Object) this)) instanceof EntityPlayerSP && Miau.moduleManager != null) {
      Sprint sprint = (Sprint) Miau.moduleManager.modules.get(Sprint.class);
      return sprint.isEnabled() && sprint.shouldApplyFovFix(iAttributeInstance)
          ? attributeValue * 1.300000011920929
          : attributeValue;
    } else {
      return attributeValue;
    }
  }

  @org.spongepowered.asm.mixin.injection.Inject(
      method = "getLocationCape",
      at = @At("RETURN"),
      cancellable = true)
  public void onGetLocationCape(
      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<
              net.minecraft.util.ResourceLocation>
          cir) {
    if (Miau.moduleManager != null) {
      miau.module.modules.render.Capes capes =
          (miau.module.modules.render.Capes)
              Miau.moduleManager.modules.get(miau.module.modules.render.Capes.class);
      if (capes != null && capes.isEnabled()) {
        boolean isSelf = ((Object) this) instanceof net.minecraft.client.entity.EntityPlayerSP;
        if (capes.allPlayer.getValue() || isSelf) {
          cir.setReturnValue(capes.getCape());
        }
      }
    }
  }
}
