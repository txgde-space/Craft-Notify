package dev.thou.craftnotify.notification;

import java.net.URI;
import java.util.Map;

public record WebhookChannel(
        String id,
        String method,
        URI url,
        String contentType,
        String bodyTemplate,
        Map<String, String> headers,
        int successStatusMin,
        int successStatusMax,
        CallbackSettings callback
) implements NotificationChannel {
    public WebhookChannel {
        headers = Map.copyOf(headers);
    }

    public boolean acceptsStatus(int statusCode) {
        return statusCode >= successStatusMin && statusCode <= successStatusMax;
    }
}
