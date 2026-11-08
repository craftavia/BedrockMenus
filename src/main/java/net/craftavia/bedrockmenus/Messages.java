package net.craftavia.bedrockmenus;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Messages are read from messages_&lt;language&gt;.properties files and registered to the GlobalTranslator,
 * so every player sees the file matching their client language.
 * Keys are registered with the NS prefix to avoid clashing with other plugins.
 * Form texts are rendered for the player's locale and converted to the legacy string Bedrock forms use.
 */
public final class Messages {

    public static final String NS = "bedrockmenus.";
    static final List<String> BUNDLED = List.of("en", "tr");
    private static final String FILE_PREFIX = "messages_";
    private static final String FILE_SUFFIX = ".properties";
    private static final Key STORE_KEY = Key.key("bedrockmenus", "messages");

    private final BedrockMenusPlugin plugin;
    private MiniMessageTranslationStore store;
    private volatile Set<String> keys = Set.of();

    public Messages(final BedrockMenusPlugin plugin) {
        this.plugin = plugin;
    }

    /** Copies the bundled language files that do not exist yet. */
    public void saveDefaults() {
        for (final String language : BUNDLED) {
            final String name = FILE_PREFIX + language + FILE_SUFFIX;
            if (!Files.exists(plugin.getDataFolder().toPath().resolve(name))) {
                plugin.saveResource(name, false);
            }
        }
    }

    /**
     * Reads every messages_*.properties file (IO). Keys missing from a file are filled from the bundled
     * file of the same language, then from English, so older files keep working after an update.
     */
    public Map<Locale, Map<String, String>> read() throws IOException {
        final Map<String, String> english = bundled("en");
        final Map<Locale, Map<String, String>> result = new LinkedHashMap<>();

        try (DirectoryStream<Path> files = Files.newDirectoryStream(plugin.getDataFolder().toPath(),
                FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (final Path file : files) {
                final String fileName = file.getFileName().toString();
                final String code = fileName.substring(FILE_PREFIX.length(), fileName.length() - FILE_SUFFIX.length());
                final Locale locale = Translator.parseLocale(code);
                if (locale == null || locale.getLanguage().isEmpty()) {
                    plugin.getSLF4JLogger().warn("Skipping {}: '{}' is not a language code.", fileName, code);
                    continue;
                }
                final Map<String, String> merged = new HashMap<>(english);
                merged.putAll(bundled(code));
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    merged.putAll(toMap(reader));
                }
                result.put(locale, expandPrefix(merged));
            }
        }

        if (result.isEmpty()) {
            result.put(Locale.ENGLISH, expandPrefix(new HashMap<>(english)));
        }
        return result;
    }

    /** Registers the messages to the translator. Call on the main thread. */
    public void apply(final Map<Locale, Map<String, String>> messages, final Locale defaultLocale) {
        unregister();
        final MiniMessageTranslationStore newStore = MiniMessageTranslationStore.create(STORE_KEY);
        newStore.defaultLocale(pickDefault(messages, defaultLocale));
        final Set<String> allKeys = new HashSet<>();
        messages.forEach((locale, map) -> {
            final Map<String, String> namespaced = new HashMap<>(map.size());
            map.forEach((k, v) -> namespaced.put(NS + k, v));
            newStore.registerAll(locale, namespaced);
            allKeys.addAll(map.keySet());
        });
        GlobalTranslator.translator().addSource(newStore);
        this.store = newStore;
        this.keys = Set.copyOf(allKeys);
    }

    public void unregister() {
        if (store != null) {
            GlobalTranslator.translator().removeSource(store);
            store = null;
        }
    }

    /** True if at least one loaded language file defines this key. */
    public boolean has(final String key) {
        return keys.contains(key);
    }

    /** The configured default if it has a file, otherwise English, otherwise any loaded language. */
    static Locale pickDefault(final Map<Locale, Map<String, String>> messages, final Locale wanted) {
        if (wanted != null) {
            for (final Locale locale : messages.keySet()) {
                if (locale.equals(wanted) || locale.getLanguage().equals(wanted.getLanguage())) {
                    return locale;
                }
            }
        }
        for (final Locale locale : messages.keySet()) {
            if (locale.getLanguage().equals("en")) {
                return locale;
            }
        }
        return messages.keySet().iterator().next();
    }

    private Map<String, String> bundled(final String code) throws IOException {
        try (InputStream in = plugin.getResource(FILE_PREFIX + code + FILE_SUFFIX)) {
            if (in == null) {
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return toMap(reader);
            }
        }
    }

    private static Map<String, String> toMap(final Reader reader) throws IOException {
        final Properties properties = new Properties();
        properties.load(reader);
        final Map<String, String> map = new HashMap<>();
        properties.forEach((k, v) -> map.put((String) k, (String) v));
        return map;
    }

    private static Map<String, String> expandPrefix(final Map<String, String> messages) {
        final String prefix = messages.getOrDefault("prefix", "");
        messages.replaceAll((k, v) -> v.replace("<prefix>", prefix));
        return Map.copyOf(messages);
    }

    public static Component msg(final String key, final ComponentLike... args) {
        return Component.translatable(NS + key, args);
    }

    public static void send(final Audience audience, final String key, final ComponentLike... args) {
        audience.sendMessage(msg(key, args));
    }

    /** Renders a message for the given client locale as a legacy (section sign) string for a Bedrock form. */
    public static String form(final Locale locale, final String key, final ComponentLike... args) {
        return ItemText.legacy(GlobalTranslator.render(msg(key, args), locale == null ? Locale.ENGLISH : locale));
    }
}
