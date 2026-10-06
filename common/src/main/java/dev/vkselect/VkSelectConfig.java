package dev.vkselect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The persisted mod configuration.
 *
 * <p>The file lives in the loader's config directory and is written as plain JSON so it can be
 * edited by hand:
 *
 * <pre>{@code
 * {
 *   "enabled": true,
 *   "selectedDevice": "NVIDIA GeForce RTX 5060 Ti",
 *   "showInMainMenu": true,
 *   "showInPauseMenu": true
 * }
 * }</pre>
 *
 * <p>It is read while the Vulkan backend is created, which happens before the loader entrypoints
 * hand out their own configuration APIs, so it deliberately only relies on {@link Env}.
 */
public final class VkSelectConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile VkSelectConfig instance;

    /** Master switch; {@code -Dvkselect.disabled=true} overrides it without editing the file. */
    private boolean enabled = true;

    /** The full name of the device to use, or an empty string to keep vanilla behaviour. */
    private String selectedDevice = "";

    /** Whether the overlay button is added to the title screen. */
    private boolean showInMainMenu = true;

    /** Whether the overlay button is added to the in-game pause menu. */
    private boolean showInPauseMenu = true;

    /** Whether the device list should be logged while the game picks a device. */
    private boolean logDeviceSelection = true;

    public static VkSelectConfig get() {
        VkSelectConfig current = instance;
        if (current == null) {
            synchronized (VkSelectConfig.class) {
                current = instance;
                if (current == null) {
                    current = load();
                    instance = current;
                }
            }
        }
        return current;
    }

    private static VkSelectConfig load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return new VkSelectConfig();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            VkSelectConfig config = new VkSelectConfig();
            config.enabled = readBoolean(root, "enabled", config.enabled);
            config.selectedDevice = readString(root, "selectedDevice", config.selectedDevice);
            config.showInMainMenu = readBoolean(root, "showInMainMenu", config.showInMainMenu);
            config.showInPauseMenu = readBoolean(root, "showInPauseMenu", config.showInPauseMenu);
            config.logDeviceSelection = readBoolean(root, "logDeviceSelection", config.logDeviceSelection);
            VkSelect.LOGGER.info("[vkselect] Loaded configuration from {}", file);
            return config;
        } catch (Exception e) {
            VkSelect.LOGGER.warn("[vkselect] Could not read {} - using defaults", file, e);
            return new VkSelectConfig();
        }
    }

    private static String readString(JsonObject root, String key, String fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : fallback;
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsBoolean() : fallback;
    }

    public static Path file() {
        return Env.configDir().resolve(VkSelect.CONFIG_FILE);
    }

    /** Saves the current values and replaces the cached instance. */
    public void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", this.enabled);
            root.addProperty("selectedDevice", this.selectedDevice);
            root.addProperty("showInMainMenu", this.showInMainMenu);
            root.addProperty("showInPauseMenu", this.showInPauseMenu);
            root.addProperty("logDeviceSelection", this.logDeviceSelection);
            Files.writeString(file, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            instance = this;
            VkSelect.LOGGER.info("[vkselect] Saved configuration to {}", file);
        } catch (IOException e) {
            VkSelect.LOGGER.error("[vkselect] Could not write {}", file, e);
        }
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public String selectedDevice() {
        return this.selectedDevice;
    }

    public void setSelectedDevice(String name) {
        this.selectedDevice = name == null ? "" : name;
    }

    public boolean hasSelection() {
        return !this.selectedDevice.isBlank();
    }

    public boolean showInMainMenu() {
        return this.showInMainMenu;
    }

    public void setShowInMainMenu(boolean value) {
        this.showInMainMenu = value;
    }

    public boolean showInPauseMenu() {
        return this.showInPauseMenu;
    }

    public void setShowInPauseMenu(boolean value) {
        this.showInPauseMenu = value;
    }

    public boolean logDeviceSelection() {
        return this.logDeviceSelection;
    }

    public void setLogDeviceSelection(boolean value) {
        this.logDeviceSelection = value;
    }
}
