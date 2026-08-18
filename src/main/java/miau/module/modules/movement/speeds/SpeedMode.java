package miau.module.modules.movement.speeds;

import java.util.Collections;
import java.util.List;
import miau.event.impl.JumpEvent;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.MoveInputEvent;
import miau.module.modules.movement.Speed;
import miau.property.Property;
import net.minecraft.client.Minecraft;

public abstract class SpeedMode {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  protected final String name;
  protected final Speed parent;

  public SpeedMode(String name, Speed parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public List<Property<?>> getProperties() {
    return Collections.emptyList();
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onStrafe(StrafeEvent event) {}

  public void onLivingUpdate(LivingUpdateEvent event) {}

  public void onPacket(PacketEvent event) {}

  public void onJump(JumpEvent event) {}

  public void onMoveInput(MoveInputEvent event) {}
}
