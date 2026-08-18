package miau.module.modules.combat;

import java.util.Random;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

public class AutoGapple extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode =
      new ModeProperty("Mode", 0, new String[] {"Auto", "LegitAuto", "Legit", "Head"});
  private final FloatProperty percent = new FloatProperty("HealthPercent", 75.0F, 1.0F, 100.0F);
  private final IntProperty min = new IntProperty("MinDelay", 75, 1, 5000);
  private final IntProperty max = new IntProperty("MaxDelay", 125, 1, 5000);
  private final FloatProperty regenSec = new FloatProperty("MinRegenSec", 4.6F, 0.0F, 10.0F);
  private final BooleanProperty groundCheck = new BooleanProperty("OnlyOnGround", false);
  private final BooleanProperty waitRegen = new BooleanProperty("WaitRegen", true);
  private final BooleanProperty invCheck = new BooleanProperty("InvCheck", false);
  private final BooleanProperty absorpCheck = new BooleanProperty("NoAbsorption", true);
  private final BooleanProperty fastEatValue =
      new BooleanProperty(
          "FastEat",
          false,
          () -> this.mode.getModeString().equals("LegitAuto") || this.mode.getModeString().equals("Legit"));
  private final IntProperty eatDelayValue =
      new IntProperty("FastEatDelay", 14, 0, 35, this.fastEatValue::getValue);
  private final BooleanProperty eatMessage = new BooleanProperty("CreateMessageAfterEaten", false);
  private final TimerUtil timer = new TimerUtil();
  private int eating = -1;
  private int delay = 0;
  private boolean isDisable = false;
  private boolean tryHeal = false;
  private int prevSlot = -1;
  private boolean switchBack = false;

  public AutoGapple() {
    super("AutoGapple", false);
  }

  @Override
  public void onEnabled() {
    this.eating = -1;
    this.prevSlot = -1;
    this.switchBack = false;
    this.timer.reset();
    this.isDisable = false;
    this.tryHeal = false;
    this.delay = MathHelper.getRandomIntegerInRange(new Random(), this.min.getValue(), this.max.getValue());
  }

  @EventTarget
  public void onWorld(LoadWorldEvent event) {
    this.isDisable = true;
    this.tryHeal = false;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    Packet<?> packet = event.getPacket();
    if (this.eating != -1 && packet instanceof C03PacketPlayer) {
      this.eating++;
    } else if (packet instanceof S09PacketHeldItemChange || packet instanceof C09PacketHeldItemChange) {
      this.eating = -1;
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null) return;
    if (event.getType() != EventType.PRE) return;

    if (this.tryHeal) {
      switch (this.mode.getModeString()) {
        case "Auto":
          int gappleInHotbar = this.findHotbar(Items.golden_apple);
          if (gappleInHotbar != -1) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(gappleInHotbar));
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            for (int i = 0; i < 35; i++) {
              PacketUtil.sendPacket(new C03PacketPlayer(mc.thePlayer.onGround));
            }
            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            if (this.eatMessage.getValue()) {
              mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("Gapple eaten"));
            }
            this.tryHeal = false;
            this.timer.reset();
            this.delay = MathHelper.getRandomIntegerInRange(new Random(), this.min.getValue(), this.max.getValue());
          } else {
            this.tryHeal = false;
          }
          break;
        case "LegitAuto":
          if (this.eating == -1) {
            int gapple2 = this.findHotbar(Items.golden_apple);
            if (gapple2 == -1) {
              this.tryHeal = false;
              return;
            }
            PacketUtil.sendPacket(new C09PacketHeldItemChange(gapple2));
            if (this.eatMessage.getValue()) {
              mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("Gapple eaten"));
            }
            mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            this.eating = 0;
          } else if (this.eating > 35 || (this.fastEatValue.getValue() && this.eating > this.eatDelayValue.getValue())) {
            for (int i = 0; i < 35 - this.eating; i++) {
              PacketUtil.sendPacket(new C03PacketPlayer(mc.thePlayer.onGround));
            }
            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            this.timer.reset();
            this.tryHeal = false;
            this.delay = MathHelper.getRandomIntegerInRange(new Random(), this.min.getValue(), this.max.getValue());
            if (this.eatMessage.getValue()) {
              mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("Gapple eaten"));
            }
          }
          break;
        case "Legit":
          if (this.eating == -1) {
            int gapple3 = this.findHotbar(Items.golden_apple);
            if (gapple3 == -1) {
              this.tryHeal = false;
              return;
            }
            if (this.prevSlot == -1) {
              this.prevSlot = mc.thePlayer.inventory.currentItem;
            }
            mc.thePlayer.inventory.currentItem = gapple3;
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            this.eating = 0;
          } else if (this.eating > 35 || (this.fastEatValue.getValue() && this.eating > this.eatDelayValue.getValue())) {
            for (int i = 0; i < 35 - this.eating; i++) {
              PacketUtil.sendPacket(new C03PacketPlayer(mc.thePlayer.onGround));
            }
            this.timer.reset();
            this.tryHeal = false;
            this.delay = MathHelper.getRandomIntegerInRange(new Random(), this.min.getValue(), this.max.getValue());
            if (this.eatMessage.getValue()) {
              mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("Gapple eaten"));
            }
          }
          break;
        case "Head":
          int headInHotbar = this.findHotbar(Items.skull);
          if (headInHotbar != -1) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(headInHotbar));
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            this.timer.reset();
            this.tryHeal = false;
            this.delay = MathHelper.getRandomIntegerInRange(new Random(), this.min.getValue(), this.max.getValue());
          } else {
            this.tryHeal = false;
            if (this.eatMessage.getValue()) {
              mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("Gapple eaten"));
            }
          }
          break;
      }
    }

    if (mc.thePlayer.ticksExisted <= 10 && this.isDisable) {
      this.isDisable = false;
    }

    int absorp = MathHelper.ceiling_double_int((double) ((miau.mixin.IAccessorEntityLivingBase) mc.thePlayer).getAbsorptionAmount());

    if (!this.tryHeal && this.prevSlot != -1) {
      if (!this.switchBack) {
        this.switchBack = true;
        return;
      }
      mc.thePlayer.inventory.currentItem = this.prevSlot;
      this.eating = -1;
      this.prevSlot = -1;
      this.switchBack = false;
    }

    if ((this.groundCheck.getValue() && !mc.thePlayer.onGround)
        || (this.invCheck.getValue() && mc.currentScreen instanceof GuiContainer)
        || (absorp > 0 && this.absorpCheck.getValue())) {
      return;
    }

    if (this.waitRegen.getValue()
        && mc.thePlayer.isPotionActive(Potion.regeneration)
        && mc.thePlayer.getActivePotionEffect(Potion.regeneration).getDuration()
            > (int) (this.regenSec.getValue() * 20.0F)) {
      return;
    }

    if (!this.isDisable
        && mc.thePlayer.getHealth() <= (this.percent.getValue() / 100.0F) * mc.thePlayer.getMaxHealth()
        && this.timer.hasTimeElapsed((long) this.delay)) {
      if (this.tryHeal) return;
      this.tryHeal = true;
    }
  }

  private int findHotbar(net.minecraft.item.Item item) {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.getItem() == item) return i;
    }
    return -1;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}
