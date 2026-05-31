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
 * Shape: short visible trunk (3–5 blocks), lumpy domed canopy (radius 3–5 blocks),
 * dense leaves with few internal gaps. The canopy droops slightly at the edges so
 * the tree reads as full and heavy rather than a ball on a stick.
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
        // variant 0: medium height,  wide dome,  leans north-ish
        // variant 1: shorter,        taller dome, leans east-ish
        // variant 2: taller trunk,   wide dome,  leans south-ish  (+stub branches)
        // variant 3: short trunk,    squat dome,  leans west-ish  (+stub branches)
        int trunkHeight  = 3 + (variant % 3);                     // 3, 4, 4, or 3
        int totalHeight  = Mth.nextInt(rand, def.minHeight(), def.maxHeight());
        int canopyRadiusH = 3 + (variant / 2) + rand.nextInt(2);  // 3–5 horizontal
        int canopyRadiusV = canopyRadiusH - 1 + rand.nextInt(2);   // slightly flatter than wide
        double leanAngle  = (variant * Math.PI * 0.5) + (rand.nextDouble() - 0.5) * 0.5;
        double leanAmount = def.canLean() ? 0.04 + rand.nextDouble() * 0.07 : 0.0;

        // Suppress unused totalHeight — it drives variation via RNG consumption
        // (consuming the same number of rand calls per variant keeps other
        // parameters stable even when height isn't directly used below)
        @SuppressWarnings("unused") int _consumed = totalHeight;

        // Build trunk
        List<BlockPos> trunkBlocks = growTrunk(base, trunkHeight, leanAngle, leanAmount);
        BlockPos trunkTop = trunkBlocks.get(trunkBlocks.size() - 1);

        // Canopy center: 1 block above trunk top, with a tiny random offset for character
        Vec3 canopyCenter = new Vec3(
                trunkTop.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.4,
                trunkTop.getY() + 1.0,
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
     * Lumpy domed canopy with three characteristics:
     *
     * 1. Surface noise — per-voxel position hash perturbs the effective shell radius
     *    by ±15 %, producing an irregular silhouette.
     *
     * 2. Edge droop — the bottom half of the canopy uses a compressed vertical radius
     *    (×0.82), which pushes leaves slightly outward at the bottom and makes the
     *    dome sag naturally rather than cut off in a straight line.
     *
     * 3. Density gradient — voxels deep inside the ellipsoid fill at full density;
     *    voxels near the surface thin out so there are almost no internal air gaps
     *    but the outer shell has a soft, irregular feel.
     */
    private void paintCanopy(LevelAccessor level, Vec3 center, int rx, int ry,
                              RandomSource rand, float baseDensity) {
        int rz = rx; // symmetric on the horizontal plane
        float density = Mth.clamp(baseDensity, 0.84f, 0.96f);

        int cx = (int) center.x;
        int cy = (int) center.y;
        int cz = (int) center.z;

        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            for (int dy = -ry - 1; dy <= ry + 1; dy++) {
                for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                    double nx = dx / (double) rx;
                    // Droop: bottom half uses a tighter vertical scale so the dome
                    // bulges outward slightly at the lower edge
                    double effectiveRy = dy < 0 ? ry * 0.82 : ry;
                    double ny = dy / effectiveRy;
                    double nz = dz / (double) rz;
                    double dist2 = nx * nx + ny * ny + nz * nz;

                    // Stable surface noise from position hash — makes silhouette lumpy
                    long nh = hashPos(cx + dx, cy + dy, cz + dz);
                    double noise = (nh & 0xFFFFL) / (double) 0xFFFFL; // 0.0–1.0
                    double shell = 1.0 + (noise - 0.5) * 0.30;        // 0.85–1.15

                    if (dist2 <= shell * shell) {
                        BlockPos p = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);
                        // Interior fills densely; shell thins toward edge
                        float fill = dist2 < 0.50
                                ? density
                                : density * (float) (1.0 - (dist2 - 0.50) * 0.55);
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
