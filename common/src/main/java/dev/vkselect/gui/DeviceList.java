package dev.vkselect.gui;

import dev.vkselect.VkSelect;
import dev.vkselect.vulkan.VulkanDevice;
import java.util.List;
import java.util.function.Consumer;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The device list of the selection overlay: a fixed height box that always shows its rows, with a
 * scrollbar on the right hand side.
 *
 * <p>Replaces the earlier dropdown - the mock up asks for a plain scroll menu, so there is no
 * collapse/expand state any more. Scrolling works with the mouse wheel and by dragging the
 * scrollbar thumb; clicking a row selects it.
 */
public class DeviceList extends AbstractWidget {
    public static final int ROW_HEIGHT = 18;
    /** Rows visible at once; the rest is reachable through the scrollbar. */
    public static final int VISIBLE_ROWS = 4;

    private static final int TEXT_MARGIN = 4;
    /** The same sprites vanilla uses for its lists. */
    private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("widget/scroller_background");
    private static final int SCROLLBAR_WIDTH = 6;
    /** Vanilla clamps the thumb between 32px and (height - 8). */
    private static final int MIN_THUMB_HEIGHT = 32;
    private static final int THUMB_BOTTOM_MARGIN = 8;
    private static final int SEPARATOR_DASH = 3;
    private static final int SEPARATOR_PERIOD = 6;

    private static final int COLOR_BACKGROUND = 0xC0101010;
    private static final int COLOR_BORDER = 0xFF000000;
    private static final int COLOR_ROW_SELECTED = 0xFF1E3A1E;
    private static final int COLOR_ROW_HOVER = 0xFF2E4A2E;
    private static final int COLOR_SEPARATOR = 0xFF9AA0AA;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_CURRENT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SELECTED = 0xFF7FE07F;
    private static final int COLOR_TEXT_EMPTY = 0xFFAAAAAA;

    private final Consumer<VulkanDevice> onSelect;
    /** Resolved once instead of per row per frame; the language is loaded when a screen opens. */
    private final String currentSuffix;
    private final Component emptyLabel;

    private List<VulkanDevice> devices = List.of();
    private int selected = -1;
    private int scroll;
    private boolean draggingThumb;

    public DeviceList(int x, int y, int width, List<VulkanDevice> devices, String selectedName,
                      Consumer<VulkanDevice> onSelect) {
        super(x, y, width, VISIBLE_ROWS * ROW_HEIGHT,
                Component.translatable(VkSelect.LANG_PREFIX + "gui.deviceList"));
        this.onSelect = onSelect;
        this.currentSuffix = Component.translatable(VkSelect.LANG_PREFIX + "gui.currentSuffix").getString();
        this.emptyLabel = Component.translatable(VkSelect.LANG_PREFIX + "gui.noDevices");
        setDevices(devices, selectedName);
    }

    public void setDevices(List<VulkanDevice> devices, String selectedName) {
        this.devices = List.copyOf(devices);
        this.selected = indexOf(selectedName);
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
        this.setHeight(VISIBLE_ROWS * ROW_HEIGHT);
    }

    private int indexOf(String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }
        for (int i = 0; i < this.devices.size(); i++) {
            if (this.devices.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** The currently highlighted device, or {@code null} when the list is empty. */
    public VulkanDevice selectedDevice() {
        return this.selected >= 0 && this.selected < this.devices.size() ? this.devices.get(this.selected) : null;
    }

    public void setSelectedIndex(int index) {
        this.selected = index;
    }

    private int maxScroll() {
        return Math.max(0, this.devices.size() - VISIBLE_ROWS);
    }

    private int scrollbarX() {
        return getX() + getWidth() - SCROLLBAR_WIDTH;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= scrollbarX() && mouseX < scrollbarX() + SCROLLBAR_WIDTH
                && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    /** Vanilla's formula, expressed with this widget's row based scroll offset. */
    private int thumbHeight() {
        int contentHeight = Math.max(this.devices.size(), VISIBLE_ROWS) * ROW_HEIGHT;
        return Mth.clamp((int) ((float) (getHeight() * getHeight()) / contentHeight),
                MIN_THUMB_HEIGHT, getHeight() - THUMB_BOTTOM_MARGIN);
    }

    private int thumbY() {
        int maxPixels = maxScroll() * ROW_HEIGHT;
        if (maxPixels == 0) {
            return getY();
        }
        int travel = getHeight() - thumbHeight();
        return Math.max(getY(), this.scroll * ROW_HEIGHT * travel / maxPixels + getY());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), COLOR_BACKGROUND);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), COLOR_BORDER);

