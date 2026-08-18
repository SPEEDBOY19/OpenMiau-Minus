package miau.ui.clickgui.demise;

import net.minecraft.client.Minecraft;

public interface IComponent {
  Minecraft mc = Minecraft.getMinecraft();

  default void drawScreen(int mouseX, int mouseY) {}

  default void mouseClicked(int mouseX, int mouseY, int mouseButton) {}

  default void mouseReleased(int mouseX, int mouseY, int state) {}

  default void keyTyped(char typedChar, int keyCode) {}
}
