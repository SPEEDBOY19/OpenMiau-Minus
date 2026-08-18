package miau.mixin;

import java.util.List;
import miau.Miau;
import miau.data.Box;
import miau.event.EventManager;
import miau.event.impl.PickEvent;
import miau.event.impl.PostRaytraceEvent;
import miau.event.impl.RaytraceEvent;
import miau.event.impl.Render3DEvent;
import miau.module.modules.combat.*;
import miau.module.modules.misc.*;
import miau.module.modules.movement.*;
import miau.module.modules.network.*;
import miau.module.modules.player.*;
import miau.module.modules.render.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@SideOnly(Side.CLIENT)
@Mixin(
    value = {EntityRenderer.class},
    priority = 9999)
public abstract class MixinEntityRenderer {
  @Unique private Box<Integer> slot = null;
  @Unique private Box<ItemStack> using = null;
  @Unique private Box<Integer> useCount = null;
  @Shadow private Minecraft mc;
  @Shadow private float thirdPersonDistance;
  @Shadow private float thirdPersonDistanceTemp;
  @Shadow private ShaderGroup theShaderGroup;
  @Unique private boolean miau$freeLookRestoreRotation;
  @Unique private float miau$freeLookYaw;
  @Unique private float miau$freeLookPitch;
  @Unique private float miau$freeLookPrevYaw;
  @Unique private float miau$freeLookPrevPitch;

