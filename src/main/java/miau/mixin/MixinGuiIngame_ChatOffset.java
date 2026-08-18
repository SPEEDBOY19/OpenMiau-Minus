package miau.mixin;

import miau.util.client.ChatUtil;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame_ChatOffset {

  @Unique private static float openMiau$cachedAnim = 0.0f;

  @Unique
  private void openMiau$pushAnimMatrix() {
    if (openMiau$cachedAnim > 0.001f) {
      GlStateManager.pushMatrix();
      GlStateManager.translate(0, -15 * openMiau$cachedAnim, 0);
    }
  }

  @Unique
  private void openMiau$popAnimMatrix() {
    if (openMiau$cachedAnim > 0.001f) {
      GlStateManager.popMatrix();
    }
  }

  @Inject(method = "renderPlayerStats", at = @At("HEAD"))
  private void onRenderPlayerStatsPre(ScaledResolution scaledRes, CallbackInfo ci) {
    openMiau$cachedAnim = ChatUtil.openingAnimation.getOutput().floatValue();
    openMiau$pushAnimMatrix();
  }

  @Inject(method = "renderPlayerStats", at = @At("RETURN"))
  private void onRenderPlayerStatsPost(ScaledResolution scaledRes, CallbackInfo ci) {
    openMiau$popAnimMatrix();
  }

  @Inject(method = "renderExpBar", at = @At("HEAD"))
  private void onRenderExpBarPre(ScaledResolution scaledRes, int x, CallbackInfo ci) {
    openMiau$pushAnimMatrix();
  }

  @Inject(method = "renderExpBar", at = @At("RETURN"))
  private void onRenderExpBarPost(ScaledResolution scaledRes, int x, CallbackInfo ci) {
    openMiau$popAnimMatrix();
  }

  @Inject(method = "renderHorseJumpBar", at = @At("HEAD"))
  private void onRenderHorseJumpBarPre(ScaledResolution scaledRes, int x, CallbackInfo ci) {
    openMiau$pushAnimMatrix();
  }

  @Inject(method = "renderHorseJumpBar", at = @At("RETURN"))
  private void onRenderHorseJumpBarPost(ScaledResolution scaledRes, int x, CallbackInfo ci) {
    openMiau$popAnimMatrix();
  }

  @Inject(method = "renderTooltip", at = @At("HEAD"))
  private void onRenderTooltipPre(ScaledResolution scaledRes, float partialTicks, CallbackInfo ci) {
    openMiau$pushAnimMatrix();
  }

  @Inject(method = "renderTooltip", at = @At("RETURN"))
  private void onRenderTooltipPost(
      ScaledResolution scaledRes, float partialTicks, CallbackInfo ci) {
    openMiau$popAnimMatrix();
  }

  @Inject(method = "renderSelectedItem", at = @At("HEAD"))
  private void onRenderSelectedItemPre(ScaledResolution scaledRes, CallbackInfo ci) {
    openMiau$pushAnimMatrix();
  }

  @Inject(method = "renderSelectedItem", at = @At("RETURN"))
  private void onRenderSelectedItemPost(ScaledResolution scaledRes, CallbackInfo ci) {
    openMiau$popAnimMatrix();
  }
}
