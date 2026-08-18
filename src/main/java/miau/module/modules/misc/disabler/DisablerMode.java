package miau.module.modules.misc.disabler;

import miau.event.impl.*;
import miau.module.modules.misc.Disabler;
import net.minecraft.client.Minecraft;

public abstract class DisablerMode {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  protected final String name;
  protected final Disabler parent;

  public DisablerMode(String name, Disabler parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onTick(TickEvent event) {}

  public void onPacket(PacketEvent event) {}

  public void onStrafe(StrafeEvent event) {}

  public void onLivingUpdate(LivingUpdateEvent event) {}

  public void onMoveInput(MoveInputEvent event) {}

  public void onJump(JumpEvent event) {}

  public void onRender2D(Render2DEvent event) {}

  public void onLoadWorld(LoadWorldEvent event) {}
}
