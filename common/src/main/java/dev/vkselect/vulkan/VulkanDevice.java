package dev.vkselect.vulkan;

import com.mojang.renderpearl.api.device.DeviceType;

/**
 * A single Vulkan physical device as reported by {@code vkEnumeratePhysicalDevices}.
 *
 * @param index         the enumeration index, which is also the {@code [index]} shown by
 *                      {@code /vkselect list}
 * @param name          {@code VkPhysicalDeviceProperties.deviceName}
 * @param vendorName    the device vendor as a readable string (e.g. {@code NVIDIA})
 * @param driverName    {@code VkPhysicalDeviceDriverProperties.driverName}
 * @param driverVersion a readable driver version, decoded using the vendor specific scheme when
 *                      one is known
 * @param driverInfo    {@code VkPhysicalDeviceDriverProperties.driverInfo}
 * @param apiVersion    the Vulkan API version supported by the device, e.g. {@code 1.4.351}
 * @param type          the renderpearl device type (discrete / integrated / virtual / CPU)
 * @param current       whether this is the device the current game session is running on
 */
public record VulkanDevice(
        int index,
        String name,
        String vendorName,
        String driverName,
        String driverVersion,
        String driverInfo,
        String apiVersion,
        DeviceType type,
        boolean current) {

    public VulkanDevice withCurrent(boolean current) {
        return new VulkanDevice(index, name, vendorName, driverName, driverVersion, driverInfo, apiVersion, type, current);
    }

    /** Short suffix used by the GUI and the list command, e.g. {@code (dGPU)}. */
    public String typeSuffix() {
        return switch (type) {
            case INTEGRATED -> " (iGPU)";
            case DISCRETE -> " (dGPU)";
            case VIRTUAL -> " (vGPU)";
            case CPU -> " (software)";
            default -> "";
        };
    }
}
