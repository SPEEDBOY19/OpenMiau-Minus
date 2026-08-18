package miau.module.modules.movement;

import com.google.common.base.CaseFormat;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class NoFall extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final TimerUtil packetDelayTimer = new TimerUtil();
  private final TimerUtil scoreboardResetTimer = new TimerUtil();
  private boolean slowFalling = false;
  private boolean lastOnGround = false;
  private int lastMlgSlot = -1;
  private boolean mlgPlaced = false;
  public final ModeProperty mode =
      new ModeProperty(
          "mode", 0, new String[] {"PACKET", "BLINK", "NO_GROUND", "SPOOF", "LEGIT", "VULCAN", "TIMER"});
  public final FloatProperty distance = new FloatProperty("distance", 3.0F, 0.0F, 20.0F);
  public final IntProperty delay = new IntProperty("delay", 0, 0, 10000);

  public boolean canTrigger() {
    return this.scoreboardResetTimer.hasTimeElapsed(3000)
        && this.packetDelayTimer.hasTimeElapsed(this.delay.getValue().longValue());
  }

  private final TimerUtil timerTickTimer = new TimerUtil();
  private int timerTicks = 0;
  private int timerSettick = 0;
  private double timerRepdist = 0;

  public NoFall() {
    super("NoFall", false);
  }

  @EventTarget(Priority.HIGH)
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE
        && event.getPacket() instanceof S08PacketPlayerPosLook) {
      this.onDisabled();
    } else if (this.isEnabled() && event.getType() == EventType.SEND && !event.isCancelled()) {
      if (event.getPacket() instanceof C03PacketPlayer) {
        C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
        switch (this.mode.getValue()) {
          case 0:
            if (this.slowFalling) {
              this.slowFalling = false;
              ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
            } else if (!packet.isOnGround()) {
              AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
              if (PlayerUtil.canFly(this.distance.getValue())
                  && !PlayerUtil.checkInWater(aabb)
                  && this.canTrigger()) {
                this.packetDelayTimer.reset();
                this.slowFalling = true;
                ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.5F;
              }
            }
            break;
          case 1:
            boolean allowed =
                !mc.thePlayer.isOnLadder()
                    && !mc.thePlayer.capabilities.allowFlying
                    && mc.thePlayer.hurtTime == 0;
            if (Miau.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
              if (this.lastOnGround
                  && !packet.isOnGround()
                  && allowed
                  && PlayerUtil.canFly(this.distance.getValue().intValue())
                  && mc.thePlayer.motionY < 0.0) {
                Miau.blinkManager.setBlinkState(false, Miau.blinkManager.getBlinkingModule());
                Miau.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
              }
            } else if (!allowed) {
              Miau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
              ChatUtil.display("%s%s: &cFailed player check!&r", this.getName());
            } else if (PlayerUtil.checkInWater(
                mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
              Miau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
              ChatUtil.display("%s%s: &cFailed void check!&r", this.getName());
            } else if (packet.isOnGround()) {
              for (Packet<?> blinkedPacket : Miau.blinkManager.blinkedPackets) {
                if (blinkedPacket instanceof C03PacketPlayer) {
                  ((IAccessorC03PacketPlayer) blinkedPacket).setOnGround(true);
                }
              }
              Miau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
              this.packetDelayTimer.reset();
            }
            this.lastOnGround = packet.isOnGround() && allowed && this.canTrigger();
            break;
          case 2:
            ((IAccessorC03PacketPlayer) packet).setOnGround(false);
            break;
          case 3:
            if (!packet.isOnGround()) {
              AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
              if (PlayerUtil.canFly(this.distance.getValue())
                  && !PlayerUtil.checkInWater(aabb)
                  && this.canTrigger()) {
                this.packetDelayTimer.reset();
                ((IAccessorC03PacketPlayer) packet).setOnGround(true);
                mc.thePlayer.fallDistance = 0.0F;
              }
            }
          case 5:
            if (!packet.isOnGround() && mc.thePlayer.fallDistance > 7.0f && this.canTrigger()) {
              this.packetDelayTimer.reset();
              ((IAccessorC03PacketPlayer) packet).setOnGround(true);
              mc.thePlayer.fallDistance = 0.0F;
              mc.thePlayer.motionY = 0.0;
            }
            break;
          case 6:
            float fallDist = mc.thePlayer.fallDistance;
            if (fallDist >= 1.51F && this.timerRepdist == 0.0 && !this.isOverVoid()) {
              ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.45F;
              PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
              this.timerSettick = this.timerTicks + 1;
              this.timerRepdist = fallDist;
            }
            if (fallDist - this.timerRepdist >= 1.51F && !this.isOverVoid()) {
              ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.6F;
              PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
              this.timerSettick = this.timerTicks + 5;
              this.timerRepdist = fallDist;
            }
            if (mc.thePlayer.onGround) {
              ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
              this.timerRepdist = 0.0;
            }
            break;
        }
      }
    }
  }

  @EventTarget(Priority.HIGHEST)
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      this.timerTicks++;
      if (ServerUtil.hasPlayerCountInfo()) {
        this.scoreboardResetTimer.reset();
      }
      if (this.mode.getValue() == 0 && this.slowFalling) {
        PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
        mc.thePlayer.fallDistance = 0.0F;
      }
      if (this.mode.getValue() == 4) {
        this.handleLegitMlg();
      }
      if (this.mode.getValue() == 6) {
        if (this.timerTicks == this.timerSettick) {
          ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        }
        if (this.timerTicks >= this.timerSettick) {
          this.timerSettick = 0;
        }
      }
    }
  }

  private boolean isOverVoid() {
    if (mc.thePlayer == null || mc.theWorld == null) return true;
    int x = MathHelper.floor_double(mc.thePlayer.posX);
    int z = MathHelper.floor_double(mc.thePlayer.posZ);
    for (int y = MathHelper.floor_double(mc.thePlayer.posY); y > -1; y--) {
      if (!mc.theWorld.isAirBlock(new BlockPos(x, y, z))) {
        return false;
      }
    }
    return true;
  }

  private void handleLegitMlg() {
    if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;
    if (mc.thePlayer.onGround
        || mc.thePlayer.capabilities.isFlying
        || mc.thePlayer.isInWater()
        || mc.thePlayer.isOnLadder()) {
      this.resetLegitMlg();
      return;
    }
    if (mc.thePlayer.fallDistance < this.distance.getValue() || mc.thePlayer.motionY >= -0.1D)
      return;

    int waterSlot = this.findWaterBucketSlot();
    if (waterSlot == -1) return;
    BlockPos target = this.findMlgTarget();
    if (target == null) return;

    if (this.lastMlgSlot == -1) {
      this.lastMlgSlot = mc.thePlayer.inventory.currentItem;
    }
    mc.thePlayer.inventory.currentItem = waterSlot;
    mc.playerController.updateController();
    mc.thePlayer.rotationPitch = 90.0F;

    if (!this.mlgPlaced
        && mc.thePlayer.getDistance(
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D)
            <= mc.playerController.getBlockReachDistance() + 1.5F) {
      Vec3 hitVec = new Vec3(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
      ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
      if (stack != null
          && mc.playerController.onPlayerRightClick(
              mc.thePlayer, mc.theWorld, stack, target, EnumFacing.UP, hitVec)) {
        mc.thePlayer.swingItem();
        this.mlgPlaced = true;
      }
    }
  }

  private int findWaterBucketSlot() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
      if (stack != null && stack.getItem() == Items.water_bucket) {
        return i;
      }
    }
    return -1;
  }

  private BlockPos findMlgTarget() {
    BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    for (int y = 1; y <= 6; y++) {
      BlockPos pos = playerPos.down(y);
      if (!mc.theWorld.isAirBlock(pos) && mc.theWorld.isAirBlock(pos.up())) {
        return pos;
      }
    }
    MovingObjectPosition ray =
        mc.theWorld.rayTraceBlocks(
            new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ),
            new Vec3(
                mc.thePlayer.posX,
                mc.thePlayer.posY - mc.playerController.getBlockReachDistance() - 2.0D,
                mc.thePlayer.posZ),
            false,
            true,
            false);
    return ray != null && ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        ? ray.getBlockPos()
        : null;
  }

  private void resetLegitMlg() {
    if (this.lastMlgSlot != -1 && mc.thePlayer != null) {
      mc.thePlayer.inventory.currentItem = this.lastMlgSlot;
      mc.playerController.updateController();
    }
    this.lastMlgSlot = -1;
    this.mlgPlaced = false;
  }

  @Override
  public void onDisabled() {
    this.lastOnGround = false;
    this.resetLegitMlg();
    Miau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
    if (this.slowFalling) {
      this.slowFalling = false;
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
    }
    this.timerTicks = 0;
    this.timerSettick = 0;
    this.timerRepdist = 0.0;
    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
  }

  @Override
  public void verifyValue(String mode) {
    if (this.isEnabled()) {
      this.onDisabled();
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }
}
