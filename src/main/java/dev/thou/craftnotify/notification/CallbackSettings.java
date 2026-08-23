package dev.thou.craftnotify.notification;

public record CallbackSettings(
        boolean enabled,
        String bindAddress,
        int port,
        String path,
        String publicUrl,
        String token
) {
    public static CallbackSettings disabled() {
        return new CallbackSettings(false, "127.0.0.1", 0, "/craft-notify/callback", "", "");
    }
}
