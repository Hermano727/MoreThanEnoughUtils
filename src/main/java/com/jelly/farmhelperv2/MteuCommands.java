package com.jelly.farmhelperv2;

import com.jelly.farmhelperv2.config.FarmHelperConfigScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side commands for MoreThanEnoughUtils.
 *
 * Currently exposes:
 * - /mteu: opens the MTEU config GUI.
 */
public final class MteuCommands {

    private MteuCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(MteuCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                         CommandRegistryAccess registryAccess) {
        dispatcher.register(
                literal("mteu")
                        .executes(context -> {
                            FabricClientCommandSource source = context.getSource();
                            MinecraftClient client = source.getClient();
                            if (client.player == null) {
                                throw new CommandSyntaxException(
                                        null,
                                        Text.literal("Player not available")
                                );
                            }
                            client.send(() -> client.setScreen(FarmHelperConfigScreen.create(client.currentScreen)));
                            return 1;
                        })
        );
    }
}

