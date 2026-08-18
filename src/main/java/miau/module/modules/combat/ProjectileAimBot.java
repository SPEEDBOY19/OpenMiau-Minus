package miau.module.modules.combat;

import java.awt.Color;
import java.util.Comparator;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.Render3DEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.management.RotationState;
import miau.module.Module;
import miau.module.modules.misc.AntiBot;
import miau.module.modules.render.HUD;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.MoveUtil;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class ProjectileAimBot extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty bow = new BooleanProperty("bow", true);
  public final BooleanProperty egg = new BooleanProperty("egg", true);
  public final BooleanProperty snowball = new BooleanProperty("snowball", true);
  public final BooleanProperty pearl = new BooleanProperty("ender-pearl", false);
  public final BooleanProperty otherItems = new BooleanProperty("other-items", false);
  public final FloatProperty range = new FloatProperty("range", 10.0F, 0.0F, 30.0F);
  public final BooleanProperty throughWalls = new BooleanProperty("through-walls", false);
  public final FloatProperty throughWallsRange =
      new FloatProperty("through-walls-range", 10.0F, 0.0F, 30.0F, () -> throughWalls.getValue());
  public final ModeProperty priority =
      new ModeProperty("priority", 2, new String[] {"Health", "Distance", "Direction"});
  public final ModeProperty gravityType =
      new ModeProperty("gravity-type", 1, new String[] {"None", "Projectile"});
  public final BooleanProperty predict =
      new BooleanProperty("predict", true, () -> gravityType.getValue() == 1);
  public final FloatProperty predictSize =
      new FloatProperty(
          "predict-size",
          2.0F,
          0.1F,
          5.0F,
          () -> predict.getValue() && gravityType.getValue() == 1);
  public final BooleanProperty mark = new BooleanProperty("mark", true);
  public final BooleanProperty silentAim = new BooleanProperty("silent-aim", true);
  public final ModeProperty moveFix =
      new ModeProperty(
          "move-fix", 1, new String[] {"NONE", "SILENT", "STRICT"}, () -> silentAim.getValue());
  public final ModeProperty showTarget =
      new ModeProperty("show-target", 0, new String[] {"NONE", "DEFAULT", "HUD"});

  public final BooleanProperty targetPlayers = new BooleanProperty("target-players", true);
  public final BooleanProperty targetInvisibles =
      new BooleanProperty("target-invisibles", false, this.targetPlayers::getValue);
  public final BooleanProperty targetBosses = new BooleanProperty("target-bosses", false);
  public final BooleanProperty targetMobs = new BooleanProperty("target-mobs", false);
  public final BooleanProperty targetAnimals = new BooleanProperty("target-animals", false);
  public final BooleanProperty targetGolems = new BooleanProperty("target-golems", false);
  public final BooleanProperty targetSilverfish = new BooleanProperty("target-silverfish", false);
  public final BooleanProperty targetTeams = new BooleanProperty("target-teams", true);

  public final BooleanProperty auto = new BooleanProperty("auto", false);
  public final IntProperty autoAmount =
      new IntProperty("auto-amount", 1, 1, 10, this.auto::getValue);
  public final BooleanProperty weaponOnly =
      new BooleanProperty("weapon-only", true, this.auto::getValue);
  public final FloatProperty autoDelay =
      new FloatProperty("auto-delay", 500.0F, 0.0F, 2000.0F, this.auto::getValue);

  private EntityLivingBase target;
  private int throwState = 0;
  private int lastSlot = -1;
  private long lastThrowTime = 0L;
  private int throwsRemaining = 0;
  private boolean hasRotated = false;

  public ProjectileAimBot() {
    super("ProjectileAimBot", false, false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.PRE
        || mc.thePlayer == null
        || mc.theWorld == null) return;

    if (auto.getValue()) {
      if (weaponOnly.getValue()
          && !(mc.thePlayer.getHeldItem() != null
              && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)) {
        if (this.throwState != 0 || this.lastSlot != -1) this.switchBack();
        resetAutoState();
        return;
      }
      if (!this.hasProjectile()) {
        resetAutoState();
        this.switchBack();
        return;
      }
      if (this.throwState == 0) {
        this.target = getTarget();
        if (this.target == null) return;
        KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.isInRange(this.target)) {
          return;
        }
        if (System.currentTimeMillis() - this.lastThrowTime < autoDelay.getValue()) return;
        this.throwsRemaining = this.autoAmount.getValue();
        this.throwState = 1;
        this.hasRotated = false;
      }
      if (this.throwState == 1) {
        this.switchToProjectile();
        this.throwState = 2;
      } else if (this.throwState == 2) {
        if (this.throwsRemaining > 0) {
          ItemStack stack = mc.thePlayer.getHeldItem();
          if (stack == null) {
            this.throwState = 4;
            return;
          }
          float[] rotations =
              gravityType.getValue() == 1
                  ? getProjectileRotations(this.target, stack)
                  : getDirectRotations(this.target);
          if (rotations != null) {
            event.setRotation(rotations[0], rotations[1], 3);
            event.setPervRotation(rotations[0], 3);
            this.hasRotated = true;
            this.throwState = 3;
          } else {
            this.throwState = 4;
          }
        } else {
          this.throwState = 4;
        }
      } else if (this.throwState == 3) {
        this.throwProjectile();
        this.throwsRemaining--;
        this.throwState = this.throwsRemaining > 0 ? 2 : 4;
      } else if (this.throwState == 4) {
        this.switchBack();
        resetAutoState();
        this.lastThrowTime = System.currentTimeMillis();
      }
      return;
    }

    target = null;
    ItemStack stack = mc.thePlayer.getHeldItem();
    if (stack == null || !isValidHeldItem(stack)) return;
    if (stack.getItem() instanceof ItemBow && !mc.thePlayer.isUsingItem()) return;

    target = getTarget();
    if (target == null) return;

    float[] rotations =
        gravityType.getValue() == 1
            ? getProjectileRotations(target, stack)
            : getDirectRotations(target);
    if (rotations == null) return;

    float yaw = rotations[0];
    float pitch = rotations[1];
    event.setRotation(yaw, pitch, 3);
    if (silentAim.getValue()) {
      if (moveFix.getValue() != 0) {
        event.setPervRotation(yaw, 3);
      }
    } else {
      event.setPervRotation(yaw, 3);
      Miau.rotationManager.setRotation(yaw, pitch, 3, true);
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (!isEnabled() || !silentAim.getValue()) return;
    if (moveFix.getValue() == 1
        && RotationState.isActived()
        && RotationState.getPriority() == 3.0F
        && MoveUtil.isForwardPressed()) {
      MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled() || target == null || !TeamUtil.isEntityLoaded(target)) return;
    if (mark.getValue()) {
      drawPlatform(target, new Color(37, 126, 255, 70), event.getPartialTicks());
    }
    if (showTarget.getValue() != 0) {
      Color color =
          showTarget.getValue() == 2
              ? ((HUD) Miau.moduleManager.modules.get(HUD.class))
                  .getColor(System.currentTimeMillis())
              : new Color(255, 0, 0);
      RenderUtil.drawEntityBox(target, color.getRed(), color.getGreen(), color.getBlue());
    }
  }

  private boolean isValidHeldItem(ItemStack stack) {
    Item item = stack.getItem();
    if (item instanceof ItemBow) return bow.getValue();
    if (item instanceof ItemEgg) return egg.getValue();
    if (item instanceof ItemSnowball) return snowball.getValue();
    if (item instanceof ItemEnderPearl) return pearl.getValue();
    return otherItems.getValue();
  }

  private void resetAutoState() {
    this.target = null;
    this.throwState = 0;
    this.throwsRemaining = 0;
    this.hasRotated = false;
  }

  private boolean hasProjectile() {
    return getProjectileSlot() != -1;
  }

  private int getProjectileSlot() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && isValidHeldItem(stack) && !(stack.getItem() instanceof ItemBow))
        return i;
    }
    return -1;
  }

  private void switchToProjectile() {
    int slot = getProjectileSlot();
    if (slot != -1) {
      lastSlot = mc.thePlayer.inventory.currentItem;
      mc.thePlayer.inventory.currentItem = slot;
    }
  }

  private void switchBack() {
    if (lastSlot != -1) {
      mc.thePlayer.inventory.currentItem = lastSlot;
      lastSlot = -1;
    }
  }

  private void throwProjectile() {
    int slot = mc.thePlayer.inventory.currentItem;
    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
    if (stack != null) {
      mc.getNetHandler()
          .addToSendQueue(
              new net.minecraft.network.play.client.C08PacketPlayerBlockPlacement(stack));
    }
  }

  private EntityLivingBase getTarget() {
    return mc.theWorld.loadedEntityList.stream()
        .filter(entity -> entity instanceof EntityLivingBase)
        .map(entity -> (EntityLivingBase) entity)
        .filter(this::isValidTarget)
        .min(Comparator.comparingDouble(this::priorityValue))
        .orElse(null);
  }

  private boolean isValidTarget(EntityLivingBase entity) {
    if (!isValid(entity)) return false;
    if (RotationUtil.distanceToEntity(entity) > range.getValue()) return false;
    if (throughWalls.getValue()) {
      return RotationUtil.distanceToEntity(entity) <= throughWallsRange.getValue()
          || RotationUtil.rayTrace(entity) == null;
    }
    return RotationUtil.rayTrace(entity) == null;
  }

  private boolean isValid(EntityLivingBase entityLivingBase) {
    if (entityLivingBase == null || mc.theWorld == null || mc.thePlayer == null) return false;
    if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) return false;
    if (entityLivingBase == mc.thePlayer || entityLivingBase == mc.thePlayer.ridingEntity) {
      return false;
    }
    if (entityLivingBase == mc.getRenderViewEntity()
        || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
      return false;
    }
    if (entityLivingBase.deathTime > 0) return false;
    if (entityLivingBase instanceof EntityOtherPlayerMP) {
      return isValidPlayer((EntityPlayer) entityLivingBase);
    }
    if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
      return targetBosses.getValue();
    }
    if (entityLivingBase instanceof EntityMob || entityLivingBase instanceof EntitySlime) {
      if (entityLivingBase instanceof EntitySilverfish) {
        return targetSilverfish.getValue() && allowTeamColor(entityLivingBase);
      }
      return targetMobs.getValue();
    }
    if (entityLivingBase instanceof EntityAnimal
        || entityLivingBase instanceof EntityBat
        || entityLivingBase instanceof EntitySquid
        || entityLivingBase instanceof EntityVillager) {
      return targetAnimals.getValue();
    }
    if (entityLivingBase instanceof EntityIronGolem) {
      return targetGolems.getValue() && allowTeamColor(entityLivingBase);
    }
    return false;
  }

  private boolean isValidPlayer(EntityPlayer player) {
    if (!targetPlayers.getValue()) return false;
    if (player.isPlayerSleeping()) return false;
    boolean isInvisible = player.isInvisible();
    if (isInvisible && !targetInvisibles.getValue()) return false;
    if (TeamUtil.isFriend(player)) return false;
    if (!TeamUtil.isTarget(player)) return false;
    return allowSameTeam(player) && (isInvisible || !AntiBot.isBot(player));
  }

  private boolean allowTeamColor(EntityLivingBase entityLivingBase) {
    return targetTeams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase);
  }

  private boolean allowSameTeam(EntityPlayer player) {
    return targetTeams.getValue() || !TeamUtil.isSameTeam(player);
  }

  private double priorityValue(EntityLivingBase entity) {
    switch (priority.getValue()) {
      case 0:
        return TeamUtil.getHealthScore(entity);
      case 1:
        return RotationUtil.distanceToEntity(entity);
      case 2:
      default:
        return Math.abs(
            MathHelper.wrapAngleTo180_float(getYawTo(entity) - mc.thePlayer.rotationYaw));
    }
  }

  private float[] getDirectRotations(EntityLivingBase entity) {
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
    double x = entity.posX - eyes.xCoord;
    double y = entity.posY + entity.getEyeHeight() - eyes.yCoord;
    double z = entity.posZ - eyes.zCoord;
    return RotationUtil.getRotations(
        x, y, z, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 180.0F, 0.0F);
  }

  private float[] getProjectileRotations(EntityLivingBase entity, ItemStack stack) {
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
    double targetX = entity.posX;
    double targetY = entity.posY + entity.getEyeHeight();
    double targetZ = entity.posZ;

    if (predict.getValue()) {
      double predictTicks = predictSize.getValue();
      targetX += (entity.posX - entity.prevPosX) * predictTicks;
      targetY += (entity.posY - entity.prevPosY) * predictTicks;
      targetZ += (entity.posZ - entity.prevPosZ) * predictTicks;
    }

    double diffX = targetX - eyes.xCoord;
    double diffY = targetY - eyes.yCoord;
    double diffZ = targetZ - eyes.zCoord;
    double horizontal = Math.sqrt(diffX * diffX + diffZ * diffZ);
    float velocity = getProjectileVelocity(stack);
    float gravity = getProjectileGravity(stack);

    float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
    float pitch;
    if (gravity <= 0.0F) {
      pitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontal));
    } else {
      double velocitySq = velocity * velocity;
      double root =
          velocitySq * velocitySq
              - gravity * (gravity * horizontal * horizontal + 2.0D * diffY * velocitySq);
      if (root < 0.0D) return getDirectRotations(entity);
      pitch =
          (float)
              -Math.toDegrees(Math.atan((velocitySq - Math.sqrt(root)) / (gravity * horizontal)));
    }
    return new float[] {
      RotationUtil.quantizeAngle(yaw),
      RotationUtil.quantizeAngle(MathHelper.clamp_float(pitch, -90.0F, 90.0F))
    };
  }

  private float getProjectileVelocity(ItemStack stack) {
    if (stack.getItem() instanceof ItemBow) {
      int useDuration = mc.thePlayer.getItemInUseDuration();
      float charge = useDuration / 20.0F;
      charge = (charge * charge + charge * 2.0F) / 3.0F;
      return Math.min(charge, 1.0F) * 3.0F;
    }
    return 0.5F;
  }

  private float getProjectileGravity(ItemStack stack) {
    return stack.getItem() instanceof ItemBow ? 0.05F : 0.03F;
  }

  private float getYawTo(Entity entity) {
    double x = entity.posX - mc.thePlayer.posX;
    double z = entity.posZ - mc.thePlayer.posZ;
    return (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
  }

  private void drawPlatform(Entity entity, Color color, float partialTicks) {
    double renderX =
        entity.lastTickPosX
            + (entity.posX - entity.lastTickPosX) * partialTicks
            - mc.getRenderManager().viewerPosX;
    double renderY =
        entity.lastTickPosY
            + (entity.posY - entity.lastTickPosY) * partialTicks
            - mc.getRenderManager().viewerPosY;
    double renderZ =
        entity.lastTickPosZ
            + (entity.posZ - entity.lastTickPosZ) * partialTicks
            - mc.getRenderManager().viewerPosZ;
    AxisAlignedBB box = entity.getEntityBoundingBox();
    double radius = Math.max(box.maxX - box.minX, box.maxZ - box.minZ) * 0.75D;

    GlStateManager.pushMatrix();
    GlStateManager.translate(renderX, renderY + 0.02D, renderZ);
    RenderUtil.drawLine((float) -radius, 0.0F, (float) radius, 0.0F, 2.0F, color.getRGB());
    RenderUtil.drawLine(0.0F, (float) -radius, 0.0F, (float) radius, 2.0F, color.getRGB());
    GlStateManager.popMatrix();
  }

  public boolean hasTarget() {
    return target != null && mc.thePlayer != null && RotationUtil.rayTrace(target) == null;
  }

  @Override
  public void onDisabled() {
    switchBack();
    resetAutoState();
    target = null;
    RotationState.applyState(false, 0.0F, 0.0F, 0.0F, Integer.MIN_VALUE);
  }
}
