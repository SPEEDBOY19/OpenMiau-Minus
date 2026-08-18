package miau.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import miau.ClientInfo;
import miau.management.MiauAPI;
import miau.ui.GuiUpdateClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu {
  private static boolean hasCheckedVersion = false;

  @Inject(method = "initGui", at = @At("RETURN"))
  private void onInitGui(CallbackInfo ci) {
    if (!hasCheckedVersion) {
      hasCheckedVersion = true;
      GuiMainMenu menu = (GuiMainMenu) (Object) this;
      new Thread(
              () -> {
                try {
                  String responseStr = MiauAPI.getClientVersion();
                  JsonObject response = new JsonParser().parse(responseStr).getAsJsonObject();

                  if (response.has("status")
                      && response.get("status").getAsString().equals("success")) {
                    String latestVersion = response.get("version").getAsString();
                    String updateUrl = response.get("updateUrl").getAsString();

                    if (MiauAPI.isOutdated(ClientInfo.VERSION, latestVersion)) {
                      Minecraft.getMinecraft()
                          .addScheduledTask(
                              () -> {
                                Minecraft.getMinecraft()
                                    .displayGuiScreen(
                                        new GuiUpdateClient(
                                            menu, ClientInfo.VERSION, latestVersion, updateUrl));
                              });
                    }
                  }
                } catch (Exception e) {

                }
              })
          .start();
    }
  }

  @Redirect(
      method = "drawScreen",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiMainMenu;drawString(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"))
  private void replaceCopyrightWithCredits(
      GuiMainMenu instance, FontRenderer fontRenderer, String text, int x, int y, int color) {
    String credits = "Credit: [Tirumdz-d, ihatenocheat-bot, SPEEDBOY19 based on Project-Miau]";
    if ("Copyright Mojang AB. Do not distribute!".equals(text)) {
      int creditsX = instance.width - fontRenderer.getStringWidth(credits) - 2;
      instance.drawString(fontRenderer, credits, creditsX, y, 0xFFFFFFFF);
      return;
    }
    instance.drawString(fontRenderer, text, x, y, color);
  }
}
