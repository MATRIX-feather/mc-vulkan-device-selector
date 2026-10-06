package dev.vkselect.fabric;

import dev.vkselect.Env;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric implementation of the loader specific paths. */
public final class FabricPlatform implements Env.Platform {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
