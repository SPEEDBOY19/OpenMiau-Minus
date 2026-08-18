package miau.mixin;

import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {GuiNewChat.class},
    priority = 9999)
public abstract class MixinGuiNewChat {
  private static final int OPENMIAU_DUPLICATE_CHAT_ID = 873420;
  private String openMiauLastMessage = null;
  private int openMiauDuplicateCount = 1;
  private boolean openMiauReplacingDuplicate = false;

  @Shadow @Final private net.minecraft.client.Minecraft mc;
  @Shadow @Final private java.util.List<net.minecraft.client.gui.ChatLine> drawnChatLines;
  @Shadow private int scrollPos;
  @Shadow private boolean isScrolled;

  @Shadow
  public abstract int getLineCount();

  @Shadow
  public abstract boolean getChatOpen();

  @Shadow
  public abstract float getChatScale();

  @Shadow
  public abstract int getChatWidth();

  @Shadow
  public abstract void deleteChatLine(int id);

  @Shadow
  public abstract void printChatMessageWithOptionalDeletion(
      IChatComponent chatComponent, int chatLineId);

  @Inject(
      method = {"printChatMessageWithOptionalDeletion"},
      at = @At("HEAD"),
      cancellable = true)
  private void openMiau$compactDuplicateChat(
      IChatComponent chatComponent, int chatLineId, CallbackInfo ci) {
    if (openMiauReplacingDuplicate || chatComponent == null || chatLineId != 0) {
      return;
    }

    String message = chatComponent.getUnformattedText();
    if (message == null || message.isEmpty()) {
      openMiauLastMessage = message;
      openMiauDuplicateCount = 1;
      return;
    }

    if (message.equals(openMiauLastMessage)) {
      openMiauDuplicateCount++;
      IChatComponent compacted = chatComponent.createCopy();
      compacted.appendText(" §7[x" + openMiauDuplicateCount + "]");

      ci.cancel();
      openMiauReplacingDuplicate = true;
      this.deleteChatLine(OPENMIAU_DUPLICATE_CHAT_ID);
      this.printChatMessageWithOptionalDeletion(compacted, OPENMIAU_DUPLICATE_CHAT_ID);
      openMiauReplacingDuplicate = false;
    } else {
      if (openMiauDuplicateCount > 1) {
        this.deleteChatLine(OPENMIAU_DUPLICATE_CHAT_ID);
      }
      openMiauLastMessage = message;
      openMiauDuplicateCount = 1;
    }
  }

  private miau.module.modules.render.HUD openMiauCachedHud;

  @Redirect(
      method = "drawChat",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"))
  private int openMiau$renderCustomChatPrefix(
      net.minecraft.client.gui.FontRenderer fontRenderer,
      String text,
      float x,
      float y,
      int color) {
    if (text != null && text.contains(miau.enums.ChatColors.PREFIX_CLEAN)) {
      if (openMiauCachedHud == null) {
        openMiauCachedHud =
            (miau.module.modules.render.HUD)
                miau.Miau.moduleManager.getModule(miau.module.modules.render.HUD.class);
      }
      if (openMiauCachedHud != null && openMiauCachedHud.isEnabled()) {
        int prefixIndex = text.indexOf(miau.enums.ChatColors.PREFIX_CLEAN);
        String before = text.substring(0, prefixIndex);
        float currentX = x;

        if (!before.isEmpty()) {
          currentX = fontRenderer.drawStringWithShadow(before, currentX, y, color);
        }

        long time = System.currentTimeMillis();
        int alpha = (color >> 24) & 0xFF;

        for (int i = 0; i < miau.enums.ChatColors.PREFIX_CLEAN.length(); i++) {
          char c = miau.enums.ChatColors.PREFIX_CLEAN.charAt(i);
          int charColor = openMiauCachedHud.getColor(time, i * 15).getRGB();
          int finalColor = (alpha << 24) | (charColor & 0x00FFFFFF);
          currentX = fontRenderer.drawStringWithShadow(String.valueOf(c), currentX, y, finalColor);
        }

        String after = text.substring(prefixIndex + miau.enums.ChatColors.PREFIX_CLEAN.length());
        if (!after.isEmpty()) {
          currentX = fontRenderer.drawStringWithShadow(after, currentX, y, color);
        }
        return (int) currentX;
      }
    }
    return fontRenderer.drawStringWithShadow(text, x, y, color);
  }

  /**
   * Redirect the first GlStateManager.translate call in drawChat to apply the opening animation Y
   * offset (Tenacity style).
   */
  @Redirect(
      method = "drawChat",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V",
              ordinal = 0))
  private void redirectChatTranslate(float x, float y, float z) {
    float animY = 17.0F;
    try {
      if (miau.util.client.ChatUtil.openingAnimation != null) {
        animY =
            17.0F - (16.0F * miau.util.client.ChatUtil.openingAnimation.getOutput().floatValue());
      }
    } catch (Exception e) {
      animY = 17.0F;
    }
    net.minecraft.client.renderer.GlStateManager.translate(x, animY, z);
  }

  @Inject(method = "drawChat", at = @At("HEAD"))
  private void onDrawChatPre(int updateCounter, CallbackInfo ci) {
    try {
      if (mc.theWorld == null || mc.thePlayer == null) return;

      if (openMiauCachedHud == null) {
        openMiauCachedHud =
            (miau.module.modules.render.HUD)
                miau.Miau.moduleManager.getModule(miau.module.modules.render.HUD.class);
      }
    } catch (Exception e) {

    }
  }
}
