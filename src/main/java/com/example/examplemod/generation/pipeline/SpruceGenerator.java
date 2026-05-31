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
 * Procedural spruce generator — cold-climate conifer, dense and layered.
 *
 * Design contract:
 *  - Near-vertical trunk: straight pillar, no dramatic bends, gradual taper only
 *  - Whorl-based branching: distinct elevation tiers radiating from trunk at golden-angle offsets
 *  - Branch length decreases linearly bottom→top — this is what creates the triangular silhouette
 *  - Branches angle slightly downward, sag gently under foliage weight
 *  - Foliage painted as flat horizontal discs per whorl, hugging branch geometry not floating blobs
 *  - Strong central leader terminates in a narrow elongated apex spike
 *  - Lower trunk (bottom 15%) left bare for visual weight at ground level
 */
public final class SpruceGenerator {

    private BlockState wood;
    private BlockState leaves;

    private record Node(Vec3 pos, Vec3 dir, float radius) {}

    /** Per-whorl data used to paint foliage after branch geometry is placed. */
    private record Whorl(double centerY, double reach, Vec3 trunkPos) {}

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public boolean generate(TreeDefinition def, GenerationContext ctx) {
        wood   = Blocks.SPRUCE_LOG.defaultBlockState();
        leaves = Blocks.SPRUCE_LEAVES.defaultBlockState();

        LevelAccessor level = ctx.level();
        BlockPos base = TerrainAdapter.findSurface(level, ctx.origin());
        RandomSource rand = ctx.random();

        List<Node> trunk = growTrunk(base, def, rand);
        List<List<Node>> allBranches = new ArrayList<>();
        List<Whorl> whorls = new ArrayList<>();

        growWhorlBranches(trunk, def, rand, allBranches, whorls);

        paintTrunk(level, trunk);
        for (List<Node> branch : allBranches) {
            paintBranch(level, branch);
        }

        paintWhorlFoliage(level, whorls, def, rand);
        paintApex(level, trunk, rand);

        return true;
    }

    // -----------------------------------------------------------------------
    // Trunk — near-vertical pillar, smooth gradual taper, no flare
    // -----------------------------------------------------------------------

    private List<Node> growTrunk(BlockPos base, TreeDefinition def, RandomSource rand) {
        List<Node> nodes = new ArrayList<>();
        int height = Mth.nextInt(rand, def.minHeight(), def.maxHeight());
        int steps  = height + 10;
        double step = (double) height / steps;

        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);

        // Minimal lean — spruce grows efficiently vertical
        double leanAngle    = rand.nextDouble() * 2 * Math.PI;
        double leanStrength = 0.001 + rand.nextDouble() * 0.004;
        Vec3 dir = new Vec3(
                Math.cos(leanAngle) * leanStrength,
                1.0,
                Math.sin(leanAngle) * leanStrength).normalize();

        double accX = 0, accZ = 0;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            // Smooth taper: thick base narrowing to a tight leader at the top
            float radius = Mth.lerp(t, def.maxTrunkWidth() * 0.55f, 0.52f);
            nodes.add(new Node(pos, dir, radius));

