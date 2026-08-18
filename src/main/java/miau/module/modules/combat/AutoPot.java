package miau.module.modules.combat;

import java.util.List;
import java.util.Random;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public class AutoPot extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final FloatProperty health =
      new FloatProperty(
          "Health",
          15.0F,
          1.0F,
          20.0F,
          () -> this.healPotion.getValue() || this.regenerationPotion.getValue());
  private final IntProperty delay = new IntProperty("Delay", 500, 500, 1000);
  private final BooleanProperty healPotion = new BooleanProperty("HealPotion", true);
  private final BooleanProperty regenerationPotion = new BooleanProperty("RegenPotion", true);
  private final BooleanProperty fireResistancePotion = new BooleanProperty("FireResPotion", true);
  private final BooleanProperty strengthPotion = new BooleanProperty("StrengthPotion", true);
  private final BooleanProperty jumpPotion = new BooleanProperty("JumpPotion", true);
  private final BooleanProperty speedPotion = new BooleanProperty("SpeedPotion", true);
  private final BooleanProperty openInventory = new BooleanProperty("OpenInv", false);
  private final BooleanProperty simulateInventory =
      new BooleanProperty("SimulateInventory", true, () -> !this.openInventory.getValue());
  private final FloatProperty groundDistance = new FloatProperty("GroundDistance", 2.0F, 0.0F, 5.0F);
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Normal", "Jump", "Port"});
  private final TimerUtil msTimer = new TimerUtil();
  private final Random random = new Random();

  public AutoPot() {
    super("AutoPot", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;
    if (event.getType() != EventType.PRE) return;
    if (!this.msTimer.hasTimeElapsed((long) this.delay.getValue())) return;
    if (mc.playerController.isInCreativeMode()) return;

    int potionInHotbar = this.findPotion(0, 8);
    if (potionInHotbar != -1) {
      if (mc.thePlayer.onGround) {
        switch (this.mode.getModeString()) {
          case "Jump":
            if (!mc.gameSettings.keyBindJump.isKeyDown()) {
              mc.thePlayer.jump();
            }
            break;
          case "Port":
            mc.thePlayer.moveEntity(0.0, 0.42, 0.0);
            break;
        }
      }

      Miau.slotComponent.setSlot(potionInHotbar, false);
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(potionInHotbar);
      if (stack == null) return;

      if (mc.thePlayer.rotationPitch <= 80.0F) {
        float pitch = 80.0F + this.random.nextFloat() * 10.0F;
        Miau.rotationManager.setSilentRotation(mc.thePlayer.rotationYaw, pitch, 3);
      }

      PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
      this.msTimer.reset();
      return;
    }

    int potionInInventory = this.findPotion(9, 35);
    if (potionInInventory == -1) return;
    if (!this.hasSpaceInHotbar()) return;

    mc.playerController.windowClick(0, potionInInventory, 0, 1, mc.thePlayer);
    this.msTimer.reset();
  }

  private int findPotion(int startSlot, int endSlot) {
    for (int i = startSlot; i <= endSlot; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack == null) continue;
      if (!(stack.getItem() instanceof ItemPotion)) continue;
      if (!ItemPotion.isSplash(stack.getMetadata())) continue;

      ItemPotion itemPotion = (ItemPotion) stack.getItem();
      List<PotionEffect> effects = itemPotion.getEffects(stack);
      if (effects == null) continue;

      for (PotionEffect potionEffect : effects) {
        if (this.healPotion.getValue()
            && potionEffect.getPotionID() == Potion.heal.getId()
            && mc.thePlayer.getHealth() <= this.health.getValue()) {
          return i;
        }
      }

      if (!mc.thePlayer.isPotionActive(Potion.regeneration)) {
        for (PotionEffect potionEffect : effects) {
          if (this.regenerationPotion.getValue()
              && potionEffect.getPotionID() == Potion.regeneration.getId()
              && mc.thePlayer.getHealth() <= this.health.getValue()) {
            return i;
          }
        }
      }

      if (!mc.thePlayer.isPotionActive(Potion.fireResistance)) {
        for (PotionEffect potionEffect : effects) {
          if (this.fireResistancePotion.getValue()
              && potionEffect.getPotionID() == Potion.fireResistance.getId()) {
            return i;
          }
        }
      }

      if (!mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
        for (PotionEffect potionEffect : effects) {
          if (this.speedPotion.getValue() && potionEffect.getPotionID() == Potion.moveSpeed.getId()) {
            return i;
          }
        }
      }

      if (!mc.thePlayer.isPotionActive(Potion.jump)) {
        for (PotionEffect potionEffect : effects) {
          if (this.jumpPotion.getValue() && potionEffect.getPotionID() == Potion.jump.getId()) {
            return i;
          }
        }
      }

      if (!mc.thePlayer.isPotionActive(Potion.damageBoost)) {
        for (PotionEffect potionEffect : effects) {
          if (this.strengthPotion.getValue()
              && potionEffect.getPotionID() == Potion.damageBoost.getId()) {
            return i;
          }
        }
      }
    }
    return -1;
  }

  private boolean hasSpaceInHotbar() {
    for (int i = 0; i < 9; i++) {
      if (mc.thePlayer.inventory.getStackInSlot(i) == null) return true;
    }
    return false;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}
