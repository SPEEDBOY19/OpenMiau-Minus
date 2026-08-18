package miau.module.modules.render;

import miau.module.Module;
import miau.ui.nogui.NoguiGui;
import net.minecraft.client.Minecraft;

public class NoGui extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public NoGui() {
        super("NoGui", false);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        mc.displayGuiScreen(new NoguiGui());
    }
}
