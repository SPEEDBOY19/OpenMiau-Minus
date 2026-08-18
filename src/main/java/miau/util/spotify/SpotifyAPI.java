package miau.util.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;

public class SpotifyAPI {
  public boolean isPlaying = false;
  public String trackName = "";
  public String artistName = "";
  public String albumUrl = "";
  public String currentMbid = "";
  public long progressMs = 0;
  public long durationMs = 1;
  public long lastPollMs = 0;

  private String clientId = "";
  private String clientSecret = "";
  private String accessToken = "";
  private String refreshToken = "";
  private Thread pollingThread;
  private HttpServer server;

  private static final String REDIRECT_URI = "https://api.getmiau.today/api/spotify/callback";
  private static final File SPOTIFY_CREDS_DIR = new File(Minecraft.getMinecraft().mcDataDir, "Miau_Spotify.json");

  public void startConnection(String clientId, String clientSecret) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;

    loadTokens();

    if (accessToken.isEmpty() && refreshToken.isEmpty()) {
      startAuthServer();
      openBrowserForAuth();
    } else {
      startPolling();
    }
  }

  public void stopConnection() {
    if (pollingThread != null && pollingThread.isAlive()) {
      pollingThread.interrupt();
    }
    if (server != null) {
      server.stop(0);
      server = null;
    }
    isPlaying = false;
  }

  private void startAuthServer() {
    try {
      if (server != null) return;

      server = HttpServer.create(new InetSocketAddress(8080), 0);
      server.createContext("/callback", new HttpHandler() {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
          String query = exchange.getRequestURI().getQuery();
          String response = "Spotify Authentication Complete! You can close this tab.";
          exchange.sendResponseHeaders(200, response.length());
          OutputStream os = exchange.getResponseBody();
          os.write(response.getBytes());
          os.close();

          if (query != null && query.contains("code=")) {
            String code = query.split("code=")[1].split("&")[0];
            exchangeCodeForTokens(code);
          }
        }
      });
      server.setExecutor(null);
      server.start();
      ChatUtil.display("§aStarted local HTTPS server on port 8080 for Spotify Auth.");
    } catch (Exception e) {
      ChatUtil.display("§cFailed to start local HTTPS server for Spotify Auth: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void openBrowserForAuth() {
    try {
      String authUrl = "https://accounts.spotify.com/authorize?"
          + "client_id=" + URLEncoder.encode(clientId, "UTF-8")
          + "&response_type=code"
          + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
          + "&scope=" + URLEncoder.encode("user-read-currently-playing user-read-playback-state", "UTF-8");

      if (java.awt.Desktop.isDesktopSupported()) {
        java.awt.Desktop.getDesktop().browse(new java.net.URI(authUrl));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void exchangeCodeForTokens(String code) {
    try {
      URL url = new URL("https://accounts.spotify.com/api/token");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

      String authHeader = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
      conn.setRequestProperty("Authorization", "Basic " + authHeader);
      conn.setDoOutput(true);

      String body = "grant_type=authorization_code&code=" + code + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8");
      try (OutputStream os = conn.getOutputStream()) {
        byte[] input = body.getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }

      int status = conn.getResponseCode();
      if (status == 200) {
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
          content.append(inputLine);
        }
        in.close();

        JsonObject json = new JsonParser().parse(content.toString()).getAsJsonObject();
        accessToken = json.get("access_token").getAsString();
        if (json.has("refresh_token")) {
          refreshToken = json.get("refresh_token").getAsString();
        }
        saveTokens();
        ChatUtil.display("§aSuccessfully authenticated with Spotify!");
        
        if (server != null) {
          server.stop(0);
          server = null;
        }

        startPolling();
      } else {
        ChatUtil.display("§cFailed to exchange code for tokens. Status: " + status);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void refreshAccessToken() {
    if (refreshToken.isEmpty()) return;
    try {
      URL url = new URL("https://accounts.spotify.com/api/token");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

      String authHeader = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
      conn.setRequestProperty("Authorization", "Basic " + authHeader);
      conn.setDoOutput(true);

      String body = "grant_type=refresh_token&refresh_token=" + refreshToken;
      try (OutputStream os = conn.getOutputStream()) {
        byte[] input = body.getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }

      int status = conn.getResponseCode();
      if (status == 200) {
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
          content.append(inputLine);
        }
        in.close();

        JsonObject json = new JsonParser().parse(content.toString()).getAsJsonObject();
        accessToken = json.get("access_token").getAsString();
        saveTokens();
      } else {
        ChatUtil.display("§cFailed to refresh Spotify token. Re-authenticating...");
        accessToken = "";
        refreshToken = "";
        saveTokens();
        startAuthServer();
        openBrowserForAuth();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void startPolling() {
    if (pollingThread != null && pollingThread.isAlive()) {
      pollingThread.interrupt();
    }

    pollingThread = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          pollSpotify();
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    pollingThread.start();
    ChatUtil.display("§aConnected to Spotify API...");
  }

  private void pollSpotify() throws Exception {
    if (accessToken.isEmpty()) return;

    URL url = new URL("https://api.spotify.com/v1/me/player/currently-playing");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);

    int status = conn.getResponseCode();
    if (status == 401) {
      refreshAccessToken();
      return;
    }

    if (status == 200) {
      BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      String inputLine;
      StringBuilder content = new StringBuilder();
      while ((inputLine = in.readLine()) != null) {
        content.append(inputLine);
      }
      in.close();
      conn.disconnect();

      parseResponse(content.toString());
    } else if (status == 204) {
      isPlaying = false;
      conn.disconnect();
    } else {
      isPlaying = false;
      conn.disconnect();
    }
  }

  private void parseResponse(String responseBody) {
    try {
      if (responseBody.isEmpty()) {
        isPlaying = false;
        return;
      }
      
      JsonObject json = new JsonParser().parse(responseBody).getAsJsonObject();
      
      if (json.has("is_playing")) {
        isPlaying = json.get("is_playing").getAsBoolean();
      }

      if (json.has("progress_ms")) {
        progressMs = json.get("progress_ms").getAsLong();
      }
      lastPollMs = System.currentTimeMillis();

      if (json.has("item") && !json.get("item").isJsonNull()) {
        JsonObject item = json.getAsJsonObject("item");

        if (item.has("name")) {
          this.trackName = item.get("name").getAsString();
        }

        if (item.has("duration_ms")) {
          this.durationMs = item.get("duration_ms").getAsLong();
        }

        if (item.has("artists")) {
          JsonArray artists = item.getAsJsonArray("artists");
          if (artists.size() > 0) {
            this.artistName = artists.get(0).getAsJsonObject().get("name").getAsString();
          }
        }

        if (item.has("album")) {
          JsonObject album = item.getAsJsonObject("album");
          if (album.has("images")) {
            JsonArray images = album.getAsJsonArray("images");
            if (images.size() > 0) {
              this.albumUrl = images.get(0).getAsJsonObject().get("url").getAsString();
            }
          }
        }

        if (item.has("id")) {
          this.currentMbid = item.get("id").getAsString();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      isPlaying = false;
    }
  }

  private void saveTokens() {
    JsonObject tokenObj = new JsonObject();
    tokenObj.addProperty("access_token", accessToken);
    tokenObj.addProperty("refresh_token", refreshToken);
    try (Writer writer = new BufferedWriter(new FileWriter(SPOTIFY_CREDS_DIR))) {
      new com.google.gson.Gson().toJson(tokenObj, writer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void loadTokens() {
    if (!SPOTIFY_CREDS_DIR.exists()) return;
    try (Reader reader = new FileReader(SPOTIFY_CREDS_DIR)) {
      JsonObject tokenObj = new JsonParser().parse(reader).getAsJsonObject();
      if (tokenObj.has("access_token")) {
        accessToken = tokenObj.get("access_token").getAsString();
      }
      if (tokenObj.has("refresh_token")) {
        refreshToken = tokenObj.get("refresh_token").getAsString();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
