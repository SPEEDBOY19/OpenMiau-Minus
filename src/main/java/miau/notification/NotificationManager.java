package miau.notification;

import java.util.ArrayList;
import java.util.List;

public final class NotificationManager {

  private final List<Notification> notifications = new ArrayList<>();

  public List<Notification> getNotifications() {
    return notifications;
  }

  public NotificationBuilder builder(final NotificationType type) {
    return new NotificationBuilder(this, type);
  }

  private Notification publish(final Notification notification) {
    System.out.println(notification.getTitle() + ": " + notification.getDescription());
    notifications.add(notification);
    return notification;
  }

  public void remove(final Notification notification) {
    notifications.remove(notification);
  }

  public void pop(final String title, final String description, final NotificationType type) {
    if (hasDuplicate(description)) return;
    publish(new Notification(type, title, description, 2000));
  }

  public void pop(final String description, final NotificationType type) {
    pop(type.name(), description, type);
  }

  public void pop(
      final String title, final String description, final int delay, final NotificationType type) {
    if (hasDuplicate(description)) return;
    publish(new Notification(type, title, description, delay));
  }

  public void pop(final String description, final int delay, final NotificationType type) {
    pop(type.name(), description, delay, type);
  }

  private boolean hasDuplicate(final String description) {
    for (final Notification notification : notifications) {
      if (notification.getDescription().equalsIgnoreCase(description)) {
        return true;
      }
    }
    return false;
  }

  public static class NotificationBuilder {
    private final NotificationManager dispatcher;
    private final NotificationType type;
    private String title, description;
    private int duration;

    private NotificationBuilder(final NotificationManager dispatcher, final NotificationType type) {
      this.dispatcher = dispatcher;
      this.type = type;
      this.title = "Notification";
      this.duration = 2000;
    }

    public NotificationBuilder title(final String title) {
      this.title = title;
      return this;
    }

    public NotificationBuilder description(final String description) {
      this.description = description;
      return this;
    }

    public NotificationBuilder duration(final int duration) {
      this.duration = duration;
      return this;
    }

    public Notification buildAndPublish() {
      return dispatcher.publish(new Notification(type, title, description, duration));
    }
  }
}
