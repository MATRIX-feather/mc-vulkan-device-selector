package dev.vkselect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import dev.vkselect.vulkan.VulkanDevice;
import dev.vkselect.vulkan.VulkanDeviceEnumerator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Runtime helpers around the renderer: what device is currently in use and the cached device list
 * used by the command suggestions and the GUI.
 */
public final class VkSelectRuntime {
    /** How long an enumeration result may be reused, in milliseconds. */
    private static final long CACHE_MILLIS = 15_000L;

    private static volatile VulkanDeviceEnumerator.Result cached = null;
    private static volatile long cachedAt = 0L;

    private VkSelectRuntime() {
    }

    /** The device the current session runs on, or {@code null} when the backend is not ready. */
    @Nullable
    public static DeviceInfo currentDeviceInfo() {
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            return device == null ? null : device.getDeviceInfo();
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    public static String currentDeviceName() {
        DeviceInfo info = currentDeviceInfo();
        return info == null ? null : info.name();
    }

    /**
     * Enumerates the Vulkan devices, caching the result for a few seconds so that command
     * suggestions and GUI rebuilds do not create a Vulkan instance on every keystroke.
     */
    public static VulkanDeviceEnumerator.Result devices() {
        VulkanDeviceEnumerator.Result result = cached;
        if (result != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
            return result;
        }
        result = VulkanDeviceEnumerator.enumerate();
        cachedAt = System.currentTimeMillis();
        cached = result;
        return result;
    }

    /** Enumerates the devices and marks the one the current session runs on. */
    public static VulkanDeviceEnumerator.Result markedDevices() {
        VulkanDeviceEnumerator.Result result = devices();
        String currentName = currentDeviceName();
        if (currentName == null || result.devices().isEmpty()) {
            return result;
        }
        List<VulkanDevice> marked = result.devices().stream()
                .map(device -> device.withCurrent(device.name().equalsIgnoreCase(currentName)))
                .toList();
        return new VulkanDeviceEnumerator.Result(marked, result.error());
    }

    /** Drops the cache, e.g. after the user confirmed a new device. */
    public static void invalidateCache() {
        cached = null;
        cachedAt = 0L;
    }

    /**
     * Whether Minecraft is configured to prefer the Vulkan backend.
     *
     * <p>This cannot be derived from {@code getBackendsToTry()}: that method always returns both
     * backends and only swaps their order. Only the enum value itself says which one is tried first,
     * and the first backend that can create a device wins, so anything but {@link
     * net.minecraft.client.PreferredGraphicsApi#VULKAN} means the device selection is ignored.
     */
    public static boolean vulkanPreferred() {
        try {
            return net.minecraft.client.Minecraft.getInstance().options.preferredGraphicsBackend().get()
                    == net.minecraft.client.PreferredGraphicsApi.VULKAN;
        } catch (Throwable t) {
            VkSelect.LOGGER.debug("[vkselect] Could not read the preferred graphics API", t);
            return false;
        }
    }
}
