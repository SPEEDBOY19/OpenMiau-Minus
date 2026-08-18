package miau.module.modules.misc.cheatdetector.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

public class KillauraCheck extends Check {
  private static final long COMBAT_WINDOW_TICKS = 70L;
  private static final long SESSION_RESET_TICKS = 140L;
  private static final int WINDOW_SIZE = 10;
  private static final float QUANTUM = 1.40625F;
  private static final float VL_LIMIT = 400.0F;
  private static final float VL_FADE_PER_TICK = 0.5F;

  private static final int EAT_TIMEOUT = 33;
  private static final int MIN_USE_TIME = 6;
  private static final int CONSUME_FAIL_VL = 8;

  private static final float BURST_STEP_MIN = 7.0F;
  private static final float BURST_QUIET = 2.5F;
  private static final int BURST_MAX_TICKS = 7;
  private static final float BURST_SUM_MIN = 20.0F;
  private static final float SNAP_PRE_ERROR_MIN = 20.0F;
  private static final int SNAP_MIN_HITS = 3;
  private static final float SNAP_VL = 90.0F;
  private static final float RETURN_VL = 55.0F;
  private static final long RETURN_PAIR_TICKS = 8L;

  private static final int TRACK_WINDOW = 24;
  private static final float TRACK_RATIO = 0.85F;
  private static final float TRACK_LOS_MIN = 2.5F;
  private static final float TRACK_LOS_MAX = 45.0F;
  private static final double TRACK_MIN_DIST = 2.2D;
  private static final float TRACK_VL = 80.0F;

  private static final double MOVE_MIN_SPEED = 0.15D;
  private static final double MOVE_MAX_SPEED = 0.45D;
  private static final double MOVE_FLAT_DY = 0.001D;
  private static final double MOVE_SMOOTH_ACCEL = 0.022D;
  private static final int MOVE_WINDOW = 12;
  private static final float MOVE_MEAN_LIMIT = 7.5F;
  private static final float MOVE_DESYNC_RESIDUAL = 8.0F;
  private static final float MOVE_VL = 70.0F;
  private static final float LOCK_RESIDUAL = 13.0F;
  private static final int LOCK_HITS = 3;
  private static final float MOVE_LOCK_VL = 85.0F;
  private static final double SPRINT_ACCEL = 0.08D;
  private static final double SPRINT_MIN_SPEED = 0.25D;
  private static final float SPRINT_OFFSET = 62.0F;
  private static final int SPRINT_HITS = 4;
  private static final float MOVE_SPRINT_VL = 85.0F;
  private static final int MOVE_DECAY_TICKS = 40;

  private static final double TARGET_RANGE_SQ = 36.0D;
  private static final double HITBOX_HALF_WIDTH = 0.4D;
  private static final int TRAIL_LEN = 5;

  private float aimVl;
  private boolean failedKillaura;
  private long lastSwingTick = Long.MIN_VALUE;
  private float lastYaw;
  private float lastPitch;
  private boolean hasRotation;
  private final List<Float> yawChangeWindow = new ArrayList<Float>();
  private int snapStreak;
  private int burstTicks;
  private float burstSum;
  private float burstDir;
  private float preBurstYaw;
  private int quietTicks;
  private int snapHits;
  private int snapMisses;
  private long lastSnapHitTick = Long.MIN_VALUE;
  private UUID lastTargetId;
  private float lastBearing = Float.NaN;
  private int trackSamples;
  private int trackTicks;
  private double lastVelX;
  private double lastVelZ;
  private double lastMoveY;
  private boolean hasVel;
  private int moveSamples;
  private int moveDesyncTicks;
  private float residualSum;
  private int lockDesync;
  private int sprintDesync;
  private int moveTickCounter;
  private int useItemTicks;
  private long lastEatTick;
  private int consumeVl;

  private static final Map<UUID, Trail> TRAILS = new HashMap<UUID, Trail>();

  private static final class Trail {
    final double[] x = new double[TRAIL_LEN];
    final double[] z = new double[TRAIL_LEN];
    long lastTick = Long.MIN_VALUE;
    int size;

