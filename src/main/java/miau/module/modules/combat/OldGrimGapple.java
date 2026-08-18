package miau.module.modules.combat;

import java.awt.Color;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.UpdateEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.util.client.ChatUtil;
import miau.util.network.PacketUtil;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class OldGrimGapple extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final IntProperty c = new IntProperty("C03PacketPlayer", 32, 32, 40);
  private final IntProperty eatHealth = new IntProperty("EatHealth", 12, 1, 18);
  private final BooleanProperty autoEat = new BooleanProperty("AutoGapple", true);
  private final BooleanProperty progressBar2 = new BooleanProperty("ProgressBar2", false);
  private final BooleanProperty stopWhenNoTarget = new BooleanProperty("StopWhenNoTarget", true);
  private final BooleanProperty stuck = new BooleanProperty("Stuck", false);
  private final BooleanProperty isEatingGapple =
      new BooleanProperty("DisplayStateInDynamicIsland", true);

  private final BooleanProperty checkAbsorption = new BooleanProperty("CheckAbsorption", true);
  private final IntProperty minAbsorption = new IntProperty("MinAbsorption", 0, 0, 8);

  public float eatingProgress = 0f;

  public boolean shouldShowIndicator = false;

  private double x = 0.0;
  private double y = 0.0;
  private double z = 0.0;
  private boolean cancelMove = false;
  private boolean r = false;
  private int ticks = 0;
  private int pauseTicks = 0;
  private float yaw = 0f;
  private float pitch = 0f;
  private boolean shouldEat = false;
  public boolean isEating = false;

  private int slot = -1;

  public OldGrimGapple() {
    super("OldGrimGapple", false);
  }

  @Override
  public void onEnabled() {
    shouldEat = false;
    isEating = false;
    eatingProgress = 0f;
    shouldShowIndicator = false;
    ticks = 0;
    pauseTicks = 0;
    stopStuck();
  }

  @Override
  public void onDisabled() {
    ticks = 0;
    pauseTicks = 0;
    shouldEat = false;
    isEating = false;
    eatingProgress = 0f;
    shouldShowIndicator = false;
    stopStuck();
  }

  @EventTarget
  public void onWorld(LoadWorldEvent event) {
    shouldEat = false;
    isEating = false;
    eatingProgress = 0f;
    shouldShowIndicator = false;
    ticks = 0;
    pauseTicks = 0;
    stopStuck();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getPacket() instanceof C03PacketPlayer && cancelMove && ticks < c.getValue()) {
      if (event.getPacket() instanceof C05PacketPlayerLook) {
        C05PacketPlayerLook packet = (C05PacketPlayerLook) event.getPacket();
        yaw = packet.getYaw();
        pitch = packet.getPitch();
      }
      ticks++;
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    yaw = mc.thePlayer.rotationYaw;
    pitch = mc.thePlayer.rotationPitch;

    slot = getGApple();

    boolean shouldContinueEating = checkShouldEat();

    if (shouldContinueEating && slot >= 0) {
      isEating = true;

      if (pauseTicks > 0) {
        pauseTicks--;
        if (pauseTicks <= 0 && stuck.getValue()) {
          stopStuck();
        }
        eatingProgress = 0f;
        return;
      }

      if (stuck.getValue() && !cancelMove && pauseTicks == 0) {
        stuck();
      }

      if (ticks < c.getValue()) {
        if (!cancelMove) {
          ticks++;
        }
      }

      eatingProgress = (float) ticks / (float) c.getValue();
      shouldShowIndicator = true;

      if (ticks >= c.getValue()) {
        PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
        PacketUtil.sendPacket(
            new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getStackInSlot(slot)));

        if (stuck.getValue()) {
          release();
        }

        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        PacketUtil.sendPacket(
            new C08PacketPlayerBlockPlacement(
                mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem)));

        ticks = 0;
        pauseTicks = 2;
        eatingProgress = 0f;

        if (mc.thePlayer.ticksExisted % 20 == 0) {
          ChatUtil.display("§6Auto Eating...");
        }
      }
    } else {
      shouldEat = false;
      isEating = false;
      if (stuck.getValue()) {
        stopStuck();
      }
      ticks = 0;
      pauseTicks = 0;
      eatingProgress = 0f;
      shouldShowIndicator = false;

      if (shouldContinueEating && slot < 0) {
        if (mc.thePlayer.ticksExisted % 40 == 0) {
          ChatUtil.display("§4NoGapple!");
        }
      }
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (isEating && progressBar2.getValue()) {
      ScaledResolution scaledScreen = new ScaledResolution(mc);
      float width = scaledScreen.getScaledWidth();
      float height = scaledScreen.getScaledHeight();
      drawProgressBar(width, height);
    }
  }

  private void stuck() {
    if (!r) {
      x = mc.thePlayer.motionX;
      y = mc.thePlayer.motionY;
      z = mc.thePlayer.motionZ;
      r = true;
    }
    cancelMove = true;
  }

  private void stopStuck() {
    cancelMove = false;
    if (r) {
      mc.thePlayer.motionX = x;
      mc.thePlayer.motionY = y;
      mc.thePlayer.motionZ = z;
      r = false;
    }
  }

  private void release() {
    PacketUtil.sendPacket(new C05PacketPlayerLook(yaw, pitch, mc.thePlayer.onGround));
    int count = Math.max(ticks - 1, 0);
    for (int i = 0; i < count; i++) {
      PacketUtil.sendPacket(new C03PacketPlayer(mc.thePlayer.onGround));
    }
  }

  private int getGApple() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.getItem() instanceof ItemAppleGold) {
        return i;
      }
    }
    return -1;
  }

  private boolean checkShouldEat() {
    if (!autoEat.getValue()) {
      return false;
    }

    boolean hasTarget = checkKillAuraTarget();
    boolean healthOk = checkHealthCondition();
    boolean absorptionOk = checkAbsorptionCondition();

    if (healthOk && hasTarget && absorptionOk && !shouldEat) {
      ChatUtil.display("§aAuto eating started");
      shouldEat = true;
      isEating = true;
    }

    if ((!healthOk || !hasTarget || !absorptionOk) && shouldEat) {
      ChatUtil.display("§eAuto eating stopped");
      shouldEat = false;
      isEating = false;
    }

    return shouldEat;
  }

  private boolean checkHealthCondition() {
    int currentHealth = (int) Math.round(mc.thePlayer.getHealth());
    return currentHealth <= eatHealth.getValue();
  }

  private boolean checkAbsorptionCondition() {
    if (!checkAbsorption.getValue()) {
      return true;
    }
    return mc.thePlayer.getAbsorptionAmount() <= minAbsorption.getValue();
  }

  private boolean checkKillAuraTarget() {
    if (!stopWhenNoTarget.getValue()) {
      return true;
    }
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    return killAura != null && killAura.getTarget() != null;
  }

  private void drawProgressBar(float width, float height) {
    float progressLength = 140F;
    float startY = height / 4 * 3;
    float startX = width / 2 - progressLength / 2;

    float progressRatio = Math.min(Math.max((float) ticks / (float) c.getValue(), 0f), 1f);
    float currentProgress = progressLength * progressRatio;
    int progressPercent = (int) (progressRatio * 100);

    showShadow(startX - 2, startY - 2, progressLength + 4, 11F, 0.3F);

    RenderUtil.drawRoundedRectangle(
        startX, startY, startX + progressLength, startY + 7F, 2F,
        new Color(0, 0, 0, 128).getRGB());

    if (currentProgress != 0f) {
      RenderUtil.drawRoundedGradientRect(
          startX,
          startY,
          startX + currentProgress,
          startY + 7F,
          3f,
          new Color(76, 157, 240, 255).getRGB(),
          new Color(53, 200, 167, 255).getRGB(),
          new Color(76, 157, 240, 255).getRGB(),
          new Color(53, 200, 167, 255).getRGB());
    }

    String percentText = progressPercent + "%";
    mc.fontRendererObj.drawStringWithShadow(
        percentText, startX + progressLength + 5, startY, new Color(255, 255, 255, 255).getRGB());
  }

  private void showShadow(
      float startX, float startY, float width, float height, float shadowStrength) {
    RenderUtil.drawRoundedRectangle(
        startX,
        startY,
        startX + width,
        startY + height,
        3f,
        new Color(0, 0, 0, 120).getRGB());
  }
}