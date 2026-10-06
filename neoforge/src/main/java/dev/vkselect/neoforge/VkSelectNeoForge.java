package dev.vkselect.neoforge;

import dev.vkselect.Env;
import dev.vkselect.VkSelect;
import dev.vkselect.command.VkSelectCommandTree;
import dev.vkselect.VkSelectScreens;
import dev.vkselect.gui.VulkanDeviceSelectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entrypoint.
 *
 * <p>{@code dist = Dist.CLIENT} keeps the class from being constructed on dedicated servers, so the
 * client only types below are safe.
 *
 * <p>Everything uses the NeoForge API that ships with the loader itself:
 * {@link RegisterClientCommandsEvent} for {@code /vkselect}, {@link ScreenEvent.Init.Post} for the
 * menu buttons and {@link IConfigScreenFactory} for the mod list entry.
 */
@Mod(value = VkSelect.MOD_ID, dist = Dist.CLIENT)
public final class VkSelectNeoForge {
    public VkSelectNeoForge(ModContainer container, IEventBus modBus) {
        Env.setPlatform(new NeoForgePlatform());

        // Shows up as the "Config" button of the mod in NeoForge's mod list.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new VulkanDeviceSelectScreen(parent));

        NeoForge.EVENT_BUS.addListener(VkSelectNeoForge::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(VkSelectNeoForge::onScreenInit);

        VkSelect.LOGGER.info("[vkselect] NeoForge client ready: /vkselect registered, menu entry points active");
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(VkSelectCommandTree.create(NeoForgeMessenger.INSTANCE));
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (VkSelectScreens.shouldAddTo(screen)) {
            event.addListener(VkSelectScreens.createEntryButton(screen));
        }
    }

    private enum NeoForgeMessenger implements VkSelectCommandTree.Messenger<CommandSourceStack> {
        INSTANCE;

        @Override
        public void info(CommandSourceStack source, Component message) {
            source.sendSuccess(() -> message, false);
        }

        @Override
        public void error(CommandSourceStack source, Component message) {
            source.sendFailure(message);
        }
    }
}
