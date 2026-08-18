package miau.mixin;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(RenderItem.class)
public abstract class MixinRenderItem {

  @Inject(
      method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V",
      at = @At("HEAD"))
  private void glintFix$resetItemBodyState(
      ItemStack stack, IBakedModel model, CallbackInfo ci) {
    GlStateManager.disableCull();
    GlStateManager.enableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(770, 771);
    GlStateManager.depthFunc(515);
    GlStateManager.depthMask(true);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }

  @Inject(method = "renderEffect", at = @At("HEAD"))
  private void glintFix$prepareGlintState(CallbackInfo ci) {
    GlStateManager.disableLighting();
    GlStateManager.depthFunc(514);
  }

  @Inject(method = "renderEffect", at = @At("RETURN"))
  private void glintFix$restoreGlintState(CallbackInfo ci) {
    GlStateManager.depthMask(true);
    GlStateManager.blendFunc(770, 771);
    GlStateManager.depthFunc(515);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.enableLighting();
  }
}