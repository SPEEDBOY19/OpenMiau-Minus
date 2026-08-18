package miau.mixin;

import java.awt.*;
import miau.util.animation.Direction;
import miau.util.animation.impl.DecelerateAnimation;
import miau.util.client.ChatUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiChat.class)
public abstract class MixinGuiChat extends GuiScreen {

  @Shadow protected GuiTextField inputField;
  @Shadow private String historyBuffer;
  @Shadow private int sentHistoryCursor;

  @Shadow
  public abstract void autocompletePlayerNames();

  @Shadow
  public abstract void getSentHistory(int msgPos);

  @Unique private static float openMiau$cachedAnimOut;

  @Inject(method = "initGui", at = @At("HEAD"))
  private void onInitGui(CallbackInfo ci) {
    ChatUtil.openingAnimation = new DecelerateAnimation(175, 1);
    ChatUtil.openingAnimation.reset();
  }

  @Inject(method = "onGuiClosed", at = @At("HEAD"))
  private void onGuiClosedInject(CallbackInfo ci) {
    ChatUtil.openingAnimation.setDirection(Direction.BACKWARDS);
  }
}
