package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.impl.KnockbackEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.PercentProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.client.KeyBindUtil;
import miau.util.player.RayCastUtil;
import miau.util.player.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.util.MathHelper;

public class JumpReset extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private boolean setJump;
  private boolean ignoreNext;
  private boolean aiming;
  private int lastHurtTime;
  private double lastFallDistance;

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"STANDARD", "POLAR"});
  public final PercentProperty chance = new PercentProperty("chance", 100);
  public final BooleanProperty mouseDown = new BooleanProperty("mouse-down", false);
  public final BooleanProperty movingForward = new BooleanProperty("moving-forward", true);
  public final BooleanProperty aimingOnPlayer = new BooleanProperty("aiming-on-player", true);

  public final FloatProperty exitRange = new FloatProperty("exit-range", 3.0F, 2.0F, 6.0F, () -> this.mode.getValue() == 1);
  public final IntProperty predictionTicks = new IntProperty("prediction-ticks", 2, 0, 5, () -> this.mode.getValue() == 1);

  public JumpReset() {
    super("JumpReset", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.mode.getValue() == 1 /* POLAR */) return;
    if (event.getType() == EventType.PRE) {
      int hurtTime = mc.thePlayer.hurtTime;
      boolean onGround = mc.thePlayer.onGround;

      if (onGround && lastFallDistance > 3.0 && !mc.thePlayer.capabilities.allowFlying) {
        ignoreNext = true;
      }

      if (hurtTime > lastHurtTime) {
        boolean mouseDownCheck = KeyBindUtil.isKeyDown(-100) || !mouseDown.getValue();
        boolean aimingCheck = aiming || !aimingOnPlayer.getValue();
        boolean forwardCheck =
            KeyBindUtil.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
                || !movingForward.getValue();

        if (!ignoreNext
            && onGround
            && aimingCheck
            && forwardCheck
            && mouseDownCheck
            && !mc.thePlayer.isBurning()
            && Math.random() * 100.0 < chance.getValue()
            && !hasBadEffect()) {
          setJump = true;
          KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
        }
        ignoreNext = false;
      }

      lastHurtTime = hurtTime;
      lastFallDistance = mc.thePlayer.fallDistance;

    } else if (event.getType() == EventType.POST) {
      if (this.mode.getValue() == 1 /* POLAR */) return;
      if (setJump && !KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
        setJump = false;
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
      }
    }
  }

  @EventTarget
  public void onKnockback(KnockbackEvent event) {
    if (this.isEnabled() && this.mode.getValue() == 1 /* POLAR */) {
      if (mc.thePlayer.onGround && event.getY() > 0) {
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
      }
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled() || this.mode.getValue() != 1 /* POLAR */) return;

    EntityLivingBase target = null;
    double closestDist = Double.MAX_VALUE;
    for (net.minecraft.entity.Entity entity : mc.theWorld.loadedEntityList) {
      if (entity instanceof net.minecraft.entity.player.EntityPlayer && entity != mc.thePlayer 
          && !miau.util.player.TeamUtil.isSameTeam((net.minecraft.entity.player.EntityPlayer) entity) 
          && !miau.util.player.TeamUtil.isFriend((net.minecraft.entity.player.EntityPlayer) entity)) {
        double dist = mc.thePlayer.getDistanceToEntity(entity);
        if (dist <= 6.0 && dist < closestDist) {
          closestDist = dist;
          target = (EntityLivingBase) entity;
        }
      }
    }

    if (target != null && shouldJump(target)) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
    }
  }

  private boolean shouldJump(EntityLivingBase target) {
    SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
    int simHurtTime = mc.thePlayer.hurtTime;

    int predTicks = this.predictionTicks.getValue();
    for (int i = 0; i < predTicks; i++) {
      sim.tick();
      if (simHurtTime > 0) simHurtTime--;
    }

    if (simHurtTime <= 0) {
      ItemStack targetHeld = target.getHeldItem();
      int knockbackLevel = 0;
      if (targetHeld != null) {
        knockbackLevel = EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.knockback.effectId, targetHeld);
      }
      double kb = knockbackLevel + (target.isSprinting() ? 1.0 : 0.0);
      float yawHead = target.rotationYawHead;
      sim.motionX += -MathHelper.sin(yawHead * (float) Math.PI / 180.0F) * kb * 0.5;
      sim.motionZ += MathHelper.cos(yawHead * (float) Math.PI / 180.0F) * kb * 0.5;
      sim.motionY += 0.1;
    }

    double targetDeltaX = target.posX - target.lastTickPosX;
    double targetDeltaY = target.posY - target.lastTickPosY;
    double targetDeltaZ = target.posZ - target.lastTickPosZ;
    double predTargetX = target.posX + targetDeltaX;
    double predTargetY = target.posY + targetDeltaY;
    double predTargetZ = target.posZ + targetDeltaZ;

    double dx = sim.getPos().xCoord - predTargetX;
    double dy = sim.getPos().yCoord - predTargetY;
    double dz = sim.getPos().zCoord - predTargetZ;
    double predDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

    double currentDist = mc.thePlayer.getDistanceToEntity(target);

    double exitRangeVal = this.exitRange.getValue();
    return mc.thePlayer.isSprinting() && predDist > exitRangeVal && currentDist <= exitRangeVal;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.mode.getValue() == 1 /* POLAR */) return;
    if (event.getType() != EventType.SEND) return;
    if (!(event.getPacket() instanceof C03PacketPlayer)) return;

    C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();

    float yaw;
    float pitch;

    if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
      yaw = ((C03PacketPlayer.C06PacketPlayerPosLook) packet).getYaw();
      pitch = ((C03PacketPlayer.C06PacketPlayerPosLook) packet).getPitch();
    } else if (packet instanceof C03PacketPlayer.C05PacketPlayerLook) {
      yaw = ((C03PacketPlayer.C05PacketPlayerLook) packet).getYaw();
      pitch = ((C03PacketPlayer.C05PacketPlayerLook) packet).getPitch();
    } else {
      return;
    }

    net.minecraft.util.MovingObjectPosition mop =
        RayCastUtil.rayCast(yaw, pitch, 5.0, 0.0f, mc.thePlayer);

    if (mop != null
        && mop.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY
        && mop.entityHit instanceof EntityOtherPlayerMP) {
      aiming = true;
    } else {
      aiming = false;
    }
  }

  private boolean hasBadEffect() {
    for (net.minecraft.potion.PotionEffect effect : mc.thePlayer.getActivePotionEffects()) {
      int potionId = effect.getPotionID();
      if (potionId == Potion.jump.getId()
          || potionId == Potion.poison.getId()
          || potionId == Potion.wither.getId()) {
        return true;
      }
    }
    return false;
  }
}
