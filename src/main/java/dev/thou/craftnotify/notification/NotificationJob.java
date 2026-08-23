package dev.thou.craftnotify.notification;

import net.minecraft.core.BlockPos;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record NotificationJob(String channelId, String title, String content, Instant createdAt) {
    public static NotificationJob from(String channelId, String titleTemplate, String contentTemplate,
                                       String label, String server, String dimension, BlockPos pos,
                                       int power, int suppressed) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("server", server);
        variables.put("label", label);
        variables.put("dimension", dimension);
        variables.put("x", Integer.toString(pos.getX()));
        variables.put("y", Integer.toString(pos.getY()));
        variables.put("z", Integer.toString(pos.getZ()));
        variables.put("power", Integer.toString(power));
        variables.put("suppressed", Integer.toString(suppressed));
        variables.put("time", Instant.now().toString());
        return new NotificationJob(
                channelId,
                render(titleTemplate, variables),
                render(contentTemplate, variables),
                Instant.now()
        );
    }

    private static String render(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
