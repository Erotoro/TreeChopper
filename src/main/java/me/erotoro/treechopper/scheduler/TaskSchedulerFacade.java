package me.erotoro.treechopper.scheduler;

import me.erotoro.treechopper.model.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TaskSchedulerFacade {

    private static final String FOLIA_RUNTIME_MARKER_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    private final JavaPlugin plugin;
    private final boolean foliaServer;
    private final Set<ScheduledHandle> scheduledHandles = ConcurrentHashMap.newKeySet();
    private volatile boolean regionSchedulerAvailable;
    private volatile boolean foliaFallbackBlockedLogged;

    public TaskSchedulerFacade(JavaPlugin plugin) {
        this.plugin = plugin;
        Class<?> serverClass = plugin.getServer().getClass();
        String serverVersion = plugin.getServer().getVersion();
        this.foliaServer = isFoliaServer(serverClass, serverVersion);
        this.regionSchedulerAvailable = shouldUseRegionScheduler(serverClass, serverVersion);
    }

    public boolean isRegionSchedulerAvailable() {
        return regionSchedulerAvailable;
    }

    public void cancelAll() {
        for (ScheduledHandle handle : new ArrayList<>(scheduledHandles)) {
            handle.cancel();
        }
        scheduledHandles.clear();
    }

    public void scheduleBlockBatches(Collection<Block> blocks, long delayTicks, int maxBlocksPerTask, Consumer<List<Block>> action) {
        for (List<Block> regionBlocks : groupBlocksByChunk(blocks).values()) {
            for (int start = 0, batchIndex = 0; start < regionBlocks.size(); start += maxBlocksPerTask, batchIndex++) {
                int end = Math.min(start + maxBlocksPerTask, regionBlocks.size());
                List<Block> batch = new ArrayList<>(regionBlocks.subList(start, end));
                Location anchor = batch.get(0).getLocation();
                long scheduledDelay = delayTicks + batchIndex;
                scheduleDelayed(anchor, scheduledDelay, () -> action.accept(batch));
            }
        }
    }

    public void scheduleDelayed(Location location, long delayTicks, Runnable action) {
        ScheduledHandle[] holder = new ScheduledHandle[1];
        Runnable wrappedAction = () -> {
            try {
                action.run();
            } finally {
                if (holder[0] != null) {
                    holder[0].cancel();
                }
            }
        };

        if (regionSchedulerAvailable) {
            try {
                Object regionScheduler = plugin.getServer().getClass().getMethod("getRegionScheduler").invoke(plugin.getServer());
                long effectiveDelayTicks = Math.max(1L, delayTicks);
                ReflectiveHandle handle = scheduleWithRegionScheduler(regionScheduler, location, effectiveDelayTicks, wrappedAction);
                holder[0] = handle;
                scheduledHandles.add(handle);
                return;
            } catch (ReflectiveOperationException | IllegalArgumentException exception) {
                if (foliaServer) {
                    regionSchedulerAvailable = false;
                    logBlockedFoliaFallback(formatFoliaSchedulerFailureLog(exception));
                    return;
                }
                plugin.getLogger().warning(formatRegionSchedulerFailureLog(exception));
                regionSchedulerAvailable = false;
            }
        }

        if (foliaServer) {
            logBlockedFoliaFallback(formatFoliaSchedulerUnavailableLog());
            return;
        }

        BukkitHandle handle = new BukkitHandle(Bukkit.getScheduler().runTaskLater(plugin, wrappedAction, delayTicks));
        holder[0] = handle;
        scheduledHandles.add(handle);
    }

    private Map<ChunkKey, List<Block>> groupBlocksByChunk(Collection<Block> blocks) {
        Map<ChunkKey, List<Block>> grouped = new HashMap<>();
        for (Block block : blocks) {
            ChunkKey chunkKey = ChunkKey.of(block);
            grouped.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(block);
        }
        return grouped;
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    static boolean shouldUseRegionScheduler(Class<?> serverClass, String serverVersion) {
        return hasMethod(serverClass, "getRegionScheduler");
    }

    static boolean isFoliaServer(Class<?> serverClass, String serverVersion) {
        return isClassPresent(serverClass.getClassLoader(), FOLIA_RUNTIME_MARKER_CLASS);
    }

    static boolean isClassPresent(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private ReflectiveHandle scheduleWithRegionScheduler(Object regionScheduler, Location location, long delayTicks, Runnable wrappedAction)
            throws ReflectiveOperationException {
        Method coordinateBased = findCoordinateRunDelayedMethod(regionScheduler.getClass());
        RegionSchedulerReflectionException coordinateFailure = null;
        if (coordinateBased != null && location.getWorld() != null) {
            World world = location.getWorld();
            try {
                return new ReflectiveHandle(
                        coordinateBased.invoke(
                                regionScheduler,
                                plugin,
                                world,
                                location.getBlockX() >> 4,
                                location.getBlockZ() >> 4,
                                (Consumer<Object>) ignored -> wrappedAction.run(),
                                delayTicks
                        )
                );
            } catch (ReflectiveOperationException | IllegalArgumentException exception) {
                coordinateFailure = new RegionSchedulerReflectionException(
                        "world/chunk-based",
                        coordinateBased.toGenericString(),
                        exception
                );
            }
        }

        Method locationBased = findLocationRunDelayedMethod(regionScheduler.getClass());
        if (locationBased != null) {
            try {
                return new ReflectiveHandle(
                        locationBased.invoke(regionScheduler, plugin, location.clone(), (Consumer<Object>) ignored -> wrappedAction.run(), delayTicks)
                );
            } catch (ReflectiveOperationException | IllegalArgumentException exception) {
                RegionSchedulerReflectionException locationFailure = new RegionSchedulerReflectionException(
                        "location-based",
                        locationBased.toGenericString(),
                        exception
                );
                if (coordinateFailure != null) {
                    locationFailure.addSuppressed(coordinateFailure);
                }
                throw locationFailure;
            }
        }

        if (coordinateFailure != null) {
            throw coordinateFailure;
        }
        throw new RegionSchedulerReflectionException(
                "location-based",
                "runDelayed(Plugin, Location, Consumer, long)",
                new NoSuchMethodException("No compatible RegionScheduler#runDelayed overload found")
        );
    }

    private static Method findLocationRunDelayedMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("runDelayed")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 4) {
                continue;
            }
            if (!Plugin.class.isAssignableFrom(params[0])) {
                continue;
            }
            if (!Location.class.isAssignableFrom(params[1])) {
                continue;
            }
            if (!Consumer.class.isAssignableFrom(params[2])) {
                continue;
            }
            if (!isLongLike(params[3])) {
                continue;
            }
            return method;
        }
        return null;
    }

    private static Method findCoordinateRunDelayedMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("runDelayed")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 6) {
                continue;
            }
            if (!Plugin.class.isAssignableFrom(params[0])) {
                continue;
            }
            if (!World.class.isAssignableFrom(params[1])) {
                continue;
            }
            if (!isIntLike(params[2]) || !isIntLike(params[3])) {
                continue;
            }
            if (!Consumer.class.isAssignableFrom(params[4])) {
                continue;
            }
            if (!isLongLike(params[5])) {
                continue;
            }
            return method;
        }
        return null;
    }

    private static boolean isIntLike(Class<?> type) {
        return type == Integer.TYPE || type == Integer.class;
    }

    private static boolean isLongLike(Class<?> type) {
        return type == Long.TYPE || type == Long.class;
    }

    private static String formatRegionSchedulerFailureLog(Throwable throwable) {
        String path = "unknown";
        String signature = "unknown";
        Throwable actual = throwable;

        if (throwable instanceof RegionSchedulerReflectionException reflectionException) {
            path = reflectionException.path;
            signature = reflectionException.signature;
            if (reflectionException.getCause() != null) {
                actual = reflectionException.getCause();
            }
        }

        Throwable unwrapped = unwrapInvocationTarget(actual);
        String exceptionType = unwrapped == null ? "unknown" : unwrapped.getClass().getName();
        String exceptionMessage = unwrapped == null ? "unknown" : safeMessage(unwrapped.getMessage());
        return "[TreeChopper] Failed to invoke Folia region scheduler for delayed task. "
                + "path=" + path
                + ", signature=" + signature
                + ", exception=" + exceptionType
                + ", message=" + exceptionMessage
                + ". Falling back to Bukkit scheduler.";
    }

    private static String formatFoliaSchedulerFailureLog(Throwable throwable) {
        String path = "unknown";
        String signature = "unknown";
        Throwable actual = throwable;

        if (throwable instanceof RegionSchedulerReflectionException reflectionException) {
            path = reflectionException.path;
            signature = reflectionException.signature;
            if (reflectionException.getCause() != null) {
                actual = reflectionException.getCause();
            }
        }

        Throwable unwrapped = unwrapInvocationTarget(actual);
        String exceptionType = unwrapped == null ? "unknown" : unwrapped.getClass().getName();
        String exceptionMessage = unwrapped == null ? "unknown" : safeMessage(unwrapped.getMessage());
        return "[TreeChopper] Failed to invoke Folia region scheduler for delayed task. "
                + "path=" + path
                + ", signature=" + signature
                + ", exception=" + exceptionType
                + ", message=" + exceptionMessage
                + ". Bukkit fallback is intentionally disabled on Folia; task was not scheduled.";
    }

    private static String formatFoliaSchedulerUnavailableLog() {
        return "[TreeChopper] Folia delayed task scheduling is unavailable and Bukkit fallback is intentionally disabled on Folia; task was not scheduled.";
    }

    private void logBlockedFoliaFallback(String message) {
        if (foliaFallbackBlockedLogged) {
            return;
        }
        synchronized (this) {
            if (foliaFallbackBlockedLogged) {
                return;
            }
            foliaFallbackBlockedLogged = true;
            plugin.getLogger().severe(message);
        }
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "<no message>";
        }
        return message;
    }

    private static Throwable unwrapInvocationTarget(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getCause() != null) {
            current = invocationTargetException.getCause();
        }
        return current;
    }

    private static final class RegionSchedulerReflectionException extends ReflectiveOperationException {
        private final String path;
        private final String signature;

        private RegionSchedulerReflectionException(String path, String signature, Throwable cause) {
            super(cause == null ? null : cause.getMessage(), cause);
            this.path = path;
            this.signature = signature;
        }
    }

    private interface ScheduledHandle {
        void cancel();
    }

    private final class BukkitHandle implements ScheduledHandle {
        private final BukkitTask task;

        private BukkitHandle(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
            scheduledHandles.remove(this);
        }
    }

    private final class ReflectiveHandle implements ScheduledHandle {
        private final Object scheduledTask;

        private ReflectiveHandle(Object scheduledTask) {
            this.scheduledTask = scheduledTask;
        }

        @Override
        public void cancel() {
            try {
                scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            }
            scheduledHandles.remove(this);
        }
    }
}
