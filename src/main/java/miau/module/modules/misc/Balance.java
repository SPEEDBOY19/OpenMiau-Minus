package miau.module.modules.misc;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.Module;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

public class Balance extends Module {
  public static int balance = 0;
  public static long lastC03 = System.currentTimeMillis();

  public Balance() {
    super("Balance", false);
  }

  @Override
  public void onEnabled() {
    balance = 0;
    lastC03 = System.currentTimeMillis();
  }

  @EventTarget
  public void onPacket(PacketEvent e) {
    if (!this.isEnabled()) return;

    if (e.getType() == EventType.SEND && e.getPacket() instanceof C03PacketPlayer) {
      if (!e.isCancelled()) {
        balance += 50;
      }
      balance -= (int) (System.currentTimeMillis() - lastC03);
      lastC03 = System.currentTimeMillis();
    }
  }

  public static void onRelease(Packet<?> packet) {
    if (packet instanceof C03PacketPlayer) {
      balance += 50;
    }
  }
}
