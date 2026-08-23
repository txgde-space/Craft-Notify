package dev.thou.craftnotify.notification;

public record NotificationResult(boolean accepted, int httpStatus, String message) {
    public static NotificationResult accepted(String message) {
        return new NotificationResult(true, 200, message);
    }

    public static NotificationResult accepted(int httpStatus, String message) {
        return new NotificationResult(true, httpStatus, message);
    }

    public static NotificationResult failed(int httpStatus, String message) {
        return new NotificationResult(false, httpStatus, message);
    }
}
