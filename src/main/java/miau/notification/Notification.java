package miau.notification;

import miau.util.time.TimerUtil;

public final class Notification {

  private final TimerUtil timer = new TimerUtil();
  private final NotificationType type;
  private final String title, description;
  private final int duration;

  Notification(
      final NotificationType type,
      final String title,
      final String description,
      final int duration) {
    this.type = type;
    this.title = title;
    this.description = description;
    this.duration = duration;
    this.timer.reset();
  }

  public NotificationType getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public int getDuration() {
    return duration;
  }

  public boolean hasExpired() {
    return timer.hasTimeElapsed(duration, false);
  }

  public long getTime() {
    return timer.getTime();
  }
}
