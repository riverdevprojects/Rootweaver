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
 * Procedural redwood generator — towering, vertical, monumental.
 *
 * Design contract:
 *  - Massive columnar trunk: 40-80+ blocks, very slow taper, heavy base flare
 *  - Branches concentrated in the upper 25-35% of the tree — lower trunk remains bare
 *  - Thick primary scaffolds emerge outward first, then rise or gently droop under foliage
 *  - 3-level hierarchy (primary → secondary → tertiary), layered and supportive
 *  - Dense cohesive crown core with irregular overlapping edge foliage
 *  - Broad ancient redwood crown — heavy, connected, and visible as one silhouette
 *  - Broad stabilizing roots, heavy and ancient
 */
public final class RedwoodGenerator {

    private BlockState wood;
    private BlockState leaves;

    private record Node(Vec3 pos, Vec3 dir, float radius) {}

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public boolean generate(TreeDefinition def, GenerationContext ctx) {
        wood   = Blocks.JUNGLE_LOG.defaultBlockState();
        leaves = Blocks.SPRUCE_LEAVES.defaultBlockState();

        LevelAccessor level = ctx.level();
        BlockPos base = TerrainAdapter.findSurface(level, ctx.origin());
        RandomSource rand = ctx.random();

        double leanAngle    = rand.nextDouble() * 2 * Math.PI;
        double leanStrength = 0.003 + rand.nextDouble() * 0.010;

        List<Node> trunk = growTrunk(base, def, rand, leanAngle, leanStrength);
        List<List<Node>> roots = growRoots(base, def, rand);
        List<List<Node>> allBranches = new ArrayList<>();
        List<Vec3> leafAnchors = new ArrayList<>();

        growAllBranches(trunk, def, rand, allBranches, leafAnchors);

        paintTrunk(level, trunk, rand);
        for (List<Node> root : roots) paintRoot(level, root);
        for (List<Node> branch : allBranches) {
            for (Node n : branch) {
                if (n.radius() >= 0.3f) {
                    paintSphere(level, n.pos(), n.radius(), wood);
                } else {
                    setIfAirOrWood(level, BlockPos.containing(n.pos()), wood);
                }
            }
        }

        placeLeafClusters(level, leafAnchors, def, rand);
        return true;
    }

    // -----------------------------------------------------------------------
    // Trunk — massive columnar pillar, nearly perfectly vertical
    // -----------------------------------------------------------------------

    private List<Node> growTrunk(BlockPos base, TreeDefinition def, RandomSource rand,
                                  double leanAngle, double leanStrength) {
        List<Node> nodes = new ArrayList<>();
        int height = Mth.nextInt(rand, def.minHeight(), def.maxHeight());
        int steps  = height + 12;
        double step = (double) height / steps;

        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        Vec3 dir = new Vec3(
                Math.cos(leanAngle) * leanStrength,
                1.0,
                Math.sin(leanAngle) * leanStrength).normalize();

        double accX = 0, accZ = 0;
        double twistRate = (rand.nextDouble() - 0.5) * 0.012;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);

            // Column stays massive throughout — top remains 2.4 radius (~5-block-wide trunk)
            float natural = Mth.lerp(t, def.maxTrunkWidth() * 0.65f, 2.4f);

            // Heavy base flare over first 20% — power 2.0 for a smooth ground anchor
            float flare = t < 0.20f
                    ? (float) Math.pow(1.0 - t / 0.20, 2.0) * (def.maxTrunkWidth() * 0.42f)
                    : 0.0f;

            float radius = natural + flare;

            // Subtle bark texture — slightly more frequent than oak for aged appearance
            if (rand.nextFloat() < 0.06f && t > 0.05f && t < 0.95f) {
                radius += 0.15f + rand.nextFloat() * 0.20f;
            }

            nodes.add(new Node(pos, dir, radius));

            // Very low curvature — redwoods are near-vertical pillars
            accX = accX * 0.88 + (rand.nextDouble() - 0.5) * 0.035;
            accZ = accZ * 0.88 + (rand.nextDouble() - 0.5) * 0.035;

            leanAngle += twistRate;
            double lx = Math.cos(leanAngle) * leanStrength * 0.15;
            double lz = Math.sin(leanAngle) * leanStrength * 0.15;

