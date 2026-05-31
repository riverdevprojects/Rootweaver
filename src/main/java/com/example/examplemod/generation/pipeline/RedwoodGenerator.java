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
 *  - Branches ONLY from upper 45-92% of trunk — lower half completely bare
 *  - Nearly horizontal emergence (5-25°) angling gently upward as they extend
 *  - 3-level hierarchy (primary → secondary → tertiary), disciplined not chaotic
 *  - Occasional dead lower limbs for ancient character
 *  - Narrow layered canopy — taller-than-wide clusters, sky-visible between sections
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

        // Main canopy: solid conical leaf mass from canopy base to trunk top
        paintConicalCanopy(level, trunk, def, rand);
        // Accent puffs at branch tips for detail beyond the cone edge
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
        int trunkSize    = trunk.size();
        // More primary branches for better canopy coverage
        int primaryCount = 6 + rand.nextInt(Math.max(1, (int)(def.branchDensity() * 5)));

        for (int b = 0; b < primaryCount; b++) {
            double angle = (2 * Math.PI * b / primaryCount)
                    + (rand.nextDouble() - 0.5) * (Math.PI / primaryCount);

            // Branches only from upper 45-92% — lower trunk bare, monumental column
            float tSpawn = 0.45f + rand.nextFloat() * 0.47f;
            int spawnIdx = Math.min(trunkSize - 2, (int)(tSpawn * trunkSize));
            Node spawn   = trunk.get(spawnIdx);

            // Dead limb in 45-62% zone — short, bare, adds ancient aged character
            boolean isDead = tSpawn < 0.62f && rand.nextFloat() < 0.40f;

            // Cone shape: higher branches are shorter, lower branches longer
            float heightScale = 1.0f - (tSpawn - 0.45f) / 0.47f * 0.55f; // 1.0 at bottom, 0.45 at top
            int baseLen = def.minBranchLength() + rand.nextInt(Math.max(1, def.maxBranchLength() - def.minBranchLength()));
            int primarySteps = isDead
                    ? 4 + rand.nextInt(4)
                    : Math.max(5, (int)(baseLen * heightScale));

            // Slightly downward initial angle for lower branches, flat for upper — classic redwood droop
            double elevDeg = tSpawn < 0.70f
                    ? -5.0 - rand.nextDouble() * 10.0   // lower branches droop
                    : -2.0 + rand.nextDouble() * 8.0;   // upper branches nearly flat
            double elevAngle = Math.toRadians(elevDeg);
            Vec3 outDir  = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 initDir = outDir.scale(Math.cos(Math.abs(elevAngle)))
                    .add(0, Math.sin(elevAngle), 0).normalize();

            float primaryRadius = Math.max(0.55f, spawn.radius() * (0.32f + rand.nextFloat() * 0.14f));

            List<Node> primary = growPrimary(spawn.pos(), initDir, primarySteps, primaryRadius,
                    def, rand, allBranches, leafAnchors, isDead);

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

        // Guarantee at least 2 secondaries on live branches for reliable coverage
        int secCount = isDead ? 0 : (2 + rand.nextInt(3));
        int[] secPositions = secCount > 0
                ? pickAttachPoints(steps, secCount, 0.25f, 0.85f, rand)
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

            // Slight droop as branch extends outward — redwood branches hang, not lift
            double upCurve = 0.005 - t * 0.020;
            dir = dir.add(accX, upCurve, accZ).normalize();
            pos = pos.add(dir.scale(0.85));
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
        int steps = 4 + rand.nextInt(5);
        Vec3 dir  = divergeDir(parentDir, 0.32, 0.48, 0.50, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        double accX = 0, accZ = 0;

        // More tertiaries = more foliage coverage — guarantee at least 2
        int tertiaryCount = 2 + rand.nextInt(2);
        int[] tertiaryPositions = pickAttachPoints(steps, tertiaryCount, 0.25f, 0.85f, rand);

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

            // Add leaf anchors along the secondary branch, not just at the tip
            if (i > 0 && i % 2 == 0) {
                leafAnchors.add(pos);
            }

            accX = accX * 0.78 + (rand.nextDouble() - 0.5) * 0.09;
            accZ = accZ * 0.78 + (rand.nextDouble() - 0.5) * 0.09;
            dir = dir.add(accX, 0.04 + rand.nextDouble() * 0.03, accZ).normalize();
            pos = pos.add(dir.scale(0.78));
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
        Vec3 dir  = divergeDir(parentDir, 0.38, 0.42, 0.55, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.15f, startRadius * (1.0f - t * 0.68f));
            nodes.add(new Node(pos, dir, radius));

            // Every step of tertiary is a leaf source — dense foliage spray along full length
            leafAnchors.add(pos);

            Vec3 jitter = new Vec3(
                    (rand.nextDouble() - 0.5) * 0.14,
                    0.06 + rand.nextDouble() * 0.05,
                    (rand.nextDouble() - 0.5) * 0.14);
            dir = dir.add(jitter).normalize();
            pos = pos.add(dir.scale(0.65));
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
     * Conical leaf mass — the primary canopy volume.
     * Paints a linearly tapered cone from canopyStart (45% of trunk) to trunk top.
     * Radius at the base is wide (filling out to branch tips); tapers to a small
     * cap at the crown. Per-layer radius gets slight noise so the silhouette is
     * ragged and natural rather than a perfect triangle.
     */
    private void paintConicalCanopy(LevelAccessor level, List<Node> trunk,
                                     TreeDefinition def, RandomSource rand) {
        int trunkSize = trunk.size();
        int canopyStartIdx = (int)(0.45f * trunkSize);
        Node canopyBottom = trunk.get(canopyStartIdx);
        Node canopyTop    = trunk.get(trunkSize - 1);

        double bottomY = canopyBottom.pos().y;
        double topY    = canopyTop.pos().y;
        // Trunk lean means the cone center drifts — follow trunk midline
        double cx = (canopyBottom.pos().x + canopyTop.pos().x) * 0.5;
        double cz = (canopyBottom.pos().z + canopyTop.pos().z) * 0.5;

        // Widest radius at canopy base — scaled to branch length so bigger trees fill out more
        double maxRadius = 6.0 + def.maxBranchLength() * 0.55;
        float density = Mth.clamp(def.leafDensity(), 0.68f, 0.88f);

        for (int y = (int)bottomY; y <= (int)topY + 1; y++) {
            double t = (y - bottomY) / Math.max(1, topY - bottomY); // 0=base, 1=tip
            // Linear taper + per-layer noise for ragged silhouette
            double radius = maxRadius * (1.0 - t) + (rand.nextDouble() - 0.3) * 2.5;
            radius = Math.max(1.0, radius);
            int r = (int)Math.ceil(radius);

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    // Soft edge: full density in core, fading at the fringe
                    float edgeFactor = (float)(1.0 - Math.max(0, dist - radius * 0.7) / (radius * 0.3 + 0.01));
                    if (dist <= radius && rand.nextFloat() < density * edgeFactor) {
                        BlockPos p = BlockPos.containing(cx + dx, y, cz + dz);
                        if (level.getBlockState(p).isAir()) {
                            level.setBlock(p, leaves, 3);
                        }
                    }
                }
            }
        }
    }

    /** Small accent puffs at branch tips — adds detail and depth beyond the cone edge. */
    private void placeLeafClusters(LevelAccessor level, List<Vec3> anchors,
                                    TreeDefinition def, RandomSource rand) {
        float density = Mth.clamp(def.leafDensity() * 0.7f, 0.45f, 0.65f);
        for (Vec3 anchor : anchors) {
            // Only tip anchors (not the mid-branch ones) get puffs — filter by sampling ~30%
            if (rand.nextFloat() > 0.30f) continue;
            int r = 1 + rand.nextInt(2);
            paintEllipsoid(level, anchor, r, r + rand.nextInt(2), r, rand, density);
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
