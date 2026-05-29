package com.example.examplemod.generation.pipeline;

import com.example.examplemod.api.definition.TreeDefinition;
import com.example.examplemod.generation.context.GenerationContext;
import com.example.examplemod.generation.terrain.TerrainAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural birch generator — a completely distinct visual species from oak.
 *
 * Design contract:
 *  - Slender trunk visible through the canopy (tapers to a single block above ~40% height)
 *  - Branches ONLY from upper 55-88% of trunk, angled steeply upward (30-65°)
 *  - Shallow 2-level hierarchy (primary → secondary) — no heavy limb mass
 *  - Many small leaf clusters at low density — light-filtering, gap-rich canopy
 *  - No roots, no flare, no sag arcs
 */
public final class BirchGenerator {

    private BlockState wood;
    private BlockState leaves;

    private record Node(Vec3 pos, Vec3 dir, float radius) {}

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public boolean generate(TreeDefinition def, GenerationContext ctx) {
        wood   = Blocks.BIRCH_LOG.defaultBlockState();
        leaves = Blocks.BIRCH_LEAVES.defaultBlockState();

        LevelAccessor level = ctx.level();
        BlockPos base = TerrainAdapter.findSurface(level, ctx.origin());
        RandomSource rand = ctx.random();

        double leanAngle    = rand.nextDouble() * 2 * Math.PI;
        double leanStrength = 0.006 + rand.nextDouble() * 0.018;

        List<Node> trunk = growTrunk(base, def, rand, leanAngle, leanStrength);
        List<List<Node>> allBranches = new ArrayList<>();
        List<Vec3> leafAnchors = new ArrayList<>();

        growAllBranches(trunk, def, rand, allBranches, leafAnchors);

        paintTrunk(level, trunk);
        for (List<Node> branch : allBranches) {
            for (Node n : branch) {
                if (n.radius() >= 0.25f) {
                    paintSphere(level, n.pos(), n.radius(), wood);
                } else {
                    setIfAirOrLeaves(level, BlockPos.containing(n.pos()), wood);
                }
            }
        }

        placeLeafClusters(level, leafAnchors, def, rand);
        return true;
    }

    // -----------------------------------------------------------------------
    // Trunk — thin, upright, gentle S-curve energy
    // -----------------------------------------------------------------------

    private List<Node> growTrunk(BlockPos base, TreeDefinition def, RandomSource rand,
                                  double leanAngle, double leanStrength) {
        List<Node> nodes = new ArrayList<>();
        int height = Mth.nextInt(rand, def.minHeight(), def.maxHeight());
        // Extra steps for smoother curve resolution at birch's graceful scale
        int steps  = height + 4;
        double step = (double) height / steps;

        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        Vec3 dir = new Vec3(
                Math.cos(leanAngle) * leanStrength,
                1.0,
                Math.sin(leanAngle) * leanStrength).normalize();

        double accX = 0, accZ = 0;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);

            // Keep radius below the paintSphere threshold (sqrt(0.5) ≈ 0.707) at all heights
            // so the trunk is a uniform single-block column from ground to crown — no chunky base.
            float radius = Mth.lerp(t, 0.62f, 0.38f);

            nodes.add(new Node(pos, dir, radius));

            // Tiny organic drift — maintains upright energy while avoiding rigidity
            accX = accX * 0.86 + (rand.nextDouble() - 0.5) * 0.036;
            accZ = accZ * 0.86 + (rand.nextDouble() - 0.5) * 0.036;

