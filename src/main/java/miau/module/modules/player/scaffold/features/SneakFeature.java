package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.math.RandomUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0BPacketEntityAction;

public class SneakFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  public int sneakingTicks = -1;
  public int placements = 0;
  public int pause = 0;
  public int slow = 0;
  public int ticksOnAir = 0;
  private boolean silentSneaking = false;

  // Mode: 0 = OFF, 1 = NORMAL, 2 = SILENT
  public final ModeProperty sneakMode =
      new ModeProperty("sneak-mode", 0, new String[] {"OFF", "NORMAL", "SILENT"});

  public final FloatProperty startSneaking =
      new FloatProperty("start-sneaking", 0.0F, 0.0F, 5.0F, () -> this.sneakMode.getValue() != 0);
  public final FloatProperty stopSneaking =
      new FloatProperty("stop-sneaking", 0.0F, 0.0F, 5.0F, () -> this.sneakMode.getValue() != 0);
  public final IntProperty sneakEvery =
      new IntProperty("sneak-every", 1, 1, 10, () -> this.sneakMode.getValue() != 0);
  public final FloatProperty sneakingSpeed =
      new FloatProperty("sneaking-speed", 0.2F, 0.2F, 1.0F, () -> this.sneakMode.getValue() != 0);

  public SneakFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(sneakMode, startSneaking, stopSneaking, sneakEvery, sneakingSpeed);
  }

  private void setSneakingState(boolean state) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer == null) return;

    int mode = sneakMode.getValue();

    if (mode == 1) { // NORMAL (Sneak thật client-side)
      if (silentSneaking) {
        mc.thePlayer.sendQueue.addToSendQueue(
            new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
        silentSneaking = false;
      }
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), state);
    } else if (mode == 2) { // SILENT (Lừa antcheat bằng packet, không giữ phím Shift)
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
      if (state) {
        if (!silentSneaking) {
          mc.thePlayer.sendQueue.addToSendQueue(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
          silentSneaking = true;
        }
      } else {
        if (silentSneaking) {
          mc.thePlayer.sendQueue.addToSendQueue(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
          silentSneaking = false;
        }
      }
    } else { // OFF
      if (silentSneaking) {
        mc.thePlayer.sendQueue.addToSendQueue(
            new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
        silentSneaking = false;
      }
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }
  }

  public void calculateSneaking() {
    if (this.sneakMode.getValue() == 0 && this.pause <= 0) {
      setSneakingState(false);
      return;
    }

    if (this.ticksOnAir == 0 && this.sneakingTicks < 0) {
      setSneakingState(false);
    }

    this.sneakingTicks--;

    int ahead = (int) (float) this.startSneaking.getValue();
    int place =
        (int)
            RandomUtil.nextFloat(
                scaffold.options.placeDelay.getValue(),
                scaffold.options.placeDelay.getSecondValue());
    int after = (int) (float) this.stopSneaking.getValue();

    if (this.pause > 0) {
      this.pause--;
      this.sneakingTicks = 0;
      this.placements = 0;
    }

    if (this.sneakingTicks >= 0) {
      setSneakingState(true);
      return;
    }

    if (this.ticksOnAir > 0) this.sneakingTicks = after;

    if (this.ticksOnAir > 0
        || PlayerUtil.blockRelativeToPlayer(
                Minecraft.getMinecraft().thePlayer.motionX * ahead,
                1.0,
                Minecraft.getMinecraft().thePlayer.motionZ * ahead)
            instanceof BlockAir) {
      if (this.placements <= 0) {
        this.sneakingTicks = ahead + place + after;
        this.placements = this.sneakEvery.getValue();
      }
    }

    if (this.sneakingTicks < 0) {
      setSneakingState(false);
    }
  }

  @Override
  public void onDisable() {
    setSneakingState(false);
    this.sneakingTicks = -1;
    this.placements = 0;
    this.pause = 0;
    this.slow = 0;
    this.ticksOnAir = 0;
  }
}