    void push(double px, double pz, long tick) {
      if (tick == this.lastTick && this.size > 0) {
        return;
      }
      System.arraycopy(this.x, 0, this.x, 1, TRAIL_LEN - 1);
      System.arraycopy(this.z, 0, this.z, 1, TRAIL_LEN - 1);
      this.x[0] = px;
      this.z[0] = pz;
      this.lastTick = tick;
      if (this.size < TRAIL_LEN) {
        ++this.size;
      }
    }
  }

  @Override
  public String getName() {
    return "Killaura";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.ridingEntity != null) {
      return;
    }

    UUID uuid = player.getUniqueID();
    long tick = mc.theWorld.getTotalWorldTime();
    trail(uuid).push(player.posX, player.posZ, tick);
    trail(mc.thePlayer.getUniqueID()).push(mc.thePlayer.posX, mc.thePlayer.posZ, tick);

    this.consumeComponent(player, tick);

    if (player.isSwingInProgress) {
      lastSwingTick = tick;
    }

    float yaw = player.rotationYaw;
    float pitch = player.rotationPitch;
    if (!hasRotation) {
      lastYaw = yaw;
      lastPitch = pitch;
      hasRotation = true;
      return;
    }
    float prevYaw = lastYaw;
    float yawChange = wrapDegrees(yaw - lastYaw);
    float pitchChange = wrapDegrees(pitch - lastPitch);
    lastYaw = yaw;
    lastPitch = pitch;

    // Teleport/lag guard.
    double moveX = player.posX - player.lastTickPosX;
    double moveY = player.posY - player.lastTickPosY;
    double moveZ = player.posZ - player.lastTickPosZ;
    if (moveX * moveX + moveZ * moveZ > 25.0D) {
      yawChangeWindow.clear();
      this.resetBurst();
      lastBearing = Float.NaN;
      lastTargetId = null;
      hasVel = false;
      moveSamples = 0;
      moveDesyncTicks = 0;
      residualSum = 0.0F;
      return;
    }

    if (lastSwingTick == Long.MIN_VALUE
        || tick < lastSwingTick
        || tick - lastSwingTick > COMBAT_WINDOW_TICKS) {
      if (lastSwingTick != Long.MIN_VALUE && tick - lastSwingTick > SESSION_RESET_TICKS) {
        this.resetSession();
      }
      return;
    }

    float absYawChange = Math.abs(yawChange);
    float absPitchChange = Math.abs(pitchChange);
    if (absYawChange != 0.0F || absPitchChange != 0.0F) {
      yawChangeWindow.add(absYawChange);
      if (yawChangeWindow.size() >= WINDOW_SIZE) {
        this.analyzeWindow(player, yawChangeWindow);
        yawChangeWindow.clear();
      }
    }

    List<EntityPlayer> targets = this.targetsNear(player, tick);
    this.burstMachine(player, tick, yawChange, prevYaw, targets);
    this.trackComponent(player, yaw, targets);
    this.movementComponent(player, moveX, moveY, moveZ, yaw, targets);

