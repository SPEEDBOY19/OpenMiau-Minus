package miau.event.impl;

import miau.event.Cancellable;
import miau.event.Event;
import net.minecraft.client.gui.GuiScreen;

public class GuiOpenEvent implements Event, Cancellable {
    private GuiScreen gui;
    private boolean cancelled;

    public GuiOpenEvent(GuiScreen gui) {
        this.gui = gui;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public GuiScreen getGui() {
        return this.gui;
    }

    public void setGui(GuiScreen gui) {
        this.gui = gui;
    }
}
