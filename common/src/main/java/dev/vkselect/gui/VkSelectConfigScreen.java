package dev.vkselect.gui;

import dev.vkselect.VkSelect;
import dev.vkselect.VkSelectConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The configuration page, opened from the {@code Config} button in the top right of the selection
 * overlay.
 *
 * <p>Layout follows the mock up: a title, one row per option (option title on top of an ON/OFF
 * toggle) and a Back button at the bottom. Every toggle is written to {@code config/vkselect.json}
 * immediately, and Back returns to the selection overlay.
 */
public class VkSelectConfigScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 320;
    private static final int PADDING = 16;
    private static final int TITLE_HEIGHT = 12;
    private static final int OPTION_LABEL_HEIGHT = 11;
    private static final int ROW_HEIGHT = 44;
    private static final int TOGGLE_WIDTH = 100;
    private static final int TOGGLE_HEIGHT = 20;
    private static final int BACK_HEIGHT = 20;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_OPTION = 0xFFE0E0E0;
    private static final int COLOR_HINT = 0xFFA0A0A0;
    private static final int COLOR_BORDER = 0xFF000000;

    @Nullable
    private final Screen parent;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int titleY;
    private int firstRowY;
    private int hintY;
    private int backY;

    public VkSelectConfigScreen(@Nullable Screen parent) {
        super(Component.translatable(VkSelect.LANG_PREFIX + "gui.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        VkSelectConfig config = VkSelectConfig.get();

        this.panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(200, this.width - 20));
        int contentWidth = this.panelWidth - PADDING * 2;
        int rowsHeight = ROW_HEIGHT * 2;
        this.panelHeight = PADDING + TITLE_HEIGHT + 10 + rowsHeight + 8 + OPTION_LABEL_HEIGHT + 10
                + BACK_HEIGHT + PADDING;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = Math.max(4, (this.height - this.panelHeight) / 2);

        this.titleY = this.panelY + PADDING;
        this.firstRowY = this.titleY + TITLE_HEIGHT + 10;
        this.hintY = this.firstRowY + rowsHeight + 8;
        this.backY = this.hintY + OPTION_LABEL_HEIGHT + 10;

        int toggleX = this.panelX + (this.panelWidth - TOGGLE_WIDTH) / 2;

        this.addRenderableWidget(CycleButton.onOffBuilder(config.showInMainMenu())
                .displayOnlyValue()
                .create(toggleX, this.firstRowY + OPTION_LABEL_HEIGHT + 2, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable(VkSelect.LANG_PREFIX + "gui.config.showInMainMenu"),
                        (button, value) -> {
                            config.setShowInMainMenu(value);
                            config.save();
                        }));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.showInPauseMenu())
                .displayOnlyValue()
                .create(toggleX, this.firstRowY + ROW_HEIGHT + OPTION_LABEL_HEIGHT + 2, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable(VkSelect.LANG_PREFIX + "gui.config.showInPauseMenu"),
                        (button, value) -> {
                            config.setShowInPauseMenu(value);
                            config.save();
                        }));

        int backWidth = Math.min(200, contentWidth);
        this.addRenderableWidget(Button.builder(
                        Component.translatable(VkSelect.LANG_PREFIX + "gui.config.back"),
                        button -> this.onClose())
                .bounds(this.panelX + (this.panelWidth - backWidth) / 2, this.backY, backWidth, BACK_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractMenuBackground(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
        graphics.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, COLOR_BORDER);

        int textLeft = this.panelX + PADDING;
        int textRight = this.panelX + this.panelWidth - PADDING;
        GuiText.drawFitted(graphics, this.title, COLOR_TITLE,
                textLeft, textRight, this.titleY, this.titleY + TITLE_HEIGHT);

        // Option titles sit above their toggle, like the widget mock up.
        GuiText.drawFitted(graphics, Component.translatable(VkSelect.LANG_PREFIX + "gui.config.showInMainMenu"),
                COLOR_OPTION, textLeft, textRight, this.firstRowY, this.firstRowY + OPTION_LABEL_HEIGHT);
        GuiText.drawFitted(graphics, Component.translatable(VkSelect.LANG_PREFIX + "gui.config.showInPauseMenu"),
                COLOR_OPTION, textLeft, textRight,
                this.firstRowY + ROW_HEIGHT, this.firstRowY + ROW_HEIGHT + OPTION_LABEL_HEIGHT);

        GuiText.drawFitted(graphics, Component.translatable(VkSelect.LANG_PREFIX + "gui.config.hint"),
                COLOR_HINT, textLeft, textRight, this.hintY, this.hintY + OPTION_LABEL_HEIGHT);

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
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return this.parent == null || this.parent.isPauseScreen();
    }
}
