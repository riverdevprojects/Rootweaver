package com.example.examplemod.strategy;

import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;

public interface GrowthStrategy {
    GrowthProfile profile(TreeDefinition definition, GenerationContext context);

    record GrowthProfile(int trunkSteps, float stepSize, float branchBias, float taper, float curvature, float upwardPull,
                         float branchDivergence) {}
}
