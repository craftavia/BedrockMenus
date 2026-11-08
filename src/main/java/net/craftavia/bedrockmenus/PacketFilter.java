package net.craftavia.bedrockmenus;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Never sends the chest window of a converted menu to the Bedrock client: the server thinks the menu is open
 * (the owning plugin keeps working as usual) while the client only sees the form. Updates to the player
 * inventory part of that window are rewritten to the player inventory (window 0) so item changes are not lost.
 * Runs on the network thread.
 */
final class PacketFilter implements PacketListener {

    private record Expect(int topSize, long until) {
    }

    private record Win(int id, int topSize) {
    }

    private static final int PLAYER_SLOTS = 36;
    private static final long EXPECT_MS = 2000;

    private final Map<UUID, Expect> expect = new ConcurrentHashMap<>();
    private final Map<UUID, Win> bridged = new ConcurrentHashMap<>();

    /** Main thread: the next window this player opens is shown as a form. */
    void expect(final UUID uuid, final int topSize) {
        expect.put(uuid, new Expect(topSize, System.currentTimeMillis() + EXPECT_MS));
    }

    /** Main thread: the next window this player opens is shown normally. */
    void clearExpect(final UUID uuid) {
        expect.remove(uuid);
    }

    void forget(final UUID uuid) {
        expect.remove(uuid);
        bridged.remove(uuid);
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        final PacketTypeCommon type = event.getPacketType();
        if (type != PacketType.Play.Server.OPEN_WINDOW && type != PacketType.Play.Server.WINDOW_ITEMS
                && type != PacketType.Play.Server.SET_SLOT && type != PacketType.Play.Server.WINDOW_PROPERTY
                && type != PacketType.Play.Server.CLOSE_WINDOW) {
            return;
        }
        final User user = event.getUser();
        final UUID uuid = user == null ? null : user.getUUID();
        if (uuid == null) {
            return;
        }
        if (type == PacketType.Play.Server.OPEN_WINDOW) {
            final Expect ex = expect.remove(uuid);
            final WrapperPlayServerOpenWindow open = new WrapperPlayServerOpenWindow(event);
            if (ex != null && ex.until() >= System.currentTimeMillis()) {
                bridged.put(uuid, new Win(open.getContainerId(), ex.topSize()));
                event.setCancelled(true);
            } else {
                bridged.remove(uuid);
            }
            return;
        }
        final Win win = bridged.get(uuid);
        if (win == null) {
            return;
        }
        if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            final WrapperPlayServerWindowItems items = new WrapperPlayServerWindowItems(event);
            if (items.getWindowId() != win.id()) {
                return;
            }
            event.setCancelled(true);
            final List<ItemStack> list = items.getItems();
            for (int rel = 0; rel < PLAYER_SLOTS; rel++) {
                final int idx = win.topSize() + rel;
                if (idx < list.size()) {
                    // Window 0 slots 9-44 are the main inventory and the hotbar, in the same order.
                    user.sendPacketSilently(new WrapperPlayServerSetSlot(0, 0, rel + 9, list.get(idx)));
                }
            }
        } else if (type == PacketType.Play.Server.SET_SLOT) {
            final WrapperPlayServerSetSlot slot = new WrapperPlayServerSetSlot(event);
            if (slot.getWindowId() != win.id()) {
                return;
            }
            final int rel = slot.getSlot() - win.topSize();
            if (rel < 0 || rel >= PLAYER_SLOTS) {
                event.setCancelled(true);
                return;
            }
            slot.setWindowId(0);
            slot.setStateId(0);
            slot.setSlot(rel + 9);
            event.markForReEncode(true);
        } else if (type == PacketType.Play.Server.WINDOW_PROPERTY) {
            if (new WrapperPlayServerWindowProperty(event).getContainerId() == win.id()) {
                event.setCancelled(true);
            }
        } else {
            if (new WrapperPlayServerCloseWindow(event).getWindowId() == win.id()) {
                event.setCancelled(true);
                bridged.remove(uuid);
            }
        }
    }
}
