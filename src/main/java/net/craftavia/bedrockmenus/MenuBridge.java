package net.craftavia.bedrockmenus;

import net.craftavia.bedrockmenus.form.SimpleSpec;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Turns a plugin chest menu opened by a Bedrock player into a SimpleForm. The menu really stays open on the
 * server (its window packets never reach the client, see {@link PacketFilter}); a tap on the form is passed to
 * the plugin that owns the menu as a real InventoryClickEvent, so every plugin's own logic keeps working.
 */
final class MenuBridge implements Listener {

    private static final Set<InventoryType> TYPES = EnumSet.of(InventoryType.CHEST, InventoryType.HOPPER,
            InventoryType.DISPENSER, InventoryType.DROPPER, InventoryType.BARREL, InventoryType.SHULKER_BOX);
    private static final AtomicLong TOKENS = new AtomicLong();
    private static final int TITLE_MAX = 40;
    private static final int NAME_MAX = 38;
    private static final int LINE_MAX = 40;

    /** The converted menu a player has open. Main thread only. */
    private static final class Session {
        final Inventory top;
        final String title;
        long token;
        boolean awaiting;
        List<Integer> slots = List.of();
        List<ItemStack> snaps = List.of();

        Session(final Inventory top, final String title) {
            this.top = top;
            this.title = title;
        }
    }

    private final BedrockMenusPlugin plugin;
    private final Forms forms;
    private final PacketFilter packets;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Session closed with OPEN_NEW; a new menu opened in the same tick may take over its form. */
    private final Map<UUID, Session> replaced = new HashMap<>();

    MenuBridge(final BedrockMenusPlugin plugin, final Forms forms, final PacketFilter packets) {
        this.plugin = plugin;
        this.forms = forms;
        this.packets = packets;
    }

