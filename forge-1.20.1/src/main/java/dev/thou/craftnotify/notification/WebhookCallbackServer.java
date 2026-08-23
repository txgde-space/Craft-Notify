package dev.thou.craftnotify.notification;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.thou.craftnotify.CraftNotify;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebhookCallbackServer {
    private static final int MAX_CALLBACK_BODY_BYTES = 16 * 1024;
    private static final Duration PENDING_LIFETIME = Duration.ofHours(24);
    private static final Pattern REQUEST_ID_JSON = Pattern.compile(
            "\\\"request_id\\\"\\s*:\\s*\\\"([A-Za-z0-9-]{1,80})\\\"");
    private static final Map<ListenerKey, RunningListener> listeners = new LinkedHashMap<>();
    private static final Map<String, PendingCallback> pending = new ConcurrentHashMap<>();

    private WebhookCallbackServer() {
    }

    public static synchronized void reload() {
        stop();
        Map<ListenerKey, Map<String, CallbackRoute>> routes = new LinkedHashMap<>();
        for (WebhookChannel channel : SecretChannelStore.webhookChannels()) {
            CallbackSettings callback = channel.callback();
            if (!callback.enabled()) {
                continue;
            }
            ListenerKey key = new ListenerKey(callback.bindAddress(), callback.port());
            CallbackRoute existing = routes.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(callback.path(), new CallbackRoute(channel.id(), callback.token()));
            if (existing != null) {
                CraftNotify.LOGGER.warn(
                        "Webhook callback route {}:{}{} is already used by profile '{}'; ignoring profile '{}' route",
                        key.bindAddress(), key.port(), callback.path(), existing.channelId(), channel.id());
            }
        }

        routes.forEach(WebhookCallbackServer::startListener);
        cleanupPending();
    }

    private static void startListener(ListenerKey key, Map<String, CallbackRoute> routes) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(key.bindAddress(), key.port()), 32);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "craft-notify-callback-" + key.port());
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            routes.forEach((path, route) -> server.createContext(path, exchange -> handle(exchange, path, route)));
            server.start();
            listeners.put(key, new RunningListener(server, executor));
            CraftNotify.LOGGER.info("Webhook callback listener started on {}:{} for {} route(s)",
                    key.bindAddress(), key.port(), routes.size());
        } catch (IOException | RuntimeException exception) {
            CraftNotify.LOGGER.error("Cannot start webhook callback listener on {}:{}",
                    key.bindAddress(), key.port(), exception);
        }
    }

    public static synchronized void stop() {
        listeners.values().forEach(listener -> {
            listener.server().stop(0);
            listener.executor().shutdownNow();
        });
        listeners.clear();
    }

    public static void expect(String requestId, String channelId) {
        pending.put(requestId, new PendingCallback(channelId, Instant.now().plus(PENDING_LIFETIME)));
        cleanupPending();
    }

    private static void handle(HttpExchange exchange, String configuredPath, CallbackRoute route) throws IOException {
        try {
            if (!exchange.getRequestURI().getPath().equals(configuredPath)) {
                respond(exchange, 404, "Not found");
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.getResponseHeaders().set("Allow", "POST");
                respond(exchange, 405, "POST required");
                return;
            }
            if (!authorized(exchange, route.token())) {
                respond(exchange, 401, "Unauthorized");
                return;
            }

            byte[] bodyBytes = exchange.getRequestBody().readNBytes(MAX_CALLBACK_BODY_BYTES + 1);
            if (bodyBytes.length > MAX_CALLBACK_BODY_BYTES) {
                respond(exchange, 413, "Callback body too large");
                return;
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            Matcher matcher = REQUEST_ID_JSON.matcher(body);
            if (!matcher.find()) {
                respond(exchange, 400, "Missing request_id");
                return;
            }

            String requestId = matcher.group(1);
            PendingCallback expected = pending.remove(requestId);
            if (expected == null || !expected.channelId().equals(route.channelId())) {
                respond(exchange, 404, "Unknown request_id");
                return;
            }
            CraftNotify.LOGGER.info("Webhook callback received for profile '{}' request {}: {}",
                    route.channelId(), requestId, logSafe(body));
            respond(exchange, 202, "Accepted");
        } catch (RuntimeException exception) {
            CraftNotify.LOGGER.warn("Webhook callback for profile '{}' failed: {}",
                    route.channelId(), exception.getClass().getSimpleName());
            if (exchange.getResponseCode() == -1) {
                respond(exchange, 500, "Callback processing failed");
            }
        }
    }

    private static boolean authorized(HttpExchange exchange, String token) {
        if (token.isBlank()) {
            return true;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String directToken = exchange.getRequestHeaders().getFirst("X-Redstone-Messenger-Token");
        return constantTimeEquals("Bearer " + token, authorization) || constantTimeEquals(token, directToken);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        int difference = expectedBytes.length ^ actualBytes.length;
        int length = Math.max(expectedBytes.length, actualBytes.length);
        for (int i = 0; i < length; i++) {
            byte left = i < expectedBytes.length ? expectedBytes[i] : 0;
            byte right = i < actualBytes.length ? actualBytes[i] : 0;
            difference |= left ^ right;
        }
        return difference == 0;
    }

    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
    }

    private static String logSafe(String body) {
        String oneLine = body.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 512 ? oneLine : oneLine.substring(0, 512) + "...";
    }

    private static void cleanupPending() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record ListenerKey(String bindAddress, int port) {
    }

    private record CallbackRoute(String channelId, String token) {
    }

    private record RunningListener(HttpServer server, ExecutorService executor) {
    }

    private record PendingCallback(String channelId, Instant expiresAt) {
    }
}