            // Near-zero random walk — spruce is rigid
            accX = accX * 0.86 + (rand.nextDouble() - 0.5) * 0.010;
            accZ = accZ * 0.86 + (rand.nextDouble() - 0.5) * 0.010;
            dir = dir.add(accX, 0, accZ).add(0, 0.20, 0).normalize();
            pos = pos.add(dir.scale(step));
        }
        return nodes;
    }

    // -----------------------------------------------------------------------
    // Whorl branching — tiered layers, shorter at the top, longer at the bottom
    // -----------------------------------------------------------------------

    private void growWhorlBranches(List<Node> trunk, TreeDefinition def, RandomSource rand,
                                    List<List<Node>> allBranches, List<Whorl> whorls) {
        int trunkSize = trunk.size();

        // Branches emerge from 15% to 90% of trunk — lower trunk bare for visual grounding
        float whorlStart = 0.15f;
        float whorlEnd   = 0.90f;

        // More whorls = denser, more layered appearance
        int whorlCount = 9 + (int)(def.branchDensity() * 7);

        for (int w = 0; w < whorlCount; w++) {
            float tSpawn = whorlStart + (whorlEnd - whorlStart) * w / (float)(whorlCount - 1);
            int spawnIdx = Math.min(trunkSize - 2, (int)(tSpawn * trunkSize));
            Node spawnNode = trunk.get(spawnIdx);

            // whorlT=0 at the bottom whorl, whorlT=1 at the top whorl
            float whorlT = (float) w / (whorlCount - 1);

            // Branch count per whorl: 5-6 at base, 3-4 at top — gives denser lower silhouette
            int branchesInWhorl = Math.max(3, 6 - (int)(whorlT * 2.5f) + rand.nextInt(2));

            // Branch length: long at base, short at top — this is the key to the triangular profile
            float lengthScale = 1.0f - whorlT * 0.74f; // 1.0 at bottom whorl, 0.26 at top
            int baseBranchLen = def.minBranchLength()
                    + rand.nextInt(Math.max(1, def.maxBranchLength() - def.minBranchLength()));
            int branchSteps = Math.max(3, (int)(baseBranchLen * lengthScale));

            // Golden-angle rotation per whorl so no two whorls align — looks natural
            double whorlAngleOffset = w * 2.3999 + (rand.nextDouble() - 0.5) * 0.15;

            double maxReach = 0;
            for (int b = 0; b < branchesInWhorl; b++) {
                double angle = whorlAngleOffset + (2 * Math.PI * b / branchesInWhorl);
                angle += (rand.nextDouble() - 0.5) * 0.28;

                // Lower branches angle downward; upper branches nearly level; creates droop under snow load
                double elevDeg = -6.0 - (1.0f - whorlT) * 10.0 + rand.nextDouble() * 5.0;
                double elevRad = Math.toRadians(elevDeg);

                Vec3 outDir  = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 initDir = outDir.scale(Math.cos(Math.abs(elevRad)))
                        .add(0, Math.sin(elevRad), 0).normalize();

                float branchRadius = Math.max(0.22f, spawnNode.radius() * (0.22f + rand.nextFloat() * 0.14f));

                List<Node> branch = growBranch(spawnNode.pos(), initDir, branchSteps, branchRadius, rand);
                if (!branch.isEmpty()) {
                    allBranches.add(branch);
                    // Track horizontal reach of this branch for foliage disc sizing
                    Node tip = branch.get(branch.size() - 1);
                    double dx = tip.pos().x - spawnNode.pos().x;
                    double dz = tip.pos().z - spawnNode.pos().z;
                    maxReach = Math.max(maxReach, Math.sqrt(dx * dx + dz * dz));
                }
            }

            // Record whorl for layered foliage painting
            if (maxReach > 1.5) {
                whorls.add(new Whorl(spawnNode.pos().y, maxReach, spawnNode.pos()));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Branch growth — slight downward sag, orderly not chaotic
    // -----------------------------------------------------------------------

    private List<Node> growBranch(Vec3 startPos, Vec3 startDir, int steps,
                                   float startRadius, RandomSource rand) {
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        Vec3 dir = startDir;
        double accX = 0, accZ = 0;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.16f, startRadius * (1.0f - t * 0.78f));
            nodes.add(new Node(pos, dir, radius));

            // Gradual sag under foliage weight — increases toward branch tip
            double sag = -0.006 - t * 0.018;
            accX = accX * 0.84 + (rand.nextDouble() - 0.5) * 0.038;
            accZ = accZ * 0.84 + (rand.nextDouble() - 0.5) * 0.038;
            dir = dir.add(accX, sag, accZ).normalize();
            pos = pos.add(dir.scale(0.82));
        }
        return nodes;
    }

    // -----------------------------------------------------------------------
    // Foliage — flat horizontal discs per whorl, preserving branch silhouette
    // -----------------------------------------------------------------------

    /**
     * Paints foliage as thin horizontal slabs at each whorl elevation.
     * Radius of each disc matches the branch reach at that tier — the conical
     * silhouette emerges purely from the shrinking disc radii, not from leaf blobs.
     */
    private void paintWhorlFoliage(LevelAccessor level, List<Whorl> whorls,
                                    TreeDefinition def, RandomSource rand) {
        float density = Mth.clamp(def.leafDensity(), 0.62f, 0.84f);

        for (int wi = 0; wi < whorls.size(); wi++) {
            Whorl whorl = whorls.get(wi);
            double cx = whorl.trunkPos().x;
            double cz = whorl.trunkPos().z;
            double reach = whorl.reach();

            // Foliage disc: 2 layers thick vertically, full reach horizontally
            // Upper layer slightly smaller for a beveled edge rather than a flat shelf
            for (int dy = 0; dy <= 1; dy++) {
                double layerRadius = dy == 0 ? reach : reach * 0.72;
                int r = (int) Math.ceil(layerRadius);
                int y = (int) Math.round(whorl.centerY()) + dy;

                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist > layerRadius) continue;

                        // Skip the inner trunk zone — branch wood already fills that space
                        double innerClear = 0.8;
                        if (dist < innerClear) continue;

                        // Soft density falloff toward the disc edge for a natural fringe
                        double edgeFade = 1.0 - Math.max(0, dist - layerRadius * 0.65) / (layerRadius * 0.35 + 0.01);
                        // Slight density randomness within the disc for texture
                        float placeDensity = (float)(density * edgeFade * (0.80 + rand.nextDouble() * 0.20));

                        if (rand.nextFloat() < placeDensity) {
                            BlockPos p = BlockPos.containing(cx + dx, y, cz + dz);
                            if (level.getBlockState(p).isAir()) {
                                level.setBlock(p, leaves, 3);
                            }
                        }
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Apex — narrow elongated terminal spike, the iconic spruce crown tip
    // -----------------------------------------------------------------------

    private void paintApex(LevelAccessor level, List<Node> trunk, RandomSource rand) {
        int trunkSize = trunk.size();
        Node tip = trunk.get(trunkSize - 1);
        double cx = tip.pos().x;
        double cy = tip.pos().y;
        double cz = tip.pos().z;

        // Paint a narrow elongated spike: radius widens slightly a few blocks below the tip
        // then tightens back to a single point — the classic spruce leader
        int[] radii = {1, 1, 2, 2, 1, 1, 0}; // from tip downward
        for (int i = 0; i < radii.length; i++) {
            int r = radii[i];
            int y = (int) Math.round(cy) - i;
            float apexDensity = 0.88f - i * 0.06f;

            if (r == 0) {
                // Single tip block
                BlockPos p = BlockPos.containing(cx, y, cz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, leaves, 3);
                }
                continue;
            }

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist <= r && rand.nextFloat() < apexDensity) {
                        BlockPos p = BlockPos.containing(cx + dx, y, cz + dz);
                        if (level.getBlockState(p).isAir()) {
                            level.setBlock(p, leaves, 3);
                        }
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Painting utilities
    // -----------------------------------------------------------------------

    private void paintTrunk(LevelAccessor level, List<Node> trunk) {
        for (Node n : trunk) {
            paintSphere(level, n.pos(), n.radius(), wood);
        }
    }

    private void paintBranch(LevelAccessor level, List<Node> branch) {
        for (Node n : branch) {
            if (n.radius() >= 0.28f) {
                paintSphere(level, n.pos(), n.radius(), wood);
            } else {
                setIfAirOrWood(level, BlockPos.containing(n.pos()), wood);
            }
        }
    }

    private void paintSphere(LevelAccessor level, Vec3 center, float radius, BlockState state) {
        int r = Math.max(1, Mth.ceil(radius));
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy + dz * dz <= radius * radius + 0.5f) {
                setIfAirOrWood(level, BlockPos.containing(center.x + dx, center.y + dy, center.z + dz), state);
            }
        }
    }

    private void setIfAirOrWood(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.getBlock() == leaves.getBlock()) {
            level.setBlock(pos, state, 3);
        }
    }
}
