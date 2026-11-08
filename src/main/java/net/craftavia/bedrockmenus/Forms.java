package net.craftavia.bedrockmenus;

import net.craftavia.bedrockmenus.form.ChatSender;
import net.craftavia.bedrockmenus.form.FloodgateSender;
import net.craftavia.bedrockmenus.form.FormSender;
import net.craftavia.bedrockmenus.form.SimpleSpec;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Bedrock detection and form delivery. Answer callbacks are moved to the player's scheduler (main thread);
 * Floodgate answers forms from a network thread.
 */
public final class Forms {

    private final BedrockMenusPlugin plugin;
    /** Null when Floodgate is not installed; FloodgateSender is then never loaded. */
    private final FormSender floodgate;
    private final ChatSender chat = new ChatSender();
    private volatile Set<String> fakeBedrock = Set.of();

    Forms(final BedrockMenusPlugin plugin, final boolean floodgateEnabled) {
        this.plugin = plugin;
        this.floodgate = floodgateEnabled ? new FloodgateSender() : null;
    }

    boolean floodgate() {
        return floodgate != null;
    }

    /** Lower-case names of Java players that are treated as Bedrock players (debug). */
    void setFakeBedrock(final Set<String> names) {
        this.fakeBedrock = names;
    }

    /** Debug: answers a form that was printed to chat. @return false when there is no such pending form. */
    public boolean answer(final Player player, final int id, final String answer) {
        return chat.answer(player, id, answer);
    }

    private boolean isFake(final String name) {
        return name != null && fakeBedrock.contains(name.toLowerCase(Locale.ROOT));
    }

    public boolean isBedrock(final Player p) {
        return isBedrock(p.getUniqueId(), p.getName());
    }

    /** Safe to call from a network thread. */
    public boolean isBedrock(final UUID uuid, final String name) {
        if (isFake(name)) {
            return true;
        }
        return floodgate != null && FloodgateSender.isBedrock(uuid);
    }

    private FormSender sender(final Player p) {
        return isFake(p.getName()) ? chat : floodgate;
    }

    public void simple(final Player p, final SimpleSpec spec) {
        final FormSender s = sender(p);
        if (s == null) {
            return;
        }
        s.simple(p, new SimpleSpec(spec.title(), spec.content(), spec.buttons(), spec.images(),
                i -> sync(p, () -> spec.onClick().accept(i)),
                () -> sync(p, spec.onClose())));
    }

    /** Closes the form on the player's screen; does nothing when there is none. */
    public void close(final Player p) {
        if (floodgate != null && !isFake(p.getName())) {
            FloodgateSender.close(p.getUniqueId());
        }
    }

    void forget(final UUID uuid) {
        chat.forget(uuid);
    }

    private void sync(final Player p, final Runnable r) {
        p.getScheduler().run(plugin, t -> {
            if (p.isOnline()) {
                r.run();
            }
        }, null);
    }
}
