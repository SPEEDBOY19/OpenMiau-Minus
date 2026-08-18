package miau.module.modules.combat;

import java.util.concurrent.ThreadLocalRandom;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.misc.BackTrackUtil;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class AutoRod2 extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final ModeProperty rodMode =
      new ModeProperty("RodMode", 0, new String[] {"Legit", "Packet", "NewPacket"});
  private final FloatProperty maxRange = new FloatProperty("MaxRange", 6f, 0f, 16f);
  private final IntProperty maxDelayValue = new IntProperty("MaxDelay", 200, 0, 1000);
  private final IntProperty minDelay = new IntProperty("MinDelay", 100, 0, 1000);
  private final BooleanProperty smartDelay = new BooleanProperty("SmartDelay", false);
  private final BooleanProperty smartRodTiming =
      new BooleanProperty(
          "SmartRodTiming", false, () -> smartDelay.getValue());
  private final BooleanProperty perfectTiming = new BooleanProperty("PerfectTiming", false);
  private final IntProperty perfectHurtTime =
      new IntProperty(
          "PerfectHurtTime", 9, 0, 10, () -> perfectTiming.getValue());
  private final ModeProperty predictMode =
      new ModeProperty("PredictMode", 0, new String[] {"Custom", "ExperimentalFitting"});
  private final FloatProperty predictSize =
      new FloatProperty(
          "PredictSize",
          3.5f,
          0f,
          10f,
          () -> predictMode.getModeString().equals("Custom"));

  private int currentItem = -1;
  private ItemStack itemStack = null;
  private boolean resetting = false;
  private int pauseTick = 0;
  private boolean rodActionState = false;
  private boolean itemState = false;
  private boolean hasThrownRod = false;

  public AutoRod2() {
    super("AutoRod2", false);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {rodMode.getModeString()};
  }

  @Override
  public void onDisabled() {
    reset();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;

    int rod = getRod();
    int lastCurrentItem = currentItem;
    ItemStack lastItemStack = itemStack;

    if (cancelRun()) {
      resetting = true;
      switch (rodMode.getModeString()) {
        case "Legit":
          if (rodActionState) {
            KeyBinding.setKeyBindState(
                mc.gameSettings.keyBindUseItem.getKeyCode(), false);
          }
          if (itemState) {
            swapItem(lastCurrentItem);
          }
          break;
        case "Packet":
          if (rodActionState) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(lastItemStack));
          }
          if (itemState) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(lastCurrentItem));
          }
          break;
        case "NewPacket":
          if (itemState) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(lastCurrentItem));
          }
          break;
        default:
          break;
      }
      resetting = false;

      reset();
      return;
    }

    currentItem = player.inventory.currentItem;
    if (rod >= 0) {
      itemStack = player.inventoryContainer.getSlot(rod).getStack();
    }

    EntityLivingBase target = getKillAuraTarget();
    boolean shouldPullRod =
        perfectTiming.getValue() && target != null && target.hurtTime == perfectHurtTime.getValue();

    switch (rodMode.getModeString()) {
      case "Legit":
        if (perfectTiming.getValue()) {
          if (!hasThrownRod && shouldThrowRod()) {
            swapItem(rod - 36);
            itemState = true;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            rodActionState = true;
            hasThrownRod = true;
          }

          if (hasThrownRod && (shouldPullRod || cancelRun())) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            rodActionState = false;
            swapItem(currentItem);
            itemState = true;
            hasThrownRod = false;
            pauseTick = 0;
          }
        } else {
          pauseTick++;
          if (pauseTick == 1) {
            swapItem(rod - 36);
            itemState = true;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            rodActionState = true;
          }
          if (pauseTick >= tickDelay()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            rodActionState = false;
            swapItem(currentItem);
            itemState = true;
            pauseTick = 0;
          }
        }
        break;
      case "Packet":
        if (perfectTiming.getValue()) {
          if (!hasThrownRod && shouldThrowRod()) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(rod - 36));
            itemState = true;
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
            rodActionState = true;
            hasThrownRod = true;
          }

          if (hasThrownRod && (shouldPullRod || cancelRun())) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
            rodActionState = false;
            PacketUtil.sendPacket(new C09PacketHeldItemChange(currentItem));
            itemState = false;
            hasThrownRod = false;
            pauseTick = 0;
          }
        } else {
          pauseTick++;
          if (pauseTick == 1) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(rod - 36));
            itemState = true;
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
            rodActionState = true;
          }
          if (pauseTick >= tickDelay()) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
            rodActionState = false;
            PacketUtil.sendPacket(new C09PacketHeldItemChange(currentItem));
            itemState = false;
            pauseTick = 0;
          }
        }
        break;
      case "NewPacket":
        if (perfectTiming.getValue()) {
          if (!hasThrownRod && shouldThrowRod()) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(rod - 36));
            itemState = true;
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
            hasThrownRod = true;
          }

          if (hasThrownRod && (shouldPullRod || cancelRun())) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(currentItem));
            itemState = false;
            hasThrownRod = false;
            pauseTick = 0;
          }
        } else {
          pauseTick++;
          if (pauseTick == 1) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(rod - 36));
            itemState = true;
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
          }
          if (pauseTick >= tickDelay()) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(currentItem));
            itemState = false;
            pauseTick = 0;
          }
        }
        break;
      default:
        break;
    }
  }

  private double[] predictedPoint() {
    EntityLivingBase target = getKillAuraTarget();
    if (target == null) return new double[] {0.0, 0.0};

    if (predictMode.getModeString().equals("Custom")) {
      double motionX = target.posX - target.prevPosX;
      double motionZ = target.posZ - target.prevPosZ;
      return new double[] {
        motionX * predictSize.getValue(), motionZ * predictSize.getValue()
      };
    } else if (predictMode.getModeString().equals("ExperimentalFitting")) {
      double motionX = target.posX - target.prevPosX;
      double motionZ = target.posZ - target.prevPosZ;
      double bpsX = motionX * 20;
      double bpsZ = motionZ * 20;

      double fittedX = motionX * rangeFrom(f(bpsX), f(1.0), f(9.8));
      double fittedZ = motionZ * rangeFrom(f(bpsZ), f(1.0), f(9.8));

      return new double[] {fittedX, fittedZ};
    }
    return new double[] {0.0, 0.0};
  }

  private static double f(double x) {
    return 0.00428696 * Math.pow(x, 5)
        - 0.1235 * Math.pow(x, 4)
        + 1.32092 * Math.pow(x, 3)
        - 6.35726 * Math.pow(x, 2)
        + 12.732 * x;
  }

  private static double rangeFrom(double value, double min, double max) {
    if (min > max) {
      return Math.max(max, Math.min(min, value));
    }
    return Math.max(min, Math.min(max, value));
  }

  private double distance() {
    EntityLivingBase target = getKillAuraTarget();
    if (mc.thePlayer == null || target == null) return 0.0;
    return BackTrackUtil.getDistanceToEntityBox(target);
  }

  private int delay() {
    if (smartDelay.getValue()) {
      double dist = distance();
      double calculated =
          1880f / (1f + (18.71f * Math.pow(2.7182818285, -0.2076f * dist))) / 100f;
      int rounded = (int) Math.round(calculated) * 100;
      return Math.max(200, Math.min(650, rounded));
    } else {
      int min = Math.min(minDelay.getValue(), maxDelayValue.getValue());
      int max = Math.max(minDelay.getValue(), maxDelayValue.getValue());
      return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
  }

  private int tickDelay() {
    return (int) Math.ceil(delay() / 50.0);
  }

  private boolean rodTiming() {
    EntityLivingBase target = getKillAuraTarget();
    return target != null && target.hurtTime <= 3 + tickDelay();
  }

  private boolean isInRodRange() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    float killAuraRange = killAura == null ? 0f : killAura.attackRange.getValue();
    return distance() > killAuraRange && distance() <= maxRange.getValue();
  }

  private void reset() {
    pauseTick = 0;
    rodActionState = false;
    itemState = false;
    hasThrownRod = false;
  }

  private int getRod() {
    for (int i = 36; i < 45; i++) {
      ItemStack stack = mc.thePlayer.inventory.mainInventory[i - 36];
      if (stack != null && stack.getItem() == Items.fishing_rod) {
        return i - 36;
      }
    }
    return -1;
  }

  private boolean cancelRun() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    EntityLivingBase target = killAura == null ? null : killAura.getTarget();
    if (killAura == null || !killAura.isEnabled() || target == null) {
      return true;
    }

    if (getRod() == -1) {
      return true;
    }

    if (!isInRodRange()) {
      return true;
    }

    if (smartDelay.getValue() && smartRodTiming.getValue() && !rodTiming()) {
      return true;
    }

    return false;
  }

  private boolean shouldThrowRod() {
    if (cancelRun()) {
      return false;
    }

    if (perfectTiming.getValue()) {
      EntityLivingBase target = getKillAuraTarget();
      return target != null && target.hurtTime == 9;
    }

    return true;
  }

  private EntityLivingBase getKillAuraTarget() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    return killAura == null ? null : killAura.getTarget();
  }

  private void swapItem(int slot) {
    mc.thePlayer.inventory.currentItem = Math.max(0, Math.min(8, slot));
  }
}