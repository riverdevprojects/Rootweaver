package com.example.examplemod.command;

import com.example.examplemod.api.registry.TreeRegistry;
import com.example.examplemod.generation.context.GenerationContext;
import com.example.examplemod.generation.pipeline.ProceduralTreeGenerator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ArborCommand {
    private static final ProceduralTreeGenerator GENERATOR = new ProceduralTreeGenerator();

    private ArborCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arbor")
                .then(Commands.literal("spawn")
                        .then(Commands.argument("tree", StringArgumentType.word())
                                .executes(ctx -> {
                                    String treeId = StringArgumentType.getString(ctx, "tree");
                                    var source = ctx.getSource();
                                    var player = source.getPlayerOrException();
                                    HitResult hit = player.pick(30, 0, false);
                                    if (!(hit instanceof BlockHitResult bhr)) return 0;
                                    BlockPos pos = bhr.getBlockPos().above();
                                    ServerLevel level = source.getLevel();
                                    var def = TreeRegistry.get(treeId).orElse(null);
                                    if (def == null) {
                                        source.sendFailure(Component.literal("Unknown tree: " + treeId));
                                        return 0;
                                    }
                                    GENERATOR.generate(def, new GenerationContext(level, pos, level.random, level.getSeed()));
                                    source.sendSuccess(() -> Component.literal("Spawned tree " + treeId), true);
                                    return 1;
                                })))
                .then(Commands.literal("forest")
                        .then(Commands.argument("tree", StringArgumentType.word())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(4, 128))
                                        .executes(ctx -> {
                                            String treeId = StringArgumentType.getString(ctx, "tree");
                                            int radius = IntegerArgumentType.getInteger(ctx, "radius");
                                            var source = ctx.getSource();
                                            ServerLevel level = source.getLevel();
                                            var player = source.getPlayerOrException();
                                            BlockPos center = player.blockPosition();
                                            var def = TreeRegistry.get(treeId).orElse(null);
                                            if (def == null) return 0;
                                            int count = 0;
                                            for (int i = 0; i < radius; i++) {
                                                int dx = level.random.nextInt(radius * 2 + 1) - radius;
                                                int dz = level.random.nextInt(radius * 2 + 1) - radius;
                                                BlockPos p = center.offset(dx, 12, dz);
                                                GENERATOR.generate(def, new GenerationContext(level, p, level.random, level.getSeed()));
                                                count++;
                                            }
                                            final int generated = count;
                                            source.sendSuccess(() -> Component.literal("Generated forest count=" + generated), true);
                                            return generated;
                                        })))));
    }
}
