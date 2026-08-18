package miau.notification;

public enum NotificationType {
  SUCCESS("\ue5ca", 0xFF2ECC71),
  ERROR("\ue5cd", 0xFFE74C3C),
  WARN("\ue002", 0xFFFDD235),
  INFO("\ue88f", 0xFF7097CF);

  private final String icon;
  private final int iconColor;

  NotificationType(final String icon, final int iconColor) {
    this.icon = icon;
    this.iconColor = iconColor;
  }

  public String getIcon() {
    return icon;
  }

  public int getIconColor() {
    return iconColor;
  }
}
