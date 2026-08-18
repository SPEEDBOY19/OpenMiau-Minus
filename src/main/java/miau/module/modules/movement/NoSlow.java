package miau.module.modules.movement;

import java.util.ArrayList;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.RightClickMouseEvent;
import miau.event.impl.UpdateEvent;
import miau.module.Module;
import miau.module.modules.movement.noslow.*;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.IntProperty;
import miau.util.player.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemPotion;

public class NoSlow extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode =
      new ModeProperty(
          "Mode",
          0,
          new String[] {
            "Vanilla",
            "Intave",
            "Old Grim",
            "Grim Test",
            "Hypixel",
            "NCP",
            "Old Intave",
            "Exhi",
            "New Grim",
            "Vulcan",
            "Grim 1.9",
            "GrimSemi"
          });
  public final IntProperty grimTestMaxTicks =
      new IntProperty("grim-test-max-ticks", 5, 1, 20, () -> this.mode.getValue() == 3);
  public final IntProperty grimInterval =
      new IntProperty("grim-interval", 4, 1, 20, () -> this.mode.getValue() == 8);
  public final IntProperty vulcanInterval =
      new IntProperty("vulcan-interval", 5, 1, 20, () -> this.mode.getValue() == 9);
  public final IntProperty grimSemiInterval =
      new IntProperty("grim-semi-interval", 5, 3, 20, () -> this.mode.getValue() == 11);
  public final BooleanProperty hypixelJump =
      new BooleanProperty("hypixel-jump", true, () -> this.mode.getValue() == 4);
  public final BooleanProperty swordValue = new BooleanProperty("sword", true);
  public final BooleanProperty foodValue = new BooleanProperty("food", true);
  public final BooleanProperty potionValue = new BooleanProperty("potion", true);
  public final BooleanProperty bowValue = new BooleanProperty("bow", true);
  public final BooleanProperty antiSwitch = new BooleanProperty("anti-switch", false);

  private final List<NoSlowMode> modes = new ArrayList<>();

  public NoSlow() {
    super("NoSlow", false);
    modes.add(new OMVanillaNoSlow("Vanilla", this));
    modes.add(new OMIntaveNoSlow("Intave", this));
    modes.add(new OMGrimNoSlow("Old Grim", this));
    modes.add(new OMGrimTestNoSlow("Grim Test", this));
    modes.add(new OMHypixelNoSlow("Hypixel", this));
    modes.add(new OMNCPNoSlow("NCP", this));
    modes.add(new OMOldIntaveNoSlow("Old Intave", this));
    modes.add(new OMExhiNoSlow("Exhi", this));
    modes.add(new OMNewGrimNoSlow("New Grim", this));
    modes.add(new OMVulcanNoSlow("Vulcan", this));
    modes.add(new OMOldGrimNoSlow("Grim 1.9", this));
    modes.add(new OMGrimSemiNoSlow("GrimSemi", this));
  }

  private NoSlowMode getActiveMode() {
    return modes.get(this.mode.getValue());
  }

  public void onEnable() {
    getActiveMode().onEnable();
  }

  public void onDisable() {
    getActiveMode().onDisable();
  }

  public boolean isSwordActive() {
    return this.swordValue.getValue() && ItemUtil.isHoldingSword();
  }

  public boolean isFoodActive() {
    return this.foodValue.getValue() && ItemUtil.isEating();
  }

  public boolean isBowActive() {
    return this.bowValue.getValue() && ItemUtil.isUsingBow();
  }

  public boolean isPotionActive() {
    return this.potionValue.getValue()
        && mc.thePlayer.isUsingItem()
        && mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion;
  }

  public boolean isAntiSwitchActive() {
    if (!this.isEnabled()
        || !this.antiSwitch.getValue()
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return false;
    }
    net.minecraft.item.ItemStack heldItem = mc.thePlayer.getHeldItem();
    if (heldItem == null || !(heldItem.getItem() instanceof net.minecraft.item.ItemSword)) {
      return false;
    }
    return mc.thePlayer.isUsingItem();
  }

  public boolean isAnyActive() {
    return mc.thePlayer.isUsingItem()
        && (isSwordActive() || isFoodActive() || isBowActive() || isPotionActive());
  }

  public boolean shouldCancelSlowdown() {
    if (!this.isEnabled()) return false;
    NoSlowMode activeMode = getActiveMode();
    if (activeMode instanceof OMGrimTestNoSlow) {
      return ((OMGrimTestNoSlow) activeMode).shouldCancelSlowdown();
    }
    if (activeMode instanceof OMGrimSemiNoSlow) {
      return ((OMGrimSemiNoSlow) activeMode).shouldCancelSlowdown();
    }
    return true;
  }

  public boolean canSprint() {
    return true;
  }

  public float getMotionMultiplier() {
    if (this.mode.getValue() == 2) {
      return 0.35f;
    }
    return 1.0f;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onUpdate(event);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onPacket(event);
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onRightClick(event);
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}