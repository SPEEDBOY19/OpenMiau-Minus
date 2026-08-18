package me.ksyz.accountmanager.gui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.MicrosoftAuth;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiCookieLogin extends GuiScreen {
  // Same Microsoft application as the regular "Add" (browser) flow, so the
  // resulting account refreshes exactly like any other Microsoft account.
  private static final String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
  private static final String SCOPE = "XboxLive.signin XboxLive.offline_access";

  private final GuiScreen previousScreen;

  private GuiButton chooseButton = null;
  private String status = null;
  private String cause = null;
  private String selectedFile = null;
  private volatile boolean chooserOpen = false;
  private ExecutorService executor = null;
  private volatile CompletableFuture<Void> task = null;
  private volatile boolean success = false;

  public GuiCookieLogin(GuiScreen previousScreen) {
    this.previousScreen = previousScreen;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    Keyboard.enableRepeatEvents(true);
    if (executor == null) {
      executor = Executors.newSingleThreadExecutor();
    }
    buttonList.add(
        chooseButton =
            new GuiButton(998, width / 2 - 100, height / 2, 200, 20, "Choose Cookie File..."));
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
    if (task != null && !task.isDone()) {
      task.cancel(true);
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  public void updateScreen() {
    // Disable the button while the chooser or an auth attempt is running.
    if (chooseButton != null) {
      chooseButton.enabled = !chooserOpen && (task == null || task.isDone());
    }
    if (success) {
      success = false;
      mc.displayGuiScreen(
          new GuiAccountManager(
              previousScreen,
              new Notification(
                  TextFormatting.translate(
                      String.format(
                          "&aSuccessful login! (%s)&r", SessionManager.get().getUsername())),
                  5000L)));
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();
    super.drawScreen(mouseX, mouseY, partialTicks);

    drawCenteredString(
        fontRendererObj,
        "Cookie Login",
        width / 2,
        height / 2 - fontRendererObj.FONT_HEIGHT / 2 - fontRendererObj.FONT_HEIGHT * 3 - 6,
        11184810);

    String info =
        status != null ? status : "&7Choose an exported cookies file (Netscape .txt or JSON).&r";
    drawCenteredString(
        fontRendererObj,
        TextFormatting.translate(info),
        width / 2,
        height / 2 - fontRendererObj.FONT_HEIGHT / 2 - fontRendererObj.FONT_HEIGHT - 8,
        -1);

    if (selectedFile != null) {
      drawCenteredString(
          fontRendererObj,
          TextFormatting.translate(String.format("&8File: &7%s&r", selectedFile)),
          width / 2,
          height / 2 + 26,
          -1);
    }

    if (cause != null) {
      String causeText = TextFormatting.translate(cause);
      Gui.drawRect(
          0,
          height - 2 - fontRendererObj.FONT_HEIGHT - 3,
          3 + mc.fontRendererObj.getStringWidth(causeText) + 3,
          height,
          0x64000000);
      drawString(fontRendererObj, causeText, 3, height - 2 - fontRendererObj.FONT_HEIGHT, -1);
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      if (!chooserOpen && (task == null || task.isDone())) {
        mc.displayGuiScreen(previousScreen);
      }
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button == null || !button.enabled) {
      return;
    }
    if (button.id == 998) {
      openFileChooser();
    }
  }

  /**
   * Opens a native file chooser on a background thread (so the render loop is not blocked), then
   * kicks off the cookie login once a file is picked.
   */
  private void openFileChooser() {
    if (chooserOpen || (task != null && !task.isDone())) {
      return;
    }
    chooserOpen = true;
    cause = null;
    status = "&7Opening file chooser...&r";

    Thread thread =
        new Thread(
            () -> {
              File chosen = null;
              try {
                try {
                  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                  //
                }
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select your exported cookies file");
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setFileFilter(
                    new FileNameExtensionFilter("Cookie files (*.txt, *.json)", "txt", "json"));
                chooser.setAcceptAllFileFilterUsed(true);

                JFrame parent = new JFrame();
                parent.setAlwaysOnTop(true);
                parent.setLocationRelativeTo(null);
                int result = chooser.showOpenDialog(parent);
                parent.dispose();
                if (result == JFileChooser.APPROVE_OPTION) {
                  chosen = chooser.getSelectedFile();
                }
              } catch (Throwable t) {
                status = String.format("&cFile chooser failed: %s&r", t.getMessage());
              } finally {
                chooserOpen = false;
              }

              if (chosen != null) {
                startLogin(chosen);
              } else if (status != null && status.contains("Opening")) {
                status = "&7No file selected.&r";
              }
            },
            "Cookie File Chooser");
    thread.setDaemon(true);
    thread.start();
  }

  private void startLogin(File file) {
    if (task != null && !task.isDone()) {
      return;
    }

    String[] cookieLines;
    try {
      String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      cookieLines = content.split("\\r?\\n");
    } catch (Exception e) {
      status = String.format("&cCould not read file: %s&r", e.getMessage());
      return;
    }
    if (cookieLines.length == 0) {
      status = "&cNo cookies were found in that file.&r";
      return;
    }

    selectedFile = file.getName();
    cause = null;
    MicrosoftAuth.CLIENT_ID = CLIENT_ID;
    MicrosoftAuth.SCOPE = SCOPE;
    AtomicReference<String> refreshToken = new AtomicReference<>("");
    AtomicReference<String> accessToken = new AtomicReference<>("");

    status = "&fAuthenticating with cookies (Sisu)&r";
    task =
        CompletableFuture.supplyAsync(
                () -> {
                  try {
                    StringBuilder cookies = new StringBuilder();
                    java.util.List<String> cooki = new java.util.ArrayList<>();
                    for (String cookie : cookieLines) {
                      String[] parts = cookie.split("\t");
                      if (parts.length >= 7
                          && parts[0].endsWith("login.live.com")
                          && !cooki.contains(parts[5])) {
                        cookies.append(parts[5]).append("=").append(parts[6]).append("; ");
                        cooki.add(parts[5]);
                      }
                    }
                    if (cookies.length() > 2) {
                      cookies = new StringBuilder(cookies.substring(0, cookies.length() - 2));
                    } else {
                      throw new Exception("No login.live.com cookies found!");
                    }

                    status = "&fAcquiring Xbox token (Sisu)&r";
                    javax.net.ssl.HttpsURLConnection connection =
                        (javax.net.ssl.HttpsURLConnection)
                            new java.net.URL(
                                    "https://sisu.xboxlive.com/connect/XboxLive/?state=login&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d&tid=896928775&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin&aid=1142970254")
                                .openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setInstanceFollowRedirects(false);
                    connection.connect();

                    String location = connection.getHeaderField("Location");
                    if (location == null)
                      throw new Exception("No Location header in first redirect");
                    location = location.replaceAll(" ", "%20");

                    connection =
                        (javax.net.ssl.HttpsURLConnection)
                            new java.net.URL(location).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    connection.setRequestProperty("Cookie", cookies.toString());
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setInstanceFollowRedirects(false);
                    connection.connect();

                    String location2 = connection.getHeaderField("Location");
                    if (location2 == null)
                      throw new Exception("No Location header in second redirect");

                    connection =
                        (javax.net.ssl.HttpsURLConnection)
                            new java.net.URL(location2).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    connection.setRequestProperty("Cookie", cookies.toString());
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setInstanceFollowRedirects(false);
                    connection.connect();

                    String location3 = connection.getHeaderField("Location");
                    if (location3 == null)
                      throw new Exception(
                          "No Location header in third redirect (Cookie invalid or expired)");

                    String accTokenStr = location3.split("accessToken=")[1];
                    if (accTokenStr.contains("&")) accTokenStr = accTokenStr.split("&")[0];

                    String decoded =
                        new String(
                                java.util.Base64.getDecoder().decode(accTokenStr),
                                StandardCharsets.UTF_8)
                            .split("\"rp://api.minecraftservices.com/\",")[1];
                    String token = decoded.split("\"Token\":\"")[1].split("\"")[0];
                    String uhs =
                        decoded
                            .split(
                                java.util.regex.Pattern.quote(
                                    "{\"DisplayClaims\":{\"xui\":[{\"uhs\":\""))[1]
                            .split("\"")[0];

                    Map<String, String> result = new java.util.HashMap<>();
                    result.put("Token", token);
                    result.put("uhs", uhs);
                    return result;
                  } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(
                        "Failed Sisu Xbox authentication! " + e.getMessage(), e);
                  }
                },
                executor)
            .thenComposeAsync(
                xboxXstsData -> {
                  status = "&fAcquiring Minecraft access token&r";
                  return MicrosoftAuth.acquireMCAccessToken(
                      xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor);
                })
            .thenComposeAsync(
                mcToken -> {
                  status = "&fFetching your Minecraft profile&r";
                  accessToken.set(mcToken);
                  return MicrosoftAuth.login(mcToken, executor);
                })
            .thenAccept(
                session -> {
                  status = null;
                  Account acc =
                      new Account(
                          refreshToken.get(),
                          accessToken.get(),
                          session.getUsername(),
                          0L,
                          CLIENT_ID,
                          SCOPE,
                          Account.TYPE_COOKIE);
                  for (Account account : AccountManager.accounts) {
                    if (acc.getUsername().equals(account.getUsername())) {
                      acc.setUnban(account.getUnban());
                      break;
                    }
                  }
                  AccountManager.accounts.add(acc);
                  AccountManager.save();
                  SessionManager.set(session);
                  success = true;
                })
            .exceptionally(
                error -> {
                  status = String.format("&c%s&r", error.getMessage());
                  if (error.getCause() != null) {
                    cause = String.format("&c%s&r", error.getCause().getMessage());
                  }
                  task = null;
                  return null;
                });
  }
}
