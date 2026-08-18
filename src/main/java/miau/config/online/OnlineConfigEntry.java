package miau.config.online;

public class OnlineConfigEntry {
  public String setting_id;
  public String config_id;
  public String name;
  public String setting_type;
  public String description;
  public String date;
  public String contributors;
  public String status_type;
  public String status_date;
  public String version;
  public int load_count;

  public String getId() {
    if (config_id != null && !config_id.trim().isEmpty()) {
      return config_id;
    }
    return setting_id == null ? "" : setting_id;
  }

  public String getName() {
    return name == null || name.trim().isEmpty() ? getId() : name;
  }

  public String getAuthor() {
    return contributors == null || contributors.trim().isEmpty() ? "unknown" : contributors;
  }

  public int getLoadCount() {
    return Math.max(0, load_count);
  }

  public String getVersion() {
    return version == null || version.trim().isEmpty() ? "" : version;
  }
}
