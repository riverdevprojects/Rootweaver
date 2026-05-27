package com.example.examplemod.api.definition;

import java.util.List;
import java.util.Objects;

public record TreeDefinition(
        String id,
        int minHeight,
        int maxHeight,
        int minTrunkWidth,
        int maxTrunkWidth,
        float branchDensity,
        int minBranchLength,
        int maxBranchLength,
        float leafDensity,
        float rootChance,
        boolean canLean,
        boolean canSplitTrunk,
        int maxRecursionDepth,
        GrowthStyle growthStyle,
        LeafShape leafShape,
        float heightVariation,
        float branchVariation,
        float canopyVariation,
        List<String> decorators
) {
    public TreeDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(growthStyle);
        Objects.requireNonNull(leafShape);
        decorators = List.copyOf(decorators);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private int minHeight = 8;
        private int maxHeight = 16;
        private int minTrunkWidth = 1;
        private int maxTrunkWidth = 2;
        private float branchDensity = 0.6f;
        private int minBranchLength = 3;
        private int maxBranchLength = 6;
        private float leafDensity = 0.7f;
        private float rootChance = 0.2f;
        private boolean canLean = true;
        private boolean canSplitTrunk = false;
        private int maxRecursionDepth = 3;
        private GrowthStyle growthStyle = GrowthStyle.OAK;
        private LeafShape leafShape = LeafShape.SPHERICAL;
        private float heightVariation = 0.2f;
        private float branchVariation = 0.2f;
        private float canopyVariation = 0.2f;
        private List<String> decorators = List.of();

        private Builder(String id) { this.id = id; }
        public Builder height(int min, int max) { this.minHeight=min; this.maxHeight=max; return this; }
        public Builder trunkWidth(int min, int max) { this.minTrunkWidth=min; this.maxTrunkWidth=max; return this; }
        public Builder branchDensity(float v) { this.branchDensity=v; return this; }
        public Builder branchLength(int min, int max) { this.minBranchLength=min; this.maxBranchLength=max; return this; }
        public Builder leafDensity(float v) { this.leafDensity=v; return this; }
        public Builder rootChance(float v) { this.rootChance=v; return this; }
        public Builder canLean(boolean v) { this.canLean=v; return this; }
        public Builder canSplitTrunk(boolean v) { this.canSplitTrunk=v; return this; }
        public Builder maxRecursionDepth(int v) { this.maxRecursionDepth=v; return this; }
        public Builder growthStyle(GrowthStyle v) { this.growthStyle=v; return this; }
        public Builder leafShape(LeafShape v) { this.leafShape=v; return this; }
        public Builder heightVariation(float v) { this.heightVariation=v; return this; }
        public Builder branchVariation(float v) { this.branchVariation=v; return this; }
        public Builder canopyVariation(float v) { this.canopyVariation=v; return this; }
        public Builder decorators(List<String> v) { this.decorators=v; return this; }
        public TreeDefinition build() {
            return new TreeDefinition(id,minHeight,maxHeight,minTrunkWidth,maxTrunkWidth,branchDensity,minBranchLength,maxBranchLength,
                    leafDensity,rootChance,canLean,canSplitTrunk,maxRecursionDepth,growthStyle,leafShape,heightVariation,branchVariation,
                    canopyVariation,decorators);
        }
    }
}
