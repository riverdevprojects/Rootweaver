package com.example.examplemod.strategy;

import com.example.examplemod.api.definition.GrowthStyle;
import java.util.EnumMap;
import java.util.Map;

public final class StrategyRegistry {
    private static final Map<GrowthStyle, GrowthStrategy> STRATEGIES = new EnumMap<>(GrowthStyle.class);
    static {
        STRATEGIES.put(GrowthStyle.OAK, new OakGrowthStrategy());
        STRATEGIES.put(GrowthStyle.PINE, new PineGrowthStrategy());
        STRATEGIES.put(GrowthStyle.WILLOW, new OakGrowthStrategy());
        STRATEGIES.put(GrowthStyle.DEAD_TREE, new PineGrowthStrategy());
        STRATEGIES.put(GrowthStyle.GIANT_FOREST, new OakGrowthStrategy());
        STRATEGIES.put(GrowthStyle.CUSTOM, new OakGrowthStrategy());
        STRATEGIES.put(GrowthStyle.ANCIENT_OAK, new OakGrowthStrategy());
        STRATEGIES.put(GrowthStyle.BIRCH, new OakGrowthStrategy()); // bypassed — BirchGenerator handles BIRCH directly
    }
    public static GrowthStrategy resolve(GrowthStyle style) { return STRATEGIES.get(style); }
}
