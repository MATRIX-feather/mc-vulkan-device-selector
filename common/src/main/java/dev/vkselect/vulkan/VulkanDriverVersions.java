package dev.vkselect.vulkan;

import java.util.Locale;
import org.lwjgl.vulkan.VK12;

/**
 * Converts the raw {@code VkPhysicalDeviceProperties.driverVersion} integer into a readable
 * version string.
 *
 * <p>Vulkan only guarantees a "standard" encoding (major.minor.patch) and explicitly allows
 * vendors to use their own scheme. The decoding used here mirrors the well known vendor schemes;
 * anything unknown falls back to the standard encoding.
 */
public final class VulkanDriverVersions {
    public static final int VENDOR_NVIDIA = 0x10DE;
    public static final int VENDOR_AMD = 0x1002;
    public static final int VENDOR_INTEL = 0x8086;
    public static final int VENDOR_MESA = 0x10005;

    private VulkanDriverVersions() {
    }

    /**
     * @param vendorId the {@code VkPhysicalDeviceProperties.vendorID}
     * @param version  the raw {@code driverVersion}
     * @param driverId the {@code VkPhysicalDeviceDriverProperties.driverID}, {@code -1} when unknown
     */
    public static String format(int vendorId, int version, int driverId) {
        // Khronos / Mesa style, including RADV, ANV and llvmpipe. Mesa uses the standard
        // major.minor.patch encoding, which is what vulkaninfo reports as well.
        if (driverId == VK12.VK_DRIVER_ID_MESA_RADV
                || driverId == VK12.VK_DRIVER_ID_INTEL_OPEN_SOURCE_MESA
                || driverId == VK12.VK_DRIVER_ID_MESA_LLVMPIPE
                || vendorId == VENDOR_MESA) {
            return standard(version);
        }

        return switch (vendorId) {
            // NVIDIA and the Windows Intel drivers use a 10/8/8/6 bit split. Only the first three
            // numbers are meaningful to users, e.g. 580.65.06.
            case VENDOR_NVIDIA, VENDOR_INTEL -> String.format(Locale.ROOT, "%d.%02d.%02d",
                    version >>> 22 & 0x3FF, version >>> 14 & 0xFF, version >>> 6 & 0xFF);
            default -> standard(version);
        };
    }

    /** Vulkan's standard encoding: 10 bit major, 10 bit minor, 12 bit patch. */
    public static String standard(int version) {
        return String.format(Locale.ROOT, "%d.%d.%d",
                version >>> 22 & 0x3FF, version >>> 12 & 0x3FF, version & 0xFFF);
    }

    /** Renders {@code VkPhysicalDeviceProperties.apiVersion}, e.g. {@code 1.4.351}. */
    public static String apiVersion(int apiVersion) {
        return String.format(Locale.ROOT, "%d.%d.%d",
                apiVersion >>> 22 & 0x7F, apiVersion >>> 12 & 0x3FF, apiVersion & 0xFFF);
    }
}
