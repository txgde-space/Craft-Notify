package dev.thou.craftnotify.notification;

import dev.thou.craftnotify.CraftNotify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public final class NotificationDispatcher {
    private static final URI PUSH_PLUS_ENDPOINT = URI.create("https://www.pushplus.plus/send");
    private static final String NOTIFY_X_ENDPOINT_PREFIX = "https://www.notifyx.cn/api/v1/send/";
    // NotifyX currently validates this at 30 characters even though its public help page says 100.
    private static final int NOTIFY_X_TITLE_MAX_CODE_POINTS = 30;
    private static volatile ExecutorService executor;
    private static volatile HttpClient httpClient;

    private NotificationDispatcher() {
    }

    public static synchronized void start() {
        if (executor != null) {
            return;
        }
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "craft-notify-http");
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(
                1, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100), threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        httpClient = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public static synchronized void stop() {
        ExecutorService current = executor;
        executor = null;
        httpClient = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    public static CompletableFuture<NotificationResult> dispatch(NotificationJob job) {
        NotificationChannel channel = SecretChannelStore.channel(job.channelId()).orElse(null);
        HttpClient client = httpClient;
        if (channel == null) {
            return CompletableFuture.completedFuture(NotificationResult.failed(0, "Unknown channel"));
        }
        if (client == null) {
            return CompletableFuture.completedFuture(NotificationResult.failed(0, "Dispatcher is stopped"));
        }

        try {
            HttpRequest request = buildRequest(channel, job);
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> parseResponse(channel, response));
        } catch (RuntimeException exception) {
            CraftNotify.LOGGER.warn("Notification request for profile {} could not be submitted: {}",
                    job.channelId(), exception.getClass().getSimpleName());
            String message = exception instanceof IllegalArgumentException
                    ? exception.getMessage()
                    : "Notification queue is full";
            return CompletableFuture.completedFuture(NotificationResult.failed(0, message));
        }
    }

    private static HttpRequest buildRequest(NotificationChannel channel, NotificationJob job) {
        URI endpoint;
        String body;
        if (channel instanceof PushPlusChannel pushPlus) {
            endpoint = PUSH_PLUS_ENDPOINT;
            body = pushPlusJson(pushPlus, job);
        } else if (channel instanceof NotifyXChannel notifyX) {
            endpoint = notifyXEndpoint(notifyX.key());
            body = notifyXJson(notifyX, job);
        } else if (channel instanceof WebhookChannel webhook) {
            return buildWebhookRequest(webhook, job);
        } else {
            throw new IllegalArgumentException("Unsupported notification channel type");
        }
        return HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static NotificationResult parseResponse(NotificationChannel channel, HttpResponse<String> response) {
        if (channel instanceof WebhookChannel webhook) {
            if (webhook.acceptsStatus(response.statusCode())) {
                return NotificationResult.accepted(response.statusCode(),
                        "Webhook accepted the notification (HTTP " + response.statusCode() + ")");
            }
            return NotificationResult.failed(response.statusCode(),
                    "Webhook rejected the notification (HTTP " + response.statusCode() + ")");
        }
        if (channel instanceof NotifyXChannel) {
            return parseNotifyXResponse(response);
        }
        return parsePushPlusResponse(response);
    }

    private static NotificationResult parsePushPlusResponse(HttpResponse<String> response) {
        String body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return NotificationResult.failed(response.statusCode(), "PushPlus HTTP " + response.statusCode());
        }
        if (containsJsonCode(body, 200)) {
            return NotificationResult.accepted("PushPlus accepted the notification");
        }
        return NotificationResult.failed(response.statusCode(), "PushPlus rejected the notification");
    }

    private static boolean containsJsonCode(String body, int code) {
        return body != null && body.matches("(?s).*\\\"code\\\"\\s*:\\s*" + code + "(?:\\s*[,}]).*");
    }

    private static NotificationResult parseNotifyXResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return NotificationResult.failed(response.statusCode(),
                    "NotifyX HTTP " + response.statusCode() + responseDetail(response.body()));
        }
        String body = response.body();
        if (body != null && body.matches("(?s).*\\\"status\\\"\\s*:\\s*\\\"queued\\\".*")) {
            return NotificationResult.accepted("NotifyX queued the notification");
        }
        return NotificationResult.failed(response.statusCode(),
                "NotifyX rejected the notification" + responseDetail(body));
    }

    private static URI notifyXEndpoint(String key) {
        if (!key.matches("[A-Za-z0-9_-]{8,256}")) {
            throw new IllegalArgumentException("Invalid NotifyX key format");
        }
        return URI.create(NOTIFY_X_ENDPOINT_PREFIX + key);
    }

    private static String notifyXJson(NotifyXChannel channel, NotificationJob job) {
        if (job.title().isBlank()) {
            throw new IllegalArgumentException("NotifyX requires a non-empty notification title");
        }
        if (job.content().isBlank()) {
            throw new IllegalArgumentException("NotifyX requires non-empty notification content");
        }
        StringBuilder json = new StringBuilder("{");
        field(json, "title", truncateCodePoints(job.title(), NOTIFY_X_TITLE_MAX_CODE_POINTS));
        field(json, "content", truncateCodePoints(job.content(), 2000));
        if (!channel.description().isBlank()) {
            field(json, "description", channel.description());
        }
        if (!channel.team().isBlank()) {
            field(json, "team", channel.team());
        }
        json.setLength(json.length() - 1);
        return json.append('}').toString();
    }

    private static String responseDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String clean = body.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .strip();
        if (clean.length() > 180) {
            clean = clean.substring(0, 180) + "...";
        }
        return ": " + clean;
    }

    private static String truncateCodePoints(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private static String pushPlusJson(PushPlusChannel channel, NotificationJob job) {
        StringBuilder json = new StringBuilder("{");
        field(json, "token", channel.token());
        field(json, "title", job.title());
        field(json, "content", job.content());
        field(json, "template", channel.template());
        field(json, "channel", channel.channel());
        if (!channel.topic().isBlank()) {
            field(json, "topic", channel.topic());
        }
        json.setLength(json.length() - 1);
        return json.append('}').toString();
    }

    private static HttpRequest buildWebhookRequest(WebhookChannel channel, NotificationJob job) {
        String requestId = UUID.randomUUID().toString();
        CallbackSettings callback = channel.callback();
        String callbackUrl = callback.enabled() ? callbackUrl(callback) : "";
        String body = renderWebhookTemplate(channel.bodyTemplate(), channel, job, requestId, callbackUrl);

        HttpRequest.Builder builder = HttpRequest.newBuilder(channel.url())
                .timeout(Duration.ofSeconds(10));
        if (!channel.contentType().isBlank()) {
            builder.header("Content-Type", channel.contentType());
        }
        channel.headers().forEach((name, value) -> builder.header(
                name, renderWebhookTemplate(value, channel, job, requestId, callbackUrl)));
        HttpRequest.BodyPublisher publisher = body.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        builder.method(channel.method(), publisher);
        if (callback.enabled()) {
            WebhookCallbackServer.expect(requestId, channel.id());
        }
        return builder.build();
    }

    private static String callbackUrl(CallbackSettings callback) {
        if (!callback.publicUrl().isBlank()) {
            return callback.publicUrl();
        }
        String host = callback.bindAddress();
        if (host.equals("0.0.0.0") || host.equals("::")) {
            host = "127.0.0.1";
        }
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + callback.port() + callback.path();
    }

    private static String renderWebhookTemplate(String template, WebhookChannel channel, NotificationJob job,
                                                String requestId, String callbackUrl) {
        return template
                .replace("{request_id_json}", escapeJson(requestId))
                .replace("{title_json}", escapeJson(job.title()))
                .replace("{content_json}", escapeJson(job.content()))
                .replace("{created_at_json}", escapeJson(job.createdAt().toString()))
                .replace("{channel_json}", escapeJson(channel.id()))
                .replace("{callback_url_json}", escapeJson(callbackUrl))
                .replace("{callback_token_json}", escapeJson(channel.callback().token()))
                .replace("{request_id}", requestId)
                .replace("{title}", job.title())
                .replace("{content}", job.content())
                .replace("{created_at}", job.createdAt().toString())
                .replace("{channel}", channel.id())
                .replace("{callback_url}", callbackUrl)
                .replace("{callback_token}", channel.callback().token());
    }

    private static void field(StringBuilder json, String key, String value) {
        json.append('"').append(escapeJson(key)).append("\":\"")
                .append(escapeJson(value)).append("\",");
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
