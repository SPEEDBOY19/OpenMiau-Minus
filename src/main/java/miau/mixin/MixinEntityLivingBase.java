package miau.mixin;

import miau.Miau;
import miau.event.EventManager;
import miau.event.impl.StrafeEvent;
import miau.management.RotationState;
import miau.module.modules.movement.Jesus;
import miau.module.modules.render.Animations;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(
    value = {EntityLivingBase.class},
    priority = 9999)
public abstract class MixinEntityLivingBase extends MixinEntity {
  @ModifyVariable(
      method = {"jump"},
      at = @At("STORE"),
      ordinal = 0)
  private float jump(float float1) {
    if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
      float yawDegrees = float1 * (float) (180.0 / Math.PI);
      miau.event.impl.JumpEvent event =
          new miau.event.impl.JumpEvent(
              RotationState.isActived() ? RotationState.getSmoothedYaw() : yawDegrees);
      EventManager.call(event);
      if (event.getJumpoff() != 0.0f) {
        this.motionY = event.getJumpoff();
      }
      return event.getYaw() * (float) (Math.PI / 180.0);
    }
    return float1;
  }

  @Redirect(
      method = {"moveEntityWithHeading"},
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;moveFlying(FFF)V"))
  private void moveEntityWithHeading(
      EntityLivingBase entityLivingBase, float float2, float float3, float float4) {
    if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
      float originalYaw = this.rotationYaw;
      float movementYaw = RotationState.isActived() ? RotationState.getSmoothedYaw() : originalYaw;
      StrafeEvent event = new StrafeEvent(float2, float3, float4, movementYaw);
      EventManager.call(event);
      if (event.isCancelled()) {
        this.rotationYaw = movementYaw;
        return;
      }
      float2 = event.getStrafe();
      float3 = event.getForward();
      float4 = event.getFriction();
      this.rotationYaw = event.getYaw();
      entityLivingBase.moveFlying(float2, float3, float4);
      this.rotationYaw = originalYaw;
    } else {
      entityLivingBase.moveFlying(float2, float3, float4);
    }
  }

  @ModifyVariable(
      method = {"moveEntityWithHeading"},
      name = {"f3"},
      at = @At("STORE"))
  private float moveEntityWithHeading(float float1) {
    if ((EntityLivingBase) ((Object) this) instanceof EntityPlayerSP
        && float1
            == (float)
                EnchantmentHelper.getDepthStriderModifier((EntityLivingBase) ((Object) this))) {
      if (Miau.moduleManager == null) {
        return float1;
      }
      Jesus jesus = (Jesus) Miau.moduleManager.modules.get(Jesus.class);
      if (jesus != null && jesus.isEnabled() && (!jesus.groundOnly.getValue() || this.onGround)) {
        return Math.max(float1, jesus.speed.getValue());
      }
    }
    return float1;
  }

  @Shadow
  public abstract boolean isPotionActive(net.minecraft.potion.Potion potionIn);

  @Shadow
  public abstract net.minecraft.potion.PotionEffect getActivePotionEffect(
      net.minecraft.potion.Potion potionIn);

  @org.spongepowered.asm.mixin.injection.Inject(
      method = "getArmSwingAnimationEnd",
      at = @At("RETURN"),
      cancellable = true)
  private void getArmSwingAnimationEnd(
      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
    int original = cir.getReturnValue();
    cir.setReturnValue(Animations.getSwingAnimationEnd((EntityLivingBase) (Object) this, original));
  }
}
