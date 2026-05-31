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
 * Compact rounded-canopy "common" oak — the workhorse tree of the forest floor.
 *
 * Shape: short trunk (3–4 blocks), oblate dome canopy (H-radius 3–5, V-radius 2–4,
 * so the dome is always wider than tall). Dense leaves with a rounded crown taper,
 * sagging edge skirt, and surface noise — reads as full and heavy, not a ball on a stick.
 *
 * Designed to cluster: no spacing or collision rejection is applied, so canopies
 * freely interpenetrate into a continuous forest roof.
 *
 * Four silhouette variants are chosen deterministically from a position hash so
 * the same location always produces the same variant, giving stable-but-varied
 * forests without visible copy-paste patterns.
 */
public final class CommonOakGenerator {

    private static final BlockState WOOD   = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState();

    public boolean generate(TreeDefinition def, GenerationContext ctx) {
        LevelAccessor level = ctx.level();
        BlockPos base = TerrainAdapter.findSurface(level, ctx.origin());
        RandomSource rand = ctx.random();

        // Stable per-position variant (0–3) — different from the world-level RNG so
        // successive trees in a forest don't all pick the same archetype.
        long posHash = hashPos(base.getX(), base.getY(), base.getZ()) ^ ctx.seed();
        int variant = (int) Math.abs(posHash % 4);

        // --- Variant parameter table ---
        // variant 0: 3-block trunk, medium dome (H=3-4), leans north-ish
        // variant 1: 3-block trunk, narrow dome  (H=3-4), leans east-ish
        // variant 2: 4-block trunk, wide dome    (H=4-5), leans south-ish (+stub branches)
        // variant 3: 4-block trunk, wide dome    (H=4-5), leans west-ish  (+stub branches)
        //
        // H/V ratio kept at ~1.3–1.8 to produce a visibly oblate (wider-than-tall) dome.
        int trunkHeight   = 3 + (variant / 2);                             // 3 or 4
        // consume def height for RNG parity with other generators
        @SuppressWarnings("unused") int _h = Mth.nextInt(rand, def.minHeight(), def.maxHeight());
        int canopyRadiusH = 3 + (variant % 2) + rand.nextInt(2);           // 3–5 horizontal
        // Oblate: vertical radius is ~60–70 % of horizontal — dome is clearly wider than tall
        int canopyRadiusV = Math.max(2, (int)(canopyRadiusH * 0.65f) + rand.nextInt(2)); // 2–4
        double leanAngle  = (variant * Math.PI * 0.5) + (rand.nextDouble() - 0.5) * 0.5;
        double leanAmount = def.canLean() ? 0.04 + rand.nextDouble() * 0.07 : 0.0;

        // Build trunk
        List<BlockPos> trunkBlocks = growTrunk(base, trunkHeight, leanAngle, leanAmount);
        BlockPos trunkTop = trunkBlocks.get(trunkBlocks.size() - 1);

        // Canopy center sits at trunk-top level (not above it) so the dome's weight
        // reads low — its lower half wraps around the trunk crown rather than
        // floating on top of it.
        Vec3 canopyCenter = new Vec3(
                trunkTop.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.4,
                trunkTop.getY(),
                trunkTop.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.4);

        // Paint
        for (BlockPos p : trunkBlocks) {
            setIfAirOrLeaves(level, p, WOOD);
        }

        if (variant >= 2) {
            placeStubBranches(level, trunkTop, leanAngle, rand);
        }

        paintCanopy(level, canopyCenter, canopyRadiusH, canopyRadiusV, rand, def.leafDensity());

        return true;
    }

    // -----------------------------------------------------------------------
    // Trunk
    // -----------------------------------------------------------------------

    private List<BlockPos> growTrunk(BlockPos base, int height, double leanAngle, double leanAmount) {
        List<BlockPos> blocks = new ArrayList<>();
        double x = base.getX() + 0.5;
        double y = base.getY();
        double z = base.getZ() + 0.5;
        double dx = Math.cos(leanAngle) * leanAmount;
        double dz = Math.sin(leanAngle) * leanAmount;
        for (int i = 0; i < height; i++) {
            blocks.add(BlockPos.containing(x, y + i, z));
            x += dx;
            z += dz;
        }
        return blocks;
    }

    // -----------------------------------------------------------------------
    // Stub branches (variants 2 and 3 only)
    // -----------------------------------------------------------------------

