package com.example.examplemod.api.debug;

import com.example.examplemod.api.preset.ArborPresets;
import com.example.examplemod.api.registry.TreeRegistry;

public final class DebugTreePresets {
    private DebugTreePresets() {}

    public static void bootstrap() {
        TreeRegistry.register(ArborPresets.oak("debug_oak"));
        TreeRegistry.register(ArborPresets.pine("debug_pine"));
        TreeRegistry.register(ArborPresets.giant("debug_giant"));
    }
}
