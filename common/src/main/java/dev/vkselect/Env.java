package dev.vkselect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Loader agnostic access to the few things that differ between Fabric and NeoForge.
 *
 * <p>The platform is normally installed by the loader entrypoint, but the device selection happens
 * while Minecraft is still starting up (the render backend is created from the {@code Minecraft}
 * constructor), so a reflective fallback keeps the mod working even before the entrypoint ran.
 */
public final class Env {
    /** Implemented once per loader. */
    public interface Platform {
        Path configDir();

        Path gameDir();

        boolean isModLoaded(String modId);
    }

    private static volatile Platform platform;

    private Env() {
    }

    /** Called by the loader entrypoint. */
    public static void setPlatform(Platform value) {
        platform = value;
    }

    public static Platform platform() {
        Platform current = platform;
        if (current != null) {
            return current;
        }
        synchronized (Env.class) {
            if (platform == null) {
                platform = discover();
            }
            return platform;
        }
    }

    public static Path configDir() {
        return platform().configDir();
    }

    public static Path gameDir() {
        return platform().gameDir();
    }

    public static boolean isModLoaded(String modId) {
        return platform().isModLoaded(modId);
    }

    private static Platform discover() {
        // NeoForge is probed first: on a NeoForge instance that also has fabric-loader on the
        // classpath (Sinytra Connector) the running loader is NeoForge, so its directories must win.
        // On a plain Fabric instance the FML classes are simply absent.
        Platform neoForge = discoverNeoForge();
        if (neoForge != null) {
            return neoForge;
        }
        Platform fabric = discoverFabric();
        if (fabric != null) {
            return fabric;
        }
        Path gameDir = fallbackGameDir();
        return new SimplePlatform(gameDir.resolve("config"), gameDir, id -> false);
    }

    @Nullable
    private static Platform discoverFabric() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Path configDir = (Path) loaderClass.getMethod("getConfigDir").invoke(loader);
            Path gameDir = (Path) loaderClass.getMethod("getGameDir").invoke(loader);
            Method isModLoaded = loaderClass.getMethod("isModLoaded", String.class);
            return new SimplePlatform(configDir, gameDir, id -> {
                try {
                    return Boolean.TRUE.equals(isModLoaded.invoke(loader, id));
                } catch (Throwable t) {
                    return false;
                }
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Platform discoverNeoForge() {
        try {
            Class<?> pathsClass = Class.forName("net.neoforged.fml.loading.FMLPaths");
            Path configDir = readPathHolder(pathsClass.getField("CONFIGDIR"));
            Path gameDir = readPathHolder(pathsClass.getField("GAMEDIR"));
            if (configDir == null || gameDir == null) {
                return null;
            }
            return new SimplePlatform(configDir, gameDir, id -> false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * NeoForge has used both {@code Path} fields and {@code PathHandler} wrappers for
     * {@code FMLPaths}, so accept both shapes.
     */
    @Nullable
    private static Path readPathHolder(Field field) throws ReflectiveOperationException {
        Object value = field.get(null);
        if (value instanceof Path path) {
            return path;
        }
        if (value == null) {
            return null;
        }
        Method get = value.getClass().getMethod("get");
        Object result = get.invoke(value);
        return result instanceof Path path ? path : null;
    }

    /** Last resort: the game directory from Minecraft itself, or the working directory. */
    private static Path fallbackGameDir() {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft != null && minecraft.gameDirectory != null) {
                return minecraft.gameDirectory.toPath();
            }
        } catch (Throwable ignored) {
            // Minecraft is not initialised yet.
        }
        return Path.of(".").toAbsolutePath().normalize();
    }

    /** Creates the config directory if it does not exist yet. */
    public static Path ensureConfigDir() {
        Path dir = configDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            VkSelect.LOGGER.warn("[vkselect] Could not create config directory {}", dir, e);
        }
        return dir;
    }

    private record SimplePlatform(Path configDir, Path gameDir,
                                  java.util.function.Predicate<String> modLoaded) implements Platform {
        @Override
        public boolean isModLoaded(String modId) {
            return this.modLoaded.test(modId);
        }
    }
}
