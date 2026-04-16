package me.erotoro.treechopper.replant;

import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlantingValidatorTest {

    @Test
    void acceptsValidAirTargetWhenPlacementRulesAllow() {
        Block target = mockTarget(Material.AIR, false, true);
        Block soil = mock(Block.class);
        when(soil.isLiquid()).thenReturn(false);
        when(target.getRelative(0, -1, 0)).thenReturn(soil);

        BlockData data = mock(BlockData.class);
        when(data.isSupported(target)).thenReturn(true);
        Function<Material, BlockData> dataFactory = ignored -> data;
        Predicate<Material> replaceableChecker = ignored -> false;

        PlantingValidator validator = new PlantingValidator(dataFactory, replaceableChecker, ignored -> true, material -> material == Material.AIR);
        PlantingValidator.ValidationResult result = validator.validate(target, Material.OAK_SAPLING);

        assertTrue(result.valid());
    }

    @Test
    void rejectsNonReplaceableOccupiedTarget() {
        Block target = mockTarget(Material.STONE, false, true);
        Block soil = mock(Block.class);
        when(soil.isLiquid()).thenReturn(false);
        when(target.getRelative(0, -1, 0)).thenReturn(soil);

        BlockData data = mock(BlockData.class);
        when(data.isSupported(target)).thenReturn(true);

        PlantingValidator validator = new PlantingValidator(ignored -> data, ignored -> false, ignored -> true, material -> material == Material.AIR);
        PlantingValidator.ValidationResult result = validator.validate(target, Material.OAK_SAPLING);

        assertFalse(result.valid());
    }

    @Test
    void rejectsLiquidSoil() {
        Block target = mockTarget(Material.AIR, false, true);
        Block soil = mock(Block.class);
        when(soil.isLiquid()).thenReturn(true);
        when(target.getRelative(0, -1, 0)).thenReturn(soil);

        BlockData data = mock(BlockData.class);
        when(data.isSupported(target)).thenReturn(true);
        PlantingValidator validator = new PlantingValidator(ignored -> data, ignored -> true, ignored -> true, material -> material == Material.AIR);

        PlantingValidator.ValidationResult result = validator.validate(target, Material.OAK_SAPLING);

        assertFalse(result.valid());
    }

    @Test
    void rejectsWhenCanPlaceFailsForInvalidSoilOrSupport() {
        Block target = mockTarget(Material.AIR, false, false);
        Block soil = mock(Block.class);
        when(soil.isLiquid()).thenReturn(false);
        when(target.getRelative(0, -1, 0)).thenReturn(soil);

        BlockData data = mock(BlockData.class);
        when(data.isSupported(target)).thenReturn(true);
        PlantingValidator validator = new PlantingValidator(ignored -> data, ignored -> true, ignored -> true, material -> material == Material.AIR);

        PlantingValidator.ValidationResult result = validator.validate(target, Material.OAK_SAPLING);

        assertFalse(result.valid());
    }

    @Test
    void rejectsLiquidTargetBlock() {
        Block target = mockTarget(Material.WATER, true, true);
        Block soil = mock(Block.class);
        when(soil.isLiquid()).thenReturn(false);
        when(target.getRelative(0, -1, 0)).thenReturn(soil);

        BlockData data = mock(BlockData.class);
        when(data.isSupported(target)).thenReturn(true);
        PlantingValidator validator = new PlantingValidator(ignored -> data, ignored -> true, ignored -> true, material -> material == Material.AIR);

        PlantingValidator.ValidationResult result = validator.validate(target, Material.OAK_SAPLING);

        assertFalse(result.valid());
    }

    private Block mockTarget(Material type, boolean liquid, boolean canPlace) {
        Block target = mock(Block.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.isLoaded()).thenReturn(true);
        when(target.getChunk()).thenReturn(chunk);
        when(target.getType()).thenReturn(type);
        when(target.isLiquid()).thenReturn(liquid);
        when(target.canPlace(org.mockito.ArgumentMatchers.any(BlockData.class))).thenReturn(canPlace);
        return target;
    }
}
