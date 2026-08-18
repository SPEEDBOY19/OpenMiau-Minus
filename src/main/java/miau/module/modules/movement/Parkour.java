package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.MoveInputEvent;
import miau.module.Module;
import miau.util.player.MoveUtil;
import miau.util.player.SimulatedPlayer;
import net.minecraft.client.Minecraft;

public class Parkour extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public Parkour() {
    super("Parkour", false);
  }

  @EventTarget
  public void onMovementInput(MoveInputEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    SimulatedPlayer simPlayer = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);

    simPlayer.tick();

    if (MoveUtil.isMoving() && mc.thePlayer.onGround && !mc.thePlayer.isSneaking() && !mc.gameSettings.keyBindSneak.isKeyDown() && !simPlayer.onGround) {
      mc.thePlayer.movementInput.jump = true;
    }
  }
}