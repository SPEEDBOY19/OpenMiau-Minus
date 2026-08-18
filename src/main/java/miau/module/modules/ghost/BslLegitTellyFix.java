package miau.module.modules.ghost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.LeftClickMouseEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.PlayerUpdateEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.RightClickMouseEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorRenderManager;
import miau.module.Module;
import miau.module.modules.movement.SafeWalk;
import miau.property.properties.BooleanProperty;
import miau.util.client.KeyBindUtil;
import miau.util.player.RotationUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

// port of BSLegitTellyFix.java by NeverBeBanned (Fixed by Tirum)
public class BslLegitTellyFix extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty autoSwap = new BooleanProperty("Auto swap", true);
  public final BooleanProperty disableSafeWalk = new BooleanProperty("Disable SafeWalk", true);
  public final BooleanProperty showActivationHitbox =
      new BooleanProperty("Show activation hitbox", false);

  private static final Map<String, Object> bridge = new HashMap<>();

  private boolean armed = false;
  private boolean running = false;
  private long activatePromptAt = 0L;
  private long promptBrokeAt = 0L;
  private float promptAlpha = 0.0f;
  private long promptFadeLastAt = 0L;
  private int promptFadeRgb = 0xFF5555;
  private int[] hitboxLastPos = null;
  private int hitboxLastFace = -1;
  private boolean activationMovementHeld = false;
  private boolean antiSwayTapUsed = false;
  private final HashSet<String> cancelledGhostBlocks = new HashSet<>();
  private boolean tellyAutoPlaceWindow = false;
  private boolean autoPlaceDebugActive = false;
  private boolean safeWalkStateCaptured = false;
  private boolean safeWalkWasEnabled = false;

  private int setupTick = 0;
  private int cyclePhase = 19;
  private float baseYaw = 0.0f;
  private int travelX = 0;
  private int travelZ = 0;
  private double antiSwayLane = 0.0;
  private float antiSwayYawOffset = 0.0f;
  private int bridgeLaneBlock = 0;
  private int bridgeStartProgress = 0;
  private int[] latestStraightPlacedPos = null;
  private boolean firstTellyPlacementPending = false;
  private boolean adaptiveAimValid = false;
  private float adaptiveAimYaw = 0.0f;
  private float adaptiveAimPitch = 0.0f;
  private long adaptiveAimUpdatedAt = 0L;
  private long takeoverDetectionAt = 0L;
  private boolean takeoverCameraValid = false;
  private float takeoverCameraYaw = 0.0f;
  private float takeoverCameraPitch = 0.0f;
  private float takeoverAccumulated = 0.0f;
  private long takeoverLastFrameAt = 0L;
  private long freezeLastTickAt = 0L;
  private boolean ignoreForwardUntilRelease = false;
  private boolean ignoreBackUntilRelease = false;
  private boolean ignoreLeftUntilRelease = false;
  private boolean ignoreRightUntilRelease = false;
  private boolean ignoreJumpUntilRelease = false;
  private boolean ignoreSneakUntilRelease = false;
  private boolean ignoreSprintUntilRelease = false;

  private boolean rotationActive = false;
  private long rotationStartedAt = 0L;
  private long rotationDuration = 50L;
  private float rotationStartYaw = 0.0f;
  private float rotationStartPitch = 0.0f;
  private float rotationTargetYaw = 0.0f;
  private float rotationTargetPitch = 0.0f;
  private float scriptedRotationYaw = 0.0f;
  private float scriptedRotationPitch = 0.0f;

  private final double SENSITIVITY_QUANTUM = 0.03404715;
  private final int[] YAW_NUDGE_PATTERN = {0, 1, -1, 2, -2};
  private int rotationStepCounter = 0;
  private final double ACTIVATION_ACROSS_MIN = 0.38;
  private final double ACTIVATION_ACROSS_MAX = 0.65;
  private final double ACTIVATION_HEIGHT_MIN = 0.25;
  private final double ACTIVATION_HEIGHT_MAX = 0.75;
  private final float ACTIVATION_YAW_TOLERANCE = 2.0f;

  private final float[] yawCurve =
      new float[] {
        91.68f, 98.88f, 78.94f, 37.45f, 1.61f, -21.69f, -33.98f,
        -35.80f, -34.64f, -33.85f, -33.06f, -31.55f, -29.26f, -26.65f,
        -24.19f, -21.07f, -18.84f, -17.06f, -8.87f, 2.61f, 41.94f
      };

  private final float[] pitchCurve =
      new float[] {
        64.31f, 59.95f, 60.57f, 61.46f, 60.64f, 58.89f, 56.91f,
        56.63f, 58.65f, 61.63f, 64.20f, 66.74f, 68.69f, 70.64f,
        73.01f, 75.37f, 77.46f, 78.56f, 78.90f, 77.22f, 72.25f
      };

  private final float[] forwardCurve =
      new float[] {
        1.0f, 1.0f, 0.0f, 0.0f, -1.0f, -1.0f, -1.0f,
        -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f,
        -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f
      };

  private final float[] strafeCurve =
      new float[] {
        -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, -1.0f, -1.0f, -1.0f, -1.0f
      };

  private final double[] FACE_HIT_OFFSETS = {0.5, 0.25, 0.75, 0.15, 0.85};
  private final double[] EXTENDED_FACE_HIT_OFFSETS =
      {0.5, 0.25, 0.75, 0.15, 0.85, 0.35, 0.65, 0.05, 0.95};
  private final int[] ALLOWED_PLACE_FACES = {2, 3, 4, 5, 1};
  private final String[] REPLACEABLE_BLOCKS = {
    "air", "water", "flowing_water", "lava", "flowing_lava", "fire", "tallgrass", "deadbush",
    "snow_layer", "double_plant", "vine"
  };
  private final String[] EXPERIMENTAL_REPLACEABLE_BLOCKS = {
    "sapling", "yellow_flower", "red_flower", "brown_mushroom", "red_mushroom",
    "wheat", "carrots", "potatoes", "nether_wart", "reeds"
  };
  private final String[] UNPLACEABLE_EXACT = {
    "snow_layer", "web", "sapling", "daylight_detector", "beacon", "banner",
    "end_portal_frame", "end_portal", "lever", "stone_button", "wooden_button",
    "skull", "cactus", "double_plant", "waterlily", "carpet", "tripwire_hook",
    "tallgrass", "yellow_flower", "red_flower", "flower_pot", "sign", "ladder",
    "torch", "redstone_torch", "unlit_redstone_torch", "gravel", "clay", "sand",
    "soul_sand", "chest", "trapped_chest", "ender_chest", "furnace", "lit_furnace",
    "jukebox", "enchanting_table", "dropper", "dispenser", "hopper", "anvil",
    "noteblock", "crafting_table", "mob_spawner", "brewing_stand", "bed"
  };
  private final String[] UNPLACEABLE_CONTAINS = {
    "stairs", "slab", "fence", "pane", "rail", "door",
    "torch", "pumpkin", "flower", "sapling", "banner", "button",
    "skull", "web", "carpet", "cactus", "sign", "mushroom"
  };
  private final String[] INTERACTABLE_TYPES = {
    "BlockTrapDoor", "BlockDoor", "BlockContainer", "BlockJukebox", "BlockFenceGate",
    "BlockChest", "BlockEnderChest", "BlockEnchantmentTable", "BlockBrewingStand",
    "BlockBed", "BlockDropper", "BlockDispenser", "BlockHopper", "BlockAnvil",
    "BlockNote", "BlockWorkbench", "BlockFurnace", "BlockBeacon", "BlockMobSpawner",
    "BlockDaylightDetector", "BlockCommandBlock", "BlockStandingSign", "BlockWallSign", "BlockSkull"
  };

  private int currentClientTick = Integer.MIN_VALUE;
  private int placementEvaluationTick = Integer.MIN_VALUE;
  private int lastPlacementAttemptTick = Integer.MIN_VALUE;
  private int lastSuccessfulPlaceTick = Integer.MIN_VALUE;
  private int forceSuppressTick = Integer.MIN_VALUE;
  private long totalC08Counter = 0L;
  private long c08CounterAtTickBoundary = 0L;
  private boolean hasLastSentServerPos = false;
  private double lastSentServerPosX, lastSentServerPosY, lastSentServerPosZ;
  private Object[] cachedCandidate = null;
  private int cachedCandidateTick = Integer.MIN_VALUE;
  private float cachedCandidateYaw = Float.NaN;
  private float cachedCandidatePitch = Float.NaN;
  private boolean candidateResolvedThisTick = false;
  private int[] lastPlacedPos = null;
  private int[] lastSupportPos = null;
  private int lastSupportFace = -1;
  private List<int[]> cachedBelowTargets = null;
  private int cachedBelowTargetsTick = Integer.MIN_VALUE;
  private final Map<String, Integer> rejectedTargets = new HashMap<>();
  private int forcedModeCheck = 0;
  private boolean useSuppressed = false;
  private boolean silentPitchActive = false;
  private float silentPitch = 0f;
  private boolean placingViaModule = false;
  private boolean manualC08InWindow = false;

  private final Map<Integer, Boolean> lastKeyDown = new HashMap<>();
  private boolean lastRmbDown = false;

  public BslLegitTellyFix() {
    super("BslLegitTellyFix", false);
  }

  @Override
  public void onEnabled() {
    autoPlaceOnEnable();
    armAutomation();
  }

  @Override
  public void onDisabled() {
    stopAutomation(false);
    autoPlaceOnDisable();
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    if (mc.thePlayer != null) stopAutomation(false);
    autoPlaceOnWorldJoin();
  }

  @EventTarget
  public void onPreUpdate(PlayerUpdateEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    enforceSafeWalkDisabledForRun();
    pollKeyTransitions();
    pollMouseTransitions();

    if (running) {
      setKeyPressed("attack", false);
      applySmoothedRotation();
    }

    if (armed && !running) updateActivationPrompt();

    if (!running) return;

    long freezeNow = now();
    if (freezeLastTickAt != 0L && freezeNow - freezeLastTickAt > 300L) {
      stopAutomation(true);
      return;
    }
    freezeLastTickAt = freezeNow;

    Entity player = mc.thePlayer;
    if (player.isDead || player.fallDistance > 7.0f) {
      stopAutomation(true);
      return;
    }
    handleAutoSwap(player);
    if (!isHoldingBlock(player)) {
      stopAutomation(true);
      return;
    }
    if (firstTellyPlacementPending) updateAdaptivePlacementAim(player);

    autoPlaceOnPreUpdate();
    if (firstTellyPlacementPending) updateAdaptivePlacementAim(player);
  }

  private float activationPitch() {
    return 75.0f;
  }

  private void handleAutoSwap(Entity player) {
    if (!autoSwap.getValue()) return;
    if (!(player instanceof EntityPlayer)) return;

    int threshold = 5;
    ItemStack held = ((EntityPlayer) player).getHeldItem();
    int heldCount = held != null && isUsableBlockStack(held) ? held.stackSize : 0;
    if (heldCount > threshold) return;

    int bestSlot = -1;
    int bestSize = heldCount;
    for (int slot = 0; slot <= 8; slot++) {
      if (slot == ((EntityPlayer) player).inventory.currentItem) continue;
      ItemStack stack = ((EntityPlayer) player).inventory.getStackInSlot(slot);
      if (!isUsableBlockStack(stack)) continue;
      if (stack.stackSize > bestSize) {
        bestSize = stack.stackSize;
        bestSlot = slot;
      }
    }

    if (bestSlot != -1) ((EntityPlayer) player).inventory.currentItem = bestSlot;
  }

  private boolean activationPromptReady() {
    return activatePromptAt != 0L && now() - activatePromptAt >= 1000L;
  }

  private boolean activationSuppressUse() {
    return activatePromptAt != 0L && now() - activatePromptAt >= 850L;
  }

  private void updateActivationPrompt() {
    Entity player = mc.thePlayer;
    if (player == null || mc.currentScreen != null) {
      clearActivationPrompt();
      return;
    }

    setActivationMovementHold(activationPromptReady() && Mouse.isButtonDown(1));

    boolean lookingDown = player.rotationPitch >= activationPitch();
    boolean atEdge = lookingDown && isLookingAtEdge(player);

    if (mc.thePlayer.isSneaking() && atEdge) {
      if (activatePromptAt == 0L) activatePromptAt = now();
      promptBrokeAt = 0L;
      if (activationSuppressUse()) setKeyPressed("use", false);
      if (activationPromptReady() && Mouse.isButtonDown(1)) {
        disableSafeWalkForRun();
        enforceSafeWalkDisabledForRun();
      } else if (safeWalkStateCaptured) {
        restoreSafeWalkState();
      }
      return;
    }

    if (activatePromptAt == 0L) return;

    if (!activationPromptReady()) {
      clearActivationPrompt();
      return;
    }

    if (promptBrokeAt == 0L) {
      rememberActivationPromptColor();
      promptBrokeAt = now();
    }
    setKeyPressed("use", false);

    if (!mc.thePlayer.isSneaking()
        && Mouse.isButtonDown(1)
        && isActivationYawAligned(player.rotationYaw)) {
      rememberActivationPromptColor();
      activatePromptAt = 0L;
      promptBrokeAt = 0L;
      beginAutomation();
      if (!running) setKeyPressed("use", false);
      return;
    }

    if (now() - promptBrokeAt > 300L) {
      clearActivationPrompt();
    }
  }

  private void clearActivationPrompt() {
    rememberActivationPromptColor();
    if (activationSuppressUse()) {
      setKeyPressed("use", false);
    }
    activatePromptAt = 0L;
    promptBrokeAt = 0L;
    setActivationMovementHold(false);
    if (!running) restoreSafeWalkState();
  }

  private void rememberActivationPromptColor() {
    if (activatePromptAt != 0L) {
      promptFadeRgb = activationPromptReady() ? 0x55FF55 : 0xFF5555;
    }
  }

  private int[] travelDirectionFromYaw(float yaw) {
    double radians = Math.toRadians(yaw);
    double rawX = Math.sin(radians) - Math.cos(radians);
    double rawZ = -Math.cos(radians) - Math.sin(radians);
    if (Math.abs(rawX) >= Math.abs(rawZ)) return new int[] {rawX >= 0.0 ? 1 : -1, 0};
    return new int[] {0, rawZ >= 0.0 ? 1 : -1};
  }

  private boolean isLookingAtEdge(Entity player) {
    if (!isActivationYawAligned(player.rotationYaw)) return false;
    Object[] hit = raycastBlock(4.5, player.rotationYaw, player.rotationPitch);
    if (hit == null || hit.length < 3 || hit[0] == null || hit[1] == null || hit[2] == null)
      return false;

    int face = faceFromName((String) hit[2]);
    if (face < 2) return false;
    if (!isInActivationFaceCenter(face, (Vec3) hit[1])) return false;

    int[] travel = travelDirectionFromYaw(player.rotationYaw);
    int travelFace = travel[0] > 0 ? 5 : travel[0] < 0 ? 4 : travel[1] > 0 ? 3 : 2;
    if (face != travelFace) return false;

    int[] pos = posFromVec((Vec3) hit[0]);
    if (!isPlayerOnActivationBlock(player, pos)) return false;
    int aheadX = pos[0] + travel[0];
    int aheadZ = pos[2] + travel[1];
    if (!isReplaceableName(blockNameAt(aheadX, pos[1] + 1, aheadZ), false)) return false;

    Vec3 playerPos = playerPosition(player);
    double lipDistance;
    if (face == 5) lipDistance = (pos[0] + 1) - playerPos.xCoord;
    else if (face == 4) lipDistance = playerPos.xCoord - pos[0];
    else if (face == 3) lipDistance = (pos[2] + 1) - playerPos.zCoord;
    else lipDistance = playerPos.zCoord - pos[2];
    return lipDistance <= 0.65;
  }

  private boolean isActivationYawAligned(float yaw) {
    float nearestDiagonal = Math.round((yaw - 45.0f) / 90.0f) * 90.0f + 45.0f;
    return Math.abs(tellyWrapAngle(yaw - nearestDiagonal)) <= ACTIVATION_YAW_TOLERANCE;
  }

  private boolean isPlayerOnActivationBlock(Entity player, int[] pos) {
    if (pos == null) return false;
    Vec3 playerPos = playerPosition(player);
    if (pos[1] != floor(playerPos.yCoord - 0.01)) return false;
    double centerX = pos[0] + 0.5;
    double centerZ = pos[2] + 0.5;
    return Math.abs(playerPos.xCoord - centerX) <= 0.85
        && Math.abs(playerPos.zCoord - centerZ) <= 0.85;
  }

  private boolean isInActivationFaceCenter(int face, Vec3 localHit) {
    if (localHit == null) return false;
    double acrossFace = (face == 4 || face == 5) ? localHit.zCoord : localHit.xCoord;
    if (face == 3 || face == 4) acrossFace = 1.0 - acrossFace;
    return acrossFace >= ACTIVATION_ACROSS_MIN
        && acrossFace <= ACTIVATION_ACROSS_MAX
        && localHit.yCoord >= ACTIVATION_HEIGHT_MIN
        && localHit.yCoord <= ACTIVATION_HEIGHT_MAX;
  }

  @EventTarget
  public void onRenderWorld(Render3DEvent event) {
    if (!showActivationHitbox.getValue()) return;
    if (!armed || running) return;
    if (promptAlpha < 0.05f) return;

    if (activatePromptAt != 0L) {
      Object[] hit = raycastBlock(4.5, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
      if (hit != null && hit.length >= 3 && hit[0] != null && hit[2] != null) {
        int face = faceFromName((String) hit[2]);
        if (face >= 2) {
          hitboxLastPos = posFromVec((Vec3) hit[0]);
          hitboxLastFace = face;
        }
      }
    }

    if (hitboxLastPos == null || hitboxLastFace < 2) return;
    drawActivationFaceRegion(hitboxLastPos, hitboxLastFace);
  }

  private void drawActivationFaceRegion(int[] pos, int face) {
    Vec3 cam = renderPosition();
    if (cam == null) return;

    double yMin = pos[1] + ACTIVATION_HEIGHT_MIN;
    double yMax = pos[1] + ACTIVATION_HEIGHT_MAX;
    double x1, z1, x2, z2;

    if (face == 5) {
      x1 = pos[0] + 1.005;
      x2 = x1;
      z1 = pos[2] + ACTIVATION_ACROSS_MIN;
      z2 = pos[2] + ACTIVATION_ACROSS_MAX;
    } else if (face == 4) {
      x1 = pos[0] - 0.005;
      x2 = x1;
      z1 = pos[2] + (1.0 - ACTIVATION_ACROSS_MAX);
      z2 = pos[2] + (1.0 - ACTIVATION_ACROSS_MIN);
    } else if (face == 3) {
      z1 = pos[2] + 1.005;
      z2 = z1;
      x1 = pos[0] + (1.0 - ACTIVATION_ACROSS_MAX);
      x2 = pos[0] + (1.0 - ACTIVATION_ACROSS_MIN);
    } else {
      z1 = pos[2] - 0.005;
      z2 = z1;
      x1 = pos[0] + ACTIVATION_ACROSS_MIN;
      x2 = pos[0] + ACTIVATION_ACROSS_MAX;
    }

    int r = (promptFadeRgb >> 16) & 0xFF;
    int g = (promptFadeRgb >> 8) & 0xFF;
    int b = promptFadeRgb & 0xFF;
    int fillAlpha = (int) (60.0f * promptAlpha);
    int lineAlpha = (int) (220.0f * promptAlpha);
    if (fillAlpha < 4) fillAlpha = 4;
    if (lineAlpha < 16) lineAlpha = 16;

    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_ALPHA_TEST);
    GL11.glDisable(GL11.GL_CULL_FACE);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glDepthMask(false);
    GL11.glTranslated(-cam.xCoord, -cam.yCoord, -cam.zCoord);

    GL11.glColor4f(r / 255.0f, g / 255.0f, b / 255.0f, fillAlpha / 255.0f);
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex3d(x1, yMin, z1);
    GL11.glVertex3d(x2, yMin, z2);
    GL11.glVertex3d(x2, yMax, z2);
    GL11.glVertex3d(x1, yMax, z1);
    GL11.glEnd();

    GL11.glLineWidth(2.0f);
    GL11.glColor4f(r / 255.0f, g / 255.0f, b / 255.0f, lineAlpha / 255.0f);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    GL11.glVertex3d(x1, yMin, z1);
    GL11.glVertex3d(x2, yMin, z2);
    GL11.glVertex3d(x2, yMax, z2);
    GL11.glVertex3d(x1, yMax, z1);
    GL11.glEnd();
    GL11.glLineWidth(1.0f);

    GL11.glDepthMask(true);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GL11.glEnable(GL11.GL_CULL_FACE);
    GL11.glEnable(GL11.GL_ALPHA_TEST);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    GL11.glPopMatrix();
  }

  @EventTarget
  public void onRenderTick(Render2DEvent event) {
    updateActivatePromptFade();
    drawActivatePrompt();
    if (!running) return;
    if (detectManualCameraTakeover()) return;
    applySmoothedRotation();
    autoPlaceOnRenderTick();
  }

  private void drawActivatePrompt() {
    if (promptAlpha < 0.05f) return;

    int[] display = getDisplaySize();
    if (display == null || display.length < 2) return;

    String text = "Activate?";
    int alpha = (int) (promptAlpha * 255.0f);
    if (alpha < 16) alpha = 16;
    int color = (alpha << 24) | promptFadeRgb;
    float x = display[0] / 2.0f - mc.fontRendererObj.getStringWidth(text) / 2.0f;
    float y = display[1] / 2.0f + 10.0f;
    mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
  }

  private void updateActivatePromptFade() {
    boolean show = armed && !running && activatePromptAt != 0L;
    if (show) rememberActivationPromptColor();

    long now = now();
    long elapsed = promptFadeLastAt == 0L ? 0L : Math.min(100L, now - promptFadeLastAt);
    promptFadeLastAt = now;
    float step = elapsed / 200.0f;
    promptAlpha += show ? step : -step;
    if (promptAlpha < 0.0f) promptAlpha = 0.0f;
    if (promptAlpha > 1.0f) promptAlpha = 1.0f;
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (running) {
      setKeyPressed("attack", false);
      event.setCancelled(true);
    }
    // SỬA: Cho phép click tay bình thường khi chưa Telly
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (running) {
      setKeyPressed("use", tellyAutoPlaceWindow);
      event.setCancelled(true);
      return;
    }
    if (armed && activationSuppressUse() && Mouse.isButtonDown(1)) {
      event.setCancelled(true);
    }
    // SỬA: Cho phép click tay / đặt block bình thường khi chưa Telly
  }

  private void pollMouseTransitions() {
    boolean down = Mouse.isButtonDown(1);
    if (lastRmbDown && !down) {
      if (armed && !running) setActivationMovementHold(false);
    }
    lastRmbDown = down;
  }

  private void pollKeyTransitions() {
    int[] keys = {
      getKeyCode("drop"), getKeyCode("forward"), getKeyCode("back"),
      getKeyCode("left"), getKeyCode("right"), getKeyCode("jump"), getKeyCode("sneak"),
      getKeyCode("sprint")
    };
    for (int code : keys) {
      boolean down = KeyBindUtil.isKeyDown(code);
      Boolean was = lastKeyDown.get(code);
      if (was != null && !was && down) {
        onKeyPressed(code);
      } else if (was != null && was && !down) {
        onKeyReleased(code);
      }
      lastKeyDown.put(code, down);
    }
  }

  private void onKeyPressed(int keyCode) {
    if (isDropProtected() && keyCode == getKeyCode("drop")) {
      setKeyPressed("drop", false);
      return;
    }
    if (!running) return;
    if (keyCode == getKeyCode("sneak")) {
      suppressSneakInput();
    }
    // SỬA: Đã gỡ bỏ stopAutomation(true) để tránh việc lỡ chạm phím di chuyển làm ngắt Telly giữa chừng
  }

  private void onKeyReleased(int keyCode) {
    if (!running
        && activationMovementHeld
        && (keyCode == getKeyCode("back") || keyCode == getKeyCode("right"))) {
      setKeyPressed("back", true);
      setKeyPressed("right", true);
      return;
    }
    if (!running) return;
    if (!isManualMovementKey(keyCode)) return;
    clearInitialMovementHold(keyCode);
  }

  private void setActivationMovementHold(boolean hold) {
    if (hold) {
      activationMovementHeld = true;
      setKeyPressed("back", true);
      setKeyPressed("right", true);
      return;
    }
    if (!activationMovementHeld) return;
    activationMovementHeld = false;
    setKeyPressed("back", KeyBindUtil.isKeyDown(getKeyCode("back")));
    setKeyPressed("right", KeyBindUtil.isKeyDown(getKeyCode("right")));
  }

  private boolean isScriptHeldKey(int keyCode) {
    if (keyCode == getKeyCode("forward")) return isPressed("forward");
    if (keyCode == getKeyCode("back")) return isPressed("back");
    if (keyCode == getKeyCode("left")) return isPressed("left");
    if (keyCode == getKeyCode("right")) return isPressed("right");
    if (keyCode == getKeyCode("jump")) return isPressed("jump");
    if (keyCode == getKeyCode("sprint")) return isPressed("sprint");
    return false;
  }

  private boolean isManualMovementKey(int keyCode) {
    return keyCode == getKeyCode("forward")
        || keyCode == getKeyCode("back")
        || keyCode == getKeyCode("left")
        || keyCode == getKeyCode("right")
        || keyCode == getKeyCode("jump")
        || keyCode == getKeyCode("sneak")
        || keyCode == getKeyCode("sprint");
  }

  private void captureInitialMovementHolds() {
    ignoreForwardUntilRelease = KeyBindUtil.isKeyDown(getKeyCode("forward"));
    ignoreBackUntilRelease = KeyBindUtil.isKeyDown(getKeyCode("back"));
    ignoreLeftUntilRelease = KeyBindUtil.isKeyDown(getKeyCode("left"));
    ignoreRightUntilRelease = KeyBindUtil.isKeyDown(getKeyCode("right"));
    ignoreJumpUntilRelease = KeyBindUtil.isKeyDown(getKeyCode("jump"));
    ignoreSneakUntilRelease = KeyBindUtil.isKeyDown(getKeyCode("sneak"));
    ignoreSprintUntilRelease = KeyBindUtil.isKeyDown(getKeyCode("sprint"));
  }

  private boolean isInitialMovementHold(int keyCode) {
    if (keyCode == getKeyCode("forward")) return ignoreForwardUntilRelease;
    if (keyCode == getKeyCode("back")) return ignoreBackUntilRelease;
    if (keyCode == getKeyCode("left")) return ignoreLeftUntilRelease;
    if (keyCode == getKeyCode("right")) return ignoreRightUntilRelease;
    if (keyCode == getKeyCode("jump")) return ignoreJumpUntilRelease;
    if (keyCode == getKeyCode("sneak")) return ignoreSneakUntilRelease;
    if (keyCode == getKeyCode("sprint")) return ignoreSprintUntilRelease;
    return false;
  }

  private void clearInitialMovementHold(int keyCode) {
    if (keyCode == getKeyCode("forward")) ignoreForwardUntilRelease = false;
    if (keyCode == getKeyCode("back")) ignoreBackUntilRelease = false;
    if (keyCode == getKeyCode("left")) ignoreLeftUntilRelease = false;
    if (keyCode == getKeyCode("right")) ignoreRightUntilRelease = false;
    if (keyCode == getKeyCode("jump")) ignoreJumpUntilRelease = false;
    if (keyCode == getKeyCode("sneak")) ignoreSneakUntilRelease = false;
    if (keyCode == getKeyCode("sprint")) ignoreSprintUntilRelease = false;
  }

  private void clearInitialMovementHolds() {
    ignoreForwardUntilRelease = false;
    ignoreBackUntilRelease = false;
    ignoreLeftUntilRelease = false;
    ignoreRightUntilRelease = false;
    ignoreJumpUntilRelease = false;
    ignoreSneakUntilRelease = false;
    ignoreSprintUntilRelease = false;
  }

  private boolean detectManualCameraTakeover() {
    // SỬA: Tắt ngắt tự động do nhích chuột quá tay để không bị rớt block giữa chừng
    if (!running || setupTick >= 0 || now() < takeoverDetectionAt) return false;
    return false;
  }

  @EventTarget
  public void onPacketSent(PacketEvent event) {
    if (event.getType() != EventType.SEND) return;
    joueur((net.minecraft.network.Packet<?>) event.getPacket());
  }

  private void joueur(net.minecraft.network.Packet<?> packet) {
    if (packet instanceof C07PacketPlayerDigging) {
      C07PacketPlayerDigging digging = (C07PacketPlayerDigging) packet;
      String status = digging.getStatus() == null ? "" : digging.getStatus().toString().toUpperCase();
      if (isDropProtected() && status.contains("DROP")) return;
    }
    if (!running) {
      if (packet instanceof C07PacketPlayerDigging
              && isDropProtected()
              && String.valueOf(((C07PacketPlayerDigging) packet).getStatus()).toUpperCase()
                  .contains("DROP")) {
        return;
      }
      return;
    }
    if (packet instanceof C02PacketUseEntity) {
      C02PacketUseEntity interaction = (C02PacketUseEntity) packet;
      if (interaction.getAction() == C02PacketUseEntity.Action.ATTACK) return;
    }
    if (packet instanceof C07PacketPlayerDigging) {
      C07PacketPlayerDigging digging = (C07PacketPlayerDigging) packet;
      String status = digging.getStatus() == null ? "" : digging.getStatus().toString().toUpperCase();
      if (status.contains("DESTROY")) return;
    }
    if (packet instanceof C0BPacketEntityAction) {
      C0BPacketEntityAction action = (C0BPacketEntityAction) packet;
      if (action.getAction() == C0BPacketEntityAction.Action.START_SNEAKING) return;
    }

    int[] placedTarget = null;
    if (packet instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) packet;
      int direction = placement.getPlacedBlockDirection();
      if (direction != 255) {
        placedTarget = offsetPos(posFromPos(placement.getPosition()), direction);
        if (!isStraightTellyTarget(placedTarget)) {
          cancelledGhostBlocks.add(posKey(placedTarget));
          return;
        }
      }
    }

    boolean allowed = autoPlaceOnPacketSent(packet);
    if (allowed && placedTarget != null) {
      cancelledGhostBlocks.remove(posKey(placedTarget));
      latestStraightPlacedPos = new int[] {placedTarget[0], placedTarget[1], placedTarget[2]};
      if (firstTellyPlacementPending && setupTick < 0) {
        firstTellyPlacementPending = false;
        adaptiveAimValid = false;
        adaptiveAimUpdatedAt = 0L;
      }
    }
  }

  @EventTarget
  public void onPacketReceived(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE) return;
    net.minecraft.network.Packet<?> packet = event.getPacket();
    if (running && packet instanceof S08PacketPlayerPosLook) {
      stopAutomation(true);
      event.setCancelled(true);
      return;
    }
    if (packet instanceof S23PacketBlockChange && !cancelledGhostBlocks.isEmpty()) {
      S23PacketBlockChange change = (S23PacketBlockChange) packet;
      if (change.getBlockPosition() != null) {
        cancelledGhostBlocks.remove(posKeyFromPos(change.getBlockPosition()));
      }
    }
  }

  private boolean isActivationInProgress() {
    return armed && !running && activatePromptAt != 0L;
  }

  private boolean isDropProtected() {
    return running || isActivationInProgress();
  }

  @EventTarget
  public void onPostPlayerInput(MoveInputEvent event) {
    if (!running) return;
    suppressSneakInput();
    enforceSafeWalkDisabledForRun();

    if (setupTick >= 0) {
      if (setupTick < 12) {
        boolean setupJump = setupTick >= 6;
        applyMovement(-1.0f, -1.0f, setupJump, false);
        applyUse(true);

        if (setupTick == 11) {
          setRotationTarget(baseYaw + yawCurve[19], pitchCurve[19], 50L);
        } else {
          setRotationTarget(baseYaw, 74.52f, 50L);
        }
        setupTick++;
        return;
      }

      setupTick = -1;
      takeoverDetectionAt = now() + 125L;
      takeoverCameraValid = mc.thePlayer != null;
      takeoverAccumulated = 0.0f;
      takeoverLastFrameAt = now();
      if (mc.thePlayer != null) {
        takeoverCameraYaw = mc.thePlayer.rotationYaw;
        takeoverCameraPitch = mc.thePlayer.rotationPitch;
      }
      captureInitialMovementHolds();
      cyclePhase = 19;
      firstTellyPlacementPending = true;
      adaptiveAimValid = false;
      clearCachedCandidate();
      updateAdaptivePlacementAim(mc.thePlayer);
    }

    int phase = cyclePhase;
    float strafe = strafeCurve[phase];

    boolean sprinting = phase == 0 || phase == 1;
    boolean jumping = phase >= 1 && phase <= 19;
    boolean use = phase >= 7;

    applyMovement(forwardCurve[phase], strafe, jumping, sprinting);
    applyUse(use);

    int nextPhase = (phase + 1) % yawCurve.length;
    setRotationTarget(baseYaw + yawCurve[nextPhase], pitchCurve[nextPhase], 50L);
    cyclePhase = nextPhase;
  }

  @EventTarget
  public void onPreMotion(UpdateEvent event) {
    if (!running || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null) return;
    float yaw = mc.thePlayer.rotationYaw;
    float pitch = mc.thePlayer.rotationPitch;
    autoPlaceOnPreMotion();
    if (silentPitchActive && !manualC08InWindow) {
      pitch = sanitizePitch(silentPitch, pitch);
    }
    event.setRotation(yaw, pitch, 10);
    RotationUtil.serverYaw = yaw;
    RotationUtil.serverPitch = pitch;
  }

  private void autoPlaceOnPreMotion() {}

  @EventTarget
  public void onPostMotion(UpdateEvent event) {
    if (event.getType() != EventType.POST) return;
    if (!running) return;
    c08CounterAtTickBoundary = totalC08Counter;
    manualC08InWindow = false;
  }

  private void armAutomation() {
    armed = true;
    running = false;
    activatePromptAt = 0L;
    promptBrokeAt = 0L;
    setupTick = 0;
    cyclePhase = 19;
    rotationActive = false;
    activationMovementHeld = false;
    printStatus("&eArmed. Sneak looking down, wait for green, hold rmb and release sneak");
  }

  private void beginAutomation() {
    Entity player = mc.thePlayer;
    if (player == null || !isHoldingBlock(player)) {
      printStatus("&cHold blocks before starting");
      return;
    }
    if (!isActivationYawAligned(player.rotationYaw)) return;

    disableSafeWalkForRun();
    baseYaw = player.rotationYaw;
    calculateTravelDirection(baseYaw);
    antiSwayLane = travelX != 0 ? playerPosition(player).zCoord : playerPosition(player).xCoord;
    antiSwayYawOffset = 0.0f;
    antiSwayTapUsed = false;
    cancelledGhostBlocks.clear();
    initializeStraightBridgeLane(player);
    firstTellyPlacementPending = false;
    adaptiveAimValid = false;
    adaptiveAimUpdatedAt = 0L;
    setupTick = 0;
    cyclePhase = 19;
    armed = false;
    running = true;
    freezeLastTickAt = now();
    activationMovementHeld = false;
    tellyAutoPlaceWindow = true;
    scriptedRotationYaw = player.rotationYaw;
    scriptedRotationPitch = player.rotationPitch;
    takeoverDetectionAt = 0L;
    takeoverCameraValid = false;
    clearInitialMovementHolds();
    resetControllerState();
    setKeyPressed("attack", false);
    applyMovement(-1.0f, -1.0f, false, false);
    setRotationTarget(baseYaw, 74.52f, 50L);
    applyUse(true);
    printStatus("&aStarted");
  }

  private void stopAutomation(boolean turnOffButton) {
    armed = false;
    running = false;
    setupTick = 0;
    cyclePhase = 19;
    rotationActive = false;
    activationMovementHeld = false;
    tellyAutoPlaceWindow = false;
    autoPlaceDebugActive = false;
    antiSwayYawOffset = 0.0f;
    antiSwayTapUsed = false;
    firstTellyPlacementPending = false;
    latestStraightPlacedPos = null;
    adaptiveAimValid = false;
    adaptiveAimUpdatedAt = 0L;
    scriptedRotationYaw = 0.0f;
    scriptedRotationPitch = 0.0f;
    takeoverDetectionAt = 0L;
    takeoverCameraValid = false;
    takeoverCameraYaw = 0.0f;
    takeoverCameraPitch = 0.0f;
    takeoverAccumulated = 0.0f;
    takeoverLastFrameAt = 0L;

    try {
      cancelledGhostBlocks.clear();
      clearInitialMovementHolds();
      resetControllerState();
      mc.thePlayer.movementInput.moveForward = 0.0f;
      mc.thePlayer.movementInput.moveStrafe = 0.0f;
      mc.thePlayer.movementInput.jump = false;
      mc.thePlayer.setSprinting(false);
      releaseMovementKeys();
      restorePhysicalUse();
      setKeyPressed("attack", Mouse.isButtonDown(0));
    } catch (Exception ignored) {
    }

    restoreSafeWalkState();

    freezeLastTickAt = 0L;
    armed = true;
    activatePromptAt = 0L;
    promptBrokeAt = 0L;
    if (turnOffButton) {
      printStatus("&eStopped. Sneak looking down to arm again");
    }
  }

  private void disableSafeWalkForRun() {
    if (safeWalkStateCaptured) {
      enforceSafeWalkDisabledForRun();
      return;
    }
    if (!disableSafeWalk.getValue()) return;

    try {
      safeWalkWasEnabled = isSafeWalkEnabled();
      safeWalkStateCaptured = true;
      if (safeWalkWasEnabled) setSafeWalkEnabled(false);
    } catch (Exception ignored) {
      safeWalkStateCaptured = false;
    }
  }

  private void enforceSafeWalkDisabledForRun() {
    if (!safeWalkStateCaptured) return;
    try {
      if (isSafeWalkEnabled()) setSafeWalkEnabled(false);
    } catch (Exception ignored) {
    }
  }

  private void restoreSafeWalkState() {
    if (!safeWalkStateCaptured) return;

    boolean restoreEnabled = safeWalkWasEnabled;
    safeWalkStateCaptured = false;
    try {
      boolean currentlyEnabled = isSafeWalkEnabled();
      if (restoreEnabled && !currentlyEnabled) setSafeWalkEnabled(true);
      if (!restoreEnabled && currentlyEnabled) setSafeWalkEnabled(false);
    } catch (Exception ignored) {
    }
  }

  private SafeWalk safeWalk() {
    return (SafeWalk) Miau.moduleManager.modules.get(SafeWalk.class);
  }

  private boolean isSafeWalkEnabled() {
    SafeWalk s = safeWalk();
    return s != null && s.isEnabled();
  }

  private void setSafeWalkEnabled(boolean on) {
    SafeWalk s = safeWalk();
    if (s != null) s.setEnabled(on);
  }

  private void printStatus(String message) {
    miau.util.client.ChatUtil.display("§bTelly §7| " + message);
  }

  private void setRotationTarget(float targetYaw, float targetPitch, long duration) {
    Entity player = mc.thePlayer;
    if (player == null) return;

    applySmoothedRotation();
    rotationStartYaw = player.rotationYaw;
    rotationStartPitch = player.rotationPitch;
    float correctedTargetYaw = targetYaw;
    boolean adaptivePlacementTarget =
        running
            && tellyAutoPlaceWindow
            && firstTellyPlacementPending
            && adaptiveAimValid
            && now() - adaptiveAimUpdatedAt <= 125L;
    if (adaptivePlacementTarget) {
      correctedTargetYaw = adaptiveAimYaw;
      targetPitch = adaptiveAimPitch;
    } else if (running) {
      correctedTargetYaw += antiSwayYawOffset;
    }

    rotationStepCounter++;
    correctedTargetYaw +=
        (float) (SENSITIVITY_QUANTUM * YAW_NUDGE_PATTERN[rotationStepCounter % 5]);

    rotationTargetYaw = rotationStartYaw + tellyWrapAngle(correctedTargetYaw - rotationStartYaw);
    rotationTargetPitch = clamp(targetPitch, -90.0f, 90.0f);
    rotationStartedAt = now();
    rotationDuration = Math.max(1L, duration);
    rotationActive = true;
  }

  private void applySmoothedRotation() {
    if (!rotationActive) return;
    Entity player = mc.thePlayer;
    if (player == null) return;

    double progress = (double) (now() - rotationStartedAt) / (double) rotationDuration;
    if (progress < 0.0) progress = 0.0;
    if (progress > 1.0) progress = 1.0;

    float desiredYaw = rotationStartYaw + (rotationTargetYaw - rotationStartYaw) * (float) progress;
    float desiredPitch =
        rotationStartPitch + (rotationTargetPitch - rotationStartPitch) * (float) progress;
    float quantizedYaw = quantizeFrom(rotationStartYaw, desiredYaw);
    float quantizedPitch = quantizeFrom(rotationStartPitch, desiredPitch);

    scriptedRotationYaw = quantizedYaw;
    scriptedRotationPitch = clamp(quantizedPitch, -90.0f, 90.0f);
    player.rotationYaw = scriptedRotationYaw;
    player.rotationPitch = scriptedRotationPitch;
    if (progress >= 1.0) rotationActive = false;
  }

  private float quantizeFrom(float origin, float value) {
    double steps = Math.round((value - origin) / SENSITIVITY_QUANTUM);
    return (float) (origin + steps * SENSITIVITY_QUANTUM);
  }

  private void applyMovement(float forward, float strafe, boolean jumping, boolean sprinting) {
    float controlledForward = forward;
    boolean controlledSprint = sprinting;

    float correctedStrafe = strafe;
    boolean antiSway = running;
    if (antiSway) correctedStrafe = applyAntiSwayCorrection(controlledForward, strafe);
    else antiSwayYawOffset = 0.0f;

    setKeyPressed("forward", controlledForward > 0.03f);
    setKeyPressed("back", controlledForward < -0.03f);
    setKeyPressed("left", correctedStrafe > 0.5f);
    setKeyPressed("right", correctedStrafe < -0.5f);
    setKeyPressed("jump", jumping);
    setKeyPressed("sprint", controlledSprint);
    mc.thePlayer.movementInput.moveForward = controlledForward;
    mc.thePlayer.movementInput.moveStrafe = correctedStrafe;
    mc.thePlayer.movementInput.jump = jumping;
    mc.thePlayer.movementInput.sneak = false;
    mc.thePlayer.setSprinting(controlledSprint);
  }

  private void suppressSneakInput() {
    setKeyPressed("sneak", false);
    mc.thePlayer.movementInput.sneak = false;
  }

  private void calculateTravelDirection(float yaw) {
    double radians = Math.toRadians(yaw);
    double rawX = Math.sin(radians) - Math.cos(radians);
    double rawZ = -Math.cos(radians) - Math.sin(radians);

    if (Math.abs(rawX) >= Math.abs(rawZ)) {
      travelX = rawX >= 0.0 ? 1 : -1;
      travelZ = 0;
    } else {
      travelX = 0;
      travelZ = rawZ >= 0.0 ? 1 : -1;
    }
  }

  private void initializeStraightBridgeLane(Entity player) {
    Vec3 position = playerPosition(player);
    int startX = floor(position.xCoord);
    int startY = floor(position.yCoord) - 1;
    int startZ = floor(position.zCoord);
    bridgeLaneBlock = travelX != 0 ? startZ : startX;
    bridgeStartProgress = startX * travelX + startZ * travelZ;

    Object[] hit = raycastBlock(4.5, player.rotationYaw, player.rotationPitch);
    if (hit != null && hit.length > 0 && hit[0] instanceof Vec3) {
      int[] hitPos = posFromVec((Vec3) hit[0]);
      int hitLane = travelX != 0 ? hitPos[2] : hitPos[0];
      int hitProgress = straightProgress(hitPos);
      if (hitLane == bridgeLaneBlock
          && Math.abs(hitPos[0] - startX) <= 2
          && Math.abs(hitPos[2] - startZ) <= 2
          && hitProgress < bridgeStartProgress) {
        bridgeStartProgress = hitProgress;
      }
    }

    latestStraightPlacedPos = new int[] {startX, startY, startZ};
  }

  private int straightProgress(int[] position) {
    if (position == null) return Integer.MIN_VALUE;
    return position[0] * travelX + position[2] * travelZ;
  }

  private boolean isStraightTellyTarget(int[] position) {
    if (!running || position == null) return true;
    int lane = travelX != 0 ? position[2] : position[0];
    if (lane != bridgeLaneBlock) return false;
    return straightProgress(position) >= bridgeStartProgress;
  }

  private void updateAdaptivePlacementAim(Entity player) {
    if (!firstTellyPlacementPending) return;
    Object[] candidate = cachedCandidate;
    if (candidate != null) {
      int[] target = candidatePlacedPos(candidate);
      Vec3 hitVec = candidateHitVec(candidate);
      if (isStraightTellyTarget(target) && hitVec != null) {
        setAdaptiveAimToPoint(player, hitVec);
        return;
      }
    }

    int[] support = latestStraightPlacedPos != null ? latestStraightPlacedPos : lastPlacedPos;
    if (support == null || !isStraightTellyTarget(support)) return;
    int face = travelX > 0 ? 5 : travelX < 0 ? 4 : travelZ > 0 ? 3 : 2;
    int[] nextTarget = offsetPos(support, face);
    if (!isStraightTellyTarget(nextTarget)
        || !isReplaceable(nextTarget[0], nextTarget[1], nextTarget[2])) return;
    Vec3 fallbackHit = getSupportFaceHitVec(support, face, 0.5, 0.5);
    setAdaptiveAimToPoint(player, fallbackHit);
  }

  private void setAdaptiveAimToPoint(Entity player, Vec3 point) {
    if (player == null || point == null) return;
    Vec3 position = playerPosition(player);
    double eyeX = position.xCoord;
    double eyeY = position.yCoord + player.getEyeHeight();
    double eyeZ = position.zCoord;
    double dx = point.xCoord - eyeX;
    double dy = point.yCoord - eyeY;
    double dz = point.zCoord - eyeZ;
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    if (horizontal < 1.0E-5 && Math.abs(dy) < 1.0E-5) return;

    adaptiveAimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    adaptiveAimPitch =
        clamp((float) (-Math.toDegrees(Math.atan2(dy, horizontal))), -89.0f, 89.0f);
    adaptiveAimUpdatedAt = now();
    adaptiveAimValid = true;
  }

  private float applyAntiSwayCorrection(float forward, float recordedStrafe) {
    Entity player = mc.thePlayer;
    if (player == null) return recordedStrafe;

    Vec3 position = playerPosition(player);
    Vec3 motion = playerMotion();
    double lanePosition = travelX != 0 ? position.zCoord : position.xCoord;
    double laneVelocity = motion == null ? 0.0 : (travelX != 0 ? motion.zCoord : motion.xCoord);
    double error = antiSwayLane - lanePosition;

    if (Math.abs(error) < 0.015 && Math.abs(laneVelocity) < 0.008) {
      antiSwayTapUsed = false;
      antiSwayYawOffset *= 0.65f;
      if (Math.abs(antiSwayYawOffset) < 0.03f) antiSwayYawOffset = 0.0f;
      return recordedStrafe;
    }

    double desiredLaneVelocity = error * 0.42 - laneVelocity * 0.78;
    if (desiredLaneVelocity > 0.16) desiredLaneVelocity = 0.16;
    if (desiredLaneVelocity < -0.16) desiredLaneVelocity = -0.16;
    double velocityCorrection = desiredLaneVelocity - laneVelocity;

    double radians = Math.toRadians(player.rotationYaw);
    double sin = Math.sin(radians);
    double cos = Math.cos(radians);
    double yawLaneDerivative =
        travelX != 0
            ? -forward * sin + recordedStrafe * cos
            : -forward * cos - recordedStrafe * sin;
    double desiredYawOffset = 0.0;
    if (Math.abs(yawLaneDerivative) >= 0.12) {
      desiredYawOffset = Math.toDegrees(velocityCorrection * 0.55 / yawLaneDerivative);
    }
    if (desiredYawOffset > 2.25) desiredYawOffset = 2.25;
    if (desiredYawOffset < -2.25) desiredYawOffset = -2.25;
    antiSwayYawOffset = antiSwayYawOffset * 0.60f + (float) desiredYawOffset * 0.40f;

    double strafeLaneAxis = travelX != 0 ? sin : cos;
    boolean tapHelps = Math.abs(strafeLaneAxis) >= 0.20 && velocityCorrection * strafeLaneAxis > 0.0;
    if (tapHelps
        && !antiSwayTapUsed
        && Math.abs(velocityCorrection) >= 0.03
        && recordedStrafe < 0.5f) {
      antiSwayTapUsed = true;
      return recordedStrafe + 1.0f;
    }

    return recordedStrafe;
  }

  private void applyUse(boolean pressed) {
    if (pressed && !autoPlaceDebugActive) {
      printStatus("&aAutoPlace activated");
    }
    autoPlaceDebugActive = pressed;
    tellyAutoPlaceWindow = pressed;
    setKeyPressed("use", pressed);
  }

  private void restorePhysicalUse() {
    tellyAutoPlaceWindow = false;
    autoPlaceDebugActive = false;
    setKeyPressed("use", Mouse.isButtonDown(1));
  }

  private void releaseMovementKeys() {
    restorePhysicalKey("forward");
    restorePhysicalKey("back");
    restorePhysicalKey("left");
    restorePhysicalKey("right");
    restorePhysicalKey("jump");
    restorePhysicalKey("sneak");
    restorePhysicalKey("sprint");
  }

  private void restorePhysicalKey(String key) {
    int code = getKeyCode(key);
    setKeyPressed(key, code >= 0 && KeyBindUtil.isKeyDown(code));
  }

  private float tellyWrapAngle(float angle) {
    while (angle <= -180.0f) angle += 360.0f;
    while (angle > 180.0f) angle -= 360.0f;
    return angle;
  }

  private float clamp(float value, float minimum, float maximum) {
    if (value < minimum) return minimum;
    if (value > maximum) return maximum;
    return value;
  }

  // ---- auto place engine ----

  private void autoPlaceOnEnable() {
    setKeyPressed("attack", false);
    resetControllerState();
  }

  private void autoPlaceOnDisable() {
    resetControllerState();
    restoreUseToPhysicalState();
    setKeyPressed("attack", false);
    bridge.remove("AutoPlacePlacing");
    releaseExperimentalPlacementClaim();
  }

  private void resetControllerState() {
    currentClientTick = Integer.MIN_VALUE;
    placementEvaluationTick = Integer.MIN_VALUE;
    lastPlacementAttemptTick = Integer.MIN_VALUE;
    lastSuccessfulPlaceTick = Integer.MIN_VALUE;
    forceSuppressTick = Integer.MIN_VALUE;
    totalC08Counter = 0L;
    c08CounterAtTickBoundary = 0L;
    hasLastSentServerPos = false;
    clearCachedCandidate();
    lastPlacedPos = null;
    lastSupportPos = null;
    lastSupportFace = -1;
    cachedBelowTargets = null;
    cachedBelowTargetsTick = Integer.MIN_VALUE;
    rejectedTargets.clear();
    forcedModeCheck = 0;
    useSuppressed = false;
    silentPitchActive = false;
    placingViaModule = false;
    manualC08InWindow = false;
  }

  private void autoPlaceOnWorldJoin() {
    resetControllerState();
  }

  private void autoPlaceOnPreUpdate() {
    Entity player = mc.thePlayer;
    if (player == null) return;

    syncPlacementTick(player);

    if (placementEvaluationTick != currentClientTick) {
      placementEvaluationTick = currentClientTick;
      processAutoPlaceTick(player);
    }
  }

  private void syncPlacementTick(Entity player) {
    int tick = placementTick(player);
    if (tick == currentClientTick) return;
    currentClientTick = tick;
    candidateResolvedThisTick = false;
    silentPitchActive = false;
  }

  private boolean useExtendedSearch() {
    return true;
  }

  private void autoPlaceOnPostMotion() {
    c08CounterAtTickBoundary = totalC08Counter;
    manualC08InWindow = false;
  }

  private void autoPlaceOnRenderTick() {
    Entity player = mc.thePlayer;
    if (player == null) return;
    if (!isAutoPlaceActiveWindow(player)) return;

    ItemStack heldStack = heldItem(player);
    if (!isUsableBlockStack(heldStack)) return;

    float basePitch = sanitizePitch(player.rotationPitch, player.rotationPitch);
    Object[] candidate =
        resolveCandidateWithOffCursorSilentPitch(player, player.rotationYaw, basePitch, heldStack);
    if (candidate != null) {
      silentPitch = sanitizePitch(candidatePitch(candidate), basePitch);
      silentPitchActive = true;
      suppressUse();
    }
  }

  private boolean autoPlaceOnMouse(int button, boolean state) {
    if (!state || (button != 0 && button != 1)) return true;

    if (button == 1 && shouldCancelAutoPlaceUseItem()) {
      suppressUse();
      return false;
    }
    if (!shouldSuppressManualClicksThisTick()) return true;
    setKeyPressed("attack", false);
    return false;
  }

  private boolean shouldSuppressManualClicksThisTick() {
    if (!isInGameContext()) return false;
    return lastSuccessfulPlaceTick == currentClientTick || forceSuppressTick == currentClientTick;
  }

  private boolean shouldCancelAutoPlaceUseItem() {
    if (!isInGameContext()) return false;
    if (shouldSuppressManualClicksThisTick()) return true;
    return useSuppressed && silentPitchActive;
  }

  private void suppressUse() {
    setKeyPressed("use", false);
    useSuppressed = true;
  }

  private void restoreUseToPhysicalState() {
    setKeyPressed("use", running ? tellyAutoPlaceWindow : Mouse.isButtonDown(1));
    useSuppressed = false;
  }

  private boolean isInGameContext() {
    return mc.thePlayer != null && mc.currentScreen == null;
  }

  private boolean areAutoPlaceConditionsMet(Entity player) {
    if (!tellyAutoPlaceWindow) return false;
    return isUsableBlockStack(heldItem(player));
  }

  private boolean isAutoPlaceActiveWindow(Entity player) {
    if (!isInGameContext()) return false;
    if (bridge.containsKey("ScaffoldRunning")) return false;
    if (!areAutoPlaceConditionsMet(player)) return false;
    return isUsableBlockStack(heldItem(player));
  }

  private boolean isUsableBlockStack(ItemStack stack) {
    if (stack == null || !isBlockStack(stack) || stack.getItem() == null
        || stack.stackSize <= 0) return false;
    String name = stackName(stack).toLowerCase();
    for (String bad : UNPLACEABLE_EXACT) {
      if (name.equals(bad)) return false;
    }
    for (String bad : UNPLACEABLE_CONTAINS) {
      if (name.contains(bad)) return false;
    }
    return true;
  }

  private boolean isBlockBelowPlayerReplaceable(Entity player) {
    Vec3 pos = playerPosition(player);
    return isReplaceable(floor(pos.xCoord), floor(pos.yCoord) - 1, floor(pos.zCoord));
  }

  private boolean placedInCurrentWindow() {
    return totalC08Counter > c08CounterAtTickBoundary;
  }

  private boolean claimExperimentalPlacementTick() {
    Object tickValue = bridge.get("PlacementArbiterTick");
    Object ownerValue = bridge.get("PlacementArbiterOwner");
    if (tickValue instanceof Number
        && ((Number) tickValue).intValue() == currentClientTick
        && ownerValue != null
        && !"BslLegitTellyFix".equals(String.valueOf(ownerValue))) {
      return false;
    }
    bridge.put("PlacementArbiterTick", currentClientTick);
    bridge.put("PlacementArbiterOwner", "BslLegitTellyFix");
    return true;
  }

  private void releaseExperimentalPlacementClaim() {
    Object ownerValue = bridge.get("PlacementArbiterOwner");
    if (ownerValue == null || !"BslLegitTellyFix".equals(String.valueOf(ownerValue))) return;
    bridge.remove("PlacementArbiterTick");
    bridge.remove("PlacementArbiterOwner");
  }

  private void processAutoPlaceTick(Entity player) {
    pruneRejectedTargets();

    if (lastPlacedPos != null
        && !isSupportAvailable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2])) {
      lastPlacedPos = null;
      lastSupportPos = null;
      lastSupportFace = -1;
    }

    if (!isAutoPlaceActiveWindow(player)) {
      clearCachedCandidate();
      bridge.remove("AutoPlacePlacing");
      if (useSuppressed) restoreUseToPhysicalState();
      return;
    }

    ItemStack heldStack = heldItem(player);
    if (!isUsableBlockStack(heldStack)) {
      clearCachedCandidate();
      if (useSuppressed) restoreUseToPhysicalState();
      return;
    }

    if (!isBlockBelowPlayerReplaceable(player)) {
      clearCachedCandidate();
      if (useSuppressed) restoreUseToPhysicalState();
      return;
    }

    float yaw = player.rotationYaw;
    float basePitch = sanitizePitch(player.rotationPitch, player.rotationPitch);
    Object[] candidate = resolveCandidateWithOffCursorSilentPitch(player, yaw, basePitch, heldStack);
    if (candidate != null) {
      silentPitch = sanitizePitch(candidatePitch(candidate), basePitch);
      silentPitchActive = true;
      suppressUse();
    } else if (useSuppressed
        && !placedInCurrentWindow()
        && lastPlacementAttemptTick != currentClientTick) {
      restoreUseToPhysicalState();
    }

    if (placedInCurrentWindow() || lastPlacementAttemptTick == currentClientTick) {
      suppressUse();
      return;
    }

    if (candidate == null) {
      clearCachedCandidate();
      return;
    }

    if (!claimExperimentalPlacementTick()) {
      clearCachedCandidate();
      return;
    }

    bridge.put("AutoPlacePlacing", Boolean.TRUE);
    lastPlacementAttemptTick = currentClientTick;

    if (attemptPlacement(player, candidate, heldStack)) return;

    if (placedInCurrentWindow()) return;

    float retryYaw = player.rotationYaw;
    float retryPitch = player.rotationPitch;
    clearCachedCandidate();
    Object[] retryCandidate =
        findBelowPlacement(
            player, retryYaw, retryPitch, heldStack, now() + (useExtendedSearch() ? 4L : 2L));
    cacheCandidate(retryCandidate, retryYaw, retryPitch);
    if (retryCandidate != null) {
      silentPitch = sanitizePitch(candidatePitch(retryCandidate), retryPitch);
      silentPitchActive = true;
      if (attemptPlacement(player, retryCandidate, heldStack)) return;
    }
    releaseExperimentalPlacementClaim();
  }

  private boolean attemptPlacement(Entity player, Object[] candidate, ItemStack heldStack) {
    if (candidate == null) return false;
    int[] placedPos = candidatePlacedPos(candidate);
    int[] supportPos = candidateSupportPos(candidate);
    int face = candidateFace(candidate);
    if (placedPos == null || supportPos == null || face <= 0) return false;
    if (!isStraightTellyTarget(placedPos)) return false;
    if (!isBlockBelowPlayerReplaceable(player)) return false;
    if (!isUsableBlockStack(heldItem(player))) return false;
    if (placedInCurrentWindow()) return false;

    float placementPitch = sanitizePitch(candidatePitch(candidate), player.rotationPitch);
    Object[] prePlaceHit =
        resolveVerifiedHit(player.rotationYaw, placementPitch, supportPos, face, placedPos);
    if (prePlaceHit == null) return false;

    if (cancelledGhostBlocks.contains(posKey(supportPos))) return false;
    if (!isReplaceable(placedPos[0], placedPos[1], placedPos[2])) return false;
    if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return false;
    if (doesPlacementIntersectPlayer(player, placedPos)) return false;

    long counterBefore = totalC08Counter;
    Vec3 hitAbs = (Vec3) prePlaceHit[2];
    placingViaModule = true;
    boolean placed = placeBlock(supportPos[0], supportPos[1], supportPos[2], faceName(face), hitAbs);
    placingViaModule = false;
    boolean packetSent = totalC08Counter > counterBefore;

    if (!placed && !packetSent) return false;
    if (!packetSent) {
      markRejectedTarget(placedPos);
      return false;
    }

    lastPlacedPos = placedPos;
    lastSupportPos = supportPos;
    lastSupportFace = face;
    lastSuccessfulPlaceTick = currentClientTick;
    forceSuppressTick = currentClientTick;
    mc.thePlayer.swingItem();
    return true;
  }

  private Object[] resolveVerifiedHit(
      float yaw, float pitch, int[] expectedSupport, int expectedFace, int[] expectedPlaced) {
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] tracedSupport = (int[]) traced[0];
    int tracedFace = (Integer) traced[1];
    if (!posEquals(tracedSupport, expectedSupport) || tracedFace != expectedFace) return null;
    int[] tracedPlaced = offsetPos(tracedSupport, tracedFace);
    if (!posEquals(tracedPlaced, expectedPlaced)) return null;
    return traced;
  }

  private Object[] resolveCandidateWithOffCursorSilentPitch(
      Entity player, float yaw, float basePitch, ItemStack heldStack) {
    float safeBasePitch = sanitizePitch(basePitch, player.rotationPitch);
    Object[] previousCandidate = cachedCandidate;
    Object[] baseCandidate = resolveCandidateForCurrentTick(player, yaw, safeBasePitch, heldStack);
    if (baseCandidate == null) {
      if (previousCandidate != null) {
        float previousBlockPitch =
            getBlockDerivedSilentPitch(player, previousCandidate, safeBasePitch);
        Object[] recovered =
            resolveCandidateForCurrentTick(player, yaw, previousBlockPitch, heldStack);
        if (recovered != null) return recovered;
        cacheCandidate(previousCandidate, yaw, safeBasePitch);
        return previousCandidate;
      }
      return null;
    }
    if (isPlacementLookAligned(
        yaw,
        safeBasePitch,
        candidateSupportPos(baseCandidate),
        candidateFace(baseCandidate),
        candidatePlacedPos(baseCandidate))) {
      return baseCandidate;
    }
    float blockPitch = getBlockDerivedSilentPitch(player, baseCandidate, safeBasePitch);
    if (isPlacementLookAligned(
        yaw,
        blockPitch,
        candidateSupportPos(baseCandidate),
        candidateFace(baseCandidate),
        candidatePlacedPos(baseCandidate))) {
      return new Object[] {
        blockPitch,
        candidateSupportPos(baseCandidate),
        candidateFace(baseCandidate),
        candidateHitVec(baseCandidate),
        candidatePlacedPos(baseCandidate)
      };
    }
    Object[] corrected = resolveCandidateForCurrentTick(player, yaw, blockPitch, heldStack);
    if (corrected != null
        && posEquals(candidatePlacedPos(baseCandidate), candidatePlacedPos(corrected))) {
      return corrected;
    }
    cacheCandidate(baseCandidate, yaw, safeBasePitch);
    return baseCandidate;
  }

  private Object[] resolveCandidateForCurrentTick(
      Entity player, float yaw, float pitch, ItemStack heldStack) {
    float safePitch = sanitizePitch(pitch, player.rotationPitch);
    if (hasCachedCandidateForCurrentTick(yaw, safePitch)) return cachedCandidate;
    Object[] candidate =
        findBelowPlacement(player, yaw, safePitch, heldStack, now() + (useExtendedSearch() ? 8L : 4L));
    cacheCandidate(candidate, yaw, safePitch);
    return candidate;
  }

  private float getBlockDerivedSilentPitch(Entity player, Object[] candidate, float fallbackPitch) {
    if (candidate == null) return sanitizePitch(fallbackPitch, fallbackPitch);
    Vec3 hitVec = candidateHitVec(candidate);
    if (hitVec != null) {
      Float derived = computePitchToHitVec(player, hitVec);
      if (derived != null) return sanitizePitch(derived, fallbackPitch);
    }
    return sanitizePitch(candidatePitch(candidate), fallbackPitch);
  }

  private void cacheCandidate(Object[] candidate, float yaw, float pitch) {
    cachedCandidate = candidate;
    cachedCandidateTick = currentClientTick;
    cachedCandidateYaw = yaw;
    cachedCandidatePitch = pitch;
    candidateResolvedThisTick = candidate != null;
  }

  private boolean hasCachedCandidateForCurrentTick(float yaw, float pitch) {
    if (cachedCandidateTick != currentClientTick
        || !candidateResolvedThisTick
        || cachedCandidate == null) return false;
    if (Float.isNaN(cachedCandidateYaw) || Float.isNaN(cachedCandidatePitch)) return false;
    return Math.abs(wrapAngle(yaw - cachedCandidateYaw)) <= 0.75f
        && Math.abs(pitch - cachedCandidatePitch) <= 0.75f;
  }

  private void clearCachedCandidate() {
    cachedCandidate = null;
    cachedCandidateTick = Integer.MIN_VALUE;
    cachedCandidateYaw = Float.NaN;
    cachedCandidatePitch = Float.NaN;
    candidateResolvedThisTick = false;
  }

  private Object[] findBelowPlacement(
      Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
    if (now() >= deadlineMs) return null;

    Object[] cursorRayCandidate = findDirectCursorRayPlacement(player, yaw, currentPitch, heldStack);
    if (cursorRayCandidate != null) return cursorRayCandidate;

    int currentY = getCurrentBelowTargetY(player);
    int strictY = getStrictBelowTargetY(player);
    int previousY = getPreviousBelowTargetY(player);
    int[] feetPos = getFeetBelowTargetAtY(player, currentY);

    List<int[]> targets = new ArrayList<>();
    addBelowTarget(player, targets, feetPos);
    addBelowTarget(player, targets, offsetPos(feetPos, facingFromYaw(yaw)));

    for (int dy = 0; dy <= 2; dy++) {
      int targetY = dy == 0 ? currentY : dy == 1 ? strictY : previousY;
      if (targetY == Integer.MIN_VALUE
          || (dy == 1 && targetY == currentY)
          || (dy == 2 && (targetY == currentY || targetY == strictY))) continue;
      addBelowTarget(player, targets, new int[] {feetPos[0], targetY, feetPos[2]});
    }

    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        addBelowTarget(player, targets, new int[] {feetPos[0] + dx, currentY, feetPos[2] + dz});
        if (strictY != currentY)
          addBelowTarget(player, targets, new int[] {feetPos[0] + dx, strictY, feetPos[2] + dz});
        if (previousY != currentY && previousY != strictY)
          addBelowTarget(
              player, targets, new int[] {feetPos[0] + dx, previousY, feetPos[2] + dz});
      }
    }

    if (!player.onGround) {
      addBelowTarget(player, targets, getMotionBelowTargetAtY(player, currentY, 1.0));
      if (previousY != currentY && previousY != strictY) {
        addBelowTarget(player, targets, getMotionBelowTargetAtY(player, previousY, 1.0));
      }
    }

    Object[] bestCandidate = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (int[] targetPos : targets) {
      if (now() >= deadlineMs) return null;
      if (!isPlacementTargetAvailable(player, targetPos)) continue;
      Object[] candidate =
          findPitchPlacementForTarget(
              player, yaw, currentPitch, targetPos, heldStack, null, -1, deadlineMs, false, true);
      if (candidate == null) continue;
      double score =
          scorePlacementCandidate(
              player,
              currentPitch,
              candidatePitch(candidate),
              candidateFace(candidate),
              0.5,
              0.5);
      if (score < bestScore) {
        bestScore = score;
        bestCandidate = candidate;
      }
    }
    return bestCandidate;
  }

  private Object[] findDirectCursorRayPlacement(
      Entity player, float yaw, float pitch, ItemStack heldStack) {
    if (!isUsableBlockStack(heldStack)) return null;
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] supportPos = (int[]) traced[0];
    int face = (Integer) traced[1];
    if (face == 0) return null;
    int[] targetPos = offsetPos(supportPos, face);
    if (!isPlacementTargetAvailable(player, targetPos)) return null;
    if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return null;
    if (shouldRejectStraightSideSwitch(player, targetPos, face)) return null;
    float tracedPitch = clampFloat(pitch, -89.0f, 89.0f);
    return new Object[] {tracedPitch, supportPos, face, (Vec3) traced[2], targetPos};
  }

  private boolean isCursorDirectedAtBlock(float yaw, float pitch) {
    return rayCast(yaw, pitch) != null;
  }

  private boolean isStraightCenterBelowAir(Entity player) {
    Vec3 pos = playerPosition(player);
    return isReplaceableName(
        blockNameAt(floor(pos.xCoord), getCurrentBelowTargetY(player), floor(pos.zCoord)), true);
  }

  private boolean isStraightPreviousTickCenterOnGroundSupport(Entity player) {
    Vec3 last = playerPositionLast(player);
    return !isReplaceableName(
        blockNameAt(floor(last.xCoord), floor(last.yCoord) - 1, floor(last.zCoord)), true);
  }

  private boolean isNearStraightSupportEdge(Entity player) {
    if (lastSupportPos == null || lastSupportFace < 2) return false;
    Vec3 pos = playerPosition(player);
    double localX = pos.xCoord - lastSupportPos[0];
    double localZ = pos.zCoord - lastSupportPos[2];
    if (isPastStraightSupportEdgeThreshold(lastSupportFace, localX, localZ)) return true;
    Vec3 motion = playerMotion();
    if (motion.xCoord * motion.xCoord + motion.zCoord * motion.zCoord < 1.0E-4) return false;
    if (!isMovingTowardStraightSupportEdge(lastSupportFace, motion.xCoord, motion.zCoord))
      return false;
    return isPastStraightSupportEdgeThreshold(
        lastSupportFace, localX + motion.xCoord * 1.45, localZ + motion.zCoord * 1.45);
  }

  private boolean isPastStraightSupportEdgeThreshold(int supportFace, double localX, double localZ) {
    if (supportFace == 5) return localX >= 0.52;
    if (supportFace == 4) return localX <= 0.48;
    if (supportFace == 3) return localZ >= 0.52;
    if (supportFace == 2) return localZ <= 0.48;
    return false;
  }

  private boolean isMovingTowardStraightSupportEdge(int supportFace, double motionX, double motionZ) {
    if (supportFace == 5) return motionX > 0.0;
    if (supportFace == 4) return motionX < 0.0;
    if (supportFace == 3) return motionZ > 0.0;
    if (supportFace == 2) return motionZ < 0.0;
    return false;
  }

  private List<int[]> getBelowPlayerFallbackEndpoints(Entity player, float yaw, float pitch, int targetY) {
    List<int[]> endpoints = new ArrayList<>();
    if (!isDiagonalMovementContext(player)) {
      if (!player.onGround) {
        addBelowTargetIfUnique(player, endpoints, getFeetBelowTargetAtY(player, targetY));
        addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.0));
        addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.7));
      }
      addBelowTargetIfUnique(player, endpoints, getCursorStartTargetAtY(player, yaw, pitch, targetY));
      addBelowTargetIfUnique(player, endpoints, getCursorPlacedTargetFromRay(yaw, pitch, targetY));
      addBelowTargetIfUnique(player, endpoints, getCursorTargetAtY(player, yaw, pitch, targetY));
      return endpoints;
    }
    addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.0));
    addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.7));
    return endpoints;
  }

  private Object[] findBelowPlayerAirborneFallback(
      Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
    if (now() >= deadlineMs) return null;
    int playerBelowY = getCurrentBelowTargetY(player);
    boolean diagonal = isDiagonalMovementContext(player);
    boolean allowNonCursorTarget = diagonal || !player.onGround;
    List<int[]> fallbackTargets = new ArrayList<>();
    for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, currentPitch, playerBelowY)) {
      addBelowTarget(player, fallbackTargets, endpoint);
    }
    for (int[] targetPos : fallbackTargets) {
      if (now() >= deadlineMs) return null;
      if (!isPlacementTargetAvailable(player, targetPos)) continue;
      Object[] candidate =
          findPitchPlacementForTarget(
              player, yaw, currentPitch, targetPos, heldStack, null, -1, deadlineMs, false,
              allowNonCursorTarget);
      if (candidate != null) return candidate;
    }
    return null;
  }

  private Object[] findNearestSupportToBelowPlayerFallback(
      Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
    if (now() >= deadlineMs) return null;
    int targetY = getCurrentBelowTargetY(player);
    int[] belowPlayer = getFeetBelowTargetAtY(player, targetY);
    if (belowPlayer == null || hasDirectSupportNeighbor(belowPlayer)) return null;

    int[] searchOrigin = getPathStartTowardBelowPlayer(player, targetY, belowPlayer);
    int[] nearestStart = findNearestSupportedReplaceableTarget(player, searchOrigin, belowPlayer, targetY, deadlineMs);
    if (nearestStart == null) return null;

    List<int[]> requiredPath = rasterizeHorizontalLineAtY(nearestStart, belowPlayer, targetY, 64);
    for (int i = requiredPath.size() - 1; i >= 0; i--) {
      if (now() >= deadlineMs) return null;
      int[] pathPos = requiredPath.get(i);
      if (!isPlacementTargetAvailable(player, pathPos)) continue;
      Object[] candidate =
          findPitchPlacementForTarget(
              player, yaw, currentPitch, pathPos, heldStack, null, -1, deadlineMs, false, true);
      if (candidate != null) return candidate;
    }
    return null;
  }

  private int[] findNearestSupportedReplaceableTarget(
      Entity player, int[] origin, int[] belowPlayer, int targetY, long deadlineMs) {
    if (origin == null || belowPlayer == null || now() >= deadlineMs) return null;
    for (int radius = 0; radius <= 3; radius++) {
      int[] bestAtRadius = null;
      double bestScore = Double.POSITIVE_INFINITY;
      for (int dx = -radius; dx <= radius; dx++) {
        int dzAbs = radius - Math.abs(dx);
        int[] positive = new int[] {origin[0] + dx, targetY, origin[2] + dzAbs};
        if (isPlacementTargetAvailable(player, positive) && hasDirectSupportNeighbor(positive)) {
          double score = scoreAirPathStartCandidate(positive, belowPlayer, origin);
          if (score < bestScore) {
            bestScore = score;
            bestAtRadius = positive;
          }
        }
        if (dzAbs == 0) continue;
        int[] negative = new int[] {origin[0] + dx, targetY, origin[2] - dzAbs};
        if (isPlacementTargetAvailable(player, negative) && hasDirectSupportNeighbor(negative)) {
          double score = scoreAirPathStartCandidate(negative, belowPlayer, origin);
          if (score < bestScore) {
            bestScore = score;
            bestAtRadius = negative;
          }
        }
      }
      if (bestAtRadius != null) return bestAtRadius;
    }
    return null;
  }

  private double scoreAirPathStartCandidate(int[] candidate, int[] belowPlayer, int[] origin) {
    double sampleY = candidate[1] + 0.5;
    double goalDistSq =
        distSq(
            candidate[0] + 0.5, sampleY, candidate[2] + 0.5,
            belowPlayer[0] + 0.5, sampleY, belowPlayer[2] + 0.5);
    double originDistSq =
        distSq(
            candidate[0] + 0.5, sampleY, candidate[2] + 0.5,
            origin[0] + 0.5, sampleY, origin[2] + 0.5);
    return goalDistSq * 4.0 + originDistSq;
  }

  private int[] getPathStartTowardBelowPlayer(Entity player, int targetY, int[] fallback) {
    int[] pathStart = null;
    if (lastPlacedPos != null && lastPlacedPos[1] == targetY) pathStart = lastPlacedPos;
    if (pathStart == null) pathStart = getMotionBelowTargetAtY(player, targetY, 1.7);
    if (pathStart == null) pathStart = getMotionBelowTargetAtY(player, targetY, 1.0);
    return pathStart != null ? pathStart : fallback;
  }

  private boolean hasValidLastPlacedPos(Entity player) {
    if (lastPlacedPos == null) return false;
    return isWithinReach(player, lastPlacedPos)
        && isSupportAvailable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2])
        && !isInteractable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2]);
  }

  private boolean hasValidLastSupportFace(Entity player) {
    if (lastSupportPos == null || lastSupportFace < 0) return false;
    return isWithinReach(player, lastSupportPos)
        && isSupportAvailable(lastSupportPos[0], lastSupportPos[1], lastSupportPos[2])
        && !isInteractable(lastSupportPos[0], lastSupportPos[1], lastSupportPos[2]);
  }

  private Object[] findLegacyBelowPlacement(
      Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
    if (now() >= deadlineMs || !isUsableBlockStack(heldStack)) return null;
    if (isDiagonalMovementContext(player)) {
      Object[] diagonalCandidate =
          findLegacyDiagonalPlacement(player, yaw, currentPitch, heldStack, deadlineMs);
      if (diagonalCandidate != null) return diagonalCandidate;
    }
    if (hasValidLastPlacedPos(player)) {
      Object[] preferred =
          findLegacyBelowPlacementForSupport(
              player, yaw, currentPitch, heldStack, lastPlacedPos, deadlineMs);
      if (preferred != null) return preferred;
    }
    return findLegacyBelowPlacementForSupport(player, yaw, currentPitch, heldStack, null, deadlineMs);
  }

  private Object[] findLegacyDiagonalPlacement(
      Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
    if (now() >= deadlineMs) return null;
    List<int[]> diagonalTargets = new ArrayList<>();
    int currentY = getCurrentBelowTargetY(player);
    int strictY = getStrictBelowTargetY(player);
    for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, currentPitch, currentY)) {
      addBelowTarget(player, diagonalTargets, endpoint);
    }
    if (strictY != currentY) {
      for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, currentPitch, strictY)) {
        addBelowTarget(player, diagonalTargets, endpoint);
      }
    }
    if (diagonalTargets.isEmpty()) return null;
    int[] preferredSupportPos = hasValidLastPlacedPos(player) ? lastPlacedPos : null;
    for (int[] targetPos : diagonalTargets) {
      if (now() >= deadlineMs) return null;
      if (!isPlacementTargetAvailable(player, targetPos)) continue;
      Object[] candidate =
          findLegacyPitchPlacementForTarget(
              player, yaw, currentPitch, targetPos, heldStack, preferredSupportPos, deadlineMs);
      if (candidate != null) return candidate;
    }
    if (preferredSupportPos == null) return null;
    for (int[] targetPos : diagonalTargets) {
      if (now() >= deadlineMs) return null;
      if (!isPlacementTargetAvailable(player, targetPos)) continue;
      Object[] candidate =
          findLegacyPitchPlacementForTarget(
              player, yaw, currentPitch, targetPos, heldStack, null, deadlineMs);
      if (candidate != null) return candidate;
    }
    return null;
  }

  private Object[] findLegacyBelowPlacementForSupport(
      Entity player, float yaw, float currentPitch, ItemStack heldStack, int[] preferredSupportPos,
      long deadlineMs) {
    for (int[] targetPos : getMessageStyleBelowTargets(player)) {
      if (now() >= deadlineMs) return null;
      if (!isPlacementTargetAvailable(player, targetPos)) continue;
      Object[] candidate =
          findLegacyPitchPlacementForTarget(
              player, yaw, currentPitch, targetPos, heldStack, preferredSupportPos, deadlineMs);
      if (candidate != null) return candidate;
    }
    return null;
  }

  private Object[] findLegacyPitchPlacementForTarget(
      Entity player, float yaw, float currentPitch, int[] targetPos, ItemStack heldStack,
      int[] preferredSupportPos, long deadlineMs) {
    float clampedBasePitch = clampFloat(currentPitch, 40.0f, 89.0f);
    Object[] direct = tryLegacyPitch(yaw, clampedBasePitch, targetPos, preferredSupportPos, deadlineMs);
    if (direct != null) return direct;
    for (int offset = 1; offset <= 49; offset++) {
      if (now() >= deadlineMs) return null;
      float up = clampedBasePitch + offset;
      if (up <= 89.0f) {
        Object[] candidate = tryLegacyPitch(yaw, up, targetPos, preferredSupportPos, deadlineMs);
        if (candidate != null) return candidate;
      }
      float down = clampedBasePitch - offset;
      if (down >= 40.0f) {
        Object[] candidate = tryLegacyPitch(yaw, down, targetPos, preferredSupportPos, deadlineMs);
        if (candidate != null) return candidate;
      }
    }
    return null;
  }

  private Object[] tryLegacyPitch(float yaw, float pitch, int[] targetPos, int[] preferredSupportPos, long deadlineMs) {
    if (now() >= deadlineMs) return null;
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] supportPos = (int[]) traced[0];
    int face = (Integer) traced[1];
    if (preferredSupportPos != null && !posEquals(supportPos, preferredSupportPos)) return null;
    if (face == 0) return null;
    if (isReplaceable(supportPos[0], supportPos[1], supportPos[2])
        || isInteractable(supportPos[0], supportPos[1], supportPos[2])) return null;
    int[] placedPos = offsetPos(supportPos, face);
    if (!posEquals(placedPos, targetPos)) return null;
    return new Object[] {Math.min(pitch, 89.0f), supportPos, face, (Vec3) traced[2], placedPos};
  }

  private List<int[]> getMessageStyleBelowTargets(Entity player) {
    double[] offsets = {0.0, 0.29, -0.29};
    Vec3 pos = playerPosition(player);
    int maxY = floor(pos.yCoord) - 1;
    int minY = floor(pos.yCoord) - 2;
    List<int[]> targets = new ArrayList<>();
    for (int targetY = maxY; targetY >= minY; targetY--) {
      for (double xOffset : offsets) {
        for (double zOffset : offsets) {
          targets.add(new int[] {floor(pos.xCoord + xOffset), targetY, floor(pos.zCoord + zOffset)});
        }
      }
    }
    return targets;
  }

  private Object[] findBelowPlacementForSupport(
      Entity player, float yaw, float currentPitch, ItemStack heldStack, int[] preferredSupportPos,
      int preferredSupportFace, long deadlineMs) {
    boolean diagonal = isDiagonalMovementContext(player);
    for (int[] targetPos : getBelowTargets(player, yaw, currentPitch)) {
      if (now() >= deadlineMs) return null;
      if (!isPlacementTargetAvailable(player, targetPos)) continue;
      if (!isStrictOneBelowPlayer(player, targetPos)) continue;
      Object[] candidate =
          findPitchPlacementForTarget(
              player, yaw, currentPitch, targetPos, heldStack, preferredSupportPos,
              preferredSupportFace, deadlineMs, false, diagonal);
      if (candidate != null) return candidate;
    }
    return null;
  }

  private boolean isWithinReach(Entity player, int[] pos) {
    if (pos == null) return false;
    Vec3 eyes = getEyes(player);
    double cx = Math.max(pos[0], Math.min(eyes.xCoord, pos[0] + 1.0));
    double cy = Math.max(pos[1], Math.min(eyes.yCoord, pos[1] + 1.0));
    double cz = Math.max(pos[2], Math.min(eyes.zCoord, pos[2] + 1.0));
    double dx = eyes.xCoord - cx;
    double dy = eyes.yCoord - cy;
    double dz = eyes.zCoord - cz;
    return dx * dx + dy * dy + dz * dz <= reach() * reach();
  }

  private Object[] findPitchPlacementForTarget(
      Entity player, float yaw, float currentPitch, int[] targetPos, ItemStack heldStack,
      int[] preferredSupportPos, int preferredSupportFace, long deadlineMs,
      boolean requireLookAlignment, boolean allowNonCursorTarget) {
    if (now() >= deadlineMs || targetPos == null) return null;
    boolean effectiveAllowNonCursorTarget =
        allowNonCursorTarget || shouldAllowPlayerOneNonCursorTarget(player, targetPos);
    if (!effectiveAllowNonCursorTarget
        && !isCursorOrBelowPlayerTarget(player, targetPos, yaw, currentPitch)) return null;
    if (!isPlacementTargetAvailable(player, targetPos)) return null;

    Object[] bestCandidate = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (int placeFace : getAllowedPlaceFacesForContext(player, yaw)) {
      if (now() >= deadlineMs) break;
      if (shouldRejectStraightSideSwitch(player, targetPos, placeFace)) continue;
      int[] supportPos = offsetPos(targetPos, opposite(placeFace));
      if (preferredSupportPos != null && !posEquals(supportPos, preferredSupportPos)) continue;
      if (preferredSupportFace >= 0 && placeFace != preferredSupportFace) continue;
      if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) continue;
      if (!isWithinReach(player, supportPos)) continue;

      double[] hitOffsets = useExtendedSearch() ? EXTENDED_FACE_HIT_OFFSETS : FACE_HIT_OFFSETS;
      for (double primaryOffset : hitOffsets) {
        for (double secondaryOffset : hitOffsets) {
          if (now() >= deadlineMs) break;
          Vec3 hitVec = getSupportFaceHitVec(supportPos, placeFace, primaryOffset, secondaryOffset);
          Object[] candidate =
              buildPlacementCandidateForHitVec(
                  player, yaw, targetPos, supportPos, placeFace, hitVec, requireLookAlignment,
                  effectiveAllowNonCursorTarget);
          if (candidate == null) continue;
          double candidateScore =
              scorePlacementCandidate(
                  player, currentPitch, candidatePitch(candidate), placeFace, primaryOffset,
                  secondaryOffset);
          if (candidateScore < bestScore) {
            bestScore = candidateScore;
            bestCandidate = candidate;
          }
        }
      }
    }
    if (bestCandidate == null && preferredSupportPos != null && preferredSupportFace >= 0) {
      return findRayAlignedPitchCandidate(
          yaw, currentPitch, targetPos, preferredSupportPos, preferredSupportFace, deadlineMs);
    }
    return bestCandidate;
  }

  private Object[] findRayAlignedPitchCandidate(
      float yaw, float currentPitch, int[] targetPos, int[] supportPos, int placeFace,
      long deadlineMs) {
    float clampedBasePitch = clampFloat(currentPitch, 40.0f, 89.0f);
    for (int offset = 0; offset <= 49; offset++) {
      if (now() >= deadlineMs) return null;
      float upPitch = clampedBasePitch + offset;
      if (upPitch <= 89.0f) {
        Object[] candidate = tryRayAlignedPitch(yaw, upPitch, targetPos, supportPos, placeFace);
        if (candidate != null) return candidate;
      }
      if (offset == 0) continue;
      float downPitch = clampedBasePitch - offset;
      if (downPitch >= 40.0f) {
        Object[] candidate = tryRayAlignedPitch(yaw, downPitch, targetPos, supportPos, placeFace);
        if (candidate != null) return candidate;
      }
    }
    return null;
  }

  private Object[] tryRayAlignedPitch(float yaw, float pitch, int[] targetPos, int[] supportPos, int placeFace) {
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] tracedSupport = (int[]) traced[0];
    int tracedFace = (Integer) traced[1];
    if (!posEquals(tracedSupport, supportPos) || tracedFace != placeFace) return null;
    int[] tracedPlaced = offsetPos(tracedSupport, tracedFace);
    if (!posEquals(tracedPlaced, targetPos)) return null;
    return new Object[] {pitch, tracedSupport, tracedFace, (Vec3) traced[2], tracedPlaced};
  }

  private double scorePlacementCandidate(
      Entity player, float currentPitch, float candidatePitchValue, int placeFace,
      double primaryOffset, double secondaryOffset) {
    double pitchPenalty = Math.abs(wrapAngle(candidatePitchValue - currentPitch));
    double centerPenalty = Math.abs(primaryOffset - 0.5) + Math.abs(secondaryOffset - 0.5);
    double facePenalty = placeFace == 1 ? 0.0 : 0.35;
    double straightSidePenalty = getStraightSideSwitchPenalty(player, placeFace);
    return pitchPenalty + centerPenalty * 2.0 + facePenalty + straightSidePenalty;
  }

  private double getStraightSideSwitchPenalty(Entity player, int placeFace) {
    if (getConditionModeCheck(player) != 1) return 0.0;
    if (lastSupportFace < 2) return 0.0;
    if (placeFace == lastSupportFace) return 0.0;
    return 0.8;
  }

  private boolean shouldRejectStraightSideSwitch(Entity player, int[] targetPos, int placeFace) {
    if (targetPos == null || getConditionModeCheck(player) != 1) return false;
    if (placeFace < 2) return false;
    if (lastSupportFace < 2) return false;
    if (placeFace == lastSupportFace) return false;
    if (isNearStraightSupportEdge(player)) return false;
    int[] laneSupportPos = offsetPos(targetPos, opposite(lastSupportFace));
    return isSupportAvailable(laneSupportPos[0], laneSupportPos[1], laneSupportPos[2])
        && isWithinReach(player, laneSupportPos);
  }

  private Object[] buildPlacementCandidateForHitVec(
      Entity player, float yaw, int[] targetPos, int[] supportPos, int placeFace, Vec3 hitVec,
      boolean requireLookAlignment, boolean allowNonCursorTarget) {
    if (hitVec == null) return null;
    int[] offsetTarget = offsetPos(supportPos, placeFace);
    if (!posEquals(offsetTarget, targetPos)) return null;
    if (!isStrictOneBelowPlayer(player, offsetTarget)) return null;
    Float pitch = computePitchToHitVec(player, hitVec);
    if (pitch == null) return null;
    if (!isPlacementLookAligned(yaw, pitch, supportPos, placeFace, targetPos)) return null;
    if (!(allowNonCursorTarget
        || isDiagonalMovementContext(player)
        || isSupportFaceVisible(player, supportPos, placeFace, hitVec))) return null;
    return new Object[] {pitch, supportPos, placeFace, hitVec, offsetTarget};
  }

  private int[] getAllowedPlaceFacesForContext(Entity player, float yaw) {
    if (getConditionModeCheck(player) != 1) return ALLOWED_PLACE_FACES;
    int forward = getStraightForwardFacing(player, yaw);
    if (useExtendedSearch()) {
      return new int[] {rotateY(forward), rotateYCCW(forward), forward, opposite(forward), 1};
    }
    return new int[] {rotateY(forward), rotateYCCW(forward), forward, opposite(forward)};
  }

  private boolean isPlacementLookAligned(
      float yaw, float pitch, int[] supportPos, int placeFace, int[] targetPos) {
    if (supportPos == null || placeFace < 0 || targetPos == null) return false;
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return false;
    if (!posEquals((int[]) traced[0], supportPos) || (Integer) traced[1] != placeFace) return false;
    int[] tracedOffset = offsetPos((int[]) traced[0], (Integer) traced[1]);
    return posEquals(tracedOffset, targetPos);
  }

  private boolean isSupportFaceVisible(Entity player, int[] supportPos, int placeFace, Vec3 hitVec) {
    Vec3 eyes = getEyes(player);
    double dx = hitVec.xCoord - eyes.xCoord;
    double dy = hitVec.yCoord - eyes.yCoord;
    double dz = hitVec.zCoord - eyes.zCoord;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (distance < 1.0E-4) return false;
    float traceYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    float tracePitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    Object[] traced = raycastBlock(distance + 0.5, traceYaw, tracePitch);
    if (traced == null) return false;
    int[] tracedPos = posFromVec((Vec3) traced[0]);
    int tracedFace = faceFromName((String) traced[2]);
    return posEquals(tracedPos, supportPos) && tracedFace == placeFace;
  }

  private Vec3 getSupportFaceHitVec(
      int[] supportPos, int placeFace, double primaryOffset, double secondaryOffset) {
    double primary = Math.max(0.001, Math.min(0.999, primaryOffset));
    double secondary = Math.max(0.001, Math.min(0.999, secondaryOffset));
    if (placeFace == 2)
      return new Vec3(supportPos[0] + primary, supportPos[1] + secondary, supportPos[2] + 0.001);
    if (placeFace == 3)
      return new Vec3(supportPos[0] + primary, supportPos[1] + secondary, supportPos[2] + 0.999);
    if (placeFace == 5)
      return new Vec3(supportPos[0] + 0.999, supportPos[1] + primary, supportPos[2] + secondary);
    if (placeFace == 4)
      return new Vec3(supportPos[0] + 0.001, supportPos[1] + primary, supportPos[2] + secondary);
    if (placeFace == 0)
      return new Vec3(supportPos[0] + primary, supportPos[1] + 0.001, supportPos[2] + secondary);
    return new Vec3(supportPos[0] + primary, supportPos[1] + 0.999, supportPos[2] + secondary);
  }

  private Float computePitchToHitVec(Entity player, Vec3 hitVec) {
    Vec3 eyes = getEyes(player);
    double dx = hitVec.xCoord - eyes.xCoord;
    double dz = hitVec.zCoord - eyes.zCoord;
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    double dy = hitVec.yCoord - eyes.yCoord;
    float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    return Math.max(-89.0f, Math.min(89.0f, pitch));
  }

  private List<int[]> getBelowTargets(Entity player, float yaw, float pitch) {
    if (cachedBelowTargetsTick == currentClientTick && cachedBelowTargets != null)
      return cachedBelowTargets;
    List<int[]> belowTargets = new ArrayList<>();
    boolean diagonal = isDiagonalMovementContext(player);
    if (!diagonal) {
      int currentY = getCurrentBelowTargetY(player);
      addBelowTarget(player, belowTargets, getCursorStartTargetAtY(player, yaw, pitch, currentY));
      if (belowTargets.isEmpty()) {
        int strictY = getStrictBelowTargetY(player);
        if (strictY != currentY)
          addBelowTarget(player, belowTargets, getCursorStartTargetAtY(player, yaw, pitch, strictY));
      }
      if (belowTargets.isEmpty())
        addBelowTarget(player, belowTargets, getCursorPlacedTargetFromRay(yaw, pitch, currentY));
      if (belowTargets.isEmpty()) {
        int strictY = getStrictBelowTargetY(player);
        if (strictY != currentY)
          addBelowTarget(player, belowTargets, getCursorPlacedTargetFromRay(yaw, pitch, strictY));
      }
      if (belowTargets.isEmpty())
        addBelowTarget(player, belowTargets, getCursorTargetAtY(player, yaw, pitch, currentY));
    } else {
      int currentY = getCurrentBelowTargetY(player);
      addBelowTarget(player, belowTargets, getMotionBelowTargetAtY(player, currentY, 1.0));
      addBelowTarget(player, belowTargets, getMotionBelowTargetAtY(player, currentY, 1.7));
      for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, pitch, currentY)) {
        addBelowTarget(player, belowTargets, endpoint);
      }
    }
    cachedBelowTargets = belowTargets;
    cachedBelowTargetsTick = currentClientTick;
    return belowTargets;
  }

  private boolean isCursorOrBelowPlayerTarget(Entity player, int[] targetPos, float yaw, float pitch) {
    if (targetPos == null) return false;
    if (!isDiagonalMovementContext(player)) {
      int currentY = getCurrentBelowTargetY(player);
      if (posEquals(getCursorStartTargetAtY(player, yaw, pitch, currentY), targetPos)) return true;
      if (posEquals(getCursorPlacedTargetFromRay(yaw, pitch, currentY), targetPos)) return true;
      int strictY = getStrictBelowTargetY(player);
      if (strictY != currentY) {
        if (posEquals(getCursorStartTargetAtY(player, yaw, pitch, strictY), targetPos)) return true;
        if (posEquals(getCursorPlacedTargetFromRay(yaw, pitch, strictY), targetPos)) return true;
      }
      if (isCursorInsideTargetAtY(player, targetPos, yaw, pitch, currentY)) return true;
      return posEquals(getCursorTargetAtY(player, yaw, pitch, currentY), targetPos);
    }
    int strictY = getStrictBelowTargetY(player);
    if (isBelowPlayerTargetAtY(player, targetPos, strictY, yaw, pitch)) return true;
    return isBelowPlayerTargetAtY(player, targetPos, getCurrentBelowTargetY(player), yaw, pitch);
  }

  private boolean isBelowPlayerTargetAtY(Entity player, int[] targetPos, int targetY, float yaw, float pitch) {
    for (int[] candidate : getBelowPlayerFallbackEndpoints(player, yaw, pitch, targetY)) {
      if (posEquals(targetPos, candidate)) return true;
    }
    return false;
  }

  private int[] getFeetBelowTargetAtY(Entity player, int targetY) {
    Vec3 pos = playerPosition(player);
    return new int[] {floor(pos.xCoord), targetY, floor(pos.zCoord)};
  }

  private boolean shouldAllowPlayerOneNonCursorTarget(Entity player, int[] targetPos) {
    if (targetPos == null) return false;
    if (isDiagonalMovementContext(player) || player.onGround) return false;
    if (!isPlayerHitboxFullyInsideSingleBlockColumn(player)) return false;
    if (!hasValidLastSupportFace(player) || lastSupportFace == 0) return false;
    int[] continuationTarget = offsetPos(lastSupportPos, lastSupportFace);
    if (!posEquals(targetPos, continuationTarget)) return false;
    int targetY = targetPos[1];
    int currentY = getCurrentBelowTargetY(player);
    int strictY = getStrictBelowTargetY(player);
    if (targetY != currentY && targetY != strictY) return false;
    int[] feetBelow = getFeetBelowTargetAtY(player, targetY);
    int horizontalDistance = Math.abs(targetPos[0] - feetBelow[0]) + Math.abs(targetPos[2] - feetBelow[2]);
    return horizontalDistance <= 1;
  }

  private boolean isPlayerHitboxFullyInsideSingleBlockColumn(Entity player) {
    Vec3 pos = playerPosition(player);
    double half = player.width / 2.0;
    int minX = floor(pos.xCoord - half + 1.0E-4);
    int maxX = floor(pos.xCoord + half - 1.0E-4);
    if (minX != maxX) return false;
    int minZ = floor(pos.zCoord - half + 1.0E-4);
    int maxZ = floor(pos.zCoord + half - 1.0E-4);
    return minZ == maxZ;
  }

  private int[] getMotionBelowTargetAtY(Entity player, int targetY, double multiplier) {
    Vec3 pos = playerPosition(player);
    Vec3 motion = playerMotion();
    return new int[] {
      floor(pos.xCoord + motion.xCoord * multiplier), targetY,
      floor(pos.zCoord + motion.zCoord * multiplier)
    };
  }

  private boolean hasDirectSupportNeighbor(int[] targetPos) {
    for (int placeFace : ALLOWED_PLACE_FACES) {
      int[] supportPos = offsetPos(targetPos, opposite(placeFace));
      if (isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return true;
    }
    return false;
  }

  private void addBelowTargetIfUnique(Entity player, List<int[]> targets, int[] candidate) {
    if (candidate == null) return;
    if (!isStrictOneBelowPlayer(player, candidate)) return;
    for (int[] existing : targets) {
      if (posEquals(existing, candidate)) return;
    }
    targets.add(candidate);
  }

  private void addBelowTarget(Entity player, List<int[]> targets, int[] candidate) {
    addBelowTargetIfUnique(player, targets, candidate);
  }

  private List<int[]> rasterizeHorizontalLineAtY(int[] start, int[] end, int y, int maxSteps) {
    List<int[]> line = new ArrayList<>();
    int x0 = start[0];
    int z0 = start[2];
    int x1 = end[0];
    int z1 = end[2];
    int dx = Math.abs(x1 - x0);
    int dz = Math.abs(z1 - z0);
    int sx = Integer.compare(x1, x0);
    int sz = Integer.compare(z1, z0);
    int movedX = 0;
    int movedZ = 0;
    for (int steps = 0; steps < maxSteps; steps++) {
      line.add(new int[] {x0, y, z0});
      if ((x0 == x1 && z0 == z1) || (movedX >= dx && movedZ >= dz)) break;
      if (movedX >= dx) {
        z0 += sz;
        movedZ++;
      } else if (movedZ >= dz) {
        x0 += sx;
        movedX++;
      } else if ((1 + 2 * movedX) * dz < (1 + 2 * movedZ) * dx) {
        x0 += sx;
        movedX++;
      } else {
        z0 += sz;
        movedZ++;
      }
    }
    return line;
  }

  private int getDetectedModeCheck(Entity player) {
    float forwardInput = Math.abs(mc.thePlayer.movementInput.moveForward);
    float strafeInput = Math.abs(mc.thePlayer.movementInput.moveStrafe);
    if (forwardInput >= 0.08f || strafeInput >= 0.08f) {
      return (forwardInput >= 0.08f && strafeInput >= 0.08f) ? 1 : 2;
    }
    double[] direction = getMotionDirectionComponents(player);
    if (direction == null) return 1;
    double angleDeg = Math.toDegrees(Math.atan2(direction[1], direction[0]));
    double norm90 = (angleDeg % 90.0 + 90.0) % 90.0;
    return Math.abs(norm90 - 45.0) <= 18.0 ? 2 : 1;
  }

  private double[] getMotionDirectionComponents(Entity player) {
    Vec3 pos = playerPosition(player);
    Vec3 last = playerPositionLast(player);
    double dirX = pos.xCoord - last.xCoord;
    double dirZ = pos.zCoord - last.zCoord;
    double speedSq = dirX * dirX + dirZ * dirZ;
    if (speedSq < 1.0E-4) {
      Vec3 motion = playerMotion();
      dirX = motion.xCoord;
      dirZ = motion.zCoord;
      speedSq = dirX * dirX + dirZ * dirZ;
    }
    if (speedSq < 1.0E-4) return null;
    return new double[] {dirX, dirZ};
  }

  private double[] getInputDirectionComponents(float referenceYaw) {
    float forwardInput = mc.thePlayer.movementInput.moveForward;
    float strafeInput = mc.thePlayer.movementInput.moveStrafe;
    if (Math.abs(forwardInput) < 0.08f && Math.abs(strafeInput) < 0.08f) return null;
    double yawRadians = Math.toRadians(referenceYaw);
    double sinYaw = Math.sin(yawRadians);
    double cosYaw = Math.cos(yawRadians);
    double dirX = forwardInput * -sinYaw + strafeInput * cosYaw;
    double dirZ = forwardInput * cosYaw - strafeInput * sinYaw;
    if (dirX * dirX + dirZ * dirZ < 1.0E-4) return null;
    return new double[] {dirX, dirZ};
  }

  private int getStraightForwardFacing(Entity player, float fallbackYaw) {
    double[] direction = getInputDirectionComponents(fallbackYaw);
    if (direction == null) direction = getMotionDirectionComponents(player);
    if (direction == null) return facingFromYaw(fallbackYaw);
    float directionYaw = (float) (Math.toDegrees(Math.atan2(direction[1], direction[0])) - 90.0);
    return facingFromYaw(directionYaw);
  }

  private int getConditionModeCheck(Entity player) {
    if (forcedModeCheck != 0) return forcedModeCheck;
    return getDetectedModeCheck(player);
  }

  private boolean isDiagonalMovementContext(Entity player) {
    return getConditionModeCheck(player) == 2;
  }

  private int[] getCursorPlacedTargetFromRay(float yaw, float pitch, int targetY) {
    Object[] traced = rayCast(yaw, pitch);
    if (traced == null) return null;
    int[] offsetTarget = offsetPos((int[]) traced[0], (Integer) traced[1]);
    if (offsetTarget[1] != targetY) return null;
    return offsetTarget;
  }

  private int[] getCursorStartTargetAtY(Entity player, float fallbackYaw, float fallbackPitch, int targetY) {
    Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
    Vec3 lookVec = getCursorLookVec(player);
    if (cursorPoint == null || lookVec == null) return null;
    double startX = cursorPoint.xCoord - lookVec.xCoord * 0.03;
    double startZ = cursorPoint.zCoord - lookVec.zCoord * 0.03;
    return new int[] {floor(startX), targetY, floor(startZ)};
  }

  private int[] getCursorTargetAtY(Entity player, float fallbackYaw, float fallbackPitch, int targetY) {
    Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
    if (cursorPoint == null) return null;
    return new int[] {floor(cursorPoint.xCoord), targetY, floor(cursorPoint.zCoord)};
  }

  private Vec3 getCursorIntersectionAtY(Entity player, int targetY) {
    Vec3 eyes = getEyes(player);
    Vec3 lookVec = getCursorLookVec(player);
    if (lookVec == null || Math.abs(lookVec.yCoord) < 1.0E-4) return null;
    double t = (targetY - eyes.yCoord) / lookVec.yCoord;
    if (t <= 0.0) return null;
    return new Vec3(eyes.xCoord + lookVec.xCoord * t, targetY + 0.5, eyes.zCoord + lookVec.zCoord * t);
  }

  private Vec3 getCursorLookVec(Entity player) {
    double[] cameraRotations = renderRotations();
    if (cameraRotations != null && cameraRotations.length >= 2) {
      return getLookVec((float) cameraRotations[0], (float) cameraRotations[1]);
    }
    return getLookVec(player.rotationYaw, player.rotationPitch);
  }

  private Vec3 getLookVec(float yaw, float pitch) {
    double yawRad = Math.toRadians(yaw);
    double pitchRad = Math.toRadians(pitch);
    double cosPitch = Math.cos(pitchRad);
    return new Vec3(
        -Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
  }

  private boolean isCursorInsideTargetAtY(Entity player, int[] targetPos, float yaw, float pitch, int targetY) {
    if (targetPos == null || targetPos[1] != targetY) return false;
    Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
    if (cursorPoint == null) return false;
    double x = cursorPoint.xCoord;
    double z = cursorPoint.zCoord;
    return x >= targetPos[0] - 1.0E-6
        && x <= targetPos[0] + 1.0 + 1.0E-6
        && z >= targetPos[2] - 1.0E-6
        && z <= targetPos[2] + 1.0 + 1.0E-6;
  }

  private boolean isPlacementTargetAvailable(Entity player, int[] pos) {
    return isBasePlacementTargetAvailable(player, pos) && isStrictOneBelowPlayer(player, pos);
  }

  private boolean isBasePlacementTargetAvailable(Entity player, int[] pos) {
    return pos != null
        && isStraightTellyTarget(pos)
        && !isRejectedTarget(pos)
        && !doesPlacementIntersectPlayer(player, pos)
        && isReplaceable(pos[0], pos[1], pos[2]);
  }

  private boolean doesPlacementIntersectPlayer(Entity player, int[] placePos) {
    if (placePos == null) return false;
    if (isInsideAnyPlayerPositionCell(player, placePos)) return true;

    Vec3 pos = playerPosition(player);
    double half = player.width / 2.0;
    double height = player.height;
    if (boxIntersectsBlock(
        pos.xCoord - half, pos.yCoord, pos.zCoord - half,
        pos.xCoord + half, pos.yCoord + height, pos.zCoord + half, placePos)) return true;
    if (isBlockPosInsideBounds(
        placePos, pos.xCoord - half, pos.yCoord, pos.zCoord - half,
        pos.xCoord + half, pos.yCoord + height, pos.zCoord + half)) return true;

    if (!shouldUseHistoricalPlayerCollisionChecks(player, placePos)) return false;

    Vec3 last = playerPositionLast(player);
    if (last.xCoord != pos.xCoord
        || last.yCoord != pos.yCoord
        || last.zCoord != pos.zCoord) {
      if (boxIntersectsBlock(
          last.xCoord - half, last.yCoord, last.zCoord - half,
          last.xCoord + half, last.yCoord + height, last.zCoord + half, placePos)) return true;
      if (isBlockPosInsideBounds(
          placePos, last.xCoord - half, last.yCoord, last.zCoord - half,
          last.xCoord + half, last.yCoord + height, last.zCoord + half)) return true;
    }
    if (hasLastSentServerPos
        && (lastSentServerPosX != pos.xCoord
            || lastSentServerPosY != pos.yCoord
            || lastSentServerPosZ != pos.zCoord)) {
      double sx = lastSentServerPosX;
      double sy = lastSentServerPosY;
      double sz = lastSentServerPosZ;
      if (boxIntersectsBlock(
          sx - half, sy, sz - half, sx + half, sy + height, sz + half, placePos)) return true;
      if (isBlockPosInsideBounds(
          placePos, sx - half, sy, sz - half, sx + half, sy + height, sz + half)) return true;
    }
    return false;
  }

  private boolean boxIntersectsBlock(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int[] pos) {
    return maxX > pos[0]
        && minX < pos[0] + 1.0
        && maxY > pos[1]
        && minY < pos[1] + 1.0
        && maxZ > pos[2]
        && minZ < pos[2] + 1.0;
  }

  private boolean isBlockPosInsideBounds(
      int[] pos, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    int bMinX = floor(minX + 1.0E-4);
    int bMaxX = floor(maxX - 1.0E-4);
    if (pos[0] < bMinX || pos[0] > bMaxX) return false;
    int bMinZ = floor(minZ + 1.0E-4);
    int bMaxZ = floor(maxZ - 1.0E-4);
    if (pos[2] < bMinZ || pos[2] > bMaxZ) return false;
    int bMinY = floor(minY + 1.0E-4);
    int bMaxY = floor(maxY - 1.0E-4);
    return pos[1] >= bMinY && pos[1] <= bMaxY;
  }

  private boolean isInsideAnyPlayerPositionCell(Entity player, int[] placePos) {
    Vec3 pos = playerPosition(player);
    if (isInsidePlayerPositionCell(placePos, pos.xCoord, pos.yCoord, pos.zCoord)) return true;
    if (!shouldUseHistoricalPlayerCollisionChecks(player, placePos)) return false;
    Vec3 last = playerPositionLast(player);
    if (isInsidePlayerPositionCell(placePos, last.xCoord, last.yCoord, last.zCoord)) return true;
    return hasLastSentServerPos
        && isInsidePlayerPositionCell(placePos, lastSentServerPosX, lastSentServerPosY, lastSentServerPosZ);
  }

  private boolean shouldUseHistoricalPlayerCollisionChecks(Entity player, int[] placePos) {
    if (!player.onGround) return false;
    if (placePos == null) return true;
    return placePos[1] > getCurrentBelowTargetY(player);
  }

  private boolean isInsidePlayerPositionCell(int[] placePos, double x, double y, double z) {
    int playerX = floor(x);
    int playerY = floor(y);
    int playerZ = floor(z);
    return placePos[0] == playerX
        && placePos[2] == playerZ
        && (placePos[1] == playerY || placePos[1] == playerY + 1);
  }

  private boolean isStrictOneBelowPlayer(Entity player, int[] pos) {
    if (pos == null) return false;
    int targetY = pos[1];
    int currentY = getCurrentBelowTargetY(player);
    if (targetY == currentY) return true;
    if (targetY == getStrictBelowTargetY(player)) return true;
    int previousY = getPreviousBelowTargetY(player);
    if (previousY != Integer.MIN_VALUE && targetY == previousY) return true;
    return isStraightAscendingContext(player) && targetY == currentY + 1;
  }

  private double getStableBelowReferenceY(Entity player) {
    Vec3 pos = playerPosition(player);
    double referenceY = pos.yCoord;
    Vec3 motion = playerMotion();
    if (!player.onGround && motion.yCoord > -0.12 && motion.yCoord <= 0.0) {
      referenceY = Math.max(referenceY, playerPositionLast(player).yCoord);
    }
    return referenceY;
  }

  private int getStrictBelowTargetY(Entity player) {
    if (isDiagonalMovementContext(player)) return getCurrentBelowTargetY(player);
    double projectedY = getStableBelowReferenceY(player);
    Vec3 motion = playerMotion();
    if (!player.onGround && motion.yCoord < -0.12) {
      projectedY = playerPosition(player).yCoord + motion.yCoord * 0.75;
    }
    return floor(projectedY) - 1;
  }

  private int getCurrentBelowTargetY(Entity player) {
    return floor(getStableBelowReferenceY(player)) - 1;
  }

  private int getPreviousBelowTargetY(Entity player) {
    return floor(playerPositionLast(player).yCoord) - 1;
  }

  private boolean isStraightAscendingContext(Entity player) {
    if (getConditionModeCheck(player) != 1) return false;
    Vec3 motion = playerMotion();
    return motion.yCoord > 0.0
        || playerPosition(player).yCoord > playerPositionLast(player).yCoord + 1.0E-4;
  }

  private boolean isSupportAvailable(int x, int y, int z) {
    if (isInteractable(x, y, z)) return false;
    return !isReplaceable(x, y, z);
  }

  private boolean isRejectedTarget(int[] pos) {
    Integer rejectedAtTick = rejectedTargets.get(posKey(pos));
    if (rejectedAtTick == null) return false;
    return currentClientTick - rejectedAtTick <= 4;
  }

  private void markRejectedTarget(int[] pos) {
    if (pos == null) return;
    rejectedTargets.put(posKey(pos), currentClientTick);
  }

  private void pruneRejectedTargets() {
    if (rejectedTargets.isEmpty()) return;
    Iterator<Map.Entry<String, Integer>> iterator = rejectedTargets.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Integer> entry = iterator.next();
      if (currentClientTick - entry.getValue() > 4) {
        iterator.remove();
        continue;
      }
      String[] parts = entry.getKey().split(",");
      if (!isReplaceable(
          Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))) {
        iterator.remove();
      }
    }
  }

  private Object[] rayCast(float yaw, float pitch) {
    Object[] hit = raycastBlock(reach(), yaw, pitch);
    if (hit == null || hit[0] == null || hit[2] == null) return null;
    int face = faceFromName((String) hit[2]);
    if (face < 0 || face == 0) return null;
    int[] supportPos = posFromVec((Vec3) hit[0]);
    Vec3 offset = (Vec3) hit[1];
    Vec3 hitAbs =
        new Vec3(supportPos[0] + offset.xCoord, supportPos[1] + offset.yCoord, supportPos[2] + offset.zCoord);
    return new Object[] {supportPos, face, hitAbs};
  }

  private Vec3 getEyes(Entity player) {
    Vec3 pos = playerPosition(player);
    return new Vec3(pos.xCoord, pos.yCoord + player.getEyeHeight(), pos.zCoord);
  }

  private String blockNameAt(int x, int y, int z) {
    Block block = BlockUtil.getBlock(new net.minecraft.util.BlockPos(x, y, z));
    return block == null ? "air" : blockName(block);
  }

  private String blockName(Block block) {
    try {
      ResourceLocation name = Block.blockRegistry.getNameForObject(block);
      return name == null ? "air" : name.getResourcePath();
    } catch (Exception e) {
      return "air";
    }
  }

  private boolean isReplaceable(int x, int y, int z) {
    return isReplaceableName(blockNameAt(x, y, z), false);
  }

  private boolean isReplaceableName(String name, boolean airOnly) {
    if (name == null) return false;
    if (airOnly) return name.equals("air");
    for (String replaceable : REPLACEABLE_BLOCKS) {
      if (name.equals(replaceable)) return true;
    }
    for (String replaceable : EXPERIMENTAL_REPLACEABLE_BLOCKS) {
      if (name.equals(replaceable)) return true;
    }
    return false;
  }

  private boolean isInteractable(int x, int y, int z) {
    Block block = BlockUtil.getBlock(new net.minecraft.util.BlockPos(x, y, z));
    if (block == null) return false;
    if (BlockUtil.isInteractable(block)) return true;
    String type = block.getClass().getSimpleName();
    if (type == null) return false;
    for (String interactableType : INTERACTABLE_TYPES) {
      if (type.equals(interactableType)) return true;
    }
    return false;
  }

  private double reach() {
    return mc.playerController.isInCreativeMode() ? 5.0 : 4.5;
  }

  private int placementTick(Entity player) {
    if (isRavenTimerActive()) return (int) (now() / 50L);
    return player.ticksExisted;
  }

  private boolean isRavenTimerActive() {
    try {
      for (Module m : Miau.moduleManager.modules.values()) {
        if (m.getName().equalsIgnoreCase("Timer") && m.isEnabled()) return true;
      }
    } catch (Exception ignored) {
      return false;
    }
    return false;
  }

  private float candidatePitch(Object[] candidate) {
    return clampFloat((Float) candidate[0], -90.0f, 90.0f);
  }

  private int[] candidateSupportPos(Object[] candidate) {
    return (int[]) candidate[1];
  }

  private int candidateFace(Object[] candidate) {
    return (Integer) candidate[2];
  }

  private Vec3 candidateHitVec(Object[] candidate) {
    return (Vec3) candidate[3];
  }

  private int[] candidatePlacedPos(Object[] candidate) {
    return (int[]) candidate[4];
  }

  private float sanitizePitch(float pitch, float fallbackPitch) {
    float safeFallback =
        clampFloat(Float.isNaN(fallbackPitch) ? 0.0f : fallbackPitch, -90.0f, 90.0f);
    if (Float.isNaN(pitch) || Float.isInfinite(pitch)) return safeFallback;
    return clampFloat(pitch, -90.0f, 90.0f);
  }

  private int floor(double value) {
    int i = (int) value;
    return value < i ? i - 1 : i;
  }

  private float clampFloat(float value, float min, float max) {
    return value < min ? min : (value > max ? max : value);
  }

  private float wrapAngle(float angle) {
    angle = angle % 360f;
    if (angle >= 180f) angle -= 360f;
    if (angle < -180f) angle += 360f;
    return angle;
  }

  private double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
    double dx = x1 - x2;
    double dy = y1 - y2;
    double dz = z1 - z2;
    return dx * dx + dy * dy + dz * dz;
  }

  private boolean posEquals(int[] a, int[] b) {
    return a != null && b != null && a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
  }

  private String posKey(int[] pos) {
    return pos[0] + "," + pos[1] + "," + pos[2];
  }

  private int[] posFromVec(Vec3 vec) {
    return new int[] {floor(vec.xCoord), floor(vec.yCoord), floor(vec.zCoord)};
  }

  private int[] posFromPos(net.minecraft.util.BlockPos pos) {
    return new int[] {pos.getX(), pos.getY(), pos.getZ()};
  }

  private String posKeyFromPos(net.minecraft.util.BlockPos pos) {
    return pos.getX() + "," + pos.getY() + "," + pos.getZ();
  }

  private long now() {
    return System.currentTimeMillis();
  }

  private ItemStack heldItem(Entity player) {
    return player instanceof EntityLivingBase ? ((EntityLivingBase) player).getHeldItem() : null;
  }

  private boolean isHoldingBlock(Entity player) {
    return isUsableBlockStack(heldItem(player));
  }

  private boolean isBlockStack(ItemStack stack) {
    return stack != null && stack.getItem() instanceof ItemBlock;
  }

  private String stackName(ItemStack stack) {
    if (stack == null || !isBlockStack(stack)) return "";
    Block block = ((ItemBlock) stack.getItem()).getBlock();
    return blockName(block);
  }

  private String faceName(int face) {
    if (face == 0) return "DOWN";
    if (face == 1) return "UP";
    if (face == 2) return "NORTH";
    if (face == 3) return "SOUTH";
    if (face == 4) return "WEST";
    return "EAST";
  }

  private int faceFromName(String name) {
    if (name == null) return -1;
    String upper = name.toUpperCase();
    if (upper.equals("DOWN")) return 0;
    if (upper.equals("UP")) return 1;
    if (upper.equals("NORTH")) return 2;
    if (upper.equals("SOUTH")) return 3;
    if (upper.equals("WEST")) return 4;
    if (upper.equals("EAST")) return 5;
    return -1;
  }

  private int[] offsetPos(int[] pos, int face) {
    if (face == 0) return new int[] {pos[0], pos[1] - 1, pos[2]};
    if (face == 1) return new int[] {pos[0], pos[1] + 1, pos[2]};
    if (face == 2) return new int[] {pos[0], pos[1], pos[2] - 1};
    if (face == 3) return new int[] {pos[0], pos[1], pos[2] + 1};
    if (face == 4) return new int[] {pos[0] - 1, pos[1], pos[2]};
    return new int[] {pos[0] + 1, pos[1], pos[2]};
  }

  private Vec3 playerPosition(Entity player) {
    return new Vec3(player.posX, player.posY, player.posZ);
  }

  private Vec3 renderPosition() {
    RenderManager renderManager = mc.getRenderManager();
    return new Vec3(
        ((IAccessorRenderManager) renderManager).getRenderPosX(),
        ((IAccessorRenderManager) renderManager).getRenderPosY(),
        ((IAccessorRenderManager) renderManager).getRenderPosZ());
  }

  private int[] getDisplaySize() {
    ScaledResolution resolution = new ScaledResolution(mc);
    return new int[] {resolution.getScaledWidth(), resolution.getScaledHeight()};
  }

  private Object[] raycastBlock(double distance, float yaw, float pitch) {
    if (mc.thePlayer == null || mc.theWorld == null) return null;
    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 lookVec = getLookVec(yaw, pitch);
    Vec3 targetPos =
        eyePos.addVector(
            lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
    MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyePos, targetPos, false, false, true);
    if (mop == null || mop.getBlockPos() == null || mop.hitVec == null) return null;
    net.minecraft.util.BlockPos blockPos = mop.getBlockPos();
    Vec3 blockVec = new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    Vec3 localHit =
        new Vec3(
            mop.hitVec.xCoord - blockPos.getX(),
            mop.hitVec.yCoord - blockPos.getY(),
            mop.hitVec.zCoord - blockPos.getZ());
    String face = mop.sideHit == null ? "" : mop.sideHit.getName().toUpperCase();
    return new Object[] {blockVec, localHit, face};
  }

  private boolean placeBlock(int x, int y, int z, String faceName, Vec3 hitAbs) {
    if (mc.thePlayer == null) return false;
    int direction = faceFromName(faceName);
    if (direction < 0) return false;
    ItemStack stack = heldItem(mc.thePlayer);
    if (!isUsableBlockStack(stack)) return false;
    net.minecraft.util.BlockPos pos = new net.minecraft.util.BlockPos(x, y, z);
    float fx = (float) Math.max(0.0, Math.min(1.0, hitAbs.xCoord - x));
    float fy = (float) Math.max(0.0, Math.min(1.0, hitAbs.yCoord - y));
    float fz = (float) Math.max(0.0, Math.min(1.0, hitAbs.zCoord - z));
    mc.thePlayer.sendQueue.addToSendQueue(
        new C08PacketPlayerBlockPlacement(pos, direction, stack, fx, fy, fz));
    return true;
  }

  private boolean autoPlaceOnPacketSent(net.minecraft.network.Packet<?> packet) {
    if (packet instanceof C03PacketPlayer) {
      if (packet instanceof C03PacketPlayer.C04PacketPlayerPosition
          || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
        hasLastSentServerPos = true;
        lastSentServerPosX = mc.thePlayer.posX;
        lastSentServerPosY = mc.thePlayer.posY;
        lastSentServerPosZ = mc.thePlayer.posZ;
      }
      return true;
    }
    if (packet instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) packet;
      if (c08.getPlacedBlockDirection() == 255) {
        if (shouldCancelAutoPlaceUseItem()) {
          suppressUse();
          return false;
        }
      } else {
        ItemStack stack = c08.getStack();
        if (stack != null && isBlockStack(stack)) {
          totalC08Counter++;
          if (!placingViaModule) manualC08InWindow = true;
        }
      }
    }
    return true;
  }

  private int getKeyCode(String name) {
    if (name == null) return -1;
    KeyBinding binding;
    switch (name) {
      case "forward":
        binding = mc.gameSettings.keyBindForward;
        break;
      case "back":
        binding = mc.gameSettings.keyBindBack;
        break;
      case "left":
        binding = mc.gameSettings.keyBindLeft;
        break;
      case "right":
        binding = mc.gameSettings.keyBindRight;
        break;
      case "jump":
        binding = mc.gameSettings.keyBindJump;
        break;
      case "sneak":
        binding = mc.gameSettings.keyBindSneak;
        break;
      case "sprint":
        binding = mc.gameSettings.keyBindSprint;
        break;
      case "drop":
        binding = mc.gameSettings.keyBindDrop;
        break;
      case "use":
        binding = mc.gameSettings.keyBindUseItem;
        break;
      case "attack":
        binding = mc.gameSettings.keyBindAttack;
        break;
      default:
        return -1;
    }
    return binding == null ? -1 : binding.getKeyCode();
  }

  private void setKeyPressed(String name, boolean pressed) {
    int code = getKeyCode(name);
    if (code < 0) return;
    KeyBindUtil.setKeyBindState(code, pressed);
  }

  private boolean isPressed(String name) {
    int code = getKeyCode(name);
    return code >= 0 && KeyBindUtil.isKeyDown(code);
  }

  private Vec3 playerMotion() {
    if (mc.thePlayer == null) return null;
    return new Vec3(mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ);
  }

  private Vec3 playerPositionLast(Entity player) {
    return new Vec3(player.lastTickPosX, player.lastTickPosY, player.lastTickPosZ);
  }

  private double[] renderRotations() {
    if (mc.thePlayer == null) return null;
    return new double[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
  }

  private int opposite(int face) {
    if (face == 0) return 1;
    if (face == 1) return 0;
    if (face == 2) return 3;
    if (face == 3) return 2;
    if (face == 4) return 5;
    return 4;
  }

  private int rotateY(int face) {
    if (face == 2) return 5;
    if (face == 5) return 3;
    if (face == 3) return 4;
    if (face == 4) return 2;
    return face;
  }

  private int rotateYCCW(int face) {
    if (face == 2) return 4;
    if (face == 4) return 3;
    if (face == 3) return 5;
    if (face == 5) return 2;
    return face;
  }

  private int facingFromYaw(float yaw) {
    int index = floor(yaw / 90.0 + 0.5) & 3;
    if (index == 0) return 3;
    if (index == 1) return 4;
    if (index == 2) return 2;
    return 5;
  }
}