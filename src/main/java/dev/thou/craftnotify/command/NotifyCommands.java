package dev.thou.craftnotify.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import dev.thou.craftnotify.notification.SecretChannelStore;
import dev.thou.craftnotify.notification.WebhookCallbackServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class NotifyCommands {
    private NotifyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var configure = Commands.literal("configure")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("channel", StringArgumentType.word())
                                .then(Commands.argument("label", StringArgumentType.string())
                                        .then(Commands.argument("title", StringArgumentType.string())
                                                .then(Commands.argument("content", StringArgumentType.string())
                                                        .executes(context -> configure(context.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                StringArgumentType.getString(context, "channel"),
                                                                StringArgumentType.getString(context, "label"),
                                                                StringArgumentType.getString(context, "title"),
                                                                StringArgumentType.getString(context, "content"),
                                                                30))
                                                        .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(5, 86400))
                                                                .executes(context -> configure(context.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                        StringArgumentType.getString(context, "channel"),
                                                                        StringArgumentType.getString(context, "label"),
                                                                        StringArgumentType.getString(context, "title"),
                                                                        StringArgumentType.getString(context, "content"),
                                                                        IntegerArgumentType.getInteger(context, "cooldownSeconds")))))))));

        dispatcher.register(Commands.literal("notify")
                .then(Commands.literal("channels")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Configured channels: " + SecretChannelStore.channelIds()),
                                    false
                            );
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            SecretChannelStore.reload();
                            WebhookCallbackServer.reload();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Craft Notify channels reloaded"), true);
                            return 1;
                        }))
                .then(configure));
    }

    private static int configure(CommandSourceStack source, BlockPos pos, String channel, String label,
                                 String title, String content, int cooldownSeconds) {
        if (!(source.getLevel().getBlockEntity(pos) instanceof NotifierBlockEntity notifier)) {
            source.sendFailure(Component.literal("There is no Redstone Notifier at " + pos.toShortString()));
            return 0;
        }
        notifier.configure(label, channel, title, content, cooldownSeconds);
        source.sendSuccess(() -> Component.literal(
                "Configured notifier at " + pos.toShortString() + " for channel '" + channel + "'"), true);
        return 1;
    }
}