  @Inject(
      method = {"updateCameraAndRender"},
      at = {@At("HEAD")})
  private void updateCameraAndRender(float float1, long long2, CallbackInfo callbackInfo) {
    if (this.mc.thePlayer != null) {
      KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
      if (killAura != null && killAura.isEnabled() && killAura.isBlocking()) {
        this.using = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUse());
        ((IAccessorEntityPlayer) this.mc.thePlayer)
            .setItemInUse(this.mc.thePlayer.inventory.getCurrentItem());
        this.useCount = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUseCount());
        ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(69000);
      }
    }
  }

  @Inject(
      method = {"updateCameraAndRender"},
      at = {@At("RETURN")})
  private void postUpdateCameraAndRender(float float1, long long2, CallbackInfo callbackInfo) {
    if (this.slot != null) {
      this.mc.thePlayer.inventory.currentItem = this.slot.value;
      this.slot = null;
    }
    if (this.using != null) {
      ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.using.value);
      this.using = null;
    }
    if (this.useCount != null) {
      ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(this.useCount.value);
      this.useCount = null;
    }
  }

  @Inject(
      method = {"updateRenderer"},
      at = {@At("HEAD")})
  private void updateRenderer(CallbackInfo callbackInfo) {
    AutoBlockIn autoBlockIn = (AutoBlockIn) Miau.moduleManager.modules.get(AutoBlockIn.class);
    if (autoBlockIn != null && autoBlockIn.isEnabled() && autoBlockIn.itemSpoof.getValue()) {
      int slot = autoBlockIn.getSlot();
      if (slot >= 0) {
        this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
        this.mc.thePlayer.inventory.currentItem = slot;
      }
    }
  }

  @Inject(
      method = {"updateRenderer"},
      at = {@At("RETURN")})
  private void postUpdateRenderer(CallbackInfo callbackInfo) {
    if (this.slot != null) {
      this.mc.thePlayer.inventory.currentItem = this.slot.value;
      this.slot = null;
    }
  }

  @Inject(
      method = {"renderWorldPass"},
      at = {
        @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z",
            shift = At.Shift.BEFORE)
      })
  private void renderWorldPass(int integer, float float2, long long3, CallbackInfo callbackInfo) {
    EventManager.call(new Render3DEvent(float2));
  }

  @ModifyConstant(
      method = {"hurtCameraEffect"},
      constant = {@Constant(floatValue = 14.0F, ordinal = 0)})
  private float hurtCameraEffect(float float1) {
    if (Miau.moduleManager == null) {
      return float1;
    } else {
      NoHurtCam noHurtCam = (NoHurtCam) Miau.moduleManager.modules.get(NoHurtCam.class);
      return noHurtCam.isEnabled()
          ? float1 * (float) noHurtCam.multiplier.getValue().intValue() / 100.0F
          : float1;
    }
  }

  @ModifyConstant(
      method = {"getMouseOver"},
      constant = {@Constant(doubleValue = 3.0, ordinal = 1)})
  private double getMouseOver(double range) {
    PickEvent event = new PickEvent(range);
    EventManager.call(event);
    return event.getRange();
  }

  @ModifyVariable(
      method = {"getMouseOver"},
      at = @At("STORE"),
      name = {"d0"})
  private double storeMouseOver(double range) {
    RaytraceEvent event = new RaytraceEvent(range);
    EventManager.call(event);
    return event.getRange();
  }

  @Inject(
      method = {"getMouseOver"},
      at = {@At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 0)},
      locals = LocalCapture.CAPTURE_FAILSOFT)
  private void a(
      float float1,
      CallbackInfo callbackInfo,
      Entity entity,
      double double4,
      double double5,
      Vec3 vec36,
      boolean boolean7,
      int integer8,
      Vec3 vec39,
      Vec3 vec310,
      Vec3 vec311,
      float float12,
      List<Entity> list,
      double double14,
      int integer15) {
    if (Miau.moduleManager != null) {
      GhostHand ghostHand = (GhostHand) Miau.moduleManager.modules.get(GhostHand.class);
      if (ghostHand != null && ghostHand.isEnabled()) {
        list.removeIf(ghostHand::shouldSkip);
      }
    }
  }

  @Inject(method = "getMouseOver", at = @At("RETURN"))
  private void onGetMouseOverReturn(float partialTicks, CallbackInfo ci) {
    if (Miau.moduleManager != null) {
      Piercing piercing = (Piercing) Miau.moduleManager.modules.get(Piercing.class);
      if (piercing != null && piercing.isEnabled()) {
        piercing.modifyMouseOver(partialTicks);
      }
    }
    PostRaytraceEvent event = new PostRaytraceEvent(partialTicks);
    EventManager.call(event);
  }

  @Inject(
      method = {"orientCamera"},
      at = {@At("HEAD")})
  private void orientFreeLook(float partialTicks, CallbackInfo callbackInfo) {
    if (Miau.moduleManager == null || this.mc.thePlayer == null) {
      return;
    }
    FreeLook freeLook = (FreeLook) Miau.moduleManager.modules.get(FreeLook.class);
    if (freeLook != null && freeLook.isFreeLooking()) {
      EntityPlayerSP player = this.mc.thePlayer;
      this.miau$freeLookYaw = player.rotationYaw;
      this.miau$freeLookPitch = player.rotationPitch;
      this.miau$freeLookPrevYaw = player.prevRotationYaw;
      this.miau$freeLookPrevPitch = player.prevRotationPitch;
      this.miau$freeLookRestoreRotation = true;
      player.prevRotationYaw = player.rotationYaw = freeLook.getCameraYaw();
      player.prevRotationPitch =
          player.rotationPitch = MathHelper.clamp_float(freeLook.getCameraPitch(), -90.0F, 90.0F);
      if (this.mc.gameSettings.thirdPersonView == 0) {
        this.mc.gameSettings.thirdPersonView = 1;
      }
      this.thirdPersonDistanceTemp = this.thirdPersonDistance;
    }
  }

  @Inject(
      method = {"orientCamera"},
      at = {@At("RETURN")})
  private void restoreFreeLook(float partialTicks, CallbackInfo callbackInfo) {
    if (this.miau$freeLookRestoreRotation && this.mc.thePlayer != null) {
      EntityPlayerSP player = this.mc.thePlayer;
      player.rotationYaw = this.miau$freeLookYaw;
      player.rotationPitch = this.miau$freeLookPitch;
      player.prevRotationYaw = this.miau$freeLookPrevYaw;
      player.prevRotationPitch = this.miau$freeLookPrevPitch;
      this.miau$freeLookRestoreRotation = false;
    }
  }

  @Redirect(
      method = {"orientCamera"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/util/Vec3;distanceTo(Lnet/minecraft/util/Vec3;)D"))
  private double v(Vec3 vec31, Vec3 vec32) {
    if (Miau.moduleManager == null) {
      return vec31.distanceTo(vec32);
    } else {
      ViewClip viewClip = (ViewClip) Miau.moduleManager.modules.get(ViewClip.class);
      return viewClip != null && viewClip.isEnabled()
          ? (double) this.thirdPersonDistance
          : vec31.distanceTo(vec32);
    }
  }

  @Redirect(
      method = {"setupFog"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/block/Block;getMaterial()Lnet/minecraft/block/material/Material;"))
  private Material x(Block block) {
    if (Miau.moduleManager == null) {
      return block.getMaterial();
    } else {
      ViewClip viewClip = (ViewClip) Miau.moduleManager.modules.get(ViewClip.class);
      return viewClip != null && viewClip.isEnabled() ? Material.air : block.getMaterial();
    }
  }

  @Redirect(
      method = {"updateFogColor"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
  private boolean y(EntityLivingBase entityLivingBase, Potion potion) {
    if (potion == Potion.blindness && Miau.moduleManager != null) {
      AntiDebuff antiDebuff = (AntiDebuff) Miau.moduleManager.modules.get(AntiDebuff.class);
      if (antiDebuff.isEnabled() && antiDebuff.blindness.getValue()) {
        return false;
      }
    }
    return ((IAccessorEntityLivingBase) entityLivingBase)
        .getActivePotionsMap()
        .containsKey(potion.id);
  }

  @Redirect(
      method = {"setupFog"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
  private boolean q(EntityLivingBase entityLivingBase, Potion potion) {
    if (potion == Potion.blindness && Miau.moduleManager != null) {
      AntiDebuff antiDebuff = (AntiDebuff) Miau.moduleManager.modules.get(AntiDebuff.class);
      if (antiDebuff.isEnabled() && antiDebuff.blindness.getValue()) {
        return false;
      }
    }
    return ((IAccessorEntityLivingBase) entityLivingBase)
        .getActivePotionsMap()
        .containsKey(potion.id);
  }

  @Redirect(
      method = {"setupCameraTransform"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
  private boolean c(EntityPlayerSP entityPlayerSP, Potion potion) {
    if (potion == Potion.confusion && Miau.moduleManager != null) {
      AntiDebuff antiDebuff = (AntiDebuff) Miau.moduleManager.modules.get(AntiDebuff.class);
      if (antiDebuff.isEnabled() && antiDebuff.nausea.getValue()) {
        return false;
      }
    }
    return ((IAccessorEntityLivingBase) entityPlayerSP)
        .getActivePotionsMap()
        .containsKey(potion.id);
  }
}
