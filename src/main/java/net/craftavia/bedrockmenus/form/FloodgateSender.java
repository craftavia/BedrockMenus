package net.craftavia.bedrockmenus.form;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * The only class that touches Floodgate. It is never loaded when Floodgate is not installed,
 * so the plugin can start (idle) without it.
 */
public final class FloodgateSender implements FormSender {

    public static boolean isBedrock(final UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    public static void close(final UUID uuid) {
        FloodgateApi.getInstance().closeForm(uuid);
    }

    @Override
    public void simple(final Player player, final SimpleSpec spec) {
        final SimpleForm.Builder form = SimpleForm.builder().title(spec.title()).content(spec.content());
        for (int i = 0; i < spec.buttons().size(); i++) {
            final String text = spec.buttons().get(i);
            final String image = spec.image(i);
            if (image == null || image.isBlank()) {
                form.button(text);
            } else if (image.startsWith("http")) {
                form.button(text, FormImage.Type.URL, image);
            } else {
                form.button(text, FormImage.Type.PATH, image);
            }
        }
        form.validResultHandler(res -> spec.onClick().accept(res.clickedButtonId()));
        form.closedOrInvalidResultHandler(spec.onClose());
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
}
