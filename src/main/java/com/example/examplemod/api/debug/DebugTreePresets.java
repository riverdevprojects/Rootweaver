package com.example.examplemod.api.debug;

import com.example.examplemod.api.definition.GrowthStyle;
import com.example.examplemod.api.definition.LeafShape;
import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.api.preset.ArborPresets;
import com.example.examplemod.api.registry.TreeRegistry;

public final class DebugTreePresets {
    private DebugTreePresets() {}

    public static void bootstrap() {
        TreeRegistry.register(buildOak());
        TreeRegistry.register(ArborPresets.pine("debug_pine"));
        TreeRegistry.register(ArborPresets.giant("debug_giant"));
        TreeRegistry.register(buildOldOak());
    }

    /**
     * Mature woodland oak — same biological family as old-growth but younger and more common.
     * Spawn with: /arbor spawn debug_oak
     */
    private static TreeDefinition buildOak() {
        return TreeDefinition.builder("debug_oak")
                .height(10, 16)
                .trunkWidth(2, 3)
                .branchDensity(0.80f)
                .branchLength(6, 11)
                .leafDensity(0.80f)
                .rootChance(0.35f)
                .canLean(true)
                .canSplitTrunk(true)
                .maxRecursionDepth(3)
                .growthStyle(GrowthStyle.ANCIENT_OAK)
                .leafShape(LeafShape.LAYERED)
                .heightVariation(0.20f)
                .branchVariation(0.25f)
                .canopyVariation(0.30f)
                .build();
    }

    /**
     * Ancient sprawling oak — LOTR / old-growth aesthetic.
     * Uses AncientOakGenerator via GrowthStyle.ANCIENT_OAK.
     * Spawn with: /arbor spawn debug_old_oak
     */
    private static TreeDefinition buildOldOak() {
        return TreeDefinition.builder("debug_old_oak")
                .height(28, 42)
                .trunkWidth(4, 6)
                .branchDensity(0.90f)
                .branchLength(12, 20)
                .leafDensity(0.75f)
                .rootChance(1.0f)
                .canLean(true)
                .canSplitTrunk(true)
                .maxRecursionDepth(4)
                .growthStyle(GrowthStyle.ANCIENT_OAK)
                .leafShape(LeafShape.LAYERED)
                .heightVariation(0.30f)
                .branchVariation(0.35f)
                .canopyVariation(0.40f)
                .build();
    }
}
