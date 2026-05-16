package me.erotoro.treechopper.tree;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestBlockWorld {

    private final UUID worldId = UUID.randomUUID();
    private final Map<BlockPos, Material> materials = new HashMap<>();
    private final Map<BlockPos, Block> blocks = new HashMap<>();
    private final Chunk loadedChunk = mock(Chunk.class);
    private final World world = mock(World.class);

    TestBlockWorld() {
        when(loadedChunk.isLoaded()).thenReturn(true);
        when(world.getUID()).thenReturn(worldId);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> blockAt(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class)))
                .thenAnswer(invocation -> {
                    Location location = invocation.getArgument(0);
                    return blockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                });
    }

    World world() {
        return world;
    }

    Block blockAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return blocks.computeIfAbsent(pos, this::createBlock);
    }

    void setType(int x, int y, int z, Material material) {
        materials.put(new BlockPos(x, y, z), Objects.requireNonNull(material, "material"));
    }

    Material getType(int x, int y, int z) {
        return materials.getOrDefault(new BlockPos(x, y, z), Material.AIR);
    }

    private Block createBlock(BlockPos pos) {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(pos.x());
        when(block.getY()).thenReturn(pos.y());
        when(block.getZ()).thenReturn(pos.z());
        when(block.getWorld()).thenReturn(world);
        when(block.getChunk()).thenReturn(loadedChunk);
        when(block.getLocation()).thenAnswer(invocation -> new Location(world, pos.x(), pos.y(), pos.z()));
        when(block.getRelative(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> blockAt(
                        pos.x() + invocation.<Integer>getArgument(0),
                        pos.y() + invocation.<Integer>getArgument(1),
                        pos.z() + invocation.<Integer>getArgument(2)
                ));
        when(block.getType()).thenAnswer(invocation -> getType(pos.x(), pos.y(), pos.z()));
        when(block.getState()).thenAnswer(invocation -> createBlockState(pos));
        when(block.breakNaturally()).thenAnswer(invocation -> {
            setType(pos.x(), pos.y(), pos.z(), Material.AIR);
            return true;
        });
        doAnswer(invocation -> {
            setType(pos.x(), pos.y(), pos.z(), invocation.getArgument(0));
            return null;
        }).when(block).setType(org.mockito.ArgumentMatchers.any(Material.class), org.mockito.ArgumentMatchers.anyBoolean());
        return block;
    }

    private BlockState createBlockState(BlockPos pos) {
        BlockState state = mock(BlockState.class);
        when(state.getType()).thenReturn(getType(pos.x(), pos.y(), pos.z()));
        when(state.getX()).thenReturn(pos.x());
        when(state.getY()).thenReturn(pos.y());
        when(state.getZ()).thenReturn(pos.z());
        when(state.getWorld()).thenReturn(world);
        return state;
    }

    private record BlockPos(int x, int y, int z) {
    }
}
