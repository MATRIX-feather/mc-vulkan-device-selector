package dev.vkselect;

import dev.vkselect.gui.VulkanDeviceSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Loader independent screen integration.
 *
 * <p>Minecraft 26.3 builds the title screen and the pause menu inside private methods, so the
 * button is appended through the loader APIs instead of a mixin: Fabric uses
 * {@code ScreenEvents.AFTER_INIT} plus {@code Screens.getButtons(Screen)}, NeoForge uses
 * {@code ScreenEvent.Init.Post#addListener}. This class only knows how to build the button and
 * when it should be shown.
 */
public final class VkSelectScreens {
    public static final int BUTTON_X = 6;
    public static final int BUTTON_Y = 6;
    public static final int BUTTON_WIDTH = 120;
    public static final int BUTTON_HEIGHT = 20;

    private static final Component BUTTON_LABEL = Component.translatable(VkSelect.LANG_PREFIX + "gui.button");

    private VkSelectScreens() {
    }

    /** Builds the entry point button; the caller adds it to the screen. */
    public static Button createEntryButton(@Nullable Screen parent) {
        return Button.builder(BUTTON_LABEL, button -> open(parent))
                .bounds(BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(VkSelect.LANG_PREFIX + "gui.button.tooltip")))
                .build();
    }

    /** Opens the overlay. */
    public static void open(@Nullable Screen parent) {
        // Gui#setScreen, not Minecraft#setScreenAndShow: the latter renders an extra frame right away
        // ("forcedTick"), and that frame is extracted with shouldRenderLevel=false, so over a loaded
        // level the background comes out black for one frame. Vanilla only uses it while tearing a
        // level down. Gui#setScreen is what every vanilla screen uses.
        Minecraft.getInstance().gui.setScreen(new VulkanDeviceSelectScreen(parent));
    }

    /** Whether the entry point should be added to this screen. */
    public static boolean shouldAddTo(Screen screen) {
        if (screen instanceof TitleScreen) {
            return VkSelectConfig.get().showInMainMenu() && !alreadyAdded(screen);
        }
        if (screen instanceof PauseScreen) {
            return VkSelectConfig.get().showInPauseMenu() && !alreadyAdded(screen);
        }
        return false;
    }

    /** Guards against adding the button twice when a screen is re-initialised. */
    public static boolean alreadyAdded(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && BUTTON_LABEL.equals(widget.getMessage())) {
                return true;
            }
        }
        return false;
    }
}
