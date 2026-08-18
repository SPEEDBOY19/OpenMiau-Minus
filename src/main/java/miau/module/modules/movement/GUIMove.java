package miau.module.modules.movement;

import java.util.ArrayDeque;
import java.util.Queue;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorKeyBinding;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.network.PacketUtil;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import org.lwjgl.input.Mouse;

public class GUIMove extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty notInChests = new BooleanProperty("NotInChests", false);
  public final BooleanProperty aacAdditionPro = new BooleanProperty("AACAdditionPro", false);
  public final BooleanProperty intave = new BooleanProperty("Intave", false);
  public final BooleanProperty intaveSafe = new BooleanProperty("IntaveSafe", false, () -> this.intave.getValue());
  public final BooleanProperty saveC0E = new BooleanProperty("SaveC0E", false);
  public final BooleanProperty allowJump = new BooleanProperty("AllowJump", false);
  public final BooleanProperty allowSneak = new BooleanProperty("AllowSneak", false, () -> !this.intave.getValue());
  public final BooleanProperty noSprintWhenClosed = new BooleanProperty("NoSprintWhenClosed", false, () -> this.saveC0E.getValue());
  public final FloatProperty inventoryMotion = new FloatProperty("InventoryMotion", 1f, 0f, 2f);
  private final Queue<C0EPacketClickWindow> clickWindowList = new ArrayDeque<>();
  private final KeyBinding[] affectedBindings = {
    mc.gameSettings.keyBindForward,
    mc.gameSettings.keyBindBack,
    mc.gameSettings.keyBindRight,
    mc.gameSettings.keyBindLeft,
    mc.gameSettings.keyBindJump,
    mc.gameSettings.keyBindSprint,
    mc.gameSettings.keyBindSneak
  };

  public GUIMove() {
    super("GUIMove", false);
  }

  private boolean isIntave() {
    return (mc.currentScreen instanceof GuiInventory || mc.currentScreen instanceof GuiChest) && this.intave.getValue();
  }

  private boolean shouldFreezeInputs(GuiScreen screen) {
    if (this.notInChests.getValue() && screen instanceof GuiChest) {
      return true;
    }
    return false;
  }

  private boolean isButtonPressed(KeyBinding keyBinding) {
    if (keyBinding.getKeyCode() < 0) {
      return Mouse.isButtonDown(keyBinding.getKeyCode() + 100);
    }
    return keyBinding.isKeyDown();
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer == null || mc.theWorld == null) {
        return;
      }
      GuiScreen screen = mc.currentScreen;
      if (this.shouldFreezeInputs(screen)) {
        this.unPressKeys();
        return;
      }
      boolean intaveActive = this.isIntave();
      if (intaveActive && this.intaveSafe.getValue() && !mc.thePlayer.onGround) {
        this.unPressKeys();
        return;
      }
      if (screen instanceof GuiInventory || screen instanceof GuiChest) {
        mc.thePlayer.motionX *= this.inventoryMotion.getValue();
        mc.thePlayer.motionZ *= this.inventoryMotion.getValue();
      }
      if (intaveActive && MoveUtil.isMoving()) {
        ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(true);
      }
      for (KeyBinding affectedBinding : this.affectedBindings) {
        if (affectedBinding == mc.gameSettings.keyBindSneak && intaveActive && MoveUtil.isMoving()) {
          continue;
        }
        if (affectedBinding == mc.gameSettings.keyBindJump
            && !this.allowJump.getValue()
            && (screen instanceof GuiInventory || screen instanceof GuiChest)) {
          ((IAccessorKeyBinding) affectedBinding).setPressed(false);
          continue;
        }
        boolean pressed =
            this.isButtonPressed(affectedBinding)
                || (affectedBinding == mc.gameSettings.keyBindSneak
                    && this.allowSneak.getValue()
                    && this.isButtonPressed(mc.gameSettings.keyBindSneak))
                || (affectedBinding == mc.gameSettings.keyBindSprint
                    && Miau.moduleManager.modules.get(Sprint.class).isEnabled());
        ((IAccessorKeyBinding) affectedBinding).setPressed(pressed);
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled() && event.getType() == EventType.SEND) {
      if (!this.saveC0E.getValue()) {
        return;
      }
      if (this.noSprintWhenClosed.getValue()) {
        if (!this.clickWindowList.isEmpty() && mc.currentScreen == null) {
          mc.thePlayer.setSprinting(false);
        }
        if (event.getPacket() instanceof C0DPacketCloseWindow) {
          event.setCancelled(true);
          mc.thePlayer.setSprinting(false);
          PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow());
        }
      }
      if (mc.currentScreen != null) {
        if (event.getPacket() instanceof C0EPacketClickWindow) {
          this.clickWindowList.add((C0EPacketClickWindow) event.getPacket());
          event.setCancelled(true);
        }
      } else if (!this.clickWindowList.isEmpty()) {
        this.clickWindowList.forEach(PacketUtil::sendPacketNoEvent);
        this.clickWindowList.clear();
      }
    }
  }

  @Override
  public void onDisabled() {
    this.restorePhysicalKeys();
  }

  private void restorePhysicalKeys() {
    for (KeyBinding affectedBinding : this.affectedBindings) {
      ((IAccessorKeyBinding) affectedBinding).setPressed(this.isButtonPressed(affectedBinding));
    }
  }

  private void unPressKeys() {
    for (KeyBinding affectedBinding : this.affectedBindings) {
      ((IAccessorKeyBinding) affectedBinding).setPressed(false);
    }
  }

  @Override
  public String[] getSuffix() {
    if (this.aacAdditionPro.getValue()) {
      return new String[] {"AACAdditionPro"};
    }
    if (this.inventoryMotion.getValue() != 1f) {
      return new String[] {String.valueOf(this.inventoryMotion.getValue())};
    }
    return new String[0];
  }
}