package miau.management;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

public class MiauAPI {

  public static final String APIBASE = "https://api.getmiau.today/";
  public static final String BRANCH = "myau";

  private static final String API_V1 = APIBASE + "api/v1";
  private static final int TIMEOUT_MS = 10000;

  public static String listOnlineConfigs() throws Exception {
    return get(clientPath("settings"), "Miau/OnlineConfig");
  }

  public static String loadOnlineConfig(String settingId) throws Exception {
    return get(clientPath("settings/" + encode(settingId)), "Miau/OnlineConfig");
  }

  public static String listUserConfigs() throws Exception {
    return get(clientPath("user-configs"), "Miau/UserConfig");
  }

  public static String loadUserConfig(String configId) throws Exception {
    return get(clientPath("user-configs/" + encode(configId)), "Miau/UserConfig");
  }

  public static String rpcConfig() throws Exception {
    return get(APIBASE + "api/rpc", "Miau/RPC");
  }

  public static String getClientVersion() throws Exception {
    return get(API_V1 + "/client/version", "Miau/Version");
  }

  public static String getClientNotification() throws Exception {
    return get(API_V1 + "/client/notification", "Miau/Notification");
  }

  public static boolean isOutdated(String current, String latest) {
    if (current == null || latest == null) return false;
    if (current.equals(latest)) return false;

    String[] currParts = current.replaceAll("[^0-9.]", "").split("\\.");
    String[] latestParts = latest.replaceAll("[^0-9.]", "").split("\\.");

    int length = Math.max(currParts.length, latestParts.length);
    for (int i = 0; i < length; i++) {
      int curr =
          i < currParts.length && !currParts[i].isEmpty() ? Integer.parseInt(currParts[i]) : 0;
      int lat =
          i < latestParts.length && !latestParts[i].isEmpty()
              ? Integer.parseInt(latestParts[i])
              : 0;
      if (curr < lat) return true;
      if (curr > lat) return false;
    }
    return false;
  }

  public static String encode(String value) throws Exception {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
  }

  private static String clientPath(String path) throws Exception {
    return API_V1 + "/client/" + encode(BRANCH) + "/" + path;
  }

  private static String get(String url, String userAgent) throws Exception {
    return request("GET", url, null, userAgent, Collections.emptyMap());
  }

  private static String request(
      String method, String url, String body, String userAgent, Map<String, String> extraHeaders)
      throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    try {
      connection.setRequestMethod(method);
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setRequestProperty("User-Agent", userAgent);

      if (extraHeaders != null) {
        for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
          connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
      }

      if (body != null) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream outputStream = connection.getOutputStream()) {
          outputStream.write(bytes);
        }
      }

      int code = connection.getResponseCode();
      String response =
          read(
              code >= 200 && code < 300
                  ? connection.getInputStream()
                  : connection.getErrorStream());

      if (code < 200 || code >= 300) {
        throw new Exception(formatError(code, response));
      }
      return response;
    } finally {
      connection.disconnect();
    }
  }

  private static String read(InputStream stream) throws Exception {
    if (stream == null) return "";
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line).append('\n');
      }
      return builder.toString();
    }
  }

  private static String formatError(int code, String body) {
    if (body == null || body.isEmpty()) return "HTTP " + code;

    String text =
        body.replaceAll("(?is)<style.*?</style>", " ")
            .replaceAll("(?is)<script.*?</script>", " ")
            .replaceAll("(?is)<[^>]+>", " ")
            .replaceAll("\\s+", " ")
            .trim();

    if (text.length() > 180) {
      text = text.substring(0, 180) + "...";
    }
    return text.isEmpty() ? "HTTP " + code : "HTTP " + code + ": " + text;
  }
}
