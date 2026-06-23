package me.erotoro.treechopper.compat;

import org.bukkit.entity.FallingBlock;

import java.lang.reflect.Method;

/**
 * Version-safe access to {@link FallingBlock#setCancelDrop(boolean)}.
 *
 * <p>{@code setCancelDrop} only exists on newer API versions (~1.19.3+). On older versions the
 * method is absent, so invoking it directly would throw {@link NoSuchMethodError}. When the method
 * is unavailable the plugin instead relies on a fallback {@code EntityChangeBlockEvent} listener to
 * stop our cosmetic falling logs from reforming into blocks on landing — see
 * {@link #isSupported()}.
 */
public final class FallingBlockCompat {

    private static final Method SET_CANCEL_DROP = resolveSetCancelDrop();

    private FallingBlockCompat() {
    }

    /**
     * Whether {@link FallingBlock#setCancelDrop(boolean)} is available on this server. When
     * {@code false}, callers must guard falling-block landing themselves (e.g. by cancelling
     * {@code EntityChangeBlockEvent}).
     */
    public static boolean isSupported() {
        return SET_CANCEL_DROP != null;
    }

    /**
     * Calls {@code setCancelDrop} when available; a no-op otherwise.
     */
    public static void setCancelDrop(FallingBlock fallingBlock, boolean cancel) {
        if (SET_CANCEL_DROP == null || fallingBlock == null) {
            return;
        }
        try {
            SET_CANCEL_DROP.invoke(fallingBlock, cancel);
        } catch (ReflectiveOperationException ignored) {
            // Treated as unsupported; the fallback listener handles landing on these servers.
        }
    }

    private static Method resolveSetCancelDrop() {
        try {
            return FallingBlock.class.getMethod("setCancelDrop", boolean.class);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
