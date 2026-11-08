package net.craftavia.bedrockmenus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns item names and lore into form text. Bedrock forms only understand legacy section sign colors,
 * so this is the one place where Components become legacy strings.
 */
final class ItemText {

    private ItemText() {
    }

    static String legacy(final Component c) {
        if (c == null) {
            return "";
        }
        // Italic and obfuscated text are hard to read on a form button.
        return LegacyComponentSerializer.legacySection().serialize(c)
                .replace("§o", "").replace("§k", "");
    }

    static String plain(final String legacy) {
        return legacy.replaceAll("§.", "");
    }

    static String name(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasCustomName()) {
            return legacy(meta.customName());
        }
        if (meta != null && meta.hasItemName()) {
            return legacy(meta.itemName());
        }
        return "§f" + pretty(item.getType());
    }

    static List<String> lore(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        final List<String> out = new ArrayList<>();
        if (meta == null || !meta.hasLore()) {
            return out;
        }
        final List<Component> lines = meta.lore();
        if (lines == null) {
            return out;
        }
        for (final Component line : lines) {
            out.add(legacy(line));
        }
        // Drop empty lines at the start and the end.
        while (!out.isEmpty() && plain(out.getFirst()).isBlank()) {
            out.removeFirst();
        }
        while (!out.isEmpty() && plain(out.getLast()).isBlank()) {
            out.removeLast();
        }
        return out;
    }

    /** Filler: an item with an empty name and no lore, or a glass pane without a real name. */
    static boolean isFiller(final ItemStack item) {
        final boolean pane = item.getType().name().endsWith("GLASS_PANE");
        final String n = plain(name(item)).trim();
        final List<String> lore = lore(item);
        if (lore.isEmpty() && n.isEmpty()) {
            return true;
        }
        if (pane && lore.isEmpty()) {
            final ItemMeta meta = item.getItemMeta();
            return meta == null || !meta.hasCustomName() || n.length() <= 1;
        }
        return false;
    }

    static String pretty(final Material m) {
        final String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder sb = new StringBuilder();
        for (final String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(p.substring(0, 1).toUpperCase(Locale.ROOT)).append(p.substring(1));
        }
        return sb.toString();
    }

    /** Shortens to max visible characters while keeping color codes. */
    static String cut(final String legacy, final int max) {
        final String plain = plain(legacy);
        if (plain.length() <= max) {
            return legacy;
        }
        final StringBuilder sb = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < legacy.length() && visible < max - 1; i++) {
            final char ch = legacy.charAt(i);
            sb.append(ch);
            if (ch == '§' && i + 1 < legacy.length()) {
                sb.append(legacy.charAt(++i));
            } else {
                visible++;
            }
        }
        return sb.append('…').toString();
    }
}
