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
 * Specialized generator for ancient, sprawling oak trees.
 * Produces LOTR/old-growth style trees with:
 *  - Massive flared base with buttress roots
 *  - 4-level branch hierarchy (primary→secondary→tertiary→twig)
 *  - Heavy sag arc on primary branches
 *  - Clustered canopy masses with natural gaps
 *  - Ground root system
 */
public final class AncientOakGenerator {

    private static final BlockState WOOD   = Blocks.DARK_OAK_LOG.defaultBlockState();
    private static final BlockState LEAVES = Blocks.DARK_OAK_LEAVES.defaultBlockState();

    private record Node(Vec3 pos, Vec3 dir, float radius) {}

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public boolean generate(TreeDefinition def, GenerationContext ctx) {
        LevelAccessor level = ctx.level();
        BlockPos base = TerrainAdapter.findSurface(level, ctx.origin());
        RandomSource rand = ctx.random();

        // Tree personality — drives lean direction and twist feel
        double leanAngle   = rand.nextDouble() * 2 * Math.PI;
        double leanStrength = 0.025 + rand.nextDouble() * 0.055;

        List<Node> trunk = growTrunk(base, def, rand, leanAngle, leanStrength);
        List<List<Node>> roots = growRoots(base, def, rand);
        List<List<Node>> allBranches = new ArrayList<>();
        List<Vec3> leafAnchors = new ArrayList<>();

        growAllBranches(trunk, def, rand, allBranches, leafAnchors);

        // Paint wood
        paintTrunk(level, trunk, rand);
        for (List<Node> root : roots) {
            paintRoot(level, root);
        }
        for (List<Node> branch : allBranches) {
            for (Node n : branch) {
                if (n.radius() >= 0.3f) {
                    paintSphere(level, n.pos(), n.radius(), WOOD);
                } else {
                    setIfAirOrWood(level, BlockPos.containing(n.pos()), WOOD);
                }
            }
        }

        // Paint foliage
        placeLeafClusters(level, leafAnchors, def, rand);

        return true;
    }

    // -----------------------------------------------------------------------
    // Trunk
    // -----------------------------------------------------------------------

    private List<Node> growTrunk(BlockPos base, TreeDefinition def, RandomSource rand,
                                  double leanAngle, double leanStrength) {
        List<Node> nodes = new ArrayList<>();
        int height  = Mth.nextInt(rand, def.minHeight(), def.maxHeight());
        int steps   = height + 6;
        double step = (double) height / steps;

        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        Vec3 dir = new Vec3(
                Math.cos(leanAngle) * leanStrength,
                1.0,
                Math.sin(leanAngle) * leanStrength).normalize();

        double accX = 0, accZ = 0;
        // Slow twist: lean direction rotates as tree grows
        double twistRate = (rand.nextDouble() - 0.5) * 0.04;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);

            // Aggressive base flare — cubic decay from base
            float flare = t < 0.14f
                    ? (float) Math.pow(1.0 - t / 0.14, 2.2) * (def.maxTrunkWidth() * 0.6f)
                    : 0.0f;
            // Natural taper from max to a narrow top
            float natural = Mth.lerp(t, def.maxTrunkWidth() * 0.52f, 0.55f);
            float radius = natural + flare;

            // Occasional bark bulge for age character
            if (rand.nextFloat() < 0.06f && t > 0.1f && t < 0.9f) {
                radius += 0.25f + rand.nextFloat() * 0.35f;
            }

            nodes.add(new Node(pos, dir, radius));

            // Smooth accumulated curvature (Perlin-like)
            accX = accX * 0.82 + (rand.nextDouble() - 0.5) * 0.07;
            accZ = accZ * 0.82 + (rand.nextDouble() - 0.5) * 0.07;

            // Slowly rotating lean axis
            leanAngle += twistRate;
            double lx = Math.cos(leanAngle) * leanStrength * 0.25;
            double lz = Math.sin(leanAngle) * leanStrength * 0.25;

