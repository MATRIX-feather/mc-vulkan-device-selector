package dev.vkselect.vulkan;

import com.mojang.renderpearl.api.device.DeviceType;
import dev.vkselect.VkSelect;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceDriverProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

/**
 * Enumerates the Vulkan physical devices of the machine.
 *
 * <p>Minecraft 26.3 only creates a Vulkan instance when it actually runs on the Vulkan backend,
 * so this class creates a short lived instance of its own. That makes the device list available
 * from the main menu (and from {@code /vkselect list}) even while the game is still running on
 * OpenGL.
 */
public final class VulkanDeviceEnumerator {
    /** The Vulkan API version Minecraft itself requests. */
    private static final int REQUESTED_API_VERSION = VK12.VK_API_VERSION_1_2;

    private static final int VK_SUCCESS = 0;

    private VulkanDeviceEnumerator() {
    }

    /** The outcome of an enumeration attempt: either a list of devices or a human readable error. */
    public record Result(List<VulkanDevice> devices, @Nullable String error) {
        public boolean ok() {
            return this.error == null;
        }
    }

    /** A physical device together with the enumeration index reported by the driver. */
    public record RawDevice(int index, String name, VkPhysicalDevice handle) {
    }

    /**
     * Enumerates every Vulkan device of the machine. Never throws: failures are reported through
     * {@link Result#error()} so the caller can show them in the GUI or the command output.
     */
    public static Result enumerate() {
        try {
            List<VulkanDevice> devices = enumerateOrThrow();
            if (devices.isEmpty()) {
                return new Result(List.of(), "No Vulkan capable devices were found.");
            }
            return new Result(List.copyOf(devices), null);
        } catch (Throwable t) {
            VkSelect.LOGGER.warn("[vkselect] Failed to enumerate Vulkan devices", t);
            String message = t.getMessage();
            return new Result(List.of(), message == null || message.isBlank()
                    ? t.getClass().getSimpleName()
                    : message);
        }
    }

