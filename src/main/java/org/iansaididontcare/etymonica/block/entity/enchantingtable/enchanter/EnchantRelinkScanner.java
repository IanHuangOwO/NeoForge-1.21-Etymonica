package org.iansaididontcare.etymonica.block.entity.enchantingtable.enchanter;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class EnchantRelinkScanner {
    private final int radius;
    private final int maxDistSq;
    private final int cap;
    private final int totalPositions;

    private int x;
    private int y;
    private int z;
    private int scannedPositions;
    private boolean active;

    public EnchantRelinkScanner(int radius, int cap) {
        this.radius = radius;
        this.maxDistSq = radius * radius;
        this.cap = cap;
        this.x = -radius;
        this.y = -radius;
        this.z = -radius;
        int side = (radius * 2) + 1;
        this.totalPositions = side * side * side;
        this.scannedPositions = 0;
        this.active = radius > 0 && cap > 0;
    }

    public boolean isActive() {
        return active;
    }

    public void stop() {
        this.active = false;
    }

    public int getCap() {
        return cap;
    }

    public int getTotalPositions() {
        return totalPositions;
    }

    public int getScannedPositions() {
        return scannedPositions;
    }

    @Nullable
    public BlockPos nextCandidate(BlockPos origin, int currentLinkedCount) {
        while (active) {
            if (x > radius) {
                active = false;
                return null;
            }

            int dx = x;
            int dy = y;
            int dz = z;

            z++;
            if (z > radius) {
                z = -radius;
                y++;
                if (y > radius) {
                    y = -radius;
                    x++;
                }
            }

            scannedPositions++;

            if (dx == 0 && dy == 0 && dz == 0) continue;
            int d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > maxDistSq) continue;

            if (currentLinkedCount >= cap) {
                active = false;
                return null;
            }

            return origin.offset(dx, dy, dz);
        }

        return null;
    }
}
