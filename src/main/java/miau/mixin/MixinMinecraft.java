package miau.mixin;

import miau.Miau;
import miau.event.EventManager;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.init.Initializer;
import miau.module.modules.ghost.NoClickDelay;
import miau.module.modules.misc.BalanceFix;
import miau.ui.menu.MiauMainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(
    value = {Minecraft.class},
    priority = 9999)
public abstract class MixinMinecraft {
  @Shadow private int leftClickCounter;
  @Shadow public PlayerControllerMP playerController;
  @Shadow public WorldClient theWorld;
  @Shadow public EntityPlayerSP thePlayer;
  @Shadow public GuiScreen currentScreen;

  @Inject(
      method = {"startGame"},
      at = {@At("HEAD")})
  private void startGame(CallbackInfo callbackInfo) {
    new Initializer();
  }

  @Inject(
      method = {"startGame"},
      at = {@At("RETURN")})
  private void postStartGame(CallbackInfo callbackInfo) {
    new Miau();
  }

  @Inject(
      method = {"runTick"},
      at = {@At("HEAD")},
      cancellable = true)
  private void runTick(CallbackInfo callbackInfo) {
    if (this.theWorld != null && this.thePlayer != null) {
      TickEvent event = new TickEvent(EventType.PRE);
      EventManager.call(event);
      if (event.isCancelled()) {
        callbackInfo.cancel();
      }
    }
  }

  @Inject(
      method = {"runTick"},
      at = {@At("RETURN")})
  private void postRunTick(CallbackInfo callbackInfo) {
    if (this.theWorld != null && this.thePlayer != null) {
      EventManager.call(new TickEvent(EventType.POST));
    }
  }

  @Inject(
      method = {"loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"},
      at = {@At("HEAD")})
  private void loadWorld(WorldClient worldClient, String string, CallbackInfo callbackInfo) {
    EventManager.call(new LoadWorldEvent());
  }

  @Inject(
      method = {"updateFramebufferSize"},
      at = {@At("RETURN")})
  private void updateFramebufferSize(CallbackInfo callbackInfo) {
    EventManager.call(new ResizeEvent());
  }

  @Inject(
      method = {"clickMouse"},
      at = {@At("HEAD")},
      cancellable = true)
  private void clickMouse(CallbackInfo callbackInfo) {
    if (Miau.moduleManager != null) {
      NoClickDelay noClickDelay = (NoClickDelay) Miau.moduleManager.modules.get(NoClickDelay.class);
      if (noClickDelay != null && noClickDelay.isEnabled()) {
        this.leftClickCounter = 0;
      }
    }
    LeftClickMouseEvent event = new LeftClickMouseEvent();
    EventManager.call(event);
    if (event.isCancelled()) {
      callbackInfo.cancel();
    }
  }

  @Inject(
      method = {"rightClickMouse"},
      at = {@At("HEAD")},
      cancellable = true)
  private void rightClickMouse(CallbackInfo callbackInfo) {
    RightClickMouseEvent event = new RightClickMouseEvent();
    EventManager.call(event);
    if (event.isCancelled()) {
      callbackInfo.cancel();
    }
  }

  @Inject(
      method = {"sendClickBlockToController"},
      at = {@At("HEAD")},
      cancellable = true)
  private void sendClickBlockToController(CallbackInfo callbackInfo) {
    HitBlockEvent event = new HitBlockEvent();
    EventManager.call(event);
    if (event.isCancelled()) {
      callbackInfo.cancel();
      this.playerController.resetBlockRemoving();
    }
  }

  @Redirect(
      method = {"runTick"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/settings/KeyBinding;setKeyBindState(IZ)V"))
  private void setKeyBindState(int integer, boolean boolean2) {
    KeyBinding.setKeyBindState(integer, boolean2);
    if (boolean2 && this.currentScreen == null) {
      EventManager.call(new KeyEvent(integer));
    }
  }

  @Redirect(
      method = {"runTick"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"))
  private void changeCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
    SwapItemEvent event = new SwapItemEvent(-1, slot);
    EventManager.call(event);
    if (!event.isCancelled()) {
      inventoryPlayer.changeCurrentItem(slot);
    }
  }

  @Inject(method = "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", at = @At("HEAD"), cancellable = true)
  private void onDisplayGuiScreen(GuiScreen guiScreenIn, CallbackInfo ci) {
    if (Miau.moduleManager == null) return;

    GuiScreen gui = guiScreenIn;

    // Main menu replacement
    if (gui instanceof GuiMainMenu || (gui == null && this.theWorld == null)) {
      gui = new MiauMainMenu();
    }

    if (gui != null) {
      GuiOpenEvent event = new GuiOpenEvent(gui);
      EventManager.call(event);

      if (event.isCancelled()) {
        ci.cancel();
        return;
      }

      if (event.getGui() != gui) {
        modifiedGui = event.getGui();
      } else if (gui != guiScreenIn) {
        modifiedGui = gui;
      }
    }
  }

  private GuiScreen modifiedGui = null;

  @ModifyVariable(method = "displayGuiScreen", at = @At("HEAD"), argsOnly = true)
  private GuiScreen modifyGuiScreen(GuiScreen guiScreenIn) {
    if (modifiedGui != null) {
      GuiScreen ret = modifiedGui;
      modifiedGui = null;
      return ret;
    }
    return guiScreenIn;
  }

  @Inject(
      method = "runGameLoop",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/util/Timer;updateTimer()V",
              shift = At.Shift.AFTER))
  private void afterUpdateTimer(CallbackInfo ci) {
    if (Miau.moduleManager != null) {
      BalanceFix balanceFix = (BalanceFix) Miau.moduleManager.modules.get(BalanceFix.class);
      if (balanceFix != null && balanceFix.isEnabled()) {
        ((IAccessorMinecraft) this).getTimer().elapsedTicks =
            Math.min(((IAccessorMinecraft) this).getTimer().elapsedTicks, 10);
      }
    }
  }
}
