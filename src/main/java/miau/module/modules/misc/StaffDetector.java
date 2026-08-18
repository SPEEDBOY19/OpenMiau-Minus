package miau.module.modules.misc;

import java.util.*;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.notification.NotificationType;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S38PacketPlayerListItem;

public class StaffDetector extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"3FMC"});
  public final BooleanProperty autoLeave = new BooleanProperty("auto-leave", false);

  private static final Set<String> STAFF_LIST =
      new HashSet<>(
          Arrays.asList(
              "VinhGaming",
              "cheesethesylveon",
              "thanhhau",
              "sennekoi",
              "lasgana",
              "novapev4",
              "_DuckVN_",
              "khoaho01623"));

  public StaffDetector() {
    super("StaffDetector", false, false);
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE) return;
    Packet<?> packet = event.getPacket();

    if (packet instanceof S38PacketPlayerListItem) {
      S38PacketPlayerListItem pkt = (S38PacketPlayerListItem) packet;

      if (pkt.getAction() == S38PacketPlayerListItem.Action.ADD_PLAYER) {
        for (S38PacketPlayerListItem.AddPlayerData data : pkt.getEntries()) {
          if (data == null || data.getProfile() == null) continue;
          String name = data.getProfile().getName();
          if (name == null) continue;

          if (STAFF_LIST.contains(name.toLowerCase())) {
            alert(
                NotificationType.ERROR,
                "Staff Online!",
                name + " joined the server",
                "&c&l[STAFF] &r&f" + name + " &ajoined the server!");
            triggerAutoLeave(name);
          }
        }

      } else if (pkt.getAction() == S38PacketPlayerListItem.Action.REMOVE_PLAYER
          && mc.theWorld != null) {
        for (S38PacketPlayerListItem.AddPlayerData data : pkt.getEntries()) {
          if (data == null || data.getProfile() == null) continue;
          net.minecraft.entity.player.EntityPlayer entity =
              mc.theWorld.getPlayerEntityByUUID(data.getProfile().getId());
          if (entity == null || entity == mc.thePlayer) continue;
          String name = entity.getGameProfile().getName();

          if (STAFF_LIST.contains(name.toLowerCase())) {
            alert(
                NotificationType.SUCCESS,
                "Staff Left",
                name + " left the server",
                "&a[STAFF] &f" + name + " &cleft the server.");
          }
        }
      }
    }
  }

  private void alert(NotificationType type, String title, String desc, String chatMsg) {
    Miau.notificationManager
        .builder(type)
        .title(title)
        .description(desc)
        .duration(3000)
        .buildAndPublish();
    ChatUtil.display(chatMsg);
  }

  private void triggerAutoLeave(String name) {
    if (autoLeave.getValue() && mc.thePlayer != null) {
      ChatUtil.sendMessage("/hub");
      this.setEnabled(false);
    }
  }
}
