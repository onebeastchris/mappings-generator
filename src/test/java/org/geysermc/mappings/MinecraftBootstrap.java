package org.geysermc.mappings;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.geysermc.mappings.util.RegistryAccessUtil;

import java.lang.reflect.Method;

/// Boots Minecraft's registries for unit tests (run by fabric-loader-junit, so the game classes are loaded like in the generator)
public final class MinecraftBootstrap {
    private static boolean done;
    private static boolean components;

    private MinecraftBootstrap() {}

    public static synchronized void bootstrap() {
        if (!done) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            done = true;
        }
    }

    /// Also loads the data-driven registries and binds the default item components, like the generator does
    public static synchronized void bootstrapWithComponents() {
        bootstrap();
        if (!components) {
            freezeBuiltInRegistries();
            RegistryAccessUtil.create().join();
            components = true;
        }
    }

    /// Fabric API leaves the built-in registries unfrozen after the bootstrap (mods may still register); the datagen
    /// entrypoint freezes them before generators run, a unit test has to do it itself
    private static void freezeBuiltInRegistries() {
        try {
            Method freeze = BuiltInRegistries.class.getDeclaredMethod("freeze");
            freeze.setAccessible(true);
            freeze.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not freeze the built-in registries", e);
        }
    }
}
