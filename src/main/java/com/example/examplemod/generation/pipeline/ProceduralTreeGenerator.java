package com.example.examplemod.generation.pipeline;

import com.example.examplemod.api.definition.GrowthStyle;
import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;
import com.example.examplemod.generation.terrain.TerrainAdapter;
import com.example.examplemod.strategy.GrowthStrategy;
import com.example.examplemod.strategy.StrategyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ProceduralTreeGenerator {
    private static final boolean DEBUG_STRUCTURE = Boolean.getBoolean("arborforge.debugStructure");

    public boolean generate(TreeDefinition definition, GenerationContext ctx) {
        if (definition.growthStyle() == GrowthStyle.ANCIENT_OAK) {
            return new AncientOakGenerator().generate(definition, ctx);
        }
        LevelAccessor level = ctx.level();
        BlockPos base = TerrainAdapter.findSurface(level, ctx.origin());
        GrowthStrategy strategy = StrategyRegistry.resolve(definition.growthStyle());
        GrowthStrategy.GrowthProfile profile = strategy.profile(definition, ctx);

        TreeGraph graph = generateStructure(base, definition, profile, ctx.random());
        voxelizeWood(level, graph);
        placeLeaves(level, definition, graph, ctx.random());
        if (DEBUG_STRUCTURE) {
            debugStructure(level, graph);
        }
        return true;
    }

    private TreeGraph generateStructure(BlockPos base, TreeDefinition def, GrowthStrategy.GrowthProfile profile, RandomSource random) {
        Vec3 position = new Vec3(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        Vec3 direction = new Vec3((random.nextDouble() - 0.5D) * 0.04D, 1.0D, (random.nextDouble() - 0.5D) * 0.04D).normalize();

        List<TreeNode> trunk = new ArrayList<>();
        List<List<TreeNode>> branches = new ArrayList<>();
        List<Vec3> leafAnchors = new ArrayList<>();

        for (int i = 0; i < profile.trunkSteps(); i++) {
            float t = i / (float) Math.max(1, profile.trunkSteps() - 1);
            float radius = Mth.lerp(1.0f - t, def.maxTrunkWidth() * 0.5f, Math.max(0.8f, def.minTrunkWidth() * 0.35f));
            TreeNode node = new TreeNode(position, direction, radius);
            trunk.add(node);

            if (i > 3 && t > 0.2f && random.nextFloat() < def.branchDensity() * (0.35f + t * profile.branchBias())) {
                int branchLen = Mth.nextInt(random, def.minBranchLength(), def.maxBranchLength());
                List<TreeNode> branch = growBranch(node, branchLen, 1, Math.min(def.maxRecursionDepth(), 5),
                        profile, def, random, branches, leafAnchors);
                if (!branch.isEmpty()) {
                    branches.add(branch);
                    leafAnchors.add(branch.get(branch.size() - 1).position());
                }
            }

            Vec3 jitter = new Vec3((random.nextDouble() - 0.5D) * profile.curvature(), 0.0D,
                    (random.nextDouble() - 0.5D) * profile.curvature());
            Vec3 lean = new Vec3(direction.x * 0.06D, 0.0D, direction.z * 0.06D);
            direction = direction.add(jitter).add(lean).add(0.0D, profile.upwardPull(), 0.0D).normalize();
            position = position.add(direction.scale(profile.stepSize()));
        }

        return new TreeGraph(trunk, branches, leafAnchors);
    }

    private List<TreeNode> growBranch(TreeNode start, int steps, int depth, int maxDepth, GrowthStrategy.GrowthProfile profile,
                                      TreeDefinition def, RandomSource random, List<List<TreeNode>> branches, List<Vec3> leafAnchors) {
        List<TreeNode> nodes = new ArrayList<>();
        Vec3 position = start.position();
        Vec3 outward = new Vec3(random.nextDouble() - 0.5D, 0.0D, random.nextDouble() - 0.5D).normalize();
        Vec3 direction = start.direction().scale(0.5D)
                .add(outward.scale(profile.branchDivergence()))
                .add(0.0D, 0.5D, 0.0D).normalize();

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.35f, start.radius() * (1.0f - t) * 0.65f);
            TreeNode node = new TreeNode(position, direction, radius);
            nodes.add(node);

            if (depth < maxDepth && i > 1 && random.nextFloat() < def.branchDensity() * 0.22f / (depth + 1)) {
                int subSteps = Math.max(2, Mth.nextInt(random, def.minBranchLength() / 2, Math.max(2, def.maxBranchLength() - 1)));
                List<TreeNode> sub = growBranch(node, subSteps, depth + 1, maxDepth, profile, def, random, branches, leafAnchors);
                if (!sub.isEmpty()) {
                    branches.add(sub);
                    leafAnchors.add(sub.get(sub.size() - 1).position());
                }
            }

            Vec3 curve = new Vec3((random.nextDouble() - 0.5D) * profile.curvature() * 1.5D, 0.0D,
                    (random.nextDouble() - 0.5D) * profile.curvature() * 1.5D);
            direction = direction.add(curve).add(0.0D, 0.16D, 0.0D).normalize();
            position = position.add(direction.scale(0.8D));
        }

        return nodes;
    }

    private void voxelizeWood(LevelAccessor level, TreeGraph graph) {
        for (TreeNode node : graph.trunkPath()) {
            paintSphere(level, node.position(), node.radius(), Blocks.OAK_LOG.defaultBlockState());
        }
        for (List<TreeNode> branch : graph.branches()) {
            for (TreeNode node : branch) {
                paintSphere(level, node.position(), node.radius(), Blocks.OAK_LOG.defaultBlockState());
            }
        }
    }

    private void placeLeaves(LevelAccessor level, TreeDefinition def, TreeGraph graph, RandomSource random) {
        for (Vec3 anchor : graph.leafAnchors()) {
            float density = Mth.clamp(def.leafDensity(), 0.2f, 1.0f);
            int rx = Math.max(2, Math.round(2 + def.maxBranchLength() * 0.35f * density));
            int ry = Math.max(2, Math.round(rx * (0.7f + random.nextFloat() * 0.5f)));
            int rz = Math.max(2, Math.round(2 + def.maxBranchLength() * 0.35f * density));
            paintEllipsoid(level, anchor, rx, ry, rz, Blocks.OAK_LEAVES.defaultBlockState(), random, density);
        }
    }

    private void debugStructure(LevelAccessor level, TreeGraph graph) {
        for (TreeNode node : graph.trunkPath()) {
            setIfAir(level, BlockPos.containing(node.position()), Blocks.GLOWSTONE.defaultBlockState());
        }
        for (List<TreeNode> branch : graph.branches()) {
            for (TreeNode node : branch) {
                setIfAir(level, BlockPos.containing(node.position()), Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
        for (Vec3 anchor : graph.leafAnchors()) {
            setIfAir(level, BlockPos.containing(anchor), Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    private void paintSphere(LevelAccessor level, Vec3 center, float radius, BlockState state) {
        int r = Math.max(1, Mth.ceil(radius));
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if ((dx * dx + dy * dy + dz * dz) <= radius * radius + 0.5f) {
                BlockPos p = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);
                setIfAirOrLeaves(level, p, state);
            }
        }
    }

    private void paintEllipsoid(LevelAccessor level, Vec3 center, int rx, int ry, int rz, BlockState leaves, RandomSource random, float density) {
        for (int dx = -rx; dx <= rx; dx++) for (int dy = -ry; dy <= ry; dy++) for (int dz = -rz; dz <= rz; dz++) {
            double nx = dx / (double) rx;
            double ny = dy / (double) ry;
            double nz = dz / (double) rz;
            if (nx * nx + ny * ny + nz * nz <= 1.0D && random.nextFloat() <= density) {
                BlockPos p = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);
                setIfAir(level, p, leaves);
            }
        }
    }

    private void setIfAir(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, 3);
        }
    }

    private void setIfAirOrLeaves(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(Blocks.OAK_LEAVES)) {
            level.setBlock(pos, state, 3);
        }
    }

    private record TreeNode(Vec3 position, Vec3 direction, float radius) {}

    private record TreeGraph(List<TreeNode> trunkPath, List<List<TreeNode>> branches, List<Vec3> leafAnchors) {}
}