    /** Creates a temporary instance and returns every device, or throws. */
    private static List<VulkanDevice> enumerateOrThrow() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo applicationInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8("Minecraft Java Edition"))
                    .applicationVersion(0)
                    .pEngineName(stack.UTF8("MinecraftJE"))
                    .engineVersion(0)
                    .apiVersion(REQUESTED_API_VERSION);
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(applicationInfo);

            // MoltenVK only enumerates its devices when the portability enumeration bit is set, and
            // Vulkan requires the matching extension to be enabled at the same time.
            if (hasInstanceExtension("VK_KHR_portability_enumeration")) {
                PointerBuffer portabilityExtension = stack.callocPointer(1);
                portabilityExtension.put(0, stack.UTF8("VK_KHR_portability_enumeration"));
                portabilityExtension.flip();
                createInfo.flags(KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)
                        .ppEnabledExtensionNames(portabilityExtension);
            }

            PointerBuffer pInstance = stack.callocPointer(1);
            int result = VK12.vkCreateInstance(createInfo, null, pInstance);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateInstance failed with VkResult " + result);
            }

            VkInstance instance = new VkInstance(pInstance.get(0), createInfo);
            try {
                LongBuffer devices = enumerateHandles(stack, instance);

                List<VulkanDevice> out = new ArrayList<>(devices.capacity());
                for (int i = 0; i < devices.capacity(); i++) {
                    VkPhysicalDevice handle = new VkPhysicalDevice(devices.get(i), instance);
                    out.add(describe(stack, handle, i));
                }
                return out;
            } finally {
                VK12.vkDestroyInstance(instance, null);
            }
        }
    }

    /** Raw device handles of an already existing instance, used while the game picks its device. */
    public static List<RawDevice> rawDevices(VkInstance instance) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handles = enumerateHandles(stack, instance);
            List<RawDevice> devices = new ArrayList<>(handles.capacity());
            for (int i = 0; i < handles.capacity(); i++) {
                VkPhysicalDevice handle = new VkPhysicalDevice(handles.get(i), instance);
                devices.add(new RawDevice(i, nameOf(stack, handle), handle));
            }
            return devices;
        }
    }

    /** Cheap name lookup, used for matching a saved preference against a device handle. */
    public static String nameOf(VkPhysicalDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return nameOf(stack, device);
        }
    }

    private static String nameOf(MemoryStack stack, VkPhysicalDevice device) {
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
        VK12.vkGetPhysicalDeviceProperties(device, properties);
        String name = properties.deviceNameString();
        return name == null ? "" : name;
    }

    private static LongBuffer enumerateHandles(MemoryStack stack, VkInstance instance) {
        IntBuffer count = stack.callocInt(1);
        int result = VK12.vkEnumeratePhysicalDevices(instance, count, null);
        if (result != VK_SUCCESS) {
            throw new IllegalStateException("vkEnumeratePhysicalDevices failed with VkResult " + result);
        }
        int deviceCount = count.get(0);
        if (deviceCount <= 0) {
            return stack.mallocLong(0);
        }

        PointerBuffer pointers = stack.callocPointer(deviceCount);
        result = VK12.vkEnumeratePhysicalDevices(instance, count, pointers);
        if (result != VK_SUCCESS) {
            throw new IllegalStateException("vkEnumeratePhysicalDevices failed with VkResult " + result);
        }

        int actual = count.get(0);
        LongBuffer handles = stack.mallocLong(actual);
        for (int i = 0; i < actual; i++) {
            handles.put(i, pointers.get(i));
        }
        return handles;
    }

    private static VulkanDevice describe(MemoryStack stack, VkPhysicalDevice device, int index) {
        VkPhysicalDeviceProperties2 properties2 = VkPhysicalDeviceProperties2.calloc(stack).sType$Default();

        // VkPhysicalDeviceDriverProperties is core since Vulkan 1.2 (VK_KHR_driver_properties).
        // Chaining it into a device that only reports 1.0/1.1 would be out of spec, so it is only
        // requested when the device is new enough; driverName/driverVersion then fall back.
        boolean hasDriverProperties = driverPropertiesSupported(stack, device);
        VkPhysicalDeviceDriverProperties driverProperties =
                VkPhysicalDeviceDriverProperties.calloc(stack).sType$Default();
        if (hasDriverProperties) {
            properties2.pNext(driverProperties);
        }
        VK12.vkGetPhysicalDeviceProperties2(device, properties2);

        VkPhysicalDeviceProperties properties = properties2.properties();
        int vendorId = properties.vendorID();
        int driverId = hasDriverProperties ? driverProperties.driverID() : -1;
        String driverName = hasDriverProperties ? driverProperties.driverNameString() : null;
        String driverInfo = hasDriverProperties ? driverProperties.driverInfoString() : null;

        return new VulkanDevice(
                index,
                nameOf(stack, device),
                vendorName(vendorId),
                driverName == null ? "" : driverName,
                VulkanDriverVersions.format(vendorId, properties.driverVersion(), driverId),
                driverInfo == null ? "" : driverInfo,
                VulkanDriverVersions.apiVersion(properties.apiVersion()),
                deviceType(properties.deviceType()),
                false);
    }

    private static boolean driverPropertiesSupported(MemoryStack stack, VkPhysicalDevice device) {
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
        VK12.vkGetPhysicalDeviceProperties(device, properties);
        return properties.apiVersion() >= VK12.VK_API_VERSION_1_2;
    }

    private static DeviceType deviceType(int vulkanDeviceType) {
        return switch (vulkanDeviceType) {
            case 1 -> DeviceType.INTEGRATED;
            case 2 -> DeviceType.DISCRETE;
            case 3 -> DeviceType.VIRTUAL;
            case 4 -> DeviceType.CPU;
            default -> DeviceType.OTHER;
        };
    }

    private static String vendorName(int vendorId) {
        return switch (vendorId) {
            case VulkanDriverVersions.VENDOR_NVIDIA -> "NVIDIA";
            case VulkanDriverVersions.VENDOR_AMD -> "AMD";
            case VulkanDriverVersions.VENDOR_INTEL -> "Intel";
            case VulkanDriverVersions.VENDOR_MESA -> "Mesa";
            case 0x5143 -> "Qualcomm";
            case 0x1010 -> "Imagination";
            case 0x13B5 -> "ARM";
            case 0x1AE0 -> "Google";
            case 0x10006 -> "NVIDIA (Vulkan SC)";
            default -> String.format("0x%04X", vendorId);
        };
    }

    private static boolean hasInstanceExtension(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.callocInt(1);
            if (VK12.vkEnumerateInstanceExtensionProperties((String) null, count, null) != VK_SUCCESS) {
                return false;
            }
            int total = count.get(0);
            if (total <= 0) {
                return false;
            }
            VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(total, stack);
            if (VK12.vkEnumerateInstanceExtensionProperties((String) null, count, extensions) != VK_SUCCESS) {
                return false;
            }
            for (int i = 0; i < extensions.capacity(); i++) {
                if (name.equals(extensions.get(i).extensionNameString())) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            VkSelect.LOGGER.debug("[vkselect] Could not query instance extensions", t);
            return false;
        }
    }

    /** Unmodifiable empty result, mostly useful for tests. */
    public static Result empty(String error) {
        return new Result(Collections.emptyList(), error);
    }
}
