package net.craftavia.bedrockmenus.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.craftavia.bedrockmenus.BedrockMenusPlugin;
import net.craftavia.bedrockmenus.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /bedrockmenus reload | status | answer (debug). */
public final class BedrockMenusCommand implements BasicCommand {

    static final String ADMIN = "bedrockmenus.admin";

    private final BedrockMenusPlugin plugin;

    public BedrockMenusCommand(final BedrockMenusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(final CommandSourceStack source, final String[] args) {
        final CommandSender sender = source.getSender();
        if (!sender.hasPermission(ADMIN)) {
            Messages.send(sender, "no-permission");
            return;
        }
        final String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> plugin.reload(sender);
            case "status" -> Messages.send(sender, "status",
                    Component.text(plugin.getPluginMeta().getVersion()),
                    // Arguments that are messages themselves are not translated by the server: render them first.
                    GlobalTranslator.render(Messages.msg(plugin.floodgate() ? "installed" : "not-installed"),
                            sender instanceof Player player ? player.locale() : Locale.ROOT),
                    Component.text(plugin.openMenus()),
                    Component.text(plugin.itemIcons()));
            case "answer" -> answer(sender, args);
            default -> Messages.send(sender, "usage");
        }
    }

    /** Debug: answers a form printed to chat for a player in debug.fake-bedrock-players. */
    private void answer(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player) || args.length < 3) {
            Messages.send(sender, "answer-usage");
            return;
        }
        final int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (final NumberFormatException e) {
            Messages.send(sender, "answer-usage");
            return;
        }
        if (!plugin.forms().answer(player, id, args[2])) {
            Messages.send(sender, "answer-none");
        }
    }

    @Override
    public @NotNull Collection<String> suggest(final CommandSourceStack source, final String[] args) {
        if (args.length <= 1 && source.getSender().hasPermission(ADMIN)) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("reload", "status").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }

    @Override
    public String permission() {
        return ADMIN;
    }
}
