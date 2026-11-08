package net.craftavia.bedrockmenus.form;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A button list form. Callbacks are run on the player's scheduler (see Forms).
 * images: same order as buttons; "http..." = URL image, anything else = Bedrock texture path, null = no image.
 */
public record SimpleSpec(String title, String content, List<String> buttons, List<String> images,
                         IntConsumer onClick, Runnable onClose) {

    public SimpleSpec(final String title, final String content, final List<String> buttons,
                      final IntConsumer onClick, final Runnable onClose) {
        this(title, content, buttons, List.of(), onClick, onClose);
    }

    public String image(final int index) {
        return images != null && index < images.size() ? images.get(index) : null;
    }
}
