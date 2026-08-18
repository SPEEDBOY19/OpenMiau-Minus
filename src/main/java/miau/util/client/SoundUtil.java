package miau.util.client;

import java.util.concurrent.ThreadLocalRandom;
import miau.Miau;
import miau.module.modules.render.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;

public class SoundUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final String[] ATTACK_SOUNDS = {
    "miau.attack.ting",
    "miau.attack.bass",
    "miau.attack.pop",
    "miau.attack.swing",
    "miau.attack.cloth3"
  };
  private static long lastAttackSound = 0L;

  public static void playSound(String soundName) {
    playSound(soundName, 1.0F);
  }

  public static void playSound(String soundName, float pitch) {
    playSound(soundName, getVolume(), pitch);
  }

  public static void playSound(String soundName, float volume, float pitch) {
    SoundHandler soundHandler = mc.getSoundHandler();
    if (soundHandler == null || mc.thePlayer == null) {
      return;
    }
    soundHandler.playSound(
        new PositionedSoundRecord(
            new ResourceLocation(soundName),
            volume,
            pitch,
            (float) mc.thePlayer.posX,
            (float) mc.thePlayer.posY,
            (float) mc.thePlayer.posZ));
  }

  public static float getVolume() {
    if (Miau.moduleManager == null || Miau.moduleManager.modules == null) {
      return 1.0F;
    }
    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
    return hud == null ? 1.0F : hud.soundVolume.getValue();
  }

  public static void playEnableSound() {
    playSound(getToggleEvent("miau.enable"));
  }

  public static void playDisableSound() {
    playSound(getToggleEvent("miau.disable"));
  }

  public static void playWelcomeSound() {
    SoundHandler soundHandler = mc.getSoundHandler();
    if (soundHandler == null) {
      return;
    }
    soundHandler.playSound(
        PositionedSoundRecord.create(new ResourceLocation("miau.welcome"), 1.0F));
  }

  private static String getToggleEvent(String base) {
    HUD hud = getHud();
    if (hud != null && hud.toggleSoundSelect.getValue() == 1) {
      return base + ".sigma5";
    }
    return base;
  }

  private static HUD getHud() {
    if (Miau.moduleManager == null || Miau.moduleManager.modules == null) {
      return null;
    }
    return (HUD) Miau.moduleManager.modules.get(HUD.class);
  }

  public static void playAttackSound() {
    if (Miau.moduleManager == null || Miau.moduleManager.modules == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastAttackSound < 120L) {
      return;
    }
    lastAttackSound = now;
    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
    if (hud == null || !hud.attackSound.getValue()) {
      return;
    }
    int index = hud.attackSoundSelect.getValue();
    String sound =
        index >= 0 && index < ATTACK_SOUNDS.length
            ? ATTACK_SOUNDS[index]
            : ATTACK_SOUNDS[ThreadLocalRandom.current().nextInt(ATTACK_SOUNDS.length)];
    float pitch = 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F;
    playSound(sound, pitch);
  }
}
