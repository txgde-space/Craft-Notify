package dev.thou.craftnotify.preset;

import dev.thou.craftnotify.MoreMath;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.thou.craftnotify.CraftNotify;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuiPresetStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("craft-notify-presets.json");
    private static final String RESOURCE = "/assets/craft_notify/presets.json";
    private static volatile GuiPresetCatalog catalog = GuiPresetCatalog.empty();

    private GuiPresetStore() {
    }

    public static GuiPresetCatalog catalog() {
        return catalog;
    }

    public static synchronized void reload() {
        ensureFile();
        try {
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            catalog = parse(json);
            CraftNotify.LOGGER.info("Loaded Craft Notify GUI presets: {} devices, {} titles, {} messages, {} cooldowns",
                    catalog.devices().size(), catalog.titles().size(),
                    catalog.messages().size(), catalog.cooldowns().size());
        } catch (IOException | JsonSyntaxException | IllegalArgumentException exception) {
            CraftNotify.LOGGER.error("Cannot load GUI presets from {}; using previous/fallback catalog",
                    CONFIG_PATH, exception);
            if (catalog.devices().isEmpty()) {
                catalog = bundledCatalog();
            }
        }
    }

    private static void ensureFile() {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (InputStream in = GuiPresetStore.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    Files.writeString(CONFIG_PATH, GSON.toJson(new FileFormat()), StandardCharsets.UTF_8);
                    return;
                }
                Files.copy(in, CONFIG_PATH);
            }
        } catch (IOException exception) {
            CraftNotify.LOGGER.error("Cannot create GUI preset file at {}", CONFIG_PATH, exception);
        }
    }

    private static GuiPresetCatalog bundledCatalog() {
        try (InputStream in = GuiPresetStore.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return GuiPresetCatalog.empty();
            }
            FileFormat file = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), FileFormat.class);
            return fromFile(file);
        } catch (IOException | JsonSyntaxException exception) {
            CraftNotify.LOGGER.error("Cannot read bundled GUI presets", exception);
            return GuiPresetCatalog.empty();
        }
    }

    private static GuiPresetCatalog parse(String json) {
        FileFormat file = GSON.fromJson(json, FileFormat.class);
        return fromFile(file);
    }

    private static GuiPresetCatalog fromFile(FileFormat file) {
        if (file == null) {
            return GuiPresetCatalog.empty();
        }
        List<GuiPresetCatalog.NamedPreset> devices = new ArrayList<>();
        if (file.devices != null) {
            for (NamedJson entry : file.devices) {
                NamedJson.validated(entry).ifPresent(devices::add);
                if (devices.size() >= GuiPresetCatalog.MAX_DEVICES) {
                    break;
                }
            }
        }
        List<GuiPresetCatalog.TemplatePreset> titles = templates(file.titles);
        List<GuiPresetCatalog.TemplatePreset> messages = templates(file.messages);
        List<Integer> cooldowns = new ArrayList<>();
        if (file.cooldowns != null) {
            for (Integer value : file.cooldowns) {
                if (value == null) {
                    continue;
                }
                int seconds = MoreMath.clamp(value, 5, 86400);
                if (!cooldowns.contains(seconds)) {
                    cooldowns.add(seconds);
                }
                if (cooldowns.size() >= GuiPresetCatalog.MAX_COOLDOWNS) {
                    break;
                }
            }
        }
        if (cooldowns.isEmpty()) {
            cooldowns.add(30);
        }
        return new GuiPresetCatalog(devices, titles, messages, cooldowns);
    }

    private static List<GuiPresetCatalog.TemplatePreset> templates(List<TemplateJson> entries) {
        List<GuiPresetCatalog.TemplatePreset> presets = new ArrayList<>();
        if (entries == null) {
            return presets;
        }
        for (TemplateJson entry : entries) {
            if (entry == null || blank(entry.id) || blank(entry.template)) {
                continue;
            }
            presets.add(new GuiPresetCatalog.TemplatePreset(
                    entry.id.strip(), entry.template, cleanNames(entry.name)));
            if (presets.size() >= GuiPresetCatalog.MAX_TEMPLATES) {
                break;
            }
        }
        return presets;
    }

    private static Map<String, String> cleanNames(Map<String, String> names) {
        Map<String, String> clean = new LinkedHashMap<>();
        if (names == null) {
            return Map.of();
        }
        names.forEach((lang, value) -> {
            if (!blank(lang) && !blank(value) && clean.size() < 8) {
                clean.put(lang.strip(), value.strip());
            }
        });
        return Map.copyOf(clean);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static final class FileFormat {
        List<NamedJson> devices;
        List<TemplateJson> titles;
        List<TemplateJson> messages;
        List<Integer> cooldowns;
    }

    static final class NamedJson {
        String id;
        Map<String, String> name;

        static java.util.Optional<GuiPresetCatalog.NamedPreset> validated(NamedJson entry) {
            if (entry == null || blank(entry.id)) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new GuiPresetCatalog.NamedPreset(entry.id.strip(), cleanNames(entry.name)));
        }
    }

    static final class TemplateJson {
        String id;
        String template;
        Map<String, String> name;
    }
}
