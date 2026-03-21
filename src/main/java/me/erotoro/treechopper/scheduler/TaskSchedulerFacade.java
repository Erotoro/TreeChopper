package me.erotoro.treechopper.scheduler;

import me.erotoro.treechopper.model.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
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

    private final JavaPlugin plugin;
    private final Set<ScheduledHandle> scheduledHandles = ConcurrentHashMap.newKeySet();
    private volatile boolean regionSchedulerAvailable;

    public TaskSchedulerFacade(JavaPlugin plugin) {
        this.plugin = plugin;
        this.regionSchedulerAvailable = hasMethod(plugin.getServer().getClass(), "getRegionScheduler");
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
                Method runDelayed = findMethod(regionScheduler.getClass(), "runDelayed", 4);
                ReflectiveHandle handle = new ReflectiveHandle(
                        runDelayed.invoke(regionScheduler, plugin, location.clone(), (Consumer<Object>) ignored -> wrappedAction.run(), delayTicks)
                );
                holder[0] = handle;
                scheduledHandles.add(handle);
                return;
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("Failed to use delayed region scheduler, falling back to Bukkit scheduler: " + exception.getMessage());
                regionSchedulerAvailable = false;
            }
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

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "/" + parameterCount);
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
