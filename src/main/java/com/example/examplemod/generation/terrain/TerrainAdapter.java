package com.example.examplemod.generation.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public final class TerrainAdapter {
    private TerrainAdapter() {}

    public static BlockPos findSurface(LevelAccessor level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        while (cursor.getY() > level.getMinBuildHeight()) {
            BlockState below = level.getBlockState(cursor.below());
            if (below.isSolid()) return cursor.immutable();
            cursor.move(0, -1, 0);
        }
        return start;
    }
}
