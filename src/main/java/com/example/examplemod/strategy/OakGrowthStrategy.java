package com.example.examplemod.strategy;

import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;
import net.minecraft.util.Mth;

public final class OakGrowthStrategy implements GrowthStrategy {
    @Override
    public TrunkPlan planTrunk(TreeDefinition d, GenerationContext c) {
        int h = Mth.nextInt(c.random(), d.minHeight(), d.maxHeight());
        int lx = d.canLean() ? c.random().nextInt(3) - 1 : 0;
        int lz = d.canLean() ? c.random().nextInt(3) - 1 : 0;
        return new TrunkPlan(h, lx, lz, 0.75f, 0.55f);
    }
}
