package net.craftavia.bedrockmenus;

import net.kyori.adventure.text.event.ClickEvent;

import java.lang.reflect.Method;

/**
 * Reads the command of a RUN_COMMAND click event on every supported server. Adventure 4.x (Minecraft 1.21.x)
 * exposes it as {@code ClickEvent.value()}, Adventure 5.x (26.x) only as {@code payload()}; calling either one
 * directly would break on the other, so the methods are looked up once at runtime.
 */
final class ClickCommands {

    private static final Object RUN_COMMAND = ClickEvent.Action.NAMES.value("run_command");
    /** ClickEvent.payload(), Adventure 4.22+. */
    private static final Method PAYLOAD = method(ClickEvent.class, "payload");
    /** ClickEvent.Payload.Text, Adventure 4.22+. */
    private static final Class<?> TEXT_PAYLOAD = type("net.kyori.adventure.text.event.ClickEvent$Payload$Text");
    private static final Method TEXT_VALUE = method(TEXT_PAYLOAD, "value");
    /** ClickEvent.value(), Adventure 4.x only. */
    private static final Method VALUE = method(ClickEvent.class, "value");

    private ClickCommands() {
    }

    /** @return the command (as sent, with or without a leading slash) or null when this is not a run-command click. */
    static String of(final ClickEvent click) {
        if (click == null || RUN_COMMAND == null || click.action() != RUN_COMMAND) {
            return null;
        }
        try {
            if (PAYLOAD != null && TEXT_VALUE != null) {
                final Object payload = PAYLOAD.invoke(click);
                return TEXT_PAYLOAD.isInstance(payload) ? (String) TEXT_VALUE.invoke(payload) : null;
            }
            return VALUE == null ? null : (String) VALUE.invoke(click);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Class<?> type(final String name) {
        try {
            return Class.forName(name, false, ClickEvent.class.getClassLoader());
        } catch (final ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method method(final Class<?> owner, final String name) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name);
        } catch (final NoSuchMethodException | SecurityException e) {
            return null;
        }
    }
}