        if (this.devices.isEmpty()) {
            GuiText.drawFitted(graphics, this.emptyLabel, COLOR_TEXT_EMPTY,
                    getX() + TEXT_MARGIN, getX() + getWidth() - TEXT_MARGIN,
                    getY(), getY() + ROW_HEIGHT);
            return;
        }

        boolean scrollable = maxScroll() > 0;
        int textRight = getX() + getWidth() - TEXT_MARGIN
                - (scrollable ? SCROLLBAR_WIDTH + 1 : 0);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = this.scroll + row;
            if (index >= this.devices.size()) {
                break;
            }

            int rowY = getY() + row * ROW_HEIGHT;
            boolean hovered = mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            VulkanDevice device = this.devices.get(index);
            int background = index == this.selected ? COLOR_ROW_SELECTED : (hovered ? COLOR_ROW_HOVER : 0);
            if (background != 0) {
                graphics.fill(getX() + 1, rowY, getX() + getWidth() - 1, rowY + ROW_HEIGHT, background);
            }
            if (row > 0) {
                drawDashedSeparator(graphics, rowY);
            }

            String label = (index == this.selected ? "> " : "") + device.name()
                    + (device.current() ? " " + this.currentSuffix : "");
            int color = index == this.selected ? COLOR_TEXT_SELECTED
                    : (device.current() ? COLOR_TEXT_CURRENT : COLOR_TEXT);
            GuiText.drawFitted(graphics, label, color, getX() + TEXT_MARGIN, textRight, rowY, rowY + ROW_HEIGHT);
        }

        if (scrollable) {
            int scrollbarX = scrollbarX();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE,
                    scrollbarX, getY(), SCROLLBAR_WIDTH, getHeight());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE,
                    scrollbarX, thumbY(), SCROLLBAR_WIDTH, thumbHeight());
            if (isOverScrollbar(mouseX, mouseY)) {
                graphics.requestCursor(this.draggingThumb ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
            }
        }
    }

    /** Dashed divider between rows, matching the mock up. */
    private void drawDashedSeparator(GuiGraphicsExtractor graphics, int y) {
        int start = getX() + TEXT_MARGIN;
        int end = getX() + getWidth() - TEXT_MARGIN;
        for (int x = start; x < end; x += SEPARATOR_PERIOD) {
            graphics.fill(x, y, Math.min(x + SEPARATOR_DASH, end), y + 1, COLOR_SEPARATOR);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || !this.visible || !isValidClickButton(event.buttonInfo())) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() || mouseY >= getY() + getHeight()) {
            return false;
        }

        if (maxScroll() > 0 && mouseX >= scrollbarX()) {
            dragThumbTo(mouseY);
            this.draggingThumb = true;
            return true;
        }

        int row = (int) ((mouseY - getY()) / ROW_HEIGHT);
        int index = this.scroll + row;
        if (index >= 0 && index < this.devices.size()) {
            this.selected = index;
            this.onSelect.accept(this.devices.get(index));
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    private void dragThumbTo(double mouseY) {
        int travel = getHeight() - thumbHeight();
        if (travel <= 0) {
            this.scroll = 0;
            return;
        }
        double relative = (mouseY - getY() - thumbHeight() / 2.0) / travel;
        this.scroll = Mth.clamp((int) Math.round(relative * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!this.draggingThumb) {
            return false;
        }
        dragThumbTo(event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!this.draggingThumb) {
            return false;
        }
        this.draggingThumb = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() <= 0) {
            return false;
        }
        if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() || mouseY >= getY() + getHeight()) {
            return false;
        }
        this.scroll = Mth.clamp((int) Math.round(this.scroll - scrollY), 0, maxScroll());
        return true;
    }
}
