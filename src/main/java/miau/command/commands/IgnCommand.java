package miau.command.commands;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import miau.command.Command;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraft.util.StringUtils;

public class IgnCommand extends Command {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public IgnCommand() {
    super(new ArrayList<String>(Arrays.asList("username", "name", "ign")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    Session session = mc.getSession();
    if (session != null) {
      String username = session.getUsername();
      if (!StringUtils.isNullOrEmpty(username)) {
        try {
          Toolkit.getDefaultToolkit()
              .getSystemClipboard()
              .setContents(new StringSelection(username), null);
          ChatUtil.display("%sYour username has been copied to the clipboard (&o%s&r)&r", username);
        } catch (Exception e) {
          ChatUtil.display(("%sFailed to copy&r"));
        }
      }
    }
  }
}
