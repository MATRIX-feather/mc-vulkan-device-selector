package dev.vkselect.gui;

import dev.vkselect.VkSelect;
import dev.vkselect.VkSelectRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The "Restart Minecraft?" prompt shown after the player picked a GPU that is not the one this
 * session runs on.
 *
 * <p>The device is chosen before the Vulkan instance exists, so a new selection can only take
 * effect on the next start. This screen explains that and offers to close the game, because
 * Minecraft neither relaunches itself nor lets the launcher pick up a new GPU while it is running.
 *
 * <p>The layout follows the mock up: title on top, the explanation in the middle, and the deferring
 * "I'll restart later" on the left with the game closing "Confirm" on the right - the same button
 * order as the device selection overlay.
 */
public class RestartRequiredScreen extends Screen {
    /** Same panel width as the device selection overlay, so both screens line up. */
    private static final int MAX_PANEL_WIDTH = 360;
    private static final int PADDING = 14;
    private static final int TITLE_TOP = 12;
    private static final int TITLE_ROW_HEIGHT = 20;
    private static final int MESSAGE_TOP_GAP = 14;
    private static final int BUTTON_TOP_GAP = 22;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MIN_BUTTON_WIDTH = 60;
    private static final int MAX_BUTTON_WIDTH = 150;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0xFF000000;

    /**
     * This screen is opened by a click on the selection overlay's "Confirm" button, so a double
     * click would otherwise land on "Confirm" here too and close the game by accident. Vanilla
     * confirm screens guard destructive buttons the same way.
     */
    private static final int CONFIRM_DELAY_TICKS = 10;

    @Nullable
    private final Screen parent;

    @Nullable
    private Button confirmButton;
    private int confirmDelayTicks = CONFIRM_DELAY_TICKS;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public RestartRequiredScreen(@Nullable Screen parent) {
        super(Component.translatable(VkSelect.LANG_PREFIX + "gui.restart.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Component message = Component.translatable(VkSelect.LANG_PREFIX + "gui.restart.message");
        this.panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(200, this.width - 20));
        int contentWidth = this.panelWidth - PADDING * 2;

        // Multi line text with vanilla wrapping; measured before the panel so it can be sized to it.
        MultiLineTextWidget messageWidget = new MultiLineTextWidget(message, this.font)
                .setMaxWidth(contentWidth)
                .setCentered(false);
        int messageHeight = messageWidget.getHeight();

        this.panelHeight = TITLE_TOP + TITLE_ROW_HEIGHT + MESSAGE_TOP_GAP + messageHeight + BUTTON_TOP_GAP
                + BUTTON_HEIGHT + PADDING;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = Math.max(4, (this.height - this.panelHeight) / 2);
        int messageY = this.panelY + TITLE_TOP + TITLE_ROW_HEIGHT + MESSAGE_TOP_GAP;
        int buttonY = messageY + messageHeight + BUTTON_TOP_GAP;

        messageWidget.setPosition(this.panelX + PADDING, messageY);
        this.addRenderableWidget(messageWidget);

        int buttonWidth = Math.max(MIN_BUTTON_WIDTH, Math.min(MAX_BUTTON_WIDTH, (contentWidth - 10) / 2));
        this.addRenderableWidget(Button.builder(
                        Component.translatable(VkSelect.LANG_PREFIX + "gui.restart.later"),
                        button -> this.restartLater())
                .bounds(this.panelX + PADDING, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());

        this.confirmButton = this.addRenderableWidget(Button.builder(
                        Component.translatable(VkSelect.LANG_PREFIX + "gui.restart.confirm"),
                        button -> this.closeGame())
                .bounds(this.panelX + this.panelWidth - PADDING - buttonWidth, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
        this.confirmButton.active = this.confirmDelayTicks <= 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.confirmDelayTicks > 0 && --this.confirmDelayTicks == 0 && this.confirmButton != null) {
            this.confirmButton.active = true;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractMenuBackground(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
        graphics.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);
        GuiText.drawFitted(graphics, this.title, COLOR_TITLE,
                this.panelX + PADDING, this.panelX + this.panelWidth - PADDING,
                this.panelY + TITLE_TOP, this.panelY + TITLE_TOP + TITLE_ROW_HEIGHT);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Keeps playing: the selection is already saved, it is just applied on the next start. */
    private void restartLater() {
        if (this.minecraft == null) {
            return;
        }
        if (this.parent != null) {
            this.minecraft.gui.setScreen(this.parent);
            return;
        }
        // Opened without a parent (command, or the overlay knows of none): back to the game.
        super.onClose();
    }

    /** Closes the game the same way the vanilla "Quit Game" button does, saving everything. */
    private void closeGame() {
        Minecraft minecraft = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        VkSelect.LOGGER.info("[vkselect] Closing Minecraft so the new GPU selection takes effect"
                + " on the next start");
        minecraft.stop();
    }

    @Override
    public void onClose() {
        // Escape and anything else that closes a screen means "not now".
        this.restartLater();
    }

    @Override
    public boolean isPauseScreen() {
        return this.parent == null || this.parent.isPauseScreen();
    }

    /**
     * Whether storing {@code selectedDevice} really needs a restart: something changed and the new
     * device is not the one the current session already runs on.
     */
    public static boolean needsRestart(@Nullable String previousDevice, @Nullable String selectedDevice) {
        if (selectedDevice == null || selectedDevice.isBlank()) {
            return false;
        }
        if (selectedDevice.equalsIgnoreCase(previousDevice)) {
            return false;
        }
        // Without a known running device (backend not ready yet, unknown backend) there is nothing
        // to compare against, so stay quiet instead of nagging.
        String currentDevice = VkSelectRuntime.currentDeviceName();
        return currentDevice != null && !currentDevice.equalsIgnoreCase(selectedDevice);
    }

    /**
     * Shows the prompt when {@link #needsRestart} says so.
     *
     * @return whether the prompt was shown; the caller must not close its own screen in that case
     */
    public static boolean showIfNeeded(@Nullable Screen parent, @Nullable String previousDevice,
                                       @Nullable String selectedDevice) {
        if (!needsRestart(previousDevice, selectedDevice)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        // Client commands may run off the render thread; this is a no-op hop when we are on it.
        minecraft.execute(() -> minecraft.gui.setScreen(new RestartRequiredScreen(parent)));
        return true;
    }
}
