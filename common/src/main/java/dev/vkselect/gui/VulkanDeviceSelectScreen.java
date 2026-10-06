package dev.vkselect.gui;

import dev.vkselect.VkSelect;
import dev.vkselect.VkSelectConfig;
import dev.vkselect.VkSelectRuntime;
import dev.vkselect.vulkan.VulkanDevice;
import dev.vkselect.vulkan.VulkanDeviceEnumerator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The device selection overlay.
 *
 * <p>Layout follows the mock up: a title, the device list, the device the current session runs
 * on, and the Cancel / Confirm buttons. Confirming stores the chosen device in
 * {@code config/vkselect.json}; the game picks it up the next time it starts.
 */
public class VulkanDeviceSelectScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 360;
    private static final int PADDING = 14;
    private static final int TITLE_TOP = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONFIG_BUTTON_WIDTH = 60;
    private static final int CONFIG_BUTTON_HEIGHT = 20;
    private static final int LINE_HEIGHT = 11;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_HINT = 0xFFA0A0A0;
    private static final int COLOR_WARNING = 0xFFFFC04D;
    private static final int COLOR_ERROR = 0xFFFF6060;
    private static final int COLOR_BORDER = 0xFF000000;

    @Nullable
    private final Screen parent;

    @Nullable
    private DeviceList deviceList;
    @Nullable
    private Button confirmButton;

    @Nullable
    private String chosenDevice;
    @Nullable
    private String currentDeviceName;
    @Nullable
    private String missingSavedDevice;
    @Nullable
    private String error;

    /** Cached in {@link #init()}; computing it per frame would allocate two backends every frame. */
    private boolean vulkanPreferred;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int titleY;
    private int listTop;
    private int currentLineY;
    private int hintLineY;
    private int buttonY;

    public VulkanDeviceSelectScreen(@Nullable Screen parent) {
        super(Component.translatable(VkSelect.LANG_PREFIX + "gui.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        VkSelectConfig config = VkSelectConfig.get();
        VulkanDeviceEnumerator.Result result = VkSelectRuntime.markedDevices();
        List<VulkanDevice> devices = result.devices();
        this.error = result.error();
        this.currentDeviceName = VkSelectRuntime.currentDeviceName();
        this.vulkanPreferred = VkSelectRuntime.vulkanPreferred();
        this.missingSavedDevice = null;

        // init() also runs again when the window is resized, so an unconfirmed pick must survive it.
        if (this.chosenDevice == null) {
            if (config.hasSelection()) {
                String saved = config.selectedDevice();
                if (contains(devices, saved)) {
                    this.chosenDevice = saved;
                } else {
                    // The stored device is gone (driver renamed it, eGPU unplugged, ...). Keep the
                    // game usable: warn, preselect something valid and let the user confirm again.
                    this.missingSavedDevice = saved;
                    this.chosenDevice = fallbackSelection(devices);
                }
            } else {
                this.chosenDevice = fallbackSelection(devices);
            }
        } else if (config.hasSelection() && !contains(devices, config.selectedDevice())) {
            this.missingSavedDevice = config.selectedDevice();
        }

        int listHeight = DeviceList.VISIBLE_ROWS * DeviceList.ROW_HEIGHT;
        int titleRowHeight = Math.max(LINE_HEIGHT, CONFIG_BUTTON_HEIGHT);

        this.panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(200, this.width - 20));
        this.panelHeight = TITLE_TOP + titleRowHeight + 10 + listHeight + 12 + LINE_HEIGHT + LINE_HEIGHT + 14
                + BUTTON_HEIGHT + PADDING;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = Math.max(4, (this.height - this.panelHeight) / 2);
        this.titleY = this.panelY + TITLE_TOP;
        this.listTop = this.titleY + titleRowHeight + 10;
        this.currentLineY = this.listTop + listHeight + 12;
        this.hintLineY = this.currentLineY + LINE_HEIGHT;
        this.buttonY = this.hintLineY + LINE_HEIGHT + 14;

        int contentWidth = this.panelWidth - PADDING * 2;
        this.deviceList = this.addRenderableWidget(new DeviceList(
                this.panelX + PADDING,
                this.listTop,
                contentWidth,
                devices,
                this.chosenDevice,
                device -> this.chosenDevice = device.name()));

        // Entry point to the configuration page, top right like the mock up.
        this.addRenderableWidget(Button.builder(
                        Component.translatable(VkSelect.LANG_PREFIX + "gui.config.button"),
                        button -> this.openConfig())
                .bounds(this.panelX + this.panelWidth - PADDING - CONFIG_BUTTON_WIDTH, this.titleY,
                        CONFIG_BUTTON_WIDTH, CONFIG_BUTTON_HEIGHT)
                .build());

        int buttonWidth = Math.min(110, (contentWidth - 10) / 2);
        this.addRenderableWidget(Button.builder(
                        Component.translatable(VkSelect.LANG_PREFIX + "gui.cancel"),
                        button -> this.onClose())
                .bounds(this.panelX + PADDING, this.buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());

        this.confirmButton = this.addRenderableWidget(Button.builder(
                        Component.translatable(VkSelect.LANG_PREFIX + "gui.confirm"),
                        button -> this.confirm())
                .bounds(this.panelX + this.panelWidth - PADDING - buttonWidth, this.buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
        this.confirmButton.active = this.chosenDevice != null;
    }

    /** Whether the device list contains this exact device name. */
    private static boolean contains(List<VulkanDevice> devices, @Nullable String name) {
        if (name == null) {
            return false;
        }
        return devices.stream().anyMatch(device -> device.name().equalsIgnoreCase(name));
    }

    /** What to preselect when nothing valid is stored: the running device, else the first one. */
    @Nullable
    private String fallbackSelection(List<VulkanDevice> devices) {
        if (contains(devices, this.currentDeviceName)) {
            return this.currentDeviceName;
        }
        return devices.isEmpty() ? null : devices.getFirst().name();
    }

    /** Opens the configuration page, keeping this screen as its parent. */
    private void openConfig() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(new VkSelectConfigScreen(this));
        }
    }

    private void confirm() {
        if (this.chosenDevice == null) {
            return;
        }
        VkSelectConfig config = VkSelectConfig.get();
        String previousDevice = config.selectedDevice();
        config.setSelectedDevice(this.chosenDevice);
        config.save();
        VkSelectRuntime.invalidateCache();
        VkSelect.LOGGER.info("[vkselect] The next start will use {}", this.chosenDevice);
        // No chat message here on purpose: the overlay is feedback enough, and a system message
        // would pop up every time the list is confirmed. Only /vkselect talks to the player.

        // A GPU that is not the one we are running on can only be picked up by the next start, so
        // ask the player to restart; the prompt replaces this screen when it is shown.
        if (RestartRequiredScreen.showIfNeeded(this.parent, previousDevice, this.chosenDevice)) {
            return;
        }
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractMenuBackground(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
        graphics.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
        int textLeft = this.panelX + PADDING;
        int textRight = this.panelX + this.panelWidth - PADDING;
        GuiText.drawFitted(graphics, this.title, COLOR_TITLE,
                textLeft, textRight, this.titleY, this.titleY + LINE_HEIGHT);

        Component current = this.currentDeviceName != null
                ? Component.translatable(VkSelect.LANG_PREFIX + "gui.current", this.currentDeviceName)
                : Component.translatable(VkSelect.LANG_PREFIX + "gui.currentUnknown");
        GuiText.drawFitted(graphics, current, COLOR_TEXT,
                textLeft, textRight, this.currentLineY, this.currentLineY + LINE_HEIGHT);

        Component hint;
        int hintColor;
        if (this.error != null) {
            hint = Component.translatable(VkSelect.LANG_PREFIX + "gui.error", this.error);
            hintColor = COLOR_ERROR;
        } else if (this.missingSavedDevice != null) {
            hint = Component.translatable(VkSelect.LANG_PREFIX + "gui.savedMissing", this.missingSavedDevice);
            hintColor = COLOR_WARNING;
        } else if (!this.vulkanPreferred) {
            hint = Component.translatable(VkSelect.LANG_PREFIX + "gui.hint.notVulkan");
            hintColor = COLOR_WARNING;
        } else {
            hint = Component.translatable(VkSelect.LANG_PREFIX + "gui.hint");
            hintColor = COLOR_HINT;
        }
        GuiText.drawFitted(graphics, hint, hintColor,
                textLeft, textRight, this.hintLineY, this.hintLineY + LINE_HEIGHT);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        if (this.parent != null) {
            this.minecraft.gui.setScreen(this.parent);
            return;
        }
        // No parent means the overlay was opened while in game (for example from the mod list), so
        // fall back to whatever the current loader does for a screen without a parent.
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return this.parent == null || this.parent.isPauseScreen();
    }
}
