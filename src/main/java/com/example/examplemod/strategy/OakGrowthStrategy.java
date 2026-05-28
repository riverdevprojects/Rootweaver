package com.example.examplemod.strategy;

import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;
import net.minecraft.util.Mth;

public final class OakGrowthStrategy implements GrowthStrategy {
    @Override
    public GrowthProfile profile(TreeDefinition d, GenerationContext c) {
        int h = Mth.nextInt(c.random(), d.minHeight(), d.maxHeight());
        return new GrowthProfile(h, 0.9f, 0.8f, 0.65f, 0.14f, 0.28f, 0.62f);
    }
}
