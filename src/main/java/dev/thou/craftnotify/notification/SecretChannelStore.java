package dev.thou.craftnotify.notification;

import dev.thou.craftnotify.CraftNotify;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public final class SecretChannelStore {
    private static final Path CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("craft-notify-channels.properties");
    private static final Path OTHERWORLD_CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("otherworld-calling-channels.properties");
    private static final Path REDSTONE_MESSENGER_CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("redstone-messenger-secrets.properties");
    private static final String WEBHOOK_EXAMPLE = """

            # Generic webhook example
            # webhook.type=webhook
            # webhook.url=https://example.com/minecraft/events
            # webhook.method=POST
            # webhook.content_type=application/json; charset=utf-8
            # webhook.header.Authorization=env:WEBHOOK_AUTHORIZATION
            # webhook.header.X-Server=Minecraft
            # webhook.body={"request_id":"{request_id_json}","title":"{title_json}","content":"{content_json}","created_at":"{created_at_json}","callback_url":"{callback_url_json}"}
            # webhook.success_status_min=200
            # webhook.success_status_max=299
            # webhook.callback.enabled=false
            # webhook.callback.bind=127.0.0.1
            # webhook.callback.port=8765
            # webhook.callback.path=/craft-notify/callback
            # Public URL exposed by your reverse proxy; sent as {callback_url} in the request body.
            # webhook.callback.public_url=https://example.com/craft-notify/callback
            # webhook.callback.token=env:WEBHOOK_CALLBACK_TOKEN
            """;
    private static volatile Map<String, NotificationChannel> channels = Map.of();

    private SecretChannelStore() {
    }

    public static synchronized void reload() {
        ensureExampleFile();
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int separator = line.indexOf('=');
                String key = line.substring(0, separator).strip();
                String value = line.substring(separator + 1).strip();
                int dot = key.indexOf('.');
                if (dot <= 0 || dot == key.length() - 1) {
                    continue;
                }
                sections.computeIfAbsent(key.substring(0, dot), ignored -> new LinkedHashMap<>())
                        .put(key.substring(dot + 1), value);
            }
        } catch (IOException exception) {
            CraftNotify.LOGGER.error("Cannot load notification secrets from {}", CONFIG_PATH, exception);
            channels = Map.of();
            return;
        }

        Map<String, NotificationChannel> loaded = new LinkedHashMap<>();
        sections.forEach((id, values) -> {
            String type = values.getOrDefault("type", "pushplus").strip().toLowerCase();
            switch (type) {
                case "pushplus" -> loadPushPlus(id, values).ifPresent(channel -> loaded.put(id, channel));
                case "notifyx" -> loadNotifyX(id, values).ifPresent(channel -> loaded.put(id, channel));
                case "webhook" -> loadWebhook(id, values).ifPresent(channel -> loaded.put(id, channel));
                default -> CraftNotify.LOGGER.warn(
                        "Ignoring notification profile '{}' with unsupported type '{}'", id, type);
            }
        });
        channels = Collections.unmodifiableMap(loaded);
        CraftNotify.LOGGER.info("Loaded {} Craft Notify channel(s)", loaded.size());
    }

    private static Optional<NotificationChannel> loadPushPlus(String id, Map<String, String> values) {
        String token = resolveSecret(values.getOrDefault("token", ""));
        if (token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new PushPlusChannel(
                id,
                token,
                values.getOrDefault("topic", ""),
                values.getOrDefault("template", "markdown"),
                values.getOrDefault("channel", "wechat")
        ));
    }

    private static Optional<NotificationChannel> loadNotifyX(String id, Map<String, String> values) {
        String rawKey = values.getOrDefault("key", "");
        if (rawKey.isBlank() && !values.getOrDefault("token", "").isBlank()) {
            rawKey = values.get("token");
            CraftNotify.LOGGER.warn(
                    "NotifyX profile '{}' uses 'token'; prefer '{}.key=' instead", id, id);
        }
        String key = resolveSecret(rawKey);
        if (key.isBlank()) {
            CraftNotify.LOGGER.warn("Ignoring NotifyX profile '{}' because key is empty", id);
            return Optional.empty();
        }
        String description = limit(values.getOrDefault("description", "Craft Notify redstone notification"), 500);
        String team = limit(values.getOrDefault("team", ""), 32);
        return Optional.of(new NotifyXChannel(id, key, description, team));
    }

    private static Optional<NotificationChannel> loadWebhook(String id, Map<String, String> values) {
        String rawUrl = resolveSecret(values.getOrDefault("url", ""));
        if (rawUrl.isBlank()) {
            CraftNotify.LOGGER.warn("Ignoring webhook profile '{}' because url is empty", id);
            return Optional.empty();
        }

        URI url;
        try {
            url = new URI(rawUrl);
        } catch (URISyntaxException exception) {
            CraftNotify.LOGGER.warn("Ignoring webhook profile '{}' because url is invalid", id);
            return Optional.empty();
        }
        if (!Set.of("http", "https").contains(url.getScheme()) || url.getHost() == null) {
            CraftNotify.LOGGER.warn("Ignoring webhook profile '{}': only absolute HTTP(S) URLs are supported", id);
            return Optional.empty();
        }

        String method = values.getOrDefault("method", "POST").strip().toUpperCase();
        if (!Set.of("POST", "PUT", "PATCH", "GET", "DELETE").contains(method)) {
            CraftNotify.LOGGER.warn("Ignoring webhook profile '{}' because method '{}' is unsupported", id, method);
            return Optional.empty();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key.startsWith("header.") && key.length() > "header.".length()) {
                headers.put(key.substring("header.".length()), resolveSecret(value));
            }
        });

        int successMin = parseInt(values.get("success_status_min"), 200, 100, 599, id);
        int successMax = parseInt(values.get("success_status_max"), 299, 100, 599, id);
        if (successMin > successMax) {
            CraftNotify.LOGGER.warn("Webhook profile '{}' has an inverted success status range; using 200-299", id);
            successMin = 200;
            successMax = 299;
        }

        CallbackSettings callback = loadCallbackSettings(id, values);
        String body = values.getOrDefault("body",
                "{\"request_id\":\"{request_id_json}\",\"title\":\"{title_json}\","
                        + "\"content\":\"{content_json}\",\"created_at\":\"{created_at_json}\","
                        + "\"callback_url\":\"{callback_url_json}\"}");
        return Optional.of(new WebhookChannel(
                id,
                method,
                url,
                values.getOrDefault("content_type", "application/json; charset=utf-8"),
                body,
                headers,
                successMin,
                successMax,
                callback
        ));
    }

    private static CallbackSettings loadCallbackSettings(String id, Map<String, String> values) {
        boolean enabled = Boolean.parseBoolean(values.getOrDefault("callback.enabled", "false"));
        if (!enabled) {
            return CallbackSettings.disabled();
        }
        int port = parseInt(values.get("callback.port"), 8765, 1, 65535, id);
        String bind = values.getOrDefault("callback.bind", "127.0.0.1").strip();
        String path = values.getOrDefault("callback.path", "/craft-notify/callback").strip();
        if (!path.startsWith("/") || path.contains("?") || path.contains("#")) {
            CraftNotify.LOGGER.warn("Webhook profile '{}' has an invalid callback path; using default", id);
            path = "/craft-notify/callback";
        }
        return new CallbackSettings(
                true,
                bind.isBlank() ? "127.0.0.1" : bind,
                port,
                path,
                values.getOrDefault("callback.public_url", "").strip(),
                resolveSecret(values.getOrDefault("callback.token", ""))
        );
    }

    private static int parseInt(String value, int fallback, int min, int max, String id) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.strip());
            if (parsed >= min && parsed <= max) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        CraftNotify.LOGGER.warn("Profile '{}' has invalid numeric value '{}'; using {}", id, value, fallback);
        return fallback;
    }

    private static String limit(String value, int maxLength) {
        String clean = value.strip();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }

    private static String resolveSecret(String value) {
        if (value.startsWith("env:")) {
            return System.getenv().getOrDefault(value.substring(4), "");
        }
        return value;
    }

    private static void ensureExampleFile() {
        migrateLegacyConfig();
        if (Files.exists(CONFIG_PATH)) {
            appendWebhookExampleIfMissing();
            return;
        }
        String example = """
                # This file is server-only. Never share it with players or commit it.
                # Prefer an environment variable: default.token=env:PUSHPLUS_TOKEN
                default.type=pushplus
                default.token=
                default.topic=
                default.template=markdown
                default.channel=wechat

                # NotifyX example (https://www.notifyx.cn/help/)
                # notifyx.type=notifyx
                # notifyx.key=env:NOTIFYX_KEY
                # notifyx.description=Craft Notify redstone notification
                # notifyx.team=

                """ + WEBHOOK_EXAMPLE;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, example, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CraftNotify.LOGGER.error("Cannot create notification secrets example at {}", CONFIG_PATH, exception);
        }
    }

    private static void migrateLegacyConfig() {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }
        for (Path legacyPath : List.of(OTHERWORLD_CONFIG_PATH, REDSTONE_MESSENGER_CONFIG_PATH)) {
            if (!Files.exists(legacyPath)) {
                continue;
            }
            try {
                Files.copy(legacyPath, CONFIG_PATH);
                CraftNotify.LOGGER.info("Copied legacy channel configuration from {} to {}",
                        legacyPath, CONFIG_PATH);
            } catch (IOException exception) {
                CraftNotify.LOGGER.error("Cannot migrate legacy channel configuration from {}",
                        legacyPath, exception);
            }
            return;
        }
    }

    private static void appendWebhookExampleIfMissing() {
        try {
            String existing = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            if (!existing.contains("# Generic webhook example")) {
                Files.writeString(CONFIG_PATH, WEBHOOK_EXAMPLE, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (IOException exception) {
            CraftNotify.LOGGER.warn("Cannot append webhook example to {}", CONFIG_PATH, exception);
        }
    }

    public static boolean hasChannel(String id) {
        return channels.containsKey(id);
    }

    public static Optional<NotificationChannel> channel(String id) {
        return Optional.ofNullable(channels.get(id));
    }

    public static String channelIds() {
        return channels.isEmpty() ? "(none)" : String.join(", ", channels.keySet());
    }

    public static String channelIdsForGui() {
        return channels.isEmpty() ? "" : String.join(", ", channels.keySet());
    }

    public static List<WebhookChannel> webhookChannels() {
        return channels.values().stream()
                .filter(WebhookChannel.class::isInstance)
                .map(WebhookChannel.class::cast)
                .toList();
    }
}
