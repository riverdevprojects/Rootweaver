package com.example.examplemod.api.definition;

public record ForestDefinition(
        String id,
        float density,
        float clustering,
        float clearingChance,
        float giantTreeChance,
        float youngTreeChance
) {
    public static ForestDefinition of(String id, float density, float clustering, float clearingChance, float giantTreeChance, float youngTreeChance) {
        return new ForestDefinition(id, density, clustering, clearingChance, giantTreeChance, youngTreeChance);
    }
}
