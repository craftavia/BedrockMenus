package net.craftavia.bedrockmenus;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.craftavia.bedrockmenus.command.BedrockMenusCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BedrockMenusPlugin extends JavaPlugin {

    private final AtomicBoolean reloading = new AtomicBoolean();
    private volatile Settings settings;
    private Messages messages;
    private Forms forms;
    private MenuBridge bridge;
    private PacketListenerCommon packetHandle;
    private PacketListenerCommon chatHandle;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        messages.saveDefaults();
        settings = Settings.load(getConfig(), getSLF4JLogger());
        try {
            messages.apply(messages.read(), settings.defaultLanguage());
        } catch (final Exception ex) {
            getSLF4JLogger().error("Could not read the message files", ex);
        }

        final boolean floodgate = getServer().getPluginManager().isPluginEnabled("floodgate");
        forms = new Forms(this, floodgate);
        forms.setFakeBedrock(settings.fakeBedrockPlayers());

        final PacketFilter packets = new PacketFilter();
        final ChatButtons chatButtons = new ChatButtons(this, forms);
        packetHandle = PacketEvents.getAPI().getEventManager().registerListener(packets, PacketListenerPriority.LOWEST);
        chatHandle = PacketEvents.getAPI().getEventManager().registerListener(chatButtons, PacketListenerPriority.MONITOR);
        bridge = new MenuBridge(this, forms, packets);
        getServer().getPluginManager().registerEvents(bridge, this);
        getServer().getPluginManager().registerEvents(chatButtons, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register("bedrockmenus", "Manage BedrockMenus", List.of(), new BedrockMenusCommand(this)));

        if (!floodgate) {
            getSLF4JLogger().warn("Floodgate is not installed: Bedrock players cannot be detected, menus stay chests.");
        }
        if (!settings.fakeBedrockPlayers().isEmpty()) {
            getSLF4JLogger().warn("Debug: {} Java player(s) are treated as Bedrock players (debug.fake-bedrock-players).",
                    settings.fakeBedrockPlayers().size());
        }
        getSLF4JLogger().info("Ready. Menu forms: {}, chat buttons: {}, item icons: {}.",
                settings.menuEnabled() ? "on" : "off", settings.chatButtons() ? "on" : "off", ItemIcons.size());
    }

    @Override
    public void onDisable() {
        if (bridge != null) {
            // Bedrock players must not keep an invisible menu open after a reload.
            bridge.closeAll();
        }
        if (packetHandle != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetHandle);
        }
        if (chatHandle != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(chatHandle);
        }
        if (messages != null) {
            messages.unregister();
        }
    }

    Settings settings() {
        return settings;
    }

    Messages messages() {
        return messages;
    }

    public Forms forms() {
        return forms;
    }

    public int openMenus() {
        return bridge == null ? 0 : bridge.openCount();
    }

    public boolean floodgate() {
        return forms != null && forms.floodgate();
    }

    public int itemIcons() {
        return ItemIcons.size();
    }

    /** Reads the files off the main thread, then applies them on it. */
    public void reload(final CommandSender sender) {
        // Two reloads must not run reloadConfig at the same time; the second one is ignored.
        if (!reloading.compareAndSet(false, true)) {
            return;
        }
        getServer().getAsyncScheduler().runNow(this, task -> {
            final Map<Locale, Map<String, String>> newMessages;
            final Settings newSettings;
            try {
                reloadConfig();
                newSettings = Settings.load(getConfig(), getSLF4JLogger());
                newMessages = messages.read();
            } catch (final Exception ex) {
                reloading.set(false);
                getSLF4JLogger().error("Could not read the configuration", ex);
                getServer().getGlobalRegionScheduler().run(this, t -> Messages.send(sender, "reload-failed"));
                return;
            }
            getServer().getGlobalRegionScheduler().run(this, t -> {
                reloading.set(false);
                settings = newSettings;
                forms.setFakeBedrock(newSettings.fakeBedrockPlayers());
                messages.apply(newMessages, newSettings.defaultLanguage());
                Messages.send(sender, "reloaded");
            });
        });
    }
}