    private void placeStubBranches(LevelAccessor level, BlockPos trunkTop, double leanAngle, RandomSource rand) {
        int count = 1 + rand.nextInt(2);
        for (int i = 0; i < count; i++) {
            // Spread stubs around the lean direction so they fan slightly
            double angle = leanAngle + Math.PI * (0.25 + i * 0.55) + (rand.nextDouble() - 0.5) * 0.35;
            int len = 1 + rand.nextInt(2);
            double x = trunkTop.getX() + 0.5;
            double y = trunkTop.getY() + rand.nextInt(2);
            double z = trunkTop.getZ() + 0.5;
            for (int s = 0; s < len; s++) {
                x += Math.cos(angle) * 0.85;
                y += 0.25;
                z += Math.sin(angle) * 0.85;
                setIfAirOrLeaves(level, BlockPos.containing(x, y, z), WOOD);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Canopy
    // -----------------------------------------------------------------------

    /**
     * Oblate domed canopy — wider than tall, dense, with sagging sides.
     *
     * 1. Oblate shape — rx/rz >> ry, so the dome is always visibly wider than tall.
     *    The bottom half uses a slightly compressed ry (×0.80) to push leaves
     *    outward at the lower edge rather than curving smoothly under.
     *
     * 2. Surface noise — per-voxel position hash perturbs the shell radius by ±15 %,
     *    producing a lumpy rather than perfectly smooth silhouette.
     *
     * 3. Crown taper — fill probability falls off linearly from the equator to the
     *    apex, so the top rounds off instead of reading as a flat or squared cap.
     *
     * 4. Density gradient — interior fills at full density; the shell thins toward
     *    the boundary for a soft, irregular outer feel with no internal air gaps.
     *
     * 5. Edge skirt — after the main ellipsoid, the outer horizontal ring of the
     *    canopy has leaves extended 1 block (and occasionally 2, noise-driven)
     *    straight downward, giving the sides a drooping/sagging silhouette.
     */
    private void paintCanopy(LevelAccessor level, Vec3 center, int rx, int ry,
                              RandomSource rand, float baseDensity) {
        int rz = rx; // symmetric on the horizontal plane
        float density = Mth.clamp(baseDensity, 0.84f, 0.96f);

        int cx = (int) center.x;
        int cy = (int) center.y;
        int cz = (int) center.z;

        // Pass 1 — oblate ellipsoid body with taper and noise
        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dy = -ry - 1; dy <= ry + 1; dy++) {
                for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                    double nx = dx / (double) rx;
                    // Bottom droop: lower half uses a compressed ry so the dome
                    // flares outward slightly instead of curving under cleanly
                    double effectiveRy = dy < 0 ? ry * 0.80 : ry;
                    double ny = dy / effectiveRy;
                    double nz = dz / (double) rz;
                    double dist2 = nx * nx + ny * ny + nz * nz;

                    // Per-voxel noise perturbs the effective shell boundary
                    long nh = hashPos(cx + dx, cy + dy, cz + dz);
                    double noise = (nh & 0xFFFFL) / (double) 0xFFFFL; // 0.0–1.0
                    double shell = 1.0 + (noise - 0.5) * 0.30;        // 0.85–1.15

                    if (dist2 <= shell * shell) {
                        BlockPos p = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);

                        // Density gradient: interior dense, outer shell thinner
                        float fill = dist2 < 0.50
                                ? density
                                : density * (float) (1.0 - (dist2 - 0.50) * 0.55);

                        // Crown taper: above the equator, linearly reduce fill toward apex
                        // so the top rounds off rather than reading as a flat cap
                        if (dy > 0) {
                            float topT = dy / (float) ry; // 0 at equator → 1 at apex
                            fill *= 1.0f - topT * 0.65f;
                        }

                        if (rand.nextFloat() <= fill && level.getBlockState(p).isAir()) {
                            level.setBlock(p, LEAVES, 3);
                        }
                    }
                }
            }
        }

        // Pass 2 — edge skirt: at the outer horizontal ring of the canopy, drop
        // leaves 1 block downward (occasionally 2, noise-driven) so the sides
        // sag visibly rather than cutting off as a vertical wall.
        for (int dx = -(rx + 1); dx <= rx + 1; dx++) {
            for (int dz = -(rz + 1); dz <= rz + 1; dz++) {
                double nx = dx / (double) rx;
                double nz = dz / (double) rz;
                double hDist2 = nx * nx + nz * nz;

                // Only the outer 35–120 % of the horizontal radius (rim, not interior)
                if (hDist2 < 0.42 || hDist2 > 1.28) continue;

                long nh = hashPos(cx + dx, cy - 1, cz + dz);
                float skirtNoise = (float) ((nh & 0xFFFFL) / (double) 0xFFFFL);

                // 1-block drop — most of the rim gets this
                BlockPos p1 = BlockPos.containing(center.x + dx, center.y - 1, center.z + dz);
                if (rand.nextFloat() < 0.72f && level.getBlockState(p1).isAir()) {
                    level.setBlock(p1, LEAVES, 3);
                }

                // 2-block drop — sparse, driven by noise so it's distributed unevenly
                if (skirtNoise > 0.48f) {
                    BlockPos p2 = BlockPos.containing(center.x + dx, center.y - 2, center.z + dz);
                    if (rand.nextFloat() < 0.38f && level.getBlockState(p2).isAir()) {
                        level.setBlock(p2, LEAVES, 3);
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static long hashPos(int x, int y, int z) {
        long h = (long) x * 1610612741L ^ (long) y * 805306457L ^ (long) z * 402653189L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    private void setIfAirOrLeaves(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.getBlock() == LEAVES.getBlock()) {
            level.setBlock(pos, state, 3);
        }
    }
}
