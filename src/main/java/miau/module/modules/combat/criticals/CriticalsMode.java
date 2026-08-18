package miau.module.modules.combat.criticals;

import miau.event.impl.AttackEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.Criticals;

public abstract class CriticalsMode {
  protected static final net.minecraft.client.Minecraft mc =
      net.minecraft.client.Minecraft.getMinecraft();
  protected final String name;
  protected final Criticals parent;

  public CriticalsMode(String name, Criticals parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onUpdate(UpdateEvent event) {}

  public void onAttack(AttackEvent event) {}

  public void onPacket(PacketEvent event) {}

  public void onMoveInput(MoveInputEvent event) {}
}
