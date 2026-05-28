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

        double leanAngle    = rand.nextDouble() * 2 * Math.PI;
        double leanStrength = 0.025 + rand.nextDouble() * 0.055;

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
                    paintSphere(level, n.pos(), n.radius(), WOOD);
                } else {
                    setIfAirOrWood(level, BlockPos.containing(n.pos()), WOOD);
                }
            }
        }

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
        double twistRate = (rand.nextDouble() - 0.5) * 0.04;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);

            // Natural taper: stays wide throughout — top stays at 1.4 radius (nearly 3 blocks wide)
            float natural = Mth.lerp(t, def.maxTrunkWidth() * 0.58f, 1.4f);

            // Gradual base flare extending over first 28% of height — power 1.6 for smooth roll-off
            float flare = t < 0.28f
                    ? (float) Math.pow(1.0 - t / 0.28, 1.6) * (def.maxTrunkWidth() * 0.48f)
                    : 0.0f;

            float radius = natural + flare;

            // Occasional bark bulge — keeps effect subtle
            if (rand.nextFloat() < 0.05f && t > 0.08f && t < 0.92f) {
                radius += 0.2f + rand.nextFloat() * 0.25f;
            }

            nodes.add(new Node(pos, dir, radius));

            accX = accX * 0.82 + (rand.nextDouble() - 0.5) * 0.07;
            accZ = accZ * 0.82 + (rand.nextDouble() - 0.5) * 0.07;

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

        double outward  = 0.75 + rand.nextDouble() * 0.35;
        double downward = -(0.08 + rand.nextDouble() * 0.18);

        Vec3 dir = new Vec3(Math.cos(angle) * outward, downward, Math.sin(angle) * outward).normalize();
        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.2f, startRadius * (1.0f - t * 0.78f));
            nodes.add(new Node(pos, dir, radius));

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
        int trunkSize    = trunk.size();
        int primaryCount = 4 + rand.nextInt(4);

        for (int b = 0; b < primaryCount; b++) {
            double angle = (2 * Math.PI * b / primaryCount) + (rand.nextDouble() - 0.5) * (Math.PI / primaryCount);

            float tSpawn  = 0.28f + rand.nextFloat() * 0.44f;
            int spawnIdx  = Math.min(trunkSize - 2, (int) (tSpawn * trunkSize));
            Node spawn    = trunk.get(spawnIdx);

            int primarySteps  = 18 + rand.nextInt(10);
            double stepSize   = 0.90 + rand.nextDouble() * 0.25;

            // Branches sweep outward AND upward — 17-40° elevation gives classic oak arc shape
            double elevation = 0.30 + rand.nextDouble() * 0.38;
            Vec3 outDir  = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 initDir = outDir.scale(Math.cos(elevation))
                    .add(0, Math.sin(elevation), 0).normalize();

            // Primary branches must be thick — minimum 1.5 radius regardless of trunk width
            float primaryRadius = Math.max(1.5f, spawn.radius() * (0.48f + rand.nextFloat() * 0.18f));

            List<Node> primary = growPrimary(spawn.pos(), initDir, primarySteps, (float) stepSize,
                    primaryRadius, def, rand, allBranches, leafAnchors);

            if (!primary.isEmpty()) allBranches.add(primary);
        }
    }

    // -----------------------------------------------------------------------
    // Primary branch  (depth 1)
    // -----------------------------------------------------------------------

    private List<Node> growPrimary(Vec3 startPos, Vec3 startDir, int steps, float stepSize,
                                    float startRadius, TreeDefinition def, RandomSource rand,
                                    List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        Vec3 dir = startDir;
        double accX = 0, accZ = 0;

        // 2-4 secondaries per primary for a denser tree
        int secCount = 2 + rand.nextInt(3);
        int[] secPositions = pickAttachPoints(steps, secCount, 0.20f, 0.85f, rand);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            // Slow taper — branch stays chunky along most of its length
            float radius = Math.max(0.65f, startRadius * (1.0f - t * 0.55f));
            nodes.add(new Node(pos, dir, radius));

            for (int sp : secPositions) {
                if (sp == i) {
                    // Secondary minimum is 0.8 — always visible as a 2-block-wide limb
                    float secRadius = Math.max(0.80f, radius * (0.58f + rand.nextFloat() * 0.18f));
                    growSecondary(pos, dir, secRadius, def, rand, allBranches, leafAnchors);
                }
            }

            accX = accX * 0.78 + (rand.nextDouble() - 0.5) * 0.10;
            accZ = accZ * 0.78 + (rand.nextDouble() - 0.5) * 0.10;

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
        int steps = 8 + rand.nextInt(6);
        Vec3 dir  = divergeDir(parentDir, 0.35, 0.55, 0.45, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        double accX = 0, accZ = 0;

        // 2-4 tertiaries per secondary
        int tertiaryCount    = 2 + rand.nextInt(3);
        int[] tertiaryPositions = pickAttachPoints(steps, tertiaryCount, 0.25f, 0.85f, rand);

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            // Minimum 0.45 — secondary always visible as at least 1 block wide
            float radius = Math.max(0.45f, startRadius * (1.0f - t * 0.58f));
            nodes.add(new Node(pos, dir, radius));

            for (int sp : tertiaryPositions) {
                if (sp == i) {
                    // Tertiary minimum 0.38 — just about visible as single blocks
                    float terRadius = Math.max(0.38f, radius * (0.55f + rand.nextFloat() * 0.18f));
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
    // Tertiary branch  (depth 3) — primary source of leaf anchors
    // -----------------------------------------------------------------------

    private void growTertiary(Vec3 startPos, Vec3 parentDir, float startRadius, TreeDefinition def,
                               RandomSource rand, List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int steps = 5 + rand.nextInt(4);
        Vec3 dir  = divergeDir(parentDir, 0.40, 0.55, 0.60, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;
        double accX = 0, accZ = 0;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.22f, startRadius * (1.0f - t * 0.65f));
            nodes.add(new Node(pos, dir, radius));

            accX = accX * 0.70 + (rand.nextDouble() - 0.5) * 0.18;
            accZ = accZ * 0.70 + (rand.nextDouble() - 0.5) * 0.18;
            dir = dir.add(accX, 0.09, accZ).normalize();
            pos = pos.add(dir.scale(0.7));
        }

        if (!nodes.isEmpty()) {
            allBranches.add(nodes);
            leafAnchors.add(nodes.get(nodes.size() - 1).pos());

            int twigCount = rand.nextInt(3);
            for (int tw = 0; tw < twigCount; tw++) {
                int idx = (int) (nodes.size() * (0.4 + rand.nextDouble() * 0.45));
                idx = Math.min(idx, nodes.size() - 1);
                Node attach = nodes.get(idx);
                growTwig(attach.pos(), attach.dir(), rand, allBranches, leafAnchors);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Twig  (depth 4)
    // -----------------------------------------------------------------------

    private void growTwig(Vec3 startPos, Vec3 parentDir, RandomSource rand,
                           List<List<Node>> allBranches, List<Vec3> leafAnchors) {
        int steps = 3 + rand.nextInt(3);
        Vec3 dir  = divergeDir(parentDir, 0.45, 0.5, 0.75, rand);
        List<Node> nodes = new ArrayList<>();
        Vec3 pos = startPos;

        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps - 1);
            float radius = Math.max(0.12f, 0.28f * (1.0f - t * 0.80f));
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
    // Painting
    // -----------------------------------------------------------------------

    private void paintTrunk(LevelAccessor level, List<Node> trunk, RandomSource rand) {
        for (Node n : trunk) {
            paintSphere(level, n.pos(), n.radius(), WOOD);
            // Small bark-mass splat at base — kept subtle so it doesn't create blobs
            if (n.radius() > 2.5f && rand.nextFloat() < 0.35f) {
                double ox = (rand.nextDouble() - 0.5) * 1.2;
                double oz = (rand.nextDouble() - 0.5) * 1.2;
                paintSphere(level, n.pos().add(ox, 0, oz), n.radius() * 0.38f, WOOD);
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
                        level.setBlock(p, WOOD, 3);
                    }
                }
            }
        }
    }

    /**
     * Each leaf anchor gets a primary cluster (r=3-5) plus optional satellite lobes.
     * Small clusters overlapping throughout the branch network give a natural
     * broken-canopy look rather than a solid blob.
     */
    private void placeLeafClusters(LevelAccessor level, List<Vec3> anchors, TreeDefinition def, RandomSource rand) {
        float density = Mth.clamp(def.leafDensity(), 0.45f, 0.92f);

        for (Vec3 anchor : anchors) {
            // Primary cluster — meaningfully sized
            int rx = 3 + rand.nextInt(3); // 3-5
            int ry = 2 + rand.nextInt(3); // 2-4
            int rz = 3 + rand.nextInt(3); // 3-5
            paintEllipsoid(level, anchor, rx, ry, rz, rand, density);

            // Secondary lobe — offset outward, slightly sparser (high probability)
            if (rand.nextFloat() < 0.72f) {
                Vec3 offset = new Vec3(
                        (rand.nextDouble() - 0.5) * 3.0,
                        (rand.nextDouble() - 0.5) * 1.8,
                        (rand.nextDouble() - 0.5) * 3.0);
                int r2 = 3 + rand.nextInt(3);
                int r2y = Math.max(1, r2 - 1);
                paintEllipsoid(level, anchor.add(offset), r2, r2y, r2, rand, density * 0.70f);
            }

            // Tertiary lobe — adds canopy volume in a different direction
            if (rand.nextFloat() < 0.40f) {
                Vec3 offset2 = new Vec3(
                        (rand.nextDouble() - 0.5) * 4.0,
                        (rand.nextDouble() - 0.5) * 2.5,
                        (rand.nextDouble() - 0.5) * 4.0);
                int r3 = 2 + rand.nextInt(3);
                paintEllipsoid(level, anchor.add(offset2), r3, r3, r3, rand, density * 0.55f);
            }

            // Hanging drape below branch tip
            if (rand.nextFloat() < 0.25f) {
                Vec3 drape = anchor.add(0, -(1 + rand.nextInt(2)), 0);
                paintEllipsoid(level, drape, 2, 3, 2, rand, density * 0.60f);
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
            pts[i] = Mth.clamp((int) (t * steps), 1, steps - 2);
        }
        return pts;
    }

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
