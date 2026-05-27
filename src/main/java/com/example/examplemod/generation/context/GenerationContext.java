package com.example.examplemod.generation.context;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;

public record GenerationContext(LevelAccessor level, BlockPos origin, RandomSource random, long seed) {}
