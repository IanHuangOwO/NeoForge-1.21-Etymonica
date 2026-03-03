package org.iansaididontcare.etymonica.registry.jar.api;

import java.util.Optional;

public record JarTypeStats(
    int capacity,
    Optional<ZombieJarStats> zombieSpecial
) {
    public JarTypeStats {
        capacity = Math.max(1, capacity);
    }

    public static final JarTypeStats DEFAULT = new JarTypeStats(8000, Optional.empty());
}