            dir = dir.add(accX + lx, 0, accZ + lz).add(0, 0.14, 0).normalize();
            pos = pos.add(dir.scale(step));
        }

        return nodes;
    }

    // -----------------------------------------------------------------------
    // Roots
    // -----------------------------------------------------------------------

    private List<List<Node>> growRoots(BlockPos base, TreeDefinition def, RandomSource rand) {
        List<List<Node>> roots = new ArrayList<>();
        int count = 4 + rand.nextInt(3);

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i / count) + (rand.nextDouble() - 0.5) * 0.9;
            roots.add(growSingleRoot(base, angle, def, rand));
        }
        return roots;
    }

    private List<Node> growSingleRoot(BlockPos base, double angle, TreeDefinition def, RandomSource rand) {
        List<Node> nodes = new ArrayList<>();
        int steps = 9 + rand.nextInt(7);
        float startRadius = def.maxTrunkWidth() * 0.28f + rand.nextFloat() * 0.3f;
        startRadius = Math.max(0.5f, startRadius);

        // Roots sweep outward and slightly downward
        double outward  = 0.75 + rand.nextDouble() * 0.35;
        double downward = -(0.08 + rand.nextDouble() * 0.18);

        Vec3 dir = new Vec3(Math.cos(angle) * outward, downward, Math.sin(angle) * outward).normalize();
        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.2f, startRadius * (1.0f - t * 0.78f));
            nodes.add(new Node(pos, dir, radius));

            // Roots dive more into the ground as they extend
            double divePull = -(0.03 + t * 0.07);
            Vec3 jitter = new Vec3(
                    (rand.nextDouble() - 0.5) * 0.1,
                    divePull,
                    (rand.nextDouble() - 0.5) * 0.1);
            dir = dir.add(jitter).normalize();
            pos = pos.add(dir.scale(0.85));
        }
        return nodes;
    }

    // -----------------------------------------------------------------------
    // Branch hierarchy entry
    // -----------------------------------------------------------------------

    private void growAllBranches(List<Node> trunk, TreeDefinition def, RandomSource rand,
                                  List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int trunkSize   = trunk.size();
        int primaryCount = 4 + rand.nextInt(4); // 4–7 primary branches

        // Evenly distribute primary branch angles around trunk, with per-branch jitter
        for (int b = 0; b < primaryCount; b++) {
            double angle = (2 * Math.PI * b / primaryCount) + (rand.nextDouble() - 0.5) * (Math.PI / primaryCount);

            // Spawn height: 28–72 % up the trunk, weighted toward lower-middle
            float tSpawn = 0.28f + rand.nextFloat() * 0.44f;
            int spawnIdx = Math.min(trunkSize - 2, (int) (tSpawn * trunkSize));
            Node spawn   = trunk.get(spawnIdx);

            // Primary branch — long, sweeping, with heavy arc
            int primarySteps = 18 + rand.nextInt(10); // 18–27 steps
            double stepSize  = 0.85 + rand.nextDouble() * 0.3;

            // Initial direction: mostly horizontal with slight upward tilt
            double elevation = 0.08 + rand.nextDouble() * 0.22;
            Vec3 outDir  = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 initDir = outDir.scale(Math.cos(elevation))
                    .add(0, Math.sin(elevation), 0).normalize();

            float primaryRadius = Math.max(1.1f, spawn.radius() * (0.42f + rand.nextFloat() * 0.22f));

            List<Node> primary = growPrimary(spawn.pos(), initDir, primarySteps, (float) stepSize,
                    primaryRadius, def, rand, allBranches, leafAnchors);

            if (!primary.isEmpty()) allBranches.add(primary);
        }
    }

    // -----------------------------------------------------------------------
    // Primary branch  (depth 1)
    // -----------------------------------------------------------------------

    /**
     * Primary branches sweep outward, sag in the middle, and tip upward —
     * simulating the weight of a centuries-old oak limb.
     */
    private List<Node> growPrimary(Vec3 startPos, Vec3 startDir, int steps, float stepSize,
                                    float startRadius, TreeDefinition def, RandomSource rand,
                                    List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        Vec3 dir = startDir;
        double accX = 0, accZ = 0;

        // Decide secondary attachment positions up-front (fixed count for control)
        int secCount = 1 + rand.nextInt(3); // 1–3 secondaries
        int[] secPositions = pickAttachPoints(steps, secCount, 0.25f, 0.85f, rand);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.4f, startRadius * (1.0f - t * 0.72f));
            nodes.add(new Node(pos, dir, radius));

            // Spawn secondary branches at pre-chosen positions
            for (int sp : secPositions) {
                if (sp == i) {
                    float secRadius = Math.max(0.5f, radius * (0.45f + rand.nextFloat() * 0.2f));
                    growSecondary(pos, dir, secRadius, def, rand, allBranches, leafAnchors);
                }
            }

            // Smooth curvature noise
            accX = accX * 0.78 + (rand.nextDouble() - 0.5) * 0.10;
            accZ = accZ * 0.78 + (rand.nextDouble() - 0.5) * 0.10;

            // Sag arc: peaks at t=0.5, tips curve back upward
            double sag       = Math.sin(Math.PI * t) * -0.10;
            double lightSeek = 0.035 + t * 0.045;
            dir = dir.add(accX, sag + lightSeek, accZ).normalize();
            pos = pos.add(dir.scale(stepSize));
        }

        return nodes;
    }

    // -----------------------------------------------------------------------
    // Secondary branch  (depth 2)
    // -----------------------------------------------------------------------

    private void growSecondary(Vec3 startPos, Vec3 parentDir, float startRadius, TreeDefinition def,
                                RandomSource rand, List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int steps = 8 + rand.nextInt(6); // 8–13 steps
        Vec3 dir  = divergeDir(parentDir, 0.35, 0.55, 0.45, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        double accX = 0, accZ = 0;

        int tertiaryCount = 1 + rand.nextInt(3);
        int[] tertiaryPositions = pickAttachPoints(steps, tertiaryCount, 0.3f, 0.85f, rand);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.3f, startRadius * (1.0f - t * 0.75f));
            nodes.add(new Node(pos, dir, radius));

            for (int sp : tertiaryPositions) {
                if (sp == i) {
                    float terRadius = Math.max(0.22f, radius * (0.42f + rand.nextFloat() * 0.18f));
                    growTertiary(pos, dir, terRadius, def, rand, allBranches, leafAnchors);
                }
            }

            accX = accX * 0.75 + (rand.nextDouble() - 0.5) * 0.13;
            accZ = accZ * 0.75 + (rand.nextDouble() - 0.5) * 0.13;
            dir = dir.add(accX, 0.06 + rand.nextDouble() * 0.03, accZ).normalize();
            pos = pos.add(dir.scale(0.8));
        }

        if (!nodes.isEmpty()) allBranches.add(nodes);
    }

    // -----------------------------------------------------------------------
    // Tertiary branch  (depth 3) — leaf anchors generated here
    // -----------------------------------------------------------------------

    private void growTertiary(Vec3 startPos, Vec3 parentDir, float startRadius, TreeDefinition def,
                               RandomSource rand, List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int steps = 5 + rand.nextInt(4); // 5–8 steps
        Vec3 dir  = divergeDir(parentDir, 0.4, 0.55, 0.60, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        double accX = 0, accZ = 0;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.15f, startRadius * (1.0f - t * 0.8f));
            nodes.add(new Node(pos, dir, radius));

            accX = accX * 0.70 + (rand.nextDouble() - 0.5) * 0.18;
            accZ = accZ * 0.70 + (rand.nextDouble() - 0.5) * 0.18;
            dir = dir.add(accX, 0.09, accZ).normalize();
            pos = pos.add(dir.scale(0.7));
        }

        if (!nodes.isEmpty()) {
            allBranches.add(nodes);
            // Tertiary endpoint is a leaf anchor
            leafAnchors.add(nodes.get(nodes.size() - 1).pos());

            // Grow 1–2 twigs from random spots on the tertiary
            int twigCount = rand.nextInt(3); // 0–2
            for (int tw = 0; tw < twigCount; tw++) {
                int idx = (int)(nodes.size() * (0.4 + rand.nextDouble() * 0.45));
                idx = Math.min(idx, nodes.size() - 1);
                Node attach = nodes.get(idx);
                growTwig(attach.pos(), attach.dir(), def, rand, allBranches, leafAnchors);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Twig  (depth 4 — thinnest, terminates in leaf anchor)
    // -----------------------------------------------------------------------

    private void growTwig(Vec3 startPos, Vec3 parentDir, TreeDefinition def, RandomSource rand,
                           List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int steps = 3 + rand.nextInt(3); // 3–5 steps
        Vec3 dir  = divergeDir(parentDir, 0.45, 0.5, 0.75, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.12f, 0.30f * (1.0f - t * 0.85f));
            nodes.add(new Node(pos, dir, radius));

            Vec3 jitter = new Vec3(
                    (rand.nextDouble() - 0.5) * 0.22,
                    0.10 + rand.nextDouble() * 0.08,
                    (rand.nextDouble() - 0.5) * 0.22);
            dir = dir.add(jitter).normalize();
            pos = pos.add(dir.scale(0.55));
        }

        if (!nodes.isEmpty()) {
            allBranches.add(nodes);
            leafAnchors.add(nodes.get(nodes.size() - 1).pos());
        }
    }

    // -----------------------------------------------------------------------
    // Painting helpers
    // -----------------------------------------------------------------------

    /**
     * Paints trunk nodes. Larger radii get extra offset sphere splats to
     * simulate bark-mass irregularity — the surface looks sculpted, not smooth.
     */
    private void paintTrunk(LevelAccessor level, List<Node> trunk, RandomSource rand) {
        for (Node n : trunk) {
            paintSphere(level, n.pos(), n.radius(), WOOD);
            // Extra bark-mass splat at wide sections for sculpted, aged look
            if (n.radius() > 1.8f) {
                double ox = (rand.nextDouble() - 0.5) * n.radius() * 0.6;
                double oz = (rand.nextDouble() - 0.5) * n.radius() * 0.6;
                paintSphere(level, n.pos().add(ox, 0, oz), n.radius() * 0.55f, WOOD);
            }
        }
    }

    /**
     * Paints root nodes, burying blocks that end up below the terrain surface.
     * Roots are drawn with wood so they blend into the base.
     */
    private void paintRoot(LevelAccessor level, List<Node> root) {
        for (Node n : root) {
            int r = Math.max(1, Mth.ceil(n.radius()));
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dy * dy + dz * dz <= n.radius() * n.radius() + 0.5f) {
                    BlockPos p = BlockPos.containing(n.pos().x + dx, n.pos().y + dy, n.pos().z + dz);
                    BlockState existing = level.getBlockState(p);
                    // Replace air and terrain; roots grow through ground
                    if (existing.isAir() || existing.is(Blocks.GRASS_BLOCK)
                            || existing.is(Blocks.DIRT) || existing.is(Blocks.PODZOL)
                            || existing.is(Blocks.COARSE_DIRT)) {
                        level.setBlock(p, WOOD, 3);
                    }
                }
            }
        }
    }

    /**
     * Places small, irregular leaf clusters at each anchor.
     * Multiple overlapping ellipsoids create the clustered canopy-mass effect.
     */
    private void placeLeafClusters(LevelAccessor level, List<Vec3> anchors, TreeDefinition def, RandomSource rand) {
        float density = Mth.clamp(def.leafDensity(), 0.35f, 0.92f);

        for (Vec3 anchor : anchors) {
            // Primary cluster — small, dense
            int rx = 2 + rand.nextInt(2);
            int ry = 2 + rand.nextInt(2);
            int rz = 2 + rand.nextInt(2);
            paintEllipsoid(level, anchor, rx, ry, rz, rand, density);

            // Secondary lobe — offset, slightly larger, sparser
            if (rand.nextFloat() < 0.55f) {
                Vec3 offset = new Vec3(
                        (rand.nextDouble() - 0.5) * 2.8,
                        (rand.nextDouble() - 0.5) * 1.6,
                        (rand.nextDouble() - 0.5) * 2.8);
                int r2 = 2 + rand.nextInt(3);
                paintEllipsoid(level, anchor.add(offset), r2, r2 - 1, r2, rand, density * 0.72f);
            }

            // Occasional hanging drape below
            if (rand.nextFloat() < 0.20f) {
                Vec3 drape = anchor.add(0, -(1 + rand.nextInt(2)), 0);
                paintEllipsoid(level, drape, 2, 3, 2, rand, density * 0.55f);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    /** Picks {@code count} attachment indices spread across [minT, maxT] of {@code steps}. */
    private int[] pickAttachPoints(int steps, int count, float minT, float maxT, RandomSource rand) {
        int[] pts = new int[count];
        float span = maxT - minT;
        for (int i = 0; i < count; i++) {
            float t = minT + (span * i / Math.max(1, count - 1)) + (rand.nextFloat() - 0.5f) * (span / (count + 1));
            pts[i] = Mth.clamp((int) (t * steps), 1, steps - 2);
        }
        return pts;
    }

    /**
     * Creates a branch direction that diverges organically from its parent.
     *
     * @param parentWeight  how much of the parent direction to inherit
     * @param randomWeight  horizontal random spread influence
     * @param upWeight      upward bias
     */
    private Vec3 divergeDir(Vec3 parent, double parentWeight, double randomWeight, double upWeight, RandomSource rand) {
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
                    level.setBlock(p, LEAVES, 3);
                }
            }
        }
    }

    private void setIfAirOrWood(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(Blocks.DARK_OAK_LEAVES)) {
            level.setBlock(pos, state, 3);
        }
    }
}