            // Strong upward pull maintains near-perfect verticality
            dir = dir.add(accX + lx, 0, accZ + lz).add(0, 0.22, 0).normalize();
            pos = pos.add(dir.scale(step));
        }

        return nodes;
    }

    // -----------------------------------------------------------------------
    // Roots — broad, stabilizing, ancient ground anchors
    // -----------------------------------------------------------------------

    private List<List<Node>> growRoots(BlockPos base, TreeDefinition def, RandomSource rand) {
        List<List<Node>> roots = new ArrayList<>();
        int count = Math.max(3, (int)(def.rootChance() * 7.0f) + rand.nextInt(2));
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i / count) + (rand.nextDouble() - 0.5) * 0.6;
            roots.add(growSingleRoot(base, angle, def, rand));
        }
        return roots;
    }

    private List<Node> growSingleRoot(BlockPos base, double angle, TreeDefinition def, RandomSource rand) {
        List<Node> nodes = new ArrayList<>();
        int steps = 5 + rand.nextInt(6);
        float startRadius = Math.max(0.5f, def.maxTrunkWidth() * 0.32f + rand.nextFloat() * 0.22f);

        // Broad mostly-horizontal spread — powerful but not fantasy-dramatic
        double outward  = 0.60 + rand.nextDouble() * 0.30;
        double downward = -(0.05 + rand.nextDouble() * 0.12);

        Vec3 dir = new Vec3(Math.cos(angle) * outward, downward, Math.sin(angle) * outward).normalize();
        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.25f, startRadius * (1.0f - t * 0.72f));
            nodes.add(new Node(pos, dir, radius));

            double divePull = -(0.02 + t * 0.05);
            Vec3 jitter = new Vec3(
                    (rand.nextDouble() - 0.5) * 0.08,
                    divePull,
                    (rand.nextDouble() - 0.5) * 0.08);
            dir = dir.add(jitter).normalize();
            pos = pos.add(dir.scale(0.90));
        }
        return nodes;
    }

    // -----------------------------------------------------------------------
    // Branch distribution — upper crown only, layered and orderly
    // -----------------------------------------------------------------------

    private void growAllBranches(List<Node> trunk, TreeDefinition def, RandomSource rand,
                                  List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int trunkSize = trunk.size();
        int primaryCount = 10 + rand.nextInt(4 + Math.max(1, (int)(def.branchDensity() * 6)));

        // Fill the crown interior first so the top reads as one old-growth mass, not
        // separated leaf puffs at branch tips.  Anchors sit inside the upper 25-35%
        // of the already-generated trunk; the trunk geometry itself is untouched.
        for (int i = 0; i < 9; i++) {
            float t = 0.68f + i * 0.035f + (rand.nextFloat() - 0.5f) * 0.012f;
            int idx = Mth.clamp((int)(t * trunkSize), 0, trunkSize - 1);
            Node crownNode = trunk.get(idx);
            double ring = (i % 3) * (Math.PI * 2.0 / 3.0) + rand.nextDouble() * 0.45;
            double spread = 0.6 + (i % 4) * 0.35;
            leafAnchors.add(crownNode.pos().add(
                    Math.cos(ring) * spread,
                    0.2 + rand.nextDouble() * 0.8,
                    Math.sin(ring) * spread));
        }

        for (int b = 0; b < primaryCount; b++) {
            double angle = (2 * Math.PI * b / primaryCount)
                    + (rand.nextDouble() - 0.5) * (Math.PI / primaryCount * 0.85);

            // Crown scaffolds live in the top third: broad lower skirt, shorter
            // upper branches, and a slightly irregular outline for realism.
            float layer = (b % 5) / 4.0f;
            float bandBase = 0.66f + (layer * 0.30f);
            float tSpawn = Mth.clamp(bandBase + (rand.nextFloat() - 0.5f) * 0.075f, 0.64f, 0.98f);
            int spawnIdx = Math.min(trunkSize - 2, (int)(tSpawn * trunkSize));
            Node spawn = trunk.get(spawnIdx);

            boolean upperCrown = tSpawn > 0.86f;
            int baseSteps = def.minBranchLength() + 3
                    + rand.nextInt(Math.max(1, def.maxBranchLength() - def.minBranchLength() + 3));
            int primarySteps = upperCrown
                    ? Math.max(5, (int)(baseSteps * (0.58f + rand.nextFloat() * 0.18f)))
                    : baseSteps + rand.nextInt(4);

            // Outward first with only a small vertical component; branch curvature
            // later gives the redwood its weighty upward/drooping layered crown.
            double elevAngle = Math.toRadians(-4.0 + rand.nextDouble() * 16.0);
            Vec3 outDir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 initDir = outDir.scale(Math.cos(elevAngle))
                    .add(0, Math.sin(elevAngle), 0).normalize();

            float primaryRadius = Math.max(0.85f, spawn.radius() * (0.38f + rand.nextFloat() * 0.16f));

            List<Node> primary = growPrimary(spawn.pos(), initDir, primarySteps, primaryRadius,
                    def, rand, allBranches, leafAnchors, false);

            if (!primary.isEmpty()) allBranches.add(primary);
        }
    }

    // -----------------------------------------------------------------------
    // Primary branch (depth 1)
    // -----------------------------------------------------------------------

    private List<Node> growPrimary(Vec3 startPos, Vec3 startDir, int steps,
                                    float startRadius, TreeDefinition def, RandomSource rand,
                                    List<List<Node>> allBranches, List<Vec3> leafAnchors,
                                    boolean isDead) {
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        Vec3 dir = startDir;
        double accX = 0, accZ = 0;

        int secCount = isDead ? 0 : (3 + rand.nextInt(3));
        int[] secPositions = secCount > 0
                ? pickAttachPoints(steps, secCount, 0.22f, 0.90f, rand)
                : new int[0];

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(isDead ? 0.30f : 0.50f, startRadius * (1.0f - t * 0.62f));
            nodes.add(new Node(pos, dir, radius));

            for (int sp : secPositions) {
                if (sp == i) {
                    float secRadius = Math.max(0.35f, radius * (0.50f + rand.nextFloat() * 0.15f));
                    growSecondary(pos, dir, secRadius, rand, allBranches, leafAnchors);
                }
            }

            accX = accX * 0.80 + (rand.nextDouble() - 0.5) * 0.07;
            accZ = accZ * 0.80 + (rand.nextDouble() - 0.5) * 0.07;

            if (!isDead && i > 1 && (i % 2 == 0 || t > 0.70f)) {
                leafAnchors.add(pos);
            }

            // Heavy redwood scaffolds push outward first; lower crown limbs sag a
            // touch under dense foliage, while outer tips gently recover upward.
            double upCurve = t < 0.38f ? -0.010 + rand.nextDouble() * 0.010 : 0.008 + t * 0.022;
            dir = dir.add(accX, upCurve, accZ).normalize();
            pos = pos.add(dir.scale(0.88));
        }

        if (!isDead && !nodes.isEmpty()) {
            leafAnchors.add(nodes.get(nodes.size() - 1).pos());
        }

        return nodes;
    }

    // -----------------------------------------------------------------------
    // Secondary branch (depth 2)
    // -----------------------------------------------------------------------

    private void growSecondary(Vec3 startPos, Vec3 parentDir, float startRadius,
                                RandomSource rand, List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int steps = 5 + rand.nextInt(5);
        Vec3 dir  = divergeDir(parentDir, 0.48, 0.44, 0.24, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        double accX = 0, accZ = 0;

        int tertiaryCount = 2 + rand.nextInt(3);
        int[] tertiaryPositions = pickAttachPoints(steps, tertiaryCount, 0.24f, 0.86f, rand);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.28f, startRadius * (1.0f - t * 0.60f));
            nodes.add(new Node(pos, dir, radius));

            for (int sp : tertiaryPositions) {
                if (sp == i) {
                    float terRadius = Math.max(0.20f, radius * (0.48f + rand.nextFloat() * 0.14f));
                    growTertiary(pos, dir, terRadius, rand, allBranches, leafAnchors);
                }
            }

            accX = accX * 0.78 + (rand.nextDouble() - 0.5) * 0.09;
            accZ = accZ * 0.78 + (rand.nextDouble() - 0.5) * 0.09;
            if (i > 0 && (i % 2 == 0 || t > 0.60f)) {
                leafAnchors.add(pos);
            }

            dir = dir.add(accX, 0.012 + rand.nextDouble() * 0.030, accZ).normalize();
            pos = pos.add(dir.scale(0.80));
        }

        if (!nodes.isEmpty()) {
            allBranches.add(nodes);
            leafAnchors.add(nodes.get(nodes.size() - 1).pos());
        }
    }

    // -----------------------------------------------------------------------
    // Tertiary branch (depth 3) — fine end growth, primary leaf source
    // -----------------------------------------------------------------------

    private void growTertiary(Vec3 startPos, Vec3 parentDir, float startRadius,
                               RandomSource rand, List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int steps = 3 + rand.nextInt(4);
        Vec3 dir  = divergeDir(parentDir, 0.46, 0.40, 0.22, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.15f, startRadius * (1.0f - t * 0.68f));
            nodes.add(new Node(pos, dir, radius));

            if (i > 0) {
                leafAnchors.add(pos);
            }

            Vec3 jitter = new Vec3(
                    (rand.nextDouble() - 0.5) * 0.14,
                    0.015 + rand.nextDouble() * 0.035,
                    (rand.nextDouble() - 0.5) * 0.14);
            dir = dir.add(jitter).normalize();
            pos = pos.add(dir.scale(0.66));
        }

        if (!nodes.isEmpty()) {
            allBranches.add(nodes);
            leafAnchors.add(nodes.get(nodes.size() - 1).pos());
        }
    }

    // -----------------------------------------------------------------------
    // Painting
    // -----------------------------------------------------------------------

    private void paintTrunk(LevelAccessor level, List<Node> trunk, RandomSource rand) {
        for (Node n : trunk) {
            paintSphere(level, n.pos(), n.radius(), wood);
            // Subtle bark mass splat — less frequent than old oak, keeps silhouette clean
            if (n.radius() > 3.0f && rand.nextFloat() < 0.25f) {
                double ox = (rand.nextDouble() - 0.5) * 0.9;
                double oz = (rand.nextDouble() - 0.5) * 0.9;
                paintSphere(level, n.pos().add(ox, 0, oz), n.radius() * 0.28f, wood);
            }
        }
    }

    private void paintRoot(LevelAccessor level, List<Node> root) {
        for (Node n : root) {
            int r = Math.max(1, Mth.ceil(n.radius()));
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dy * dy + dz * dz <= n.radius() * n.radius() + 0.5f) {
                    BlockPos p = BlockPos.containing(n.pos().x + dx, n.pos().y + dy, n.pos().z + dz);
                    BlockState existing = level.getBlockState(p);
                    if (existing.isAir() || existing.is(Blocks.GRASS_BLOCK)
                            || existing.is(Blocks.DIRT) || existing.is(Blocks.PODZOL)
                            || existing.is(Blocks.COARSE_DIRT)) {
                        level.setBlock(p, wood, 3);
                    }
                }
            }
        }
    }

    /**
     * Dense old-growth redwood crown.  Leaf anchors are intentionally collected
     * along the branch network and inside the crown core so foliage overlaps into
     * one connected canopy instead of appearing only as clipped tufts at branch tips.
     */
    private void placeLeafClusters(LevelAccessor level, List<Vec3> anchors,
                                    TreeDefinition def, RandomSource rand) {
        float density = Mth.clamp(def.leafDensity() + 0.16f, 0.72f, 0.94f);
        float branchScale = def.maxBranchLength() / 14.0f;

        for (Vec3 anchor : anchors) {
            // Broad overlapping clusters create the dense inner canopy core and fill
            // gaps between support limbs.  Slightly taller Y radii preserve the
            // layered conical/domed redwood crown instead of making a flat cap.
            int rx = 2 + rand.nextInt(2 + Math.max(1, (int)(branchScale * 2)));
            int ry = rx + rand.nextInt(3);
            int rz = 2 + rand.nextInt(2 + Math.max(1, (int)(branchScale * 2)));
            paintEllipsoid(level, anchor, rx, ry, rz, rand, density);

            // Softer irregular outer lobes blur the edge while staying attached to
            // the primary mass.  These are deliberately offset only a little so no
            // isolated bonsai-like tip clumps remain.
            if (rand.nextFloat() < 0.78f) {
                double lobeRange = 1.1 + def.maxBranchLength() * 0.045;
                Vec3 offset = new Vec3(
                        (rand.nextDouble() - 0.5) * lobeRange,
                        (rand.nextDouble() - 0.15) * lobeRange * 0.45,
                        (rand.nextDouble() - 0.5) * lobeRange);
                int r2 = 1 + rand.nextInt(2 + Math.max(1, (int)branchScale));
                paintEllipsoid(level, anchor.add(offset), r2 + 1, r2 + 1 + rand.nextInt(2),
                        r2 + 1, rand, density * 0.76f);
            }

            if (rand.nextFloat() < 0.35f) {
                double terRange = 1.4 + def.maxBranchLength() * 0.055;
                Vec3 offset2 = new Vec3(
                        (rand.nextDouble() - 0.5) * terRange,
                        (rand.nextDouble() - 0.35) * terRange * 0.35,
                        (rand.nextDouble() - 0.5) * terRange);
                int r3 = 1 + rand.nextInt(2);
                paintEllipsoid(level, anchor.add(offset2), r3 + 1, r3 + 1, r3 + 1, rand, density * 0.58f);
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
                setIfAirOrWood(level, BlockPos.containing(center.x + dx, center.y + dy, center.z + dz), state);
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

    private void setIfAirOrWood(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.getBlock() == leaves.getBlock()) {
            level.setBlock(pos, state, 3);
        }
    }
}
