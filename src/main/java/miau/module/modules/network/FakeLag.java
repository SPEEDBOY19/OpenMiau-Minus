package miau.module.modules.network;

import java.awt.Color;
import java.util.Iterator;
import java.util.LinkedList;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import miau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C12PacketUpdateSign;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import miau.event.impl.AttackEvent;

public class FakeLag extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"Latence", "Dynamic", "Adaptive"});

  public final IntProperty delay =
      new IntProperty("delay", 550, 0, 1000, () -> mode.getValue() == 0);
  public final IntProperty recoilTime =
      new IntProperty("recoil-time", 750, 0, 2000, () -> mode.getValue() == 0);
  public final FloatProperty allowedDistToEnemy =
      new FloatProperty("allowed-dist", 1.5F, 3.5F, 0.0F, 6.0F, () -> mode.getValue() == 0);
  public final BooleanProperty blinkOnAction =
      new BooleanProperty("blink-on-action", true, () -> mode.getValue() == 0);
  public final BooleanProperty pauseOnNoMove =
      new BooleanProperty("pause-on-no-move", true, () -> mode.getValue() == 0);
  public final BooleanProperty pauseOnChest =
      new BooleanProperty("pause-on-chest", false, () -> mode.getValue() == 0);
  public final BooleanProperty line = new BooleanProperty("line", true, () -> mode.getValue() == 0);
  public final ColorProperty lineColor =
      new ColorProperty(
          "line-color", Color.GREEN.getRGB(), () -> mode.getValue() == 0 && line.getValue());

  public final IntProperty dynamicDelay =
      new IntProperty("dynamic-delay", 200, 25, 1000, () -> mode.getValue() == 1);
  public final IntProperty dynamicCooldown =
      new IntProperty("dynamic-cooldown", 120, 0, 500, () -> mode.getValue() == 1);
  public final BooleanProperty dynamicDebug =
      new BooleanProperty("dynamic-debug", false, () -> mode.getValue() == 1);
  public final BooleanProperty dynamicIgnoreTeammates =
      new BooleanProperty("dynamic-ignore-teammates", true, () -> mode.getValue() == 1);
  public final BooleanProperty dynamicStopOnHurt =
      new BooleanProperty("dynamic-stop-on-hurt", true, () -> mode.getValue() == 1);
  public final IntProperty dynamicStopOnHurtTime =
      new IntProperty(
          "dynamic-stop-on-hurt-time",
          500,
          0,
          1000,
          () -> mode.getValue() == 1 && dynamicStopOnHurt.getValue());
  public final FloatProperty dynamicStartRange =
      new FloatProperty("dynamic-start-range", 6.0F, 3.0F, 10.0F, () -> mode.getValue() == 1);
  public final FloatProperty dynamicStopRange =
      new FloatProperty("dynamic-stop-range", 3.5F, 1.0F, 6.0F, () -> mode.getValue() == 1);
  public final FloatProperty dynamicMaxTargetRange =
      new FloatProperty("dynamic-max-target-range", 15.0F, 6.0F, 20.0F, () -> mode.getValue() == 1);

  public final FloatProperty adaptiveEnableRange =
      new FloatProperty("adaptive-enable-range", 20.0F, 4.0F, 64.0F, () -> mode.getValue() == 2);
  public final FloatProperty adaptiveSafeRange =
      new FloatProperty("adaptive-safe-range", 5.0F, 1.0F, 20.0F, () -> mode.getValue() == 2);
  public final FloatProperty adaptiveDelay =
      new FloatProperty("adaptive-delay", 100.0F, 200.0F, 0.0F, 2000.0F, () -> mode.getValue() == 2);
  public final FloatProperty adaptiveBuildupDuration =
      new FloatProperty("adaptive-buildup-duration", 600.0F, 0.0F, 3000.0F, () -> mode.getValue() == 2);
  public final FloatProperty adaptiveBuildupDelay =
      new FloatProperty("adaptive-cooldown", 500.0F, 0.0F, 5000.0F, () -> mode.getValue() == 2);

  private final LinkedList<QueueData> packetQueue = new LinkedList<>();
  private final LinkedList<PositionData> positions = new LinkedList<>();
  private final LinkedList<PositionData> renderPositions = new LinkedList<>();

  private long resetTimer;
  private boolean wasNearEnemy;
  private boolean ignoreWholeTick;

  private AbstractClientPlayer dynamicTarget;
  private long dynamicLastDisableTime = -1L;
  private long dynamicLastStopBlinkTime = -1L;
  private boolean dynamicLastHurt;
  private long dynamicLastStartBlinkTime = -1L;

  private final List<Vec3> positionHistory = new ArrayList<>();
  private boolean lagging;
  private double currentDelay = 0;
  private double targetDelay = 0;
  private long lastReleaseTime = 0;
  private EntityPlayer closestPlayer;
  private double closestDistance = Double.MAX_VALUE;

  public FakeLag() {
    super("FakeLag", false);
  }

  @Override
  public void onEnabled() {
    this.clearPackets();
    this.resetTimer = System.currentTimeMillis();
    this.wasNearEnemy = false;
    this.ignoreWholeTick = false;
    this.dynamicTarget = null;
    this.dynamicLastDisableTime = -1L;
    this.dynamicLastStopBlinkTime = -1L;
    this.dynamicLastHurt = false;
    this.dynamicLastStartBlinkTime = -1L;

    this.lagging = false;
    this.currentDelay = 0;
    this.targetDelay = 0;
    this.lastReleaseTime = 0;
    this.closestPlayer = null;
    this.closestDistance = Double.MAX_VALUE;
    this.positionHistory.clear();
  }

  @Override
  public void onDisabled() {
    stopDynamicBlink();
    if (this.lagging) {
      this.stopLag(true);
    }
    if (mc.thePlayer != null) {
      this.blink(true);
    } else {
      this.clearPackets();
    }
    this.positionHistory.clear();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null || event.isCancelled())
      return;

    Packet<?> packet = event.getPacket();

    if (this.mode.getValue() == 2) {
      if (event.getType() == EventType.SEND) {
        if (this.lagging) {
          event.setCancelled(true);
          this.packetQueue.add(new QueueData(packet, System.currentTimeMillis() + (long) currentDelay));
        }
      }
      return;
    }

    if (this.mode.getValue() == 1) {
      if (event.getType() == EventType.SEND) {
        handleDynamicAttackTarget(packet);
      }
      return;
    }

    if (this.ignoreWholeTick || this.wasNearEnemy) return;
    if (this.isIgnoredPacket(packet)) return;

    if (this.pauseOnNoMove.getValue() && !this.isMoving()) {
      this.blink(true);
      return;
    }

    if (mc.thePlayer.hurtTime != 0 && mc.thePlayer.getHealth() < mc.thePlayer.getMaxHealth()) {
      this.blink(true);
      return;
    }

    if (this.blinkOnAction.getValue() && packet instanceof C02PacketUseEntity) {
      this.blink(true);
      return;
    }

    if (this.pauseOnChest.getValue() && mc.currentScreen instanceof GuiContainer) {
      this.blink(true);
      return;
    }

    if (this.shouldFlush(packet)) {
      this.blink(true);
      return;
    }

    if (!this.hasTimePassed(this.resetTimer, this.recoilTime.getValue())) return;
    if (mc.isSingleplayer() || mc.getCurrentServerData() == null) {
      this.blink(true);
      return;
    }

    if (event.getType() != EventType.SEND) return;

    event.setCancelled(true);
    this.packetQueue.add(new QueueData(packet, System.currentTimeMillis()));

    if (packet instanceof C03PacketPlayer) {
      C03PacketPlayer playerPacket = (C03PacketPlayer) packet;
      if (playerPacket.isMoving()) {
        PositionData positionData =
            new PositionData(
                new Vec3(
                    playerPacket.getPositionX(),
                    playerPacket.getPositionY(),
                    playerPacket.getPositionZ()),
                System.currentTimeMillis());
        this.positions.add(positionData);
        this.renderPositions.add(positionData);
      }
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (this.isEnabled() && this.mode.getValue() == 2) {
      if (event.getTarget() instanceof EntityPlayer) {
        stopLag(false);
      }
    }
  }

  @EventTarget
  public void onWorldLoad(LoadWorldEvent event) {
    stopDynamicBlink();
    if (this.lagging) {
      stopLag(true);
    }
    this.blink(false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.PRE
        || mc.thePlayer == null
        || mc.theWorld == null) return;

    if (this.mode.getValue() == 2) {
      handleAdaptive();
      return;
    }

    if (this.mode.getValue() == 1) {
      handleDynamic();
      return;
    }

    this.checkEnemyDistance();

    if (mc.thePlayer.isDead || mc.thePlayer.isUsingItem()) {
      this.blink(true);
      return;
    }

    if (!this.hasTimePassed(this.resetTimer, this.recoilTime.getValue())) return;

    this.handlePackets(false);
    this.ignoreWholeTick = false;
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    pruneRenderPositions();
    if (!this.isEnabled()
        || this.mode.getValue() != 0
        || !this.line.getValue()
        || this.renderPositions.isEmpty()
        || mc.thePlayer == null) return;

    Color color = new Color(this.lineColor.getValue(), true);
    double renderX = mc.getRenderManager().viewerPosX;
    double renderY = mc.getRenderManager().viewerPosY;
    double renderZ = mc.getRenderManager().viewerPosZ;

    GL11.glPushMatrix();
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glLineWidth(2.0F);
    GL11.glColor4f(
        color.getRed() / 255.0F,
        color.getGreen() / 255.0F,
        color.getBlue() / 255.0F,
        color.getAlpha() / 255.0F);
    GL11.glBegin(GL11.GL_LINE_STRIP);
    for (PositionData position : this.renderPositions) {
      GL11.glVertex3d(
          position.pos.xCoord - renderX,
          position.pos.yCoord - renderY,
          position.pos.zCoord - renderZ);
    }
    GL11.glVertex3d(
        mc.thePlayer.posX - renderX, mc.thePlayer.posY - renderY, mc.thePlayer.posZ - renderZ);
    GL11.glEnd();
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glPopMatrix();
  }

  private void handleAdaptive() {
    recordPosition();
    findClosestPlayer();

    if (closestDistance <= adaptiveSafeRange.getValue()) {
      if (lagging) {
        stopLag(true);
      }
      handlePackets(false);
      return;
    }

    if (closestDistance > adaptiveEnableRange.getValue()) {
      if (lagging) {
        stopLag(false);
      }
      handlePackets(false);
      return;
    }

    if (lagging) {
      if (serverSidedPositionIsCloser()) {
        stopLag(false);
      } else {
        buildUp();
      }
    } else if (System.currentTimeMillis() - lastReleaseTime >= adaptiveBuildupDelay.getValue()) {
      startLag();
    }

    handlePackets(false);
  }

  private void recordPosition() {
    positionHistory.add(0, new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
    while (positionHistory.size() > 400) {
      positionHistory.remove(positionHistory.size() - 1);
    }
  }

  private void findClosestPlayer() {
    closestPlayer = null;
    closestDistance = Double.MAX_VALUE;
    if (mc.theWorld == null || mc.thePlayer == null) return;

    double maxDistance = Math.ceil(adaptiveEnableRange.getValue());
    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player == mc.thePlayer || player.isDead) continue;
      double distance = mc.thePlayer.getDistanceToEntity(player);
      if (distance <= maxDistance && distance < closestDistance) {
        closestDistance = distance;
        closestPlayer = player;
      }
    }
  }

  private boolean serverSidedPositionIsCloser() {
    if (closestPlayer == null || positionHistory.isEmpty())
      return false;

    int ticksAgo = (int) ((getPing() / 50) + currentDelay / 20);
    ticksAgo = Math.max(0, Math.min(ticksAgo, positionHistory.size() - 1));

    Vec3 serverSided = positionHistory.get(ticksAgo);
    Vec3 enemyPos = new Vec3(closestPlayer.posX, closestPlayer.posY, closestPlayer.posZ);

    double serverSidedDistance = serverSided.distanceTo(enemyPos);
    return serverSidedDistance < closestDistance;
  }

  private int getPing() {
    if (mc.thePlayer == null || mc.getNetHandler() == null) return 0;
    net.minecraft.client.network.NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
    return playerInfo != null ? playerInfo.getResponseTime() : 0;
  }

  private void startLag() {
    if (closestDistance <= adaptiveSafeRange.getValue()) return;

    lagging = true;
    currentDelay = 0;
    float min = adaptiveDelay.getValue();
    float max = adaptiveDelay.getSecondValue();
    targetDelay = min + Math.random() * (max - min);
  }

  private void buildUp() {
    if (currentDelay >= targetDelay) return;

    double increment =
        adaptiveBuildupDuration.getValue() <= 0
            ? targetDelay
            : (targetDelay * 50.0) / adaptiveBuildupDuration.getValue();

    currentDelay = Math.min(targetDelay, currentDelay + increment);
  }

  private void stopLag(boolean emergency) {
    if (!lagging) return;

    lagging = false;
    lastReleaseTime = System.currentTimeMillis();
    this.handlePackets(true);
  }

  private void handleDynamicAttackTarget(Packet<?> packet) {
    if (!(packet instanceof C02PacketUseEntity)) return;
    C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
    if (useEntity.getAction() != C02PacketUseEntity.Action.ATTACK) return;
    Entity entity = useEntity.getEntityFromWorld(mc.theWorld);
    if (entity instanceof AbstractClientPlayer) {
      if (dynamicIgnoreTeammates.getValue() && TeamUtil.isSameTeam((EntityPlayer) entity)) return;
      dynamicTarget = (AbstractClientPlayer) entity;
    }
  }

  private void handleDynamic() {
    boolean blinking = isDynamicBlinking();
    long now = System.currentTimeMillis();

    if (dynamicStopOnHurt.getValue()
        && dynamicLastDisableTime > 0
        && now - dynamicLastDisableTime <= dynamicStopOnHurtTime.getValue()) {
      if (blinking) {
        dynamicMessage("stop lag: hurt cooldown.");
        stopDynamicBlink();
        blinking = false;
      }
      dynamicLastHurt = mc.thePlayer.hurtTime > 0;
      return;
    }

    if (blinking) {
      if (now - dynamicLastStartBlinkTime >= dynamicDelay.getValue()) {
        dynamicMessage("stop lag: time out.");
        stopDynamicBlink();
        blinking = false;
      } else if (!dynamicLastHurt && mc.thePlayer.hurtTime > 0 && dynamicStopOnHurt.getValue()) {
        dynamicMessage("stop lag: hurt.");
        dynamicLastDisableTime = now;
        stopDynamicBlink();
        blinking = false;
      }
    }

    if (!isValidDynamicTarget(dynamicTarget)) {
      if (dynamicTarget != null) {
        dynamicMessage("release target: invalid.");
        dynamicTarget = null;
      }
      stopDynamicBlink();
      dynamicLastHurt = mc.thePlayer.hurtTime > 0;
      return;
    }

    double distance = mc.thePlayer.getDistanceToEntity(dynamicTarget);
    float startRange = Math.max(dynamicStartRange.getValue(), dynamicStopRange.getValue());
    float stopRange = Math.min(dynamicStartRange.getValue(), dynamicStopRange.getValue());

    if (distance > dynamicMaxTargetRange.getValue()) {
      dynamicMessage("release target: " + dynamicTarget.getName());
      dynamicTarget = null;
      stopDynamicBlink();
    } else if (blinking && distance <= stopRange) {
      dynamicMessage("stop lag: too close.");
      stopDynamicBlink();
    } else if (blinking && distance >= startRange) {
      dynamicMessage("stop lag: out of range.");
      stopDynamicBlink();
    } else if (!blinking
        && distance > stopRange
        && distance < startRange
        && now - dynamicLastStopBlinkTime >= dynamicCooldown.getValue()) {
      dynamicMessage("start lag: in range.");
      dynamicLastStartBlinkTime = now;
      startDynamicBlink();
    }

    dynamicLastHurt = mc.thePlayer.hurtTime > 0;
  }

  private boolean isValidDynamicTarget(AbstractClientPlayer target) {
    return target != null
        && !target.isDead
        && target.getHealth() > 0.0F
        && mc.theWorld != null
        && mc.theWorld.loadedEntityList.contains(target)
        && (!dynamicIgnoreTeammates.getValue() || !TeamUtil.isSameTeam(target));
  }

  private boolean isDynamicBlinking() {
    return Miau.blinkManager.getBlinkingModule() == BlinkModules.FAKE_LAG;
  }

  private void startDynamicBlink() {
    if (isDynamicBlinking()) return;
    Miau.blinkManager.setBlinkState(false, Miau.blinkManager.getBlinkingModule());
    Miau.blinkManager.setBlinkState(true, BlinkModules.FAKE_LAG);
  }

  private void stopDynamicBlink() {
    if (Miau.blinkManager.setBlinkState(false, BlinkModules.FAKE_LAG)) {
      dynamicLastStopBlinkTime = System.currentTimeMillis();
    }
  }

  private void dynamicMessage(String message) {
    if (dynamicDebug.getValue()) {
      miau.util.client.ChatUtil.display(Miau.clientName + this.getName() + ": &7" + message);
    }
  }

  private boolean shouldFlush(Packet<?> packet) {
    if (packet instanceof C0EPacketClickWindow || packet instanceof C0DPacketCloseWindow)
      return true;
    if (packet instanceof S08PacketPlayerPosLook
        || packet instanceof C08PacketPlayerBlockPlacement
        || packet instanceof C07PacketPlayerDigging
        || packet instanceof C12PacketUpdateSign
        || packet instanceof C19PacketResourcePackStatus) return true;
    if (packet instanceof S12PacketEntityVelocity
        && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId())
      return true;
    return packet instanceof S27PacketExplosion
        && (((S27PacketExplosion) packet).func_149149_c() != 0.0F
            || ((S27PacketExplosion) packet).func_149144_d() != 0.0F
            || ((S27PacketExplosion) packet).func_149147_e() != 0.0F);
  }

  private boolean isIgnoredPacket(Packet<?> packet) {
    return packet instanceof C00Handshake
        || packet instanceof C00PacketServerQuery
        || packet instanceof C01PacketPing
        || packet instanceof C01PacketChatMessage
        || packet instanceof S01PacketPong;
  }

  private void checkEnemyDistance() {
    if (this.allowedDistToEnemy.getSecondValue() <= 0.0F || this.positions.isEmpty()) {
      this.wasNearEnemy = false;
      return;
    }

    Vec3 serverPos = this.positions.getFirst().pos;
    float min = this.allowedDistToEnemy.getValue();
    float max = this.allowedDistToEnemy.getSecondValue();
    this.wasNearEnemy = false;

    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player == mc.thePlayer || player.isDead) continue;

      double distance = player.getDistance(serverPos.xCoord, serverPos.yCoord, serverPos.zCoord);
      if (distance >= min && distance <= max) {
        this.blink(true);
        this.wasNearEnemy = true;
        return;
      }
    }
  }

  private void blink(boolean handlePackets) {
    if (handlePackets) {
      this.resetTimer = System.currentTimeMillis();
    }
    this.handlePackets(true);
    this.ignoreWholeTick = true;
  }

  private void handlePackets(boolean clear) {
    long now = System.currentTimeMillis();
    Iterator<QueueData> packetIterator = this.packetQueue.iterator();
    while (packetIterator.hasNext()) {
      QueueData data = packetIterator.next();
      boolean shouldRelease = false;
      if (clear) {
        shouldRelease = true;
      } else {
        if (this.mode.getValue() == 2) {
          shouldRelease = data.time <= now;
        } else {
          shouldRelease = data.time <= now - this.delay.getValue();
        }
      }
      if (shouldRelease) {
        PacketUtil.sendPacketNoEvent(data.packet);
        packetIterator.remove();
      }
    }

    Iterator<PositionData> positionIterator = this.positions.iterator();
    while (positionIterator.hasNext()) {
      PositionData data = positionIterator.next();
      boolean shouldRelease = false;
      if (clear) {
        shouldRelease = true;
      } else {
        if (this.mode.getValue() == 2) {
          shouldRelease = data.time <= now;
        } else {
          shouldRelease = data.time <= now - this.delay.getValue();
        }
      }
      if (shouldRelease) {
        positionIterator.remove();
      }
    }
    pruneRenderPositions();
  }

  private void pruneRenderPositions() {
    long now = System.currentTimeMillis();
    long keepTime = Math.max(this.delay.getValue(), this.recoilTime.getValue()) + 1000L;
    Iterator<PositionData> renderIterator = this.renderPositions.iterator();
    while (renderIterator.hasNext()) {
      PositionData data = renderIterator.next();
      if (data.time <= now - keepTime) {
        renderIterator.remove();
      }
    }
  }

  private void clearPackets() {
    this.packetQueue.clear();
    this.positions.clear();
    this.renderPositions.clear();
  }

  private boolean hasTimePassed(long timer, int delay) {
    return System.currentTimeMillis() - timer >= delay;
  }

  private boolean isMoving() {
    return mc.thePlayer != null
        && (mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F);
  }

  public Vec3 getServerPositionForDebug() {
    if (!this.isEnabled()) return null;
    if (!this.positions.isEmpty()) return this.positions.getFirst().pos;
    pruneRenderPositions();
    return this.renderPositions.isEmpty() ? null : this.renderPositions.getFirst().pos;
  }

  @Override
  public String[] getSuffix() {
    if (this.mode.getValue() == 1) {
      return new String[] {this.mode.getModeString()};
    }
    return new String[] {this.mode.getModeString() + " " + this.packetQueue.size()};
  }

  private static class QueueData {
    private final Packet<?> packet;
    private final long time;

    private QueueData(Packet<?> packet, long time) {
      this.packet = packet;
      this.time = time;
    }
  }

  private static class PositionData {
    private final Vec3 pos;
    private final long time;

    private PositionData(Vec3 pos, long time) {
      this.pos = pos;
      this.time = time;
    }
  }
}
