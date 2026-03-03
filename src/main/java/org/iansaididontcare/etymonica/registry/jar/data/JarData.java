package org.iansaididontcare.etymonica.registry.jar.data;

import net.minecraft.resources.Identifier;
import org.iansaididontcare.etymonica.registry.jar.api.JarTypeStats;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class JarData {
    private JarData() {}

    private static volatile Map<Identifier, JarTypeStats> JAR_TYPES = Map.of();
    private static final AtomicLong REVISION = new AtomicLong(0L);

    public static JarTypeStats getJarType(Identifier jarId) {
        return JAR_TYPES.getOrDefault(jarId, JarTypeStats.DEFAULT);
    }

    public static long getRevision() {
        return REVISION.get();
    }

    public static void setJarTypes(Map<Identifier, JarTypeStats> jarTypes) {
        JAR_TYPES = Map.copyOf(jarTypes);
        REVISION.incrementAndGet();
    }
}
