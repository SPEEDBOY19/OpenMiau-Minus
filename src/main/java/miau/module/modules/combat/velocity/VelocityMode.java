package miau.module.modules.combat.velocity;

import miau.event.impl.AttackEvent;
import miau.event.impl.JumpEvent;
import miau.event.impl.KnockbackEvent;
import miau.event.impl.LivingUpdateEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.impl.HitSlowDownEvent;
import miau.module.modules.combat.Velocity;
import net.minecraft.client.Minecraft;

public abstract class VelocityMode {
  protected final String name;
  protected final Velocity parent;
  protected static final Minecraft mc = Minecraft.getMinecraft();

  public VelocityMode(String name, Velocity parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public Velocity getParent() {
    return parent;
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onUpdate(UpdateEvent event) {}

  public void onPacket(PacketEvent event) {}

  public void onKnockback(KnockbackEvent event) {}

  public void onLivingUpdate(LivingUpdateEvent event) {}

  public void onMoveInput(MoveInputEvent event) {}

  public void onAttack(AttackEvent event) {}

  public void onStrafe(StrafeEvent event) {}

  public void onJump(JumpEvent event) {}

  public void onRender3D(Render3DEvent event) {}

  public void onHitSlowDown(HitSlowDownEvent event) {}

  public void onTick(TickEvent event) {}
}
