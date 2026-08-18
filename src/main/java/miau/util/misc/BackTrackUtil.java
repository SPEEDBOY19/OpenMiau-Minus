package miau.util.misc;

import java.util.UUID;
import java.util.function.Supplier;
import miau.mixin.IAccessorS14PacketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public class BackTrackUtil {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public static Vec3 interpolatedPosition(Entity entity, float partialTicks) {
    return new Vec3(
        entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks,
        entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks,
        entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks);
  }

  public static Vec3 interpolatedPositionFrom(Entity entity, Vec3 start, float partialTicks) {
    return new Vec3(
        start.xCoord + (entity.posX - start.xCoord) * partialTicks,
        start.yCoord + (entity.posY - start.yCoord) * partialTicks,
        start.zCoord + (entity.posZ - start.zCoord) * partialTicks);
  }

  public static Vec3 getTrueInterpolatedPosition(
      Entity entity, ITruePosition tp, float partialTicks) {
    return interpolatedPositionFrom(entity, getCurrentTruePosition(tp), partialTicks);
  }

  public static Vec3 getCurrentPosition(Entity entity) {
    return new Vec3(entity.posX, entity.posY, entity.posZ);
  }

  public static Vec3 getPreviousPosition(Entity entity) {
    return new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ);
  }

  public static Vec3 getCurrentTruePosition(ITruePosition tp) {
    if (tp == null) return new Vec3(0, 0, 0);
    return new Vec3(tp.getTrueX(), tp.getTrueY(), tp.getTrueZ());
  }

  public static void setPositionAndPrevious(Entity entity, Vec3 current) {
    setPositionAndPrevious(entity, current, current);
  }

  public static void setPositionAndPrevious(Entity entity, Vec3 current, Vec3 previous) {
    double dx = current.xCoord - entity.posX;
    double dy = current.yCoord - entity.posY;
    double dz = current.zCoord - entity.posZ;
    entity.setEntityBoundingBox(entity.getEntityBoundingBox().offset(dx, dy, dz));
    entity.posX = current.xCoord;
    entity.posY = current.yCoord;
    entity.posZ = current.zCoord;
    entity.prevPosX = previous.xCoord;
    entity.prevPosY = previous.yCoord;
    entity.prevPosZ = previous.zCoord;
  }

  public static <T> T runWithSimulatedPosition(Entity entity, Vec3 position, Supplier<T> action) {
    Vec3 origPos = getCurrentPosition(entity);
    Vec3 origPrevPos = getPreviousPosition(entity);
    AxisAlignedBB origBox = entity.getEntityBoundingBox();

    double dx = position.xCoord - entity.posX;
    double dy = position.yCoord - entity.posY;
    double dz = position.zCoord - entity.posZ;

    entity.setEntityBoundingBox(origBox.offset(dx, dy, dz));
    entity.posX = position.xCoord;
    entity.posY = position.yCoord;
    entity.posZ = position.zCoord;
    entity.prevPosX = position.xCoord;
    entity.prevPosY = position.yCoord;
    entity.prevPosZ = position.zCoord;

    T result = action.get();

    entity.setEntityBoundingBox(origBox);
    entity.posX = origPos.xCoord;
    entity.posY = origPos.yCoord;
    entity.posZ = origPos.zCoord;
    entity.prevPosX = origPrevPos.xCoord;
    entity.prevPosY = origPrevPos.yCoord;
    entity.prevPosZ = origPrevPos.zCoord;

    return result;
  }

  public static double getDistanceToBox(AxisAlignedBB box) {
    if (mc.thePlayer == null || box == null) return 0.0D;
    double x = clamp(mc.thePlayer.posX, box.minX, box.maxX);
    double y = clamp(mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), box.minY, box.maxY);
    double z = clamp(mc.thePlayer.posZ, box.minZ, box.maxZ);
    double dx = mc.thePlayer.posX - x;
    double dy = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - y;
    double dz = mc.thePlayer.posZ - z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  public static double getDistanceToEntityBox(Entity entity) {
    return entity == null ? 0.0D : getDistanceToBox(entity.getEntityBoundingBox());
  }

  public static Entity getEntityFromPacket(S14PacketEntity packet) {
    if (mc.theWorld == null) return null;
    int entityId = ((IAccessorS14PacketEntity) packet).getEntityId();
    return mc.theWorld.getEntityByID(entityId);
  }

  public static Entity getEntityFromTeleport(S18PacketEntityTeleport packet) {
    return mc.theWorld == null ? null : mc.theWorld.getEntityByID(packet.getEntityId());
  }

  public static EntityPlayer getPlayerByUUID(UUID uuid) {
    if (mc.theWorld == null || uuid == null) return null;
    for (Entity entity : mc.theWorld.loadedEntityList) {
      if (entity instanceof EntityPlayer && uuid.equals(entity.getUniqueID())) {
        return (EntityPlayer) entity;
      }
    }
    return null;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private BackTrackUtil() {}
}
