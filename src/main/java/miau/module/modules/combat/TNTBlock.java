package miau.module.modules.combat;

import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class TNTBlock extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final IntProperty fuse = new IntProperty("Fuse", 10, 0, 80);
  public final FloatProperty range = new FloatProperty("Range", 9.0F, 1.0F, 20.0F);
  public final BooleanProperty autoSword = new BooleanProperty("AutoSword", true);
  private boolean blocked = false;

  public TNTBlock() {
    super("TNTBlock", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    if (event.getType() != EventType.PRE) return;
    for (Object o : mc.theWorld.loadedEntityList) {
      if (!(o instanceof EntityTNTPrimed)) continue;
      EntityTNTPrimed entity = (EntityTNTPrimed) o;
      if (entity.fuse > this.fuse.getValue()) continue;
      if (mc.thePlayer.getDistanceSqToEntity(entity) > this.range.getValue() * this.range.getValue()) continue;

      if (this.autoSword.getValue()) {
        int slot = -1;
        float bestDamage = 1.0F;
        for (int i = 0; i < 9; i++) {
          ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
          if (itemStack != null && itemStack.getItem() instanceof ItemSword) {
            float itemDamage = ((ItemSword) itemStack.getItem()).getDamageVsEntity() + 4.0F;
            if (itemDamage > bestDamage) {
              bestDamage = itemDamage;
              slot = i;
            }
          }
        }
        if (slot != -1 && slot != mc.thePlayer.inventory.currentItem) {
          mc.thePlayer.inventory.currentItem = slot;
          mc.playerController.updateController();
        }
      }

      if (mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        this.blocked = true;
      }
      return;
    }

    if (this.blocked && !GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem)) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
      this.blocked = false;
    }
  }
}
