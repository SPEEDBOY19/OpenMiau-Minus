package miau.module.modules.player;

import java.util.ArrayList;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;

public class AutoSoup extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final FloatProperty delay = new FloatProperty("delay(ms)", 50.0F, 100.0F, 0.0F, 200.0F);
  public final FloatProperty coolDown =
      new FloatProperty("cooldown(ms)", 1000.0F, 1200.0F, 0.0F, 5000.0F);
  public final FloatProperty health = new FloatProperty("health", 7.0F, 0.0F, 20.0F);
  public final BooleanProperty invConsume = new BooleanProperty("consume in inv", false);
  public final BooleanProperty autoRefill = new BooleanProperty("auto refil", true);
  public final FloatProperty invWait =
      new FloatProperty("invWait(ms)", 50.0F, 100.0F, 0.0F, 200.0F);
  public final FloatProperty invCoolDown =
      new FloatProperty("refill delay(ms)", 50.0F, 100.0F, 0.0F, 200.0F);

  private final TimerUtil cdTimer = new TimerUtil();
  private final TimerUtil invCdTimer = new TimerUtil();
  private final TimerUtil eatTimer = new TimerUtil();
  private State state = State.WAITINGTOSWITCH;
  private int originalSlot;
  private boolean inInv;
  private List<Integer> sortedSlots = new ArrayList<>();
  private float ranDelay;
  private int soupSlot;

  public AutoSoup() {
    super("AutoSoup", false);
  }

  @Override
  public void onDisabled() {
    state = State.WAITINGTOSWITCH;
    inInv = false;
    super.onDisabled();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if ((invConsume.getValue() || mc.currentScreen == null)
        && mc.thePlayer.getHealth() < health.getValue()
        && cdTimer.hasTimeElapsed((long) ranDelay)) {

      switch (state) {
        case WAITINGTOSWITCH:
          ranDelay = randomRange(delay);
          state = State.NONE;
          break;

        case NONE:
          soupSlot = getSoupSlot();
          if (soupSlot == -1) return;
          originalSlot = mc.thePlayer.inventory.currentItem;
          mc.thePlayer.inventory.currentItem = soupSlot;
          ranDelay = randomRange(delay);
          state = State.SWITCHED;
          break;

        case SWITCHED:
          mc.thePlayer.inventory.currentItem = soupSlot;
          mc.playerController.sendUseItem(
              mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getStackInSlot(soupSlot));
          eatTimer.reset();
          state = State.EATING;
          break;

        case EATING:
          if (mc.thePlayer.getItemInUseDuration() < 4 || !isHeldItemSoup()) {
            state = State.DROPPING;
            ranDelay = randomRange(delay);
          } else if (eatTimer.hasTimeElapsed(2000L)) {
            state = State.DROPPING;
            ranDelay = randomRange(delay);
          }
          break;

        case DROPPING:
          mc.playerController.sendUseItem(
              mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getStackInSlot(soupSlot));
          mc.thePlayer.inventory.currentItem = originalSlot;
          ranDelay = randomRange(coolDown);
          state = State.WAITINGTOSWITCH;
          break;
      }

      cdTimer.reset();
    }

    if (autoRefill.getValue() && mc.currentScreen instanceof GuiInventory) {
      if (!inInv) {
        ranDelay = randomRange(invWait);
        invCdTimer.reset();
        generateSlots();
        inInv = true;
      }
      if (!sortedSlots.isEmpty() && invCdTimer.hasTimeElapsed((long) ranDelay)) {
        mc.playerController.windowClick(
            mc.thePlayer.openContainer.windowId, sortedSlots.get(0), 0, 1, mc.thePlayer);
        ranDelay = randomRange(invCoolDown);
        invCdTimer.reset();
        sortedSlots.remove(0);
      }
    } else {
      inInv = false;
    }
  }

  private boolean isHeldItemSoup() {
    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(soupSlot);
    return stack != null && stack.getItem() instanceof ItemSoup;
  }

  private void generateSlots() {
    List<Integer> slots = new ArrayList<>();
    int slotsNeeded = 0;
    for (int i = 0; i <= 8; i++) {
      if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
        slotsNeeded++;
      }
    }
    for (int i = 0; i < mc.thePlayer.inventoryContainer.getInventory().size(); i++) {
      if (!slots.isEmpty() && slots.size() >= slotsNeeded) break;
      ItemStack stack = mc.thePlayer.inventoryContainer.getInventory().get(i);
      if (stack != null
          && (stack.getItem() instanceof ItemSoup || stack.getItem() == Items.mushroom_stew)
          && !(i >= 36 && i <= 44)) {
        slots.add(i);
      }
    }
    this.sortedSlots = slots;
  }

  private int getSoupSlot() {
    for (int slot = 0; slot <= 8; slot++) {
      ItemStack itemInSlot = mc.thePlayer.inventory.getStackInSlot(slot);
      if (itemInSlot != null
          && (itemInSlot.getItem() instanceof ItemSoup
              || itemInSlot.getItem() == Items.mushroom_stew)) {
        return slot;
      }
    }
    return -1;
  }

  private static float randomRange(FloatProperty prop) {
    if (prop.isDoubleSlider() && prop.getSecondValue() != null) {
      float min = prop.getValue();
      float max = prop.getSecondValue();
      if (min > max) {
        float temp = min;
        min = max;
        max = temp;
      }
      return min + (float) Math.random() * (max - min);
    }
    return prop.getValue();
  }

  private enum State {
    WAITINGTOSWITCH,
    NONE,
    SWITCHED,
    EATING,
    DROPPING
  }

  public boolean isHealing() {
    return state != State.WAITINGTOSWITCH && state != State.NONE;
  }
}
