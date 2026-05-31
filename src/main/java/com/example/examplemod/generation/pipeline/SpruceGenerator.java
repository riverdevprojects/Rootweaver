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
 *  - Thin single-block trunk: constant ~0.52 radius throughout, very slight taper
 *  - Whorl-based branching: distinct elevation tiers rotating by golden angle
 *  - Branch length decreases bottom→top — triangular silhouette from architecture alone
 *  - Branches angle slightly downward, sag gently; painted as thin sticks (hidden by foliage)
 *  - Foliage painted as 4-layer-thick horizontal discs per whorl — dense and full
 *  - Disc radius = branch reach + padding so foliage completely covers branch wood
 *  - Strong central leader terminates in narrow elongated apex spike
 *  - Lower trunk (bottom 15%) bare for ground-level visual weight
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

        // Foliage first so trunk/branch wood overwrites leaf blocks, keeping structure visible
        paintWhorlFoliage(level, whorls, def, rand);
        paintApex(level, trunk, rand);
        paintTrunk(level, trunk);
        for (List<Node> branch : allBranches) {
            paintBranch(level, branch);
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // Trunk — thin single-block column, near-perfectly vertical
    // -----------------------------------------------------------------------

    private List<Node> growTrunk(BlockPos base, TreeDefinition def, RandomSource rand) {
        List<Node> nodes = new ArrayList<>();
        int height = Mth.nextInt(rand, def.minHeight(), def.maxHeight());
        int steps  = height + 10;
        double step = (double) height / steps;

        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);

        double leanAngle    = rand.nextDouble() * 2 * Math.PI;
        double leanStrength = 0.001 + rand.nextDouble() * 0.003;
        Vec3 dir = new Vec3(
                Math.cos(leanAngle) * leanStrength,
                1.0,
                Math.sin(leanAngle) * leanStrength).normalize();

        double accX = 0, accZ = 0;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            // Thin single-block column — no trunkWidth scaling, just a slight taper
            float radius = Mth.lerp(t, 0.60f, 0.44f);
            nodes.add(new Node(pos, dir, radius));

            accX = accX * 0.86 + (rand.nextDouble() - 0.5) * 0.008;
            accZ = accZ * 0.86 + (rand.nextDouble() - 0.5) * 0.008;
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

        float whorlStart = 0.15f;
        float whorlEnd   = 0.90f;

        int whorlCount = 9 + (int)(def.branchDensity() * 7);

        for (int w = 0; w < whorlCount; w++) {
            float tSpawn = whorlStart + (whorlEnd - whorlStart) * w / (float)(whorlCount - 1);
            int spawnIdx = Math.min(trunkSize - 2, (int)(tSpawn * trunkSize));
            Node spawnNode = trunk.get(spawnIdx);

            float whorlT = (float) w / (whorlCount - 1);

            int branchesInWhorl = Math.max(3, 6 - (int)(whorlT * 2.5f) + rand.nextInt(2));

            // Branch length shrinks top→bottom to create the triangular profile
            float lengthScale = 1.0f - whorlT * 0.74f;
            int baseBranchLen = def.minBranchLength()
                    + rand.nextInt(Math.max(1, def.maxBranchLength() - def.minBranchLength()));
            int branchSteps = Math.max(3, (int)(baseBranchLen * lengthScale));

            double whorlAngleOffset = w * 2.3999 + (rand.nextDouble() - 0.5) * 0.15;

            double maxReach = 0;
            for (int b = 0; b < branchesInWhorl; b++) {
                double angle = whorlAngleOffset + (2 * Math.PI * b / branchesInWhorl);
                angle += (rand.nextDouble() - 0.5) * 0.28;

                double elevDeg = -5.0 - (1.0f - whorlT) * 12.0 + rand.nextDouble() * 5.0;
                double elevRad = Math.toRadians(elevDeg);

                Vec3 outDir  = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 initDir = outDir.scale(Math.cos(Math.abs(elevRad)))
                        .add(0, Math.sin(elevRad), 0).normalize();

                // Fixed thin radius — branches are sticks, foliage provides the volume
                float branchRadius = 0.18f + rand.nextFloat() * 0.06f;

                List<Node> branch = growBranch(spawnNode.pos(), initDir, branchSteps, branchRadius, rand);
                if (!branch.isEmpty()) {
                    allBranches.add(branch);
                    Node tip = branch.get(branch.size() - 1);
                    double dx = tip.pos().x - spawnNode.pos().x;
                    double dz = tip.pos().z - spawnNode.pos().z;
                    maxReach = Math.max(maxReach, Math.sqrt(dx * dx + dz * dz));
                }
            }

            if (maxReach > 1.0) {
                // Add padding beyond branch tips so foliage fully encloses the branch wood
                whorls.add(new Whorl(spawnNode.pos().y, maxReach + 1.2, spawnNode.pos()));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Branch growth — thin sticks, slight sag
    // -----------------------------------------------------------------------

    private List<Node> growBranch(Vec3 startPos, Vec3 startDir, int steps,
                                   float startRadius, RandomSource rand) {
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        Vec3 dir = startDir;
        double accX = 0, accZ = 0;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.14f, startRadius * (1.0f - t * 0.70f));
            nodes.add(new Node(pos, dir, radius));

            double sag = -0.005 - t * 0.016;
            accX = accX * 0.84 + (rand.nextDouble() - 0.5) * 0.036;
            accZ = accZ * 0.84 + (rand.nextDouble() - 0.5) * 0.036;
            dir = dir.add(accX, sag, accZ).normalize();
            pos = pos.add(dir.scale(0.82));
        }
        return nodes;
    }

    // -----------------------------------------------------------------------
    // Foliage — dense 4-layer horizontal discs, full coverage per whorl tier
    // -----------------------------------------------------------------------

    /**
     * Paints each whorl as a 4-layer-thick horizontal disc.
     * - Bottom layer (dy=-1): slight underside fringe, 80% radius
     * - Core layers (dy=0,1): full reach, maximum density — this is the main shelf
     * - Top layer (dy=2): tapered cap, 70% radius, slightly sparser
     * No inner clearance: foliage wraps trunk completely, trunk wood overwrites afterward.
     */
    private void paintWhorlFoliage(LevelAccessor level, List<Whorl> whorls,
                                    TreeDefinition def, RandomSource rand) {
        float baseDensity = Mth.clamp(def.leafDensity(), 0.70f, 0.88f);

        // Layer profile: {y-offset, radius-scale, density-scale}
        double[][] layers = {
            {-1, 0.72, 0.65},  // underside fringe
            { 0, 1.00, 1.00},  // main shelf level
            { 1, 0.92, 0.95},  // just above branch
            { 2, 0.62, 0.72},  // tapering top cap
        };

        for (Whorl whorl : whorls) {
            double cx = whorl.trunkPos().x;
            double cz = whorl.trunkPos().z;
            double reach = whorl.reach();
            int baseY = (int) Math.round(whorl.centerY());

            for (double[] layer : layers) {
                int y = baseY + (int) layer[0];
                double layerRadius = reach * layer[1];
                float layerDensity = (float)(baseDensity * layer[2]);
                int r = (int) Math.ceil(layerRadius);

                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist > layerRadius) continue;

                        // Soft edge fade: full density in inner 70%, fades to 0 at edge
                        double edgeFade = 1.0 - Math.max(0, dist - layerRadius * 0.70)
                                / (layerRadius * 0.30 + 0.01);
                        float placeDensity = (float)(layerDensity * edgeFade);

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
    // Apex — narrow elongated terminal spike, the iconic spruce crown
    // -----------------------------------------------------------------------

    private void paintApex(LevelAccessor level, List<Node> trunk, RandomSource rand) {
        int trunkSize = trunk.size();
        Node tip = trunk.get(trunkSize - 1);
        double cx = tip.pos().x;
        double cy = tip.pos().y;
        double cz = tip.pos().z;

        // Radius pattern from tip downward — widens then tapers, classic leader shape
        int[] radii = {0, 1, 1, 2, 2, 2, 1, 1};
        float[] densities = {1.0f, 0.92f, 0.90f, 0.85f, 0.82f, 0.78f, 0.72f, 0.65f};

        for (int i = 0; i < radii.length; i++) {
            int r = radii[i];
            int y = (int) Math.round(cy) - i;
            float apexDensity = densities[i];

            if (r == 0) {
                BlockPos p = BlockPos.containing(cx, y, cz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, leaves, 3);
                }
                continue;
            }

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= r && rand.nextFloat() < apexDensity) {
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
            // Branches are thin sticks — single-block path, no fat spheres
            setIfAirOrWood(level, BlockPos.containing(n.pos()), wood);
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
