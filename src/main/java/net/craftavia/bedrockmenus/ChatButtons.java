package net.craftavia.bedrockmenus;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.craftavia.bedrockmenus.form.SimpleSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Bedrock clients cannot click text in chat. When a chat message with clickable run-command parts (such as a
 * teleport or party request) reaches a Bedrock player and the command matches chat-buttons.commands, the same
 * buttons are also shown as a form; a tap runs that command as the player, the same way a Java click does.
 * The packet is only read, never changed. Runs on the network thread.
 */
final class ChatButtons implements PacketListener, Listener {

    private record Button(String label, String command) {
    }

    private record Line(String text, long at) {
    }

    /** A button-only message may follow the line that describes it; use that line if it came this recently. */
    private static final long PREVIOUS_MS = 2000;
    /** The same request sent again within this time does not open a second form. */
    private static final long REPEAT_MS = 5000;

    private final BedrockMenusPlugin plugin;
    private final Forms forms;
    /** The player's last plain chat line: becomes the form text when the buttons come in a separate message. */
    private final Map<UUID, Line> previous = new ConcurrentHashMap<>();
    private final Map<UUID, Line> lastForm = new ConcurrentHashMap<>();

    ChatButtons(final BedrockMenusPlugin plugin, final Forms forms) {
        this.plugin = plugin;
        this.forms = forms;
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            return;
        }
        final Settings s = plugin.settings();
        if (!s.chatButtons() || s.chatCommands().isEmpty()) {
            return;
        }
        final User user = event.getUser();
        final UUID uuid = user == null ? null : user.getUUID();
        if (uuid == null || !forms.isBedrock(uuid, user.getName())) {
            return;
        }
        final List<Button> buttons = new ArrayList<>();
        final String fullText;
        try {
            final WrapperPlayServerSystemChatMessage msg = new WrapperPlayServerSystemChatMessage(event);
            if (msg.isOverlay()) {
                return;
            }
            final Component message = msg.getMessage();
            if (message == null) {
                return;
            }
            collect(message, buttons, s.chatCommands());
            fullText = PlainTextComponentSerializer.plainText().serialize(message);
        } catch (final RuntimeException | LinkageError e) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (buttons.isEmpty()) {
            if (!fullText.isBlank()) {
                previous.put(uuid, new Line(fullText, now));
            }
            return;
        }
        String text = fullText;
        for (final Button b : buttons) {
            text = text.replace(ItemText.plain(b.label()), "");
        }
        text = text.strip();
        if (text.isEmpty()) {
            final Line prev = previous.get(uuid);
            if (prev != null && now - prev.at() <= PREVIOUS_MS) {
                text = prev.text().strip();
            }
        }
        final String key = buttons.getFirst().command();
        final Line last = lastForm.get(uuid);
        if (last != null && last.text().equals(key) && now - last.at() < REPEAT_MS) {
            return;
        }
        lastForm.put(uuid, new Line(key, now));
        final String content = text;
        // Look the player up on the main thread, not on the network thread.
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                open(player, buttons, content);
            }
        });
    }

    /** Collects clickable parts whose command is allowed, in order (children inherit a parent's click). */
    private static void collect(final Component c, final List<Button> out, final List<Pattern> allowed) {
        final String raw = ClickCommands.of(c.clickEvent());
        if (raw != null) {
            final String stripped = raw.strip();
            final String cmd = stripped.startsWith("/") ? stripped : "/" + stripped;
            if (matches(cmd, allowed) && out.stream().noneMatch(b -> b.command().equals(cmd))) {
                final String label = ItemText.legacy(c).replace("§l", "").strip();
                out.add(new Button(ItemText.plain(label).isBlank() ? "§a" + cmd : label, cmd));
            }
            return;
        }
        for (final Component child : c.children()) {
            collect(child, out, allowed);
        }
    }

    private static boolean matches(final String cmd, final List<Pattern> allowed) {
        for (final Pattern p : allowed) {
            if (p.matcher(cmd).matches()) {
                return true;
            }
        }
        return false;
    }

    private void open(final Player player, final List<Button> buttons, final String content) {
        if (!player.isOnline()) {
            return;
        }
        final Locale locale = player.locale();
        final List<String> labels = new ArrayList<>();
        for (final Button b : buttons) {
            labels.add(b.label());
        }
        labels.add(Messages.form(locale, "chat-close"));
        forms.simple(player, new SimpleSpec(title(buttons.getFirst().command(), locale), content, labels, i -> {
            if (i >= 0 && i < buttons.size()) {
                player.chat(buttons.get(i).command());
            }
        }, () -> { }));
    }

    /** Title by the first word of the command ("/essentials:tpaccept Steve" -> tpaccept -> chat-title.teleport). */
    private String title(final String command, final Locale locale) {
        String word = command.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        word = word.substring(word.indexOf(':') + 1);
        final String type = plugin.settings().chatTitles().get(word);
        final String key = type == null ? null : "chat-title." + type;
        return Messages.form(locale, key != null && plugin.messages().has(key) ? key : "chat-title.default");
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        previous.remove(event.getPlayer().getUniqueId());
        lastForm.remove(event.getPlayer().getUniqueId());
    }
}
