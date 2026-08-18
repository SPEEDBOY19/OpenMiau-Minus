package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.LivingUpdateEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemSword;
import org.lwjgl.input.Mouse;

public class BlockHit extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Legit"});
  public final BooleanProperty rmb = new BooleanProperty("RightClick Only", false);
  public final FloatProperty hurtime = new FloatProperty("Hurtime", 2.0F, 0.0F, 10.0F);

  public EntityLivingBase target;
  public boolean down;

  public BlockHit() {
    super("BlockHit", false, false);
  }

  @Override
  public void onEnabled() {
    target = null;
    down = false;
  }

  @Override
  public void onDisabled() {
    if (down) {
      release();
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (event.getTarget() != null && event.getTarget() instanceof EntityLivingBase) {
      target = (EntityLivingBase) event.getTarget();
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (mc.thePlayer == null) {
      return;
    }
    if (!isPlayerHoldingSword()) {
      return;
    }
    if (rmb.getValue() && !Mouse.isButtonDown(1)) {
      if (down) {
        release();
      }
      return;
    }
    if (target == null) {
      if (down) {
        release();
      }
      return;
    }
    if (!down && target.hurtTime > hurtime.getValue()) {
      press();
      return;
    }
    if (down && target.hurtTime <= hurtime.getValue()) {
      release();
    }
  }

  public boolean isPlayerHoldingSword() {
    return (mc.thePlayer.getCurrentEquippedItem() != null)
        && (mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword);
  }

  private void release() {
    int key = mc.gameSettings.keyBindUseItem.getKeyCode();
    KeyBinding.setKeyBindState(key, false);
    down = false;
    target = null;
  }

  private void press() {
    down = true;
    int key = mc.gameSettings.keyBindUseItem.getKeyCode();
    KeyBinding.setKeyBindState(key, true);
  }
}
