package miau.config.online;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import miau.management.MiauAPI;

public class OnlineConfigClient {
  private static final Gson GSON = new Gson();

  public List<OnlineConfigEntry> list() throws Exception {
    return parseEntries(MiauAPI.listOnlineConfigs());
  }

  public String load(String settingId) throws Exception {
    return MiauAPI.loadOnlineConfig(settingId);
  }

  public List<OnlineConfigEntry> listUserConfigs() throws Exception {
    return parseEntries(MiauAPI.listUserConfigs());
  }

  public String loadUserConfig(String configId) throws Exception {
    return MiauAPI.loadUserConfig(configId);
  }

  private List<OnlineConfigEntry> parseEntries(String json) {
    JsonElement element = new JsonParser().parse(json);
    if (!element.isJsonArray()) {
      return Collections.emptyList();
    }
    OnlineConfigEntry[] entries = GSON.fromJson(element, OnlineConfigEntry[].class);
    return entries == null ? Collections.emptyList() : Arrays.asList(entries);
  }
}
