package miau.module.modules.minigames;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.client.ChatUtil;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.BlockPos;

public class ThePitUtils extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty autoFlashQuestion = new BooleanProperty("autoflashquestion", true);
  public final FloatProperty delay =
      new FloatProperty("delay", 0f, 0f, 0f, 10f, autoFlashQuestion::getValue);
  public final BooleanProperty autoEgg = new BooleanProperty("autoegg", true);
  public final BooleanProperty debug = new BooleanProperty("debug", false);

  private String pendingAnswer = null;
  private long answerTime = 0;
  private final TimerUtil eggTimer = new TimerUtil();

  public ThePitUtils() {
    super("ThePitUtils", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (pendingAnswer != null && System.currentTimeMillis() >= answerTime) {
      mc.thePlayer.sendChatMessage(pendingAnswer);
      if (debug.getValue()) {
        ChatUtil.display("&a[ThePitUtils] Answered: &f" + pendingAnswer);
      }
      pendingAnswer = null;
    }

    if (autoEgg.getValue()) {
      if (mc.thePlayer != null && mc.theWorld != null && mc.playerController != null) {
        float reach = mc.playerController.getBlockReachDistance();
        int range = (int) Math.ceil(reach);
        for (int x = -range; x <= range; x++) {
          for (int y = -range; y <= range; y++) {
            for (int z = -range; z <= range; z++) {
              BlockPos pos =
                  new BlockPos(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z);
              Block block = mc.theWorld.getBlockState(pos).getBlock();
              if (block == Blocks.dragon_egg) {
                double distSq = mc.thePlayer.getDistanceSqToCenter(pos);
                if (distSq <= reach * reach) {
                  PacketUtil.sendPacket(
                      new C08PacketPlayerBlockPlacement(
                          pos, 1, mc.thePlayer.getHeldItem(), 0.5f, 0.5f, 0.5f));
                  mc.thePlayer.swingItem();
                  if (debug.getValue()) {
                    ChatUtil.display(
                        "&a[ThePitUtils] Clicked Dragon Egg at "
                            + pos.getX()
                            + ", "
                            + pos.getY()
                            + ", "
                            + pos.getZ());
                  }
                  return;
                }
              }
            }
          }
        }
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE) return;
    Packet<?> packet = event.getPacket();

    if (packet instanceof S02PacketChat) {
      S02PacketChat chatPacket = (S02PacketChat) packet;
      String unformattedText = chatPacket.getChatComponent().getUnformattedText();

      if (autoFlashQuestion.getValue() && unformattedText.contains("Nhanh như chớp! Giải: ")) {
        String equation = unformattedText.substring(unformattedText.indexOf("Giải: ") + 6).trim();
        try {
          double result = eval(equation);
          String answerStr =
              (result == (long) result) ? String.valueOf((long) result) : String.valueOf(result);

          float minDelay = delay.getValue();
          float maxDelay = delay.getSecondValue();
          float delaySecs = minDelay + (float) Math.random() * (maxDelay - minDelay);
          long delayMs = (long) (delaySecs * 1000);

          if (delayMs > 0) {
            pendingAnswer = answerStr;
            answerTime = System.currentTimeMillis() + delayMs;
          } else {
            mc.thePlayer.sendChatMessage(answerStr);
            if (debug.getValue()) {
              ChatUtil.display("&a[ThePitUtils] Answered: &f" + answerStr);
            }
          }
        } catch (Exception e) {
          if (debug.getValue()) {
            ChatUtil.display("&c[ThePitUtils] Failed to parse equation: " + equation);
          }
        }
      }
    }
  }

  private static double eval(final String str) {
    return new Object() {
      int pos = -1, ch;

      void nextChar() {
        ch = (++pos < str.length()) ? str.charAt(pos) : -1;
      }

      boolean eat(int charToEat) {
        while (ch == ' ') nextChar();
        if (ch == charToEat) {
          nextChar();
          return true;
        }
        return false;
      }

      double parse() {
        nextChar();
        double x = parseExpression();
        if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
        return x;
      }

      double parseExpression() {
        double x = parseTerm();
        for (; ; ) {
          if (eat('+')) x += parseTerm();
          else if (eat('-')) x -= parseTerm();
          else return x;
        }
      }

      double parseTerm() {
        double x = parseFactor();
        for (; ; ) {
          if (eat('*') || eat('x') || eat('X')) x *= parseFactor();
          else if (eat('/')) x /= parseFactor();
          else return x;
        }
      }

      double parseFactor() {
        if (eat('+')) return parseFactor();
        if (eat('-')) return -parseFactor();

        double x;
        int startPos = this.pos;
        if (eat('(')) {
          x = parseExpression();
          eat(')');
        } else if ((ch >= '0' && ch <= '9') || ch == '.') {
          while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
          x = Double.parseDouble(str.substring(startPos, this.pos));
        } else {
          throw new RuntimeException("Unexpected: " + (char) ch);
        }
        return x;
      }
    }.parse();
  }
}
