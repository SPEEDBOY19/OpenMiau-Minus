package miau.module.modules.combat;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.movement.Blink;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.TextProperty;
import miau.util.client.ChatUtil;
import miau.util.misc.BackTrackUtil;
import miau.util.misc.SomeUtil;
import miau.util.network.BlinkUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.util.Vec3;

public class SmartBlinker extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final ModeProperty tagMode =
      new ModeProperty("TagMode", 0, new String[] {"Normal", "MaxTime", "Custom", "PacketCount"});
  private final TextProperty customTag = new TextProperty("CustomTag", "");
  private final FloatProperty range = new FloatProperty("Range", 2f, 4f, 0f, 6f);
  private final BooleanProperty limitBlinkTime = new BooleanProperty("BlinkTime", true);
  private final BooleanProperty limitMoveRange = new BooleanProperty("MoveRange", false);
  private final IntProperty maxBlinkTime =
      new IntProperty("MaxBlinkTime", 500, 0, 5000, () -> limitBlinkTime.getValue());
  private final FloatProperty maxMoveRangePerBlink =
      new FloatProperty("MaxMoveRangePerBlink", 5f, 0f, 50f, () -> limitMoveRange.getValue());
  private final IntProperty minDelayBetweenCancelBlink =
      new IntProperty("MinDelayBetweenPerCancelBlink", 0, 0, 5000);
  private final IntProperty delay = new IntProperty("Delay", 1000, 0, 5000);
  private final BooleanProperty stopOnAttack = new BooleanProperty("StopOnAttack", true);
  private final BooleanProperty stopOnPlaceBlock = new BooleanProperty("StopOnPlaceBlock", false);
  private final BooleanProperty stopOnHurt = new BooleanProperty("StopOnHurt", false);
  private final BooleanProperty stopOnLag = new BooleanProperty("StopOnServerTP", true);
  private final BooleanProperty blockAllPackets = new BooleanProperty("BlockAllPackets", false);
  private final BooleanProperty tips = new BooleanProperty("Tips", true);
  private final BooleanProperty debugger = new BooleanProperty("Debugger", false);

  private final TimerUtil delayTimer = new TimerUtil();
  private final TimerUtil delayTimer2 = new TimerUtil();
  private EntityLivingBase bufferTarget = null;
  private boolean isBlinking = false;
  private boolean lastBlinkState = false;
  private long blinkStartTime = 0L;
  private Vec3 lastPlayerPos = null;
  private float totalMoveDistance = 0f;

  private int actualDelay = 0;

  public SmartBlinker() {
    super("SmartBlinker", false);
  }

  @EventTarget
  public void onGameLoop(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null) return;

    if (!isBlinking) {
      actualDelay = delay.getValue();
    }

    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (killAura != null && killAura.getTarget() instanceof EntityLivingBase) {
      bufferTarget = killAura.getTarget();
    }

    Blink blink = (Blink) Miau.moduleManager.modules.get(Blink.class);
    if (isBlinking && blink != null && blink.isEnabled()) {
      blink.toggle();
      ChatUtil.display("Don't enable your Blink Module When This Module Is Working!");
    }

    if (isBlinking && limitMoveRange.getValue()) {
      Vec3 currentPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

      if (lastPlayerPos != null) {
        float segmentDistance = (float) lastPlayerPos.distanceTo(currentPos);
        totalMoveDistance += segmentDistance;

        if (totalMoveDistance >= maxMoveRangePerBlink.getValue()) {
          debugMessage(
              String.format(
                  "MaxMoveRange reached (%.3f blocks), stopping...", totalMoveDistance));
          reset();
          return;
        }
      }
      lastPlayerPos = currentPos;
    }

    if (isBlinking
        && blinkStartTime > 0
        && limitBlinkTime.getValue()
        && System.currentTimeMillis() - blinkStartTime >= maxBlinkTime.getValue()) {
      debugMessage(
          String.format(
              "MaxBlinkTime reached, stopping... Duration: %dms",
              System.currentTimeMillis() - blinkStartTime));
      reset();
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    Packet<?> packet = event.getPacket();

    if (((stopOnAttack.getValue() && isAttackPacket(packet))
            || (stopOnPlaceBlock.getValue() && isPlaceBlockPacket(packet))
            || (stopOnLag.getValue() && isServerLagPacket(packet)))
        && isBlinking) {
      debugMessage(
          String.format(
              "StopWorking, DuringTime:%dms, MoveDistance:%.3f blocks",
              System.currentTimeMillis() - blinkStartTime, totalMoveDistance));
      reset();
      return;
    }

    if (shouldBlink()) {
      if (!isBlinking) {
        Vec3 startPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        lastPlayerPos = startPos;
        totalMoveDistance = 0f;
        debugMessage(
            String.format(
                "Blink started at position: (%.2f, %.2f, %.2f)",
                startPos.xCoord, startPos.yCoord, startPos.zCoord));
      }

      BlinkUtil.blink(
          event,
          true,
          blockAllPackets.getValue(),
          p -> p instanceof S14PacketEntity,
          Integer.MAX_VALUE,
          null);
      if (!lastBlinkState) {
        debugMessage("StartWorking");
        blinkStartTime = System.currentTimeMillis();
      }
      isBlinking = true;
      lastBlinkState = true;
    } else if (!shouldBlink() && isBlinking) {
      long duration = System.currentTimeMillis() - blinkStartTime;
      debugMessage(
          String.format(
              "StopWorking, DuringTime:%dms, TotalMoveDistance:%.3f blocks",
              duration, totalMoveDistance));
      reset();
    }
  }

  private boolean shouldBlink() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    boolean killAuraIsWorking =
        killAura != null && killAura.isEnabled() && killAura.getTarget() != null;
    if (stopOnHurt.getValue() && SomeUtil.isHurting()) return false;
    if (!killAuraIsWorking) return false;
    if (bufferTarget == null) return false;
    if (!delayTimer.hasTimeElapsed(actualDelay)) return false;

    double distance = BackTrackUtil.getDistanceToEntityBox(bufferTarget);
    boolean withinRange = distance >= range.getValue() && distance <= range.getSecondValue();

    if (isBlinking) {
      boolean continueBlink = withinRange;

      if (limitBlinkTime.getValue()) {
        continueBlink =
            continueBlink
                && blinkStartTime > 0
                && System.currentTimeMillis() - blinkStartTime < maxBlinkTime.getValue();
      }

      if (limitMoveRange.getValue()) {
        continueBlink = continueBlink && totalMoveDistance < maxMoveRangePerBlink.getValue();
      }

      return continueBlink;
    }

    return withinRange;
  }

  private void debugMessage(String msg) {
    if (debugger.getValue()) {
      ChatUtil.display(msg);
    }
  }

  private void reset() {
    if (!delayTimer2.hasTimeElapsed(minDelayBetweenCancelBlink.getValue())) return;
    delayTimer.reset();
    delayTimer2.reset();
    BlinkUtil.unblink();
    isBlinking = false;
    blinkStartTime = 0L;
    lastBlinkState = false;
    lastPlayerPos = null;
    totalMoveDistance = 0f;
  }

  @Override
  public void onEnabled() {
    if (tips.getValue()) {
      ChatUtil.display(
          "If you open this module, when module is working, the blink module will be automatically disabled");
    }
  }

  @Override
  public String[] getSuffix() {
    String tagModeString = tagMode.getModeString();
    switch (tagModeString) {
      case "Normal":
        return new String[] {String.format("%s - %s", range.getValue(), range.getSecondValue())};
      case "MaxTime":
        return new String[] {maxBlinkTime.getValue() + "ms"};
      case "Custom":
        return new String[] {customTag.getValue()};
      case "PacketCount":
        return new String[] {String.valueOf(BlinkUtil.getPacketCount())};
      default:
        return new String[0];
    }
  }

  private static boolean isAttackPacket(Packet<?> packet) {
    return packet instanceof C02PacketUseEntity
        && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK;
  }

  private static boolean isPlaceBlockPacket(Packet<?> packet) {
    return packet instanceof C08PacketPlayerBlockPlacement;
  }

  private static boolean isServerLagPacket(Packet<?> packet) {
    return packet instanceof S08PacketPlayerPosLook;
  }
}
