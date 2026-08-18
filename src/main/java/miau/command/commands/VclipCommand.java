package miau.command.commands;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import miau.command.Command;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;

public class VclipCommand extends Command {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final DecimalFormat df =
      new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));

  public VclipCommand() {
    super(new ArrayList<>(Collections.singletonList("vclip")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    if (args.size() >= 2) {
      double distance = 0.0;
      try {
        distance = Double.parseDouble(args.get(1));
      } catch (NumberFormatException e) {
      } finally {
        mc.thePlayer.setPositionAndUpdate(
            mc.thePlayer.posX, mc.thePlayer.posY + distance, mc.thePlayer.posZ);
        ChatUtil.display("%sClipped (%s blocks)", df.format(distance));
      }
      return;
    }
    ChatUtil.display("%sUsage: .%s <&odistance&r>&r", args.get(0).toLowerCase(Locale.ROOT));
  }
}
