package net.craftavia.bedrockmenus.form;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Debug only: prints the form into a Java player's chat instead of sending it to a Bedrock client.
 * Used for the players in debug.fake-bedrock-players; answered with /bedrockmenus answer.
 */
public final class ChatSender implements FormSender {

    private record Pending(int id, IntConsumer onClick, Runnable onClose) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public void simple(final Player player, final SimpleSpec spec) {
        final int id = counter.incrementAndGet();
        final String tag = "§8[§eFORM#" + id + "§8] §r";
        pending.put(player.getUniqueId(), new Pending(id, spec.onClick(), spec.onClose()));
        send(player, tag + spec.title());
        if (!spec.content().isEmpty()) {
            send(player, tag + spec.content().replace("\n", " §8|§r "));
        }
        for (int i = 0; i < spec.buttons().size(); i++) {
            final String image = spec.image(i);
            send(player, tag + "§e" + i + "§8: §r" + spec.buttons().get(i).replace("\n", " §8|§r ")
                    + (image == null ? "" : " §8(" + image + ")"));
        }
        send(player, tag + "§7/bedrockmenus answer " + id + " <number|close>");
    }

    /** @return false when there is no pending form or the id is old. */
    public boolean answer(final Player player, final int id, final String answer) {
        final Pending p = pending.get(player.getUniqueId());
        if (p == null || p.id() != id) {
            return false;
        }
        if (answer.equalsIgnoreCase("close")) {
            pending.remove(player.getUniqueId());
            p.onClose().run();
            return true;
        }
        final int button;
        try {
            button = Integer.parseInt(answer);
        } catch (final NumberFormatException e) {
            return false;
        }
        pending.remove(player.getUniqueId());
        p.onClick().accept(button);
        return true;
    }

    public void forget(final UUID uuid) {
        pending.remove(uuid);
    }

    private static void send(final Player player, final String legacy) {
        final Component c = LegacyComponentSerializer.legacySection().deserialize(legacy);
        player.sendMessage(c);
    }
}
