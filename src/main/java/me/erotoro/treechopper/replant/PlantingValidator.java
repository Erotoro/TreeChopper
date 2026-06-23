package me.erotoro.treechopper.replant;

import me.erotoro.treechopper.compat.BlockPlacementCompat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PlantingValidator {

    private final Function<Material, BlockData> blockDataFactory;
    private final Predicate<Material> replaceableChecker;
    private final Predicate<Material> blockMaterialChecker;
    private final Predicate<Material> airChecker;

    public PlantingValidator() {
        this(Bukkit::createBlockData, BlockPlacementCompat::isReplaceable, Material::isBlock, Material::isAir);
    }

    PlantingValidator(Function<Material, BlockData> blockDataFactory, Predicate<Material> replaceableChecker,
                      Predicate<Material> blockMaterialChecker, Predicate<Material> airChecker) {
        this.blockDataFactory = Objects.requireNonNull(blockDataFactory, "blockDataFactory");
        this.replaceableChecker = Objects.requireNonNull(replaceableChecker, "replaceableChecker");
        this.blockMaterialChecker = Objects.requireNonNull(blockMaterialChecker, "blockMaterialChecker");
        this.airChecker = Objects.requireNonNull(airChecker, "airChecker");
    }

    public ValidationResult validate(Block target, Material plantingMaterial) {
        if (!blockMaterialChecker.test(plantingMaterial)) {
            return ValidationResult.failure("Planting material is not placeable: " + plantingMaterial);
        }
        if (!target.getChunk().isLoaded()) {
            return ValidationResult.failure("Target chunk is not loaded.");
        }
        if (target.isLiquid()) {
            return ValidationResult.failure("Target block is liquid.");
        }
        Material targetType = target.getType();
        if (!airChecker.test(targetType) && !replaceableChecker.test(targetType)) {
            return ValidationResult.failure("Target block is not air/replaceable: " + targetType);
        }

        Block soilBlock = target.getRelative(0, -1, 0);
        if (soilBlock.isLiquid()) {
            return ValidationResult.failure("Soil block is liquid.");
        }

        BlockData plantingData = blockDataFactory.apply(plantingMaterial);
        if (!BlockPlacementCompat.canPlace(target, plantingData)) {
            return ValidationResult.failure("Block#canPlace denied planting.");
        }
        if (!BlockPlacementCompat.isSupported(plantingData, target)) {
            return ValidationResult.failure("BlockData#isSupported denied planting.");
        }
        return ValidationResult.success();
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult success() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
