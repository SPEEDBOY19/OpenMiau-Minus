package miau.module.modules.combat;

import java.util.concurrent.ConcurrentLinkedDeque;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ItemListProperty;
import miau.util.network.PacketUtil;
import miau.util.player.CombatTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.lwjgl.input.Mouse;

/**
 * @author strangerrrrs
 */
public class KnockbackDelay extends Module {
  public final FloatProperty distanceToTarget =
      new FloatProperty("Distance-to-target", 6.0f, 3.0f, 12.0f);
  public final FloatProperty chance = new FloatProperty("Chance", 100.0f, 0.0f, 100.0f);
  public final FloatProperty minimumDelay =
      new FloatProperty("Minimum-delay", 100.0f, 0.0f, 1000.0f);
  public final FloatProperty maximumDelay =
      new FloatProperty("Maximum-delay", 200.0f, 50.0f, 1000.0f);
  public final BooleanProperty inAir = new BooleanProperty("In-air", true);
  public final BooleanProperty lookingAtPlayer = new BooleanProperty("Looking-at-player", false);
  public final BooleanProperty requireLeftMouse = new BooleanProperty("Require-Left-mouse", false);
  public final BooleanProperty onlyWhitelistedItem =
      new BooleanProperty("Restrict-held-item", false);
  public final ItemListProperty whitelistedItems = new ItemListProperty("Whitelisted-items", "");

  private static final Minecraft mc = Minecraft.getMinecraft();
  private final ConcurrentLinkedDeque<Packet<?>> delayedPackets = new ConcurrentLinkedDeque<>();
  private long lagStartTime = -1;
  private long targetDelay = 0;

  public KnockbackDelay() {
    super("KnockbackDelay", false);
  }

  @Override
  public void onEnabled() {
    delayedPackets.clear();
    lagStartTime = -1;
  }

  @Override
  public void onDisabled() {
    flushDelayedPackets();
  }

  @EventTarget(Priority.HIGHEST)
  public void onReceivePacketHigh(PacketEvent e) {
    if (!this.isEnabled()) return;
    if (e.getType() != EventType.RECEIVE) return;

    if (e.getPacket() instanceof S08PacketPlayerPosLook) {
      flushDelayedPackets();
      return;
    }

    if (lagStartTime != -1) {
      e.setCancelled(true);
      delayedPackets.addLast(e.getPacket());
      return;
    }

    if (!(e.getPacket() instanceof S12PacketEntityVelocity)) {
      return;
    }

    if (mc.thePlayer == null || mc.theWorld == null) {
      return;
    }

    S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.getPacket();
    if (packet.getEntityID() != mc.thePlayer.getEntityId()) {
      return;
    }

    if (conditionsFailureReason() != null) {
      return;
    }

    if (chance.getValue() < 100.0 && Math.random() * 100.0 >= chance.getValue()) {
      return;
    }

    e.setCancelled(true);
    delayedPackets.addLast(e.getPacket());
    lagStartTime = System.currentTimeMillis();
    long minD = minimumDelay.getValue().longValue();
    long maxD = maximumDelay.getValue().longValue();
    if (minD > maxD) minD = maxD;
    targetDelay = minD + (long) (Math.random() * (maxD - minD + 1));
  }

  @EventTarget(Priority.LOWEST)
  public void onGameTick(TickEvent e) {
    if (!this.isEnabled()) return;
    if (e.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
      flushDelayedPackets();
      return;
    }

    if (lagStartTime == -1) return;

    if (conditionsFailureReason() != null) {
      flushDelayedPackets();
      return;
    }

    long nowMs = System.currentTimeMillis();

    if (nowMs - lagStartTime >= targetDelay) {
      flushDelayedPackets();
    }
  }

  @SuppressWarnings("unchecked")
  private void flushDelayedPackets() {
    lagStartTime = -1;
    if (mc.thePlayer != null && mc.getNetHandler() != null) {
      while (!delayedPackets.isEmpty()) {
        Packet<?> packet = delayedPackets.pollFirst();
        if (packet != null) {
          PacketUtil.handlePacket((Packet<INetHandlerPlayClient>) packet);
        }
      }
    }
    delayedPackets.clear();
  }

  private String conditionsFailureReason() {
    double maxRange = distanceToTarget.getValue();
    if (CombatTargeting.getTarget(
            true, false, false, true, false, true, maxRange, CombatTargeting.SortMode.DISTANCE)
        == null) {
      return "no target in range";
    }

    if (inAir.getValue() && mc.thePlayer.onGround) {
      return "not in air";
    }

    if (lookingAtPlayer.getValue()
        && CombatTargeting.getTarget(
                true, false, false, true, false, true, maxRange, CombatTargeting.SortMode.CROSSHAIR)
            == null) {
      return "not looking at player";
    }

    if (requireLeftMouse.getValue() && !Mouse.isButtonDown(0)) {
      return "LMB not held";
    }

    if (onlyWhitelistedItem.getValue()) {
      ItemStack held = mc.thePlayer.getHeldItem();
      if (held == null || !whitelistedItems.matches(held)) {
        return "held item not whitelisted";
      }
    }

    return null;
  }
}
