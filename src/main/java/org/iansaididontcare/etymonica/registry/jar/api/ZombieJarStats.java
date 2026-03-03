package org.iansaididontcare.etymonica.registry.jar.api;

public record ZombieJarStats(
    int xpToMb,
    int xpPerDrain,
    int interval,
    double radius
) {
    public static final ZombieJarStats DEFAULT = new ZombieJarStats(10, 10, 20, 1.0d);
}
