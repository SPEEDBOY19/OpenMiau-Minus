package miau.mixin;

import miau.Miau;
import miau.enums.ChatColors;
import miau.module.modules.misc.AntiObfuscate;
import miau.module.modules.misc.NickHider;
import miau.module.modules.render.HUD;
import miau.util.font.FontRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = FontRenderer.class, priority = 9999)
public abstract class MixinFontRenderer {

  @Shadow
  public abstract int drawString(String text, float x, float y, int color, boolean dropShadow);

  @Shadow public int FONT_HEIGHT;

  private boolean isCustomRendering = false;

  private void updateHeight() {
    if (Miau.moduleManager == null) return;
    this.FONT_HEIGHT = FontRepository.isMinecraftSelected() ? 9 : 9;
  }

  @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), ordinal = 0, argsOnly = true)
  private String modifyGetStringWidth(String string) {
    if (string == null || Miau.moduleManager == null) return string;

    AntiObfuscate antiObfuscate =
        (AntiObfuscate) Miau.moduleManager.modules.get(AntiObfuscate.class);
    if (antiObfuscate != null && antiObfuscate.isEnabled()) {
      string = antiObfuscate.stripObfuscated(string);
    }

    NickHider nickHider = (NickHider) Miau.moduleManager.modules.get(NickHider.class);
    if (nickHider != null && nickHider.isEnabled()) {
      string = nickHider.replaceNick(string);
    }

    return string;
  }

  @Inject(method = "drawString(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), cancellable = true)
  public void onDrawString(
      String text,
      float x,
      float y,
      int color,
      boolean dropShadow,
      CallbackInfoReturnable<Integer> cir) {
    updateHeight();
    if (isCustomRendering || text == null || text.isEmpty() || Miau.moduleManager == null) {
      return;
    }

    GL11.glColor4f(1f, 1f, 1f, 1f);
    GlStateManager.color(1f, 1f, 1f, 1f);

    AntiObfuscate antiObfuscate =
        (AntiObfuscate) Miau.moduleManager.modules.get(AntiObfuscate.class);
    if (antiObfuscate != null && antiObfuscate.isEnabled()) {
      text = antiObfuscate.stripObfuscated(text);
    }

    NickHider nickHider = (NickHider) Miau.moduleManager.modules.get(NickHider.class);
    if (nickHider != null && nickHider.isEnabled()) {
      text = nickHider.replaceNick(text);
    }

    Minecraft mc = Minecraft.getMinecraft();
    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);

    String targetName = "";
    if (mc.thePlayer != null) {
      targetName = mc.thePlayer.getName();
      if (nickHider != null && nickHider.isEnabled()) {
        targetName =
            EnumChatFormatting.getTextWithoutFormattingCodes(
                ChatColors.formatColor(nickHider.protectName.getValue()));
      }
    }

    if (!targetName.isEmpty() && text.contains(targetName)) {
      isCustomRendering = true;
      int result = drawTrueRGBName(text, x, y, color, dropShadow, targetName, hud);
      isCustomRendering = false;
      cir.setReturnValue(result);
    }
  }

  @Inject(method = "getStringWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
  public void onGetStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
    updateHeight();
  }

  @Inject(method = "getCharWidth(C)I", at = @At("HEAD"), cancellable = true)
  public void onGetCharWidth(char character, CallbackInfoReturnable<Integer> cir) {
    updateHeight();
  }

  private int drawTrueRGBName(
      String text,
      float x,
      float y,
      int originalColor,
      boolean dropShadow,
      String targetName,
      HUD hud) {
    float currentX = x;
    String remaining = text;
    int index = remaining.indexOf(targetName);
    long time = System.currentTimeMillis();
    String currentFormat = "";

    while (index != -1) {
      String before = remaining.substring(0, index);
      if (!before.isEmpty()) {
        String textToDraw = currentFormat + before;
        currentX = this.drawString(textToDraw, currentX, y, originalColor, dropShadow);
        currentFormat = FontRenderer.getFormatFromString(textToDraw);
      }

      for (int i = 0; i < targetName.length(); i++) {
        String charStr = String.valueOf(targetName.charAt(i));

        int charColor = hud != null ? hud.getColor(time, i * 15L).getRGB() : -1;

        currentX = this.drawString(charStr, currentX, y, charColor, dropShadow);
      }

      remaining = remaining.substring(index + targetName.length());
      index = remaining.indexOf(targetName);
    }

    if (!remaining.isEmpty()) {
      this.drawString(currentFormat + remaining, currentX, y, originalColor, dropShadow);
    }

    return (int) currentX;
  }
}
