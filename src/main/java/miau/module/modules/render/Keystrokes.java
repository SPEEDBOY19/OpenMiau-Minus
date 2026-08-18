package miau.module.modules.render;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LeftClickMouseEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.RightClickMouseEvent;
import miau.module.Module;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class Keystrokes extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final Deque<Long> leftClicks = new ArrayDeque<>();
  private final Deque<Long> rightClicks = new ArrayDeque<>();

  // Tọa độ & Scale
  public final IntProperty x = new IntProperty("x", 6, 0, 1000);
  public final IntProperty y = new IntProperty("y", 18, 0, 1000);
  public final IntProperty scale = new IntProperty("scale", 100, 50, 200);
  public final IntProperty opacity = new IntProperty("opacity", 102, 20, 255);
  public final BooleanProperty centerY = new BooleanProperty("center-y", true);

  // Tùy chọn Bật/Tắt các thành phần
  public final BooleanProperty showSpace = new BooleanProperty("space-bar", true);
  public final BooleanProperty showMouse = new BooleanProperty("mouse-buttons", true);
  public final BooleanProperty showCPS =
      new BooleanProperty("cps", true, () -> this.showMouse.getValue());
  public final BooleanProperty showMouseMovement = new BooleanProperty("mouse-movement", true);
  public final BooleanProperty mouseTrail =
      new BooleanProperty("mouse-trail", true, () -> this.showMouseMovement.getValue());

  // Biến lưu vị trí chấm tròn di chuyển chuột
  private float mouseDotX = 0f;
  private float mouseDotY = 0f;

  // Lớp lưu lịch sử vết di chuyển chuột (Trail)
  private static class TrailPoint {
    float x, y;
    long time;

    TrailPoint(float x, float y, long time) {
      this.x = x;
      this.y = y;
      this.time = time;
    }
  }

  private final List<TrailPoint> mouseTrailList = new ArrayList<>();

  public Keystrokes() {
    super("Keystrokes", false);
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    recordLeftClick();
  }

  public static void recordLeftClick() {
    MiauKeystrokesHolder.INSTANCE.leftClicks.addLast(System.currentTimeMillis());
  }

  private static class MiauKeystrokesHolder {
    private static final Keystrokes INSTANCE =
        (Keystrokes) miau.Miau.moduleManager.modules.get(Keystrokes.class);
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    rightClicks.addLast(System.currentTimeMillis());
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled()) return;
    long now = System.currentTimeMillis();
    prune(leftClicks, now);
    prune(rightClicks, now);

    ScaledResolution sr = new ScaledResolution(mc);
    float scaleValue = this.scale.getValue() / 100.0F;
    int baseX = this.x.getValue();
    int baseY =
        this.centerY.getValue() ? sr.getScaledHeight() / 2 - this.y.getValue() : this.y.getValue();

    GlStateManager.pushMatrix();
    GlStateManager.scale(scaleValue, scaleValue, 1.0F);
    baseX = (int) (baseX / scaleValue);
    baseY = (int) (baseY / scaleValue);

    // 1. Phím WASD
    drawKey(
        "W",
        baseX + 24,
        baseY,
        22,
        22,
        Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
    drawKey(
        "A",
        baseX,
        baseY + 24,
        22,
        22,
        Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()));
    drawKey(
        "S",
        baseX + 24,
        baseY + 24,
        22,
        22,
        Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
    drawKey(
        "D",
        baseX + 48,
        baseY + 24,
        22,
        22,
        Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()));

    int currentY = baseY + 48;

    // 2. Nút SPACE (Nếu bật)
    if (this.showSpace.getValue()) {
      drawSpaceKey(
          baseX,
          currentY,
          70,
          14,
          Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()));
      currentY += 16;
    }

    // 3. Chuột LMB / RMB (Nếu bật)
    if (this.showMouse.getValue()) {
      drawMouse("LMB", leftClicks.size(), baseX, currentY, 34, 22, Mouse.isButtonDown(0));
      drawMouse("RMB", rightClicks.size(), baseX + 36, currentY, 34, 22, Mouse.isButtonDown(1));
    }

    // 4. Ô di chuyển chuột - Mouse Movement (Nếu bật)
    if (this.showMouseMovement.getValue()) {
      drawMouseMovementBox(baseX + 74, baseY, 46, 70);
    }

    GlStateManager.popMatrix();
  }

  private void prune(Deque<Long> clicks, long now) {
    while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
      clicks.removeFirst();
    }
  }

  private int background(boolean down) {
    int alpha = down ? 204 : this.opacity.getValue();
    return (alpha << 24) | (down ? 0xFFFFFF : 0x000000);
  }

  private void drawKey(String label, int x, int y, int w, int h, boolean down) {
    int fg = down ? 0xFF111111 : 0xFFFFFFFF;
    Gui.drawRect(x, y, x + w, y + h, background(down));
    mc.fontRendererObj.drawStringWithShadow(
        label, x + w / 2 - mc.fontRendererObj.getStringWidth(label) / 2, y + 7, fg);
  }

  private void drawSpaceKey(int x, int y, int w, int h, boolean down) {
    int fg = down ? 0xFF111111 : 0xFFFFFFFF;
    Gui.drawRect(x, y, x + w, y + h, background(down));
    
    int lineW = 28;
    int lineX = x + (w - lineW) / 2;
    int lineY = y + h / 2;
    Gui.drawRect(lineX, lineY, lineX + lineW, lineY + 2, fg);
  }

  private void drawMouse(String label, int cps, int x, int y, int w, int h, boolean down) {
    int fg = down ? 0xFF111111 : 0xFFFFFFFF;
    Gui.drawRect(x, y, x + w, y + h, background(down));
    int labelY = this.showCPS.getValue() ? y + 3 : y + 7;
    mc.fontRendererObj.drawStringWithShadow(
        label, x + w / 2 - mc.fontRendererObj.getStringWidth(label) / 2, labelY, fg);
    if (this.showCPS.getValue()) {
      String cpsText = cps + " CPS";
      mc.fontRendererObj.drawStringWithShadow(
          cpsText, x + w / 2 - mc.fontRendererObj.getStringWidth(cpsText) / 2, y + 12, fg);
    }
  }

  // Khung theo dõi di chuyển chuột + Vết Trail hạt tròn
  private void drawMouseMovementBox(int x, int y, int w, int h) {
    // Background bo tròn góc màu tối đục chuẩn giao diện
    int bgAlpha = this.opacity.getValue();
    RoundedUtils.drawRound(x, y, w, h, 6f, new Color(0, 0, 0, bgAlpha));

    // Lấy Delta di chuyển chuột chuẩn từ Minecraft MouseHelper (Fix lỗi giật lag / đứng hình)
    float dx = 0f;
    float dy = 0f;
    if (mc.inGameHasFocus && mc.mouseHelper != null) {
      dx = mc.mouseHelper.deltaX * 0.15f;
      dy = -mc.mouseHelper.deltaY * 0.15f;
    }

    // Giảm dần chuyển động (hồi tâm mượt về giữa)
    mouseDotX = (mouseDotX + dx) * 0.75f;
    mouseDotY = (mouseDotY + dy) * 0.75f;

    // Giới hạn chấm tròn không vượt ra khỏi khung
    int maxOffset = (w / 2) - 6;
    float dotX = Math.max(-maxOffset, Math.min(maxOffset, mouseDotX));
    float dotY = Math.max(-maxOffset, Math.min(maxOffset, mouseDotY));

    float centerX = x + w / 2f;
    float centerY = y + h / 2f;

    float finalDotX = centerX + dotX;
    float finalDotY = centerY + dotY;

    long now = System.currentTimeMillis();

    // Thêm điểm vào Trail khi di chuột
    if (this.mouseTrail.getValue()) {
      if (Math.abs(dx) > 0.01f || Math.abs(dy) > 0.01f) {
        if (mouseTrailList.isEmpty()
            || Math.hypot(
                    finalDotX - mouseTrailList.get(mouseTrailList.size() - 1).x,
                    finalDotY - mouseTrailList.get(mouseTrailList.size() - 1).y)
                > 1.2) {
          mouseTrailList.add(new TrailPoint(finalDotX, finalDotY, now));
        }
      }

      // Xóa các hạt cũ quá 450ms
      mouseTrailList.removeIf(p -> now - p.time > 450L);

      // Vẽ Trail dạng các chấm tròn mờ dần (như hình minh họa)
      for (TrailPoint p : mouseTrailList) {
        float age = (now - p.time) / 450f;
        float alpha = (1.0f - age) * 0.75f;
        float radius = 2.8f * (1.0f - age * 0.35f);

        int color = ((int) (alpha * 255) << 24) | 0x22C55E; // Màu xanh lá nõn #22c55e
        drawCircle(p.x, p.y, radius, color);
      }
    }

    // Vẽ điểm tròn chính ở đầu trail (Màu xanh neon sáng)
    drawCircle(finalDotX, finalDotY, 3.2f, 0xFF00FF88);
  }

  // Hàm vẽ hình tròn mượt mà bằng OpenGL
  private void drawCircle(float cx, float cy, float r, int color) {
    float a = (float) (color >> 24 & 255) / 255.0F;
    float red = (float) (color >> 16 & 255) / 255.0F;
    float green = (float) (color >> 8 & 255) / 255.0F;
    float blue = (float) (color & 255) / 255.0F;

    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.color(red, green, blue, a);

    GL11.glEnable(GL11.GL_POINT_SMOOTH);
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    GL11.glVertex2f(cx, cy);
    for (int i = 0; i <= 18; i++) {
      double angle = i * Math.PI * 2 / 18;
      GL11.glVertex2f(
          (float) (cx + Math.sin(angle) * r),
          (float) (cy + Math.cos(angle) * r));
    }
    GL11.glEnd();
    GL11.glDisable(GL11.GL_POINT_SMOOTH);

    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> list = new ArrayList<>();
    list.add(x);
    list.add(y);
    list.add(scale);
    list.add(opacity);
    list.add(centerY);
    list.add(showSpace);
    list.add(showMouse);
    list.add(showCPS);
    list.add(showMouseMovement);
    list.add(mouseTrail);
    return list;
  }
}