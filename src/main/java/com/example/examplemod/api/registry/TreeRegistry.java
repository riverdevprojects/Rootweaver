package com.example.examplemod.api.registry;

import com.example.examplemod.api.definition.TreeDefinition;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class TreeRegistry {
    private static final Map<String, TreeDefinition> TREES = new ConcurrentHashMap<>();
    private TreeRegistry() {}

    public static void register(TreeDefinition definition) {
        TREES.put(definition.id(), definition);
    }

    public static Optional<TreeDefinition> get(String id) {
        return Optional.ofNullable(TREES.get(id));
    }

    public static Collection<TreeDefinition> all() {
        return TREES.values();
    }
}
