package dev.vkselect;

import dev.vkselect.vulkan.VulkanDevice;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pure matching logic that turns a user supplied keyword into a device. */
public final class DeviceSelection {
    private DeviceSelection() {
    }

    /**
     * Matches a keyword against the enumerated devices the same way both the command and the GUI do:
     * an exact (case insensitive) name wins, otherwise the first device whose name contains the
     * keyword is used.
     *
     * @param devices the devices in enumeration order
     * @param keyword the user supplied keyword, may be {@code null}
     */
    public static Optional<VulkanDevice> match(List<VulkanDevice> devices, String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        String needle = keyword.trim();
        if (needle.isEmpty()) {
            return Optional.empty();
        }

        for (VulkanDevice device : devices) {
            if (device.name().equalsIgnoreCase(needle)) {
                return Optional.of(device);
            }
        }

        String lowered = needle.toLowerCase(Locale.ROOT);
        for (VulkanDevice device : devices) {
            if (device.name().toLowerCase(Locale.ROOT).contains(lowered)) {
                return Optional.of(device);
            }
        }

        return Optional.empty();
    }

    /** Matches a keyword against raw device names, used by the render backend hook. */
    public static <T> Optional<T> matchByName(List<T> candidates, java.util.function.Function<T, String> name,
                                              String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        String needle = keyword.trim();
        for (T candidate : candidates) {
            if (name.apply(candidate).equalsIgnoreCase(needle)) {
                return Optional.of(candidate);
            }
        }
        String lowered = needle.toLowerCase(Locale.ROOT);
        for (T candidate : candidates) {
            if (name.apply(candidate).toLowerCase(Locale.ROOT).contains(lowered)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
