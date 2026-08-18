package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class SnapRotation implements IRotationLogic {

    private int airTicks = 0;
    private static final int ROTATION_DELAY_TICKS = 2;

    private float lockedYaw = 0f;
    private float lockedPitch = 79.5f;
    private boolean hasSnapped = false;

    @Override
    public void handleInitialRotation(Scaffold scaffold, UpdateEvent event,
                                      float currentYaw, float yawDiffTo180, float diagonalYaw) {
        // Not used for telly snap
    }

    public void updateRotation(Scaffold scaffold, UpdateEvent event) {
        Minecraft mc = Scaffold.mc;
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        // Lấy góc nhìn vật lý thật của chuột người chơi
        float forwardYaw   = player.rotationYaw;
        float forwardPitch = player.rotationPitch;

        // ---- Đếm tick trên không ----
        if (player.onGround) {
            airTicks = 0;
            hasSnapped = false;          // Reset cho cú nhảy tiếp theo
        } else {
            airTicks++;
        }

        // 1. Ở dưới đất -> Hướng thẳng theo mặt người chơi
        if (player.onGround) {
            scaffold.yaw       = forwardYaw;
            scaffold.pitch     = forwardPitch;
            scaffold.bridgeYaw = forwardYaw;
            return;
        }

        // 2. Vừa rời mặt đất -> Giữ nhìn thẳng trong vài ticks đầu
        if (airTicks < ROTATION_DELAY_TICKS) {
            scaffold.yaw       = forwardYaw;
            scaffold.pitch     = forwardPitch;
            scaffold.bridgeYaw = forwardYaw;
            return;
        }

        // 3. Đạt độ trễ -> Khóa góc ngắm vào block sắp đặt dưới chân
        if (!hasSnapped) {
            lockedYaw   = MathHelper.wrapAngleTo180_float(forwardYaw - 180.0f);
            
            Scaffold.BlockData blockData = scaffold.getBlockData();
            if (blockData != null && blockData.blockPos != null) {
                float[] rot = getRotationToBlock(player, blockData.blockPos);
                lockedYaw   = rot[0];
                lockedPitch = rot[1];
            } else {
                lockedPitch = 79.5f;
            }
            
            hasSnapped  = true;
        }

        // Tốc độ lướt smooth (0.35f) giúp góc quay chuyển động mềm mại, tự nhiên như tay người
        float smoothSpeed = 0.35f;
        scaffold.yaw       = smoothAngle(scaffold.yaw, lockedYaw, smoothSpeed);
        scaffold.pitch     = smoothAngle(scaffold.pitch, lockedPitch, smoothSpeed);
        scaffold.bridgeYaw = scaffold.yaw;
    }

    /**
     * Tính toán góc Yaw và Pitch chuẩn xác tới mục tiêu block
     */
    private float[] getRotationToBlock(EntityPlayer player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.posX;
        double dy = pos.getY() + 0.5 - (player.posY + player.getEyeHeight());
        double dz = pos.getZ() + 0.5 - player.posZ;

        double distHoriz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distHoriz));

        yaw = MathHelper.wrapAngleTo180_float(yaw);
        pitch = MathHelper.clamp_float(pitch, -90f, 90f);

        return new float[]{yaw, pitch};
    }

    // Hàm nội suy góc xoay mượt mà
    private float smoothAngle(float current, float target, float alpha) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        return current + diff * alpha;
    }
}