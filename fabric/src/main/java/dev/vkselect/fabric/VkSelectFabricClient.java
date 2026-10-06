package dev.vkselect.fabric;

import dev.vkselect.Env;
import dev.vkselect.VkSelect;
import dev.vkselect.command.VkSelectCommandTree;
import dev.vkselect.VkSelectScreens;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Fabric client entrypoint.
 *
 * <p>Only Fabric API is used: {@code fabric-command-api-v2} for {@code /vkselect} and
 * {@code fabric-screen-api-v1} for the menu buttons, so no mixin is needed for the entry points.
 */
public final class VkSelectFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Env.setPlatform(new FabricPlatform());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(VkSelectCommandTree.create(FabricMessenger.INSTANCE)));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> addEntryPoint(screen));

        VkSelect.LOGGER.info("[vkselect] Fabric client ready: /vkselect registered, menu entry points active");
    }

    private static void addEntryPoint(Screen screen) {
        if (VkSelectScreens.shouldAddTo(screen)) {
            Screens.getWidgets(screen).add(VkSelectScreens.createEntryButton(screen));
        }
    }

    private enum FabricMessenger implements VkSelectCommandTree.Messenger<FabricClientCommandSource> {
        INSTANCE;

        @Override
        public void info(FabricClientCommandSource source, Component message) {
            source.sendFeedback(message);
        }

        @Override
        public void error(FabricClientCommandSource source, Component message) {
            source.sendError(message);
        }
    }
}
