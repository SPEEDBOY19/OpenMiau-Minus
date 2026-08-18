package miau.module.modules.player;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.client.KeyBindUtil;
import miau.util.player.ItemUtil;
import miau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoTool extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private int currentToolSlot = -1;
  public int previousSlot = -1;
  private int tickDelayCounter = 0;

  public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
  public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
  public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);
  public final BooleanProperty spoofItem = new BooleanProperty("spoof-item", false);

  public AutoTool() {
    super("AutoTool", false);
  }

  public boolean isKillAura() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    if (killAura == null || !killAura.isEnabled()) return false;
    return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) return;
      if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
        this.currentToolSlot = -1;
        this.previousSlot = -1;
      }
      if (mc.objectMouseOver != null
          && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
          && mc.gameSettings.keyBindAttack.isKeyDown()
          && !mc.thePlayer.isUsingItem()
          && !isKillAura()) {
        if (this.tickDelayCounter >= this.switchDelay.getValue()
            && (!this.sneakOnly.getValue()
                || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
          int slot = ItemUtil.findInventorySlot(
              mc.thePlayer.inventory.currentItem,
              mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock());
          if (mc.thePlayer.inventory.currentItem != slot) {
            if (this.previousSlot == -1) {
              this.previousSlot = mc.thePlayer.inventory.currentItem;
            }
            mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
          }
        }
        this.tickDelayCounter++;
      } else {
        if (this.switchBack.getValue() && this.previousSlot != -1) {
          mc.thePlayer.inventory.currentItem = this.previousSlot;
        }
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        this.tickDelayCounter = 0;
      }
    }
  }

  @Override
  public void onDisabled() {
    this.currentToolSlot = -1;
    this.previousSlot = -1;
    this.tickDelayCounter = 0;
  }
}