package dev.thou.craftnotify.notification;

public sealed interface NotificationChannel permits PushPlusChannel, NotifyXChannel, WebhookChannel {
    String id();
}
