package miau.module.modules.combat;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.world.WorldSettings;

public class LegitReach extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Intave", "FakePlayer"});
  public final BooleanProperty aura = new BooleanProperty("Aura", false);
  public final IntProperty pulseDelay = new IntProperty("PulseDelay", 200, 50, 500);
  public final IntProperty intaveTestHurtTime = new IntProperty("Intave-Packets", 5, 0, 30);

  private EntityOtherPlayerMP fakePlayer = null;
  private EntityLivingBase currentTarget = null;
  private boolean shown = false;
  private final TimerUtil pulseTimer = new TimerUtil();

  public LegitReach() {
    super("LegitReach", false);
  }

  @Override
  public void onDisabled() {
    this.removeFakePlayer();
  }

  private void removeFakePlayer() {
    if (this.fakePlayer != null) {
      this.currentTarget = null;
      mc.theWorld.removeEntity(this.fakePlayer);
      this.fakePlayer = null;
      this.shown = false;
    }
  }

  private void attackEntity(EntityLivingBase entity) {
    mc.thePlayer.swingItem();
    mc.thePlayer.sendQueue.addToSendQueue(new C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK));
    if (mc.playerController.getCurrentGameType() != WorldSettings.GameType.SPECTATOR) {
      mc.thePlayer.attackTargetEntityWithCurrentItem(entity);
    }
  }

  private void createFakePlayer(EntityLivingBase target) {
    if (mc.theWorld == null) return;
    NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(target.getUniqueID());
    if (playerInfo == null) return;
    EntityOtherPlayerMP faker = new EntityOtherPlayerMP(mc.theWorld, playerInfo.getGameProfile());
    faker.rotationYawHead = target.rotationYawHead;
    faker.renderYawOffset = target.renderYawOffset;
    faker.copyLocationAndAnglesFrom(target);
    faker.setHealth(target.getHealth());
    for (int index = 0; index < 5; index++) {
      ItemStack stack = target.getEquipmentInSlot(index);
      if (stack != null) faker.setCurrentItemOrArmor(index, stack);
    }
    mc.theWorld.addEntityToWorld(-1337, faker);
    this.fakePlayer = faker;
    this.shown = true;
  }

  private void updateFakePlayer(boolean intaveMode) {
    if (this.fakePlayer == null || this.currentTarget == null) return;
    EntityLivingBase faker = this.fakePlayer;
    EntityLivingBase target = this.currentTarget;
    if (!faker.isEntityAlive() || target.isDead || !target.isEntityAlive()) {
      this.removeFakePlayer();
      return;
    }
    faker.setHealth(target.getHealth());
    for (int index = 0; index < 5; index++) {
      ItemStack stack = target.getEquipmentInSlot(index);
      if (stack != null) faker.setCurrentItemOrArmor(index, stack);
    }
    boolean pulse = intaveMode
        ? mc.thePlayer.ticksExisted % this.intaveTestHurtTime.getValue() == 0
        : this.pulseTimer.hasTimeElapsed(this.pulseDelay.getValue());
    if (pulse) {
      faker.rotationYawHead = target.rotationYawHead;
      faker.renderYawOffset = target.renderYawOffset;
      faker.copyLocationAndAnglesFrom(target);
      this.pulseTimer.reset();
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    Entity target = event.getTarget();
    if (!(target instanceof EntityLivingBase)) return;
    EntityLivingBase entity = (EntityLivingBase) target;
    if (this.fakePlayer == null) {
      this.currentTarget = entity;
      this.createFakePlayer(entity);
    } else if (event.getTarget() == this.fakePlayer) {
      if (this.currentTarget != null) this.attackEntity(this.currentTarget);
    } else {
      this.removeFakePlayer();
      this.currentTarget = entity;
      this.createFakePlayer(entity);
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || this.currentTarget == null) {
      this.removeFakePlayer();
      return;
    }
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (this.aura.getValue() && (killAura == null || !killAura.isEnabled())) {
      this.removeFakePlayer();
      return;
    }
    String modeString = this.mode.getModeString();
    if (modeString.equals("Intave")) {
      this.updateFakePlayer(true);
      if (!this.shown && this.currentTarget != null
          && mc.getNetHandler().getPlayerInfo(this.currentTarget.getUniqueID()) != null) {
        this.createFakePlayer(this.currentTarget);
      }
    } else if (modeString.equals("FakePlayer")) {
      this.updateFakePlayer(false);
      if (!this.shown && this.currentTarget != null
          && mc.getNetHandler().getPlayerInfo(this.currentTarget.getUniqueID()) != null) {
        this.createFakePlayer(this.currentTarget);
      }
    }
  }
}
