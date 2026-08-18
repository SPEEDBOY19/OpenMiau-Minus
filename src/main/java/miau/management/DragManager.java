package miau.management;

import java.awt.Color;
import java.util.ArrayList;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.management.drag.Orientation;
import miau.management.drag.Snap;
import miau.module.Module;
import miau.property.Property;
import miau.property.properties.DragProperty;
import miau.util.render.ShapeUtil;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public class DragManager {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private boolean wasMouseDown = false;

  private static DragProperty selectedValue = null;
  private static Vector2d offset;
  private static final ArrayList<DragProperty> draggables = new ArrayList<>();
  private static final ArrayList<String> draggableNames = new ArrayList<>();

  public static ArrayList<Snap> snaps = new ArrayList<>();
  public static Snap selected;

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    boolean shouldRender =
        mc.currentScreen instanceof GuiChat
            || mc.currentScreen instanceof miau.ui.clickgui.ClickGui;

    if (!shouldRender) {
      selectedValue = null;
      wasMouseDown = false;
      return;
    }

    ScaledResolution scaledResolution = new ScaledResolution(mc);
    final int width = scaledResolution.getScaledWidth();
    final int height = scaledResolution.getScaledHeight();

    draggables.clear();
    draggableNames.clear();

    for (Module module : Miau.moduleManager.modules.values()) {
      if (module.isEnabled()) {
        for (Property<?> value : module.getValues()) {
          if (value instanceof DragProperty) {
            DragProperty dp = (DragProperty) value;
            if (!dp.structure) {
              draggables.add(dp);
              draggableNames.add(module.getName());
            }
          }
        }
      }
    }

    if (Miau.notificationManager != null) {}

    int mouseX = Mouse.getX() * width / mc.displayWidth;
    int mouseY = height - Mouse.getY() * height / mc.displayHeight - 1;

    boolean isMouseDown = Mouse.isButtonDown(0);
    boolean justClicked = isMouseDown && !wasMouseDown;
    wasMouseDown = isMouseDown;

    if (!isMouseDown) {
      selectedValue = null;
    }

    if (justClicked) {
      for (int i = 0; i < draggables.size(); i++) {
        DragProperty positionValue = draggables.get(i);
        Vector2d position = positionValue.position;
        Vector2d scale = positionValue.scale;

        if (!positionValue.structure
            && positionValue.render
            && mouseX >= position.x
            && mouseX <= position.x + scale.x
            && mouseY >= position.y
            && mouseY <= position.y + scale.y) {
          selectedValue = positionValue;
          offset = new Vector2d(position.x - mouseX, position.y - mouseY);
        }
      }
    }

    if (selectedValue != null) {
      final double positionX = mouseX + offset.x;
      final double positionY = mouseY + offset.y;

      selectedValue.targetPosition = new Vector2d(positionX, positionY);

      snaps.clear();
      double edgeSnap = 2;

      snaps.add(new Snap(width / 2f, 5, Orientation.HORIZONTAL, true, true, true));
      snaps.add(new Snap(height / 2f, 5, Orientation.VERTICAL, true, true, true));

      snaps.add(new Snap(height - edgeSnap, 5, Orientation.VERTICAL, false, false, true));
      snaps.add(new Snap(edgeSnap, 5, Orientation.VERTICAL, false, true, false));
      snaps.add(new Snap(width - edgeSnap, 5, Orientation.HORIZONTAL, false, false, true));
      snaps.add(new Snap(edgeSnap, 5, Orientation.HORIZONTAL, false, true, false));

      for (DragProperty positionValue : draggables) {
        if (positionValue == selectedValue) continue;

        snaps.add(
            new Snap(
                positionValue.position.x + positionValue.scale.x + edgeSnap,
                5,
                Orientation.HORIZONTAL,
                false,
                true,
                false));
        snaps.add(
            new Snap(
                positionValue.position.x - edgeSnap,
                5,
                Orientation.HORIZONTAL,
                false,
                false,
                true));

        snaps.add(new Snap(positionValue.position.y, 5, Orientation.VERTICAL, false, false, true));
        snaps.add(
            new Snap(
                positionValue.position.y + positionValue.scale.y,
                5,
                Orientation.VERTICAL,
                false,
                true,
                false));
      }

      double closest;
      selected = null;
      int snapColor = new Color(255, 255, 255, 60).getRGB();

      for (Snap snap : snaps) {
        switch (snap.orientation) {
          case VERTICAL:
            closest = Double.MAX_VALUE;

            for (double y = -selectedValue.scale.y; y <= 0; y += selectedValue.scale.y / 2f) {
              if ((y == -selectedValue.scale.y / 2 && !snap.center)
                  || (y == -selectedValue.scale.y && !snap.left)
                  || (y == 0 && !snap.right)) {
                continue;
              }

              double distance = Math.abs(selectedValue.targetPosition.y - (snap.position + y));

              if (distance < snap.distance && distance < closest) {
                closest = distance;
                selectedValue.targetPosition.y = snap.position + y;
                selected = snap;
                ShapeUtil.drawRect(
                    0,
                    (float) selected.position,
                    width,
                    (float) selected.position + 0.5f,
                    snapColor);
              }
            }
            break;

          case HORIZONTAL:
            closest = Double.MAX_VALUE;
            for (double x = -selectedValue.scale.x; x <= 0; x += selectedValue.scale.x / 2f) {
              if ((x == -selectedValue.scale.x / 2 && !snap.center)
                  || (x == -selectedValue.scale.x && !snap.left)
                  || (x == 0 && !snap.right)) {
                continue;
              }

              double distance = Math.abs(selectedValue.targetPosition.x - (snap.position + x));

              if (distance < snap.distance && distance < closest) {
                closest = distance;
                selectedValue.targetPosition.x = snap.position + x;
                selected = snap;
                ShapeUtil.drawRect(
                    (float) selected.position,
                    0,
                    (float) selected.position + 0.5f,
                    height,
                    snapColor);
              }
            }
            break;
        }
      }
    }

    for (int i = 0; i < draggables.size(); i++) {
      DragProperty positionValue = draggables.get(i);
      String name = draggableNames.get(i);
      float padding = 2;

      if (!positionValue.render) continue;

      positionValue.position.x = Math.max(padding, positionValue.position.x);
      positionValue.position.x =
          Math.min(width - positionValue.scale.x - padding, positionValue.position.x);

      positionValue.position.y = Math.max(padding, positionValue.position.y);
      positionValue.position.y =
          Math.min(height - positionValue.scale.y - padding, positionValue.position.y);

      positionValue.targetPosition.x = Math.max(padding, positionValue.targetPosition.x);
      positionValue.targetPosition.x =
          Math.min(width - positionValue.scale.x - padding, positionValue.targetPosition.x);

      positionValue.targetPosition.y = Math.max(padding, positionValue.targetPosition.y);
      positionValue.targetPosition.y =
          Math.min(height - positionValue.scale.y - padding, positionValue.targetPosition.y);

      positionValue.position =
          new Vector2d(
              Math.min(width - positionValue.scale.x - padding, positionValue.targetPosition.x),
              Math.min(height - positionValue.scale.y - padding, positionValue.targetPosition.y));
    }
  }
}
