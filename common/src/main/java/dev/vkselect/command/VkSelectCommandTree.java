package dev.vkselect.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vkselect.DeviceSelection;
import dev.vkselect.VkSelect;
import dev.vkselect.VkSelectConfig;
import dev.vkselect.VkSelectRuntime;
import dev.vkselect.gui.RestartRequiredScreen;
import dev.vkselect.vulkan.VulkanDevice;
import dev.vkselect.vulkan.VulkanDeviceEnumerator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;

/**
 * The {@code /vkselect} command tree.
 *
 * <p>Both loaders run client commands on a different source type (Fabric uses
 * {@code FabricClientCommandSource}, NeoForge uses {@code CommandSourceStack}), so the tree is
 * generic over the source and the loader supplies the two tiny feedback functions.
 */
public final class VkSelectCommandTree {
    /** Sends command feedback for a specific loader's command source type. */
    public interface Messenger<S> {
        void info(S source, Component message);

        void error(S source, Component message);
    }

    private VkSelectCommandTree() {
    }

    public static <S> LiteralArgumentBuilder<S> create(Messenger<S> messenger) {
        // Plain Brigadier builders keep the tree independent of the loader specific source type;
        // net.minecraft.commands.Commands is hard wired to CommandSourceStack.
        return LiteralArgumentBuilder.<S>literal("vkselect")
                .then(LiteralArgumentBuilder.<S>literal("list")
                        .executes(context -> {
                            list(messenger, context.getSource());
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<S>literal("select")
                        .then(RequiredArgumentBuilder.<S, String>argument("keyword", StringArgumentType.greedyString())
                                .suggests((context, builder) -> suggestDevices(builder))
                                .executes(context -> {
                                    select(messenger, context.getSource(),
                                            StringArgumentType.getString(context, "keyword"));
                                    return 1;
                                })));
    }

    private static <S> void list(Messenger<S> messenger, S source) {
        VulkanDeviceEnumerator.Result result = VkSelectRuntime.markedDevices();
        if (!result.ok()) {
            messenger.error(source, Component.translatable(VkSelect.LANG_PREFIX + "command.list.failed",
                    result.error() == null ? "unknown error" : result.error()));
            return;
        }

        List<VulkanDevice> devices = result.devices();
        messenger.info(source, Component.translatable(VkSelect.LANG_PREFIX + "command.list.header", devices.size()));

        for (VulkanDevice device : devices) {
            messenger.info(source, Component.literal(format(device)));
        }

        String current = VkSelectRuntime.currentDeviceName();
        if (current != null) {
            messenger.info(source, Component.translatable(VkSelect.LANG_PREFIX + "command.current", current));
        }

        String selected = VkSelectConfig.get().selectedDevice();
        if (!selected.isBlank()) {
            messenger.info(source, Component.translatable(VkSelect.LANG_PREFIX + "command.selected", selected));
        }
    }

    /** {@code [index] <isCurrent?> <device name> <driver> <driver version>}. */
    public static String format(VulkanDevice device) {
        return String.format("[%d] %s %s | %s %s",
                device.index(),
                device.current() ? "*" : " ",
                device.name() + device.typeSuffix(),
                device.driverName().isBlank() ? "unknown" : device.driverName(),
                device.driverVersion());
    }

    private static <S> void select(Messenger<S> messenger, S source, String keyword) {
        VulkanDeviceEnumerator.Result result = VkSelectRuntime.devices();
        if (!result.ok()) {
            messenger.error(source, Component.translatable(VkSelect.LANG_PREFIX + "command.list.failed",
                    result.error() == null ? "unknown error" : result.error()));
            return;
        }

        DeviceSelection.match(result.devices(), keyword).ifPresentOrElse(device -> {
            VkSelectConfig config = VkSelectConfig.get();
            String previousDevice = config.selectedDevice();
            config.setSelectedDevice(device.name());
            config.save();
            VkSelectRuntime.invalidateCache();
            VkSelect.LOGGER.info("[vkselect] Selected device for the next start: {}", device.name());
            messenger.info(source, Component.translatable(VkSelect.LANG_PREFIX + "command.select.success", device.name()));
            // Selecting from the chat gets the same restart guidance as the overlay.
            RestartRequiredScreen.showIfNeeded(null, previousDevice, device.name());
        }, () -> messenger.error(source, Component.translatable(VkSelect.LANG_PREFIX + "command.select.notFound", keyword)));
    }

    private static List<String> deviceNames() {
        return VkSelectRuntime.devices().devices().stream().map(VulkanDevice::name).toList();
    }

    /**
     * Suggests every device whose name contains what has been typed so far.
     *
     * <p>{@code SharedSuggestionProvider.suggest} only matches prefixes (and after {@code ._/}),
     * so typing {@code radeon} would not complete {@code AMD Radeon RX 7900 XTX}; device names are
     * long, so a contains-match is much more useful here.
     */
    private static CompletableFuture<Suggestions> suggestDevices(SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String name : deviceNames()) {
            if (remaining.isEmpty() || name.toLowerCase(Locale.ROOT).contains(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}
