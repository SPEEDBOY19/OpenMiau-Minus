package miau.module.modules.misc;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LivingUpdateEvent;
import miau.module.Module;
import miau.module.modules.combat.Velocity;
import miau.module.modules.combat.velocity.StandardVelocity;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

public class Fireball extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final miau.property.properties.BooleanProperty autoDisable =
      new miau.property.properties.BooleanProperty("Auto disable", false);

  private boolean active = false;
  private int tickCounter = 0;
  private boolean resetting = false;
  private boolean done = false;

  private int savedHorizontal = -1;
  private int savedExplosionHorizontal = -1;

  public Fireball() {
    super("Fireball", false);
  }

  @Override
  public void onEnabled() {
    this.active = false;
    this.tickCounter = 0;
    this.resetting = false;
    this.done = false;
    this.savedHorizontal = -1;
    this.savedExplosionHorizontal = -1;
  }

  @Override
  public void onDisabled() {
    this.restoreVelocity();
  }

  private StandardVelocity getStandard() {
    Velocity velocity = (Velocity) Miau.moduleManager.modules.get(Velocity.class);
    if (velocity == null) return null;
    for (miau.module.modules.combat.velocity.VelocityMode mode : velocity.modes) {
      if (mode instanceof StandardVelocity) {
        return (StandardVelocity) mode;
      }
    }
    return null;
  }

  private void applyVelocity(int horizontal) {
    StandardVelocity standard = this.getStandard();
    if (standard == null) return;
    if (this.savedHorizontal == -1) {
      this.savedHorizontal = standard.horizontal.getValue();
      this.savedExplosionHorizontal = standard.explosionHorizontal.getValue();
    }
    standard.horizontal.setValue(horizontal);
    standard.explosionHorizontal.setValue(horizontal);
  }

  private void restoreVelocity() {
    StandardVelocity standard = this.getStandard();
    if (standard == null || this.savedHorizontal == -1) return;
    standard.horizontal.setValue(this.savedHorizontal);
    standard.explosionHorizontal.setValue(this.savedExplosionHorizontal);
    this.savedHorizontal = -1;
    this.savedExplosionHorizontal = -1;
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    ItemStack currentItem = mc.thePlayer.inventory.getCurrentItem();
    boolean isHoldingFireCharge =
        currentItem != null && currentItem.getItem() instanceof ItemFireball;
    boolean rmb = Mouse.isButtonDown(1);

    if (rmb && isHoldingFireCharge) {
      if (!this.active) {
        this.active = true;
        this.resetting = false;
        this.done = false;
        this.applyVelocity(100);
      }
    } else {
      if (this.active) {
        this.active = false;
        this.tickCounter = 10;
        this.resetting = true;
      }
    }

    if (!isHoldingFireCharge && !this.resetting && !this.done) {
      this.tickCounter = 10;
      this.resetting = true;
    }

    if (this.resetting && this.tickCounter > 0) {
      this.tickCounter--;
      if (this.tickCounter == 0 && !this.done) {
        this.applyVelocity(0);
        this.done = true;
        this.resetting = false;
      }
    }

    if (this.autoDisable.getValue() && this.done) {
      this.setEnabled(false);
    }
  }
}
