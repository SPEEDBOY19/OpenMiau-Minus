package miau.module.modules.misc;

import miau.enums.ChatColors;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.math.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.event.HoverEvent;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;

public class ViewPackets extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // --- CÁC TÙY CHỌN BẬT / TẮT LOG ---
    public final BooleanProperty showKillAuraRange = new BooleanProperty("Show Range Info", true);
    public final BooleanProperty showTargetHealth = new BooleanProperty("Show Target Health", true);
    
    // --- CÁC OPTION CŨ CỦA VIEWPACKETS ---
    public final BooleanProperty includeCancelled = new BooleanProperty("Include cancelled", false);
    public final BooleanProperty singlePlayer = new BooleanProperty("Singleplayer", false);
    public final BooleanProperty sent = new BooleanProperty("Sent", false);
    public final BooleanProperty ignoreC00 = new BooleanProperty("Ignore C00", true);
    public final BooleanProperty ignoreC03 = new BooleanProperty("Ignore C03", true);
    public final BooleanProperty compactC03 = new BooleanProperty("Compact C03", true);
    public final BooleanProperty ignoreC0F = new BooleanProperty("Ignore C0F", true);
    public final BooleanProperty received = new BooleanProperty("Received", false);

    private Packet packet;
    public static long tick;

    public ViewPackets() {
        super("ViewPackets", false);
    }

    @Override
    public void onDisabled() {
        packet = null;
        tick = 0;
    }

    private static String formatBoolean(boolean b) {
        return b ? "&atrue" : "&cfalse";
    }

    private void sendMessage(Packet packet, boolean received) {
        if (mc.thePlayer == null) {
            return;
        }
        String s = received ? ("&a" + packet.getClass().getSimpleName()) : applyInfo(packet);
        String string = ((compactC03.getValue() && packet instanceof C03PacketPlayer) ? "&6" : "&d") + packet.getClass().getSimpleName();
        ChatComponentText chatComponentText = new ChatComponentText(ChatColors.formatColor("&7[&dR&7]&r &7" + (received ? "Received" : "Sent") + " packet (t:&b" + tick + "&7): "));
        
        ChatStyle chatStyle = new ChatStyle();
        // FIX LỖI GẠCH ĐỎ: Ép kiểu thành IChatComponent để khớp với Constructor của HoverEvent trong MCP 1.8.9
        IChatComponent hoverText = new ChatComponentText(ChatColors.formatColor(s));
        chatStyle.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
        
        chatComponentText.appendSibling(new ChatComponentText(ChatColors.formatColor(string)).setChatStyle(chatStyle));
        mc.thePlayer.addChatMessage(chatComponentText);
    }

    @EventTarget
    public void onPacket(PacketEvent e) {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // XỬ LÝ LOG KILLAURA RANGE & MÁU ĐỐI THỦ KHI GỬI C02PacketUseEntity
        if (e.getType() == EventType.SEND && e.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity useEntity = (C02PacketUseEntity) e.getPacket();
            
            if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                Entity target = useEntity.getEntityFromWorld(mc.theWorld);
                if (target != null) {
                    
                    // 1. LOG KILLAURA RANGE
                    if (showKillAuraRange.getValue()) {
                        Vec3 eyePos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
                        Vec3 targetEyePos = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
                        
                        // Khoảng cách từ mắt đến mắt
                        double distanceRange = eyePos.distanceTo(targetEyePos);
                        
                        // Khoảng cách thực tế từ mắt tới Bounding Box của mục tiêu (Real Range)
                        AxisAlignedBB bb = target.getEntityBoundingBox();
                        Vec3 closestPoint = new Vec3(
                            MathHelper.clamp_double(eyePos.xCoord, bb.minX, bb.maxX),
                            MathHelper.clamp_double(eyePos.yCoord, bb.minY, bb.maxY),
                            MathHelper.clamp_double(eyePos.zCoord, bb.minZ, bb.maxZ)
                        );
                        double realRange = eyePos.distanceTo(closestPoint);

                        String rangeMsg = ChatColors.formatColor(
                            "&7[&dMiau&7] &e==> &7Distance Range &b" + MathUtil.round(distanceRange, 7) + 
                            " &7| Real Range &a" + MathUtil.round(realRange, 7)
                        );
                        mc.thePlayer.addChatMessage(new ChatComponentText(rangeMsg));
                    }

                    // 2. LOG MÁU ĐỐI THỦ (PLAYER / LIVING ENTITY)
                    if (showTargetHealth.getValue() && target instanceof EntityLivingBase) {
                        EntityLivingBase livingTarget = (EntityLivingBase) target;
                        float hp = livingTarget.getHealth();
                        float absHp = livingTarget.getAbsorptionAmount();
                        
                        String healthFormatted = MathUtil.round(hp, 1) + (absHp > 0 ? " &e(+" + MathUtil.round(absHp, 1) + " Abs)" : "");
                        
                        String healthMsg = ChatColors.formatColor(
                            "&7[&dMiau&7] &e==> &fPlayer: &c" + target.getName() + " &7- Máu: &a" + healthFormatted + " HP"
                        );
                        mc.thePlayer.addChatMessage(new ChatComponentText(healthMsg));
                    }
                }
            }
        }

        // --- XỬ LÝ LOG PACKET CHUẨN CỦA VIEWPACKETS ---
        if (e.getType() == EventType.SEND) {
            if (!sent.getValue()) {
                return;
            }
            if (singlePlayer.getValue() && mc.isSingleplayer() && e.getPacket().getClass().getSimpleName().charAt(0) == 'S') {
                return;
            }
            if (e.isCancelled() && !includeCancelled.getValue()) {
                return;
            }
            if (ignoreC00.getValue() && e.getPacket() instanceof C00PacketKeepAlive) {
                return;
            }
            if (ignoreC0F.getValue() && e.getPacket() instanceof C0FPacketConfirmTransaction) {
                return;
            }
            if (e.getPacket() instanceof C03PacketPlayer && (ignoreC03.getValue() || (compactC03.getValue() && (packet == null || packet instanceof C03PacketPlayer)))) {
                return;
            }
            sendMessage(packet = e.getPacket(), false);
        } else if (e.getType() == EventType.RECEIVE) {
            if (!received.getValue()) {
                return;
            }
            if (singlePlayer.getValue() && mc.isSingleplayer() && e.getPacket().getClass().getSimpleName().charAt(0) == 'C') {
                return;
            }
            sendMessage(e.getPacket(), true);
        }
    }

    private String applyInfo(Packet packet) {
        String s = "&a" + packet.getClass().getSimpleName();
        if (packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging c07PacketPlayerDigging = (C07PacketPlayerDigging)packet;
            String string = s + "\n&7Status: &b" + c07PacketPlayerDigging.getStatus().name() + "\n&7Facing: &b" + c07PacketPlayerDigging.getFacing().name();
            BlockPos getPosition = c07PacketPlayerDigging.getPosition();
            s = string + "\n&7Position: &b" + getPosition.getX() + "&7, &b" + getPosition.getY() + "&7, &b" + getPosition.getZ();
        }
        else if (packet instanceof C09PacketHeldItemChange) {
            s = s + "\n&7Swap to slot: &b" + ((C09PacketHeldItemChange)packet).getSlotId();
        }
        else if (packet instanceof C0BPacketEntityAction) {
            s = s + "\n&7Action: &b" + ((C0BPacketEntityAction)packet).getAction().name() + "\n&7Aux data: &b" + ((C0BPacketEntityAction)packet).getAuxData();
        }
        else if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement c08PacketPlayerBlockPlacement = (C08PacketPlayerBlockPlacement)packet;
            String string2 = s + "\n&7Item: &b" + ((c08PacketPlayerBlockPlacement.getStack() == null) ? "null" : c08PacketPlayerBlockPlacement.getStack().getItem().getRegistryName().replace("minecraft:", "")) + "\n&7Direction: &b" + c08PacketPlayerBlockPlacement.getPlacedBlockDirection();
            BlockPos getPosition = c08PacketPlayerBlockPlacement.getPosition();
            s = string2 + "\n&7Position: &b" + getPosition.getX() + "&7, &b" + getPosition.getY() + "&7, &b" + getPosition.getZ() + "\n&7Offset: &b" + MathUtil.round((double)c08PacketPlayerBlockPlacement.getPlacedBlockOffsetX(), 3) + "&7, &b" + MathUtil.round((double)c08PacketPlayerBlockPlacement.getPlacedBlockOffsetY(), 3) + "&7, &b" + MathUtil.round(c08PacketPlayerBlockPlacement.getPlacedBlockOffsetZ(), 3);
        }
        else if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity c02PacketUseEntity = (C02PacketUseEntity)packet;
            String string3 = s + "\n&7Action: &b" + c02PacketUseEntity.getAction().name();
            Entity getEntityFromWorld = c02PacketUseEntity.getEntityFromWorld(mc.theWorld);
            String string4 = string3 + "\n&7Target: &b" + ((getEntityFromWorld == null) ? "null" : getEntityFromWorld.getName());
            Vec3 getHitVec = c02PacketUseEntity.getHitVec();
            if (getHitVec == null) {
                s = string4 + "\n&7Hit vec: &bnull";
            }
            else {
                s = string4 + "\n&7Hit vec: &b" + MathUtil.round(getHitVec.xCoord, 3) + "&7, &b" + MathUtil.round(getHitVec.yCoord, 3) + "&7, &b" + MathUtil.round(getHitVec.zCoord, 3);
            }
        }
        else if (packet instanceof C01PacketChatMessage) {
            s = s + "\n&7Length: &b" + ((C01PacketChatMessage)packet).getMessage().length();
        }
        else if (packet instanceof C17PacketCustomPayload) {
            s = s + "\n&7Channel: &b" + ((C17PacketCustomPayload)packet).getChannelName();
        }
        else if (packet instanceof C15PacketClientSettings) {
            s = s + "\n&7Language: &b" + ((C15PacketClientSettings)packet).getLang() + "\n&7Chat visibility: &b" + ((C15PacketClientSettings)packet).getChatVisibility().name();
        }
        else if (packet instanceof C00PacketKeepAlive) {
            s = s + "\n&7Key: &b" + ((C00PacketKeepAlive)packet).getKey();
        }
        else if (packet instanceof C16PacketClientStatus) {
            s = s + "\n&7Status: &b" + ((C16PacketClientStatus)packet).getStatus().name();
        }
        else if (packet instanceof C10PacketCreativeInventoryAction) {
            s = s + "\n&7Slot: &b" + ((C10PacketCreativeInventoryAction)packet).getSlotId() + "\n&7Item: &b" + ((((C10PacketCreativeInventoryAction)packet).getStack() == null) ? "null" : ((C10PacketCreativeInventoryAction)packet).getStack().getItem().getRegistryName().replace("minecraft:", ""));
        }
        else if (packet instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow c0EPacketClickWindow = (C0EPacketClickWindow)packet;
            s = s + "\n&7Window: &b" + c0EPacketClickWindow.getWindowId() + "\n&7Slot: &b" + c0EPacketClickWindow.getSlotId() + "\n&7Button: &b" + c0EPacketClickWindow.getUsedButton() + "\n&7Action: &b" + c0EPacketClickWindow.getActionNumber() + "\n&7Mode: &b" + c0EPacketClickWindow.getMode() + "\n&7Item: &b" + ((c0EPacketClickWindow.getClickedItem() == null) ? "null" : c0EPacketClickWindow.getClickedItem().getItem().getRegistryName().replace("minecraft:", ""));
        }
        else if (packet instanceof C0FPacketConfirmTransaction) {
            s = s + "\n&7Window: &b" + ((C0FPacketConfirmTransaction)packet).getWindowId() + "\n&7Uid: &b" + ((C0FPacketConfirmTransaction)packet).getUid();
        }
        else if (packet instanceof C03PacketPlayer) {
            C03PacketPlayer c03PacketPlayer = (C03PacketPlayer)packet;
            s = s + "\n&7Position: &b" + MathUtil.round(c03PacketPlayer.getPositionX(), 3) + "&7, &b" + MathUtil.round(c03PacketPlayer.getPositionY(), 3) + "&7, &b" + MathUtil.round(c03PacketPlayer.getPositionZ(), 3) + "\n&7Rotations: &b" + MathUtil.round((double)c03PacketPlayer.getYaw(), 3) + "&7, &b" + MathUtil.round((double)c03PacketPlayer.getPitch(), 3) + "\n&7Ground: " + formatBoolean(c03PacketPlayer.isOnGround()) + "\n&7Moving: " + formatBoolean(c03PacketPlayer.isMoving()) + "\n&7Rotating: " + formatBoolean(c03PacketPlayer.getRotating());
        }
        return s + "\n&7Client tick: &e" + tick;
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (e.getType() == EventType.PRE) {
            ++tick;
        }
    }
}