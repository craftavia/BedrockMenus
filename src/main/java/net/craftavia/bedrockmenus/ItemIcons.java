package net.craftavia.bedrockmenus;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Form button image: the Bedrock vanilla texture path of a Java item (bedrock-icons.properties) or, for heads,
 * an image URL. Bedrock clients load texture paths from their own resource pack and download URL images
 * themselves; the server never fetches anything. Items without a known texture get no image.
 */
final class ItemIcons {
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{32,}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Map<String, String> PATHS = load();
    private static final Map<Material, String> MOB_HEADS = Map.of(
            Material.CREEPER_HEAD, "MHF_Creeper",
            Material.ZOMBIE_HEAD, "MHF_Zombie",
            Material.SKELETON_SKULL, "MHF_Skeleton",
            Material.WITHER_SKELETON_SKULL, "MHF_WSkeleton");

    private ItemIcons() {
    }

    static int size() {
        return PATHS.size();
    }

    /** @return an "http..." URL, a texture path, or null when there is no reliable image. */
    static String of(final ItemStack stack, final String headUrl) {
        if (stack == null) {
            return null;
        }
        try {
            final Material type = stack.getType();
            if (type == Material.PLAYER_HEAD || MOB_HEADS.containsKey(type)) {
                return head(stack, headUrl);
            }
            return PATHS.get(type.getKey().getKey());
        } catch (final RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static String head(final ItemStack stack, final String headUrl) {
        if (headUrl == null || headUrl.isBlank()) {
            return null;
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            final PlayerProfile profile = skull.getPlayerProfile();
            if (profile != null) {
                final URL skin = profile.getTextures().getSkin();
                if (skin != null) {
                    final String path = skin.getPath();
                    final String texture = path.substring(path.lastIndexOf('/') + 1);
                    if (HEX.matcher(texture).matches()) {
                        return headUrl.replace("{texture}", texture);
                    }
                }
                final String name = profile.getName();
                if (name != null && NAME.matcher(name).matches()) {
                    return headUrl.replace("{texture}", name);
                }
            }
        }
        final String mob = MOB_HEADS.get(stack.getType());
        return headUrl.replace("{texture}", mob != null ? mob : "MHF_Steve");
    }

    private static Map<String, String> load() {
        final Map<String, String> map = new HashMap<>();
        try (InputStream in = ItemIcons.class.getClassLoader().getResourceAsStream("bedrock-icons.properties")) {
            if (in == null) {
                return Map.of();
            }
            final Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (final String key : p.stringPropertyNames()) {
                map.put(key.toLowerCase(Locale.ROOT), p.getProperty(key).trim());
            }
        } catch (final IOException ignored) {
            // Buttons stay without images.
        }
        return Map.copyOf(map);
    }
}
