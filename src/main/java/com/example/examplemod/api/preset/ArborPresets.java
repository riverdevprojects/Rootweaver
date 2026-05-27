package com.example.examplemod.api.preset;

import com.example.examplemod.api.definition.GrowthStyle;
import com.example.examplemod.api.definition.LeafShape;
import com.example.examplemod.api.definition.TreeDefinition;

public final class ArborPresets {
    private ArborPresets() {}

    public static TreeDefinition oak(String id) {
        return TreeDefinition.builder(id).height(18, 35).trunkWidth(2, 4).branchDensity(0.75f).branchLength(3, 8)
                .leafDensity(0.85f).rootChance(0.4f).growthStyle(GrowthStyle.OAK).leafShape(LeafShape.SPHERICAL).build();
    }

    public static TreeDefinition pine(String id) {
        return TreeDefinition.builder(id).height(20, 38).trunkWidth(1, 3).branchDensity(0.5f).branchLength(3, 6)
                .leafDensity(0.8f).growthStyle(GrowthStyle.PINE).leafShape(LeafShape.CONICAL).build();
    }

    public static TreeDefinition giant(String id) {
        return TreeDefinition.builder(id).height(30, 52).trunkWidth(3, 5).branchDensity(0.9f).branchLength(5, 11)
                .leafDensity(0.9f).rootChance(0.6f).growthStyle(GrowthStyle.GIANT_FOREST).maxRecursionDepth(5).build();
    }
}
