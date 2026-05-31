package com.example.examplemod.api.debug;

import com.example.examplemod.api.definition.GrowthStyle;
import com.example.examplemod.api.definition.LeafShape;
import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.api.preset.ArborPresets;
import com.example.examplemod.api.registry.TreeRegistry;

public final class DebugTreePresets {
    private DebugTreePresets() {}

    public static void bootstrap() {
        TreeRegistry.register(buildCommonOak());
        TreeRegistry.register(ArborPresets.pine("debug_pine"));
        TreeRegistry.register(ArborPresets.giant("debug_giant"));
        TreeRegistry.register(buildLegendOak());
        TreeRegistry.register(buildLegendOldOak());
        TreeRegistry.register(buildBirch());
        TreeRegistry.register(buildRedwood());
        TreeRegistry.register(buildSpruce());
    }

    /**
     * Common rounded-canopy oak — compact workhorse tree, designed to cluster.
     * Spawn with: /arbor spawn debug_oak
     */
    private static TreeDefinition buildCommonOak() {
        return TreeDefinition.builder("debug_oak")
                .height(6, 8)
                .trunkWidth(1, 1)
                .branchDensity(0.0f)
                .branchLength(0, 2)
                .leafDensity(0.90f)
                .rootChance(0.0f)
                .canLean(true)
                .canSplitTrunk(false)
                .maxRecursionDepth(1)
                .growthStyle(GrowthStyle.COMMON_OAK)
                .leafShape(LeafShape.SPHERICAL)
                .heightVariation(0.25f)
                .branchVariation(0.0f)
                .canopyVariation(0.30f)
                .build();
    }

    /**
     * Legend-tier mature woodland oak — set-piece quality, placed at low density.
     * Spawn with: /arbor spawn debug_legend_oak
     */
    private static TreeDefinition buildLegendOak() {
        return TreeDefinition.builder("debug_legend_oak")
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
     * Slender woodland birch — elegant, vertical, airy.
     * Uses BirchGenerator via GrowthStyle.BIRCH.
     * Spawn with: /arbor spawn debug_birch
     */
    private static TreeDefinition buildBirch() {
        return TreeDefinition.builder("debug_birch")
                .height(12, 20)
                .trunkWidth(1, 2)
                .branchDensity(0.70f)
                .branchLength(4, 8)
                .leafDensity(0.55f)
                .rootChance(0.0f)
                .canLean(true)
                .canSplitTrunk(false)
                .maxRecursionDepth(2)
                .growthStyle(GrowthStyle.BIRCH)
                .leafShape(LeafShape.SPHERICAL)
                .heightVariation(0.25f)
                .branchVariation(0.30f)
                .canopyVariation(0.25f)
                .build();
    }

    /**
     * Towering coastal redwood — monumental, vertical, ancient.
     * Uses RedwoodGenerator via GrowthStyle.REDWOOD.
     * Spawn with: /arbor spawn debug_redwood
     */
    private static TreeDefinition buildRedwood() {
        return TreeDefinition.builder("debug_redwood")
                .height(40, 72)
                .trunkWidth(4, 7)
                .branchDensity(0.60f)
                .branchLength(8, 14)
                .leafDensity(0.65f)
                .rootChance(0.80f)
                .canLean(true)
                .canSplitTrunk(false)
                .maxRecursionDepth(3)
                .growthStyle(GrowthStyle.REDWOOD)
                .leafShape(LeafShape.OVAL)
                .heightVariation(0.35f)
                .branchVariation(0.25f)
                .canopyVariation(0.30f)
                .build();
    }

    /**
     * Cold-climate conifer — dense, layered, evergreen.
     * Uses SpruceGenerator via GrowthStyle.SPRUCE.
     * Triangular silhouette emerges from branch architecture (whorls that shorten toward crown).
     * Spawn with: /arbor spawn debug_spruce
     */
    private static TreeDefinition buildSpruce() {
        return TreeDefinition.builder("debug_spruce")
                .height(18, 30)
                .trunkWidth(1, 1)
                .branchDensity(0.72f)
                .branchLength(5, 10)
                .leafDensity(0.72f)
                .rootChance(0.0f)
                .canLean(false)
                .canSplitTrunk(false)
                .maxRecursionDepth(2)
                .growthStyle(GrowthStyle.SPRUCE)
                .leafShape(LeafShape.CONICAL)
                .heightVariation(0.20f)
                .branchVariation(0.22f)
                .canopyVariation(0.20f)
                .build();
    }

    /**
     * Legend-tier ancient sprawling oak — LOTR / old-growth set-piece.
     * Uses AncientOakGenerator via GrowthStyle.ANCIENT_OAK.
     * Spawn with: /arbor spawn debug_legend_old_oak
     */
    private static TreeDefinition buildLegendOldOak() {
        return TreeDefinition.builder("debug_legend_old_oak")
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
