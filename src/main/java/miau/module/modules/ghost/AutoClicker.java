package miau.module.modules.ghost;

import java.util.Objects;
import miau.event.EventTarget;
import miau.event.impl.LeftClickMouseEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.module.modules.render.Keystrokes;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.client.KeyBindUtil;
import miau.util.math.RandomUtil;
import miau.util.player.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

public class AutoClicker extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private boolean clickPending = false;
  private long clickDelay = 0L;

  public final FloatProperty cps = new FloatProperty("cps", 8.0F, 12.0F, 1.0F, 20.0F);
  public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
  public final BooleanProperty breakBlocks = new BooleanProperty("break-blocks", true);
  public final BooleanProperty inventory = new BooleanProperty("inventory", false);

  private long getNextClickDelay() {
    return 1000L
        / RandomUtil.nextLong(this.cps.getValue().intValue(), this.cps.getSecondValue().intValue());
  }

  private boolean isBreakingBlock() {
    return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
  }

  private boolean canClick() {
    if (!this.weaponsOnly.getValue()
        || ItemUtil.hasRawUnbreakingEnchant()
        || ItemUtil.isHoldingSword()) {
      if (this.breakBlocks.getValue() && this.isBreakingBlock() && !this.hasValidTarget()) {
        GameType gameType12 = mc.playerController.getCurrentGameType();
        return gameType12 != GameType.SURVIVAL && gameType12 != GameType.CREATIVE;
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  private boolean isValidTarget(EntityPlayer entityPlayer) {
    if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
      if (entityPlayer == mc.getRenderViewEntity()
          || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
        return false;
      } else if (entityPlayer.deathTime > 0) {
        return false;
      } else {
        return mc.objectMouseOver != null && mc.objectMouseOver.entityHit == entityPlayer;
      }
    } else {
      return false;
    }
  }

  private boolean hasValidTarget() {
    return mc.theWorld.loadedEntityList.stream()
        .filter(e -> e instanceof EntityPlayer)
        .map(e -> (EntityPlayer) e)
        .anyMatch(this::isValidTarget);
  }

  public AutoClicker() {
    super("AutoClicker", false);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
      if (this.clickDelay > 0L) {
        this.clickDelay -= 50L;
      }
      if (mc.currentScreen != null && !this.inventory.getValue()) {
        this.clickPending = false;
      } else {
        if (this.clickPending) {
          this.clickPending = false;
          if (mc.currentScreen == null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
          }
        }
        boolean isMouseDown = org.lwjgl.input.Mouse.isButtonDown(0);
        if (this.isEnabled() && this.canClick() && isMouseDown) {
          if (!mc.thePlayer.isUsingItem()) {
            while (this.clickDelay <= 0L) {
              this.clickPending = true;
              this.clickDelay = this.clickDelay + this.getNextClickDelay();

              if (mc.currentScreen != null) {
                long time = System.currentTimeMillis();
                try {
                  java.lang.reflect.Method m =
                      net.minecraft.client.gui.GuiScreen.class.getDeclaredMethod(
                          "mouseClicked", int.class, int.class, int.class);
                  m.setAccessible(true);
                  int mouseX =
                      org.lwjgl.input.Mouse.getEventX() * mc.currentScreen.width / mc.displayWidth;
                  int mouseY =
                      mc.currentScreen.height
                          - org.lwjgl.input.Mouse.getEventY()
                              * mc.currentScreen.height
                              / mc.displayHeight
                          - 1;
                  m.invoke(mc.currentScreen, mouseX, mouseY, 0);
                } catch (Exception e) {
                }
              } else {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                Keystrokes.recordLeftClick();
              }
            }
          }
        }
      }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onCLick(LeftClickMouseEvent event) {
    if (this.isEnabled() && !event.isCancelled()) {
      if (!this.clickPending) {
        this.clickDelay = this.clickDelay + this.getNextClickDelay();
      }
    }
  }

  @Override
  public void onEnabled() {
    this.clickDelay = 0L;
  }

  @Override
  public String[] getSuffix() {
    return Objects.equals(this.cps.getValue(), this.cps.getSecondValue())
        ? new String[] {String.valueOf(this.cps.getValue().intValue())}
        : new String[] {
          String.format(
              "%d-%d", this.cps.getValue().intValue(), this.cps.getSecondValue().intValue())
        };
  }
}
