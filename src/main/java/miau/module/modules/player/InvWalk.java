package miau.module.modules.player;

import com.google.common.base.CaseFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.mixin.IAccessorC0DPacketCloseWindow;
import miau.module.Module;
import miau.module.modules.movement.Sprint;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.KeyBindUtil;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

public class InvWalk extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
  private boolean keysPressed = false;
  private C16PacketClientStatus pendingStatus = null;
  private final Map<KeyBinding, Boolean> movementKeys =
      new HashMap<KeyBinding, Boolean>(8) {
        {
          put(mc.gameSettings.keyBindForward, false);
          put(mc.gameSettings.keyBindBack, false);
          put(mc.gameSettings.keyBindLeft, false);
          put(mc.gameSettings.keyBindRight, false);
          put(mc.gameSettings.keyBindJump, false);
          put(mc.gameSettings.keyBindSneak, false);
          put(mc.gameSettings.keyBindSprint, false);
        }
      };

  public final ModeProperty mode = new ModeProperty("Mode", 1, new String[] {"VANILLA", "LEGIT"});
  public final BooleanProperty guiEnabled = new BooleanProperty("click-gui", true);
  public final BooleanProperty lockMoveKey = new BooleanProperty("lock-move-dey", false);

  public InvWalk() {
    super("InvWalk", false);
  }

  public void pressMovementKeys(boolean skipSneak) {
    this.movementKeys.keySet().stream()
        .filter(key -> !skipSneak || key != mc.gameSettings.keyBindSneak)
        .forEach(key -> KeyBindUtil.updateKeyState(key.getKeyCode()));
    if (Miau.moduleManager.modules.get(Sprint.class).isEnabled()) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
    }
    this.keysPressed = true;
  }

  public void resetMovementKeys() {
    this.movementKeys.replaceAll((k, v) -> false);
  }

  public boolean isSetMovementKeys() {
    return this.movementKeys.values().stream().anyMatch(Boolean::booleanValue);
  }

  public void storeMovementKeys() {
    this.movementKeys.replaceAll((k, v) -> KeyBindUtil.isKeyDown(k.getKeyCode()));
  }

  public void restoreMovementKeys() {
    for (Map.Entry<KeyBinding, Boolean> keyBinding : movementKeys.entrySet()) {
      KeyBindUtil.setKeyBindState(keyBinding.getKey().getKeyCode(), keyBinding.getValue());
    }
    if (Miau.moduleManager.modules.get(Sprint.class).isEnabled()) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
    }
    this.keysPressed = true;
  }

  public boolean canInvWalk() {
    if (!(mc.currentScreen instanceof GuiContainer)) return false;
    if (mc.currentScreen instanceof GuiContainerCreative) return false;

    switch (this.mode.getValue()) {
      case 0:
        return true;
      case 1:
        if (!(mc.currentScreen instanceof GuiInventory)) return false;
        return this.pendingStatus != null && this.clickQueue.isEmpty();
      default:
        return false;
    }
  }

  private boolean canGuiWalk() {
    return mc.currentScreen != null
        && !(mc.currentScreen instanceof GuiChat)
        && !(mc.currentScreen instanceof GuiContainer)
        && this.guiEnabled.getValue();
  }

  private boolean shouldRefreshKeysPostTick() {
    return this.mode.getValue() == 0 && (this.canInvWalk() || this.canGuiWalk());
  }

  public boolean temporaryStackIsEmpty() {
    if (mc.thePlayer.inventory.getItemStack() != null) return false;
    if (mc.thePlayer.inventoryContainer instanceof ContainerPlayer) {
      ContainerPlayer containerPlayer = (ContainerPlayer) mc.thePlayer.inventoryContainer;
      for (int i = 0; i < containerPlayer.craftMatrix.getSizeInventory(); i++) {
        ItemStack stack = containerPlayer.craftMatrix.getStackInSlot(i);
        if (stack != null) {
          return false;
        }
      }
    }
    return true;
  }

  @EventTarget(Priority.LOWEST)
  public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
      while (!this.clickQueue.isEmpty()) {
        PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
      }
    } else if (event.getType() == EventType.POST
        && this.isEnabled()
        && this.shouldRefreshKeysPostTick()) {
      if (this.isSetMovementKeys() && this.lockMoveKey.getValue()) {
        this.restoreMovementKeys();
      } else {
        this.pressMovementKeys(true);
      }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;

    if (this.canGuiWalk()) {
      this.pressMovementKeys(true);
      return;
    }

    if (this.canInvWalk()) {
      if (this.isSetMovementKeys() && this.lockMoveKey.getValue()) {
        this.restoreMovementKeys();
      } else {
        this.pressMovementKeys(true);
      }
    } else {
      if (this.keysPressed) {
        if (mc.currentScreen != null) {
          KeyBinding.unPressAllKeys();
        } else if (this.isSetMovementKeys()) {
          this.resetMovementKeys();
          this.pressMovementKeys(false);
        }
        this.keysPressed = false;
      }
      if (this.pendingStatus != null) {
        PacketUtil.sendPacketNoEvent(this.pendingStatus);
        this.pendingStatus = null;
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.SEND) return;

    if (event.getPacket() instanceof C16PacketClientStatus) {
      this.storeMovementKeys();
      if (this.mode.getValue() == 1) {
        C16PacketClientStatus packet = (C16PacketClientStatus) event.getPacket();
        if (packet.getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
          event.setCancelled(true);
          this.pendingStatus = packet;
        }
      }
    } else if (!(event.getPacket() instanceof C0EPacketClickWindow)) {
      if (event.getPacket() instanceof C0DPacketCloseWindow) {
        C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
        if (((IAccessorC0DPacketCloseWindow) packet).getWindowId() == 0) {
          if (this.pendingStatus != null) {
            this.pendingStatus = null;
            event.setCancelled(true);
          }
        } else {
          if (!this.clickQueue.isEmpty()) {
            this.clickQueue.clear();
          }
        }
      }
    } else {
      C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();
      if (this.mode.getValue() == 1) {
        if (packet.getWindowId() == 0) {
          if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
            event.setCancelled(true);
            return;
          }
          if (this.pendingStatus != null) {
            KeyBinding.unPressAllKeys();
            event.setCancelled(true);
            this.clickQueue.offer(packet);
          }
        }
      }
      if (this.pendingStatus != null) {
        PacketUtil.sendPacketNoEvent(this.pendingStatus);
        this.pendingStatus = null;
      }
    }
  }

  @Override
  public void onDisabled() {
    if (this.keysPressed) {
      if (mc.currentScreen != null) {
        KeyBinding.unPressAllKeys();
      }
      this.keysPressed = false;
    }
    if (this.pendingStatus != null) {
      PacketUtil.sendPacketNoEvent(this.pendingStatus);
      this.pendingStatus = null;
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }
}
