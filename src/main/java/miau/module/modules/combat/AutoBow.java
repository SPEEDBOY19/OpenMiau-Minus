package miau.module.modules.combat;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBow;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class AutoBow extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty waitForBowAimbot = new BooleanProperty("WaitForBowAimbot", true);

  public AutoBow() {
    super("AutoBow", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
    if (mc.thePlayer.isUsingItem()
        && mc.thePlayer.getHeldItem() != null
        && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow
        && mc.thePlayer.getItemInUseDuration() > 20
        && this.canRelease()) {
      mc.thePlayer.stopUsingItem();
      PacketUtil.sendPacket(
          new C07PacketPlayerDigging(
              C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    }
  }

  private boolean canRelease() {
    if (!this.waitForBowAimbot.getValue()) return true;
    ProjectileAimBot projectileAimBot =
        (ProjectileAimBot) Miau.moduleManager.modules.get(ProjectileAimBot.class);
    return projectileAimBot == null || !projectileAimBot.isEnabled() || projectileAimBot.hasTarget();
  }
}
