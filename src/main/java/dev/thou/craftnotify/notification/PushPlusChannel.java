package dev.thou.craftnotify.notification;

public record PushPlusChannel(String id, String token, String topic, String template, String channel)
        implements NotificationChannel {
}
