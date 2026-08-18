package miau.module.modules.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.ChatUtil;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.ColorUtil;
import miau.util.render.RenderUtil;
import miau.util.render.Themes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public class SlinkNotifs extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final String blacklistedModules = "InvMove";

  private static final class Notification {
    String text;
    boolean enabled;
    long created;
    long closingAt = -1L;
  }

  private final List<Notification> notifications = new ArrayList<>();
  private final Map<String, Boolean> lastStates = new HashMap<>();

  private final String[] themeOptions = {
    "Default", "Rainbow", "Aurora", "Cherry", "Cotton Candy",
    "Flare", "Flower", "Forest", "Frost", "Gold",
    "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
  };
  private final String[] disableThemeOptions = {
    "Disabled", "Rainbow", "Aurora", "Cherry", "Cotton Candy",
    "Flare", "Flower", "Forest", "Frost", "Gold",
    "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
  };

  private boolean mouseDown = false;
  private boolean lastMouseDown = false;
  private float mouseX = 0.0f;
  private float mouseY = 0.0f;
  private boolean notificationDragging = false;
  private float notificationDragX = 0.0f;
  private float notificationDragY = 0.0f;
  private long closeMs = 230L;
  private long lastEditPositionWarningMs = 0L;

  public final BooleanProperty startWithFont = new BooleanProperty("Start with {f}", true);
  public final BooleanProperty syncHud2 = new BooleanProperty("Sync hud2", true);
  public final ModeProperty theme = new ModeProperty("Theme", 0, this.themeOptions);
  public final ModeProperty disableTheme =
      new ModeProperty("Disable theme", 0, this.disableThemeOptions);
  public final IntProperty duration = new IntProperty("Duration", 3000, 1000, 7000);
  public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 2.0F);
  public final ModeProperty position =
      new ModeProperty("Position", 0, new String[] {"Bottom Right", "Bottom Left", "Top Right", "Top Left"});
  public final BooleanProperty editPosition = new BooleanProperty("Edit position", false);

  public final FloatProperty brOffsetX = new FloatProperty("BR X", 12.0F, 0.0F, 10000.0F);
  public final FloatProperty brOffsetY = new FloatProperty("BR Y", 12.0F, 0.0F, 10000.0F);
  public final FloatProperty blOffsetX = new FloatProperty("BL X", 12.0F, 0.0F, 10000.0F);
  public final FloatProperty blOffsetY = new FloatProperty("BL Y", 12.0F, 0.0F, 10000.0F);
  public final FloatProperty trOffsetX = new FloatProperty("TR X", 12.0F, 0.0F, 10000.0F);
  public final FloatProperty trOffsetY = new FloatProperty("TR Y", 12.0F, 0.0F, 10000.0F);
  public final FloatProperty tlOffsetX = new FloatProperty("TL X", 12.0F, 0.0F, 10000.0F);
  public final FloatProperty tlOffsetY = new FloatProperty("TL Y", 12.0F, 0.0F, 10000.0F);

  public SlinkNotifs() {
    super("SlinkNotifs", false, true);
  }

  @Override
  public void onEnabled() {
    this.notifications.clear();
    this.notificationDragging = false;
    this.lastMouseDown = false;
    this.mouseDown = false;
    this.lastEditPositionWarningMs = 0L;
    this.syncModuleStates();
  }

  @Override
  public void onDisabled() {
    this.notifications.clear();
    this.lastStates.clear();
    this.notificationDragging = false;
  }

  @EventTarget
  public void onRenderTick(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    this.checkModuleChanges();
    this.renderNotifications();
  }

  private void checkModuleChanges() {
    this.printEditPositionWarningIfNeeded();

    int enabledCount = 0;
    int disabledCount = 0;
    String enabledName = "";
    String disabledName = "";

    for (Module module : Miau.moduleManager.modules.values()) {
      String moduleName = module.getName();
      if (moduleName == null || moduleName.equalsIgnoreCase(this.getName())) continue;
      if (this.shouldIgnoreModule(moduleName)) {
        this.lastStates.remove(moduleName);
        continue;
      }

      boolean current = module.isEnabled();
      Boolean old = this.lastStates.get(moduleName);
      if (old == null) {
        this.lastStates.put(moduleName, current);
        continue;
      }

      if (old != current) {
        this.lastStates.put(moduleName, current);
        if (current) {
          enabledCount++;
          if (enabledCount == 1) enabledName = moduleName;
        } else {
          disabledCount++;
          if (disabledCount == 1) disabledName = moduleName;
        }
      }
    }

    if (enabledCount > 0) {
      this.pushNotification(this.buildMessage(enabledName, enabledCount, true), true);
    }
    if (disabledCount > 0) {
      this.pushNotification(this.buildMessage(disabledName, disabledCount, false), false);
    }
  }

  private void syncModuleStates() {
    this.lastStates.clear();
    for (Module module : Miau.moduleManager.modules.values()) {
      String moduleName = module.getName();
      if (moduleName == null || moduleName.equalsIgnoreCase(this.getName())) continue;
      if (!this.shouldIgnoreModule(moduleName)) {
        this.lastStates.put(moduleName, module.isEnabled());
      }
    }
  }

  private String buildMessage(String firstName, int count, boolean enabled) {
    String prefix = enabled ? "Enabled " : "Disabled ";
    if (count == 1) {
      return prefix + this.aliasFor(firstName);
    }
    return prefix + count + " mods";
  }

  private String aliasFor(String moduleName) {
    if (!this.syncHud2.getValue()) return moduleName;
    return moduleName;
  }

  private void pushNotification(String text, boolean enabled) {
    Notification notification = new Notification();
    notification.text = text;
    notification.enabled = enabled;
    notification.created = System.currentTimeMillis();
    this.notifications.add(0, notification);

    int openCount = 0;
    for (Notification n : this.notifications) {
      if (!this.isNotificationClosing(n)) openCount++;
    }

    for (int i = this.notifications.size() - 1; openCount > 7 && i >= 0; i--) {
      Notification oldest = this.notifications.get(i);
      if (!this.isNotificationClosing(oldest)) {
        this.startNotificationClose(oldest);
        openCount--;
      }
    }
  }

  private void updateMouse(ScaledResolution sr) {
    this.lastMouseDown = this.mouseDown;
    this.mouseDown = Mouse.isButtonDown(0);

    float guiScale = sr.getScaleFactor();
    if (guiScale <= 0.0f) guiScale = 1.0f;
    float mpX = Mouse.getX();
    float mpY = Mouse.getY();
    this.mouseX = mpX / guiScale;
    this.mouseY = sr.getScaledHeight() - (mpY / guiScale);
  }

  private void renderNotifications() {
    ScaledResolution sr = new ScaledResolution(mc);
    int screenWidth = sr.getScaledWidth();
    int screenHeight = sr.getScaledHeight();
    long now = System.currentTimeMillis();
    boolean chatOpen = mc.currentScreen instanceof GuiChat;
    boolean editPos = this.editPosition.getValue();
    boolean previewMode = chatOpen && editPos;
    if (previewMode) {
      this.updateMouse(sr);
    } else {
      this.notificationDragging = false;
      this.lastMouseDown = false;
      this.mouseDown = false;
    }

    if (!this.notifications.isEmpty()) {
      long durationMs = this.duration.getValue();
      for (int i = this.notifications.size() - 1; i >= 0; i--) {
        Notification notification = this.notifications.get(i);
        if (this.isNotificationClosing(notification)) {
          if (now - notification.closingAt > this.closeMs) {
            this.notifications.remove(i);
          }
        } else {
          if (now - notification.created > durationMs) {
            this.startNotificationClose(notification);
          }
        }
      }
    }

    if (this.notifications.isEmpty() && !previewMode) {
      this.notificationDragging = false;
      return;
    }

    float uiScale = this.scale.getValue();
    boolean fontPrefix = this.useFontPrefix();
    float notifScale = 0.78f * uiScale;
    float tagScale = 0.70f * uiScale;
    float padding = 5.0f * uiScale;
    float height = 18.5f * uiScale;
    float tagHeight = 11.5f * uiScale;
    float spacing = height + 3.0f * uiScale;
    int corner = this.position.getValue();
    boolean right = corner == 0 || corner == 2;
    boolean bottom = corner == 0 || corner == 1;
    float qX1 = right ? screenWidth / 2.0f : 0.0f;
    float qX2 = right ? screenWidth : screenWidth / 2.0f;
    float qY1 = bottom ? screenHeight / 2.0f : 0.0f;
    float qY2 = bottom ? screenHeight : screenHeight / 2.0f;

    if (chatOpen && editPos) {
      RenderUtil.drawRect(qX1, qY1, qX2, qY2, 0x22000000);
      this.drawRectOutline(qX1 + 1.0f, qY1 + 1.0f, qX2 - 1.0f, qY2 - 1.0f, 1.0f, 0x88FFFFFF);
    }

    int count = previewMode ? 1 : this.notifications.size();
    float stackHeight = height + (count - 1) * spacing;
    String tag = Miau.clientName;
    String tagText = this.fontText(tag, fontPrefix);
    float tagTextWidth = this.getFontWidth(tagText, fontPrefix);
    float tagFontHeight = this.getFontHeight(fontPrefix) * tagScale;
    float tagWidth = tagTextWidth * tagScale + 12.0f * uiScale;
    float tagRadius = Math.max(3.0f * uiScale, tagHeight * 0.34f);
    float innerGap = 2.5f * uiScale;
    float rightGap = 8.0f * uiScale;

    for (int i = 0; i < count; i++) {
      Notification notification = null;
      String text = "Enabled Player ESP";
      boolean enabledNotification = true;
      long created = now;

      if (!previewMode) {
        notification = this.notifications.get(i);
        text = notification.text;
        enabledNotification = notification.enabled;
        created = notification.created;
      }

      long age = now - created;
      float inAnim = previewMode ? 1.0f : this.clamp(age / 210.0f, 0.0f, 1.0f);
      float outAnim =
          !previewMode && this.isNotificationClosing(notification)
              ? 1.0f
                  - this.clamp(
                      (now - notification.closingAt) / (float) this.closeMs, 0.0f, 1.0f)
              : 1.0f;
      float anim = this.easeOutCubic(Math.min(inAnim, outAnim));

      String messageText = this.fontText(text, fontPrefix);
      float messageScale = 0.60f * uiScale;
      float messageFontHeight = this.getFontHeight(fontPrefix) * messageScale;
      float messageTextWidth = this.getFontWidth(messageText, fontPrefix);
      float messageWidth = messageTextWidth * messageScale;
      float width = padding + tagWidth + innerGap + messageWidth + rightGap;
      float xOffset =
          this.clamp(
              this.getNotificationOffsetX(corner),
              2.0f,
              Math.max(2.0f, (qX2 - qX1) - width - 2.0f));
      float yOffset =
          this.clamp(
              this.getNotificationOffsetY(corner),
              2.0f,
              Math.max(2.0f, (qY2 - qY1) - stackHeight - 2.0f));

      float shownX = right ? (qX2 - width - xOffset) : (qX1 + xOffset);
      float hiddenX = right ? (screenWidth + width + 8.0f) : (-width - 8.0f);
      float x = hiddenX + (shownX - hiddenX) * anim;
      float baseY = bottom ? (qY2 - yOffset - height) : (qY1 + yOffset);
      float y = bottom ? (baseY - i * spacing) : (baseY + i * spacing);

      if (i == 0) {
        this.updateNotificationDrag(
            chatOpen && editPos, corner, qX1, qX2, qY1, qY2, shownX, baseY, width, height, stackHeight);
        xOffset =
            this.clamp(
                this.getNotificationOffsetX(corner),
                2.0f,
                Math.max(2.0f, (qX2 - qX1) - width - 2.0f));
        yOffset =
            this.clamp(
                this.getNotificationOffsetY(corner),
                2.0f,
                Math.max(2.0f, (qY2 - qY1) - stackHeight - 2.0f));
        shownX = right ? (qX2 - width - xOffset) : (qX1 + xOffset);
        x = hiddenX + (shownX - hiddenX) * anim;
        baseY = bottom ? (qY2 - yOffset - height) : (qY1 + yOffset);
        y = baseY;
      }

      float tagX = x + padding;
      float tagY = y + (height - tagHeight) / 2.0f;
      float textX = tagX + tagWidth + innerGap;
      float textY = y + (height - messageFontHeight) / 2.0f + 0.45f * uiScale;
      float tagTextX = tagX + (tagWidth - tagTextWidth * tagScale) / 2.0f;
      float tagTextY = tagY + (tagHeight - tagFontHeight) / 2.0f;
      int accent =
          this.getThemeColor(enabledNotification ? this.resolveTheme() : this.resolveDisableTheme());
      int pillColor = this.getPillColor(accent);

      this.renderNotificationBackground(x, y, width, height, height / 2.0f, anim);
      RenderUtil.drawRoundedRectangle(
          tagX, tagY, tagX + tagWidth, tagY + tagHeight, tagRadius, this.multiplyAlpha(pillColor, anim));
      this.drawScaledText(tagText, tagTextX, tagTextY, tagScale, this.multiplyAlpha(accent, anim), fontPrefix);
      this.drawScaledText(
          messageText, textX, textY, messageScale, this.multiplyAlpha(0xFFFFFFFF, anim), fontPrefix);
    }
  }

  private void renderNotificationBackground(float x, float y, float width, float height, float radius, float anim) {
    RenderUtil.drawRoundedRectangle(x, y, x + width, y + height, radius, this.multiplyAlpha(0xF008080A, anim));
  }

  private boolean isNotificationClosing(Notification notification) {
    return notification != null && notification.closingAt != -1L;
  }

  private void startNotificationClose(Notification notification) {
    if (notification == null || this.isNotificationClosing(notification)) return;
    notification.closingAt = System.currentTimeMillis();
  }

  private String resolveTheme() {
    int idx = this.theme.getValue();
    if (idx == 0) {
      return "default";
    }
    if (idx >= 1 && idx < this.themeOptions.length) return this.themeOptions[idx];
    return "white";
  }

  private String resolveDisableTheme() {
    int idx = this.disableTheme.getValue();
    if (idx <= 0) return this.resolveTheme();
    if (idx < this.disableThemeOptions.length) return this.disableThemeOptions[idx];
    return this.resolveTheme();
  }

  private int getThemeColor(String name) {
    String lo = name == null ? "white" : name.toLowerCase().trim();
    if (lo.equals("default")) {
      java.awt.Color c = Themes.getCurrentTheme().getFirstColor();
      return this.withAlpha(
          (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue(), 255);
    }
    if (lo.equals("rainbow")) return this.getRainbowColor();

    double p = this.getWaveRatio();
    if (lo.equals("aurora")) return this.lerpColor(0xFF7301C2, 0xFF17F0B1, p);
    if (lo.equals("cherry")) return this.lerpColor(0xFFDD3D69, 0xFFE0B3B7, p);
    if (lo.equals("cotton candy")) return this.lerpColor(0xFF92DAE8, 0xFFED68B8, p);
    if (lo.equals("flare")) return this.lerpColor(0xFFF26B16, 0xFFE4A61D, p);
    if (lo.equals("flower")) return this.lerpColor(0xFFC89AD8, 0xFFAC59B9, p);
    if (lo.equals("forest")) return this.lerpColor(0xFF1F7617, 0xFF60A623, p);
    if (lo.equals("frost")) return this.lerpColor(0xFFDFE3E3, 0xFFBCC5CA, p);
    if (lo.equals("gold")) return this.lerpColor(0xFFE5DF30, 0xFFDADAB6, p);
    if (lo.equals("grayscale")) return this.lerpColor(0xFF616368, 0xFFE7E8EA, p);
    if (lo.equals("inferno")) return this.lerpColor(0xFF350000, 0xFFC03912, p);
    if (lo.equals("royal")) return this.lerpColor(0xFF85BFE8, 0xFF1D3D87, p);
    if (lo.equals("sandstorm")) return this.lerpColor(0xFF9D9369, 0xFFF5E3B4, p);
    if (lo.equals("sky")) return this.lerpColor(0xFF81EAF8, 0xFF15BCD3, p);
    if (lo.equals("vine")) return this.lerpColor(0xFF27E439, 0xFF9AF8A1, p);
    return 0xFFFF6A1A;
  }

  private int getRainbowColor() {
    long now = System.currentTimeMillis();
    float hue = (now % 5000L) / 5000.0f;
    return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
  }

  private int getPillColor(int accent) {
    int r = (accent >> 16) & 0xFF;
    int g = (accent >> 8) & 0xFF;
    int b = accent & 0xFF;
    int darkR = this.clampInt((int) (r * 0.18), 0, 255);
    int darkG = this.clampInt((int) (g * 0.21), 0, 255);
    int darkB = this.clampInt((int) (b * 0.42), 0, 255);
    return this.withAlpha((darkR << 16) | (darkG << 8) | darkB, 85);
  }

  private double getWaveRatio() {
    long now = System.currentTimeMillis();
    float time = (now % 5000L) / 5000.0f;
    return time <= 0.5f ? time * 2.0 : 2.0 - time * 2.0;
  }

  private int lerpColor(int c1, int c2, double t) {
    int r =
        this.clampInt(
            (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t)), 0, 255);
    int g =
        this.clampInt(
            (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t)), 0, 255);
    int b =
        this.clampInt(
            (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t)), 0, 255);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private int clampInt(int value, int min, int max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private boolean shouldIgnoreModule(String moduleName) {
    if (moduleName == null || this.blacklistedModules == null || this.blacklistedModules.isEmpty()) {
      return false;
    }
    String[] parts = this.blacklistedModules.split(",");
    for (String part : parts) {
      if (part.trim().equalsIgnoreCase(moduleName)) return true;
    }
    return false;
  }

  private boolean isEditingPosition() {
    return this.editPosition.getValue();
  }

  private void printEditPositionWarningIfNeeded() {
    if (!this.isEditingPosition()) {
      this.lastEditPositionWarningMs = 0L;
      return;
    }
    long now = System.currentTimeMillis();
    if (this.lastEditPositionWarningMs != 0L && now - this.lastEditPositionWarningMs < 5000L) return;
    this.lastEditPositionWarningMs = now;
    ChatUtil.display(
        "&7[&dR&7] &b%ss&7: &c\"Edit position\" is enabled&7.", this.getName());
  }

  private float getNotificationOffsetX(int corner) {
    if (corner == 0) return this.brOffsetX.getValue();
    if (corner == 1) return this.blOffsetX.getValue();
    if (corner == 2) return this.trOffsetX.getValue();
    return this.tlOffsetX.getValue();
  }

  private float getNotificationOffsetY(int corner) {
    if (corner == 0) return this.brOffsetY.getValue();
    if (corner == 1) return this.blOffsetY.getValue();
    if (corner == 2) return this.trOffsetY.getValue();
    return this.tlOffsetY.getValue();
  }

  private void setNotificationOffsets(int corner, float x, float y) {
    if (corner == 0) {
      this.brOffsetX.setValue(x);
      this.brOffsetY.setValue(y);
    } else if (corner == 1) {
      this.blOffsetX.setValue(x);
      this.blOffsetY.setValue(y);
    } else if (corner == 2) {
      this.trOffsetX.setValue(x);
      this.trOffsetY.setValue(y);
    } else {
      this.tlOffsetX.setValue(x);
      this.tlOffsetY.setValue(y);
    }
  }

  private void updateNotificationDrag(
      boolean chatOpen,
      int corner,
      float qX1,
      float qX2,
      float qY1,
      float qY2,
      float shownX,
      float shownY,
      float width,
      float height,
      float stackHeight) {
    if (!chatOpen || !this.mouseDown) {
      this.notificationDragging = false;
      return;
    }

    boolean right = corner == 0 || corner == 2;
    boolean bottom = corner == 0 || corner == 1;

    if (!this.notificationDragging
        && this.mouseDown
        && !this.lastMouseDown
        && this.isMouseInside(shownX, shownY, shownX + width, shownY + height)) {
      this.notificationDragging = true;
      this.notificationDragX = this.mouseX - shownX;
      this.notificationDragY = this.mouseY - shownY;
    }

    if (!this.notificationDragging) return;

    float targetX =
        this.clamp(this.mouseX - this.notificationDragX, qX1 + 2.0f, qX2 - width - 2.0f);
    float targetY;
    if (bottom) {
      targetY =
          this.clamp(
              this.mouseY - this.notificationDragY,
              qY1 + stackHeight - height + 2.0f,
              qY2 - height - 2.0f);
    } else {
      targetY =
          this.clamp(
              this.mouseY - this.notificationDragY,
              qY1 + 2.0f,
              qY2 - stackHeight - 2.0f);
    }

    float nextXOffset = right ? (qX2 - width - targetX) : (targetX - qX1);
    float nextYOffset = bottom ? (qY2 - height - targetY) : (targetY - qY1);
    nextXOffset = this.clamp(nextXOffset, 2.0f, Math.max(2.0f, (qX2 - qX1) - width - 2.0f));
    nextYOffset = this.clamp(nextYOffset, 2.0f, Math.max(2.0f, (qY2 - qY1) - stackHeight - 2.0f));
    this.setNotificationOffsets(corner, nextXOffset, nextYOffset);
  }

  private boolean isMouseInside(float x1, float y1, float x2, float y2) {
    return this.mouseX >= x1 && this.mouseX <= x2 && this.mouseY >= y1 && this.mouseY <= y2;
  }

  private void drawRectOutline(float x1, float y1, float x2, float y2, float thickness, int color) {
    RenderUtil.drawRect(x1, y1, x2, y1 + thickness, color);
    RenderUtil.drawRect(x1, y2 - thickness, x2, y2, color);
    RenderUtil.drawRect(x1, y1, x1 + thickness, y2, color);
    RenderUtil.drawRect(x2 - thickness, y1, x2, y2, color);
  }

  private boolean useFontPrefix() {
    return this.startWithFont.getValue();
  }

  private String fontText(String text, boolean usePrefix) {
    String value = text == null ? "" : text;
    return value;
  }

  private Font getFont() {
    return FontRepository.getHudFont(18);
  }

  private float getFontWidth(String text, boolean custom) {
    if (custom) {
      return this.getFont().width(text);
    }
    return mc.fontRendererObj.getStringWidth(text);
  }

  private float getFontHeight(boolean custom) {
    if (custom) {
      return this.getFont().height();
    }
    return mc.fontRendererObj.FONT_HEIGHT;
  }

  private void drawScaledText(String text, float x, float y, float scale, int color, boolean custom) {
    RenderUtil.scaleStart(x, y, scale);
    if (custom) {
      this.getFont().drawWithShadow(text, x, y, color);
    } else {
      mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
    }
    RenderUtil.scaleEnd();
  }

  private int withAlpha(int color, int alpha) {
    return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
  }

  private int multiplyAlpha(int color, float alpha) {
    int currentAlpha = (color >> 24) & 0xFF;
    int nextAlpha = (int) (currentAlpha * this.clamp(alpha, 0.0f, 1.0f));
    return this.withAlpha(color, nextAlpha);
  }

  private float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private float easeOutCubic(float t) {
    float value = this.clamp(t, 0.0f, 1.0f);
    float inv = 1.0f - value;
    return 1.0f - inv * inv * inv;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.theme.getModeString()};
  }
}
