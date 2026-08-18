package miau.module.modules.combat;

import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import miau.util.misc.BackTrackUtil;
import miau.util.misc.SomeUtil;
import miau.util.player.RayCastUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

public class AutoRod extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final BooleanProperty facingEnemy = new BooleanProperty("FacingEnemy", true);
  private final BooleanProperty ignoreOnEnemyLowHealth =
      new BooleanProperty(
          "IgnoreOnEnemyLowHealth", true, () -> facingEnemy.getValue());
  private final BooleanProperty healthFromScoreboard =
      new BooleanProperty(
          "HealthFromScoreboard",
          false,
          () -> facingEnemy.getValue() && ignoreOnEnemyLowHealth.getValue());
  private final BooleanProperty absorption =
      new BooleanProperty(
          "Absorption",
          false,
          () -> facingEnemy.getValue() && ignoreOnEnemyLowHealth.getValue());
  private final FloatProperty activationDistance =
      new FloatProperty("ActivationDistance", 3f, 8f, 1f, 8f);
  private final IntProperty enemiesNearby = new IntProperty("EnemiesNearby", 1, 1, 5);
  private final IntProperty playerHealthThreshold =
      new IntProperty("PlayerHealthThreshold", 5, 1, 20);
  private final IntProperty enemyHealthThreshold =
      new IntProperty(
          "EnemyHealthThreshold",
          5,
          1,
          20,
          () -> facingEnemy.getValue() && ignoreOnEnemyLowHealth.getValue());
  private final IntProperty escapeHealthThreshold =
      new IntProperty("EscapeHealthThreshold", 10, 1, 20);
  private final IntProperty pushDelay = new IntProperty("PushDelay", 100, 50, 1000);
  private final IntProperty pullbackDelay = new IntProperty("PullbackDelay", 500, 50, 1000);
  private final BooleanProperty onUsingItem = new BooleanProperty("OnUsingItem", false);
  private final BooleanProperty onlyOnKillAura = new BooleanProperty("OnlyOnKillAura", false);
  private final BooleanProperty disSetInventory =
      new BooleanProperty("SetInventorySlotOnDisable", false);
  private final IntProperty disSetInventorySlot =
      new IntProperty(
          "SetInventorySlotOnDisable-Slot",
          0,
          0,
          8,
          () -> disSetInventory.getValue());
  private final BooleanProperty rangeDebugger = new BooleanProperty("RangeDebugger", false);
  private final BooleanProperty switchBackAfterUse =
      new BooleanProperty("SwitchBackAfterUse", true);
  private final BooleanProperty pullWhenTargetHurt =
      new BooleanProperty("PullWhenTargetHurt", false);
  private final IntProperty targetHurtTime =
      new IntProperty(
          "TargetHurtTime", 9, 0, 10, () -> pullWhenTargetHurt.getValue());

  private EntityLivingBase target = null;
  private final TimerUtil pushTimer = new TimerUtil();
  private final TimerUtil rodPullTimer = new TimerUtil();

  private boolean rodInUse = false;
  private int switchBack = -1;

  public AutoRod() {
    super("AutoRod", false);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      String.format(
          "%s - %s", activationDistance.getValue(), activationDistance.getSecondValue())
    };
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    EntityPlayer player = mc.thePlayer;
    if (player == null) return;

    ItemStack heldItem = player.getHeldItem();
    boolean usingRod =
        (player.isUsingItem() && heldItem != null && heldItem.getItem() == Items.fishing_rod)
            || rodInUse;

    if (onlyOnKillAura.getValue()) {
      KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
      if (killAura == null || (!killAura.isEnabled() && killAura.getTarget() == null)) return;
    }

    if (usingRod
        && pullWhenTargetHurt.getValue()
        && target != null
        && target.hurtTime >= targetHurtTime.getValue()) {
      if (switchBack != -1
          && player.inventory.currentItem != switchBack
          && switchBackAfterUse.getValue()) {
        player.inventory.currentItem = switchBack;
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
      } else {
        player.stopUsingItem();
      }

      switchBack = -1;
      rodInUse = false;

      pushTimer.reset();
      return;
    }

    if (usingRod) {
      if (rodPullTimer.hasTimeElapsed(pullbackDelay.getValue())) {
        if (switchBack != -1
            && player.inventory.currentItem != switchBack
            && switchBackAfterUse.getValue()) {
          player.inventory.currentItem = switchBack;
          ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        } else {
          player.stopUsingItem();
        }

        switchBack = -1;
        rodInUse = false;

        pushTimer.reset();
      }
    } else {
      boolean rod = false;

      if (facingEnemy.getValue()
          && getHealth(player) >= playerHealthThreshold.getValue()) {
        Entity facingEntity = mc.objectMouseOver == null ? null : mc.objectMouseOver.entityHit;
        List<Entity> nearbyEnemies = getAllNearbyEnemies();

        if (facingEntity == null) {
          MovingObjectPosition mop =
              RayCastUtil.rayCast(
                  mc.thePlayer.rotationYaw,
                  mc.thePlayer.rotationPitch,
                  activationDistance.getSecondValue());
          facingEntity = mop == null ? null : mop.entityHit;
        }

        if (!onUsingItem.getValue()) {
          ItemStack itemInUse = mc.thePlayer.getItemInUse();
          if ((itemInUse == null || itemInUse.getItem() != Items.fishing_rod)
              && (mc.thePlayer.isUsingItem() || killAuraBlocking())) {
            return;
          }
        }

        if (SomeUtil.isSelected(facingEntity)) {
          double distance =
              facingEntity != null
                  ? BackTrackUtil.getDistanceToEntityBox(facingEntity)
                  : Double.MAX_VALUE;
          float realDistance = (float) (Math.round(distance * 100.0) / 100.0);
          if (rangeDebugger.getValue()) {
            ChatUtil.display(
                String.format(
                    "%.2f | %.2f", realDistance, activationDistance.getValue().doubleValue()));
          }
          if (distance >= activationDistance.getValue()) {
            if (nearbyEnemies.size() <= enemiesNearby.getValue()) {
              if (ignoreOnEnemyLowHealth.getValue()) {
                if (facingEntity instanceof EntityLivingBase
                    && getHealth((EntityLivingBase) facingEntity)
                        >= enemyHealthThreshold.getValue()) {
                  rod = true;
                  target = (EntityLivingBase) facingEntity;
                }
              } else {
                rod = true;
                if (facingEntity instanceof EntityLivingBase) {
                  target = (EntityLivingBase) facingEntity;
                }
              }
            }
          }
        }
      } else if (getHealth(player) <= escapeHealthThreshold.getValue()) {
        rod = true;
      } else if (!facingEnemy.getValue()) {
        rod = true;
      }

      if (rod && pushTimer.hasTimeElapsed(pushDelay.getValue())) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || held.getItem() != Items.fishing_rod) {
          int rodSlot = findRod(36, 45);

          if (rodSlot == -1) {
            return;
          }

          switchBack = mc.thePlayer.inventory.currentItem;

          mc.thePlayer.inventory.currentItem = rodSlot;
          ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }

        rod();
      }
    }
  }

  private void rod() {
    int rod = findRod(36, 45);
    if (rod == -1) return;

    mc.thePlayer.inventory.currentItem = rod;
    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(rod);
    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);

    rodInUse = true;
    rodPullTimer.reset();
  }

  private int findRod(int startSlot, int endSlot) {
    for (int i = startSlot; i < endSlot; i++) {
      ItemStack stack = mc.thePlayer.inventory.mainInventory[i - 36];
      if (stack != null && stack.getItem() == Items.fishing_rod) {
        return i - 36;
      }
    }
    return -1;
  }

  private List<Entity> getAllNearbyEnemies() {
    EntityLivingBase player = mc.thePlayer;
    List<Entity> result = new ArrayList<>();
    if (player == null || mc.theWorld == null) return result;

    for (Object o : mc.theWorld.loadedEntityList) {
      if (!(o instanceof Entity)) continue;
      Entity entity = (Entity) o;
      if (SomeUtil.isSelected(entity)) {
        double distance = BackTrackUtil.getDistanceToEntityBox(entity);
        if (distance < activationDistance.getSecondValue()
            && distance > activationDistance.getValue()) {
          result.add(entity);
        }
      }
    }
    return result;
  }

  private float getHealth(EntityLivingBase entity) {
    float health = entity.getHealth();
    if (absorption.getValue()) {
      health += entity.getAbsorptionAmount();
    }
    return health;
  }

  private boolean killAuraBlocking() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    return killAura != null && killAura.isBlocking();
  }

  @Override
  public void onDisabled() {
    target = null;
    if (disSetInventory.getValue() && mc.thePlayer != null) {
      mc.thePlayer.inventory.currentItem = disSetInventorySlot.getValue();
      ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }
  }
}