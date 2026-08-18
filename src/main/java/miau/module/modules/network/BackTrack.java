package miau.module.modules.network;

import java.awt.Color;
import java.util.*;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.player.Scaffold;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.math.RandomUtil;
import miau.util.misc.BackTrackUtil;
import miau.util.misc.ITruePosition;
import miau.util.network.PacketUtil;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import org.lwjgl.opengl.GL11;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] NON_DELAYED_SOUND_SUBSTRINGS =
            new String[] {"game.player.hurt", "game.player.die"};

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"NORMAL", "BUFFER"});
    public final IntProperty nextBacktrackDelay =
            new IntProperty("next-backtrack-delay", 0, 0, 2000, () -> mode.getValue() == 0);
    public final FloatProperty delayMs =
            new FloatProperty("delay", 80.0F, 80.0F, 0.0F, 2000.0F, () -> mode.getValue() == 0);

    public final ModeProperty style =
            new ModeProperty("style", 1, new String[] {"PULSE", "SMOOTH"}, () -> mode.getValue() == 0);
    public final FloatProperty distance =
            new FloatProperty("distance", 2.0F, 3.0F, 0.0F, 10.0F, () -> mode.getValue() == 0);
    public final BooleanProperty smart =
            new BooleanProperty("smart", true, () -> mode.getValue() == 0);

    public final ModeProperty espMode =
            new ModeProperty(
                    "esp", 1, new String[] {"NONE", "BOX", "MODEL", "WIREFRAME"}, () -> mode.getValue() == 0);
    public final FloatProperty wireframeWidth =
            new FloatProperty(
                    "wireframe-width",
                    1.0F,
                    0.5F,
                    5.0F,
                    () -> mode.getValue() == 0 && espMode.getValue() == 3);
    public final ColorProperty espColor = new ColorProperty("color", 0xFF00FF00);
    public final FloatProperty packetDistance =
            new FloatProperty("packet-distance", 4.0F, 5.0F, 0.0F, 7.0F, () -> mode.getValue() == 1);
    public final IntProperty packetTimer =
            new IntProperty("packet-timer", 200, 0, 2000, () -> mode.getValue() == 1);
    public final ModeProperty packetMode =
            new ModeProperty(
                    "packet-mode", 0, new String[] {"PING", "DELAY"}, () -> mode.getValue() == 1);
    public final IntProperty packetPingSize =
            new IntProperty(
                    "packet-ping-size", 0, 0, 2000, () -> mode.getValue() == 1 && packetMode.getValue() == 0);
    public final BooleanProperty packetPlayerModel =
            new BooleanProperty("packet-player-model", true, () -> mode.getValue() == 1);
    public final BooleanProperty packetResetVelocity =
            new BooleanProperty("packet-reset-velocity", false, () -> mode.getValue() == 1);
    public final ModeProperty legacyPos =
            new ModeProperty("legacy-pos", 0, new String[] {"OFF", "ON"});
    public final IntProperty maximumCachedPositions =
            new IntProperty("max-cached-positions", 10, 1, 100);
    private final Queue<QueuedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final Queue<TimedPosition> positions = new ConcurrentLinkedQueue<>();
    private final Queue<PacketLog> packetPingQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Packet<?>> packetDelayQueue = new ConcurrentLinkedQueue<>();
    private final TimerUtil packetTimerUtil = new TimerUtil();
    private final Map<UUID, List<BacktrackData>> backtrackedPlayer = new ConcurrentHashMap<>();

    private final TimerUtil globalTimer = new TimerUtil();

    public EntityLivingBase target;
    private boolean shouldRender = true;
    private boolean ignoreWholeTick = false;
    private long delayForNextBacktrack = 0L;

    private int modernDelayValue = 80;
    private boolean modernDelayBoolean = false;
    private EntityPlayer packetTarget;
    private double packetRealX;
    private double packetRealY;
    private double packetRealZ;

    private static BackTrack instance;
    public static boolean isReleasingPackets = false;

    public BackTrack() {
        super("BackTrack", false);
        instance = this;
    }

    public static <T> T runWithNearestTrackedDistance(Entity entity, java.util.function.Supplier<T> action) {
        if (instance == null
                || !instance.isEnabled()
                || !(entity instanceof EntityPlayer)
                || entity != instance.target) {
            return action.get();
        }
        List<Vec3> candidates = new ArrayList<>();
        synchronized (instance.positions) {
            for (TimedPosition tp : instance.positions) {
                candidates.add(tp.position);
            }
        }
        if (candidates.isEmpty()) {
            return action.get();
        }
        candidates.sort(
                Comparator.comparingDouble(
                        pos ->
                                BackTrackUtil.runWithSimulatedPosition(
                                        entity, pos, () -> BackTrackUtil.getDistanceToEntityBox(entity))));
        Vec3 nearest = candidates.get(0);
        T result = BackTrackUtil.runWithSimulatedPosition(entity, nearest, action);
        return result != null ? result : action.get();
    }

    private int getSupposedDelay() {
        return mode.getValue() == 0 ? modernDelayValue : delayMs.getSecondValue().intValue();
    }

    @Override
    public void onDisabled() {
        clearPackets(true, true);
        clearPacketMode(true);
        backtrackedPlayer.clear();
        reset();
    }

    @Override
    public void onEnabled() {
        reset();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (mode.getValue() == 0) {
            clearPackets(false, true);
            target = null;
        }
        backtrackedPlayer.clear();
        clearPacketMode(false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) return;

        Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            clear();
            return;
        }

        if (mode.getValue() == 1) {
            updatePacketMode();
            return;
        }
        if (mode.getValue() == 0) {
            if (shouldBacktrack() && target instanceof ITruePosition) {
                ITruePosition targetMixin = (ITruePosition) target;
                if (targetMixin.isTruePos()) {
                    double trueDist =
                            mc.thePlayer.getDistance(
                                    targetMixin.getTrueX(), targetMixin.getTrueY(), targetMixin.getTrueZ());
                    double dist = mc.thePlayer.getDistance(target.posX, target.posY, target.posZ);
                    double boxDist = RotationUtil.distanceToBox(target.getEntityBoundingBox());

                    if (trueDist <= 6f
                            && (!smart.getValue() || trueDist >= dist)
                            && (style.getValue() == 1 || !globalTimer.hasTimeElapsed(getSupposedDelay()))) {
                        shouldRender = true;

                        if (boxDist >= distance.getValue() && boxDist <= distance.getSecondValue()) {
                            handlePackets();
                        } else {
                            handlePacketsRange();
                        }
                    } else {
                        clear();
                    }
                }
            } else {
                clear();
            }
        }
        ignoreWholeTick = false;

        updateDelayCooldown();
    }

    private boolean isWorldUpdatePacket(Packet<?> packet) {
        return packet instanceof S21PacketChunkData
                || packet instanceof S22PacketMultiBlockChange
                || packet instanceof S23PacketBlockChange
                || packet instanceof S24PacketBlockAction
                || packet instanceof S25PacketBlockBreakAnim
                || packet instanceof S26PacketMapChunkBulk
                || packet instanceof S35PacketUpdateTileEntity;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) return;
        if (isReleasingPackets) return;
        if (Miau.blinkManager != null && Miau.blinkManager.isBlinking()) return;

        Packet<?> packet = event.getPacket();
        if (event.getType() == EventType.RECEIVE && this.isWorldUpdatePacket(packet)) {
            return;
        }
        if (mode.getValue() == 1) {
            handlePacketMode(event, packet);
            return;
        }
        if (mode.getValue() == 0) {
            if (mc.isSingleplayer() || mc.getCurrentServerData() == null) {
                clearPackets(true, false);
                return;
            }

            if (packetQueue.isEmpty() && !shouldBacktrack()) return;

            if (packet instanceof C00Handshake
                    || packet instanceof C00PacketServerQuery
                    || packet instanceof S02PacketChat
                    || packet instanceof S01PacketPong) return;

            if (packet instanceof S29PacketSoundEffect) {
                String soundName = ((S29PacketSoundEffect) packet).getSoundName();
                for (String s : NON_DELAYED_SOUND_SUBSTRINGS) {
                    if (soundName.contains(s)) return;
                }
            }

            if (packet instanceof S06PacketUpdateHealth
                    && ((S06PacketUpdateHealth) packet).getHealth() <= 0) {
                clearPackets(true, true);
                return;
            }

            if (packet instanceof S13PacketDestroyEntities && target != null) {
                for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                    if (id == target.getEntityId()) {
                        clearPackets(true, true);
                        reset();
                        return;
                    }
                }
            }

            if (packet instanceof S1CPacketEntityMetadata
                    && target != null
                    && ((S1CPacketEntityMetadata) packet).getEntityId() == target.getEntityId()) {
                if (isDeadMetadata((S1CPacketEntityMetadata) packet)) {
                    clearPackets(true, true);
                    reset();
                    return;
                }
                return;
            }

            if (packet instanceof S19PacketEntityStatus && target != null) {
                Entity entity = ((S19PacketEntityStatus) packet).getEntity(mc.theWorld);
                if (entity != null && entity.getEntityId() == target.getEntityId()) {
                    return;
                }
            }

            if (event.getType() == EventType.RECEIVE) {
                if (packet instanceof S14PacketEntity && target != null) {
                    Entity entity = ((S14PacketEntity) packet).getEntity(mc.theWorld);
                    if (entity != null && entity.getEntityId() == target.getEntityId()) {
                        S14PacketEntity s14 = (S14PacketEntity) packet;
                        double newX = target.posX + s14.func_149062_c() / 32.0D;
                        double newY = target.posY + s14.func_149061_d() / 32.0D;
                        double newZ = target.posZ + s14.func_149064_e() / 32.0D;
                        positions.add(
                                new TimedPosition(new Vec3(newX, newY, newZ), System.currentTimeMillis()));
                    }
                } else if (packet instanceof S18PacketEntityTeleport
                        && target != null
                        && ((S18PacketEntityTeleport) packet).getEntityId() == target.getEntityId()) {
                    S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
                    double newX = s18.getX() / 32.0D;
                    double newY = s18.getY() / 32.0D;
                    double newZ = s18.getZ() / 32.0D;
                    positions.add(new TimedPosition(new Vec3(newX, newY, newZ), System.currentTimeMillis()));
                }

                event.setCancelled(true);
                packetQueue.add(new QueuedPacket(packet, System.currentTimeMillis()));
            }
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isValidTarget(event.getTarget())) return;

        if (mode.getValue() == 1) {
            handlePacketAttack(event);
            return;
        }

        if (mode.getValue() == 0) {
            EntityLivingBase living = (EntityLivingBase) event.getTarget();
            if (target != living) {
                if (mode.getValue() == 0) clearPackets(true, true);
                reset();
            }
            target = living;
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) return false;
        EntityLivingBase living = (EntityLivingBase) entity;
        if (!living.isEntityAlive()) return false;
        if (living == mc.thePlayer) return false;
        if (living.isInvisible()) return false;
        if (living instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) living;
            if (player.isSpectator()) return false;
            if (TeamUtil.isBot(player)) return false;
            if (TeamUtil.isFriend(player)) return false;
        }
        return true;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.getRenderManager() == null) return;
        if (mode.getValue() == 1) {
            renderPacketMode(event);
            return;
        }
        if (mode.getValue() == 0) {
            if (!shouldBacktrack() || !shouldRender || target == null) return;

            TimedPosition renderPos = null;
            for (TimedPosition p : positions) {
                renderPos = p;
            }
            if (renderPos == null) return;

            double x = renderPos.position.xCoord - mc.getRenderManager().viewerPosX;
            double y = renderPos.position.yCoord - mc.getRenderManager().viewerPosY;
            double z = renderPos.position.zCoord - mc.getRenderManager().viewerPosZ;

            Color color = new Color(espColor.getValue());

            switch (espMode.getValue()) {
                case 1:
                    AxisAlignedBB box =
                            target
                                    .getEntityBoundingBox()
                                    .offset(
                                            renderPos.position.xCoord - target.posX,
                                            renderPos.position.yCoord - target.posY,
                                            renderPos.position.zCoord - target.posZ);
                    drawBacktrackBox(box, color);
                    break;
                case 2:
                    GlStateManager.pushMatrix();
                    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                    GlStateManager.color(0.6F, 0.6F, 0.6F, 1.0F);
                    mc.getRenderManager()
                            .doRenderEntity(
                                    target,
                                    x,
                                    y,
                                    z,
                                    target.prevRotationYaw
                                            + (target.rotationYaw - target.prevRotationYaw) * event.getPartialTicks(),
                                    event.getPartialTicks(),
                                    true);
                    GL11.glPopAttrib();
                    GL11.glColor4f(1f, 1f, 1f, 1f);
                    GlStateManager.color(1f, 1f, 1f, 1f);
                    GlStateManager.popMatrix();
                    break;
                case 3:
                    GlStateManager.pushMatrix();
                    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glEnable(GL11.GL_LINE_SMOOTH);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glLineWidth(wireframeWidth.getValue());

                    GL11.glColor4f(
                            color.getRed() / 255.0F,
                            color.getGreen() / 255.0F,
                            color.getBlue() / 255.0F,
                            color.getAlpha() / 255.0F);
                    mc.getRenderManager()
                            .doRenderEntity(
                                    target,
                                    x,
                                    y,
                                    z,
                                    target.prevRotationYaw
                                            + (target.rotationYaw - target.prevRotationYaw) * event.getPartialTicks(),
                                    event.getPartialTicks(),
                                    true);

                    GL11.glPopAttrib();
                    GL11.glColor4f(1f, 1f, 1f, 1f);
                    GlStateManager.color(1f, 1f, 1f, 1f);
                    GlStateManager.popMatrix();
                    break;
            }
            return;
        }
    }

    private void handleLegacyTick() {
        long cutoff = System.currentTimeMillis() - getSupposedDelay();
        Iterator<Map.Entry<UUID, List<BacktrackData>>> it = backtrackedPlayer.entrySet().iterator();
        while (it.hasNext()) {
            List<BacktrackData> data = it.next().getValue();
            data.removeIf(d -> d.time < cutoff);
            if (data.isEmpty()) it.remove();
        }

        if (legacyPos.getValue() == 0 && mc.theWorld != null) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                    addBacktrackData(
                            entity.getUniqueID(),
                            entity.posX,
                            entity.posY,
                            entity.posZ,
                            System.currentTimeMillis());
                }
            }
        }
    }

    private void addBacktrackData(UUID id, double x, double y, double z, long time) {
        List<BacktrackData> data = backtrackedPlayer.get(id);
        if (data != null) {
            if (data.size() >= maximumCachedPositions.getValue()) {
                data.remove(0);
            }
            data.add(new BacktrackData(x, y, z, time));
        } else {
            List<BacktrackData> newData = new ArrayList<>();
            newData.add(new BacktrackData(x, y, z, time));
            backtrackedPlayer.put(id, newData);
        }
    }

    private List<BacktrackData> getBacktrackData(UUID id) {
        return backtrackedPlayer.get(id);
    }

    private void handleLegacyClientPosTick() {
        if (mc.theWorld == null) return;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                addBacktrackData(
                        entity.getUniqueID(),
                        entity.posX,
                        entity.posY,
                        entity.posZ,
                        System.currentTimeMillis());
            }
        }
    }

    private void removeBacktrackData(UUID id) {
        backtrackedPlayer.remove(id);
    }

    private void handlePackets() {
        long delay = getSupposedDelay();
        packetQueue.removeIf(
                queuedPacket -> {
                    if (queuedPacket.time <= System.currentTimeMillis() - delay) {
                        receiveQueuedPacket(queuedPacket.packet);
                        return true;
                    }
                    return false;
                });

        positions.removeIf(pos -> pos.time < System.currentTimeMillis() - delay);
    }

    private void handlePacketsRange() {
        long time = getRangeTime();
        if (time == -1L) {
            clearPackets(true, true);
            return;
        }

        packetQueue.removeIf(
                queuedPacket -> {
                    if (queuedPacket.time <= time) {
                        receiveQueuedPacket(queuedPacket.packet);
                        return true;
                    }
                    return false;
                });

        positions.removeIf(pos -> pos.time < time);
    }

    private long getRangeTime() {
        if (target == null) return -1L;
        long time = 0L;
        boolean found = false;

        for (TimedPosition data : positions) {
            time = data.time;
            AxisAlignedBB targetBox =
                    target
                            .getEntityBoundingBox()
                            .offset(
                                    data.position.xCoord - target.posX,
                                    data.position.yCoord - target.posY,
                                    data.position.zCoord - target.posZ);

            double dist = getDistanceToBox(targetBox);
            if (dist >= distance.getValue() && dist <= distance.getSecondValue()) {
                found = true;
                break;
            }
        }
        return found ? time : -1L;
    }

    private double getDistanceToBox(AxisAlignedBB box) {
        if (mc.thePlayer == null || box == null) return 0.0;
        double x = clamp(mc.thePlayer.posX, box.minX, box.maxX);
        double y = clamp(mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), box.minY, box.maxY);
        double z = clamp(mc.thePlayer.posZ, box.minZ, box.maxZ);
        return Math.sqrt(
                (mc.thePlayer.posX - x) * (mc.thePlayer.posX - x)
                        + (mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - y)
                        * (mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - y)
                        + (mc.thePlayer.posZ - z) * (mc.thePlayer.posZ - z));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void clearPackets(boolean handlePackets, boolean stopRendering) {
        packetQueue.removeIf(
                queuedPacket -> {
                    if (handlePackets) {
                        receiveQueuedPacket(queuedPacket.packet);
                    }
                    return true;
                });

        positions.clear();

        if (stopRendering) {
            shouldRender = false;
            ignoreWholeTick = true;
        }
    }

    private void updateDelayCooldown() {
        boolean shouldChangeDelay = packetQueue.isEmpty();
        if (!shouldChangeDelay) {
            modernDelayBoolean = false;
        }
        if (shouldChangeDelay && !modernDelayBoolean && !shouldBacktrack()) {
            delayForNextBacktrack = System.currentTimeMillis() + nextBacktrackDelay.getValue();
            modernDelayValue =
                    randomInt(delayMs.getValue().intValue(), delayMs.getSecondValue().intValue());
            modernDelayBoolean = true;
        }
    }

    private void clear() {
        clearPackets(true, true);
        globalTimer.reset();
    }

    private void reset() {
        target = null;
        globalTimer.reset();
    }

    private boolean shouldBacktrack() {
        if (mc.thePlayer == null
                || mc.theWorld == null
                || target == null
                || mc.thePlayer.getHealth() <= 0
                || !(Float.isNaN(target.getHealth()) || target.getHealth() > 0)
                || mc.playerController.getCurrentGameType() == WorldSettings.GameType.SPECTATOR
                || System.currentTimeMillis() < delayForNextBacktrack
                || mc.thePlayer.ticksExisted <= 20
                || ignoreWholeTick) {
            return false;
        }
        if (!target.isEntityAlive()) return false;
        if (target == mc.thePlayer) return false;
        if (target.isInvisible()) return false;
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            if (player.isSpectator()) return false;
            if (TeamUtil.isBot(player)) return false;
            if (TeamUtil.isFriend(player)) return false;
        }
        return true;
    }

    private boolean isDeadMetadata(S1CPacketEntityMetadata packet) {
        if (packet.func_149376_c() == null) return false;
        for (Object watchedObject : packet.func_149376_c()) {
            if (!(watchedObject instanceof net.minecraft.entity.DataWatcher.WatchableObject)) continue;
            net.minecraft.entity.DataWatcher.WatchableObject data =
                    (net.minecraft.entity.DataWatcher.WatchableObject) watchedObject;
            if (data.getDataValueId() != 6 || data.getObject() == null) continue;
            try {
                double value = Double.parseDouble(data.getObject().toString());
                if (!Double.isNaN(value) && value <= 0.0D) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void receiveQueuedPacket(Packet<?> packet) {
        if (packet == null || mc.getNetHandler() == null || mc.theWorld == null || mc.thePlayer == null)
            return;
        try {
            isReleasingPackets = true;
            PacketEvent event = new PacketEvent(EventType.RECEIVE, packet);
            miau.event.EventManager.call(event);
            if (!event.isCancelled()) {
                PacketUtil.handlePacket((Packet<INetHandlerPlayClient>) packet);
            }
            isReleasingPackets = false;
        } catch (RuntimeException ignored) {
            isReleasingPackets = false;
        }
    }

    private void renderBacktrackModel(
            Entity entity, double x, double y, double z, float partialTicks, Color color) {
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.color(0.6F, 0.6F, 0.6F, 1.0F);
        mc.getRenderManager()
                .doRenderEntity(
                        entity,
                        x,
                        y,
                        z,
                        entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks,
                        partialTicks,
                        true);
        GL11.glPopAttrib();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    private void renderBacktrackWireframe(
            Entity entity, double x, double y, double z, float partialTicks, Color color) {
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(wireframeWidth.getValue());
        GL11.glColor4f(
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                color.getAlpha() / 255.0F);
        mc.getRenderManager()
                .doRenderEntity(
                        entity,
                        x,
                        y,
                        z,
                        entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks,
                        partialTicks,
                        true);
        GL11.glPopAttrib();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    private void drawBacktrackBox(AxisAlignedBB box, Color color) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.pushMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        RenderGlobal.drawOutlinedBoundingBox(
                new AxisAlignedBB(
                        box.minX - mc.getRenderManager().viewerPosX,
                        box.minY - mc.getRenderManager().viewerPosY,
                        box.minZ - mc.getRenderManager().viewerPosZ,
                        box.maxX - mc.getRenderManager().viewerPosX,
                        box.maxY - mc.getRenderManager().viewerPosY,
                        box.maxZ - mc.getRenderManager().viewerPosZ),
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                color.getAlpha());
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GlStateManager.popMatrix();
        GL11.glPopAttrib();
        GlStateManager.resetColor();
    }

    private boolean shouldPacketBacktrack() {
        return mc.thePlayer != null
                && mc.theWorld != null
                && packetTarget != null
                && !packetTarget.isDead
                && packetTarget.isEntityAlive()
                && mc.thePlayer.getDistanceToEntity(packetTarget) >= packetDistance.getValue()
                && mc.thePlayer.getDistanceToEntity(packetTarget) <= packetDistance.getSecondValue()
                && !TeamUtil.isBot(packetTarget);
    }

    private void handlePacketAttack(AttackEvent event) {
        if (!(event.getTarget() instanceof EntityPlayer)
                || mc.thePlayer == null
                || mc.theWorld == null) {
            return;
        }
        EntityPlayer attacked = (EntityPlayer) event.getTarget();
        if (packetTarget != attacked) {
            clearPacketMode(true);
            packetTarget = attacked;
            packetRealX = attacked.posX;
            packetRealY = attacked.posY;
            packetRealZ = attacked.posZ;
        }
    }

    private void handlePacketMode(PacketEvent event, Packet<?> packet) {
        if (mc.thePlayer == null
                || mc.theWorld == null
                || packetTarget == null
                || event.getType() != EventType.RECEIVE) {
            return;
        }

        // Critical packets must never be delayed: release everything buffered
        // so far and let the packet through immediately (matches Avocado zone).
        if (packet instanceof S40PacketDisconnect
                || packet instanceof S02PacketChat
                || packet instanceof S08PacketPlayerPosLook) {
            flushPacketMode();
            return;
        }
        if (packet instanceof S06PacketUpdateHealth
                && ((S06PacketUpdateHealth) packet).getHealth() <= 0.0F) {
            flushPacketMode();
            return;
        }
        if (packet instanceof S13PacketDestroyEntities
                && containsEntityId((S13PacketDestroyEntities) packet, packetTarget.getEntityId())) {
            flushPacketMode();
            return;
        }

        if (!packet.getClass().getSimpleName().toLowerCase().startsWith("s")) {
            return;
        }

        if (packet instanceof S14PacketEntity) {
            Entity entity = ((S14PacketEntity) packet).getEntity(mc.theWorld);
            if (entity != null && entity.getEntityId() == packetTarget.getEntityId()) {
                packetRealX += ((S14PacketEntity) packet).func_149062_c() / 32.0D;
                packetRealY += ((S14PacketEntity) packet).func_149061_d() / 32.0D;
                packetRealZ += ((S14PacketEntity) packet).func_149064_e() / 32.0D;
            }
        } else if (packet instanceof S18PacketEntityTeleport
                && ((S18PacketEntityTeleport) packet).getEntityId() == packetTarget.getEntityId()) {
            packetRealX = ((S18PacketEntityTeleport) packet).getX() / 32.0D;
            packetRealY = ((S18PacketEntityTeleport) packet).getY() / 32.0D;
            packetRealZ = ((S18PacketEntityTeleport) packet).getZ() / 32.0D;
        } else if (packet instanceof S12PacketEntityVelocity
                && packetResetVelocity.getValue()
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
            clearPacketMode(true);
            return;
        }

        event.setCancelled(true);
        if (packetMode.getValue() == 0) {
            packetPingQueue.add(new PacketLog(packet, System.currentTimeMillis()));
        } else {
            packetDelayQueue.add(packet);
        }
    }

    private boolean containsEntityId(S13PacketDestroyEntities packet, int id) {
        for (int entityId : packet.getEntityIDs()) {
            if (entityId == id) return true;
        }
        return false;
    }

    private void flushPacketMode() {
        packetPingQueue.removeIf(
                log -> {
                    receiveQueuedPacket(log.packet);
                    return true;
                });
        packetDelayQueue.removeIf(
                packet -> {
                    receiveQueuedPacket(packet);
                    return true;
                });
    }

    private void updatePacketMode() {
        if (!shouldPacketBacktrack()) {
            clearPacketMode(true);
            return;
        }
        if (packetMode.getValue() == 0) {
            clearPacketPing(packetPingSize.getValue());
        } else if (packetTimerUtil.hasTimeElapsed(packetTimer.getValue())) {
            clearPacketDelay();
        }
    }

    private void clearPacketMode(boolean handlePackets) {
        if (handlePackets) {
            clearPacketPing(0);
            clearPacketDelay();
        }
        packetPingQueue.clear();
        packetDelayQueue.clear();
        packetTarget = null;
        packetRealX = packetRealY = packetRealZ = 0.0D;
    }

    private void clearPacketPing(int delay) {
        packetPingQueue.removeIf(
                log -> {
                    if (delay == 0 || System.currentTimeMillis() > log.time + delay) {
                        receiveQueuedPacket(log.packet);
                        return true;
                    }
                    return false;
                });
    }

    private void clearPacketDelay() {
        packetDelayQueue.removeIf(
                packet -> {
                    receiveQueuedPacket(packet);
                    packetTimerUtil.reset();
                    return true;
                });
    }

    private void renderPacketMode(Render3DEvent event) {
        if (packetTarget == null || !packetPlayerModel.getValue()) {
            return;
        }
        GlStateManager.pushMatrix();
        mc.getRenderManager()
                .doRenderEntity(
                        packetTarget,
                        packetRealX - mc.getRenderManager().viewerPosX,
                        packetRealY - mc.getRenderManager().viewerPosY,
                        packetRealZ - mc.getRenderManager().viewerPosZ,
                        packetTarget.rotationYaw,
                        event.getPartialTicks(),
                        true);
        GlStateManager.popMatrix();
        GlStateManager.resetColor();
    }

    private static int randomInt(int min, int max) {
        return RandomUtil.nextInt(Math.min(min, max), Math.max(min, max));
    }

    private static class BacktrackData {
        private final double x, y, z;
        private final long time;

        BacktrackData(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }

    private static class PacketLog {
        private final Packet<?> packet;
        private final long time;

        PacketLog(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }
    }

    private static class QueuedPacket {
        private final Packet<?> packet;
        private final long time;

        QueuedPacket(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }
    }

    private static class TimedPosition {
        private final Vec3 position;
        private final long time;

        TimedPosition(Vec3 position, long time) {
            this.position = position;
            this.time = time;
        }
    }
}