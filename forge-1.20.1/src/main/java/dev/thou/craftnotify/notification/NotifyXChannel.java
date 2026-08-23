package dev.thou.craftnotify.notification;

public record NotifyXChannel(String id, String key, String description, String team)
        implements NotificationChannel {
}
