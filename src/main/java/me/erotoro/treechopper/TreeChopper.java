package me.erotoro.treechopper;

import me.erotoro.treechopper.model.BlockKey;
import me.erotoro.treechopper.model.BreakPlan;
import me.erotoro.treechopper.model.TreeSearchResult;
import me.erotoro.treechopper.coreprotect.CoreProtectService;
import me.erotoro.treechopper.i18n.LocalizationService;
import me.erotoro.treechopper.protection.ProtectionService;
import me.erotoro.treechopper.replant.AutoReplantService;
import me.erotoro.treechopper.replant.PlantingValidator;
import me.erotoro.treechopper.replant.ReplantLayoutPlanner;
import me.erotoro.treechopper.replant.ReplantRequest;
import me.erotoro.treechopper.replant.SaplingInventoryService;
import me.erotoro.treechopper.replant.SaplingResolver;
import me.erotoro.treechopper.scheduler.TaskSchedulerFacade;
import me.erotoro.treechopper.storage.PlacedLogPersistence;
import me.erotoro.treechopper.toggle.PlayerTogglePersistence;
import me.erotoro.treechopper.toggle.PlayerToggleService;
import me.erotoro.treechopper.tree.FoliageBreakService;
import me.erotoro.treechopper.tree.TreeMaterials;
import me.erotoro.treechopper.tree.TreeSearchService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TreeChopper extends JavaPlugin implements Listener {

    private static final String FB_TAG = "treechopper_fb";

    private static final int[][] NEIGHBOR_OFFSETS = createCubeOffsets(1, false);
    private static final int[][] CARDINAL_OFFSETS = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final Set<BlockKey> playerPlacedLogs = ConcurrentHashMap.newKeySet();
    private final Set<BlockKey> internalBreaks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeFallingBlocks = ConcurrentHashMap.newKeySet();

    private PluginManager pluginManager;
    private TaskSchedulerFacade scheduler;
    private TreeSearchService treeSearchService;
    private FoliageBreakService foliageBreakService;
    private AutoReplantService autoReplantService;
    private ProtectionService protectionService;
    private CoreProtectService coreProtectService;
    private File placedLogsFile;
    private File playerTogglesFile;
    private PlayerToggleService playerToggleService;
    private LocalizationService localizationService;
    private final AtomicBoolean placedLogsDirty = new AtomicBoolean();
    private volatile TreeChopperSettings settings = TreeChopperSettings.DEFAULT;

    @Override
    public void onEnable() {
        pluginManager = getServer().getPluginManager();
        scheduler = new TaskSchedulerFacade(this);
        placedLogsFile = new File(getDataFolder(), "placed-logs.yml");
        playerTogglesFile = new File(getDataFolder(), "player-toggles.yml");
        saveDefaultConfig();
        reloadConfig();
        localizationService = new LocalizationService(this);
        localizationService.load(getConfig());
        settings = TreeChopperSettings.load(getConfig());
        treeSearchService = new TreeSearchService(playerPlacedLogs, settings, NEIGHBOR_OFFSETS, CARDINAL_OFFSETS);
        coreProtectService = CoreProtectService.initialize(this, pluginManager, settings.coreProtect());
        foliageBreakService = new FoliageBreakService(
                settings,
                scheduler,
                NEIGHBOR_OFFSETS,
                this::fireSyntheticBreak,
                coreProtectService
        );
        protectionService = ProtectionService.initialize(this, pluginManager, settings.protection());
        autoReplantService = new AutoReplantService(
                this,
                pluginManager,
                scheduler,
                settings.autoReplant(),
                protectionService,
                new SaplingResolver(),
                new PlantingValidator(),
                new ReplantLayoutPlanner(),
                new SaplingInventoryService(),
                coreProtectService
        );
        playerToggleService = new PlayerToggleService(
                new PlayerTogglePersistence(playerTogglesFile),
                settings.playerToggle(),
                getLogger()
        );
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Failed to create plugin data folder: " + getDataFolder());
        }
        pluginManager.registerEvents(this, this);
        if (getCommand("treechopper") != null) {
            getCommand("treechopper").setExecutor(this);
            getCommand("treechopper").setTabCompleter(this);
        }
        loadPlacedLogs();
        playerToggleService.load();
        getLogger().info("TreeChopper loaded. Region scheduler: " + scheduler.isRegionSchedulerAvailable()
                + ", placed logs: " + playerPlacedLogs.size()
                + ", player toggles: " + playerToggleService.size());
    }

    @Override
    public void onDisable() {
        savePlacedLogsIfDirty();
        savePlayerTogglesIfDirty();
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        playerPlacedLogs.clear();
        internalBreaks.clear();
        activeFallingBlocks.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("treechopper")) {
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("treechopper.reload")) {
                sendLocalized(sender, "messages.errors.no-permission");
                return true;
            }

            if (scheduler != null) {
                scheduler.cancelAll();
            }
            savePlacedLogsIfDirty();
            savePlayerTogglesIfDirty();
            reloadConfig();
            localizationService.load(getConfig());
            settings = TreeChopperSettings.load(getConfig());
            treeSearchService = new TreeSearchService(playerPlacedLogs, settings, NEIGHBOR_OFFSETS, CARDINAL_OFFSETS);
            coreProtectService = CoreProtectService.initialize(this, pluginManager, settings.coreProtect());
            foliageBreakService = new FoliageBreakService(
                    settings,
                    scheduler,
                    NEIGHBOR_OFFSETS,
                    this::fireSyntheticBreak,
                    coreProtectService
            );
            protectionService = ProtectionService.initialize(this, pluginManager, settings.protection());
            autoReplantService.updateProtectionService(protectionService);
            autoReplantService.updateSettings(settings.autoReplant());
            autoReplantService.updateCoreProtectService(coreProtectService);
            playerToggleService.updateSettings(settings.playerToggle());
            loadPlacedLogs();
            playerToggleService.load();
            sendLocalized(
                    sender,
                    "messages.command.reload.success",
                    "placedLogs", String.valueOf(playerPlacedLogs.size()),
                    "playerToggles", String.valueOf(playerToggleService.size())
            );
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player player)) {
                sendLocalized(sender, "messages.errors.player-only");
                return true;
            }
            if (!sender.hasPermission("treechopper.toggle")) {
                sendLocalized(sender, "messages.errors.no-permission");
                return true;
            }
            if (!playerToggleService.isFeatureEnabled()) {
                sendLocalized(sender, "messages.command.toggle.feature-disabled");
                return true;
            }

            boolean enabled = playerToggleService.toggle(player.getUniqueId());
            sendLocalized(sender, enabled ? "messages.command.toggle.enabled" : "messages.command.toggle.disabled");
            return true;
        }

        sendLocalized(sender, "messages.command.usage", "label", label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("treechopper")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>(2);
            if ("reload".startsWith(prefix) && sender.hasPermission("treechopper.reload")) {
                completions.add("reload");
            }
            if ("toggle".startsWith(prefix) && sender.hasPermission("treechopper.toggle")) {
                completions.add("toggle");
            }
            return completions;
        }
        return Collections.emptyList();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockCleanup(BlockBreakEvent event) {
        if (playerPlacedLogs.remove(BlockKey.of(event.getBlock()))) {
            markPlacedLogsDirty();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (playerPlacedLogs.remove(BlockKey.of(block))) {
                markPlacedLogsDirty();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (playerPlacedLogs.remove(BlockKey.of(block))) {
                markPlacedLogsDirty();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (playerPlacedLogs.remove(BlockKey.of(event.getBlock()))) {
            markPlacedLogsDirty();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLog(event.getBlock())) {
            if (playerPlacedLogs.add(BlockKey.of(event.getBlock()))) {
                markPlacedLogsDirty();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) {
            return;
        }
        if (!fallingBlock.getScoreboardTags().contains(FB_TAG)) {
            return;
        }
        activeFallingBlocks.remove(fallingBlock.getUniqueId());
        event.setCancelled(true);
        fallingBlock.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTreeBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockKey targetKey = BlockKey.of(block);
        if (internalBreaks.contains(targetKey) || !isLog(block)) {
            return;
        }

        Player player = event.getPlayer();
        if (!playerToggleService.isEnabled(player.getUniqueId())) {
            return;
        }
        if (!settings.shouldActivateFor(player)) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isAxe(tool)) {
            return;
        }

        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        int currentDamage = damageable.getDamage();
        int maxDurability = tool.getType().getMaxDurability();
        if (currentDamage >= maxDurability) {
            return;
        }

        if (playerPlacedLogs.remove(targetKey)) {
            markPlacedLogsDirty();
            return;
        }

        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        int effectiveMaxBreaks = unbreakingLevel > 0
                ? (maxDurability - currentDamage) * (unbreakingLevel + 1)
                : (maxDurability - currentDamage);

        TreeSearchResult result = treeSearchService.findTree(block, Math.min(effectiveMaxBreaks, settings.maxLogs()));
        if (!result.foundLeaves || result.foundPlayerPlaced) {
            return;
        }
        boolean naturalTree = result.foundLeaves && !result.foundPlayerPlaced;

        BreakPlan breakPlan = planBrokenLogs(block, result.treeBlocks, currentDamage, maxDurability, unbreakingLevel);
        if (breakPlan.blocks.isEmpty()) {
            return;
        }
        if (!protectionService.canBreakAll(player, breakPlan.blocks)) {
            return;
        }

        event.setCancelled(true);

        int newDamage = Math.min(maxDurability, currentDamage + breakPlan.durabilityDamage);
        damageable.setDamage(newDamage);
        tool.setItemMeta(meta);
        if (newDamage >= maxDurability) {
            player.getInventory().setItemInMainHand(null);
        }

        Set<BlockKey> brokenLogs = ConcurrentHashMap.newKeySet();
        Set<Location> brokenLogLocations = ConcurrentHashMap.newKeySet();

        breakDirectBlock(block, tool, event.isDropItems(), brokenLogs, brokenLogLocations);

        List<Block> animatedBlocks = new ArrayList<>();
        for (Block planned : breakPlan.blocks) {
            if (!planned.equals(block)) {
                animatedBlocks.add(planned);
            }
        }

        if (!animatedBlocks.isEmpty()) {
            animateTreeFall(player, block.getLocation(), animatedBlocks, tool, brokenLogs, brokenLogLocations, result, naturalTree);
        } else if (brokenLogs.size() == result.treeBlocks.size()) {
            onTreeFullyProcessed(player, block.getLocation(), brokenLogLocations, result, naturalTree);
        }
    }

    private BreakPlan planBrokenLogs(Block hitBlock, Set<Block> treeBlocks, int currentDamage, int maxDurability, int unbreakingLevel) {
        List<Block> ordered = new ArrayList<>();
        ordered.add(hitBlock);

        List<Block> remaining = new ArrayList<>(treeBlocks);
        remaining.remove(hitBlock);
        remaining.sort(Comparator.comparingInt(Block::getY).reversed());
        ordered.addAll(remaining);

        List<Block> result = new ArrayList<>();
        int pendingDamage = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Block candidate : ordered) {
            if (currentDamage + pendingDamage >= maxDurability) {
                break;
            }
            result.add(candidate);
            if (unbreakingLevel == 0 || random.nextDouble() < (1.0 / (unbreakingLevel + 1))) {
                pendingDamage++;
            }
        }
        return new BreakPlan(result, pendingDamage);
    }

    private void breakDirectBlock(Block block, ItemStack tool, boolean dropItems, Set<BlockKey> brokenLogs, Set<Location> brokenLogLocations) {
        if (!block.getChunk().isLoaded() || !isLog(block)) {
            return;
        }
        if (dropItems) {
            block.breakNaturally(tool);
        } else {
            block.setType(Material.AIR, false);
        }
        brokenLogs.add(BlockKey.of(block));
        brokenLogLocations.add(block.getLocation());
        if (playerPlacedLogs.remove(BlockKey.of(block))) {
            markPlacedLogsDirty();
        }
    }

    private void animateTreeFall(Player player, Location treeAnchor, List<Block> blocks, ItemStack tool, Set<BlockKey> brokenLogs,
                                 Set<Location> brokenLogLocations, TreeSearchResult result, boolean naturalTree) {
        double centerX = 0.0;
        double centerZ = 0.0;
        for (Block block : blocks) {
            centerX += block.getX();
            centerZ += block.getZ();
        }
        centerX = centerX / blocks.size() + 0.5;
        centerZ = centerZ / blocks.size() + 0.5;

        TreeMap<Integer, List<Block>> byHeight = new TreeMap<>(Comparator.reverseOrder());
        for (Block block : blocks) {
            byHeight.computeIfAbsent(block.getY(), ignored -> new ArrayList<>()).add(block);
        }
        List<List<Block>> layers = new ArrayList<>(byHeight.values());
        Location anchor = blocks.get(0).getLocation();
        double finalCenterX = centerX;
        double finalCenterZ = centerZ;
        long lastDelayTicks = 0L;

        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            long delayTicks = layerIndex * 2L;
            lastDelayTicks = delayTicks;
            scheduler.scheduleBlockBatches(layers.get(layerIndex), delayTicks, settings.maxBlocksPerTask(), batch -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (Block treeLog : batch) {
                    breakAnimatedBlock(player, treeLog, tool, finalCenterX, finalCenterZ, random, brokenLogs, brokenLogLocations);
                }
            });
        }

        scheduler.scheduleDelayed(anchor, lastDelayTicks + 5L, () -> {
            if (brokenLogs.size() == result.treeBlocks.size()) {
                onTreeFullyProcessed(player, treeAnchor, brokenLogLocations, result, naturalTree);
            }
        });
    }

    private void onTreeFullyProcessed(Player player, Location treeAnchor, Set<Location> brokenLogLocations,
                                      TreeSearchResult result, boolean naturalTree) {
        foliageBreakService.breakFoliage(player, brokenLogLocations, result.treeBlocks, result.logType);
        Location replantAnchor = resolveReplantAnchor(result, treeAnchor);
        autoReplantService.scheduleReplant(new ReplantRequest(
                player,
                replantAnchor,
                result.logType,
                result.trunkBaseBlocks,
                result.megaTree,
                naturalTree
        ));
    }

    private Location resolveReplantAnchor(TreeSearchResult result, Location fallback) {
        if (fallback.getWorld() == null || result.trunkBaseBlocks.isEmpty()) {
            return fallback;
        }
        BlockKey firstBase = result.trunkBaseBlocks.stream()
                .sorted(Comparator.comparingInt(BlockKey::y).thenComparingInt(BlockKey::x).thenComparingInt(BlockKey::z))
                .findFirst()
                .orElse(null);
        if (firstBase == null) {
            return fallback;
        }
        return new Location(fallback.getWorld(), firstBase.x(), firstBase.y(), firstBase.z());
    }

    private void cleanupFallingBlock(FallingBlock fallingBlock) {
        if (fallingBlock.isValid() && activeFallingBlocks.remove(fallingBlock.getUniqueId())) {
            fallingBlock.remove();
        }
    }

    private void breakAnimatedBlock(Player player, Block block, ItemStack tool, double centerX, double centerZ,
                                    ThreadLocalRandom random, Set<BlockKey> brokenLogs, Set<Location> brokenLogLocations) {
        if (!block.getChunk().isLoaded() || !isLog(block)) {
            return;
        }

        BlockBreakEvent syntheticBreak = fireSyntheticBreak(player, block);
        if (syntheticBreak.isCancelled()) {
            return;
        }

        BlockState originalState = block.getState();
        BlockData data = block.getBlockData();
        Location blockLocation = block.getLocation();
        if (syntheticBreak.isDropItems()) {
            for (ItemStack drop : block.getDrops(tool)) {
                block.getWorld().dropItemNaturally(blockLocation, drop);
            }
        }

        block.setType(Material.AIR, false);
        if (!block.getType().isAir()) {
            return;
        }
        if (playerPlacedLogs.remove(BlockKey.of(block))) {
            markPlacedLogsDirty();
        }
        brokenLogs.add(BlockKey.of(block));
        brokenLogLocations.add(blockLocation);
        coreProtectService.logRemoval(player.getName(), originalState);

        Location spawnLocation = blockLocation.clone().add(0.5, 0.0, 0.5);
        FallingBlock fallingBlock = block.getWorld().spawn(spawnLocation, FallingBlock.class, entity -> {
            entity.setBlockData(data);
            entity.setDropItem(false);
            entity.setHurtEntities(false);
            entity.setGravity(true);
            entity.addScoreboardTag(FB_TAG);
        });

        double spreadX = spawnLocation.getX() - centerX;
        double spreadZ = spawnLocation.getZ() - centerZ;
        fallingBlock.setVelocity(new Vector(
                spreadX * 0.08 + random.nextDouble(-0.02, 0.02),
                0.05,
                spreadZ * 0.08 + random.nextDouble(-0.02, 0.02)
        ));

        activeFallingBlocks.add(fallingBlock.getUniqueId());
        scheduler.scheduleDelayed(spawnLocation, 60L, () -> cleanupFallingBlock(fallingBlock));
    }

    private boolean isLog(Block block) {
        return TreeMaterials.isLog(block);
    }

    private boolean isAxe(ItemStack itemStack) {
        return itemStack != null && itemStack.getType().name().endsWith("_AXE");
    }

    private void loadPlacedLogs() {
        placedLogsDirty.set(false);
        int loaded = PlacedLogPersistence.load(placedLogsFile, playerPlacedLogs, settings, getLogger());
        getLogger().info("Loaded " + loaded + " player-placed logs.");
    }

    private void savePlacedLogsIfDirty() {
        if (!placedLogsDirty.get()) {
            return;
        }
        savePlacedLogs();
    }

    private void savePlacedLogs() {
        if (placedLogsFile == null) {
            return;
        }
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Failed to create plugin data folder before saving placed logs.");
            return;
        }

        try {
            PlacedLogPersistence.save(placedLogsFile, playerPlacedLogs);
            placedLogsDirty.set(false);
        } catch (IOException exception) {
            getLogger().warning("Failed to save placed logs: " + exception.getMessage());
        }
    }

    private void savePlayerTogglesIfDirty() {
        if (playerToggleService == null || !playerToggleService.isDirty()) {
            return;
        }
        playerToggleService.save();
    }

    private BlockBreakEvent fireSyntheticBreak(Player player, Block block) {
        BlockKey blockKey = BlockKey.of(block);
        internalBreaks.add(blockKey);
        try {
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            pluginManager.callEvent(event);
            return event;
        } finally {
            internalBreaks.remove(blockKey);
        }
    }

    private void markPlacedLogsDirty() {
        placedLogsDirty.set(true);
    }

    private static int[][] createCubeOffsets(int radius, boolean includeCenter) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!includeCenter && dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    offsets.add(new int[]{dx, dy, dz});
                }
            }
        }
        return offsets.toArray(new int[0][]);
    }

    private void sendLocalized(CommandSender sender, String key, String... placeholders) {
        String message;
        if (sender instanceof Player player) {
            message = localizationService.get(player, key);
        } else {
            message = localizationService.get(key);
        }
        sender.sendMessage(applyPlaceholders(message, placeholders));
    }

    private String applyPlaceholders(String input, String... placeholders) {
        if (placeholders == null || placeholders.length < 2) {
            return input;
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < placeholders.length - 1; index += 2) {
            map.put(placeholders[index], placeholders[index + 1]);
        }

        String resolved = input;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }
}
