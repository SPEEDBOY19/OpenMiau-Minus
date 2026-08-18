package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;

public class BlockAttack extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"RealAttack", "OnlySwing"});
  private final KeyBinding keyBindUseItem = mc.gameSettings.keyBindUseItem;

  public BlockAttack() {
    super("BlockAttack", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
    if (!mc.thePlayer.isBlocking() || !mc.gameSettings.keyBindAttack.isKeyDown()) return;
    KeyBinding.setKeyBindState(this.keyBindUseItem.getKeyCode(), true);
    mc.thePlayer.swingItem();
    if (this.mode.getModeString().equals("OnlySwing")) {
      mc.playerController.attackEntity(mc.thePlayer, null);
      return;
    }
    if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
      EntityLivingBase target = (EntityLivingBase) mc.objectMouseOver.entityHit;
      if (!target.isDead) {
        mc.playerController.attackEntity(mc.thePlayer, target);
      }
    }
  }

  @Override
  public void onDisabled() {
    KeyBinding.setKeyBindState(this.keyBindUseItem.getKeyCode(), false);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}
