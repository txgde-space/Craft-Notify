package dev.thou.craftnotify.preset;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuiPresetCatalog {
    public static final int MAX_DEVICES = 64;
    public static final int MAX_TEMPLATES = 48;
    public static final int MAX_COOLDOWNS = 32;

    private final List<NamedPreset> devices;
    private final List<TemplatePreset> titles;
    private final List<TemplatePreset> messages;
    private final List<Integer> cooldowns;

    public GuiPresetCatalog(List<NamedPreset> devices, List<TemplatePreset> titles,
                            List<TemplatePreset> messages, List<Integer> cooldowns) {
        this.devices = List.copyOf(devices);
        this.titles = List.copyOf(titles);
        this.messages = List.copyOf(messages);
        this.cooldowns = List.copyOf(cooldowns);
    }

    public static GuiPresetCatalog empty() {
        return new GuiPresetCatalog(List.of(), List.of(), List.of(), List.of(30));
    }

    public List<NamedPreset> devices() {
        return devices;
    }

    public List<TemplatePreset> titles() {
        return titles;
    }

    public List<TemplatePreset> messages() {
        return messages;
    }

    public List<Integer> cooldowns() {
        return cooldowns;
    }

    public void write(FriendlyByteBuf buf) {
        writeNamed(buf, devices, MAX_DEVICES);
        writeTemplates(buf, titles, MAX_TEMPLATES);
        writeTemplates(buf, messages, MAX_TEMPLATES);
        int count = Math.min(cooldowns.size(), MAX_COOLDOWNS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(cooldowns.get(i));
        }
    }

    public static GuiPresetCatalog read(FriendlyByteBuf buf) {
        return new GuiPresetCatalog(
                readNamed(buf, MAX_DEVICES),
                readTemplates(buf, MAX_TEMPLATES),
                readTemplates(buf, MAX_TEMPLATES),
                readCooldowns(buf)
        );
    }

    private static void writeNamed(FriendlyByteBuf buf, List<NamedPreset> presets, int max) {
        int count = Math.min(presets.size(), max);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            presets.get(i).write(buf);
        }
    }

    private static void writeTemplates(FriendlyByteBuf buf, List<TemplatePreset> presets, int max) {
        int count = Math.min(presets.size(), max);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            presets.get(i).write(buf);
        }
    }

    private static List<NamedPreset> readNamed(FriendlyByteBuf buf, int max) {
        int count = Math.clamp(buf.readVarInt(), 0, max);
        List<NamedPreset> presets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            presets.add(NamedPreset.read(buf));
        }
        return presets;
    }

    private static List<TemplatePreset> readTemplates(FriendlyByteBuf buf, int max) {
        int count = Math.clamp(buf.readVarInt(), 0, max);
        List<TemplatePreset> presets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            presets.add(TemplatePreset.read(buf));
        }
        return presets;
    }

    private static List<Integer> readCooldowns(FriendlyByteBuf buf) {
        int count = Math.clamp(buf.readVarInt(), 0, MAX_COOLDOWNS);
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(Math.clamp(buf.readVarInt(), 5, 86400));
        }
        if (values.isEmpty()) {
            values.add(30);
        }
        return values;
    }

    public record NamedPreset(String id, Map<String, String> names) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            writeNames(buf, names);
        }

        public static NamedPreset read(FriendlyByteBuf buf) {
            return new NamedPreset(buf.readUtf(64), readNames(buf));
        }
    }

    public record TemplatePreset(String id, String template, Map<String, String> names) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeUtf(template, 1024);
            writeNames(buf, names);
        }

        public static TemplatePreset read(FriendlyByteBuf buf) {
            return new TemplatePreset(buf.readUtf(64), buf.readUtf(1024), readNames(buf));
        }
    }

    static void writeNames(FriendlyByteBuf buf, Map<String, String> names) {
        int count = Math.min(names.size(), 8);
        buf.writeVarInt(count);
        int written = 0;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            if (written++ >= count) {
                break;
            }
            buf.writeUtf(entry.getKey(), 16);
            buf.writeUtf(entry.getValue(), 64);
        }
    }

    static Map<String, String> readNames(FriendlyByteBuf buf) {
        int count = Math.clamp(buf.readVarInt(), 0, 8);
        Map<String, String> names = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            names.put(buf.readUtf(16), buf.readUtf(64));
        }
        return Map.copyOf(names);
    }
}
