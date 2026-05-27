package com.example.examplemod.strategy;

import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;

public interface GrowthStrategy {
    TrunkPlan planTrunk(TreeDefinition definition, GenerationContext context);

    record TrunkPlan(int height, int leanX, int leanZ, float branchBias, float taper) {}
}
