package dev.vkselect.mixin;

import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import com.mojang.renderpearl.backend.vulkan.VulkanInstance;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import dev.vkselect.DeviceSelection;
import dev.vkselect.VkSelectConfig;
import dev.vkselect.vulkan.VulkanDeviceEnumerator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the vanilla device selection.
 *
 * <p>Minecraft 26.3 picks the Vulkan physical device in
 * {@code VulkanBackend.findPhysicalDevice(VulkanInstance, Set, Set)}: it enumerates every device,
 * skips the ones that cannot run the game, and otherwise prefers a discrete GPU. There is no
 * configuration option for it, so this mixin takes over at the head of the method whenever the user
 * stored a preference with {@code /vkselect select} or the GUI.
 *
 * <p>If the keyword does not match anything, or the matched device cannot run the current version
 * of the game, the injection falls through to the vanilla behaviour so a bad preference can never
 * break the game.
 */
@Mixin(VulkanBackend.class)
public abstract class VulkanBackendMixin {
    @Unique
    private static final Logger VKSELECT_LOGGER = LogUtils.getLogger();

    /** Escape hatch for a machine where a stored preference cannot boot: {@code -Dvkselect.disabled=true}. */
    @Unique
    private static final String DISABLE_PROPERTY = "vkselect.disabled";

    @Inject(method = "findPhysicalDevice", at = @At("HEAD"), cancellable = true)
    private static void vkselect$useConfiguredDevice(VulkanInstance instance,
                                                     Set<FeatureSet> requiredFeatureSets,
                                                     Set<FeatureSet> requiredIfExtensionsAvailable,
                                                     CallbackInfoReturnable<VulkanPhysicalDevice> cir) {
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            return;
        }

        String keyword = null;
        try {
            VkSelectConfig config = VkSelectConfig.get();
            if (!config.enabled() || !config.hasSelection()) {
                return;
            }
            keyword = config.selectedDevice();

            List<VulkanDeviceEnumerator.RawDevice> devices = VulkanDeviceEnumerator.rawDevices(instance.vkInstance());
            if (config.logDeviceSelection()) {
                for (VulkanDeviceEnumerator.RawDevice device : devices) {
                    VKSELECT_LOGGER.info("[vkselect] Vulkan device [{}] {}", device.index(), device.name());
                }
            }

            Optional<VulkanDeviceEnumerator.RawDevice> match =
                    DeviceSelection.matchByName(devices, VulkanDeviceEnumerator.RawDevice::name, keyword);
            if (match.isEmpty()) {
                VKSELECT_LOGGER.warn("[vkselect] No Vulkan device matches '{}'; using the default selection", keyword);
                return;
            }

            VulkanDeviceEnumerator.RawDevice chosen = match.get();

            // A device is only used when Minecraft's own suitability check accepts it. If the check
            // cannot be run at all, the vanilla choice is kept instead of gambling on the device.
            BackendCreationException unsupported;
            try {
                unsupported = VulkanBackend.checkDeviceSuitability(chosen.handle(), requiredFeatureSets, requiredIfExtensionsAvailable);
            } catch (Throwable t) {
                VKSELECT_LOGGER.warn("[vkselect] Could not verify that '{}' is usable; using the default selection",
                        chosen.name(), t);
                return;
            }
            if (unsupported != null) {
                VKSELECT_LOGGER.warn("[vkselect] '{}' cannot run this version of Minecraft ({}); using the default selection",
                        chosen.name(), unsupported.getMessage());
                return;
            }

            VKSELECT_LOGGER.info("[vkselect] Using the configured Vulkan device: {}", chosen.name());
            cir.setReturnValue(new VulkanPhysicalDevice(chosen.handle()));
        } catch (Throwable t) {
            VKSELECT_LOGGER.warn("[vkselect] Could not apply the configured device '{}'; using the default selection",
                    keyword, t);
        }
    }
}
