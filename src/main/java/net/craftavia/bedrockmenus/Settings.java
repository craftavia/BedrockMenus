package net.craftavia.bedrockmenus;

import net.kyori.adventure.translation.Translator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Immutable snapshot of config.yml; a new one is created on reload. */
record Settings(
        Locale defaultLanguage,
        boolean menuEnabled,
        Set<String> excludedPlugins,
        Set<String> onlyPlugins,
        List<Pattern> excludedTitles,
        boolean detailScreen,
        List<String> rightWords,
        List<String> shiftWords,
        List<String> leftWords,
        boolean itemTaking,
        Pattern importantLine,
        boolean buttonImages,
        String headImageUrl,
        boolean closeStaleForms,
        boolean chatButtons,
        List<Pattern> chatCommands,
        Map<String, String> chatTitles,
        Set<String> fakeBedrockPlayers) {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    static Settings load(final FileConfiguration c, final Logger log) {
        final Locale language = Translator.parseLocale(c.getString("default-language", "en"));
        final Map<String, String> titles = new HashMap<>();
        final ConfigurationSection titleSection = c.getConfigurationSection("chat-buttons.titles");
        if (titleSection != null) {
            for (final String word : titleSection.getKeys(false)) {
                final String type = titleSection.getString(word, "");
                if (type != null && !type.isBlank()) {
                    titles.put(word.toLowerCase(Locale.ROOT), type.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return new Settings(
                language == null ? Locale.ENGLISH : language,
                c.getBoolean("menu.enabled", true),
                lowerSet(c.getStringList("menu.excluded-plugins")),
                lowerSet(c.getStringList("menu.only-plugins")),
                patterns(c.getStringList("menu.excluded-titles"), "menu.excluded-titles", log),
                c.getBoolean("menu.detail-screen", true),
                lowerList(c.getStringList("menu.click-words.right")),
                lowerList(c.getStringList("menu.click-words.shift")),
                lowerList(c.getStringList("menu.click-words.left")),
                c.getBoolean("menu.item-taking", true),
                pattern(c.getString("menu.important-line", ""), "menu.important-line", log),
                c.getBoolean("menu.button-images", true),
                c.getString("menu.head-image-url", ""),
                c.getBoolean("close-stale-forms", true),
                c.getBoolean("chat-buttons.enabled", true),
                patterns(c.getStringList("chat-buttons.commands"), "chat-buttons.commands", log),
                Map.copyOf(titles),
                lowerSet(c.getStringList("debug.fake-bedrock-players")));
    }

    private static Set<String> lowerSet(final List<String> values) {
        final Set<String> out = new HashSet<>();
        for (final String v : values) {
            out.add(v.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(out);
    }

    private static List<String> lowerList(final List<String> values) {
        final List<String> out = new ArrayList<>();
        for (final String v : values) {
            if (!v.isEmpty()) {
                out.add(v.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    private static List<Pattern> patterns(final List<String> values, final String path, final Logger log) {
        final List<Pattern> out = new ArrayList<>();
        for (final String v : values) {
            final Pattern p = pattern(v, path, log);
            if (p != null) {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }

    private static Pattern pattern(final String value, final String path, final Logger log) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(value, FLAGS);
        } catch (final PatternSyntaxException e) {
            log.warn("Skipping invalid regular expression in {}: {}", path, value);
            return null;
        }
    }
}
