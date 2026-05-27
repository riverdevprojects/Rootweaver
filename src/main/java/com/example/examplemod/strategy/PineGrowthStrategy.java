package com.example.examplemod.strategy;

import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;
import net.minecraft.util.Mth;

public final class PineGrowthStrategy implements GrowthStrategy {
    @Override
    public TrunkPlan planTrunk(TreeDefinition d, GenerationContext c) {
        int h = Mth.nextInt(c.random(), d.minHeight(), d.maxHeight());
        return new TrunkPlan(h, 0, 0, 0.35f, 0.8f);
    }
}
