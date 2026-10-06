package dev.vkselect.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.vkselect.gui.VulkanDeviceSelectScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * ModMenu integration.
 *
 * <p>This class is only loaded when ModMenu requests the {@code modmenu} entrypoint, so ModMenu
 * stays a purely optional dependency: without it the class is never touched.
 */
public final class VkSelectModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // The explicit local variable keeps the wildcard type argument happy while still
        // describing the factory as "parent screen in, config screen out".
        ConfigScreenFactory<Screen> factory = VulkanDeviceSelectScreen::new;
        return factory;
    }
}
