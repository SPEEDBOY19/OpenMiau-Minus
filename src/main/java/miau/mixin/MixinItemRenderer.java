package miau.mixin;

import miau.interfaces.IMixinItemRenderer;
import miau.module.modules.render.Animations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer implements IMixinItemRenderer {
  @Shadow private float prevEquippedProgress;

  @Shadow private float equippedProgress;

  @Shadow @Final private Minecraft mc;

  @Shadow private ItemStack itemToRender;

  @Shadow
  protected abstract void rotateArroundXAndY(float angle, float angleY);

  @Shadow
  protected abstract void setLightMapFromPlayer(AbstractClientPlayer clientPlayer);

  @Shadow
  protected abstract void rotateWithPlayerRotations(
      EntityPlayerSP entityplayerspIn, float partialTicks);

  @Shadow
  protected abstract void renderItemMap(
      AbstractClientPlayer clientPlayer, float pitch, float equipmentProgress, float swingProgress);

  @Shadow
  protected abstract void transformFirstPersonItem(float equipProgress, float swingProgress);

  @Shadow
  protected abstract void performDrinking(AbstractClientPlayer clientPlayer, float partialTicks);

  @Shadow
  protected abstract void doBowTransformations(
      float partialTicks, AbstractClientPlayer clientPlayer);

  @Shadow
  protected abstract void doItemUsedTransformations(float swingProgress);

  @Shadow
  public abstract void renderItem(
      EntityLivingBase entityIn, ItemStack heldStack, ItemCameraTransforms.TransformType transform);

  @Shadow
  protected abstract void renderPlayerArm(
      AbstractClientPlayer clientPlayer, float equipProgress, float swingProgress);

  private ItemStack originalItemToRender;
  public boolean cancelUpdate = false;
  public boolean cancelReset = false;
  private boolean renderItemInUse;

  @Override
  public void setCancelUpdate(boolean cancel) {
    this.cancelUpdate = cancel;
  }

  @Override
  public void setCancelReset(boolean reset) {
    this.cancelReset = reset;
  }

  @Override
  public boolean isRenderItemInUse() {
    return renderItemInUse;
  }

  @Override
  public void setRenderItemInUse(boolean renderItemInUse) {
    this.renderItemInUse = renderItemInUse;
  }

  @Redirect(
      method = "updateEquippedItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"))
  private ItemStack redirectGetCurrentItem(InventoryPlayer inventoryPlayer) {
    return inventoryPlayer.mainInventory[inventoryPlayer.currentItem];
  }

  @Inject(method = "updateEquippedItem", at = @At("HEAD"), cancellable = true)
  private void onUpdateEquippedItem(CallbackInfo ci) {
    if (cancelUpdate) {
      cancelUpdate = false;
      equippedProgress = 1.0F;
      prevEquippedProgress = 1.0f;
      ci.cancel();
    }
  }

  @Inject(method = "resetEquippedProgress", at = @At("HEAD"), cancellable = true)
  public void injectResetEquippedProgress(CallbackInfo ci) {
    if (cancelReset) {
      cancelReset = false;
      equippedProgress = 1.0F;
      prevEquippedProgress = 1.0f;
      ci.cancel();
    }
  }

  @Inject(method = "resetEquippedProgress2", at = @At("HEAD"), cancellable = true)
  public void injectResetEquippedProgress2(CallbackInfo ci) {
    if (cancelReset) {
      cancelReset = false;
      equippedProgress = 1.0F;
      prevEquippedProgress = 1.0f;
      ci.cancel();
    }
  }

  /**
   * @author CCBlueX, ported to Miau
   * @reason Add configurable blocking animations + spoofItem support.
   */
  @Overwrite
  public void renderItemInFirstPerson(float partialTicks) {

    originalItemToRender = itemToRender;
    itemToRender = getSpoofedItem(originalItemToRender);

    float equipProgress =
        1.0F
            - (this.prevEquippedProgress
                + (this.equippedProgress - this.prevEquippedProgress) * partialTicks);
    EntityPlayerSP player = this.mc.thePlayer;
    float swingProgress = player.getSwingProgress(partialTicks);
    float pitch =
        player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
    float yaw =
        player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;

    this.rotateArroundXAndY(pitch, yaw);
    this.setLightMapFromPlayer(player);
    this.rotateWithPlayerRotations(player, partialTicks);
    GlStateManager.enableRescaleNormal();
    GlStateManager.pushMatrix();

    if (this.itemToRender != null) {
      if (this.itemToRender.getItem() instanceof ItemMap) {
        this.renderItemMap(player, pitch, equipProgress, swingProgress);
      } else if (player.getItemInUseCount() > 0) {
        EnumAction action = this.itemToRender.getItemUseAction();
        if (action == EnumAction.NONE) {
          this.transformFirstPersonItem(equipProgress, 0.0F);
        } else if (action == EnumAction.EAT || action == EnumAction.DRINK) {
          this.performDrinking(player, partialTicks);
          this.transformFirstPersonItem(equipProgress, swingProgress);
        } else if (action == EnumAction.BLOCK) {
          if (!Animations.apply(swingProgress, equipProgress, player)) {
            this.transformFirstPersonItem(equipProgress, swingProgress);
            GlStateManager.translate(-0.5F, 0.2F, 0.0F);
            GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
          }
        } else if (action == EnumAction.BOW) {
          this.transformFirstPersonItem(equipProgress, swingProgress);
          this.doBowTransformations(partialTicks, player);
        }
      } else {
        this.doItemUsedTransformations(swingProgress);
        this.transformFirstPersonItem(equipProgress, swingProgress);
      }

      RenderHelper.enableStandardItemLighting();
      this.renderItem(player, this.itemToRender, ItemCameraTransforms.TransformType.FIRST_PERSON);
    } else if (!player.isInvisible()) {
      this.renderPlayerArm(player, equipProgress, swingProgress);
    }

    GlStateManager.popMatrix();
    GlStateManager.disableRescaleNormal();
    RenderHelper.disableStandardItemLighting();

    itemToRender = originalItemToRender;
  }

  private static ItemStack getSpoofedItem(ItemStack original) {
    miau.module.modules.player.AutoTool autoTool =
        (miau.module.modules.player.AutoTool)
            miau.Miau.moduleManager.getModule(miau.module.modules.player.AutoTool.class);
    if (autoTool != null
        && autoTool.isEnabled()
        && autoTool.spoofItem.getValue()
        && Minecraft.getMinecraft().thePlayer != null) {
      return Minecraft.getMinecraft()
          .thePlayer
          .inventory
          .getStackInSlot(
              autoTool.previousSlot == -1
                  ? Minecraft.getMinecraft().thePlayer.inventory.currentItem
                  : autoTool.previousSlot);
    }
    return original;
  }
}
