package com.example.examplemod.strategy;

import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;
import net.minecraft.util.Mth;

public final class PineGrowthStrategy implements GrowthStrategy {
    @Override
    public GrowthProfile profile(TreeDefinition d, GenerationContext c) {
        int h = Mth.nextInt(c.random(), d.minHeight(), d.maxHeight());
        return new GrowthProfile(h, 1.0f, 0.4f, 0.84f, 0.07f, 0.45f, 0.38f);
    }
}
