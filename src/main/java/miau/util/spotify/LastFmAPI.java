package miau.util.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import miau.util.client.ChatUtil;

public class LastFmAPI {
  public boolean isPlaying = false;
  public String trackName = "";
  public String artistName = "";
  public String albumUrl = "";
  public String currentMbid = "";

  private String user = "";
  private String apiKey = "";
  private Thread pollingThread;

  public void startConnection(String user, String apiKey) {
    this.user = user;
    this.apiKey = apiKey;

    if (pollingThread != null && pollingThread.isAlive()) {
      pollingThread.interrupt();
    }

    pollingThread =
        new Thread(
            () -> {
              while (!Thread.currentThread().isInterrupted()) {
                try {
                  pollLastFm();
                  Thread.sleep(3000);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } catch (Exception e) {
                  e.printStackTrace();
                }
              }
            });
    pollingThread.start();
    ChatUtil.display("§aConnected to Last.fm API...");
  }

  private void pollLastFm() throws Exception {
    if (user.isEmpty() || apiKey.isEmpty()) return;

    String urlString =
        String.format(
            "https://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user=%s&api_key=%s&format=json&limit=1",
            user, apiKey);

    URL url = new URL(urlString);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);

    int status = conn.getResponseCode();
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
    } else {
      conn.disconnect();
      isPlaying = false;
    }
  }

  private void parseResponse(String responseBody) {
    try {
      JsonObject json = new JsonParser().parse(responseBody).getAsJsonObject();
      if (json.has("recenttracks")) {
        JsonObject recent = json.getAsJsonObject("recenttracks");
        if (recent.has("track")) {
          JsonArray tracks = recent.getAsJsonArray("track");
          if (tracks.size() > 0) {
            JsonObject currentTrack = tracks.get(0).getAsJsonObject();

            boolean nowPlaying = false;
            if (currentTrack.has("@attr")) {
              JsonObject attr = currentTrack.getAsJsonObject("@attr");
              if (attr.has("nowplaying") && attr.get("nowplaying").getAsString().equals("true")) {
                nowPlaying = true;
              }
            }

            this.isPlaying = nowPlaying;

            if (nowPlaying) {
              if (currentTrack.has("name")) {
                this.trackName = currentTrack.get("name").getAsString();
              }
              if (currentTrack.has("artist")) {
                this.artistName = currentTrack.getAsJsonObject("artist").get("#text").getAsString();
              }

              if (currentTrack.has("image")) {
                JsonArray images = currentTrack.getAsJsonArray("image");
                String tempUrl = "";
                for (int i = 0; i < images.size(); i++) {
                  JsonObject img = images.get(i).getAsJsonObject();
                  if (img.get("size").getAsString().equals("extralarge")
                      || img.get("size").getAsString().equals("large")) {
                    tempUrl = img.get("#text").getAsString();
                    if (img.get("size").getAsString().equals("extralarge")) break;
                  }
                }
                this.albumUrl = tempUrl;
              }

              if (currentTrack.has("url")) {
                this.currentMbid = currentTrack.get("url").getAsString();
              } else if (currentTrack.has("mbid")) {
                this.currentMbid = currentTrack.get("mbid").getAsString();
              }
            }
            return;
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    this.isPlaying = false;
  }
}
