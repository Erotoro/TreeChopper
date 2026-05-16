package me.erotoro.treechopper.replant;

import me.erotoro.treechopper.model.BlockKey;
import me.erotoro.treechopper.coreprotect.CoreProtectService;
import me.erotoro.treechopper.protection.ProtectionService;
import me.erotoro.treechopper.scheduler.TaskSchedulerFacade;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AutoReplantService {

    private final JavaPlugin plugin;
    private final PluginManager pluginManager;
    private final TaskSchedulerFacade scheduler;
    private final SaplingResolver saplingResolver;
    private final PlantingValidator plantingValidator;
    private final ReplantLayoutPlanner layoutPlanner;
    private final SaplingInventoryService saplingInventoryService;
    private volatile ProtectionService protectionService;
    private volatile CoreProtectService coreProtectService;
    private volatile AutoReplantSettings settings;

    public AutoReplantService(JavaPlugin plugin, PluginManager pluginManager, TaskSchedulerFacade scheduler,
                              AutoReplantSettings settings, ProtectionService protectionService, SaplingResolver saplingResolver,
                              PlantingValidator plantingValidator, ReplantLayoutPlanner layoutPlanner,
                              SaplingInventoryService saplingInventoryService, CoreProtectService coreProtectService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.protectionService = Objects.requireNonNull(protectionService, "protectionService");
        this.saplingResolver = Objects.requireNonNull(saplingResolver, "saplingResolver");
        this.plantingValidator = Objects.requireNonNull(plantingValidator, "plantingValidator");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner");
        this.saplingInventoryService = Objects.requireNonNull(saplingInventoryService, "saplingInventoryService");
        this.coreProtectService = Objects.requireNonNull(coreProtectService, "coreProtectService");
    }

    public void updateSettings(AutoReplantSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void updateProtectionService(ProtectionService protectionService) {
        this.protectionService = Objects.requireNonNull(protectionService, "protectionService");
    }

    public void updateCoreProtectService(CoreProtectService coreProtectService) {
        this.coreProtectService = Objects.requireNonNull(coreProtectService, "coreProtectService");
    }

    public void scheduleReplant(ReplantRequest request) {
        if (request == null || request.anchor() == null || request.logType() == null || request.trunkBaseBlocks() == null) {
            return;
        }
        PreparedAttempt preparedAttempt = prepareAttempt(request);
        if (preparedAttempt.precheckFailure().isPresent()) {
            debug(preparedAttempt.precheckFailure().get(), request, preparedAttempt.settings().debug());
            return;
        }
        if (preparedAttempt.context().isEmpty()) {
            debug(ReplantResult.skipped(
                    ReplantResultType.MISSING_PLANTING_LOCATION,
                    "Replant context is unavailable."
            ), request, preparedAttempt.settings().debug());
            return;
        }

        scheduler.scheduleDelayed(request.anchor(), preparedAttempt.settings().delayTicksAfterFell(), () -> {
            ReplantResult result = attemptReplant(request, preparedAttempt.context().get());
            debug(result, request, preparedAttempt.settings().debug());
        });
    }

    private PreparedAttempt prepareAttempt(ReplantRequest request) {
        AutoReplantSettings currentSettings = this.settings;
        if (!currentSettings.enabled()) {
            return PreparedAttempt.failed(currentSettings,
                    ReplantResult.skipped(ReplantResultType.FEATURE_DISABLED, "Auto-replant is disabled."));
        }
        World world = request.anchor().getWorld();
        if (world == null) {
            return PreparedAttempt.failed(currentSettings,
                    ReplantResult.skipped(ReplantResultType.MISSING_PLANTING_LOCATION, "Anchor world is unavailable."));
        }
        if (currentSettings.isWorldDisabled(world.getName())) {
            return PreparedAttempt.failed(currentSettings,
                    ReplantResult.skipped(ReplantResultType.WORLD_DISABLED, "World is disabled."));
        }
        if (currentSettings.onlyNaturalTrees() && !request.naturalTree()) {
            return PreparedAttempt.failed(currentSettings,
                    ReplantResult.skipped(ReplantResultType.ONLY_NATURAL_ENABLED, "Tree is not natural."));
        }

        Optional<Material> resolvedSapling = saplingResolver.resolve(request.logType());
        if (resolvedSapling.isEmpty()) {
            return PreparedAttempt.failed(currentSettings,
                    ReplantResult.skipped(ReplantResultType.UNSUPPORTED_TREE_TYPE, "No sapling mapping for " + request.logType()));
        }

        ReplantLayout layout = layoutPlanner.plan(
                request.trunkBaseBlocks(),
                request.megaTree(),
                currentSettings.replantMegaTrees(),
                currentSettings.megaMode()
        );
        if (request.megaTree() && !currentSettings.replantMegaTrees()) {
            return PreparedAttempt.failed(currentSettings,
                    ReplantResult.skipped(ReplantResultType.MEGA_DISABLED, "Mega tree replanting is disabled."));
        }
        if (layout.targetBlocks().isEmpty()) {
            return PreparedAttempt.failed(currentSettings,
                    ReplantResult.skipped(ReplantResultType.MISSING_PLANTING_LOCATION, "No valid planting layout."));
        }

        return PreparedAttempt.ready(currentSettings, new ReplantContext(currentSettings, resolvedSapling.get(), layout));
    }

    private ReplantResult attemptReplant(ReplantRequest request, ReplantContext context) {
        Player player = request.player();
        if (player == null || !player.isOnline()) {
            return ReplantResult.skipped(ReplantResultType.PLAYER_UNAVAILABLE, "Player is offline.");
        }

        World world = request.anchor().getWorld();
        if (world == null) {
            return ReplantResult.skipped(ReplantResultType.MISSING_PLANTING_LOCATION, "Anchor world is unavailable.");
        }

        AutoReplantSettings currentSettings = context.settings();
        Material saplingType = context.saplingType();
        ReplantLayout layout = context.layout();

        TargetResolution targetResolution = resolveTargetBlocks(world, layout);
        if (targetResolution.failure().isPresent()) {
            return targetResolution.failure().get();
        }
        List<Block> targets = targetResolution.targets();
        for (Block target : targets) {
            PlantingValidator.ValidationResult validation = plantingValidator.validate(target, saplingType);
            if (!validation.valid()) {
                return ReplantResult.skipped(ReplantResultType.INVALID_PLANTING_LOCATION, validation.reason());
            }
        }

        if (currentSettings.respectProtection()) {
            for (Block target : targets) {
                if (!canPlaceWithEvents(player, target, saplingType)) {
                    return ReplantResult.skipped(ReplantResultType.PROTECTION_DENIED, "BlockPlaceEvent denied at " + formatBlock(target));
                }
            }
        }

        for (Block target : targets) {
            if (!protectionService.canPlace(player, target, saplingType)) {
                return ReplantResult.skipped(
                        ReplantResultType.PROTECTION_DENIED,
                        "Protection hook denied placement at " + formatBlock(target)
                );
            }
        }

        SaplingInventoryService.InventoryPolicyResult inventoryResult = saplingInventoryService.verifyAndConsume(
                player.getInventory(),
                saplingType,
                layout.saplingsRequired(),
                currentSettings.requireSapling(),
                currentSettings.consumeSapling()
        );
        if (!inventoryResult.allowed()) {
            return inventoryResult.failure();
        }

        int planted = 0;
        for (Block target : targets) {
            target.setType(saplingType, false);
            if (target.getType() != saplingType) {
                continue;
            }
            coreProtectService.logPlacement(player.getName(), target.getState());
            planted++;
        }
        return ReplantResult.planted(planted);
    }

    private boolean canPlaceWithEvents(Player player, Block target, Material saplingType) {
        BlockState replacedState = target.getState();
        Block placeAgainst = target.getRelative(0, -1, 0);
        BlockPlaceEvent event = new BlockPlaceEvent(
                target,
                replacedState,
                placeAgainst,
                new ItemStack(saplingType),
                player,
                true,
                EquipmentSlot.HAND
        );
        pluginManager.callEvent(event);
        return !event.isCancelled() && event.canBuild();
    }

    private TargetResolution resolveTargetBlocks(World world, ReplantLayout layout) {
        List<Block> targets = new ArrayList<>(layout.targetBlocks().size());
        UUID worldId = world.getUID();
        int expectedChunkX = 0;
        int expectedChunkZ = 0;
        boolean expectedChunkSet = false;
        for (BlockKey key : layout.targetBlocks()) {
            if (!worldId.equals(key.worldId())) {
                return TargetResolution.failed(ReplantResult.skipped(
                        ReplantResultType.MISSING_PLANTING_LOCATION,
                        "Target world mismatch for trunk base."
                ));
            }
            int chunkX = key.x() >> 4;
            int chunkZ = key.z() >> 4;
            if (!expectedChunkSet) {
                expectedChunkX = chunkX;
                expectedChunkZ = chunkZ;
                expectedChunkSet = true;
            } else if (expectedChunkX != chunkX || expectedChunkZ != chunkZ) {
                return TargetResolution.failed(ReplantResult.skipped(
                        ReplantResultType.INVALID_PLANTING_LOCATION,
                        "Cross-chunk replant layout is skipped for region safety."
                ));
            }
            targets.add(world.getBlockAt(key.x(), key.y(), key.z()));
        }
        return TargetResolution.success(targets);
    }

    private void debug(ReplantResult result, ReplantRequest request, boolean debugEnabled) {
        if (!debugEnabled) {
            return;
        }
        String worldName = request.anchor().getWorld() == null ? "unknown" : request.anchor().getWorld().getName();
        plugin.getLogger().info("[AutoReplant] " + result.type() + " world=" + worldName + " detail=" + result.detail());
    }

    private String formatBlock(Block block) {
        Location location = block.getLocation();
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private record PreparedAttempt(AutoReplantSettings settings, Optional<ReplantContext> context,
                                   Optional<ReplantResult> precheckFailure) {
        private static PreparedAttempt ready(AutoReplantSettings settings, ReplantContext context) {
            return new PreparedAttempt(settings, Optional.of(context), Optional.empty());
        }

        private static PreparedAttempt failed(AutoReplantSettings settings, ReplantResult failure) {
            return new PreparedAttempt(settings, Optional.empty(), Optional.of(failure));
        }
    }

    private record ReplantContext(AutoReplantSettings settings, Material saplingType, ReplantLayout layout) {
    }

    private record TargetResolution(List<Block> targets, Optional<ReplantResult> failure) {
        private static TargetResolution success(List<Block> targets) {
            return new TargetResolution(targets, Optional.empty());
        }

        private static TargetResolution failed(ReplantResult failure) {
            return new TargetResolution(List.of(), Optional.of(failure));
        }
    }
}
