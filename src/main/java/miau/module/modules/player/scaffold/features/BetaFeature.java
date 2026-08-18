package miau.module.modules.player.scaffold.features;

import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.util.client.KeyBindUtil;

public class BetaFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  public int betaAirTicks = 0;
  public int betaGroundTicks = 0;
  public int betaPlaceCooldown = 0;
  public float lastBetaSentYaw = Float.NaN;
  public float lastBetaSentPitch = Float.NaN;
  public long lastBetaPitchQuotient = 0L;
  public int betaPlaceTicks = 999;

  public BetaFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return java.util.Collections.emptyList();
  }

  public boolean isBetaMode() {
    int mode = scaffold.rotationHandler.rotationMode.getValue();
    return mode == 7 || mode == 8; // Cho phép hoạt động trên cả Beta (7) và Telly (8)
  }

  public boolean isBetaTellyMode() {
    int mode = scaffold.rotationHandler.rotationMode.getValue();
    return (mode == 8) || (isBetaMode()
        && (scaffold.keepYFeature.keepY.getValue() == 3
            || scaffold.keepYFeature.keepY.getValue() == 4)
        && (!scaffold.keepYFeature.tellyRightClick.getValue() || isRightClickHeld()));
  }

  private boolean isRightClickHeld() {
    return Scaffold.mc.gameSettings != null && Scaffold.mc.gameSettings.keyBindUseItem.isKeyDown();
  }

  public void quietBetaMovement() {
    if (!isBetaMode() || isBetaTellyMode() || Scaffold.mc.thePlayer == null) return;
    Scaffold.mc.thePlayer.setSprinting(false);
    if (Scaffold.mc.gameSettings != null) {
      KeyBindUtil.setKeyBindState(Scaffold.mc.gameSettings.keyBindSprint.getKeyCode(), false);
    }
  }

  public boolean canBetaPlaceNow() {
    if (!isBetaMode()) return true;
    if (Scaffold.mc.thePlayer == null || scaffold.placedThisTick || this.betaPlaceCooldown > 0)
      return false;
    if ((!isBetaTellyMode() && Scaffold.mc.thePlayer.isSprinting())
        || Scaffold.mc.thePlayer.isCollidedHorizontally
        || Scaffold.mc.thePlayer.hurtTime > 0) return false;
    if (Scaffold.mc.thePlayer.onGround)
      return Math.abs(Scaffold.mc.thePlayer.motionY) < 1.0E-4 && this.betaGroundTicks > 0;
    return this.betaAirTicks > 1;
  }

  @Override
  public void onUpdate(miau.event.impl.UpdateEvent event) {
    if (!isBetaMode() || Scaffold.mc.thePlayer == null) {
      this.betaAirTicks = 0;
      this.betaGroundTicks = 0;
      this.betaPlaceCooldown = 0;
      return;
    }
    if (Scaffold.mc.thePlayer.onGround) {
      this.betaGroundTicks++;
      this.betaAirTicks = 0;
    } else {
      this.betaAirTicks++;
      this.betaGroundTicks = 0;
    }
    if (this.betaPlaceCooldown > 0) this.betaPlaceCooldown--;
  }

  @Override
  public void onMoveInput(miau.event.impl.MoveInputEvent event) {
    quietBetaMovement();
  }

  @Override
  public void onLivingUpdate(miau.event.impl.LivingUpdateEvent event) {
    quietBetaMovement();
  }

  @Override
  public void onDisable() {
    this.betaAirTicks = 0;
    this.betaGroundTicks = 0;
    this.betaPlaceCooldown = 0;
    this.lastBetaSentYaw = Float.NaN;
    this.lastBetaSentPitch = Float.NaN;
    this.lastBetaPitchQuotient = 0L;
    this.betaPlaceTicks = 999;
  }
}