    // ───────────────────────────── events ─────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(final InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !plugin.settings().menuEnabled()) {
            return;
        }
        if (!forms.isBedrock(player)) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        final Inventory inv = event.getInventory();
        final String title = ItemText.legacy(event.getView().title());
        if (!eligible(inv, title)) {
            packets.clearExpect(uuid);
            return;
        }
        packets.expect(uuid, inv.getSize());
        final Session s = new Session(inv, title);
        final Session old = replaced.remove(uuid);
        sessions.put(uuid, s);
        if (old != null && old.awaiting && old.title.equals(title) && old.top.getSize() == inv.getSize()) {
            // The menu reopened itself (page refresh/animation): the form on screen stays valid.
            s.token = old.token;
            s.awaiting = true;
            s.slots = old.slots;
            s.snaps = old.snaps;
            return;
        }
        player.getScheduler().runDelayed(plugin, t -> showMain(player, s, null), null, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        final Session s = sessions.get(uuid);
        if (s == null || s.top != event.getInventory()) {
            return;
        }
        sessions.remove(uuid);
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) {
            replaced.put(uuid, s);
            player.getScheduler().run(plugin, t -> replaced.remove(uuid, s), null);
        }
        // Once the menu is closed, sync the client's inventory with the server.
        player.getScheduler().runDelayed(plugin, t -> {
            if (!sessions.containsKey(uuid) && player.getOpenInventory().getType() == InventoryType.CRAFTING) {
                player.updateInventory();
            }
        }, null, 2L);
    }

    /** A form left on screen by the previous server (proxy switch) cannot be answered here, so close it. */
    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!plugin.settings().closeStaleForms() || !forms.isBedrock(player)) {
            return;
        }
        player.getScheduler().runDelayed(plugin, t -> {
            if (!sessions.containsKey(player.getUniqueId())) {
                forms.close(player);
            }
        }, null, 20L);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        replaced.remove(uuid);
        packets.forget(uuid);
        forms.forget(uuid);
    }

    int openCount() {
        return sessions.size();
    }

    /** onDisable: closes every converted menu and its form. Sessions are cleared first so onClose schedules nothing. */
    void closeAll() {
        final List<UUID> open = new ArrayList<>(sessions.keySet());
        sessions.clear();
        replaced.clear();
        for (final UUID uuid : open) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
                forms.close(player);
            }
        }
    }

    // ───────────────────────────── eligibility ─────────────────────────────

    private boolean eligible(final Inventory inv, final String title) {
        if (!TYPES.contains(inv.getType())) {
            return false;
        }
        final InventoryHolder holder = inv.getHolder(false);
        // Bukkit.createInventory(player, ...): a virtual menu whose holder is the player
        // (the player's own inventory is never of a chest type).
        final boolean playerHeld = holder instanceof HumanEntity;
        if (!playerHeld && (inv.getLocation() != null || holder instanceof BlockState
                || holder instanceof DoubleChest || holder instanceof Entity)) {
            return false; // real block/entity inventories (chest, barrel, donkey) open normally
        }
        final Settings cfg = plugin.settings();
        final String plainTitle = ItemText.plain(title);
        for (final Pattern p : cfg.excludedTitles()) {
            if (p.matcher(plainTitle).find()) {
                return false;
            }
        }
        final Plugin owner = owner(holder);
        if (owner == plugin) {
            return false;
        }
        if (!cfg.onlyPlugins().isEmpty()) {
            return owner != null && cfg.onlyPlugins().contains(owner.getName().toLowerCase(Locale.ROOT));
        }
        return owner == null || !cfg.excludedPlugins().contains(owner.getName().toLowerCase(Locale.ROOT));
    }

    /** The plugin that opened the menu: the holder's class first, otherwise the call stack. */
    private Plugin owner(final InventoryHolder holder) {
        if (holder != null && !(holder instanceof HumanEntity)) {
            final Plugin p = providing(holder.getClass());
            if (p != null) {
                return p;
            }
        }
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .map(MenuBridge::providing)
                .filter(p -> p != null && p != plugin)
                .findFirst().orElse(null));
    }

    private static Plugin providing(final Class<?> type) {
        try {
            return JavaPlugin.getProvidingPlugin(type);
        } catch (final IllegalArgumentException | IllegalStateException | ClassCastException e) {
            return null;
        }
    }

    // ───────────────────────────── forms ─────────────────────────────

    private boolean current(final Player player, final Session s) {
        return player.isOnline() && sessions.get(player.getUniqueId()) == s
                && player.getOpenInventory().getTopInventory() == s.top;
    }

    private Session byToken(final Player player, final long token) {
        final Session s = sessions.get(player.getUniqueId());
        if (s == null || s.token != token || player.getOpenInventory().getTopInventory() != s.top) {
            return null;
        }
        return s;
    }

    private void showMain(final Player player, final Session s, final String noticeKey) {
        if (!current(player, s)) {
            return;
        }
        final Settings cfg = plugin.settings();
        final Locale locale = player.locale();
        final List<String> buttons = new ArrayList<>();
        final List<Integer> slots = new ArrayList<>();
        final List<ItemStack> snaps = new ArrayList<>();
        final List<String> images = new ArrayList<>();
        for (int slot = 0; slot < s.top.getSize(); slot++) {
            final ItemStack item = s.top.getItem(slot);
            if (item == null || item.getType().isAir() || ItemText.isFiller(item)) {
                continue;
            }
            buttons.add(buttonText(item, cfg));
            images.add(cfg.buttonImages() ? ItemIcons.of(item, cfg.headImageUrl()) : null);
            slots.add(slot);
            snaps.add(item.clone());
        }
        buttons.add(Messages.form(locale, "form-close"));
        images.add(null);
        s.slots = slots;
        s.snaps = snaps;
        s.token = TOKENS.incrementAndGet();
        s.awaiting = true;
        final long token = s.token;
        String content = Messages.form(locale, slots.isEmpty() ? "form-empty" : "form-content");
        if (noticeKey != null) {
            final String notice = Messages.form(locale, noticeKey);
            content = notice + (ItemText.plain(content).isBlank() ? "" : "\n" + content);
        }
        forms.simple(player, new SimpleSpec(title(s, locale), content, buttons, images,
                idx -> onMainClick(player, token, idx),
                () -> onFormClosed(player, token)));
    }

    private static String title(final Session s, final Locale locale) {
        final String t = ItemText.plain(s.title).trim();
        return t.isEmpty() ? Messages.form(locale, "form-default-title") : ItemText.cut(s.title, TITLE_MAX);
    }

    private static String buttonText(final ItemStack item, final Settings cfg) {
        String text = ItemText.cut(ItemText.name(item), NAME_MAX);
        if (item.getAmount() > 1) {
            text += " §8x" + item.getAmount();
        }
        final List<String> lore = ItemText.lore(item);
        // A button has room for one lore line: an important line (such as a price) wins over the first one.
        final Pattern important = cfg.importantLine();
        if (important != null) {
            for (final String line : lore) {
                if (important.matcher(ItemText.plain(line)).find()) {
                    return text + "\n§r" + ItemText.cut(line, LINE_MAX);
                }
            }
        }
        for (final String line : lore) {
            if (!ItemText.plain(line).isBlank()) {
                text += "\n§r" + ItemText.cut(line, LINE_MAX);
                break;
            }
        }
        return text;
    }

    private void onMainClick(final Player player, final long token, final int idx) {
        final Session s = byToken(player, token);
        if (s == null) {
            return;
        }
        s.awaiting = false;
        if (idx < 0 || idx >= s.slots.size()) {
            closeMenu(player, s);
            return;
        }
        final int slot = s.slots.get(idx);
        final ItemStack snap = s.snaps.get(idx);
        if (!same(snap, s.top.getItem(slot))) {
            showMain(player, s, "form-changed");
            return;
        }
        final Settings cfg = plugin.settings();
        final List<String> lore = ItemText.lore(snap);
        final String hint = hint(lore);
        if (cfg.detailScreen() && (contains(hint, cfg.rightWords()) || contains(hint, cfg.shiftWords()))) {
            showDetail(player, s, slot, snap, lore, hint);
        } else {
            click(player, s, slot, ClickType.LEFT);
        }
    }

    /** Full lore plus one button per click type the lore mentions. */
    private void showDetail(final Player player, final Session s, final int slot, final ItemStack snap,
                            final List<String> lore, final String hint) {
        final Settings cfg = plugin.settings();
        final Locale locale = player.locale();
        final boolean right = contains(hint, cfg.rightWords());
        final boolean shift = contains(hint, cfg.shiftWords());
        final boolean left = contains(hint, cfg.leftWords());
        final List<ClickType> actions = new ArrayList<>();
        final List<String> buttons = new ArrayList<>();
        actions.add(ClickType.LEFT);
        buttons.add(Messages.form(locale, left || right ? "form-left-click" : "form-select"));
        if (right) {
            actions.add(ClickType.RIGHT);
            buttons.add(Messages.form(locale, "form-right-click"));
        }
        if (shift) {
            actions.add(ClickType.SHIFT_LEFT);
            buttons.add(Messages.form(locale, "form-shift-left-click"));
            if (right) {
                actions.add(ClickType.SHIFT_RIGHT);
                buttons.add(Messages.form(locale, "form-shift-right-click"));
            }
        }
        buttons.add(Messages.form(locale, "form-back"));
        String name = ItemText.name(snap);
        if (snap.getAmount() > 1) {
            name += " §8x" + snap.getAmount();
        }
        final String content = name + "\n\n§r" + String.join("\n§r", lore);
        s.token = TOKENS.incrementAndGet();
        s.awaiting = true;
        final long token = s.token;
        forms.simple(player, new SimpleSpec(title(s, locale), content, buttons, idx -> {
            final Session cur = byToken(player, token);
            if (cur == null) {
                return;
            }
            cur.awaiting = false;
            if (idx < 0 || idx >= actions.size()) {
                showMain(player, cur, null);
                return;
            }
            if (!same(snap, cur.top.getItem(slot))) {
                showMain(player, cur, "form-changed");
                return;
            }
            click(player, cur, slot, actions.get(idx));
        }, () -> onFormClosed(player, token)));
    }

    private static String hint(final List<String> lore) {
        return ItemText.plain(String.join(" ", lore)).toLowerCase(Locale.ROOT);
    }

    private static boolean contains(final String hint, final List<String> words) {
        for (final String w : words) {
            if (hint.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private void onFormClosed(final Player player, final long token) {
        final Session s = byToken(player, token);
        if (s != null) {
            s.awaiting = false;
            closeMenu(player, s);
        }
    }

    private void closeMenu(final Player player, final Session s) {
        if (current(player, s)) {
            player.closeInventory(InventoryCloseEvent.Reason.PLAYER);
        }
    }

    /** Passes the tap to the plugin that owns the menu as a real click. */
    private void click(final Player player, final Session s, final int slot, final ClickType click) {
        if (!current(player, s)) {
            return;
        }
        final InventoryView view = player.getOpenInventory();
        final InventoryAction action = switch (click) {
            case RIGHT -> InventoryAction.PICKUP_HALF;
            case SHIFT_LEFT, SHIFT_RIGHT -> InventoryAction.MOVE_TO_OTHER_INVENTORY;
            default -> InventoryAction.PICKUP_ALL;
        };
        final InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, click, action);
        Bukkit.getPluginManager().callEvent(event);
        if (plugin.settings().itemTaking() && event.getResult() != Event.Result.DENY && current(player, s)) {
            takeItem(player, s.top, slot, click == ClickType.RIGHT);
        }
        player.getScheduler().runDelayed(plugin, t -> {
            if (current(player, s) && !s.awaiting) {
                showMain(player, s, null);
            }
        }, null, 2L);
    }

    /** If the plugin did not cancel the click, give the item to the player like vanilla (takeable menus). */
    private static void takeItem(final Player player, final Inventory top, final int slot, final boolean half) {
        final ItemStack item = top.getItem(slot);
        if (item == null || item.getType().isAir()) {
            return;
        }
        final int amount = half ? (item.getAmount() + 1) / 2 : item.getAmount();
        final ItemStack give = item.clone();
        give.setAmount(amount);
        final Map<Integer, ItemStack> left = player.getInventory().addItem(give);
        final int notGiven = left.values().stream().mapToInt(ItemStack::getAmount).sum();
        final int moved = amount - notGiven;
        if (moved <= 0) {
            return;
        }
        if (moved >= item.getAmount()) {
            top.setItem(slot, null);
        } else {
            item.setAmount(item.getAmount() - moved);
            top.setItem(slot, item);
        }
    }

    private static boolean same(final ItemStack a, final ItemStack b) {
        return b != null && a.isSimilar(b) && a.getAmount() == b.getAmount();
    }
}
