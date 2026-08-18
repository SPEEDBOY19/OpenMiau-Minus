package miau.module.modules.combat;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class Ignite extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final BooleanProperty lighter = new BooleanProperty("Lighter", true);
  private final BooleanProperty lavaBucket = new BooleanProperty("Lava", true);
  private final TimerUtil msTimer = new TimerUtil();

  public Ignite() {
    super("Ignite", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    if (event.getType() != EventType.PRE) return;
    if (!this.msTimer.hasTimeElapsed(500L)) return;

    int lighterInHotbar = this.lighter.getValue() ? this.findItem(Items.flint_and_steel) : -1;
    int lavaInHotbar = this.lavaBucket.getValue() ? this.findItem(Items.lava_bucket) : -1;

    int fireInHotbar = lighterInHotbar != -1 ? lighterInHotbar : lavaInHotbar;
    if (fireInHotbar == -1) return;

    for (Object o : mc.theWorld.loadedEntityList) {
      if (!(o instanceof EntityLivingBase)) continue;
      EntityLivingBase entity = (EntityLivingBase) o;
      if (entity == mc.thePlayer || entity.isDead || entity instanceof EntityArmorStand) continue;
      if (entity.isBurning()) continue;

      BlockPos blockPos = new BlockPos(entity.posX, entity.posY, entity.posZ);
      if (mc.thePlayer.getDistanceSq(blockPos) >= 22.3) continue;

      Block block = mc.theWorld.getBlockState(blockPos).getBlock();
      if (!(block instanceof BlockAir)) continue;
      if (!block.isReplaceable(mc.theWorld, blockPos)) continue;

      Miau.slotComponent.setSlot(fireInHotbar, false);

      ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(fireInHotbar);
      if (itemStack == null) return;

      if (itemStack.getItem() instanceof ItemBucket) {
        float[] rotations = this.getRotations(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        PacketUtil.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(rotations[0], rotations[1], mc.thePlayer.onGround));
        PacketUtil.sendPacket(new net.minecraft.network.play.client.C08PacketPlayerBlockPlacement(itemStack));
      } else {
        for (EnumFacing side : EnumFacing.values()) {
          BlockPos neighbor = blockPos.offset(side);
          Block nBlock = mc.theWorld.getBlockState(neighbor).getBlock();
          if (nBlock instanceof BlockAir || !nBlock.isFullBlock()) continue;

          float[] rotations = this.getRotations(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5);
          PacketUtil.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(rotations[0], rotations[1], mc.thePlayer.onGround));

          Vec3 hitVec = new Vec3(
              side.getDirectionVec().getX(), side.getDirectionVec().getY(), side.getDirectionVec().getZ());
          PacketUtil.sendPacket(
              new net.minecraft.network.play.client.C08PacketPlayerBlockPlacement(
                  neighbor,
                  side.getOpposite().getIndex(),
                  itemStack,
                  (float) hitVec.xCoord,
                  (float) hitVec.yCoord,
                  (float) hitVec.zCoord));
          mc.thePlayer.swingItem();
          break;
        }
      }

      Miau.slotComponent.setSlot(mc.thePlayer.inventory.currentItem, false);
      PacketUtil.sendPacket(
          new C03PacketPlayer.C05PacketPlayerLook(
              mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.thePlayer.onGround));
      this.msTimer.reset();
      break;
    }
  }

  private int findItem(net.minecraft.item.Item item) {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.getItem() == item) return i;
    }
    return -1;
  }

  private float[] getRotations(double targetX, double targetY, double targetZ) {
    double diffX = targetX - mc.thePlayer.posX;
    double diffY = targetY - (mc.thePlayer.getEntityBoundingBox().minY + mc.thePlayer.eyeHeight);
    double diffZ = targetZ - mc.thePlayer.posZ;
    double sqrtXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
    float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
    float pitch = (float) -Math.toDegrees(Math.atan2(diffY, sqrtXZ));
    float finalYaw =
        mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
    float finalPitch =
        mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch);
    return new float[] {finalYaw, finalPitch};
  }
}
