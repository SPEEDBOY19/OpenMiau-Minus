package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.PlayerUpdateEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.ChatUtil;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C0BPacketEntityAction;

public class SprintStatesSync extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty desyncTime = new ModeProperty("DesyncTime", 2, new String[] {"OnlyWhenSprinting", "OnlyWhenNotSprinting", "Both"});
  public final ModeProperty desyncUpdateStateTime = new ModeProperty("DesyncUpdateStateTime", 0, new String[] {"onPostSprintUpdate", "onUpdate", "onMotion"});
  public final ModeProperty desyncMode = new ModeProperty("DesyncMode", 2, new String[] {"VanillaSet", "NetworkPacket", "Both"});
  public final BooleanProperty useTag = new BooleanProperty("Tag", true);
  public final BooleanProperty debugMessage = new BooleanProperty("DebugMessage", false);
  private boolean serverSprintState = false;

  public SprintStatesSync() {
    super("SprintStatesSync", false);
  }

  private void apply(boolean sprinting, boolean vanillaSet, boolean sendPacket) {
    if (vanillaSet) {
      mc.thePlayer.setSprinting(sprinting);
    }
    if (sendPacket) {
      PacketUtil.sendPacket(
          new C0BPacketEntityAction(
              mc.thePlayer,
              sprinting
                  ? C0BPacketEntityAction.Action.START_SPRINTING
                  : C0BPacketEntityAction.Action.STOP_SPRINTING));
    }
    this.serverSprintState = sprinting;
  }

  private void handle() {
    if (mc.thePlayer.isSprinting() == this.serverSprintState) return;
    String desync = this.desyncTime.getModeString();
    String mode = this.desyncMode.getModeString();
    if (this.serverSprintState) {
      if (!desync.equals("OnlyWhenSprinting")) return;
      switch (mode) {
        case "VanillaSet":
          this.apply(true, true, false);
          break;
        case "NetworkPacket":
          this.apply(true, false, true);
          break;
        case "Both":
          this.apply(true, true, true);
          break;
        default:
          break;
      }
    } else {
      if (!desync.equals("OnlyWhenNotSprinting")) return;
      switch (mode) {
        case "VanillaSet":
          this.apply(false, true, false);
          break;
        case "NetworkPacket":
          this.apply(false, false, true);
          break;
        case "Both":
          this.apply(false, true, true);
          break;
        default:
          break;
      }
    }
    if (this.debugMessage.getValue()) {
      ChatUtil.display("&7Desync");
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (!this.desyncUpdateStateTime.getModeString().equals("onPostSprintUpdate")) return;
      this.handle();
    }
  }

  @EventTarget
  public void onUpdatePost(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.POST) {
      if (!this.desyncUpdateStateTime.getModeString().equals("onUpdate")) return;
      this.handle();
    }
  }

  @EventTarget
  public void onMotion(PlayerUpdateEvent event) {
    if (this.isEnabled()) {
      if (!this.desyncUpdateStateTime.getModeString().equals("onMotion")) return;
      this.handle();
    }
  }

  @Override
  public String[] getSuffix() {
    if (this.useTag.getValue()) {
      return new String[] {this.desyncMode.getModeString()};
    }
    return new String[0];
  }
}