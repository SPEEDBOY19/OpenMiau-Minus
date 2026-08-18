package miau.notification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.util.animation.Animation;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public final class NotificationRenderer {

  private static final Font ICON_FONT = FontRepository.getFont("materialicons-regular", 24f);
  private static final Font TITLE_FONT = FontRepository.getFont("productsans-bold", 14f);
  private static final Font DESCRIPTION_FONT = FontRepository.getFont("productsans-medium", 13f);

  private final Map<Notification, Animation> animations = new HashMap<>();

  private static final NotificationRenderer INSTANCE = new NotificationRenderer();

  public static NotificationRenderer getInstance() {
    return INSTANCE;
  }

  public static void renderAll(ScaledResolution sr) {
    INSTANCE.render(sr);
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null || mc.thePlayer == null) return;
    if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat))
      return;
    render(new ScaledResolution(mc));
  }

  public void render(ScaledResolution sr) {
    final List<Notification> notifications = Miau.notificationManager.getNotifications();

    final float padding = 5;
    final float height = 21;
    final float iconSize = 14;
    final float iconOffset = iconSize + padding;

    final float scaledWidth = sr.getScaledWidth();
    final float scaledHeight = sr.getScaledHeight();

    float potionOffset = 0;
    Minecraft mc = Minecraft.getMinecraft();
    Font hudFont = TITLE_FONT;
    if (mc.thePlayer != null) {
      miau.module.modules.render.HUD hud =
          (miau.module.modules.render.HUD)
              Miau.moduleManager.getModule(miau.module.modules.render.HUD.class);
      if (hud != null) {
        hudFont = hud.getFont();
        if (hud.isEnabled() && !mc.gameSettings.showDebugInfo) {
          int effectsCount = mc.thePlayer.getActivePotionEffects().size();
          if (effectsCount > 0) {
            potionOffset = effectsCount * (hudFont.height() + 1.5f);
          }
        }
      }
    }

    for (int i = 0; i < notifications.size(); i++) {
      final Notification notification = notifications.get(i);

      Animation animation = animations.get(notification);
      if (animation == null) {
        animation = new Animation(Easing.EASE_OUT_EXPO, 400);
        animations.put(notification, animation);
      }

      final float width =
          Math.min(
              scaledWidth - padding * 2,
              Math.max(
                  100,
                  iconOffset
                      + Math.max(
                          hudFont.getStringWidth(notification.getTitle()) + (padding * 4),
                          hudFont.getStringWidth(notification.getDescription()))));

      final float endX = scaledWidth - width - padding;

      if (!notification.hasExpired()) {
        animation.setStartValue(scaledWidth);
      }
      animation.run(notification.hasExpired() ? scaledWidth : endX);

      final float x = animation.getValue();
      final float y = scaledHeight - (padding * 2) - ((i + 1) * (height + padding)) - potionOffset;

      final float progress = (float) notification.getTime() / notification.getDuration();
      final int iconColor = notification.getType().getIconColor();

      RoundedUtils.drawRound(x, y, width, height, 4, 0x80090909);

      float barWidth = (width - 0.5F) * progress;
      if (barWidth > 0) {
        RoundedUtils.drawRoundedRectRise(
            x + 0.5F,
            y + height - 1.5F,
            barWidth,
            1.5F,
            1F,
            applyOpacity(iconColor, 0.25F),
            false,
            false,
            progress > 0.95F,
            true);
      }

      RoundedUtils.drawRound(
          x + padding - 0.5F,
          y + (height - iconOffset) / 2F,
          iconOffset,
          iconOffset,
          2.75F,
          applyOpacity(darker(iconColor, 0.6F), 0.5F));

      float iconDrawY = y + 7.5f;
      ICON_FONT.draw(notification.getType().getIcon(), x + padding + 2.25F, iconDrawY, iconColor);

      hudFont.draw(notification.getTitle(), x + (padding * 2) + iconOffset, y + 4f, -1);
      hudFont.draw(
          notification.getDescription(), x + (padding * 2) + iconOffset, y + 12f, 0xFFAAAAAA);

      if (notification.hasExpired() && animation.getValue() == scaledWidth) {
        notifications.remove(notification);
        animations.remove(notification);
        i--;
      }
    }
  }

  private int darker(final int color, final float factor) {
    final float f = 1 - factor;
    final int r = (int) ((color >> 16 & 0xFF) * f);
    final int g = (int) ((color >> 8 & 0xFF) * f);
    final int b = (int) ((color & 0xFF) * f);
    final int a = color >> 24 & 0xFF;
    return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF) | ((a & 0xFF) << 24);
  }

  private int applyOpacity(final int color, float opacityFactor) {
    opacityFactor = Math.min(1, Math.max(0, opacityFactor));
    final int r = (color >> 16) & 0xFF;
    final int g = (color >> 8) & 0xFF;
    final int b = color & 0xFF;
    final int a = (int) (opacityFactor * 255.0f);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }
}