    if (aimVl > VL_LIMIT) {
      flag(player, "vl: " + aimVl);
      aimVl = 360.0F;
    }
    if (aimVl > 0.0F) {
      aimVl = Math.max(0.0F, aimVl - VL_FADE_PER_TICK);
    }
  }

  private void analyzeWindow(EntityPlayer player, List<Float> window) {
    float first = window.get(0);
    float old = first;
    int machineKnown = 0;
    int constant = 0;
    int robotized = 0;
    int bigUp = 0;
    int bigDown = 0;

    for (float change : window) {
      float r = Math.abs(change - first);
      float diff = change - old;
      if (r < QUANTUM * 1.5F && change > QUANTUM * 2.0F) {
        ++robotized;
      }
      if (r < QUANTUM && change > QUANTUM * 3.0F) {
        ++machineKnown;
      }
      if (r < QUANTUM * 0.5F && change > QUANTUM * 2.5F) {
        ++constant;
      }
      if (diff > 12.0F) {
        ++bigUp;
      }
      if (diff < -12.0F) {
        ++bigDown;
      }
      old = change;
    }

    if (machineKnown > 8) {
      this.addVl(100.0F, "heuristic(aim)");
    }
    if (constant > 6) {
      this.addVl(65.0F, "heuristic(constant)");
    }
    if (robotized > 8) {
      this.addVl(50.0F, "heuristic(sync)");
    }

    if (bigUp > 1 && bigDown > 1 && bigUp + bigDown > 4) {
      ++snapStreak;
      if (snapStreak > 2) {
        this.addVl(55.0F, "pattern(snap)");
      }
    } else {
      snapStreak = 0;
    }
  }

  private void burstMachine(
      EntityPlayer player, long tick, float yawChange, float prevYaw, List<EntityPlayer> targets) {
    float absYaw = Math.abs(yawChange);

    if (burstTicks > 0) {
      boolean sameDir = yawChange * burstDir >= 0.0F;
      if (absYaw < BURST_QUIET) {
        if (burstSum >= BURST_SUM_MIN) {
          this.evaluateBurst(player, tick, targets);
        }
        this.resetBurst();
        quietTicks = 1;
      } else if (sameDir) {
        ++burstTicks;
        burstSum += absYaw;
        if (burstTicks > BURST_MAX_TICKS) {
          burstTicks = -1;
        }
      } else if (absYaw > BURST_STEP_MIN) {
        burstTicks = 1;
        burstSum = absYaw;
        burstDir = yawChange;
        preBurstYaw = prevYaw;
        quietTicks = 0;
      } else {
        this.resetBurst();
        quietTicks = 0;
      }
    } else if (burstTicks == -1) {
      if (absYaw < BURST_QUIET) {
        this.resetBurst();
        quietTicks = 1;
      }
    } else {
      if (absYaw > BURST_STEP_MIN && quietTicks >= 2) {
        burstTicks = 1;
        burstSum = absYaw;
        burstDir = yawChange;
        preBurstYaw = prevYaw;
        quietTicks = 0;
      } else if (absYaw < BURST_QUIET) {
        ++quietTicks;
      } else {
        quietTicks = 0;
      }
    }
  }

  private void evaluateBurst(EntityPlayer player, long tick, List<EntityPlayer> targets) {
    if (targets.isEmpty()) {
      return;
    }
    float bestErr = Float.MAX_VALUE;
    float bestPre = 0.0F;
    float bestPreInside = Float.MAX_VALUE;
    for (EntityPlayer target : targets) {
      Trail trail = trail(target.getUniqueID());
      float err = this.minInsideError(player, trail, lastYaw);
      if (err < bestErr) {
        bestErr = err;
        float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);
        bestPre = Math.abs(wrapDegrees(preBurstYaw - bearingNow));
      }
      bestPreInside = Math.min(bestPreInside, this.minInsideError(player, trail, preBurstYaw));
    }

    if (bestErr <= QUANTUM && bestPre > SNAP_PRE_ERROR_MIN) {
      ++snapHits;
      lastSnapHitTick = tick;
      if (snapHits >= SNAP_MIN_HITS && snapHits > snapMisses) {
        this.addVl(SNAP_VL, "silent(snap)");
        flag(player, "silent(snap)");
      }
    } else if (bestPreInside <= QUANTUM && bestErr > SNAP_PRE_ERROR_MIN * 0.75F) {
      if (lastSnapHitTick != Long.MIN_VALUE && tick - lastSnapHitTick <= RETURN_PAIR_TICKS) {
        this.addVl(RETURN_VL, "silent(return)");
      }
    } else if (bestPre > SNAP_PRE_ERROR_MIN && bestErr > QUANTUM * 2.0F) {
      ++snapMisses;
    }
  }

  private void trackComponent(EntityPlayer player, float yaw, List<EntityPlayer> targets) {
    EntityPlayer target = null;
    double bestDistSq = Double.MAX_VALUE;
    for (EntityPlayer candidate : targets) {
      double dx = candidate.posX - player.posX;
      double dy = candidate.posY - player.posY;
      double dz = candidate.posZ - player.posZ;
      double distSq = dx * dx + dy * dy + dz * dz;
      if (distSq < bestDistSq) {
        bestDistSq = distSq;
        target = candidate;
      }
    }
    if (target == null) {
      lastTargetId = null;
      lastBearing = Float.NaN;
      return;
    }

    UUID tid = target.getUniqueID();
    Trail trail = trail(tid);
    float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);
    if (tid.equals(lastTargetId) && !Float.isNaN(lastBearing)) {
      float losDelta = Math.abs(wrapDegrees(bearingNow - lastBearing));
      double dx = target.posX - player.posX;
      double dz = target.posZ - player.posZ;
      double horizDist = Math.sqrt(dx * dx + dz * dz);
      if (losDelta > TRACK_LOS_MIN && losDelta < TRACK_LOS_MAX && horizDist >= TRACK_MIN_DIST) {
        ++trackSamples;
        if (this.minInsideError(player, trail, yaw) <= QUANTUM * 0.5F) {
          ++trackTicks;
        }
        if (trackSamples >= TRACK_WINDOW) {
          if ((float) trackTicks >= TRACK_RATIO * (float) trackSamples) {
            this.addVl(TRACK_VL, "silent(track) " + trackTicks + "/" + trackSamples);
          }
          trackSamples = 0;
          trackTicks = 0;
        }
      }
    }
    lastTargetId = tid;
    lastBearing = bearingNow;
  }

  private void movementComponent(
      EntityPlayer player,
      double moveX,
      double moveY,
      double moveZ,
      float yaw,
      List<EntityPlayer> targets) {
    if (++moveTickCounter >= MOVE_DECAY_TICKS) {
      moveTickCounter = 0;
      lockDesync = Math.max(0, lockDesync - 1);
      sprintDesync = Math.max(0, sprintDesync - 1);
    }

    boolean flat = hasVel && Math.abs(moveY) < MOVE_FLAT_DY && Math.abs(lastMoveY) < MOVE_FLAT_DY;
    boolean haveAccel = hasVel;
    double ax = moveX - lastVelX;
    double az = moveZ - lastVelZ;
    lastVelX = moveX;
    lastVelZ = moveZ;
    lastMoveY = moveY;
    hasVel = true;
    if (!haveAccel) {
      return;
    }
    double accel = Math.sqrt(ax * ax + az * az);
    double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);

    if (!flat || player.hurtTime > 0 || speed < MOVE_MIN_SPEED || speed > MOVE_MAX_SPEED) {
      return;
    }
    Block ground =
        mc.theWorld
            .getBlockState(new BlockPos(player.posX, player.posY - 0.5D, player.posZ))
            .getBlock();
    if (ground == Blocks.ice || ground == Blocks.packed_ice) {
      return;
    }

    float moveBearing = (float) Math.toDegrees(Math.atan2(-moveX, moveZ));
    float offset = wrapDegrees(moveBearing - yaw);
    float residual = bucketResidual(offset);

    if (player.isSprinting()
        && speed > SPRINT_MIN_SPEED
        && accel < SPRINT_ACCEL
        && Math.abs(offset) > SPRINT_OFFSET) {
      ++sprintDesync;
      if (sprintDesync >= SPRINT_HITS) {
        this.addVl(MOVE_SPRINT_VL, "movement(sprint) " + (int) offset + (char) 176);
        sprintDesync -= SPRINT_HITS;
      }
    }

    if (accel > MOVE_SMOOTH_ACCEL) {
      return;
    }

    if (residual > LOCK_RESIDUAL) {
      for (EntityPlayer target : targets) {
        if (this.minInsideError(player, trail(target.getUniqueID()), yaw) <= QUANTUM) {
          ++lockDesync;
          if (lockDesync >= LOCK_HITS) {
            this.addVl(MOVE_LOCK_VL, "movement(lock) " + (int) residual + (char) 176);
            lockDesync -= LOCK_HITS;
          }
          break;
        }
      }
    }

    ++moveSamples;
    residualSum += residual;
    if (residual > MOVE_DESYNC_RESIDUAL) {
      ++moveDesyncTicks;
    }
    if (moveSamples >= MOVE_WINDOW) {
      float mean = residualSum / (float) moveSamples;
      if (mean > MOVE_MEAN_LIMIT) {
        this.addVl(
            MOVE_VL,
            "movement(fix) mean="
                + String.format("%.1f", mean)
                + (char) 176
                + " hard="
                + moveDesyncTicks
                + "/"
                + moveSamples);
      }
      moveSamples = 0;
      moveDesyncTicks = 0;
      residualSum = 0.0F;
    }
  }

  private static float bucketResidual(float offset) {
    float nearest = 45.0F * Math.round(offset / 45.0F);
    return Math.abs(wrapDegrees(offset - nearest));
  }

  private void consumeComponent(EntityPlayer player, long tick) {
    ItemStack heldItem = player.getHeldItem();
    boolean isUsingItem = player.isUsingItem();
    boolean isConsumable = heldItem != null && isConsumable(heldItem.getItem());
    boolean isAttacking = player.swingProgressInt > 0;

    if (isUsingItem && isConsumable) {
      ++useItemTicks;
    } else {
      if (useItemTicks > 0) {
        lastEatTick = tick;
      }
      useItemTicks = 0;
    }

    long sinceLastEat = tick - lastEatTick;
    if (isAttacking && useItemTicks > MIN_USE_TIME && sinceLastEat < EAT_TIMEOUT && isConsumable) {
      ++consumeVl;
      if (consumeVl >= CONSUME_FAIL_VL) {
        flag(player, "vl: " + this.consumeVl);
        consumeVl = 0;
      }
    } else if (consumeVl > 0) {
      --consumeVl;
    }
  }

  private List<EntityPlayer> targetsNear(EntityPlayer attacker, long tick) {
    List<EntityPlayer> out = new ArrayList<EntityPlayer>();
    for (EntityPlayer p : mc.theWorld.playerEntities) {
      if (p == attacker || p == mc.thePlayer || p.isDead || p.isSpectator()) {
        continue;
      }
      double dx = p.posX - attacker.posX;
      double dy = p.posY - attacker.posY;
      double dz = p.posZ - attacker.posZ;
      if (dx * dx + dy * dy + dz * dz > TARGET_RANGE_SQ) {
        continue;
      }
      trail(p.getUniqueID()).push(p.posX, p.posZ, tick);
      out.add(p);
    }
    return out;
  }

  private float minInsideError(EntityPlayer attacker, Trail trail, float yaw) {
    float best = Float.MAX_VALUE;
    for (int i = 0; i < trail.size; ++i) {
      double dx = trail.x[i] - attacker.posX;
      double dz = trail.z[i] - attacker.posZ;
      double horizDist = Math.sqrt(dx * dx + dz * dz);
      if (horizDist < 0.5D) {
        continue;
      }
      float bearing = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
      float err = Math.abs(wrapDegrees(yaw - bearing));
      float halfWidth = (float) Math.toDegrees(Math.atan2(HITBOX_HALF_WIDTH, horizDist));
      best = Math.min(best, Math.max(0.0F, err - halfWidth));
    }
    return best;
  }

  private static float bearingTo(EntityPlayer attacker, double x, double z) {
    double dx = x - attacker.posX;
    double dz = z - attacker.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private void addVl(float vl, String reason) {
    aimVl += vl;
  }

  private void resetBurst() {
    burstTicks = 0;
    burstSum = 0.0F;
    burstDir = 0.0F;
  }

  private void resetSession() {
    this.resetBurst();
    quietTicks = 0;
    snapHits = 0;
    snapMisses = 0;
    lastSnapHitTick = Long.MIN_VALUE;
    trackSamples = 0;
    trackTicks = 0;
    lastTargetId = null;
    lastBearing = Float.NaN;
    hasVel = false;
    moveSamples = 0;
    moveDesyncTicks = 0;
    residualSum = 0.0F;
    lockDesync = 0;
    sprintDesync = 0;
    moveTickCounter = 0;
  }

  private static Trail trail(UUID uuid) {
    return TRAILS.computeIfAbsent(uuid, k -> new Trail());
  }

  private boolean isConsumable(Item item) {
    return item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBucketMilk;
  }

  private static float wrapDegrees(float angle) {
    angle %= 360.0F;
    if (angle >= 180.0F) {
      angle -= 360.0F;
    }
    if (angle < -180.0F) {
      angle += 360.0F;
    }
    return angle;
  }
}
