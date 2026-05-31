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
 * so the dome is always wider than tall). Dense leaves, smooth rounded underside,
 * crown taper, and surface noise — reads as a solid heavy dome, no drips or tendrils.
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
     * Oblate domed canopy — wider than tall, dense, clean rounded underside.
     *
     * Single-pass oblate ellipsoid. No edge extensions or skirts — the ellipsoid
     * curve naturally rounds the bottom back toward the trunk, which is the
     * correct silhouette (solid dome, not drippy/weeping).
     *
     * 1. Oblate shape: rx/rz >> ry, dome always wider than tall.
     *    Both halves use the same ry so the underside curves symmetrically
     *    back to center — smooth, not flared or dangling.
     * 2. Surface noise: ±12 % shell perturbation for a lumpy-not-smooth edge.
     * 3. Crown taper: fill probability drops toward the apex so the top
     *    rounds off rather than reading as a flat cap.
     * 4. Density gradient: interior fills at full density; outer shell thins
     *    slightly for a soft edge with no significant internal air gaps.
     */
    private void paintCanopy(LevelAccessor level, Vec3 center, int rx, int ry,
                              RandomSource rand, float baseDensity) {
        int rz = rx; // symmetric on the horizontal plane
        float density = Mth.clamp(baseDensity, 0.86f, 0.96f);

        int cx = (int) center.x;
        int cy = (int) center.y;
        int cz = (int) center.z;

        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dy = -ry - 1; dy <= ry + 1; dy++) {
                for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                    double nx = dx / (double) rx;
                    double ny = dy / (double) ry; // same ry top and bottom — clean ellipsoid
                    double nz = dz / (double) rz;
                    double dist2 = nx * nx + ny * ny + nz * nz;

                    // Per-voxel noise for lumpy-but-not-stringy edge (±12 %)
                    long nh = hashPos(cx + dx, cy + dy, cz + dz);
                    double noise = (nh & 0xFFFFL) / (double) 0xFFFFL; // 0.0–1.0
                    double shell = 1.0 + (noise - 0.5) * 0.24;        // 0.88–1.12

                    if (dist2 <= shell * shell) {
                        BlockPos p = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);

                        // Dense interior, slightly thinned outer shell
                        float fill = dist2 < 0.50
                                ? density
                                : density * (float) (1.0 - (dist2 - 0.50) * 0.45);

                        // Crown taper: thin toward apex so the top rounds off cleanly
                        if (dy > 0) {
                            float topT = dy / (float) ry;
                            fill *= 1.0f - topT * 0.60f;
                        }

                        if (rand.nextFloat() <= fill && level.getBlockState(p).isAir()) {
                            level.setBlock(p, LEAVES, 3);
                        }
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
