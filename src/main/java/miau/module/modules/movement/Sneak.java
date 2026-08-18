package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0BPacketEntityAction;

public class Sneak extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 3, new String[] {"Legit", "Vanilla", "Switch", "MineSecure"});
  public final BooleanProperty stopMove = new BooleanProperty("StopMove", false);

  private boolean sneaking = false;

  public Sneak() {
    super("Sneak", false);
  }

  @EventTarget
  public void onMotion(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    if (this.stopMove.getValue() && MoveUtil.isMoving()) {
      if (this.sneaking) {
        this.onDisabled();
      }
      return;
    }

    String mode = this.mode.getModeString().toLowerCase();
    if (mode.equals("legit")) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
    } else if (mode.equals("vanilla")) {
      if (this.sneaking) {
        return;
      }
      PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
    } else if (mode.equals("switch")) {
      if (event.getType() == EventType.PRE) {
        PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
        PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
      } else if (event.getType() == EventType.POST) {
        PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
        PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
      }
    } else if (mode.equals("minesecure")) {
      if (event.getType() == EventType.PRE) {
        return;
      }
      PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
    }
  }

  @EventTarget
  public void onWorld(LoadWorldEvent event) {
    this.sneaking = false;
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    EntityPlayerSP player = mc.thePlayer;

    String mode = this.mode.getModeString().toLowerCase();
    if (mode.equals("legit")) {
      if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
      }
    } else {
      PacketUtil.sendPacket(new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SNEAKING));
    }
    this.sneaking = false;
  }
}