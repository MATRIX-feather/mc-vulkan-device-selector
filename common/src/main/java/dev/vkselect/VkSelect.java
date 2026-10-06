package dev.vkselect;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Shared constants and the logger for the Vulkan Device Select mod.
 *
 * <p>This class lives in the loader-independent "common" source set, which is compiled into both
 * the Fabric and the NeoForge jar. It must therefore never reference a loader API directly.
 */
public final class VkSelect {
    /** Mod id used by both loaders. */
    public static final String MOD_ID = "vkselect";

    /** Human readable mod name, used by the GUIs. */
    public static final String MOD_NAME = "Vulkan Device Select";

    /** Name of the configuration file inside the loader's config directory. */
    public static final String CONFIG_FILE = "vkselect.json";

    /** Root translation key prefix. */
    public static final String LANG_PREFIX = "vkselect.";

    public static final Logger LOGGER = LogUtils.getLogger();

    private VkSelect() {
    }
}
