package miau.module.modules.combat;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.network.PacketUtil;
import miau.util.player.ItemUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Mouse;

public class AutoBlock extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  
  private boolean blocking = false;
  private final TimerUtil holdTimer = new TimerUtil();
  private int unblockTicks = 0;

  // --- Main Settings ---
  public final FloatProperty range = new FloatProperty("Range", 4.5F, 3.0F, 6.0F);
  public final FloatProperty maximumHoldDuration = new FloatProperty("Maximum hold duration", 200.0F, 0.0F, 1000.0F);
  public final FloatProperty maximumHurtTime = new FloatProperty("Maximum hurt time", 10.0F, 0.0F, 20.0F);

  // --- Force block animation ---
  public final BooleanProperty forceBlockAnimEnabled = new BooleanProperty("Enabled", true);
  public final BooleanProperty onlyWithinRange = new BooleanProperty("Only within range", false);

  // --- Lag Settings ---
  public final FloatProperty lagChance = new FloatProperty("Chance", 100.0F, 0.0F, 100.0F);
  public final FloatProperty lagMaximumDuration = new FloatProperty("Maximum duration", 100.0F, 0.0F, 1000.0F);
  public final BooleanProperty blockAgainImmediately = new BooleanProperty("Block again immediately", true);

  // --- Conditionals ---
  public final BooleanProperty leftMousePressed = new BooleanProperty("Left mouse pressed", true);
  public final BooleanProperty rightMousePressed = new BooleanProperty("Right mouse pressed", true);
  public final BooleanProperty damaged = new BooleanProperty("Damaged", false);

  public AutoBlock() {
    super("AutoBlock", true, false);
  }

  @Override
  public void onEnabled() {
    blocking = false;
    holdTimer.reset();
    unblockTicks = 0;
  }

  @Override
  public void onDisabled() {
    if (blocking && mc.thePlayer != null) {
      stopBlock();
    }
    blocking = false;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    if (!ItemUtil.isHoldingSword()) {
      if (blocking) stopBlock();
      return;
    }

    if (!forceBlockAnimEnabled.getValue()) {
      if (blocking) stopBlock();
      return;
    }

    boolean meetsConditions = true;
    if (leftMousePressed.getValue() && !Mouse.isButtonDown(0)) {
      meetsConditions = false;
    }
    if (rightMousePressed.getValue() && !Mouse.isButtonDown(1)) {
      meetsConditions = false;
    }
    if (damaged.getValue() && mc.thePlayer.hurtTime <= 0) {
      meetsConditions = false;
    }
    if (maximumHurtTime.getValue() > 0 && mc.thePlayer.hurtTime > maximumHurtTime.getValue()) {
      meetsConditions = false;
    }

    if (onlyWithinRange.getValue()) {
      boolean inRange = false;
      for (EntityLivingBase entity : mc.theWorld.getEntitiesWithinAABB(EntityLivingBase.class,
          mc.thePlayer.getEntityBoundingBox().expand(range.getValue(), 2.0, range.getValue()))) {
        if (entity == mc.thePlayer || entity.isDead) continue;
        if (mc.thePlayer.getDistanceToEntity(entity) <= range.getValue()) {
          inRange = true;
          break;
        }
      }
      if (!inRange) meetsConditions = false;
    }

    // Xử lý thời gian unblock ngắn để animation swing kiếm render mượt mà khi tấn công
    if (unblockTicks > 0) {
      unblockTicks--;
      if (unblockTicks == 0 && meetsConditions && blockAgainImmediately.getValue()) {
        startBlock();
        holdTimer.reset();
      }
      return;
    }

    if (meetsConditions) {
      if (!blocking && !mc.thePlayer.isUsingItem() && !Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {
        if (Math.random() * 100 <= lagChance.getValue()) {
          startBlock();
          holdTimer.reset();
        }
      } else if (blocking && maximumHoldDuration.getValue() > 0 && holdTimer.hasTimeElapsed(maximumHoldDuration.getValue().longValue())) {
        stopBlock();
        if (blockAgainImmediately.getValue() && Math.random() * 100 <= lagChance.getValue()) {
          startBlock();
          holdTimer.reset();
        }
      }
    } else {
      if (blocking) stopBlock();
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    
    // Khi click chuột trái tấn công (C02), tạm thời nhả block trong 2 ticks để animation vung kiếm (swing) hiển thị hoàn hảo, không bị đơ
    if (event.getType() == EventType.SEND && ItemUtil.isHoldingSword()) {
      if (event.getPacket() instanceof C02PacketUseEntity) {
        C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
        if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
          if (blocking) {
            stopBlock();
            unblockTicks = 2; // Giữ trạng thái unblock 2 ticks cho animation chém vung lên rồi block lại ngay
          }
        }
      }
    }
  }

  private void startBlock() {
    ItemStack heldItem = mc.thePlayer.getHeldItem();
    if (heldItem == null || !(heldItem.getItem() instanceof ItemSword)) return;
    PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(heldItem));
    mc.thePlayer.setItemInUse(heldItem, heldItem.getMaxItemUseDuration());
    blocking = true;
  }

  private void stopBlock() {
    if (blocking) {
      PacketUtil.sendPacket(new C07PacketPlayerDigging(
          C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
          BlockPos.ORIGIN,
          EnumFacing.DOWN));
      if (mc.thePlayer != null) {
        mc.thePlayer.stopUsingItem();
      }
    }
    blocking = false;
  }

  public boolean isBlocking() {
    return blocking;
  }
}