            // Strong upward pull keeps the trunk nearly vertical
            dir = dir.add(accX, 0, accZ).add(0, 0.20, 0).normalize();
            pos = pos.add(dir.scale(step));
        }

        return nodes;
    }

    // -----------------------------------------------------------------------
    // Branch distribution — upper-trunk only, steep upward angle
    // -----------------------------------------------------------------------

    private void growAllBranches(List<Node> trunk, TreeDefinition def, RandomSource rand,
                                  List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int trunkSize    = trunk.size();
        // 3-6 primaries — more than oak but each is far lighter
        int primaryCount = 3 + rand.nextInt((int)(def.branchDensity() * 4) + 1);

        for (int b = 0; b < primaryCount; b++) {
            double angle = (2 * Math.PI * b / primaryCount)
                    + (rand.nextDouble() - 0.5) * (Math.PI / primaryCount);

            // Branches only from upper 55-88% — lower trunk remains bare and visible
            float tSpawn  = 0.55f + rand.nextFloat() * 0.33f;
            int spawnIdx  = Math.min(trunkSize - 2, (int)(tSpawn * trunkSize));
            Node spawn    = trunk.get(spawnIdx);

            int primarySteps = def.minBranchLength()
                    + rand.nextInt(Math.max(1, def.maxBranchLength() - def.minBranchLength()));

            // Steep upward angle: 30-65° (oak uses 17-40°) — creates tall narrow crown
            double elevAngle = Math.toRadians(30.0 + rand.nextDouble() * 35.0);
            Vec3 outDir  = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 initDir = outDir.scale(Math.cos(elevAngle))
                    .add(0, Math.sin(elevAngle), 0).normalize();

            // Thin delicate radius — birch primaries are fine branches, not limbs
            float primaryRadius = Math.max(0.22f,
                    spawn.radius() * (0.38f + rand.nextFloat() * 0.14f));

            List<Node> primary = growPrimary(spawn.pos(), initDir, primarySteps,
                    primaryRadius, def, rand, allBranches, leafAnchors);

            if (!primary.isEmpty()) allBranches.add(primary);
        }
    }

    // -----------------------------------------------------------------------
    // Primary branch  (depth 1)
    // -----------------------------------------------------------------------

    private List<Node> growPrimary(Vec3 startPos, Vec3 startDir, int steps,
                                    float startRadius, TreeDefinition def, RandomSource rand,
                                    List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        Vec3 dir = startDir;
        double accX = 0, accZ = 0;

        // 1-2 secondaries per primary — shallower tree than oak's 2-4
        int secCount = 1 + rand.nextInt(2);
        int[] secPositions = pickAttachPoints(steps, secCount, 0.35f, 0.82f, rand);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            // Aggressive taper — birch branches thin out quickly, emphasising delicacy
            float radius = Math.max(0.18f, startRadius * (1.0f - t * 0.75f));
            nodes.add(new Node(pos, dir, radius));

            for (int sp : secPositions) {
                if (sp == i) {
                    float secRadius = Math.max(0.15f, radius * (0.40f + rand.nextFloat() * 0.15f));
                    growSecondary(pos, dir, secRadius, rand, allBranches, leafAnchors);
                }
            }

            accX = accX * 0.82 + (rand.nextDouble() - 0.5) * 0.07;
            accZ = accZ * 0.82 + (rand.nextDouble() - 0.5) * 0.07;
            // Very gentle continuing upward arc — no heavy sag like oak
            double upCurve = 0.018 + t * 0.025;
            dir = dir.add(accX, upCurve, accZ).normalize();
            pos = pos.add(dir.scale(0.82));
        }

        // Primary tip is a leaf anchor — one of many small scatter points
        if (!nodes.isEmpty()) {
            leafAnchors.add(nodes.get(nodes.size() - 1).pos());
        }

        return nodes;
    }

    // -----------------------------------------------------------------------
    // Secondary branch  (depth 2) — the main leaf-anchor producers
    // -----------------------------------------------------------------------

    private void growSecondary(Vec3 startPos, Vec3 parentDir, float startRadius,
                                RandomSource rand, List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int steps = 3 + rand.nextInt(4);
        // High up-weight: secondary branches reach toward light — more vertical than oak secondary
        Vec3 dir = divergeDir(parentDir, 0.22, 0.48, 0.72, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.12f, startRadius * (1.0f - t * 0.72f));
            nodes.add(new Node(pos, dir, radius));

            Vec3 jitter = new Vec3(
                    (rand.nextDouble() - 0.5) * 0.10,
                    0.05 + rand.nextDouble() * 0.06,
                    (rand.nextDouble() - 0.5) * 0.10);
            dir = dir.add(jitter).normalize();
            pos = pos.add(dir.scale(0.68));
        }

        if (!nodes.isEmpty()) {
            allBranches.add(nodes);
            leafAnchors.add(nodes.get(nodes.size() - 1).pos());
        }
    }

    // -----------------------------------------------------------------------
    // Painting
    // -----------------------------------------------------------------------

    private void paintTrunk(LevelAccessor level, List<Node> trunk) {
        for (Node n : trunk) {
            paintSphere(level, n.pos(), n.radius(), wood);
        }
    }

    /**
     * Each anchor gets a small tight cluster and an optional satellite.
     * The birch "airy" quality emerges from cluster separation, not internal density:
     * many small masses spread through the crown leave natural sky-gaps between them.
     */
    private void placeLeafClusters(LevelAccessor level, List<Vec3> anchors,
                                    TreeDefinition def, RandomSource rand) {
        // Hard ceiling on density — even a "dense" birch definition stays see-through
        float density = Mth.clamp(def.leafDensity(), 0.42f, 0.62f);

        for (Vec3 anchor : anchors) {
            // Small primary cluster — radius 2-3, slightly flattened vertically
            int rx = 2 + rand.nextInt(2);
            int ry = 1 + rand.nextInt(2);
            int rz = 2 + rand.nextInt(2);
            paintEllipsoid(level, anchor, rx, ry, rz, rand, density);

            // Optional small satellite in a random nearby direction
            if (rand.nextFloat() < 0.48f) {
                Vec3 offset = new Vec3(
                        (rand.nextDouble() - 0.5) * 2.0,
                        (rand.nextDouble() - 0.5) * 1.2,
                        (rand.nextDouble() - 0.5) * 2.0);
                int sr = 1 + rand.nextInt(2);
                paintEllipsoid(level, anchor.add(offset), sr, sr, sr, rand, density * 0.55f);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private int[] pickAttachPoints(int steps, int count, float minT, float maxT, RandomSource rand) {
        int[] pts = new int[count];
        float span = maxT - minT;
        for (int i = 0; i < count; i++) {
            float t = minT + (span * i / Math.max(1, count - 1))
                    + (rand.nextFloat() - 0.5f) * (span / (count + 1));
            pts[i] = Mth.clamp((int)(t * steps), 1, steps - 2);
        }
        return pts;
    }

    private Vec3 divergeDir(Vec3 parent, double parentWeight, double randomWeight,
                              double upWeight, RandomSource rand) {
        Vec3 horiz = new Vec3(rand.nextDouble() - 0.5, 0, rand.nextDouble() - 0.5).normalize();
        return parent.scale(parentWeight)
                .add(horiz.scale(randomWeight))
                .add(0, upWeight, 0)
                .normalize();
    }

    private void paintSphere(LevelAccessor level, Vec3 center, float radius, BlockState state) {
        int r = Math.max(1, Mth.ceil(radius));
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy + dz * dz <= radius * radius + 0.5f) {
                setIfAirOrLeaves(level, BlockPos.containing(center.x + dx, center.y + dy, center.z + dz), state);
            }
        }
    }

    private void paintEllipsoid(LevelAccessor level, Vec3 center, int rx, int ry, int rz,
                                  RandomSource rand, float density) {
        for (int dx = -rx; dx <= rx; dx++) for (int dy = -ry; dy <= ry; dy++) for (int dz = -rz; dz <= rz; dz++) {
            double nx = dx / (double) rx;
            double ny = dy / (double) ry;
            double nz = dz / (double) rz;
            if (nx * nx + ny * ny + nz * nz <= 1.0 && rand.nextFloat() <= density) {
                BlockPos p = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, leaves, 3);
                }
            }
        }
    }

    private void setIfAirOrLeaves(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.getBlock() == leaves.getBlock()) {
            level.setBlock(pos, state, 3);
        }
    }
}
