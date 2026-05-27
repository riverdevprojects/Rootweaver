package com.example.examplemod.generation.pipeline;

import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;
import com.example.examplemod.generation.terrain.TerrainAdapter;
import com.example.examplemod.strategy.GrowthStrategy;
import com.example.examplemod.strategy.StrategyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;

public final class ProceduralTreeGenerator {
    public boolean generate(TreeDefinition definition, GenerationContext ctx) {
        LevelAccessor level = ctx.level();
        BlockPos base = TerrainAdapter.findSurface(level, ctx.origin());
        GrowthStrategy strategy = StrategyRegistry.resolve(definition.growthStyle());
        GrowthStrategy.TrunkPlan plan = strategy.planTrunk(definition, ctx);

        int width = Math.max(1, definition.minTrunkWidth());
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = 0; y < plan.height(); y++) {
            int ox = plan.leanX() == 0 ? 0 : (y * plan.leanX()) / Math.max(1, plan.height() - 1);
            int oz = plan.leanZ() == 0 ? 0 : (y * plan.leanZ()) / Math.max(1, plan.height() - 1);
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    mutable.set(base.getX() + ox + x, base.getY() + y, base.getZ() + oz + z);
                    if (level.getBlockState(mutable).isAir()) {
                        level.setBlock(mutable, Blocks.OAK_LOG.defaultBlockState(), 3);
                    }
                }
            }
        }
        return true;
    }
}
