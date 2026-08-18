package miau.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import miau.ClientInfo;
import miau.Miau;
import miau.util.client.ChatUtil;

public final class ConfigVersionUtil {
  public static final String VERSION_KEY = "version";

  private ConfigVersionUtil() {}

  public static String getClientVersion() {
    return ClientInfo.VERSION;
  }

  public static void addVersion(JsonObject object) {
    object.addProperty(VERSION_KEY, getClientVersion());
  }

  public static void warnIfOutdated(JsonObject object, String sourceName) {
    String configVersion = readVersion(object);
    if (configVersion == null || configVersion.trim().isEmpty()) {
      return;
    }
    String clientVersion = getClientVersion();
    if (compareVersions(clientVersion, configVersion) < 0) {
      ChatUtil.display(
          "%s&cWarning:&r config &o%s&r was made for &e%s&r, your client is &e%s&r. Some settings may not load correctly.&r",
          Miau.clientName, sourceName, configVersion, clientVersion);
    }
  }

  private static String readVersion(JsonObject object) {
    JsonElement element = object.get(VERSION_KEY);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
  }

  private static int compareVersions(String left, String right) {
    int[] leftParts = parseVersion(left);
    int[] rightParts = parseVersion(right);
    int length = Math.max(leftParts.length, rightParts.length);
    for (int i = 0; i < length; i++) {
      int l = i < leftParts.length ? leftParts[i] : 0;
      int r = i < rightParts.length ? rightParts[i] : 0;
      if (l != r) {
        return Integer.compare(l, r);
      }
    }
    return 0;
  }

  private static int[] parseVersion(String version) {
    if (version == null || version.trim().isEmpty()) {
      return new int[] {0};
    }
    String clean = version.trim().split("[-+]")[0];
    String[] parts = clean.split("\\.");
    int[] numbers = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      try {
        numbers[i] = Integer.parseInt(parts[i].replaceAll("\\D.*", ""));
      } catch (Exception ignored) {
        numbers[i] = 0;
      }
    }
    return numbers;
  }
}
