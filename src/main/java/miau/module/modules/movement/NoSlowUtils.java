package miau.module.modules.movement;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.PlayerUpdateEvent;
import miau.event.impl.Render2DEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.module.modules.movement.Blink;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.KeyBindUtil;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSoup;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;

public class NoSlowUtils extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode =
      new ModeProperty("NoSlow Mode", 0, new String[] {"Gamma", "Blink", "Float", "Disabled"});
  public final BooleanProperty autoJumpGamma = new BooleanProperty("Auto Jump Gamma", false);

  private int offGroundTicks;
  private int ticks;
  private boolean send;
  private boolean doJump;
  private boolean blinking;

  public NoSlowUtils() {
    super("NoSlowUtils", false);
  }

  @Override
  public void onEnabled() {
    this.ticks = 0;
    this.blinking = false;
  }

  @EventTarget
  public void onPreUpdate(PlayerUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
    this.doJump = this.autoJumpGamma.getValue();
  }

  @EventTarget
  public void onPreMotion(PlayerUpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

    if (this.isBlinkEnabled()) {
      this.blinking = true;
    } else {
      this.blinking = false;
    }

    switch (this.mode.getValue()) {
      case 0:
        this.gammaNoSlow();
        break;
      case 1:
        this.blinkNoSlow();
        break;
      case 2:
        this.floatNoSlow();
        break;
      default:
        break;
    }
  }

  @EventTarget
  public void onRenderTick(Render2DEvent event) {
    if (!this.isEnabled()
        || !this.blinking
        || this.ticks <= 1
        || mc.currentScreen != null) {
      return;
    }
    String text = "blinking: " + "\u00a7";
    if (this.ticks > 50) {
      text += "c";
    } else if (this.ticks > 30) {
      text += "6";
    } else if (this.ticks > 20) {
      text += "e";
    } else {
      text += "a";
    }
    text += this.ticks;
    ScaledResolution sr = new ScaledResolution(mc);
    int wid = mc.fontRendererObj.getStringWidth(text) / 2 - 2;
    mc.fontRendererObj.drawStringWithShadow(
        text, sr.getScaledWidth() / 2.0F - wid, sr.getScaledHeight() / 2.0F + 13, -1);
  }

  private void gammaNoSlow() {
    if (mc.thePlayer.onGround) {
      this.offGroundTicks = 0;
    } else {
      this.offGroundTicks++;
    }

    if (this.offGroundTicks == 2 && this.send) {
      this.send = false;
      PacketUtil.sendPacketNoEvent(
          new C08PacketPlayerBlockPlacement(
              new BlockPos(-1, -1, -1), 255, mc.thePlayer.getHeldItem(), 0.0F, 0.0F, 0.0F));
    } else if (mc.thePlayer.isUsingItem() && this.isConsumable(mc.thePlayer.getHeldItem())) {
      mc.thePlayer.posY += 1E-14;
    }
  }

  @EventTarget
  public void onPacketSent(PacketEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.SEND
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }
    if (this.mode.getValue() != 0) return;

    if (event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement blockPlacement =
          (C08PacketPlayerBlockPlacement) event.getPacket();
      if (blockPlacement.getPlacedBlockDirection() == 255
          && this.isConsumable(blockPlacement.getStack())
          && this.offGroundTicks < 2) {
        if (mc.thePlayer.onGround && !this.doJump) {
          KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        } else if (mc.thePlayer.onGround && this.doJump) {
          KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
          mc.thePlayer.jump();
        }
        this.send = true;
        event.setCancelled(true);
      }
    }
  }

  private boolean isConsumable(ItemStack itemStack) {
    if (itemStack == null || itemStack.getItem() == null) return false;
    return itemStack.getItem() instanceof ItemFood
        || itemStack.getItem() instanceof ItemAppleGold
        || itemStack.getItem() instanceof ItemSoup
        || itemStack.getItem() instanceof ItemBow;
  }

  private void blinkNoSlow() {
    if (!mc.thePlayer.isUsingItem()) {
      this.disableBlink();
      this.ticks = 0;
    }
    if (mc.thePlayer.isUsingItem() && this.isConsumable(mc.thePlayer.getHeldItem())) {
      this.enableBlink();
      this.ticks++;
    }
  }

  private void floatNoSlow() {
    if (this.conditions()) {
      mc.thePlayer.posY += 0.0000001;
    }
  }

  private boolean conditions() {
    return (this.holding("apple") || this.holding("potion")) && mc.thePlayer.onGround;
  }

  private boolean holding(String itemType) {
    ItemStack heldItem = mc.thePlayer.getHeldItem();
    if (heldItem != null && heldItem.getItem() != null) {
      return heldItem
          .getItem()
          .getUnlocalizedName()
          .toLowerCase()
          .contains(itemType);
    }
    return false;
  }

  private boolean isBlinkEnabled() {
    Blink blink = (Blink) Miau.moduleManager.modules.get(Blink.class);
    return blink != null && blink.isEnabled();
  }

  private void enableBlink() {
    Blink blink = (Blink) Miau.moduleManager.modules.get(Blink.class);
    if (blink != null && !blink.isEnabled()) blink.setEnabled(true);
  }

  private void disableBlink() {
    Blink blink = (Blink) Miau.moduleManager.modules.get(Blink.class);
    if (blink != null && blink.isEnabled()) blink.setEnabled(false);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}
