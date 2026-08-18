package miau.util.dragging;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public class MouseUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static float[] getMouse() {
    final ScaledResolution scaledResolution = new ScaledResolution(mc);
    final int mouseX = Mouse.getX() * scaledResolution.getScaledWidth() / mc.displayWidth;
    final int mouseY =
        scaledResolution.getScaledHeight()
            - Mouse.getY() * scaledResolution.getScaledHeight() / mc.displayHeight
            - 1;
    return new float[] {mouseX, mouseY};
  }
}
