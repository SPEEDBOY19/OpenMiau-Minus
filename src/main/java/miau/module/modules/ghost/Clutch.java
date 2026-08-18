package miau.module.modules.ghost;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.movement.Sprint;
import miau.module.modules.player.Scaffold;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class Clutch extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty hurtTime = new BooleanProperty("hurt-time", true);
  public final BooleanProperty rotations = new BooleanProperty("rotations", false);
  public final FloatProperty rotationSpeed = new FloatProperty("rotation-speed", 100.0F, 1.0F, 180.0F, rotations::getValue);
  public final ModeProperty turnOffMode =
      new ModeProperty("disable-mode", 0, new String[] {"MOVE", "FORWARD", "NONE"});
  public final IntProperty enableDelay = new IntProperty("enable-delay", 0, 0, 100);
  public final IntProperty disableDelay = new IntProperty("disable-delay", 0, 0, 100);
  public final IntProperty sprintReEnable = new IntProperty("sprint-re-enable", 150, 0, 500);
  public final IntProperty minFallDist = new IntProperty("min-fall-distance", 0, 0, 10);

  private boolean falling = false;
  private boolean suppressed = false;
  private boolean needsSprint = false;
  private boolean fireballActive = false;
  private boolean fireballDelayActive = false;
  private int fireballDelayTicks = 0;
  private long airTimeStart = 0;
  private long landTimeStart = 0;
  private long sprintTimer = 0;
  private long lastClutchEndTime = 0;

  private static final int FALL_SCAN_DEPTH = 10;
  private static final long CHAIN_WINDOW_MS = 750;

  public Clutch() {
    super("Clutch", false);
  }

  @Override
  public void onEnabled() {
    falling = false;
    suppressed = false;
    needsSprint = false;
    fireballActive = false;
    fireballDelayActive = false;
    fireballDelayTicks = 0;
    airTimeStart = 0;
    landTimeStart = 0;
    sprintTimer = 0;
    lastClutchEndTime = 0;
  }

  @Override
  public void onDisabled() {
    if (falling) {
      disableScaffold();
    }
    falling = false;
    suppressed = false;
    needsSprint = false;
    airTimeStart = 0;
    landTimeStart = 0;
    sprintTimer = 0;
    lastClutchEndTime = 0;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!isEnabled()) return;
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.thePlayer.isSpectator()) {
      if (isScaffoldEnabled()) disableScaffold();
      return;
    }

    long now = System.currentTimeMillis();
    int dist = fallDistance();
    int minFall = minFallDist.getValue();
    long enableDly = enableDelay.getValue();
    long disableDly = disableDelay.getValue();
    long sprintDly = sprintReEnable.getValue();

    updateFireballState();
    handleTurnoff(now);

    if (!mc.thePlayer.onGround && mc.thePlayer.motionY < -0.01) {
      if (airTimeStart == 0) {
        airTimeStart = now;
      }

      boolean inChainWindow = lastClutchEndTime != 0 && now - lastClutchEndTime < CHAIN_WINDOW_MS;

      boolean shouldTrigger;
      if (inChainWindow) {
        shouldTrigger = true;
      } else if (minFall == 0) {
        shouldTrigger = dist == -1;
      } else {
        shouldTrigger = dist == -1 || dist >= minFall;
      }

      if (hurtTime.getValue()) {
        shouldTrigger = shouldTrigger && mc.thePlayer.hurtTime > 0;
      }

      if (shouldTrigger
          && !suppressed
          && !isScaffoldEnabled()
          && !mc.thePlayer.capabilities.isFlying
          && !fireballActive) {
        if (now - airTimeStart >= enableDly) {
          setScaffold(true);
          setSprint(false);
          falling = true;
          needsSprint = true;
          sprintTimer = 0;
          lastClutchEndTime = 0;
        }
      }
    } else if (mc.thePlayer.onGround) {
      airTimeStart = 0;
      suppressed = false;
      if (!falling) {
        landTimeStart = 0;
      }
    }

    if (falling && rotations.getValue()) {
      float[] smoothed = miau.util.player.RotationUtil.smooth(
          new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch},
          new float[] {mc.thePlayer.rotationYaw, 90.0F},
          rotationSpeed.getValue(),
          null,
          0);
      mc.thePlayer.rotationPitch = smoothed[1];
    }

    if (falling && (mc.thePlayer.onGround || (dist != -1 && dist < 1))) {
      if (landTimeStart == 0) {
        landTimeStart = now;
      }

      if (isScaffoldEnabled()) {
        if (now - landTimeStart >= disableDly) {
          stopClutch(now, false);
        }
      }
    }

    if (!falling && needsSprint && sprintTimer != 0) {
      if (now - sprintTimer >= sprintDly) {
        setSprint(true);
        needsSprint = false;
        sprintTimer = 0;
      }
    }
  }

  private void updateFireballState() {
    ItemStack held = mc.thePlayer.getHeldItem();
    if (held != null
        && held.getUnlocalizedName().contains("fire_charge")
        && mc.gameSettings.keyBindUseItem.isKeyDown()) {
      fireballActive = true;
      if (!fireballDelayActive) {
        fireballDelayActive = true;
        fireballDelayTicks = 20;
      }
    }

    if (fireballDelayActive && fireballDelayTicks > 0) {
      fireballDelayTicks--;
      if (fireballDelayTicks < 2) {
        fireballActive = false;
        fireballDelayActive = false;
      }
    }
  }

  private void handleTurnoff(long now) {
    if (!falling) return;
    int mode = turnOffMode.getValue();
    switch (mode) {
      case 0: // MOVE
        if (isMoving() || isAnyMovementKeyDown()) stopClutch(now, true);
        break;
      case 1: // FORWARD
        if (mc.gameSettings.keyBindForward.isKeyDown()) stopClutch(now, true);
        break;
      case 2: // NONE
        break;
    }
  }

  private void stopClutch(long now, boolean suppress) {
    disableScaffold();
    falling = false;
    suppressed = suppress;
    landTimeStart = 0;
    lastClutchEndTime = now;
    if (needsSprint) {
      sprintTimer = now;
    }
  }

  private boolean isScaffoldEnabled() {
    Module mod = Miau.moduleManager.modules.get(Scaffold.class);
    return mod != null && mod.isEnabled();
  }

  private void setScaffold(boolean enabled) {
    Module mod = Miau.moduleManager.modules.get(Scaffold.class);
    if (mod != null && mod.isEnabled() != enabled) {
      mod.toggle();
    }
  }

  private void disableScaffold() {
    Module mod = Miau.moduleManager.modules.get(Scaffold.class);
    if (mod != null && mod.isEnabled()) {
      mod.toggle();
    }
  }

  private void setSprint(boolean enabled) {
    Module mod = Miau.moduleManager.modules.get(Sprint.class);
    if (mod != null && mod.isEnabled() != enabled) {
      mod.toggle();
    }
  }

  private int fallDistance() {
    Vec3 pos = mc.thePlayer.getPositionVector();
    int startY = MathHelper.floor_double(pos.yCoord) - 1;
    if (startY < 0) return -1;

    int minY = Math.max(0, startY - FALL_SCAN_DEPTH);
    int px = MathHelper.floor_double(pos.xCoord);
    int pz = MathHelper.floor_double(pos.zCoord);

    for (int i = startY; i >= minY; i--) {
      Block block = mc.theWorld.getBlockState(new BlockPos(px, i, pz)).getBlock();
      if (block == Blocks.air) continue;
      String name = block.getUnlocalizedName();
      if (name.contains("sign") || name.contains("torch")) continue;
      return startY - i;
    }
    return -1;
  }

  private boolean isMoving() {
    return mc.thePlayer.movementInput.moveForward != 0.0f
        || mc.thePlayer.movementInput.moveStrafe != 0.0f;
  }

  private boolean isAnyMovementKeyDown() {
    return mc.gameSettings.keyBindForward.isKeyDown()
        || mc.gameSettings.keyBindBack.isKeyDown()
        || mc.gameSettings.keyBindLeft.isKeyDown()
        || mc.gameSettings.keyBindRight.isKeyDown()
        || mc.gameSettings.keyBindJump.isKeyDown()
        || mc.gameSettings.keyBindSneak.isKeyDown();
  }
}
