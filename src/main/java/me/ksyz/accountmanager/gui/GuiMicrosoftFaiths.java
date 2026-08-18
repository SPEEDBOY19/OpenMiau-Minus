package me.ksyz.accountmanager.gui;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.OAuthHandler;
import me.ksyz.accountmanager.auth.OAuthServer;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public class GuiMicrosoftFaiths extends GuiScreen {
  private static final String CLIENT_ID = "0add8caf-2cc6-4546-b798-c3d171217dd9";
  private static final String REDIRECT_URI = "http://localhost:21919/login";
  private static final String SCOPE = "XboxLive.signin%20offline_access";
  private static final String XBOX_AUTH_URL = "https://login.live.com/oauth20_token.srf";
  private static final String XBOX_REFRESH_DATA =
      "client_id=<client_id>&redirect_uri=<redirect_uri>&grant_type=authorization_code&code=";
  private static final String XBOX_REFRESH_TOKEN_DATA =
      "client_id=<client_id>&redirect_uri=<redirect_uri>&grant_type=refresh_token&refresh_token=";
  private static final ExecutorService SHARED_EXECUTOR = Executors.newSingleThreadExecutor();

  private final GuiScreen previousScreen;
  private GuiButton cancelButton;
  private ExecutorService executor;
  private CompletableFuture<Void> task;
  private String status;
  private boolean success;
  private OAuthServer oAuthServer;

  public GuiMicrosoftFaiths(GuiScreen previousScreen) {
    this.previousScreen = previousScreen;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    buttonList.add(
        cancelButton = new GuiButton(0, width / 2 - 50, height / 2 + 60, 100, 20, "Cancel"));

    if (task == null) {
      status = "&fPreparing Microsoft login...&r";
      if (executor == null) {
        executor = Executors.newSingleThreadExecutor();
      }
      startOAuthFlow();
    }
  }

  private void startOAuthFlow() {
    try {
      oAuthServer =
          new OAuthServer(
              new OAuthHandler() {
                @Override
                public void openUrl(String url) {
                  java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
                  java.awt.datatransfer.StringSelection strSel =
                      new java.awt.datatransfer.StringSelection(url);
                  toolkit.getSystemClipboard().setContents(strSel, null);
                  status = "&fLogin link copied to clipboard! Open your browser and paste it.&r";
                }

                @Override
                public void authResult(String code, String clientId, String scope) {
                  status = "&fAuth code received, logging in...&r";
                  loginWithCode(code, clientId, scope);
                }

                @Override
                public void authError(String error) {
                  status = "&cError: " + error + "&r";
                }
              },
              CLIENT_ID,
              REDIRECT_URI,
              SCOPE);
      oAuthServer.start();
    } catch (Exception e) {
      status = "&cFailed to start authentication server: " + e.getMessage() + "&r";
    }
  }

  private void loginWithCode(String code, String clientId, String scope) {
    AtomicReference<String> refreshToken = new AtomicReference<>("");
    task =
        CompletableFuture.supplyAsync(
                () -> {
                  try {
                    String body =
                        XBOX_REFRESH_DATA
                                .replace("<client_id>", clientId)
                                .replace("<redirect_uri>", REDIRECT_URI)
                            + code;
                    URL url = new URL(XBOX_AUTH_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                    conn.connect();
                    java.util.Scanner scanner =
                        new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
                    String response = scanner.hasNext() ? scanner.next() : "";
                    conn.disconnect();

                    com.google.gson.JsonObject json =
                        new com.google.gson.JsonParser().parse(response).getAsJsonObject();
                    String token =
                        json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
                    String access =
                        json.has("access_token") ? json.get("access_token").getAsString() : null;
                    if (token == null) throw new Exception("Failed to get refresh token");
                    refreshToken.set(token);

                    String mcToken = microsoftToMinecraft(access, clientId);
                    net.minecraft.util.Session session = fetchProfile(mcToken);
                    return new Object[] {session, token};
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                },
                executor)
            .thenAccept(
                result -> {
                  net.minecraft.util.Session session = (net.minecraft.util.Session) result[0];
                  String token = (String) result[1];
                  Account acc =
                      new Account(
                          token, session.getToken(), session.getUsername(), CLIENT_ID, SCOPE);
                  for (Account a : AccountManager.accounts) {
                    if (acc.getUsername().equals(a.getUsername())) {
                      acc.setUnban(a.getUnban());
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
                  status = "&c" + error.getCause().getMessage() + "&r";
                  return null;
                });
  }

  public static String refreshToken(String refreshToken, String clientId) throws Exception {
    String body =
        XBOX_REFRESH_TOKEN_DATA
                .replace("<client_id>", clientId)
                .replace("<redirect_uri>", REDIRECT_URI)
            + refreshToken;
    URL url = new URL(XBOX_AUTH_URL);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    conn.setDoOutput(true);
    conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    conn.connect();
    java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
    String response = scanner.hasNext() ? scanner.next() : "";
    conn.disconnect();
    com.google.gson.JsonObject json =
        new com.google.gson.JsonParser().parse(response).getAsJsonObject();
    if (!json.has("refresh_token")) return null;
    return json.get("refresh_token").getAsString();
  }

  public static void attemptAutoLogin() {
    AccountManager.load();
    if (AccountManager.accounts.isEmpty()) return;
    Account last = AccountManager.accounts.get(AccountManager.accounts.size() - 1);
    if (last == null || last.getRefreshToken().isEmpty() || last.getUsername().isEmpty()) return;
    try {
      String newRefresh = refreshToken(last.getRefreshToken(), last.getClientId());
      if (newRefresh == null) return;
      String body =
          XBOX_REFRESH_TOKEN_DATA
                  .replace("<client_id>", last.getClientId())
                  .replace("<redirect_uri>", REDIRECT_URI)
              + newRefresh;
      URL url = new URL(XBOX_AUTH_URL);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setDoOutput(true);
      conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
      conn.connect();
      java.util.Scanner s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
      String resp = s.hasNext() ? s.next() : "";
      conn.disconnect();
      com.google.gson.JsonObject json =
          new com.google.gson.JsonParser().parse(resp).getAsJsonObject();
      if (!json.has("access_token")) return;
      String accessToken = json.get("access_token").getAsString();
      String mcToken =
          new GuiMicrosoftFaiths(null).microsoftToMinecraft(accessToken, last.getClientId());
      net.minecraft.util.Session session = new GuiMicrosoftFaiths(null).fetchProfile(mcToken);
      SessionManager.set(session);
      int idx = AccountManager.accounts.size() - 1;
      AccountManager.accounts.set(
          idx,
          new Account(
              newRefresh,
              session.getToken(),
              session.getUsername(),
              last.getUnban(),
              last.getClientId(),
              last.getScope()));
      AccountManager.save();
    } catch (Exception e) {
      // auto-login failed
    }
  }

  private String microsoftToMinecraft(String msAccessToken, String clientId) throws Exception {
    String rpsRule = "d=" + msAccessToken;
    URL url = new URL("https://user.auth.xboxlive.com/user/authenticate");
    String xblBody =
        "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"<rps_ticket>\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}"
            .replace("<rps_ticket>", rpsRule);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Accept", "application/json");
    conn.setDoOutput(true);
    conn.getOutputStream().write(xblBody.getBytes(StandardCharsets.UTF_8));
    conn.connect();
    java.util.Scanner s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
    String xblResp = s.hasNext() ? s.next() : "";
    conn.disconnect();
    com.google.gson.JsonObject xblJson =
        new com.google.gson.JsonParser().parse(xblResp).getAsJsonObject();
    String xblToken = xblJson.get("Token").getAsString();
    String userHash =
        xblJson
            .get("DisplayClaims")
            .getAsJsonObject()
            .get("xui")
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get("uhs")
            .getAsString();

    URL xstsUrl = new URL("https://xsts.auth.xboxlive.com/xsts/authorize");
    String xstsBody =
        "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"<xbl_token>\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}"
            .replace("<xbl_token>", xblToken);
    conn = (HttpURLConnection) xstsUrl.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Accept", "application/json");
    conn.setDoOutput(true);
    conn.getOutputStream().write(xstsBody.getBytes(StandardCharsets.UTF_8));
    conn.connect();
    s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
    String xstsResp = s.hasNext() ? s.next() : "";
    conn.disconnect();
    com.google.gson.JsonObject xstsJson =
        new com.google.gson.JsonParser().parse(xstsResp).getAsJsonObject();
    String xstsToken = xstsJson.get("Token").getAsString();

    URL mcUrl = new URL("https://api.minecraftservices.com/authentication/login_with_xbox");
    String mcBody =
        "{\"identityToken\":\"XBL3.0 x=<userhash>;<xsts_token>\"}"
            .replace("<userhash>", userHash)
            .replace("<xsts_token>", xstsToken);
    conn = (HttpURLConnection) mcUrl.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Accept", "application/json");
    conn.setDoOutput(true);
    conn.getOutputStream().write(mcBody.getBytes(StandardCharsets.UTF_8));
    conn.connect();
    s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
    String mcResp = s.hasNext() ? s.next() : "";
    conn.disconnect();
    com.google.gson.JsonObject mcJson =
        new com.google.gson.JsonParser().parse(mcResp).getAsJsonObject();
    return mcJson.get("access_token").getAsString();
  }

  private net.minecraft.util.Session fetchProfile(String mcToken) throws Exception {
    URL url = new URL("https://api.minecraftservices.com/minecraft/profile");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + mcToken);
    conn.connect();
    java.util.Scanner s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
    String resp = s.hasNext() ? s.next() : "";
    conn.disconnect();
    com.google.gson.JsonObject json =
        new com.google.gson.JsonParser().parse(resp).getAsJsonObject();
    if (!json.has("id")) throw new Exception("No Minecraft profile found");
    return new net.minecraft.util.Session(
        json.get("name").getAsString(), json.get("id").getAsString(), mcToken, "mojang");
  }

  @Override
  public void onGuiClosed() {
    if (oAuthServer != null) oAuthServer.stop(true);
    if (task != null && !task.isDone()) {
      task.cancel(true);
      executor.shutdownNow();
    }
  }

  @Override
  public void updateScreen() {
    if (success) {
      mc.displayGuiScreen(
          new GuiAccountManager(
              previousScreen,
              new Notification(
                  TextFormatting.translate(
                      String.format(
                          "&aSuccessful login! (%s)&r", SessionManager.get().getUsername())),
                  5000L)));
      success = false;
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();
    super.drawScreen(mouseX, mouseY, partialTicks);
    drawCenteredString(
        fontRendererObj, "Microsoft Authentication", width / 2, height / 2 - 50, 11184810);
    if (status != null) {
      drawCenteredString(
          fontRendererObj, TextFormatting.translate(status), width / 2, height / 2 - 10, -1);
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      actionPerformed(cancelButton);
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button == null || !button.enabled) return;
    if (button.id == 0) {
      if (oAuthServer != null) oAuthServer.stop(true);
      mc.displayGuiScreen(new GuiAccountManager(previousScreen));
    }
  }
}
