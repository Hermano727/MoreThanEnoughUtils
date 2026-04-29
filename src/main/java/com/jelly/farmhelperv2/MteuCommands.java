package com.jelly.farmhelperv2;

import com.jelly.farmhelperv2.config.FarmHelperConfigScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

import com.jelly.farmhelperv2.render.JawbusWarningHud;
import com.jelly.farmhelperv2.util.ChatUtils;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side commands for MoreThanEnoughUtils.
 *
 * Currently exposes:
 * - /mteu: opens the MTEU config GUI.
 * - /mteu jawbus_test: shows the Jawbus center warning for 3 seconds (verifies HUD).
 * - /mteu jawbus_debug: toggles whether every chat line flashes that warning (debug only).
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
                        .then(literal("jawbus_test")
                                .executes(context -> {
                                    JawbusWarningHud.triggerWarning();
                                    MinecraftClient client = context.getSource().getClient();
                                    if (client.player != null) {
                                        client.player.sendMessage(
                                                ChatUtils.info("Jawbus overlay test (3s). If you see nothing, check latest.log for HUD registration.)"),
                                                false);
                                    }
                                    return 1;
                                }))
                        .then(literal("jawbus_debug")
                                .executes(context -> {
                                    JawbusWarningHud.toggleDebugTriggerOnAnyChat();
                                    boolean on = JawbusWarningHud.isDebugTriggerOnAnyChat();
                                    MinecraftClient client = context.getSource().getClient();
                                    if (client.player != null) {
                                        client.player.sendMessage(
                                                on
                                                        ? ChatUtils.warning("Jawbus debug: ANY chat line will flash the overlay (toggle off with /mteu jawbus_debug)")
                                                        : ChatUtils.info("Jawbus debug: off (normal matching only)"),
                                                false);
                                    }
                                    return 1;
                                }))
        );
    }
}

