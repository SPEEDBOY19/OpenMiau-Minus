package miau.module.modules.combat;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class AutoWeapon extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode =
      new ModeProperty("Mode", 0, new String[] {"Normal", "SwitchWeapon"});
  public final ModeProperty itemSelect =
      new ModeProperty(
          "Item",
          0,
          new String[] {"Sword", "Sword&Axe&Pickaxe", "Sword&Axe&EnchantedStick", "All"});
  public final BooleanProperty useCustomWeightToCalculateWeaponLevel =
      new BooleanProperty("UseCustomWeightToCalculateWeaponLevel", false);
  public final IntProperty damageWeight =
      new IntProperty(
          "DamageWeight", 70, 1, 100, () -> useCustomWeightToCalculateWeaponLevel.getValue());
  public final IntProperty knockbackWeight =
      new IntProperty(
          "KnockbackWeight", 20, 0, 100, () -> useCustomWeightToCalculateWeaponLevel.getValue());
  public final IntProperty fireAspectWeight =
      new IntProperty(
          "FireAspectWeight", 20, 0, 100, () -> useCustomWeightToCalculateWeaponLevel.getValue());
  public final IntProperty switchBackDelay =
      new IntProperty("SwitchBackDelay", 500, 1, 2000, () -> this.mode.getValue() == 1);
  public final BooleanProperty spoof = new BooleanProperty("SpoofItem", false);
  public final IntProperty spoofTicks =
      new IntProperty("SpoofTicks", 10, 1, 20, this.spoof::getValue);
  public final BooleanProperty cancelAttackWhenNotUsingBestWeapon =
      new BooleanProperty(
          "CancelAttackWhenNotUsingBestWeapon", false, () -> this.mode.getValue() == 0);
  public final BooleanProperty onlyOnKillAura = new BooleanProperty("OnlyOnKillAura", false);

  private boolean attackEnemy = false;
  private int bestWeaponSlot = -1;
  private int originalSlot = -1;
  private final TimerUtil switchTimer = new TimerUtil();

  public AutoWeapon() {
    super("AutoWeapon", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
    if (this.onlyOnKillAura.getValue() && !isKillAuraActive()) return;
    if (this.mode.getValue() == 1
        && this.bestWeaponSlot != -1
        && this.originalSlot != -1
        && this.switchTimer.hasTimeElapsed(this.switchBackDelay.getValue())) {
      if (this.spoof.getValue()) {
        Miau.slotComponent.setSlot(this.originalSlot, false);
      } else {
        mc.thePlayer.inventory.currentItem = this.originalSlot;
      }
      this.bestWeaponSlot = -1;
      this.originalSlot = -1;
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;
    if (this.onlyOnKillAura.getValue() && !isKillAuraActive()) return;
    this.attackEnemy = true;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.SEND) return;
    if (this.onlyOnKillAura.getValue() && !isKillAuraActive()) return;
    if (!(event.getPacket() instanceof C02PacketUseEntity)) return;
    C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
    if (packet.getAction() != C02PacketUseEntity.Action.ATTACK || !this.attackEnemy) return;
    this.attackEnemy = false;

    int bestSlot = -1;
    double bestScore = -1.0D;
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack == null || !isWeapon(stack)) continue;
      double score = getLevelScore(stack);
      if (score > bestScore) {
        bestScore = score;
        bestSlot = i;
      }
    }
    if (bestSlot == -1) return;

    boolean isHoldingBest = bestSlot == mc.thePlayer.inventory.currentItem;

    if (this.cancelAttackWhenNotUsingBestWeapon.getValue() && !isHoldingBest) {
      event.setCancelled(true);
    }

    if (isHoldingBest) return;

    if (this.mode.getValue() == 0) {
      this.selectSlot(bestSlot);
    } else {
      this.bestWeaponSlot = bestSlot;
      this.originalSlot = mc.thePlayer.inventory.currentItem;
      this.switchTimer.reset();

      this.selectSlot(bestSlot);

      int secondBest = this.originalSlot;
      double secondScore = -1.0D;
      for (int i = 0; i < 9; i++) {
        if (i == bestSlot) continue;
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
        if (stack == null || !isWeapon(stack)) continue;
        double score = getLevelScore(stack);
        if (score > secondScore) {
          secondScore = score;
          secondBest = i;
        }
      }
      this.selectSlot(secondBest);
    }

    PacketUtil.sendPacket(event.getPacket());
    event.setCancelled(true);
  }

  private void selectSlot(int slot) {
    if (this.spoof.getValue()) {
      Miau.slotComponent.setSlot(slot, false);
    } else {
      mc.thePlayer.inventory.currentItem = slot;
      ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }
  }

  private boolean isWeapon(ItemStack stack) {
    Item item = stack.getItem();
    switch (this.itemSelect.getValue()) {
      case 0:
        return item instanceof ItemSword;
      case 1:
        return item instanceof ItemSword || item instanceof ItemTool;
      case 2:
        return item instanceof ItemSword
            || item instanceof ItemTool
            || (EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack) >= 1
                && (item == Items.stick || item == Items.blaze_rod));
      default:
        return item != null;
    }
  }

  private double getLevelScore(ItemStack stack) {
    Item item = stack.getItem();
    double attackDamage = 1.0D;
    if (item instanceof ItemSword) {
      attackDamage = ((ItemSword) item).getDamageVsEntity();
    } else if (item instanceof ItemTool) {
      attackDamage = ((ItemTool) item).getToolMaterial().getDamageVsEntity();
    }
    if (this.useCustomWeightToCalculateWeaponLevel.getValue()) {
      return attackDamage * this.damageWeight.getValue()
          + EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack)
              * this.knockbackWeight.getValue()
          + EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack)
              * this.fireAspectWeight.getValue();
    }
    return attackDamage;
  }

  @Override
  public void onDisabled() {
    this.bestWeaponSlot = -1;
    this.originalSlot = -1;
    this.attackEnemy = false;
  }

  private boolean isKillAuraActive() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    return killAura != null && killAura.isEnabled();
  }
}
