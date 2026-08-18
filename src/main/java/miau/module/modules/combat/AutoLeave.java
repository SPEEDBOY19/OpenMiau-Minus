package miau.module.modules.combat;

import java.util.Random;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;

public class AutoLeave extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final FloatProperty health = new FloatProperty("Health", 8.0F, 0.0F, 20.0F);
  public final ModeProperty mode =
      new ModeProperty("Mode", 0, new String[] {"Quit", "InvalidPacket", "SelfHurt", "IllegalChat"});

  private final Random random = new Random();

  public AutoLeave() {
    super("AutoLeave", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
    if (mc.thePlayer.getHealth() > this.health.getValue()) return;
    if (mc.thePlayer.capabilities.isCreativeMode || mc.isIntegratedServerRunning()) return;
    switch (this.mode.getValue()) {
      case 0:
        mc.theWorld.sendQuittingDisconnectingPacket();
        break;
      case 1:
        PacketUtil.sendPacket(
            new C03PacketPlayer.C04PacketPlayerPosition(
                Double.NaN,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                !mc.thePlayer.onGround));
        break;
      case 2:
        PacketUtil.sendPacket(
            new C02PacketUseEntity(mc.thePlayer, C02PacketUseEntity.Action.ATTACK));
        break;
      default:
        mc.thePlayer.sendChatMessage(
            Integer.toString(this.random.nextInt())
                + "\u00a7\u00a7\u00a7"
                + Integer.toString(this.random.nextInt()));
        break;
    }
    this.setEnabled(false);
  }
}
