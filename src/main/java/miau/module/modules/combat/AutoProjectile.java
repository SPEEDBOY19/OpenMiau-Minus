package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.network.PacketUtil;
import miau.util.player.RotationUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.MovingObjectPosition;

public class AutoProjectile extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final BooleanProperty facingEnemy = new BooleanProperty("FacingEnemy", true);
  public final FloatProperty range = new FloatProperty("Range", 8.0F, 1.0F, 20.0F);
  private final IntProperty throwDelay = new IntProperty("ThrowDelay", 1250, 50, 2000);
  private final IntProperty switchBackDelay = new IntProperty("SwitchBackDelay", 500, 50, 2000);
  private final BooleanProperty onlyOnKillAura = new BooleanProperty("OnlyOnKillAura", false);
  private final TimerUtil throwTimer = new TimerUtil();
  private final TimerUtil projectilePullTimer = new TimerUtil();
  private boolean projectileInUse = false;
  private int switchBack = -1;

  public AutoProjectile() {
    super("AutoProjectile", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    if (event.getType() != EventType.PRE) return;
    if (this.onlyOnKillAura.getValue()) {
      KillAura killAura = (KillAura) miau.Miau.moduleManager.modules.get(KillAura.class);
      if (killAura == null || !killAura.isEnabled()) return;
    }

    boolean usingProjectile =
        (mc.thePlayer.isUsingItem()
                && mc.thePlayer.getHeldItem() != null
                && (mc.thePlayer.getHeldItem().getItem() == Items.snowball
                    || mc.thePlayer.getHeldItem().getItem() == Items.egg))
            || this.projectileInUse;

    if (usingProjectile) {
      if (this.projectilePullTimer.hasTimeElapsed((long) this.switchBackDelay.getValue())) {
        if (this.switchBack != -1 && mc.thePlayer.inventory.currentItem != this.switchBack) {
          mc.thePlayer.inventory.currentItem = this.switchBack;
          mc.playerController.updateController();
        } else {
          mc.thePlayer.stopUsingItem();
        }
        this.switchBack = -1;
        this.projectileInUse = false;
        this.throwTimer.reset();
      }
      return;
    }

    boolean throwProjectile = false;

    if (this.facingEnemy.getValue()) {
      Entity facingEntity = mc.objectMouseOver != null ? mc.objectMouseOver.entityHit : null;

      if (facingEntity == null) {
        facingEntity = this.raycastEntity(this.range.getValue());
      }
      if (facingEntity != null
          && RotationUtil.distanceToEntity(facingEntity) <= 0.0) {
        facingEntity = null;
      }

      if (facingEntity != null && this.isSelected(facingEntity)) {
        throwProjectile = true;
      }
    } else {
      throwProjectile = true;
    }

    if (throwProjectile && this.throwTimer.hasTimeElapsed((long) this.throwDelay.getValue())) {
      if (mc.thePlayer.getHeldItem() == null
          || (mc.thePlayer.getHeldItem().getItem() != Items.snowball
              && mc.thePlayer.getHeldItem().getItem() != Items.egg)) {
        int projectile = this.findProjectile();
        if (projectile == -1) return;

        this.switchBack = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = projectile;
        mc.playerController.updateController();
      }

      this.throwProjectile();
    }
  }

  private void throwProjectile() {
    int projectile = this.findProjectile();
    if (projectile == -1) return;

    mc.thePlayer.inventory.currentItem = projectile;
    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(projectile);
    if (stack != null) {
      PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
    }

    this.projectileInUse = true;
    this.projectilePullTimer.reset();
  }

  private int findProjectile() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null
          && (stack.getItem() == Items.snowball || stack.getItem() == Items.egg)) {
        return i;
      }
    }
    return -1;
  }

  private Entity raycastEntity(float range) {
    Entity best = null;
    double bestAngle = Double.MAX_VALUE;
    for (Object o : mc.theWorld.loadedEntityList) {
      if (!(o instanceof EntityLivingBase)) continue;
      EntityLivingBase entity = (EntityLivingBase) o;
      if (!this.isSelected(entity)) continue;
      if (RotationUtil.distanceToEntity(entity) > range) continue;
      if (RotationUtil.rayTrace(entity) != null) continue;
      float angle = RotationUtil.angleToEntity(entity);
      if (angle < bestAngle) {
        bestAngle = angle;
        best = entity;
      }
    }
    return best;
  }

  private boolean isSelected(Entity entity) {
    if (entity == null || !(entity instanceof EntityLivingBase)) return false;
    if (entity == mc.thePlayer || entity.isDead) return false;
    if (entity instanceof EntityArmorStand) return false;
    return true;
  }

  @Override
  public void onDisabled() {
    this.throwTimer.reset();
    this.projectilePullTimer.reset();
    this.projectileInUse = false;
    this.switchBack = -1;
  }
}
