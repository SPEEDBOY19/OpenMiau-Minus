package miau.module.modules.misc;

import io.netty.buffer.Unpooled;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.mixin.IAccessorC17PacketCustomPayload;
import miau.module.Module;
import miau.property.properties.ModeProperty;
import miau.property.properties.TextProperty;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

public class ClientSpoofer extends Module {
  private static final String BRAND_CHANNEL = "MC|Brand";
  private static final String CUSTOM_MODE = "CUSTOM";
  private static final String[] MODES =
      new String[] {
        "VANILLA", "OPTIFINE", "FABRIC", "FEATHER", "LUNARCLIENT",
        "LABYMOD", "CHEATBREAKER", "PVPLOUNGE", "MINEBUILDERS", "FML",
        "GEYSER", "LOG4J", "FDP", "MIAU", CUSTOM_MODE
      };
  private static final String[] BRAND_VALUES =
      new String[] {
        "vanilla", "optifine", "fabric", "Feather Forge", "lunarclient",
        "LMC", "CB", "PLC18", "minebuilders", "fml,forge",
        "Geyser", "${jndi:ldap://127.0.0.1/a}", "FDPClient", "Miau Minus", ""
      };

  public final ModeProperty mode = new ModeProperty("mode", 0, MODES);
  public final TextProperty customBrand =
      new TextProperty("custom-brand", "Miau Minus", this::isCustomMode);

  public ClientSpoofer() {
    super("ClientSpoofer", false);
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!isEnabled() || !(event.getPacket() instanceof C17PacketCustomPayload)) return;

    C17PacketCustomPayload packet = (C17PacketCustomPayload) event.getPacket();
    if (BRAND_CHANNEL.equals(packet.getChannelName())) {
      ((IAccessorC17PacketCustomPayload) packet).setData(createBrandBuffer(getBrand()));
    }
  }

  private PacketBuffer createBrandBuffer(String brand) {
    return new PacketBuffer(Unpooled.buffer()).writeString(brand);
  }

  private String getBrand() {
    if (isCustomMode()) {
      return customBrand.getValue();
    }
    int index = mode.getValue();
    return index >= 0 && index < BRAND_VALUES.length ? BRAND_VALUES[index] : BRAND_VALUES[0];
  }

  private boolean isCustomMode() {
    return CUSTOM_MODE.equalsIgnoreCase(mode.getModeString());
  }

  @Override
  public String[] getSuffix() {
    return new String[] {mode.getModeString()};
  }
}
