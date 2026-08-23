package dev.thou.craftnotify.client.screen;

import dev.thou.craftnotify.preset.GuiPresetCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TerminalPresets {
    record DeviceOption(String id, Map<String, String> names, String extraValue) {
        static DeviceOption extra(String value) {
            return new DeviceOption("extra", Map.of(), value);
        }

        static DeviceOption of(GuiPresetCatalog.NamedPreset preset) {
            return new DeviceOption(preset.id(), preset.names(), "");
        }

        String stored() {
            return isExtra() ? extraValue : localized(names);
        }

        Component label() {
            return Component.literal(stored());
        }

        boolean matches(String value) {
            if (isExtra()) {
                return extraValue.equals(value);
            }
            return names.containsValue(value) || id.equals(value);
        }

        private boolean isExtra() {
            return "extra".equals(id);
        }
    }

    record TemplateOption(String id, String template, Map<String, String> names) {
        static TemplateOption extra(String template) {
            return new TemplateOption("extra", template, Map.of());
        }

        static TemplateOption of(GuiPresetCatalog.TemplatePreset preset) {
            return new TemplateOption(preset.id(), preset.template(), preset.names());
        }

        Component label() {
            if ("extra".equals(id)) {
                return Component.translatable("screen.craft_notify.preset.current");
            }
            String name = localized(names);
            return name.isBlank() ? Component.literal(id) : Component.literal(name);
        }

        boolean matches(String value) {
            return template.equals(value);
        }
    }

    private TerminalPresets() {
    }

    static List<DeviceOption> devicesFor(List<GuiPresetCatalog.NamedPreset> presets, String current) {
        List<DeviceOption> options = new ArrayList<>();
        for (GuiPresetCatalog.NamedPreset preset : presets) {
            options.add(DeviceOption.of(preset));
        }
        boolean known = options.stream().anyMatch(option -> option.matches(current));
        if (!known && current != null && !current.isBlank()) {
            options.add(0, DeviceOption.extra(current));
        }
        if (options.isEmpty()) {
            options.add(DeviceOption.extra(current == null || current.isBlank() ? "Redstone notifier" : current));
        }
        return options;
    }

    static DeviceOption selectedDevice(List<DeviceOption> options, String current) {
        return options.stream()
                .filter(option -> option.matches(current))
                .findFirst()
                .orElse(options.getFirst());
    }

    static List<TemplateOption> titlesFor(List<GuiPresetCatalog.TemplatePreset> presets, String current) {
        return templatesFor(presets, current);
    }

    static List<TemplateOption> messagesFor(List<GuiPresetCatalog.TemplatePreset> presets, String current) {
        return templatesFor(presets, current);
    }

    static TemplateOption selectedTemplate(List<TemplateOption> options, String current) {
        return options.stream()
                .filter(option -> option.matches(current))
                .findFirst()
                .orElse(options.getFirst());
    }

    static List<Integer> cooldownsFor(List<Integer> configured, int current) {
        List<Integer> options = new ArrayList<>(configured.isEmpty() ? List.of(30) : configured);
        if (!options.contains(current)) {
            options.add(current);
            options.sort(Integer::compareTo);
        }
        return options;
    }

    static Component cooldownLabel(int seconds) {
        if (seconds % 3600 == 0 && seconds >= 3600) {
            return Component.translatable("screen.craft_notify.cooldown.hours", seconds / 3600);
        }
        if (seconds % 60 == 0 && seconds >= 60) {
            return Component.translatable("screen.craft_notify.cooldown.minutes", seconds / 60);
        }
        return Component.translatable("screen.craft_notify.cooldown.seconds", seconds);
    }

    static List<String> channelsFor(String available, String current) {
        List<String> ids = new ArrayList<>();
        if (available != null && !available.isBlank()) {
            for (String part : available.split(",")) {
                String id = part.strip();
                if (!id.isEmpty() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        if (current != null && !current.isBlank() && !ids.contains(current)) {
            ids.add(0, current);
        }
        if (ids.isEmpty()) {
            ids.add("");
        }
        return ids;
    }

    static Component channelLabel(String id) {
        if (id == null || id.isBlank()) {
            return Component.translatable("screen.craft_notify.no_channels");
        }
        return Component.literal(id);
    }

    static String preview(String title, String body, String label) {
        String sample = body.isBlank() ? title : title + " — " + body;
        return sample
                .replace("{server}", "Server")
                .replace("{label}", label)
                .replace("{dimension}", "minecraft:overworld")
                .replace("{x}", "100")
                .replace("{y}", "64")
                .replace("{z}", "-20")
                .replace("{power}", "15")
                .replace("{suppressed}", "0")
                .replace("{time}", "now");
    }

    private static List<TemplateOption> templatesFor(List<GuiPresetCatalog.TemplatePreset> presets, String current) {
        List<TemplateOption> options = new ArrayList<>();
        for (GuiPresetCatalog.TemplatePreset preset : presets) {
            options.add(TemplateOption.of(preset));
        }
        boolean known = options.stream().anyMatch(option -> option.matches(current));
        if (!known && current != null && !current.isBlank()) {
            options.add(0, TemplateOption.extra(current));
        }
        if (options.isEmpty()) {
            options.add(TemplateOption.extra(current == null || current.isBlank() ? "{label}" : current));
        }
        return options;
    }

    static String localized(Map<String, String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        String lang = "en_us";
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            lang = minecraft.getLanguageManager().getSelected();
        }
        if (names.containsKey(lang)) {
            return names.get(lang);
        }
        if (lang.startsWith("zh")) {
            for (Map.Entry<String, String> entry : names.entrySet()) {
                if (entry.getKey().startsWith("zh")) {
                    return entry.getValue();
                }
            }
        }
        if (names.containsKey("en_us")) {
            return names.get("en_us");
        }
        return names.values().iterator().next();
    }
}
