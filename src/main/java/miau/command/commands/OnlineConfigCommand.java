package miau.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import miau.Miau;
import miau.command.Command;
import miau.config.online.OnlineConfigApplier;
import miau.config.online.OnlineConfigClient;
import miau.config.online.OnlineConfigEntry;
import miau.util.client.ChatUtil;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;

public class OnlineConfigCommand extends Command {
  private static final ExecutorService EXECUTOR =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "Miau OnlineConfig");
            thread.setDaemon(true);
            return thread;
          });

  private final OnlineConfigClient client = new OnlineConfigClient();
  private volatile List<OnlineConfigEntry> cache = Collections.emptyList();

  public OnlineConfigCommand() {
    super(new ArrayList<>(Arrays.asList("onlineconfig", "onlinecfg", "ocfg", "online")));
  }

  @Override
  public void runCommand(ArrayList<String> args) {
    if (args.size() < 2) {
      usage();
      return;
    }
    String sub = args.get(1).toLowerCase(Locale.ROOT);
    if (sub.equals("list") || sub.equals("l")) {
      async(this::listConfigs);
    } else if (sub.equals("load")) {
      if (args.size() < 3) {
        ChatUtil.display(Miau.clientName + "Missing online config id/name&r");
        return;
      }
      async(() -> loadConfig(String.join(" ", args.subList(2, args.size()))));
    } else {
      usage();
    }
  }

  private void listConfigs() {
    try {
      List<OnlineConfigEntry> entries =
          Collections.unmodifiableList(new ArrayList<>(client.list()));
      runOnClientThread(
          () -> {
            cache = entries;
            ChatUtil.display(
                Miau.clientName
                    + (entries.isEmpty() ? "No online configs found&r" : "Online configs:&r"));
            for (OnlineConfigEntry entry : entries) {
              sendEntry(entry);
            }
          });
    } catch (Exception e) {
      runOnClientThread(
          () ->
              ChatUtil.sendFormatted(
                  Miau.clientName + "Failed to list online configs: &c" + e.getMessage() + "&r"));
    }
  }

  private void loadConfig(String input) {
    try {
      OnlineConfigEntry entry = findEntry(input);
      if (entry == null) {
        runOnClientThread(
            () ->
                ChatUtil.display(
                    Miau.clientName + "Online config not found (&o" + input + "&r)&r"));
        return;
      }
      String json = client.load(entry.getId());
      runOnClientThread(() -> applyConfig(entry, json));
    } catch (Exception e) {
      runOnClientThread(
          () ->
              ChatUtil.sendFormatted(
                  Miau.clientName + "Failed to load online config: &c" + e.getMessage() + "&r"));
    }
  }

  private void applyConfig(OnlineConfigEntry entry, String json) {
    try {
      showMetadata(entry);
      int applied = new OnlineConfigApplier().apply(json);
      ChatUtil.display(
          "%sOnline config loaded (&a&o%s&r) &7- applied %d setting(s)&r",
          entry.getName(), applied);
    } catch (Exception e) {
      ChatUtil.display(
          Miau.clientName + "Failed to load online config: &c" + e.getMessage() + "&r");
    }
  }

  private OnlineConfigEntry findEntry(String input) throws Exception {
    List<OnlineConfigEntry> entries = cache;
    if (entries.isEmpty()) {
      entries = Collections.unmodifiableList(new ArrayList<>(client.list()));
      cache = entries;
    }
    for (OnlineConfigEntry entry : entries) {
      if (entry.getId().equalsIgnoreCase(input) || entry.getName().equalsIgnoreCase(input)) {
        return entry;
      }
    }
    return null;
  }

  private void sendEntry(OnlineConfigEntry entry) {
    String command = ".onlineconfig load " + entry.getId();
    String version = entry.getVersion().isEmpty() ? "" : " §7v§e" + entry.getVersion();
    String line =
        String.format(
            "§7» §f%s%s §7[§b%s§7] §7by §a%s",
            entry.getName(), version, safe(entry.setting_type), entry.getAuthor());
    ChatUtil.send(
        new ChatComponentText(line)
            .setChatStyle(
                new ChatStyle()
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .setChatHoverEvent(
                        new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText(
                                command
                                    + "\n§7id: §f"
                                    + entry.getId()
                                    + "\n§7status: §f"
                                    + safe(entry.status_type))))));
  }

  private void showMetadata(OnlineConfigEntry entry) {
    ChatUtil.display(Miau.clientName + "Loading online config...&r");
    ChatUtil.display("&fName: &a" + entry.getName() + "&r");
    ChatUtil.display("&fUpload time: &b" + safe(entry.date) + "&r");
    ChatUtil.display("&fAuthor: &a" + entry.getAuthor() + "&r");
    ChatUtil.display("&fType: &b" + safe(entry.setting_type) + "&r");
    ChatUtil.display("&fStatus: &e" + safe(entry.status_type) + "&r");
    if (!entry.getVersion().isEmpty()) {
      ChatUtil.display("&fVersion: &e" + entry.getVersion() + "&r");
    }
    if (entry.description != null && !entry.description.trim().isEmpty()) {
      ChatUtil.display("&fDescription: &7" + entry.description + "&r");
    }
  }

  private void usage() {
    ChatUtil.display(
        Miau.clientName + "Usage: .onlineconfig &olist&r | .onlineconfig &oload&r <&oid/name&r>");
  }

  private void async(Runnable task) {
    EXECUTOR.execute(task);
  }

  private void runOnClientThread(Runnable task) {
    net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(task);
  }

  private String safe(String value) {
    return value == null || value.trim().isEmpty() ? "unknown" : value;
  }
}
